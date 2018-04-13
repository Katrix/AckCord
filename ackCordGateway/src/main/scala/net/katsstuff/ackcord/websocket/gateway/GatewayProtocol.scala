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
package net.katsstuff.ackcord.websocket.gateway

import java.time.OffsetDateTime

import cats.Later
import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.shapes._
import io.circe.syntax._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.util.{JsonNull, JsonOption, JsonSome, JsonUndefined}

object GatewayProtocol extends DiscordProtocol {

  implicit val opCodeEncoder: Encoder[GatewayOpCode] = Encoder[Int].contramap(_.code)
  implicit val opCodeDecoder: Decoder[GatewayOpCode] =
    Decoder[Int].emap(GatewayOpCode.forCode(_).toRight("Not an opCode"))

  implicit val readyDataEncoder: Encoder[GatewayEvent.ReadyData] = deriveEncoder
  implicit val readyDataDecoder: Decoder[GatewayEvent.ReadyData] = deriveDecoder

  implicit val resumedDataEncoder: Encoder[GatewayEvent.ResumedData] = deriveEncoder
  implicit val resumedDataDecoder: Decoder[GatewayEvent.ResumedData] = deriveDecoder

  implicit val guildEmojisUpdateDataEncoder: Encoder[GatewayEvent.GuildEmojisUpdateData] = deriveEncoder
  implicit val guildEmojisUpdateDataDecoder: Decoder[GatewayEvent.GuildEmojisUpdateData] = deriveDecoder

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

  implicit val channelPinsUpdateDataEncoder: Encoder[GatewayEvent.ChannelPinsUpdateData] =
    (a: GatewayEvent.ChannelPinsUpdateData) =>
      JsonOption.removeUndefinedToObj(
        "channel_id" -> JsonSome(a.channelId.asJson),
        "timestamp"  -> a.timestamp.map(_.asJson)
    )
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
      val base = JsonOption.removeUndefined(
        Seq(
          "id"               -> JsonSome(a.id.asJson),
          "channel_id"       -> JsonSome(a.channelId.asJson),
          "content"          -> a.content.map(_.asJson),
          "timestamp"        -> a.timestamp.map(_.asJson),
          "edited_timestamp" -> a.editedTimestamp.map(_.asJson),
          "tts"              -> a.tts.map(_.asJson),
          "mention_everyone" -> a.mentionEveryone.map(_.asJson),
          "mentions"         -> a.mentions.map(_.asJson),
          "mention_roles"    -> a.mentionRoles.map(_.asJson),
          "attachments"      -> a.attachment.map(_.asJson),
          "embeds"           -> a.embeds.map(_.asJson),
          "reactions"        -> a.reactions.map(_.asJson),
          "nonce"            -> a.nonce.map(_.asJson),
          "pinned"           -> a.pinned.map(_.asJson),
          "webhook_id"       -> a.webhookId.map(_.asJson)
        )
      )

      a.author match {
        case JsonSome(user: User)             => Json.obj(base :+ "author" -> user.asJson: _*)
        case JsonSome(webhook: WebhookAuthor) => Json.obj(base :+ "author" -> webhook.asJson: _*)
        case JsonNull                         => Json.obj(base :+ "author" -> Json.Null: _*)
        case JsonUndefined                    => Json.obj(base: _*)
      }
    }

  implicit val rawPartialMessageDecoder: Decoder[GatewayEvent.RawPartialMessage] = (c: HCursor) => {
    val isWebhook = c.keys.exists(_.toSeq.contains("webhook_id"))

    for {
      id        <- c.downField("id").as[MessageId]
      channelId <- c.downField("channel_id").as[ChannelId]
      author <- {
        if (isWebhook) c.downField("author").as[JsonOption[WebhookAuthor]]
        else c.downField("author").as[JsonOption[User]]
      }
      content         <- c.downField("content").as[JsonOption[String]]
      timestamp       <- c.downField("timestamp").as[JsonOption[OffsetDateTime]]
      editedTimestamp <- c.downField("edited_timestamp").as[JsonOption[OffsetDateTime]]
      tts             <- c.downField("tts").as[JsonOption[Boolean]]
      mentionEveryone <- c.downField("mention_everyone").as[JsonOption[Boolean]]
      mentions        <- c.downField("mentions").as[JsonOption[Seq[User]]]
      mentionRoles    <- c.downField("mention_roles").as[JsonOption[Seq[RoleId]]]
      attachment      <- c.downField("attachments").as[JsonOption[Seq[Attachment]]]
      embeds          <- c.downField("embeds").as[JsonOption[Seq[ReceivedEmbed]]]
      reactions       <- c.downField("reactions").as[JsonOption[Seq[Reaction]]]
      nonce           <- c.downField("nonce").as[JsonOption[RawSnowflake]]
      pinned          <- c.downField("pinned").as[JsonOption[Boolean]]
      webhookId       <- c.downField("webhook_id").as[JsonOption[String]]
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
      JsonOption.removeUndefinedToObj(
        "op" -> JsonSome(a.op.asJson),
        "d"  -> JsonSome(a.dataEncoder(a.d.value.toTry.get)),
        "s"  -> a.s.map(_.asJson),
        "t"  -> a.t.map(_.name.asJson)
    )

  implicit val wsMessageDecoder: Decoder[GatewayMessage[_]] = (c: HCursor) => {
    val dCursor = c.downField("d")

    val op = c.get[GatewayOpCode]("op")

    //We use the apply method on the companion object here
    op.flatMap {
      case GatewayOpCode.Dispatch            => decodeDispatch(c)
      case GatewayOpCode.Heartbeat           => dCursor.as[Option[Int]].map(Heartbeat)
      case GatewayOpCode.Identify            => dCursor.as[IdentifyData].map(Identify)
      case GatewayOpCode.StatusUpdate        => dCursor.as[StatusData].map(StatusUpdate)
      case GatewayOpCode.VoiceStateUpdate    => dCursor.as[VoiceStateUpdateData].map(VoiceStateUpdate)
      case GatewayOpCode.VoiceServerPing     => dCursor.as[VoiceServerUpdateData].map(VoiceServerUpdate)
      case GatewayOpCode.Resume              => dCursor.as[ResumeData].map(Resume)
      case GatewayOpCode.Reconnect           => Right(Reconnect)
      case GatewayOpCode.RequestGuildMembers => dCursor.as[RequestGuildMembersData].map(RequestGuildMembers)
      case GatewayOpCode.InvalidSession      => dCursor.as[Boolean].map(InvalidSession)
      case GatewayOpCode.Hello               => dCursor.as[HelloData].map(Hello)
      case GatewayOpCode.HeartbeatACK        => Right(HeartbeatACK)
    }
  }

  private def decodeDispatch(c: HCursor): Decoder.Result[Dispatch[_]] = {
    val dC = c.downField("d")

    c.get[Int]("s")
      .flatMap { seq =>
        //We use the apply method on the companion object here
        def createDispatch[Data: Decoder: Encoder](
            create: Later[Decoder.Result[Data]] => ComplexGatewayEvent[Data, _]
        ): Either[DecodingFailure, Dispatch[Data]] = Right(Dispatch(seq, create(Later(dC.as[Data]))))

        c.get[String]("t")
          .flatMap {
            case "READY"                       => createDispatch(GatewayEvent.Ready)
            case "RESUMED"                     => createDispatch(GatewayEvent.Resumed)
            case "CHANNEL_CREATE"              => createDispatch(GatewayEvent.ChannelCreate)
            case "CHANNEL_UPDATE"              => createDispatch(GatewayEvent.ChannelUpdate)
            case "CHANNEL_DELETE"              => createDispatch(GatewayEvent.ChannelDelete)
            case "CHANNEL_PINS_UPDATE"         => createDispatch(GatewayEvent.ChannelPinsUpdate)
            case "GUILD_CREATE"                => createDispatch(GatewayEvent.GuildCreate)
            case "GUILD_UPDATE"                => createDispatch(GatewayEvent.GuildUpdate)
            case "GUILD_DELETE"                => createDispatch(GatewayEvent.GuildDelete)
            case "GUILD_BAN_ADD"               => createDispatch(GatewayEvent.GuildBanAdd)
            case "GUILD_BAN_REMOVE"            => createDispatch(GatewayEvent.GuildBanRemove)
            case "GUILD_EMOJIS_UPDATE"         => createDispatch(GatewayEvent.GuildEmojisUpdate)
            case "GUILD_INTEGRATIONS_UPDATE"   => createDispatch(GatewayEvent.GuildIntegrationsUpdate)
            case "GUILD_MEMBER_ADD"            => createDispatch(GatewayEvent.GuildMemberAdd)
            case "GUILD_MEMBER_REMOVE"         => createDispatch(GatewayEvent.GuildMemberRemove)
            case "GUILD_MEMBER_UPDATE"         => createDispatch(GatewayEvent.GuildMemberUpdate)
            case "GUILD_MEMBER_CHUNK"          => createDispatch(GatewayEvent.GuildMemberChunk)
            case "GUILD_ROLE_CREATE"           => createDispatch(GatewayEvent.GuildRoleCreate)
            case "GUILD_ROLE_UPDATE"           => createDispatch(GatewayEvent.GuildRoleUpdate)
            case "GUILD_ROLE_DELETE"           => createDispatch(GatewayEvent.GuildRoleDelete)
            case "MESSAGE_CREATE"              => createDispatch(GatewayEvent.MessageCreate)
            case "MESSAGE_UPDATE"              => createDispatch(GatewayEvent.MessageUpdate)
            case "MESSAGE_DELETE"              => createDispatch(GatewayEvent.MessageDelete)
            case "MESSAGE_DELETE_BULK"         => createDispatch(GatewayEvent.MessageDeleteBulk)
            case "MESSAGE_REACTION_ADD"        => createDispatch(GatewayEvent.MessageReactionAdd)
            case "MESSAGE_REACTION_REMOVE"     => createDispatch(GatewayEvent.MessageReactionRemove)
            case "MESSAGE_REACTION_REMOVE_ALL" => createDispatch(GatewayEvent.MessageReactionRemoveAll)
            case "PRESENCE_UPDATE"             => createDispatch(GatewayEvent.PresenceUpdate)
            case "TYPING_START"                => createDispatch(GatewayEvent.TypingStart)
            case "USER_UPDATE"                 => createDispatch(GatewayEvent.UserUpdate)
            case "VOICE_STATE_UPDATE"          => createDispatch(GatewayEvent.VoiceStateUpdate)
            case "VOICE_SERVER_UPDATE"         => createDispatch(GatewayEvent.VoiceServerUpdate)
            case "WEBHOOK_UPDATE"              => createDispatch(GatewayEvent.WebhookUpdate)
            case _                             => Left(DecodingFailure("Invalid message type", c.downField("t").history))
          }
      }
  }
}
