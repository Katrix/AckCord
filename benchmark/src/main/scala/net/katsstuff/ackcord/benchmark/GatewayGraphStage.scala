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
package net.katsstuff.ackcord.benchmark

import java.util.UUID

import scala.concurrent.duration._

import akka.http.scaladsl.coding.Deflate
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.stage.{GraphStage, InHandler, OutHandler, TimerGraphStageLogic, TimerGraphStageLogicWithLogging}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import akka.util.ByteString
import io.circe.parser
import io.circe.syntax._
import net.katsstuff.ackcord.CoreClientSettings
import net.katsstuff.ackcord.benchmark.GatewayGraphStage.{ExpectingIdentify, First, NormalRun}
import net.katsstuff.ackcord.data.{UnavailableGuild, User}
import net.katsstuff.ackcord.http.websocket.gateway.GatewayEvent.{Ready, ReadyData, Resumed, ResumedData}
import net.katsstuff.ackcord.http.websocket.gateway.GatewayProtocol._
import net.katsstuff.ackcord.http.websocket.gateway._

class GatewayGraphStage(settings: CoreClientSettings, readyUser: User, readyGuilds: Seq[UnavailableGuild])
    extends GraphStage[FanInShape2[Message, Int => Dispatch[_], Message]] {
  val in:         Inlet[Message]            = Inlet("GatewayGraphStage.in")
  val dispatchIn: Inlet[Int => Dispatch[_]] = Inlet("GatewayGraphStage.dispatchIn")

  val out: Outlet[Message] = Outlet("GatewayGraphStage.out")
  override def shape = new FanInShape2(in, dispatchIn, out)

  override def createLogic(inheritedAttributes: Attributes): TimerGraphStageLogic =
    new TimerGraphStageLogicWithLogging(shape) with InHandler with OutHandler {
      var state:     GatewayGraphStage.State = First
      var prevSeq:   Int                     = -1
      var seq:       Int                     = -1
      var sessionId: String                  = _
      val compress = false

      val HeartbeatFailTimerKey: String = "HeartbeatTimer"

      val heartbeatInterval = 40000

      override def onPush(): Unit = {
        val elem = parser.parse(grab(in).asTextMessage.getStrictText).flatMap(_.as[GatewayMessage[_]]).toTry.get

        elem match {
          case Heartbeat(_) =>
            //We ignore the seq
            log.debug("Received heartbeat. Sending HeartbeatACK")
            scheduleOnce(HeartbeatFailTimerKey, (heartbeatInterval * 1.25).millis)
            pushMsg(HeartbeatACK)
          case Identify(identifyData) if state == ExpectingIdentify =>
            log.info("Received Identify")
            sessionId = UUID.randomUUID().toString
            if (identifyData.token == settings.token) {
              seq = 0
              pushMsg(Dispatch(seq, Ready(ReadyData(6, readyUser, Seq.empty, readyGuilds, sessionId, Seq.empty))))
              pull(dispatchIn)
              state = NormalRun
            } else {
              log.error("Wrong Identify")
              failStage(new IllegalArgumentException("Wrong Identify"))
            }

          case Resume(resumeData) if state == ExpectingIdentify && sessionId != null =>
            log.info("Received Resume")
            if (resumeData.token == settings.token && resumeData.sessionId == sessionId) {
              pushMsg(Dispatch(seq, Resumed(ResumedData(Seq.empty))))
              if(!hasBeenPulled(dispatchIn)) pull(dispatchIn)
              state = NormalRun
            } else {
              log.error("Wrong resume")
              failStage(new IllegalArgumentException("Wrong resume"))
            }
          case _ =>
            println("Got ignored event")
        }

        if (!hasBeenPulled(in)) pull(in)
      }

      override def onPull(): Unit = {
        if (state == First) {
          log.info("Sending hello")
          pushMsg(Hello(HelloData(heartbeatInterval, Seq.empty)))
          scheduleOnce(HeartbeatFailTimerKey, (heartbeatInterval * 1.25).millis)
          state = ExpectingIdentify
        } else {
          if (!hasBeenPulled(in)) pull(in)
        }
      }

      setHandlers(in, out, this)

      setHandler(dispatchIn, new InHandler {

        override def onPush(): Unit = {
          prevSeq = seq
          seq += 1
          pushMsg(grab(dispatchIn)(seq))
          pull(dispatchIn)
        }

        override def onUpstreamFinish(): Unit = {
          log.info("Upstream finished")
          super.onUpstreamFinish()
        }

        override def onUpstreamFailure(ex: Throwable): Unit = {
          log.error(ex, "Upstream failure")
          super.onUpstreamFailure(ex)
        }
      })

      override def onUpstreamFinish(): Unit = {
        log.info("Upstream finished")
        super.onUpstreamFinish()
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        log.error(ex, "Upstream failure")
        super.onUpstreamFailure(ex)
      }

      override def onDownstreamFinish(): Unit = {
        log.info("Downstream finish")
        super.onDownstreamFinish()
      }

      def pushMsg(gatewayMsg: GatewayMessage[_]): Unit = {
        val str = gatewayMsg.asJson.noSpaces

        val msg = if (compress) BinaryMessage(Deflate.encode(ByteString.fromString(str))) else TextMessage(str)
        emit(out, msg)
      }

      override protected def onTimer(timerKey: Any): Unit =
        if (timerKey == HeartbeatFailTimerKey) {
          log.error("Did not receive heartbeat")
          failStage(new IllegalStateException("Did not receive heartbeat"))
        }
    }
}
object GatewayGraphStage {
  sealed trait State
  case object First             extends State
  case object ExpectingIdentify extends State
  case object NormalRun         extends State
}
