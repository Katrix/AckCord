/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
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
package net.katsstuff.akkacord.http.websocket

import java.time.OffsetDateTime

import akka.NotUsed
import io.circe._
import io.circe.syntax._
import io.circe.shapes._
import io.circe.generic.extras.semiauto._
import net.katsstuff.akkacord.data.{Attachment, Embed, Reaction, Snowflake, User, VoiceState, WebhookAuthor}
import net.katsstuff.akkacord.http._

object WsProtocol extends DiscordProtocol {

  implicit val opCodeEncoder: Encoder[OpCode] = (a: OpCode) => Json.fromInt(a.code)
  implicit val opCodeDecoder: Decoder[OpCode] = (c: HCursor) =>
    c.as[Int].flatMap(OpCode.forCode(_).toRight(DecodingFailure("Not an opCode", c.history)))

  implicit def wsEventEncoder[A]: Encoder[WsEvent[A]] = (a: WsEvent[A]) => Json.fromString(a.name)
  implicit def wsEventDecoder: Decoder[WsEvent[_]] =
    (c: HCursor) => c.as[String].flatMap(WsEvent.forName(_).toRight(DecodingFailure("Not an event", c.history)))

  implicit val readyDataEncoder: Encoder[WsEvent.ReadyData] = deriveEncoder
  implicit val readyDataDecoder: Decoder[WsEvent.ReadyData] = deriveDecoder

  implicit val resumedDataEncoder: Encoder[WsEvent.ResumedData] = deriveEncoder
  implicit val resumedDataDecoder: Decoder[WsEvent.ResumedData] = deriveDecoder

  implicit val guildDeleteDataEncoder: Encoder[WsEvent.GuildDeleteData] = deriveEncoder
  implicit val guildDeleteDataDecoder: Decoder[WsEvent.GuildDeleteData] = deriveDecoder

  implicit val guildEmojisUpdateDataEncoder: Encoder[WsEvent.GuildEmojisUpdateData] = {
    import io.circe.generic.extras.auto._
    deriveEncoder
  }
  implicit val guildEmojisUpdateDataDecoder: Decoder[WsEvent.GuildEmojisUpdateData] = {
    import io.circe.generic.extras.auto._
    deriveDecoder
  }

  implicit val guildIntegrationsUpdateDataEncoder: Encoder[WsEvent.GuildIntegrationsUpdateData] = deriveEncoder
  implicit val guildIntegrationsUpdateDataDecoder: Decoder[WsEvent.GuildIntegrationsUpdateData] = deriveDecoder

  implicit val guildMemberRemoveDataEncoder: Encoder[WsEvent.GuildMemberRemoveData] = deriveEncoder
  implicit val guildMemberRemoveDataDecoder: Decoder[WsEvent.GuildMemberRemoveData] = deriveDecoder

  implicit val guildMemberUpdateDataEncoder: Encoder[WsEvent.GuildMemberUpdateData] = deriveEncoder
  implicit val guildMemberUpdateDataDecoder: Decoder[WsEvent.GuildMemberUpdateData] = deriveDecoder

  implicit val guildMemberChunkDataEncoder: Encoder[WsEvent.GuildMemberChunkData] = deriveEncoder
  implicit val guildMemberChunkDataDecoder: Decoder[WsEvent.GuildMemberChunkData] = deriveDecoder

  implicit val guildRoleModifyDataEncoder: Encoder[WsEvent.GuildRoleModifyData] = deriveEncoder
  implicit val guildRoleModifyDataDecoder: Decoder[WsEvent.GuildRoleModifyData] = deriveDecoder

  implicit val guildRoleDeleteDataEncoder: Encoder[WsEvent.GuildRoleDeleteData] = deriveEncoder
  implicit val guildRoleDeleteDataDecoder: Decoder[WsEvent.GuildRoleDeleteData] = deriveDecoder

  implicit val messageDeleteDataEncoder: Encoder[WsEvent.MessageDeleteData] = deriveEncoder
  implicit val messageDeleteDataDecoder: Decoder[WsEvent.MessageDeleteData] = deriveDecoder

  implicit val messageDeleteBulkDataEncoder: Encoder[WsEvent.MessageDeleteBulkData] = deriveEncoder
  implicit val messageDeleteBulkDataDecoder: Decoder[WsEvent.MessageDeleteBulkData] = deriveDecoder

  implicit val presenceUpdateDataEncoder: Encoder[WsEvent.PresenceUpdateData] = {
    import io.circe.generic.extras.auto._
    deriveEncoder
  }
  implicit val presenceUpdateDataDecoder: Decoder[WsEvent.PresenceUpdateData] = {
    import io.circe.generic.extras.auto._
    deriveDecoder
  }

  implicit val typingStartDataEncoder: Encoder[WsEvent.TypingStartData] = deriveEncoder
  implicit val typingStartDataDecoder: Decoder[WsEvent.TypingStartData] = deriveDecoder

  implicit val voiceServerUpdateDataEncoder: Encoder[VoiceServerUpdateData] = deriveEncoder
  implicit val voiceServerUpdateDataDecoder: Decoder[VoiceServerUpdateData] = deriveDecoder

  implicit val identifyObjectEncoder: Encoder[IdentifyObject] = deriveEncoder
  implicit val identifyObjectDecoder: Decoder[IdentifyObject] = deriveDecoder

  implicit val statusDataEncoder: Encoder[StatusData] = {
    import io.circe.generic.extras.auto._
    deriveEncoder
  }
  implicit val statusDataDecoder: Decoder[StatusData] = {
    import io.circe.generic.extras.auto._
    deriveDecoder
  }

  implicit val voiceStatusDataEncoder: Encoder[VoiceStatusData] = deriveEncoder
  implicit val voiceStatusDataDecoder: Decoder[VoiceStatusData] = deriveDecoder

  implicit val resumeDataEncoder: Encoder[ResumeData] = deriveEncoder
  implicit val resumeDataDecoder: Decoder[ResumeData] = deriveDecoder

  implicit val requestGuildMembersDataEncoder: Encoder[RequestGuildMembersData] = deriveEncoder
  implicit val requestGuildMembersDataDecoder: Decoder[RequestGuildMembersData] = deriveDecoder

  implicit val helloDataEncoder: Encoder[HelloData] = deriveEncoder
  implicit val helloDataDecoder: Decoder[HelloData] = deriveDecoder

  implicit def wsMessageEncoder[Data: Encoder]: Encoder[WsMessage[Data]] =
    (a: WsMessage[Data]) => Json.obj("op" -> a.op.asJson, "d" -> a.d.asJson, "s" -> a.s.asJson, "t" -> a.t.asJson)

  implicit val rawPartialMessageEncoder: Encoder[WsEvent.RawPartialMessage] = (a: WsEvent.RawPartialMessage) => {
    val base = Seq(
      "id"               -> a.id.asJson,
      "channel_id"       -> a.channelId.asJson,
      "content"          -> a.content.asJson,
      "timestamp"        -> a.timestamp.asJson,
      "edited_timestamp" -> a.editedTimestamp.asJson,
      "tts"              -> a.tts.asJson,
      "mention_everyone" -> a.mentionEveryone.asJson,
      "mentions"         -> a.mentions.asJson,
      "mention_roles"    -> a.mentionRoles.asJson,
      "attachments"      -> a.attachment.asJson,
      "embeds"           -> a.embeds.asJson,
      "reactions"        -> a.reactions.asJson,
      "nonce"            -> a.nonce.asJson,
      "pinned"           -> a.pinned.asJson,
      "webhook_id"       -> a.webhookId.asJson
    )

    a.author match {
      case Some(user:    User)          => Json.obj(base :+ "author" -> user.asJson:    _*)
      case Some(webhook: WebhookAuthor) => Json.obj(base :+ "author" -> webhook.asJson: _*)
      case None => Json.obj(base: _*)
    }
  }

  implicit val rawPartialMessageDecoder: Decoder[WsEvent.RawPartialMessage] = (c: HCursor) => {
    val isWebhook = c.fields.exists(_.contains("webhook_id"))

    for {
      id              <- c.downField("id").as[Snowflake]
      channelId       <- c.downField("channel_id").as[Snowflake]
      author          <- if (isWebhook) c.downField("author").as[Option[WebhookAuthor]] else c.downField("author").as[Option[User]]
      content         <- c.downField("content").as[Option[String]]
      timestamp       <- c.downField("timestamp").as[Option[OffsetDateTime]]
      editedTimestamp <- c.downField("edited_timestamp").as[Option[OffsetDateTime]]
      tts             <- c.downField("tts").as[Option[Boolean]]
      mentionEveryone <- c.downField("mention_everyone").as[Option[Boolean]]
      mentions        <- c.downField("mentions").as[Option[Seq[User]]]
      mentionRoles    <- c.downField("mention_roles").as[Option[Seq[Snowflake]]]
      attachment      <- c.downField("attachments").as[Option[Seq[Attachment]]]
      embeds          <- c.downField("embeds").as[Option[Seq[Embed]]]
      reactions       <- c.downField("reactions").as[Option[Seq[Reaction]]]
      nonce           <- c.downField("nonce").as[Option[Snowflake]]
      pinned          <- c.downField("pinned").as[Option[Boolean]]
      webhookId       <- c.downField("webhook_id").as[Option[String]]
    } yield
      WsEvent.RawPartialMessage(
        id,
        channelId,
        author,
        content,
        timestamp,
        editedTimestamp,
        tts,
        mentionEveryone,
        mentions,
        mentionRoles,
        attachment,
        embeds,
        reactions,
        nonce,
        pinned,
        webhookId
      )
  }

  implicit val wsMessageDecoder = new Decoder[WsMessage[_]] {
    override def apply(c: HCursor): Decoder.Result[WsMessage[_]] = {
      val opC = c.downField("op")
      val dC  = c.downField("d")

      val op = opC.as[OpCode]

      op.flatMap {
        case OpCode.Dispatch =>
          for {
            seq   <- c.downField("s").as[Int]
            event <- c.downField("t").as[WsEvent[_]]
            data <- event match {
              case WsEvent.Ready                   => dC.as[WsEvent.ReadyData]
              case WsEvent.Resumed                 => dC.as[WsEvent.ResumedData]
              case WsEvent.ChannelCreate           => dC.as[RawChannel]
              case WsEvent.ChannelUpdate           => dC.as[RawGuildChannel]
              case WsEvent.ChannelDelete           => dC.as[RawChannel]
              case WsEvent.GuildCreate             => dC.as[RawGuild]
              case WsEvent.GuildUpdate             => dC.as[RawGuild]
              case WsEvent.GuildDelete             => dC.as[WsEvent.GuildDeleteData]
              case WsEvent.GuildBanAdd             => dC.as[WsEvent.GuildUser]
              case WsEvent.GuildBanRemove          => dC.as[WsEvent.GuildUser]
              case WsEvent.GuildEmojisUpdate       => dC.as[WsEvent.GuildEmojisUpdateData]
              case WsEvent.GuildIntegrationsUpdate => dC.as[WsEvent.GuildIntegrationsUpdateData]
              case WsEvent.GuildMemberAdd          => dC.as[WsEvent.RawGuildMemberWithGuild]
              case WsEvent.GuildMemberRemove       => dC.as[WsEvent.GuildMemberRemoveData]
              case WsEvent.GuildMemberUpdate       => dC.as[WsEvent.GuildMemberUpdateData]
              case WsEvent.GuildMemberChunk        => dC.as[WsEvent.GuildMemberChunkData]
              case WsEvent.GuildRoleCreate         => dC.as[WsEvent.GuildRoleModifyData]
              case WsEvent.GuildRoleUpdate         => dC.as[WsEvent.GuildRoleModifyData]
              case WsEvent.GuildRoleDelete         => dC.as[WsEvent.GuildRoleDeleteData]
              case WsEvent.MessageCreate           => dC.as[RawMessage]
              case WsEvent.MessageUpdate           => dC.as[WsEvent.RawPartialMessage]
              case WsEvent.MessageDelete           => dC.as[WsEvent.MessageDeleteData]
              case WsEvent.MessageDeleteBulk       => dC.as[WsEvent.MessageDeleteBulkData]
              case WsEvent.PresenceUpdate          => dC.as[WsEvent.PresenceUpdateData]
              case WsEvent.TypingStart             => dC.as[WsEvent.TypingStartData]
              //case Event.UserSettingsUpdate =>
              case WsEvent.UserUpdate        => dC.as[User]
              case WsEvent.VoiceStateUpdate  => dC.as[VoiceState]
              case WsEvent.VoiceServerUpdate => dC.as[VoiceServerUpdateData]
            }
          } yield Dispatch(seq, event, data)
        case OpCode.Heartbeat           => dC.as[Option[Int]].map(Heartbeat.apply)
        case OpCode.Identify            => dC.as[IdentifyObject].map(Identify.apply)
        case OpCode.StatusUpdate        => dC.as[StatusData].map(StatusUpdate.apply)
        case OpCode.VoiceStateUpdate    => dC.as[VoiceStatusData].map(VoiceStateUpdate.apply)
        case OpCode.VoiceServerPing     => dC.as[VoiceServerUpdateData].map(VoiceServerUpdate.apply)
        case OpCode.Resume              => dC.as[ResumeData].map(Resume.apply)
        case OpCode.Reconnect           => Right(Reconnect(NotUsed))
        case OpCode.RequestGuildMembers => dC.as[RequestGuildMembersData].map(RequestGuildMembers.apply)
        case OpCode.InvalidSession      => Right(InvalidSession(NotUsed))
        case OpCode.Hello               => dC.as[HelloData].map(Hello.apply)
        case OpCode.HeartbeatACK        => Right(HeartbeatACK(NotUsed))
      }
    }
  }
}
