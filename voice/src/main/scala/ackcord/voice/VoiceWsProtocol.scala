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

//noinspection NameBooleanParameters
object VoiceWsProtocol extends DiscordProtocol {

  implicit val speakingFlagsCodec: Codec[SpeakingFlag] = Codec.from(
    Decoder[Long].emap(i => Right(SpeakingFlag.fromLong(i))),
    Encoder[Long].contramap(identity)
  )

  implicit val identifyDataCodec: Codec[IdentifyData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val selectProtocolDataCodec: Codec[SelectProtocolData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val selectProtocolConnectionDataCodec: Codec[SelectProtocolConnectionData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val readyDataCodec: Codec[ReadyData] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val sessionDescriptionDataEncoder: Encoder[SessionDescriptionData] = (a: SessionDescriptionData) => {
    Json.obj("mode" -> a.mode.asJson, "secret_key" -> a.secretKey.toArray.asJson)
  }

  implicit val sessionDescriptionDataDecoder: Decoder[SessionDescriptionData] = (c: HCursor) => {
    for {
      mode      <- c.get[String]("mode")
      secretKey <- c.get[Seq[Int]]("secret_key")
    } yield SessionDescriptionData(mode, ByteString(secretKey.map(_.toByte): _*))
  }

  implicit val speakingDataEncoder: Encoder[SpeakingData] = (a: SpeakingData) =>
    JsonOption.removeUndefinedToObj(
      "speaking" -> JsonSome(a.speaking.asJson),
      "delay"    -> a.delay.map(_.asJson),
      "ssrc"     -> a.ssrc.map(_.asJson),
      "user_id"  -> a.userId.map(_.asJson)
    )
  implicit val speakingDataDecoder: Decoder[SpeakingData] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)

  implicit val resumeDataCodec: Codec[ResumeData] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val helloDataCodec: Codec[HelloData] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

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
        case Hello(d)               => d.asJson
        case Resumed                => Json.obj()
        case IgnoreClientDisconnect => Json.obj()
        case UnknownVoiceMessage(_) => Json.obj()
      }

      JsonOption.removeUndefinedToObj(
        "op" -> JsonSome(a.op.asJson),
        "d"  -> JsonSome(data),
        "s"  -> a.s.map(_.asJson)
      )
    }

  implicit val wsMessageDecoder: Decoder[VoiceMessage[_]] = (c: HCursor) => {
    val dCursor = c.downField("d")

    val op = c.get[VoiceOpCode]("op")

    def mkMsg[Data: Decoder, B](create: Data => B): Either[DecodingFailure, B] =
      dCursor.as[Data].map(create)

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
      case VoiceOpCode.Hello              => mkMsg(Hello)
      case tpe @ VoiceOpCode.Unknown(_)   => Right(UnknownVoiceMessage(tpe))
    }
  }
}
