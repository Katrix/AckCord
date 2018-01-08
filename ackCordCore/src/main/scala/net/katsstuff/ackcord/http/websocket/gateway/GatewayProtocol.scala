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
package net.katsstuff.ackcord.http.websocket.gateway

import java.time.OffsetDateTime

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.shapes._
import io.circe.syntax._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http._

object GatewayProtocol extends DiscordProtocol {

  implicit val opCodeEncoder: Encoder[GatewayOpCode] = Encoder[Int].contramap(_.code)
  implicit val opCodeDecoder: Decoder[GatewayOpCode] =
    Decoder[Int].emap(GatewayOpCode.forCode(_).toRight("Not an opCode"))

  implicit val readyDataEncoder: Encoder[GatewayEvent.ReadyData] = deriveEncoder
  implicit val readyDataDecoder: Decoder[GatewayEvent.ReadyData] = deriveDecoder

  implicit val resumedDataEncoder: Encoder[GatewayEvent.ResumedData] = deriveEncoder
  implicit val resumedDataDecoder: Decoder[GatewayEvent.ResumedData] = deriveDecoder

  implicit val guildEmojisUpdateDataEncoder: Encoder[GatewayEvent.GuildEmojisUpdateData] = {
    import io.circe.generic.extras.auto._
    deriveEncoder
  }
  implicit val guildEmojisUpdateDataDecoder: Decoder[GatewayEvent.GuildEmojisUpdateData] = {
    import io.circe.generic.extras.auto._
    deriveDecoder
  }

  implicit val guildIntegrationsUpdateDataEncoder: Encoder[GatewayEvent.GuildIntegrationsUpdateData] = deriveEncoder
  implicit val guildIntegrationsUpdateDataDecoder: Decoder[GatewayEvent.GuildIntegrationsUpdateData] = deriveDecoder

  implicit val guildMemberRemoveDataEncoder: Encoder[GatewayEvent.GuildMemberRemoveData] = deriveEncoder
  implicit val guildMemberRemoveDataDecoder: Decoder[GatewayEvent.GuildMemberRemoveData] = deriveDecoder

  implicit val guildMemberUpdateDataEncoder: Encoder[GatewayEvent.GuildMemberUpdateData] = deriveEncoder
  implicit val guildMemberUpdateDataDecoder: Decoder[GatewayEvent.GuildMemberUpdateData] = deriveDecoder

  implicit val guildMemberChunkDataEncoder: Encoder[GatewayEvent.GuildMemberChunkData] = deriveEncoder
  implicit val guildMemberChunkDataDecoder: Decoder[GatewayEvent.GuildMemberChunkData] = deriveDecoder

  implicit val guildRoleModifyDataEncoder: Encoder[GatewayEvent.GuildRoleModifyData] = deriveEncoder
  implicit val guildRoleModifyDataDecoder: Decoder[GatewayEvent.GuildRoleModifyData] = deriveDecoder

  implicit val guildRoleDeleteDataEncoder: Encoder[GatewayEvent.GuildRoleDeleteData] = deriveEncoder
  implicit val guildRoleDeleteDataDecoder: Decoder[GatewayEvent.GuildRoleDeleteData] = deriveDecoder

  implicit val messageDeleteDataEncoder: Encoder[GatewayEvent.MessageDeleteData] = deriveEncoder
  implicit val messageDeleteDataDecoder: Decoder[GatewayEvent.MessageDeleteData] = deriveDecoder

  implicit val messageDeleteBulkDataEncoder: Encoder[GatewayEvent.MessageDeleteBulkData] = deriveEncoder
  implicit val messageDeleteBulkDataDecoder: Decoder[GatewayEvent.MessageDeleteBulkData] = deriveDecoder

  implicit val presenceUpdateDataEncoder: Encoder[GatewayEvent.PresenceUpdateData] = {
    import io.circe.generic.extras.auto._
    deriveEncoder
  }
  implicit val presenceUpdateDataDecoder: Decoder[GatewayEvent.PresenceUpdateData] = {
    import io.circe.generic.extras.auto._
    deriveDecoder
  }

  implicit val typingStartDataEncoder: Encoder[GatewayEvent.TypingStartData] = deriveEncoder
  implicit val typingStartDataDecoder: Decoder[GatewayEvent.TypingStartData] = deriveDecoder

  implicit val voiceServerUpdateDataEncoder: Encoder[VoiceServerUpdateData] = deriveEncoder
  implicit val voiceServerUpdateDataDecoder: Decoder[VoiceServerUpdateData] = deriveDecoder

  implicit val identifyObjectEncoder: Encoder[IdentifyData] = deriveEncoder
  implicit val identifyObjectDecoder: Decoder[IdentifyData] = deriveDecoder

  implicit val statusDataEncoder: Encoder[StatusData] = {
    import io.circe.generic.extras.auto._
    deriveEncoder
  }
  implicit val statusDataDecoder: Decoder[StatusData] = {
    import io.circe.generic.extras.auto._
    deriveDecoder
  }

  implicit val resumeDataEncoder: Encoder[ResumeData] = deriveEncoder
  implicit val resumeDataDecoder: Decoder[ResumeData] = deriveDecoder

  implicit val requestGuildMembersDataEncoder: Encoder[RequestGuildMembersData] = deriveEncoder
  implicit val requestGuildMembersDataDecoder: Decoder[RequestGuildMembersData] = deriveDecoder

  implicit val helloDataEncoder: Encoder[HelloData] = deriveEncoder
  implicit val helloDataDecoder: Decoder[HelloData] = deriveDecoder

  implicit val voiceStateUpdateDataEncoder: Encoder[VoiceStateUpdateData] = deriveEncoder
  implicit val voiceStateUpdateDataDecoder: Decoder[VoiceStateUpdateData] = deriveDecoder

  implicit val rawGuildMemberWithGuildEncoder: Encoder[GatewayEvent.RawGuildMemberWithGuild] = deriveEncoder
  implicit val rawGuildMemberWithGuildDecoder: Decoder[GatewayEvent.RawGuildMemberWithGuild] = deriveDecoder

  implicit val channelPinsUpdateDataEncoder: Encoder[GatewayEvent.ChannelPinsUpdateData] = deriveEncoder
  implicit val channelPinsUpdateDataDecoder: Decoder[GatewayEvent.ChannelPinsUpdateData] = deriveDecoder

  implicit val messageEmojiEncoder: Encoder[PartialEmoji] = deriveEncoder
  implicit val messageEmojiDecoder: Decoder[PartialEmoji] = deriveDecoder

  implicit val messageReactionDataEncoder: Encoder[GatewayEvent.MessageReactionData] = deriveEncoder
  implicit val messageReactionDataDecoder: Decoder[GatewayEvent.MessageReactionData] = deriveDecoder

  implicit val messageReactionRemoveAllDataEncoder: Encoder[GatewayEvent.MessageReactionRemoveAllData] = deriveEncoder
  implicit val messageReactionRemoveAllDataDecoder: Decoder[GatewayEvent.MessageReactionRemoveAllData] = deriveDecoder

  implicit val webhookUpdateDataEncoder: Encoder[GatewayEvent.WebhookUpdateData] = deriveEncoder
  implicit val webhookUpdateDataDecoder: Decoder[GatewayEvent.WebhookUpdateData] = deriveDecoder

  implicit val rawPartialMessageEncoder: Encoder[GatewayEvent.RawPartialMessage] =
    (a: GatewayEvent.RawPartialMessage) => {
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

  implicit val rawPartialMessageDecoder: Decoder[GatewayEvent.RawPartialMessage] = (c: HCursor) => {
    val isWebhook = c.fields.exists(_.contains("webhook_id"))

    for {
      id        <- c.downField("id").as[MessageId]
      channelId <- c.downField("channel_id").as[ChannelId]
      author <- if (isWebhook) c.downField("author").as[Option[WebhookAuthor]]
      else c.downField("author").as[Option[User]]
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
      nonce           <- c.downField("nonce").as[Option[RawSnowflake]]
      pinned          <- c.downField("pinned").as[Option[Boolean]]
      webhookId       <- c.downField("webhook_id").as[Option[String]]
    } yield
      GatewayEvent.RawPartialMessage(
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

  implicit def wsMessageEncoder[D]: Encoder[GatewayMessage[D]] =
    (a: GatewayMessage[D]) =>
      Json.obj("op" -> a.op.asJson, "d" -> a.dEncoder(a.d), "s" -> a.s.asJson, "t" -> a.t.map(_.name).asJson)

  implicit val wsMessageDecoder: Decoder[GatewayMessage[_]] = (c: HCursor) => {
    val opC = c.downField("op")
    val dC  = c.downField("d")

    val op = opC.as[GatewayOpCode]

    op.flatMap {
      case GatewayOpCode.Dispatch            => decodeDispatch(c)
      case GatewayOpCode.Heartbeat           => dC.as[Option[Int]].map(Heartbeat.apply)
      case GatewayOpCode.Identify            => dC.as[IdentifyData].map(Identify.apply)
      case GatewayOpCode.StatusUpdate        => dC.as[StatusData].map(StatusUpdate.apply)
      case GatewayOpCode.VoiceStateUpdate    => dC.as[VoiceStateUpdateData].map(VoiceStateUpdate.apply)
      case GatewayOpCode.VoiceServerPing     => dC.as[VoiceServerUpdateData].map(VoiceServerUpdate.apply)
      case GatewayOpCode.Resume              => dC.as[ResumeData].map(Resume.apply)
      case GatewayOpCode.Reconnect           => Right(Reconnect)
      case GatewayOpCode.RequestGuildMembers => dC.as[RequestGuildMembersData].map(RequestGuildMembers.apply)
      case GatewayOpCode.InvalidSession      => dC.as[Boolean].map(InvalidSession.apply)
      case GatewayOpCode.Hello               => dC.as[HelloData].map(Hello.apply)
      case GatewayOpCode.HeartbeatACK        => Right(HeartbeatACK)
    }
  }

  private def decodeDispatch(c: HCursor): Decoder.Result[Dispatch[_]] = {
    val dC = c.downField("d")

    def createDispatch[Data: Decoder: Encoder](
        seq: Int,
        create: Data => ComplexGatewayEvent[Data, _]
    ): Either[DecodingFailure, Dispatch[Data]] =
      dC.as[Data].map(data => Dispatch(seq, create(data)))

    c.get[Int]("s")
      .flatMap { seq =>
        c.get[String]("t")
          .flatMap {
            case "READY"                       => createDispatch(seq, GatewayEvent.Ready)
            case "RESUMED"                     => createDispatch(seq, GatewayEvent.Resumed.apply)
            case "CHANNEL_CREATE"              => createDispatch(seq, GatewayEvent.ChannelCreate)
            case "CHANNEL_UPDATE"              => createDispatch(seq, GatewayEvent.ChannelUpdate)
            case "CHANNEL_DELETE"              => createDispatch(seq, GatewayEvent.ChannelDelete)
            case "CHANNEL_PINS_UPDATE"         => createDispatch(seq, GatewayEvent.ChannelPinsUpdate)
            case "GUILD_CREATE"                => createDispatch(seq, GatewayEvent.GuildCreate)
            case "GUILD_UPDATE"                => createDispatch(seq, GatewayEvent.GuildUpdate)
            case "GUILD_DELETE"                => createDispatch(seq, GatewayEvent.GuildDelete)
            case "GUILD_BAN_ADD"               => createDispatch(seq, GatewayEvent.GuildBanAdd)
            case "GUILD_BAN_REMOVE"            => createDispatch(seq, GatewayEvent.GuildBanRemove)
            case "GUILD_EMOJIS_UPDATE"         => createDispatch(seq, GatewayEvent.GuildEmojisUpdate)
            case "GUILD_INTEGRATIONS_UPDATE"   => createDispatch(seq, GatewayEvent.GuildIntegrationsUpdate)
            case "GUILD_MEMBER_ADD"            => createDispatch(seq, GatewayEvent.GuildMemberAdd)
            case "GUILD_MEMBER_REMOVE"         => createDispatch(seq, GatewayEvent.GuildMemberRemove)
            case "GUILD_MEMBER_UPDATE"         => createDispatch(seq, GatewayEvent.GuildMemberUpdate)
            case "GUILD_MEMBER_CHUNK"          => createDispatch(seq, GatewayEvent.GuildMemberChunk)
            case "GUILD_ROLE_CREATE"           => createDispatch(seq, GatewayEvent.GuildRoleCreate)
            case "GUILD_ROLE_UPDATE"           => createDispatch(seq, GatewayEvent.GuildRoleUpdate)
            case "GUILD_ROLE_DELETE"           => createDispatch(seq, GatewayEvent.GuildRoleDelete)
            case "MESSAGE_CREATE"              => createDispatch(seq, GatewayEvent.MessageCreate)
            case "MESSAGE_UPDATE"              => createDispatch(seq, GatewayEvent.MessageUpdate)
            case "MESSAGE_DELETE"              => createDispatch(seq, GatewayEvent.MessageDelete)
            case "MESSAGE_DELETE_BULK"         => createDispatch(seq, GatewayEvent.MessageDeleteBulk)
            case "MESSAGE_REACTION_ADD"        => createDispatch(seq, GatewayEvent.MessageReactionAdd)
            case "MESSAGE_REACTION_REMOVE"     => createDispatch(seq, GatewayEvent.MessageReactionRemove)
            case "MESSAGE_REACTION_REMOVE_ALL" => createDispatch(seq, GatewayEvent.MessageReactionRemoveAll)
            case "PRESENCE_UPDATE"             => createDispatch(seq, GatewayEvent.PresenceUpdate)
            case "TYPING_START"                => createDispatch(seq, GatewayEvent.TypingStart)
            case "USER_UPDATE"                 => createDispatch(seq, GatewayEvent.UserUpdate)
            case "VOICE_STATE_UPDATE"          => createDispatch(seq, GatewayEvent.VoiceStateUpdate)
            case "VOICE_SERVER_UPDATE"         => createDispatch(seq, GatewayEvent.VoiceServerUpdate)
            case "WEBHOOK_UPDATE"              => createDispatch(seq, GatewayEvent.WebhookUpdate)
            case _                             => Left(DecodingFailure("Invalid message type", c.downField("t").history))
          }
      }
  }
}
