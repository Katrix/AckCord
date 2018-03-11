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
package net.katsstuff.ackcord.lavaplayer

import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.ByteString
import net.katsstuff.ackcord.network.websocket.voice.VoiceUDPHandler.{silence, SendData, SendDataBurst}
import net.katsstuff.ackcord.network.websocket.voice.VoiceWsHandler.SetSpeaking

class AudioSender(player: AudioPlayer, udpHandler: ActorRef, wsHandler: ActorRef) extends Actor {
  import AudioSender._

  implicit val system: ActorSystem = context.system

  var future: ScheduledFuture[_] = _
  var isSpeaking = false

  //We use our own scheduler as the Akka one isn't the most accurate one
  val threadScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  override def postStop(): Unit = {
    if (future != null) {
      future.cancel(false)
    }

    threadScheduler.shutdownNow()
  }

  override def receive: Receive = {
    case SendAudio =>
      if (future != null) {
        setSpeaking(true)

        val frame = player.provide()
        if (frame != null) {
          udpHandler ! SendData(ByteString.fromArray(frame.data))
        }
      } else {
        setSpeaking(false)
      }

    case StartSendAudio =>
      if (future == null) {
        setSpeaking(true)

        future = threadScheduler.scheduleAtFixedRate(() => self ! SendAudio, 20, 20, TimeUnit.MILLISECONDS)
      }

    case StopSendAudio =>
      if (future != null) {
        setSpeaking(false)

        udpHandler ! SendDataBurst(Seq.fill(5)(silence))

        future.cancel(false)
        future = null
      }
  }

  def setSpeaking(speaking: Boolean): Unit = {
    if (speaking != isSpeaking) {
      wsHandler ! SetSpeaking(speaking)
      isSpeaking = speaking
    }
  }
}
object AudioSender {
  def props(player: AudioPlayer, udpHandler: ActorRef, wsHandler: ActorRef): Props =
    Props(new AudioSender(player, udpHandler, wsHandler)).withDispatcher("akka.io.pinned-dispatcher")
  case object SendAudio
  case object StartSendAudio
  case object StopSendAudio
}
