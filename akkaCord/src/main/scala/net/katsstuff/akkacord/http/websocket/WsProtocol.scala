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
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.http._

object WsProtocol extends DiscordProtocol {

  implicit val opCodeEncoder: Encoder[OpCode] = Encoder[Int].contramap(_.code)
  implicit val opCodeDecoder: Decoder[OpCode] = Decoder[Int].emap(OpCode.forCode(_).toRight("Not an opCode"))

  implicit def wsEventEncoder[A]: Encoder[WsEvent[A]] = Encoder[String].contramap(_.name)
  implicit val wsEventDecoder:    Decoder[WsEvent[_]] = Decoder[String].emap(WsEvent.forName(_).toRight("Not an event"))

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
      case Some(user: User)             => Json.obj(base :+ "author" -> user.asJson: _*)
      case Some(webhook: WebhookAuthor) => Json.obj(base :+ "author" -> webhook.asJson: _*)
      case None                         => Json.obj(base: _*)
    }
  }

  implicit val rawPartialMessageDecoder: Decoder[WsEvent.RawPartialMessage] = (c: HCursor) => {
    val isWebhook = c.fields.exists(_.contains("webhook_id"))

    for {
      id              <- c.downField("id").as[MessageId]
      channelId       <- c.downField("channel_id").as[ChannelId]
      author          <- if (isWebhook) c.downField("author").as[Option[WebhookAuthor]] else c.downField("author").as[Option[User]]
      content         <- c.downField("content").as[Option[String]]
      timestamp       <- c.downField("timestamp").as[Option[OffsetDateTime]]
      editedTimestamp <- c.downField("edited_timestamp").as[Option[OffsetDateTime]]
      tts             <- c.downField("tts").as[Option[Boolean]]
      mentionEveryone <- c.downField("mention_everyone").as[Option[Boolean]]
      mentions        <- c.downField("mentions").as[Option[Seq[User]]]
      mentionRoles    <- c.downField("mention_roles").as[Option[Seq[RoleId]]]
      attachment      <- c.downField("attachments").as[Option[Seq[Attachment]]]
      embeds          <- c.downField("embeds").as[Option[Seq[ReceivedEmbed]]]
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
        case OpCode.Dispatch            => decodeDispatch(c)
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

  private def decodeDispatch(c: HCursor): Decoder.Result[Dispatch[_]] = {
    val dC = c.downField("d")

    def createDispatch[Data](seq: Int, event: WsEvent[Data]): Data => Dispatch[Data] = Dispatch(seq, event, _)

    c.downField("s")
      .as[Int]
      .flatMap { seq =>
        c.downField("t")
          .as[WsEvent[_]]
          .flatMap {
            case event @ WsEvent.Ready                   => dC.as[WsEvent.ReadyData].map(createDispatch(seq, event))
            case event @ WsEvent.Resumed                 => dC.as[WsEvent.ResumedData].map(createDispatch(seq, event))
            case event @ WsEvent.ChannelCreate           => dC.as[RawChannel].map(createDispatch(seq, event))
            case event @ WsEvent.ChannelUpdate           => dC.as[RawGuildChannel].map(createDispatch(seq, event))
            case event @ WsEvent.ChannelDelete           => dC.as[RawChannel].map(createDispatch(seq, event))
            case event @ WsEvent.GuildCreate             => dC.as[RawGuild].map(createDispatch(seq, event))
            case event @ WsEvent.GuildUpdate             => dC.as[RawGuild].map(createDispatch(seq, event))
            case event @ WsEvent.GuildDelete             => dC.as[WsEvent.GuildDeleteData].map(createDispatch(seq, event))
            case event @ WsEvent.GuildBanAdd             => dC.as[WsEvent.GuildUser].map(createDispatch(seq, event))
            case event @ WsEvent.GuildBanRemove          => dC.as[WsEvent.GuildUser].map(createDispatch(seq, event))
            case event @ WsEvent.GuildEmojisUpdate       => dC.as[WsEvent.GuildEmojisUpdateData].map(createDispatch(seq, event))
            case event @ WsEvent.GuildIntegrationsUpdate => dC.as[WsEvent.GuildIntegrationsUpdateData].map(createDispatch(seq, event))
            case event @ WsEvent.GuildMemberAdd          => dC.as[WsEvent.RawGuildMemberWithGuild].map(createDispatch(seq, event))
            case event @ WsEvent.GuildMemberRemove       => dC.as[WsEvent.GuildMemberRemoveData].map(createDispatch(seq, event))
            case event @ WsEvent.GuildMemberUpdate       => dC.as[WsEvent.GuildMemberUpdateData].map(createDispatch(seq, event))
            case event @ WsEvent.GuildMemberChunk        => dC.as[WsEvent.GuildMemberChunkData].map(createDispatch(seq, event))
            case event @ WsEvent.GuildRoleCreate         => dC.as[WsEvent.GuildRoleModifyData].map(createDispatch(seq, event))
            case event @ WsEvent.GuildRoleUpdate         => dC.as[WsEvent.GuildRoleModifyData].map(createDispatch(seq, event))
            case event @ WsEvent.GuildRoleDelete         => dC.as[WsEvent.GuildRoleDeleteData].map(createDispatch(seq, event))
            case event @ WsEvent.MessageCreate           => dC.as[RawMessage].map(createDispatch(seq, event))
            case event @ WsEvent.MessageUpdate           => dC.as[WsEvent.RawPartialMessage].map(createDispatch(seq, event))
            case event @ WsEvent.MessageDelete           => dC.as[WsEvent.MessageDeleteData].map(createDispatch(seq, event))
            case event @ WsEvent.MessageDeleteBulk       => dC.as[WsEvent.MessageDeleteBulkData].map(createDispatch(seq, event))
            case event @ WsEvent.PresenceUpdate          => dC.as[WsEvent.PresenceUpdateData].map(createDispatch(seq, event))
            case event @ WsEvent.TypingStart             => dC.as[WsEvent.TypingStartData].map(createDispatch(seq, event))
            //case Event.UserSettingsUpdate =>
            case event @ WsEvent.UserUpdate        => dC.as[User].map(createDispatch(seq, event))
            case event @ WsEvent.VoiceStateUpdate  => dC.as[VoiceState].map(createDispatch(seq, event))
            case event @ WsEvent.VoiceServerUpdate => dC.as[VoiceServerUpdateData].map(createDispatch(seq, event))
          }
      }
  }
}
