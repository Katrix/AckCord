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
package ackcord.gateway

import java.time.OffsetDateTime

import ackcord.data._
import ackcord.data.raw.RawMessageActivity
import ackcord.util.{JsonNull, JsonOption, JsonSome, JsonUndefined}
import cats.Later
import cats.syntax.either._
import io.circe.syntax._
import io.circe.{derivation, _}

//noinspection NameBooleanParameters
object GatewayProtocol extends DiscordProtocol {

  implicit val gatewayIntentsCodec: Codec[GatewayIntents] = Codec.from(
    Decoder[Int].emap(i => Right(GatewayIntents.fromInt(i))),
    Encoder[Int].contramap(_.toInt)
  )

  implicit val readyApplicationCodec: Codec[GatewayEvent.ReadyApplication] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val readyDataCodec: Codec[GatewayEvent.ReadyData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val guildEmojisUpdateDataCodec: Codec[GatewayEvent.GuildEmojisUpdateData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val guildIntegrationsUpdateDataCodec: Codec[GatewayEvent.GuildIntegrationsUpdateData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val guildMemberRemoveDataCodec: Codec[GatewayEvent.GuildMemberRemoveData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val guildMemberUpdateDataCodec: Codec[GatewayEvent.GuildMemberUpdateData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val guildMemberChunkDataCodec: Codec[GatewayEvent.GuildMemberChunkData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val guildRoleModifyDataCodec: Codec[GatewayEvent.GuildRoleModifyData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val guildRoleDeleteDataCodec: Codec[GatewayEvent.GuildRoleDeleteData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val inviteCreateDataCodec: Codec[GatewayEvent.InviteCreateData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val inviteDeleteDataCodec: Codec[GatewayEvent.InviteDeleteData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val messageDeleteDataCodec: Codec[GatewayEvent.MessageDeleteData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val messageDeleteBulkDataCodec: Codec[GatewayEvent.MessageDeleteBulkData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val presenceUpdateDataCodec: Codec[GatewayEvent.PresenceUpdateData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val typingStartDataCodec: Codec[GatewayEvent.TypingStartData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val voiceServerUpdateDataCodec: Codec[VoiceServerUpdateData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val identifyObjectCodec: Codec[IdentifyData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val statusDataCodec: Codec[StatusData] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val resumeDataCodec: Codec[ResumeData] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val requestGuildMembersDataEncoder: Encoder[RequestGuildMembersData] = (a: RequestGuildMembersData) =>
    Json.obj(
      "guild_id"  -> a.guildId.fold(_.asJson, _.asJson),
      "query"     -> a.query.asJson,
      "limit"     -> a.limit.asJson,
      "presences" -> a.presences.asJson,
      "user_ids"  -> a.userIds.asJson
    )
  implicit val requestGuildMembersDataDecoder: Decoder[RequestGuildMembersData] = (c: HCursor) =>
    for {
      guildId <-
        c
          .get[GuildId]("guild_id")
          .fold(_ => c.get[Seq[GuildId]]("guild_id").map(Left.apply), r => Right(Right(r)))
      query     <- c.get[Option[String]]("query")
      limit     <- c.get[Int]("limit")
      presences <- c.get[Boolean]("presences")
      userIds   <- c.get[Option[Seq[UserId]]]("user_ids")
      nonce     <- c.get[Option[String]]("nonce")
    } yield RequestGuildMembersData(guildId, query, limit, presences, userIds, nonce)

  implicit val helloDataCodec: Codec[HelloData] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val voiceStateUpdateDataCodec: Codec[VoiceStateUpdateData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawGuildMemberWithGuildCodec: Codec[GatewayEvent.RawGuildMemberWithGuild] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val channelPinsUpdateDataEncoder: Encoder[GatewayEvent.ChannelPinsUpdateData] =
    (a: GatewayEvent.ChannelPinsUpdateData) =>
      JsonOption.removeUndefinedToObj(
        "channel_id" -> JsonSome(a.channelId.asJson),
        "timestamp"  -> a.timestamp.map(_.asJson)
      )
  implicit val channelPinsUpdateDataDecoder: Decoder[GatewayEvent.ChannelPinsUpdateData] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)

  implicit val messageEmojiCodec: Codec[PartialEmoji] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val messageReactionDataCodec: Codec[GatewayEvent.MessageReactionData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val messageReactionRemoveAllDataCodec: Codec[GatewayEvent.MessageReactionRemoveAllData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val messageReactionRemoveEmojiDataCodec: Codec[GatewayEvent.MessageReactionRemoveEmojiData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val webhookUpdateDataCodec: Codec[GatewayEvent.WebhookUpdateData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val userWithGuildIdCodec: Codec[GatewayEvent.UserWithGuildId] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val simpleRawInteractionCodec: Codec[GatewayEvent.SimpleRawInteraction] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

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
          "nonce"            -> a.nonce.map(_.fold(_.asJson, _.asJson)),
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
      id        <- c.get[MessageId]("id")
      channelId <- c.get[TextChannelId]("channel_id")
      author <- {
        if (isWebhook) c.get[JsonOption[WebhookAuthor]]("author")
        else c.get[JsonOption[User]]("author")
      }
      content         <- c.get[JsonOption[String]]("content")
      timestamp       <- c.get[JsonOption[OffsetDateTime]]("timestamp")
      editedTimestamp <- c.get[JsonOption[OffsetDateTime]]("edited_timestamp")
      tts             <- c.get[JsonOption[Boolean]]("tts")
      mentionEveryone <- c.get[JsonOption[Boolean]]("mention_everyone")
      mentions        <- c.get[JsonOption[Seq[User]]]("mentions")
      mentionRoles    <- c.get[JsonOption[Seq[RoleId]]]("mention_roles")
      attachment      <- c.get[JsonOption[Seq[Attachment]]]("attachments")
      embeds          <- c.get[JsonOption[Seq[ReceivedEmbed]]]("embeds")
      reactions       <- c.get[JsonOption[Seq[Reaction]]]("reactions")
      nonce <-
        c
          .downField("nonce")
          .as[JsonOption[Int]]
          .map(_.map(Left.apply))
          .orElse(c.get[JsonOption[String]]("nonce").map(_.map(Right.apply)))

      pinned            <- c.get[JsonOption[Boolean]]("pinned")
      webhookId         <- c.get[JsonOption[String]]("webhook_id")
      messageType       <- c.get[JsonOption[MessageType]]("message_type")
      activity          <- c.get[JsonOption[RawMessageActivity]]("activity")
      application       <- c.get[JsonOption[MessageApplication]]("application")
      messageReference  <- c.get[JsonOption[MessageReference]]("message_reference")
      flags             <- c.get[JsonOption[MessageFlags]]("flags")
      stickers          <- c.get[JsonOption[Seq[Sticker]]]("stickers")
      referencedMessage <- c.get[JsonOption[GatewayEvent.RawPartialMessage]]("referenced_message")
    } yield GatewayEvent.RawPartialMessage(
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
      webhookId,
      messageType,
      activity,
      application,
      messageReference,
      flags,
      stickers,
      referencedMessage
    )
  }

  implicit val wsMessageEncoder: Encoder[GatewayMessage[_]] = {
    case dispatch: Dispatch[_] => encodeDispatch(dispatch)
    case a =>
      val d = a match {
        case Heartbeat(d)              => JsonSome(d.asJson)
        case Identify(d)               => JsonSome(d.asJson)
        case StatusUpdate(d)           => JsonSome(d.asJson)
        case VoiceStateUpdate(d)       => JsonSome(d.asJson)
        case VoiceServerUpdate(d)      => JsonSome(d.asJson)
        case Resume(d)                 => JsonSome(d.asJson)
        case Reconnect                 => JsonUndefined
        case RequestGuildMembers(d)    => JsonSome(d.asJson)
        case InvalidSession(resumable) => JsonSome(resumable.asJson)
        case Hello(d)                  => JsonSome(d.asJson)
        case HeartbeatACK              => JsonUndefined
        case UnknownGatewayMessage(_)  => JsonUndefined
        case d @ Dispatch(_, _)        => sys.error("impossible")
      }

      JsonOption.removeUndefinedToObj(
        "op" -> JsonSome(a.op.asJson),
        "d"  -> d,
        "s"  -> a.s.map(_.asJson),
        "t"  -> a.t.map(_.name.asJson)
      )
  }

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
      case op @ GatewayOpCode.Unknown(_)     => Right(UnknownGatewayMessage(op))
    }
  }

  private def encodeDispatch[D](dispatch: Dispatch[D]): Json = {
    JsonOption.removeUndefinedToObj(
      "op" -> JsonSome(dispatch.op.asJson),
      "d"  -> JsonSome(dispatch.event.rawData),
      "s"  -> dispatch.s.map(_.asJson),
      "t"  -> dispatch.t.map(_.name.asJson)
    )
  }

  private def decodeDispatch(c: HCursor): Decoder.Result[Dispatch[_]] = {
    val dC = c.downField("d")

    c.get[Int]("s")
      .flatMap { seq =>
        //We use the apply method on the companion object here
        def createDispatch[Data: Decoder: Encoder](
            create: (Json, Later[Decoder.Result[Data]]) => GatewayEvent[Data]
        ): Either[DecodingFailure, Dispatch[Data]] = Right(Dispatch(seq, create(c.value, Later(dC.as[Data]))))

        c.get[String]("t")
          .flatMap {
            case "READY"                         => createDispatch(GatewayEvent.Ready)
            case "RESUMED"                       => Right(Dispatch(seq, GatewayEvent.Resumed(c.value)))
            case "CHANNEL_CREATE"                => createDispatch(GatewayEvent.ChannelCreate)
            case "CHANNEL_UPDATE"                => createDispatch(GatewayEvent.ChannelUpdate)
            case "CHANNEL_DELETE"                => createDispatch(GatewayEvent.ChannelDelete)
            case "CHANNEL_PINS_UPDATE"           => createDispatch(GatewayEvent.ChannelPinsUpdate)
            case "GUILD_CREATE"                  => createDispatch(GatewayEvent.GuildCreate)
            case "GUILD_UPDATE"                  => createDispatch(GatewayEvent.GuildUpdate)
            case "GUILD_DELETE"                  => createDispatch(GatewayEvent.GuildDelete)
            case "GUILD_BAN_ADD"                 => createDispatch(GatewayEvent.GuildBanAdd)
            case "GUILD_BAN_REMOVE"              => createDispatch(GatewayEvent.GuildBanRemove)
            case "GUILD_EMOJIS_UPDATE"           => createDispatch(GatewayEvent.GuildEmojisUpdate)
            case "GUILD_INTEGRATIONS_UPDATE"     => createDispatch(GatewayEvent.GuildIntegrationsUpdate)
            case "GUILD_MEMBER_ADD"              => createDispatch(GatewayEvent.GuildMemberAdd)
            case "GUILD_MEMBER_REMOVE"           => createDispatch(GatewayEvent.GuildMemberRemove)
            case "GUILD_MEMBER_UPDATE"           => createDispatch(GatewayEvent.GuildMemberUpdate)
            case "GUILD_MEMBER_CHUNK"            => createDispatch(GatewayEvent.GuildMemberChunk)
            case "GUILD_ROLE_CREATE"             => createDispatch(GatewayEvent.GuildRoleCreate)
            case "GUILD_ROLE_UPDATE"             => createDispatch(GatewayEvent.GuildRoleUpdate)
            case "GUILD_ROLE_DELETE"             => createDispatch(GatewayEvent.GuildRoleDelete)
            case "INVITE_CREATE"                 => createDispatch(GatewayEvent.InviteCreate)
            case "INVITE_DELETE"                 => createDispatch(GatewayEvent.InviteDelete)
            case "MESSAGE_CREATE"                => createDispatch(GatewayEvent.MessageCreate)
            case "MESSAGE_UPDATE"                => createDispatch(GatewayEvent.MessageUpdate)
            case "MESSAGE_DELETE"                => createDispatch(GatewayEvent.MessageDelete)
            case "MESSAGE_DELETE_BULK"           => createDispatch(GatewayEvent.MessageDeleteBulk)
            case "MESSAGE_REACTION_ADD"          => createDispatch(GatewayEvent.MessageReactionAdd)
            case "MESSAGE_REACTION_REMOVE"       => createDispatch(GatewayEvent.MessageReactionRemove)
            case "MESSAGE_REACTION_REMOVE_ALL"   => createDispatch(GatewayEvent.MessageReactionRemoveAll)
            case "MESSAGE_REACTION_REMOVE_EMOJI" => createDispatch(GatewayEvent.MessageReactionRemoveEmoji)
            case "PRESENCE_UPDATE"               => createDispatch(GatewayEvent.PresenceUpdate)
            case "TYPING_START"                  => createDispatch(GatewayEvent.TypingStart)
            case "USER_UPDATE"                   => createDispatch(GatewayEvent.UserUpdate)
            case "VOICE_STATE_UPDATE"            => createDispatch(GatewayEvent.VoiceStateUpdate)
            case "VOICE_SERVER_UPDATE"           => createDispatch(GatewayEvent.VoiceServerUpdate)
            case "WEBHOOK_UPDATE"                => createDispatch(GatewayEvent.WebhookUpdate)
            case "INTERACTION_CREATE"            => createDispatch(GatewayEvent.InteractionCreate)
            case s @ ("PRESENCES_REPLACE" | "APPLICATION_COMMAND_CREATE" | "APPLICATION_COMMAND_UPDATE" | "APPLICATION_COMMAND_DELETE") =>
              Right(Dispatch(seq, GatewayEvent.IgnoredEvent(s, c.value, Later(Right(())))))
            case s => Left(DecodingFailure(s"Invalid message type $s", c.downField("t").history))
          }
      }
  }
}
