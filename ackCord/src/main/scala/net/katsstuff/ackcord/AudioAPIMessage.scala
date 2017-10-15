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
package net.katsstuff.ackcord

import akka.util.ByteString
import net.katsstuff.ackcord.data.{Snowflake, UserId}
import net.katsstuff.ackcord.http.websocket.voice.VoiceUDPHandler.RTPHeader

/**
  * The base trait for all audio events. Note that the audio API does not
  * have any connections to [[SnowflakeCache]] or any [[net.katsstuff.ackcord.data.CacheSnapshot]].
  * As such you have to find the objects for the IDs yourself.
  */
sealed trait AudioAPIMessage {

  /**
    * The server id for the voice channel. For guilds this is the guild id.
    */
  def serverId: Snowflake

  /**
    * The client user id
    */
  def userId: UserId
}
object AudioAPIMessage {
  case class UserSpeaking(speakingUserId: UserId, isSpeaking: Boolean, delay: Option[Int], serverId: Snowflake, userId: UserId)
      extends AudioAPIMessage

  case class ReceivedData(
      data: ByteString,
      fromUserId: Option[UserId],
      header: RTPHeader,
      serverId: Snowflake,
      userId: UserId
  ) extends AudioAPIMessage

  case class FinishedSource(serverId: Snowflake, userId: UserId) extends AudioAPIMessage
}
