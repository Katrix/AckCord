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
package net.katsstuff.ackcord.http.websocket.voice

import akka.NotUsed
import akka.util.ByteString
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.websocket.WsMessage

sealed trait VoiceMessage[D] extends WsMessage[D, VoiceOpCode]

case class IdentifyData(serverId: Snowflake, userId: UserId, sessionId: String, token: String)
case class Identify(d: IdentifyData) extends VoiceMessage[IdentifyData] {
  override def op: VoiceOpCode = VoiceOpCode.Identify
}

case class SelectProtocolConnectionData(address: String, port: Int, mode: String)
case class SelectProtocolData(protocol: String, data: SelectProtocolConnectionData)
case class SelectProtocol(d: SelectProtocolData) extends VoiceMessage[SelectProtocolData] {
  override def op: VoiceOpCode = VoiceOpCode.SelectProtocol
}

case class ReadyObject(ssrc: Int, port: Int, modes: Seq[String], heartbeatInterval: Int)
case class Ready(d: ReadyObject) extends VoiceMessage[ReadyObject] {
  override def op: VoiceOpCode = VoiceOpCode.Ready
}

case class Heartbeat(d: Int) extends VoiceMessage[Int] {
  override def op: VoiceOpCode = VoiceOpCode.Heartbeat
}

case class SessionDescriptionData(mode: String, secretKey: ByteString)
case class SessionDescription(d: SessionDescriptionData) extends VoiceMessage[SessionDescriptionData] {
  override def op: VoiceOpCode = VoiceOpCode.SessionDescription
}

case class SpeakingData(speaking: Boolean, delay: Int, ssrc: Int, userId: Option[UserId])
case class Speaking(d: SpeakingData) extends VoiceMessage[SpeakingData] {
  override def op: VoiceOpCode = VoiceOpCode.Speaking
}

case class HeartbeatACK(d: Int) extends VoiceMessage[Int] {
  override def op: VoiceOpCode = VoiceOpCode.HeartbeatACK
}

case class ResumeData(serverId: Snowflake, sessionId: String, token: String)
case class Resume(d: ResumeData) extends VoiceMessage[ResumeData] {
  override def op: VoiceOpCode = VoiceOpCode.Resume
}

case class Hello(heartbeatInterval: Int) extends VoiceMessage[NotUsed] {
  override def op: VoiceOpCode = VoiceOpCode.Hello
  override def d:  NotUsed     = NotUsed
}

case object Resumed extends VoiceMessage[NotUsed] {
  override def op: VoiceOpCode = VoiceOpCode.Resumed
  override def d:  NotUsed     = NotUsed
}

sealed abstract case class VoiceOpCode(code: Int)
object VoiceOpCode {
  object Identify           extends VoiceOpCode(0)
  object SelectProtocol     extends VoiceOpCode(1)
  object Ready              extends VoiceOpCode(2)
  object Heartbeat          extends VoiceOpCode(3)
  object SessionDescription extends VoiceOpCode(4)
  object Speaking           extends VoiceOpCode(5)
  object HeartbeatACK       extends VoiceOpCode(6)
  object Resume             extends VoiceOpCode(7)
  object Hello              extends VoiceOpCode(8)
  object Resumed            extends VoiceOpCode(9)
  object ClientDisconnect   extends VoiceOpCode(13)

  def forCode(code: Int): Option[VoiceOpCode] = code match {
    case 0  => Some(Identify)
    case 1  => Some(SelectProtocol)
    case 2  => Some(Ready)
    case 3  => Some(Heartbeat)
    case 4  => Some(SessionDescription)
    case 5  => Some(Speaking)
    case 6  => Some(HeartbeatACK)
    case 7  => Some(Resume)
    case 8  => Some(Hello)
    case 9  => Some(Resumed)
    case 13 => Some(ClientDisconnect)
    case _  => None
  }
}
