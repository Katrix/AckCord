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

import ackcord.util.AckCordRequestSettings
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status, Timers}

//noinspection ActorMutableStateInspection
class Ratelimiter extends Actor with Timers with ActorLogging {
  import Ratelimiter._

  private val routeLimits = new mutable.HashMap[String, Int] //Key is bucket

  private val uriToBucket = new mutable.HashMap[String, String]

  private val remainingRequests = new mutable.HashMap[String, Int]
  private val rateLimits        = new mutable.HashMap[String, mutable.Queue[(ActorRef, WantToPass[_])]]

  private var globalRatelimitTimeout = 0.toLong
  private val globalLimited          = new mutable.Queue[(ActorRef, WantToPass[_])]

  private val settings = AckCordRequestSettings()(context.system)

  def isGlobalRatelimited: Boolean = globalRatelimitTimeout - System.currentTimeMillis() > 0

  def handleWantToPassGlobal[A](sendResponseTo: ActorRef, request: WantToPass[A]): Unit = {
    context.watchWith(sendResponseTo, GlobalTimedOut(sendResponseTo))
    globalLimited.enqueue(sendResponseTo -> request)
  }

  def handleWantToPassNotGlobal[A](sendResponseTo: ActorRef, request: WantToPass[A]): Unit = {
    val WantToPass(route, _, responseObj) = request

    if (!remainingRequests.contains(route.uriWithMajor)) {
      for {
        bucket           <- uriToBucket.get(route.uriWithoutMajor)
        defaultRateLimit <- routeLimits.get(bucket)
      } {
        remainingRequests.put(route.uriWithMajor, defaultRateLimit)
      }
    }

    val remainingOpt = remainingRequests.get(route.uriWithMajor)

    if (remainingOpt.forall(_ > 0)) {
      remainingOpt.foreach(remaining => remainingRequests.put(route.uriWithMajor, remaining - 1))
      sendResponseTo ! responseObj
    } else {
      context.watchWith(sendResponseTo, TimedOut(route.uriWithMajor, sendResponseTo))
      rateLimits.getOrElseUpdate(route.uriWithMajor, mutable.Queue.empty).enqueue(sendResponseTo -> request)
    }
  }

  def receive: Receive = {
    case ResetRatelimit(uriWithMajor, bucket) =>
      if (settings.LogRatelimitEvents) {
        log.debug(
          s"""|Reseting ratelimit for: $uriWithMajor
              |Bucket: $bucket
              |Limit: ${routeLimits.get(bucket)}
              |Current time: ${System.currentTimeMillis()}
              |""".stripMargin
        )
      }

      routeLimits.get(bucket) match {
        case Some(limit) => remainingRequests.put(uriWithMajor, limit)
        case None        => remainingRequests.remove(uriWithMajor)
      }
      releaseWaiting(uriWithMajor)

    case GlobalTimer =>
      globalLimited.dequeueAll(_ => true).foreach {
        case (actor, request) =>
          context.unwatch(actor)
          handleWantToPassNotGlobal(actor, request)
      }

    case request @ WantToPass(route, identifier, _) =>
      if (settings.LogRatelimitEvents) {
        log.debug(
          s"""|Got incoming request: ${route.uriWithMajor} $identifier
              |RouteLimits: ${uriToBucket.get(route.uriWithoutMajor).flatMap(k => routeLimits.get(k))}
              |Remaining requests: ${remainingRequests.get(route.uriWithMajor)}
              |Requests waiting: ${rateLimits.get(route.uriWithMajor).fold(0)(_.size)}
              |Global ratelimit timeout: $globalRatelimitTimeout
              |Global requests waiting: ${globalLimited.size}
              |Current time: ${System.currentTimeMillis()}
              |""".stripMargin
        )
      }

      val sendResponseTo = sender()

      if (isGlobalRatelimited) {
        handleWantToPassGlobal(sendResponseTo, request)
      } else {
        handleWantToPassNotGlobal(sendResponseTo, request)
      }

    case UpdateRatelimits(
        route,
        RatelimitInfo(timeTilReset, remainingRequestsAmount, bucketLimit, bucket),
        isGlobal,
        identifier
        ) =>
      if (settings.LogRatelimitEvents) {
        log.debug(
          s"""|Updating ratelimits info: ${route.uriWithMajor} $identifier
              |Bucket: $bucket
              |BucketLimit: $bucketLimit
              |Global: $isGlobal
              |TimeTilReset: $timeTilReset
              |RemainingAmount: $remainingRequestsAmount
              |Current time: ${System.currentTimeMillis()}
              |""".stripMargin
        )
      }

      //We don't update the remainingRequests map here as the information we get here is slightly outdated

      routeLimits.put(bucket, bucketLimit)
      uriToBucket.put(route.uriWithoutMajor, bucket)

      if (isGlobal) {
        globalRatelimitTimeout = System.currentTimeMillis() + timeTilReset.toMillis
        timers.startSingleTimer(GlobalTimer, GlobalTimer, timeTilReset)
      } else {
        timers.startSingleTimer(
          route.uriWithMajor,
          ResetRatelimit(route.uriWithMajor, bucket),
          timeTilReset
        )
      }

    case TimedOut(uri, actorRef) =>
      rateLimits.get(uri).foreach(_.dequeueFirst(_._1 == actorRef))

    case GlobalTimedOut(actorRef) =>
      globalLimited.dequeueFirst(_._1 == actorRef)
  }

  def releaseWaiting(uri: String): Unit = {
    rateLimits.get(uri).foreach { queue =>
      @tailrec
      def release(remaining: Int): Int = {
        if (remaining <= 0 || queue.isEmpty) remaining
        else {
          val (sender, WantToPass(_, _, response)) = queue.dequeue()
          sender ! response
          context.unwatch(sender)
          release(remaining - 1)
        }
      }

      if (isGlobalRatelimited) {
        queue.dequeueAll(_ => true).foreach {
          case (sendResponseTo, request) =>
            context.unwatch(sendResponseTo)
            handleWantToPassGlobal(sendResponseTo, request)
        }
      } else {
        val newRemaining = release(remainingRequests.getOrElse(uri, Int.MaxValue))
        remainingRequests.put(uri, newRemaining)
      }
    }
  }

  override def postStop(): Unit =
    rateLimits.values.flatten.foreach {
      case (actor, _) =>
        actor ! Status.Failure(new IllegalStateException("Ratelimiter stopped"))
    }
}
object Ratelimiter {
  //TODO: Custom dispatcher
  def props: Props = Props(new Ratelimiter())

  case class WantToPass[A](route: RequestRoute, identifier: UUID, ret: A)
  case class UpdateRatelimits(
      route: RequestRoute,
      ratelimitInfo: RatelimitInfo,
      isGlobal: Boolean,
      identifier: UUID
  )

  private case object GlobalTimer
  private case class ResetRatelimit(uriWithMajor: String, bucket: String)
  private case class TimedOut(uriWithMajor: String, actorRef: ActorRef)
  private case class GlobalTimedOut(actorRef: ActorRef)
}
