/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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

import scala.concurrent.duration._

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler, TimerGraphStageLogic}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}

//Some of this was taken from the Delay graph stage
class GlobalRatelimiter[Data, Ctx]
    extends GraphStage[FanInShape2[Request[Data, Ctx], RequestRatelimited[Ctx], Request[Data, Ctx]]] {
  val in:       Inlet[Request[Data, Ctx]]      = Inlet("GlobalRatelimiter.in")
  val answerIn: Inlet[RequestRatelimited[Ctx]] = Inlet("GlobalRatelimiter.answerIn")
  val out:      Outlet[Request[Data, Ctx]]     = Outlet("GlobalRatelimiter.out")
  override def shape = new FanInShape2(in, answerIn, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with InHandler with OutHandler {
      def timerName: String = "GlobalRateLimiter"

      private var ratelimitTimeout = 0.toLong
      private var elem: Request[Data, Ctx] = _

      def isRatelimited: Boolean = ratelimitTimeout - System.currentTimeMillis() > 0

      override def preStart(): Unit = pull(answerIn)

      def onPush(): Unit = {
        if (isRatelimited) {
          elem = grab(in)
        } else {
          push(out, grab(in))
        }
      }

      def onPull(): Unit = {
        if (!isRatelimited && elem != null) {
          push(out, elem)
          elem = null
        }

        if (!isClosed(in) && !hasBeenPulled(in) && !isRatelimited) {
          pull(in)
        }

        completeIfReady()
      }

      override def onUpstreamFinish(): Unit =
        completeIfReady()

      setHandler(in, this)
      setHandler(out, this)

      setHandler(
        answerIn,
        new InHandler {
          override def onPush(): Unit = {
            grab(answerIn) match {
              case RequestRatelimited(_, true, tilReset, _, _) if tilReset > 0.millis =>
                ratelimitTimeout = System.currentTimeMillis() + tilReset.toMillis
                scheduleOnce(timerName, tilReset)
              case _ =>
            }
            pull(answerIn)
          }
        }
      )

      def completeIfReady(): Unit = {
        if (isClosed(in)) {
          if (elem == null) completeStage()
          else emit(out, elem, () => completeStage())
        }
      }

      final override protected def onTimer(key: Any): Unit = {
        if (isAvailable(out) && elem != null) {
          push(out, elem)
          elem = null
        }
        completeIfReady()
      }
    }
}
