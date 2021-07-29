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
package ackcord.voice

import scala.collection.immutable

import ackcord.data._
import ackcord.util.{IntCirceEnumWithUnknown, JsonOption, JsonUndefined}
import akka.NotUsed
import akka.util.ByteString
import enumeratum.values.{IntEnum, IntEnumEntry}

/** Messages sent to the voice websocket. */
sealed trait VoiceMessage[D] {

  /** The op code for the message. */
  def op: VoiceOpCode

  /** A sequence number for the message if there is one. */
  def s: JsonOption[Int] = JsonUndefined

  /** The data for the message. */
  def d: D
}

/**
  * Data used by [[Identify]]
  * @param serverId The server id we want to connect to
  * @param userId Our user id
  * @param sessionId The session id received in [[ackcord.APIMessage.VoiceStateUpdate]]
  * @param token The token received in [[ackcord.APIMessage.VoiceServerUpdate]]
  */
case class IdentifyData(serverId: RawSnowflake, userId: UserId, sessionId: String, token: String)

/**
  * Sent by the client to inform Discord that we want to send voice data.
  * Discord responds with [[Ready]]
  */
case class Identify(d: IdentifyData) extends VoiceMessage[IdentifyData] {
  override def op: VoiceOpCode = VoiceOpCode.Identify
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
  override def op: VoiceOpCode = VoiceOpCode.SelectProtocol
}
object SelectProtocol {
  def apply(
      protocol: String,
      address: String,
      port: Int,
      mode: String
  ): SelectProtocol = SelectProtocol(SelectProtocolData(protocol, SelectProtocolConnectionData(address, port, mode)))
}

/**
  * Data of [[Ready]]
  * @param ssrc Our ssrc
  * @param port The port to connect to
  * @param ip The address to send voice data to
  * @param modes The supported modes
  */
case class ReadyData(ssrc: Int, port: Int, ip: String, modes: Seq[String])

/** Sent by Discord following [[Identify]] */
case class Ready(d: ReadyData) extends VoiceMessage[ReadyData] {
  override def op: VoiceOpCode = VoiceOpCode.Ready
}

/**
  * Sent by the client at some interval specified by [[Hello]]
  * @param d Nonce
  */
case class Heartbeat(d: Int) extends VoiceMessage[Int] {
  override def op: VoiceOpCode = VoiceOpCode.Heartbeat
}

/**
  * Data of [[SessionDescription]]
  * @param mode The mode used
  * @param secretKey The secret key used for encryption
  */
case class SessionDescriptionData(mode: String, secretKey: ByteString)

/** Sent by Discord in response to [[SelectProtocol]] */
case class SessionDescription(d: SessionDescriptionData) extends VoiceMessage[SessionDescriptionData] {
  override def op: VoiceOpCode = VoiceOpCode.SessionDescription
}

/**
  * Data of [[Speaking]]
  * @param speaking If the user is speaking
  * @param delay Delay
  * @param ssrc The ssrc of the speaking user
  * @param userId Optional user id
  */
case class SpeakingData(
    speaking: SpeakingFlag,
    delay: JsonOption[Int],
    ssrc: JsonOption[Int],
    userId: JsonOption[UserId]
)

/**
  * Sent by Discord when a user is speaking, anc client when we want to
  * set the bot as speaking. This is required before sending voice data.
  */
case class Speaking(d: SpeakingData) extends VoiceMessage[SpeakingData] {
  override def op: VoiceOpCode = VoiceOpCode.Speaking
}
object Speaking {
  def apply(
      speaking: SpeakingFlag,
      delay: JsonOption[Int],
      ssrc: JsonOption[Int],
      userId: JsonOption[UserId]
  ): Speaking =
    Speaking(SpeakingData(speaking, delay, ssrc, userId))
}

/**
  * Sent by Discord as acknowledgement of our heartbeat
  * @param d The nonce we sent
  */
case class HeartbeatACK(d: Int) extends VoiceMessage[Int] {
  override def op: VoiceOpCode = VoiceOpCode.HeartbeatACK
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
  override def op: VoiceOpCode = VoiceOpCode.Resume
}

/**
  * Data of [[Hello]]
  * @param heartbeatInterval How often to heartbeat
  */
case class HelloData(heartbeatInterval: Double)

/** Sent by Discord to tell us what heartbeat interval we should use. */
case class Hello(d: HelloData) extends VoiceMessage[HelloData] {
  override def op: VoiceOpCode = VoiceOpCode.Hello
}

/** Send by Discord when we successfully resume a connection */
case object Resumed extends VoiceMessage[NotUsed] {
  override def op: VoiceOpCode = VoiceOpCode.Resumed
  override def d: NotUsed      = NotUsed
}

/** Message for OpCode 13, should be ignored */
case object IgnoreClientDisconnect extends VoiceMessage[NotUsed] {
  override def op: VoiceOpCode = VoiceOpCode.ClientDisconnect
  override def d: NotUsed      = NotUsed
}

/** Message for unknown voice opcode */
case class UnknownVoiceMessage(op: VoiceOpCode) extends VoiceMessage[NotUsed] {
  override def d: NotUsed = NotUsed
}

/**
  * Voice opcode used by voice websocket
  * @param value The int value of the code
  */
sealed abstract class VoiceOpCode(val value: Int) extends IntEnumEntry
object VoiceOpCode extends IntEnum[VoiceOpCode] with IntCirceEnumWithUnknown[VoiceOpCode] {
  override def values: immutable.IndexedSeq[VoiceOpCode] = findValues

  object Identify            extends VoiceOpCode(0)
  object SelectProtocol      extends VoiceOpCode(1)
  object Ready               extends VoiceOpCode(2)
  object Heartbeat           extends VoiceOpCode(3)
  object SessionDescription  extends VoiceOpCode(4)
  object Speaking            extends VoiceOpCode(5)
  object HeartbeatACK        extends VoiceOpCode(6)
  object Resume              extends VoiceOpCode(7)
  object Hello               extends VoiceOpCode(8)
  object Resumed             extends VoiceOpCode(9)
  object ClientDisconnect    extends VoiceOpCode(13)
  case class Unknown(i: Int) extends VoiceOpCode(i)

  override def createUnknown(value: Int): VoiceOpCode = Unknown(value)
}
