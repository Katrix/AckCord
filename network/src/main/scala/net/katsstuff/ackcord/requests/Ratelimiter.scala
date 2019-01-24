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
package net.katsstuff.ackcord.requests

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor.{Actor, ActorRef, Props, Status, Timers}

class Ratelimiter extends Actor with Timers {
  import Ratelimiter._

  private val routeLimits       = new mutable.HashMap[String, Int]
  private val remainingRequests = new mutable.HashMap[String, Int]
  private val rateLimits        = new mutable.HashMap[String, mutable.Queue[(ActorRef, Any)]]

  private var globalRatelimitTimeout = 0.toLong
  private val globalLimited          = new mutable.Queue[(ActorRef, Any)]

  def isGlobalRatelimited: Boolean = globalRatelimitTimeout - System.currentTimeMillis() > 0

  def receive: Receive = {
    case ResetRatelimit(uri) =>
      routeLimits.get(uri) match {
        case Some(limit) => remainingRequests.put(uri, limit)
        case None        => remainingRequests.remove(uri)
      }
      releaseWaiting(uri)

    case GlobalTimer =>
      globalLimited.dequeueAll(_ => true).foreach {
        case (actor, obj) =>
          actor ! obj
          context.unwatch(actor)
      }

    case WantToPass(uri, responseObj) =>
      val sendResponseTo = sender()

      if (isGlobalRatelimited) {
        context.watchWith(sendResponseTo, GlobalTimedOut(sendResponseTo))
        globalLimited.enqueue(sendResponseTo -> responseObj)
      } else {
        val remainingOpt = remainingRequests.get(uri)

        if (remainingOpt.forall(_ > 0)) {
          remainingOpt.foreach(remaining => remainingRequests.put(uri, remaining - 1))
          sendResponseTo ! responseObj
        } else {
          context.watchWith(sendResponseTo, TimedOut(uri, sendResponseTo))
          rateLimits.getOrElseUpdate(uri, mutable.Queue.empty).enqueue(sendResponseTo -> responseObj)
        }
      }

    case UpdateRatelimits(uri, isGlobal, timeTilReset, remainingRequestsAmount, requestLimit) =>
      if (isGlobal) {
        globalRatelimitTimeout = System.currentTimeMillis() + timeTilReset.toMillis
        timers.startSingleTimer(GlobalTimer, GlobalTimer, timeTilReset)
      } else {
        routeLimits.put(uri, requestLimit)
        remainingRequests.put(uri, remainingRequestsAmount)
        timers.startSingleTimer(uri, ResetRatelimit(uri), timeTilReset)
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
          val (sender, response) = queue.dequeue()
          sender ! response
          context.unwatch(sender)
          release(remaining - 1)
        }
      }

      val newRemaining = release(remainingRequests.getOrElse(uri, Int.MaxValue))
      remainingRequests.put(uri, newRemaining)
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

  private case object GlobalTimer

  case class WantToPass[A](route: String, ret: A)
  case class ResetRatelimit(uri: String)
  case class UpdateRatelimits(
      uri: String,
      isGlobal: Boolean,
      timeTilReset: FiniteDuration,
      remainingRequests: Int,
      requestLimit: Int
  )

  case class TimedOut(uri: String, actorRef: ActorRef)
  case class GlobalTimedOut(actorRef: ActorRef)
}
