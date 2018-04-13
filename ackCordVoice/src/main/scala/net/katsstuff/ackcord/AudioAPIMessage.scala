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
package net.katsstuff.ackcord

import akka.actor.ActorRef
import akka.util.ByteString
import net.katsstuff.ackcord.data.{RawSnowflake, UserId}
import net.katsstuff.ackcord.websocket.voice.VoiceUDPHandler.RTPHeader

/**
  * The base trait for all audio events. Note that the audio API does not
  * have any connections to any [[CacheSnapshot]]s.
  * As such you have to find the objects for the IDs yourself.
  */
sealed trait AudioAPIMessage {

  /**
    * The server id for the voice channel. For guilds this is the guild id.
    */
  def serverId: RawSnowflake

  /**
    * The client user id
    */
  def userId: UserId
}
object AudioAPIMessage {

  /**
    * Sent to the receiver when a user is speaking
    * @param speakingUserId The userId of the speaker
    * @param ssrc The ssrc of the speaker
    * @param isSpeaking If the user is speaking, or stopped speaking
    */
  case class UserSpeaking(
      speakingUserId: UserId,
      ssrc: Option[Int],
      isSpeaking: Boolean,
      delay: Option[Int],
      serverId: RawSnowflake,
      userId: UserId
  ) extends AudioAPIMessage

  /**
    * Sent to the listener when everything is ready to send voice data.
    * @param udpHandler The udp handler. Used for sending data.
    */
  case class Ready(udpHandler: ActorRef, serverId: RawSnowflake, userId: UserId) extends AudioAPIMessage

  /**
    * Sent to the data receiver when a user speaks.
    * @param data The raw data
    * @param header The RTP header. This contains the ssrc of the speaker.
    *               To get the userId of the speaker, use [[UserSpeaking]].
    */
  case class ReceivedData(data: ByteString, header: RTPHeader, serverId: RawSnowflake, userId: UserId)
      extends AudioAPIMessage
}
