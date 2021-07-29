/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ackcord.requests

import java.util.UUID

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._

import ackcord.util.AckCordRequestSettings
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import org.slf4j.Logger

class Ratelimiter(
    context: ActorContext[Ratelimiter.Command],
    log: Logger,
    timers: TimerScheduler[Ratelimiter.Command],
    settings: AckCordRequestSettings
) extends AbstractBehavior[Ratelimiter.Command](context) {
  import Ratelimiter._

  private val routeLimits = new mutable.HashMap[String, Int] //Key is bucket

  private val uriToBucket = new mutable.HashMap[String, String]

  private val remainingRequests = new mutable.HashMap[String, Int]
  private val rateLimits        = new mutable.HashMap[String, mutable.Queue[WantToPass[_]]]
  private val ratelimitResets   = new mutable.HashMap[String, Long]

  private var globalRatelimitTimeout = 0.toLong
  private val globalLimited          = new mutable.Queue[WantToPass[_]]

  def isGlobalRatelimited: Boolean = globalRatelimitTimeout - System.currentTimeMillis() > 0

  def handleWantToPassGlobal[A](request: WantToPass[A]): Unit = {
    context.watchWith(request.replyTo, GlobalTimedOut(request.replyTo))
    globalLimited.enqueue(request)
  }

  def scheduleSpuriousWakeup(route: RequestRoute): Unit = {
    if (settings.SpuriousWakeup.toMillis > 0 && !timers.isTimerActive(route.uriWithMajor)) {
      uriToBucket.get(route.uriWithoutMajor).foreach { bucket =>
        if (settings.LogRatelimitEvents) {
          log.debug(s"""|
                        |Scheduling spurious wakeup for: ${route.uriWithMajor}
                        |In: ${settings.SpuriousWakeup}
                        |""".stripMargin)
        }

        timers.startSingleTimer(
          route.uriWithMajor,
          ResetRatelimit(route.uriWithMajor, bucket, spurious = true),
          settings.SpuriousWakeup
        )
      }
    }
  }

  def getRemainingRequests(route: RequestRoute): Option[Int] = {
    remainingRequests.get(route.uriWithMajor).orElse {
      for {
        bucket           <- uriToBucket.get(route.uriWithoutMajor)
        defaultRateLimit <- routeLimits.get(bucket)
      } yield defaultRateLimit
    }
  }

  def handleWantToPassNotGlobal[A](request: WantToPass[A]): Unit = {
    val WantToPass(route, _, sender, _) = request

    val remainingOpt = getRemainingRequests(route)
    if (remainingOpt.forall(_ > 0)) {
      remainingOpt.foreach(remaining => remainingRequests.put(route.uriWithMajor, remaining - 1))
      sendResponse(request)
    } else {
      context.watchWith(sender, TimedOut(route.uriWithMajor, sender))
      rateLimits.getOrElseUpdate(route.uriWithMajor, mutable.Queue.empty).enqueue(request)
      scheduleSpuriousWakeup(route)
    }
  }

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case ResetRatelimit(uriWithMajor, bucket, spurious) =>
      if (settings.LogRatelimitEvents) {
        log.debug(
          s"""|
              |Reseting ratelimit for: $uriWithMajor
              |Spurious: $spurious
              |Bucket: $bucket
              |Limit: ${routeLimits.get(bucket)}
              |Current time: ${System.currentTimeMillis()}
              |""".stripMargin
        )
      }

      if (spurious) {
        log.warn(s"Encountered spurious wakeup for route $uriWithMajor")
      }

      routeLimits.get(bucket) match {
        case Some(limit) => remainingRequests.put(uriWithMajor, limit)
        case None        => remainingRequests.remove(uriWithMajor)
      }
      releaseWaiting(uriWithMajor)
      Behaviors.same

    case GlobalTimer =>
      globalLimited.dequeueAll(_ => true).foreach { request =>
        context.unwatch(request.replyTo)
        handleWantToPassNotGlobal(request)
      }

      Behaviors.same

    case request @ WantToPass(route, identifier, _, _) =>
      if (settings.LogRatelimitEvents) {
        log.debug(
          s"""|
              |Got incoming request: ${route.uriWithMajor} $identifier
              |RouteLimits: ${uriToBucket.get(route.uriWithoutMajor).flatMap(k => routeLimits.get(k))}
              |Remaining requests: ${remainingRequests.get(route.uriWithMajor)}
              |Requests waiting: ${rateLimits.get(route.uriWithMajor).fold(0)(_.size)}
              |Global ratelimit timeout: $globalRatelimitTimeout
              |Global requests waiting: ${globalLimited.size}
              |Current time: ${System.currentTimeMillis()}
              |""".stripMargin
        )
      }

      if (isGlobalRatelimited) {
        handleWantToPassGlobal(request)
      } else {
        handleWantToPassNotGlobal(request)
      }
      Behaviors.same

    case QueryRatelimits(route, replyTo) =>
      if (isGlobalRatelimited) {
        replyTo ! Left((globalRatelimitTimeout - System.currentTimeMillis()).millis)
      } else {
        getRemainingRequests(route) match {
          case Some(0) =>
            val resetDuration =
              ratelimitResets
                .get(route.uriWithMajor)
                .fold(Duration.Inf: Duration)(reset => (reset - System.currentTimeMillis()).millis)

            replyTo ! Left(resetDuration)
          case Some(remaining) => replyTo ! Right(remaining)
          case None            => replyTo ! Right(-1)
        }
      }

      Behaviors.same

    case UpdateRatelimits(
          route,
          ratelimitInfo @ RatelimitInfo(timeTilReset, remainingRequestsAmount, bucketLimit, bucket),
          isGlobal,
          identifier
        ) =>
      if (settings.LogRatelimitEvents) {
        log.debug(
          s"""|
              |Updating ratelimits info: ${route.method.value} ${route.uriWithMajor} $identifier
              |IsValid ${ratelimitInfo.isValid}
              |Bucket: $bucket
              |BucketLimit: $bucketLimit
              |Global: $isGlobal
              |TimeTilReset: $timeTilReset
              |RemainingAmount: $remainingRequestsAmount
              |Current time: ${System.currentTimeMillis()}
              |""".stripMargin
        )
      }

      if (ratelimitInfo.isValid) {
        //We don't update the remainingRequests map here as the information we get here is slightly outdated
        routeLimits.put(bucket, bucketLimit)
        uriToBucket.put(route.uriWithoutMajor, bucket)

        if (isGlobal) {
          globalRatelimitTimeout = System.currentTimeMillis() + timeTilReset.toMillis
          timers.startSingleTimer(GlobalTimer, GlobalTimer, timeTilReset)
        } else {
          ratelimitResets.put(route.uriWithMajor, System.currentTimeMillis() + timeTilReset.toMillis)
          timers.startSingleTimer(
            route.uriWithMajor,
            ResetRatelimit(route.uriWithMajor, bucket, spurious = false),
            timeTilReset
          )
        }
      } else {
        scheduleSpuriousWakeup(route)
      }

      Behaviors.same

    case TimedOut(uri, actorRef) =>
      rateLimits.get(uri).flatMap(_.dequeueFirst(_.replyTo == actorRef)).foreach {
        case WantToPass(route, identifier, _, _) =>
          if (settings.LogRatelimitEvents) {
            log.debug(
              s"""|
                  |Ratelimit timed out: ${route.method.value} ${route.uriWithMajor} $identifier
                  |Current time: ${System.currentTimeMillis()}
                  |""".stripMargin
            )
          }
      }
      Behaviors.same

    case GlobalTimedOut(actorRef) =>
      globalLimited.dequeueFirst(_.replyTo == actorRef).foreach { case WantToPass(route, identifier, _, _) =>
        if (settings.LogRatelimitEvents) {
          log.debug(
            s"""|
                |Ratelimit timed out globally: ${route.method.value} ${route.uriWithMajor} $identifier
                |Current time: ${System.currentTimeMillis()}
                |""".stripMargin
          )
        }
      }
      Behaviors.same
  }

  def sendResponse[A](request: WantToPass[A]): Unit = request.replyTo ! CanPass(request.ret)

  def releaseWaiting(uri: String): Unit = {
    rateLimits.get(uri).foreach { queue =>
      @tailrec
      def release(remaining: Int): Int = {
        if (remaining <= 0 || queue.isEmpty) remaining
        else {
          val request = queue.dequeue()
          if (settings.LogRatelimitEvents) {
            val WantToPass(route, identifier, _, _) = request
            log.debug(
              s"""|
                  |Releasing request: ${route.method.value} ${route.uriWithMajor} $identifier
                  |Remaining requests: $remaining
                  |Current time: ${System.currentTimeMillis()}
                  |""".stripMargin
            )
          }

          sendResponse(request)
          context.unwatch(request.replyTo)
          release(remaining - 1)
        }
      }

      if (isGlobalRatelimited) {
        queue.dequeueAll(_ => true).foreach { request =>
          log.debug("\nReleasing all requests due to global")
          context.unwatch(request.replyTo)
          handleWantToPassGlobal(request)
        }
      } else {
        val newRemaining = release(remainingRequests.getOrElse(uri, Int.MaxValue))
        remainingRequests.put(uri, newRemaining)
      }
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = { case PostStop =>
    rateLimits.values.flatten.foreach { requests =>
      requests.replyTo ! FailedRequest(new IllegalStateException("Ratelimiter stopped"))
    }
    Behaviors.stopped
  }
}

object Ratelimiter {

  def apply(): Behavior[Command] =
    Behaviors
      .supervise(
        Behaviors.setup[Command] { context =>
          val settings = AckCordRequestSettings()(context.system)
          val log      = context.log

          Behaviors.withTimers(timers => new Ratelimiter(context, log, timers, settings))
        }
      )
      .onFailure(SupervisorStrategy.restart)

  sealed trait Command

  sealed trait Response[+A]
  case class CanPass[+A](a: A)           extends Response[A]
  case class FailedRequest(e: Throwable) extends Response[Nothing]

  case class WantToPass[A](route: RequestRoute, identifier: UUID, replyTo: ActorRef[Response[A]], ret: A)
      extends Command
  case class QueryRatelimits(route: RequestRoute, replyTo: ActorRef[Either[Duration, Int]]) extends Command
  case class UpdateRatelimits(
      route: RequestRoute,
      ratelimitInfo: RatelimitInfo,
      isGlobal: Boolean,
      identifier: UUID
  ) extends Command

  private case object GlobalTimer                                                            extends Command
  private case class ResetRatelimit(uriWithMajor: String, bucket: String, spurious: Boolean) extends Command
  private case class TimedOut[A](uriWithMajor: String, actorRef: ActorRef[A])                extends Command
  private case class GlobalTimedOut[A](actorRef: ActorRef[A])                                extends Command
}
