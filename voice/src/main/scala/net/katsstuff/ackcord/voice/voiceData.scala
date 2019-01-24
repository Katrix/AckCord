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
package net.katsstuff.ackcord.voice

import akka.NotUsed
import akka.util.ByteString
import io.circe.{Encoder, Json}
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.util.{JsonOption, JsonUndefined}
import VoiceWsProtocol._

/**
  * Messages sent to the voice websocket.
  */
sealed trait VoiceMessage[D] {

  /**
    * The op code for the message.
    */
  def op: VoiceOpCode

  /**
    * A sequence number for the message if there is one.
    */
  def s: JsonOption[Int] = JsonUndefined

  /**
    * An encoder for the message.
    */
  def dataEncoder: Encoder[D]

  /**
    * The data for the message.
    */
  def d: D
}

/**
  * Data used by [[Identify]]
  * @param serverId The server id we want to connect to
  * @param userId Our user id
  * @param sessionId The session id received in [[net.katsstuff.ackcord.APIMessage.VoiceStateUpdate]]
  * @param token The token received in [[net.katsstuff.ackcord.APIMessage.VoiceServerUpdate]]
  */
case class IdentifyData(serverId: RawSnowflake, userId: UserId, sessionId: String, token: String)

/**
  * Sent by the client to inform Discord that we want to send voice data.
  * Discord responds with [[Ready]]
  */
case class Identify(d: IdentifyData) extends VoiceMessage[IdentifyData] {
  override def op: VoiceOpCode                    = VoiceOpCode.Identify
  override def dataEncoder: Encoder[IdentifyData] = Encoder[IdentifyData]
}

/**
  * Connection data used by [[SelectProtocol]]
  * @param address Our IP address discovered using ip discovery
  * @param port Our port discovered using ip discovery
  * @param mode The encryption mode, currently supports only xsalsa20_poly1305
  */
case class SelectProtocolConnectionData(address: String, port: Int, mode: String)

/**
  * Data used by [[SelectProtocol]]
  * @param protocol The protocol to use, currently only supports udp
  * @param data  The connection data
  */
case class SelectProtocolData(protocol: String, data: SelectProtocolConnectionData)

/**
  * Sent by the client when everything else is done.
  * Discord responds with [[SessionDescription]]
  */
case class SelectProtocol(d: SelectProtocolData) extends VoiceMessage[SelectProtocolData] {
  override def op: VoiceOpCode                          = VoiceOpCode.SelectProtocol
  override def dataEncoder: Encoder[SelectProtocolData] = Encoder[SelectProtocolData]
}

/**
  * Data of [[Ready]]
  * @param ssrc Our ssrc
  * @param port The port to connect to
  * @param modes The supported modes
  * @param heartbeatInterval Faulty heartbeat interval, should be ignored
  */
case class ReadyData(ssrc: Int, port: Int, modes: Seq[String], heartbeatInterval: Int)

/**
  * Sent by Discord following [[Identify]]
  */
case class Ready(d: ReadyData) extends VoiceMessage[ReadyData] {
  override def op: VoiceOpCode                 = VoiceOpCode.Ready
  override def dataEncoder: Encoder[ReadyData] = Encoder[ReadyData]
}

/**
  * Sent by the client at some interval specified by [[Hello]]
  * @param d Nonce
  */
case class Heartbeat(d: Int) extends VoiceMessage[Int] {
  override def op: VoiceOpCode           = VoiceOpCode.Heartbeat
  override def dataEncoder: Encoder[Int] = Encoder[Int]
}

/**
  * Data of [[SessionDescription]]
  * @param mode The mode used
  * @param secretKey The secret key used for encryption
  */
case class SessionDescriptionData(mode: String, secretKey: ByteString)

/**
  * Sent by Discord in response to [[SelectProtocol]]
  */
case class SessionDescription(d: SessionDescriptionData) extends VoiceMessage[SessionDescriptionData] {
  override def op: VoiceOpCode                              = VoiceOpCode.SessionDescription
  override def dataEncoder: Encoder[SessionDescriptionData] = Encoder[SessionDescriptionData]
}

/**
  * Data of [[Speaking]]
  * @param speaking If the user is speaking
  * @param delay Delay
  * @param ssrc The ssrc of the speaking user
  * @param userId Optional user id
  */
case class SpeakingData(speaking: Boolean, delay: JsonOption[Int], ssrc: JsonOption[Int], userId: JsonOption[UserId])

/**
  * Sent by Discord when a user is speaking, anc client when we want to
  * set the bot as speaking. This is required before sending voice data.
  */
case class Speaking(d: SpeakingData) extends VoiceMessage[SpeakingData] {
  override def op: VoiceOpCode                    = VoiceOpCode.Speaking
  override def dataEncoder: Encoder[SpeakingData] = Encoder[SpeakingData]
}

/**
  * Sent by Discord as acknowledgement of our heartbeat
  * @param d The nonce we sent
  */
case class HeartbeatACK(d: Int) extends VoiceMessage[Int] {
  override def op: VoiceOpCode           = VoiceOpCode.HeartbeatACK
  override def dataEncoder: Encoder[Int] = Encoder[Int]
}

/**
  * Data of [[Resume]]
  * @param serverId The server id to resume for
  * @param sessionId The session id
  * @param token The token
  */
case class ResumeData(serverId: RawSnowflake, sessionId: String, token: String)

/**
  * Sent by the client when we want to resume a connection after getting
  * disconnected.
  */
case class Resume(d: ResumeData) extends VoiceMessage[ResumeData] {
  override def op: VoiceOpCode                  = VoiceOpCode.Resume
  override def dataEncoder: Encoder[ResumeData] = Encoder[ResumeData]
}

/**
  * Sent by Discord to tell us what heartbeat interval we should use.
  */
case class Hello(heartbeatInterval: Int) extends VoiceMessage[NotUsed] {
  override def op: VoiceOpCode               = VoiceOpCode.Hello
  override def d: NotUsed                    = NotUsed
  override def dataEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
}

/**
  * Send by Discord when we successfully resume a connection
  */
case object Resumed extends VoiceMessage[NotUsed] {
  override def op: VoiceOpCode               = VoiceOpCode.Resumed
  override def d: NotUsed                    = NotUsed
  override def dataEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
}

/**
  * Message for OpCode 12, should be ignored
  */
case object IgnoreMessage12 extends VoiceMessage[NotUsed] {
  override def op: VoiceOpCode               = VoiceOpCode.Op12Ignore
  override def d: NotUsed                    = NotUsed
  override def dataEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
}

/**
  * Message for OpCode 13, should be ignored
  */
case object IgnoreClientDisconnect extends VoiceMessage[NotUsed] {
  override def op: VoiceOpCode               = VoiceOpCode.ClientDisconnect
  override def d: NotUsed                    = NotUsed
  override def dataEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
}

/**
  * Voice opcode used by voice websocket
  * @param code The int value of the code
  */
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
  object Op12Ignore         extends VoiceOpCode(12) //This should be ignored
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
    case 12 => Some(Op12Ignore)
    case 13 => Some(ClientDisconnect)
    case _  => None
  }
}
