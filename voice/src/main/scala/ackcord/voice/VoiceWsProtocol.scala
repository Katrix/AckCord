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

import ackcord.data.DiscordProtocol
import ackcord.util.{JsonOption, JsonSome}
import akka.util.ByteString
import io.circe.syntax._
import io.circe.{derivation, _}

object VoiceWsProtocol extends DiscordProtocol {

  @deprecated("Prefer the instance provided in the companion object instead", since = "0.14.0")
  val opCodeEncoder: Encoder[VoiceOpCode] = Encoder[VoiceOpCode]
  @deprecated("Prefer the instance provided in the companion object instead", since = "0.14.0")
  val opCodeDecoder: Decoder[VoiceOpCode] = Decoder[VoiceOpCode]

  implicit val identifyDataEncoder: Encoder[IdentifyData] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val identifyDataDecoder: Decoder[IdentifyData] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val selectProtocolDataEncoder: Encoder[SelectProtocolData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val selectProtocolDataDecoder: Decoder[SelectProtocolData] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val selectProtocolConnectionDataEncoder: Encoder[SelectProtocolConnectionData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val selectProtocolConnectionDataDecoder: Decoder[SelectProtocolConnectionData] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val readyDataEncoder: Encoder[ReadyData] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val readyDataDecoder: Decoder[ReadyData] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val sessionDescriptionDataEncoder: Encoder[SessionDescriptionData] = (a: SessionDescriptionData) => {
    Json.obj("mode" -> a.mode.asJson, "secret_key" -> a.secretKey.toArray.asJson)
  }

  implicit val sessionDescriptionDataDecoder: Decoder[SessionDescriptionData] = (c: HCursor) => {
    for {
      mode      <- c.get[String]("mode").right
      secretKey <- c.get[Seq[Int]]("secret_key").right
    } yield SessionDescriptionData(mode, ByteString(secretKey.map(_.toByte): _*))
  }

  implicit val speakingDataEncoder: Encoder[SpeakingData] = (a: SpeakingData) =>
    JsonOption.removeUndefinedToObj(
      "speaking" -> JsonSome(a.speaking.asJson),
      "delay"    -> a.delay.map(_.asJson),
      "ssrc"     -> a.ssrc.map(_.asJson),
      "user_id"  -> a.userId.map(_.asJson)
  )
  implicit val speakingDataDecoder: Decoder[SpeakingData] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val resumeDataEncoder: Encoder[ResumeData] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val resumeDataDecoder: Decoder[ResumeData] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit def wsMessageEncoder[Data]: Encoder[VoiceMessage[Data]] =
    (a: VoiceMessage[Data]) => {
      val data = a match {
        case Identify(d)            => d.asJson
        case SelectProtocol(d)      => d.asJson
        case Ready(d)               => d.asJson
        case Heartbeat(d)           => d.asJson
        case SessionDescription(d)  => d.asJson
        case Speaking(d)            => d.asJson
        case HeartbeatACK(d)        => d.asJson
        case Resume(d)              => d.asJson
        case Hello(_)               => Json.obj()
        case Resumed                => Json.obj()
        case IgnoreMessage12        => Json.obj()
        case IgnoreClientDisconnect => Json.obj()
      }

      JsonOption.removeUndefinedToObj(
        "op" -> JsonSome(a.op.asJson),
        "d"  -> JsonSome(data),
        "s"  -> a.s.map(_.asJson)
      )
    }

  implicit val wsMessageDecoder: Decoder[VoiceMessage[_]] = (c: HCursor) => {
    c.get[Int]("heartbeat_interval").right.map(Hello).left.flatMap { _ =>
      val dCursor = c.downField("d")

      val op = c.get[VoiceOpCode]("op").right

      def mkMsg[Data: Decoder, B](create: Data => B): Either[DecodingFailure, B] =
        dCursor.as[Data].right.map(create)

      //We use the apply method on the companion object here
      op.flatMap {
        case VoiceOpCode.Identify           => mkMsg(Identify)
        case VoiceOpCode.SelectProtocol     => mkMsg(SelectProtocol.apply)
        case VoiceOpCode.Ready              => mkMsg(Ready)
        case VoiceOpCode.Heartbeat          => mkMsg(Heartbeat)
        case VoiceOpCode.SessionDescription => mkMsg(SessionDescription)
        case VoiceOpCode.Speaking           => mkMsg(Speaking.apply)
        case VoiceOpCode.HeartbeatACK       => mkMsg(HeartbeatACK)
        case VoiceOpCode.Resume             => mkMsg(Resume)
        case VoiceOpCode.Resumed            => Right(Resumed)
        case VoiceOpCode.ClientDisconnect   => Right(IgnoreClientDisconnect) //We don't know what to do with this
        case VoiceOpCode.Op12Ignore         => Right(IgnoreMessage12) //We don't know what to do with this
        case VoiceOpCode.Hello              => dCursor.downField("heartbeat_interval").as[Int].right.map(Hello)
      }
    }
  }
}
