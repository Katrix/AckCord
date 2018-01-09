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

import scala.concurrent.duration._

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Timers}
import akka.util.ByteString
import net.katsstuff.ackcord.http.websocket.voice.VoiceUDPHandler._
import net.katsstuff.ackcord.http.websocket.voice.VoiceWsHandler.SetSpeaking
import net.katsstuff.ackcord.lavaplayer.AudioSender.{SendAudio, StartSendAudio, StopSendAudio}

class BurstingAudioSender(player: AudioPlayer, udpHandler: ActorRef, wsHandler: ActorRef)
    extends Actor
    with ActorLogging
    with Timers {

  final val BehindLimit = -40
  final val AheadLimit  = 150

  implicit val system: ActorSystem = context.system

  var isSpeaking          = false
  var hasEnabledBurstMode = false
  var awaitingPackets     = 0

  var expectedTime: Long = 0

  override def postStop(): Unit =
    if (hasEnabledBurstMode) udpHandler ! StopBurstMode

  override def receive: Receive = {
    case DataRequest(numOfPackets) =>
      if (isSpeaking) calculateAndSendPackets(numOfPackets + awaitingPackets)
      else awaitingPackets += numOfPackets
    case SendAudio =>
      if (isSpeaking) calculateAndSendPackets(awaitingPackets)
    case StartSendAudio =>
      setSpeaking(true)
      if (!hasEnabledBurstMode) {
        udpHandler ! BeginBurstMode
        hasEnabledBurstMode = true
      }

      if (awaitingPackets != 0) {
        calculateAndSendPackets(awaitingPackets)
      }
      expectedTime = 0
    case StopSendAudio =>
      setSpeaking(false)
      udpHandler ! SendDataBurst(Seq.fill(5)(silence))
  }

  def setSpeaking(speaking: Boolean): Unit = {
    if (speaking != isSpeaking) {
      wsHandler ! SetSpeaking(speaking)
      isSpeaking = speaking
    }
  }

  def calculateAndSendPackets(num: Int): Unit = {
    //We try to keep track of how much audio we have sent time wise, and check if it's too early to send another burst
    val currentTime = System.currentTimeMillis()
    if (expectedTime == 0) expectedTime = currentTime
    val expectedDiff = expectedTime - currentTime

    if (expectedDiff < AheadLimit && expectedDiff > BehindLimit) {
      sendPackets(num)
    } else if (expectedDiff <= BehindLimit) {
      log.warning("Behind on sending data with {} ms", expectedDiff.abs)
      val extra = expectedDiff.toInt.abs / 20
      sendPackets(num + extra)
    } else {
      awaitingPackets = num
      timers.startSingleTimer("EarlyRequest", SendAudio, expectedDiff.millis)
    }
  }

  def sendPackets(num: Int): Unit = {
    val data = for {
      _ <- 0 until num
      frame = player.provide()
      if frame != null
    } yield ByteString.fromArray(frame.data)

    //It might be that we don't get as many packets as we asked for, in which case we have to handle the extra packets at some other time
    val sentNum = data.length
    val notSent = num - sentNum

    udpHandler ! SendDataBurst(data)
    awaitingPackets = notSent

    if (awaitingPackets > 0) {
      val untilRetry = if (sentNum == 0) {
        expectedTime = 0 //If we did not manage to send any packets we reset everything
        20
      } else {
        val notSentTime = notSent * 20
        expectedTime += notSentTime
        notSentTime
      }
      timers.startSingleTimer("RetryRequest", SendAudio, untilRetry.millis)
    } else {
      expectedTime += sentNum * 20
    }
  }
}
object BurstingAudioSender {
  def props(player: AudioPlayer, udpHandler: ActorRef, wsHandler: ActorRef): Props =
    Props(new BurstingAudioSender(player, udpHandler, wsHandler))
}
