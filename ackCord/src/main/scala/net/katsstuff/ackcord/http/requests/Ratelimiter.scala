/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord.http.requests

import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor.{Actor, ActorRef, Props, Status, Timers}
import akka.http.scaladsl.model.Uri

class Ratelimiter extends Actor with Timers {
  import Ratelimiter._

  private val remainingRequests = new mutable.HashMap[Uri, Int]
  private val rateLimits        = new mutable.HashMap[Uri, mutable.Queue[(ActorRef, Any)]]

  def receive: Receive = {
    case ResetRatelimit(uri) =>
      remainingRequests.put(uri, Int.MaxValue)
      releaseWaiting(uri)
    case WantToPass(uri, ret) =>
      if (remainingRequests.get(uri).forall(_ > 0)) {
        remainingRequests.put(uri, remainingRequests.getOrElse(uri, Int.MaxValue))
        sender() ! ret
      } else {
        rateLimits.getOrElseUpdate(uri, mutable.Queue.empty).enqueue((sender(), ret))
      }
    case UpdateRatelimits(uri, timeTilReset, remainingRequestsAmount) =>
      remainingRequests.put(uri, remainingRequestsAmount)
      timers.startSingleTimer(uri, ResetRatelimit(uri), timeTilReset.millis)
  }

  def releaseWaiting(uri: Uri): Unit =
    rateLimits.get(uri).foreach { queue =>
      queue.foreach(e => e._1 ! e._2)
    }

  override def postStop(): Unit =
    rateLimits.values.flatten.foreach(_._1 ! Status.Failure(new IllegalStateException("Ratelimiter stopped")))
}
object Ratelimiter {
  //TODO: Custom dispatcher
  def props: Props = Props(new Ratelimiter())

  case class WantToPass[A](uri: Uri, ret: A)
  case class ResetRatelimit(uri: Uri)
  case class UpdateRatelimits(uri: Uri, timeTilReset: Long, remainingRequests: Int)
}
