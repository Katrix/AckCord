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

package ackcord.lavaplayer

import scala.concurrent.duration._

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, TimerGraphStageLogicWithLogging}
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.util.ByteString
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer

class LavaplayerSource(player: AudioPlayer) extends GraphStage[SourceShape[ByteString]] {
  val out: Outlet[ByteString] = Outlet("LavaplayerSource.out")

  override def shape: SourceShape[ByteString] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogicWithLogging(shape) with OutHandler {
      override def onPull(): Unit =
        tryPushFrame()

      override protected def onTimer(timerKey: Any): Unit = timerKey match {
        case "RetryProvide" => tryPushFrame()
      }

      def tryPushFrame(): Unit = {
        val frame = player.provide()
        if (frame != null) {
          push(out, ByteString.fromArray(frame.getData))
          //log.debug("Sending data")
        } else {
          //log.debug("Scheduling attempt to provide frame")
          scheduleOnce("RetryProvide", 20.millis)
        }
      }

      setHandler(out, this)
    }
}
object LavaplayerSource {

  def source(player: AudioPlayer): Source[ByteString, NotUsed] =
    Source.fromGraph(new LavaplayerSource(player))
}
