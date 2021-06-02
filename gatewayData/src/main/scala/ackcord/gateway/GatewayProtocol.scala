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

import ackcord.data._
import ackcord.data.raw.{PartialRawGuildMember, RawMessageActivity}
import ackcord.util.{JsonNull, JsonOption, JsonSome, JsonUndefined}
import cats.Later
import cats.syntax.all._
import io.circe.syntax._
import io.circe.{derivation, _}
import java.time.OffsetDateTime

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

  implicit val statusDataCodec: Codec[PresenceData] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val resumeDataCodec: Codec[ResumeData] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val requestGuildMembersDataCodec: Codec[RequestGuildMembersData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val helloDataCodec: Codec[HelloData] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val voiceStateUpdateDataCodec: Codec[VoiceStateUpdateData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawGuildMemberWithGuildCodec: Codec[GatewayEvent.RawGuildMemberWithGuild] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val channelPinsUpdateDataEncoder: Encoder[GatewayEvent.ChannelPinsUpdateData] =
    (a: GatewayEvent.ChannelPinsUpdateData) =>
      JsonOption.removeUndefinedToObj(
        "guild_id"           -> JsonSome(a.guildId.asJson),
        "channel_id"         -> JsonSome(a.channelId.asJson),
        "last_pin_timestamp" -> a.lastPinTimestamp.toJson
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

  implicit val simpleApplicationCommandWithGuildCodec: Codec[GatewayEvent.ApplicationCommandWithGuildId] =
    Codec.from(
      (c: HCursor) =>
        for {
          command <- c.as[ApplicationCommand]
          guildId <- c.get[Option[GuildId]]("guild_id")
        } yield GatewayEvent.ApplicationCommandWithGuildId(command, guildId),
      (a: GatewayEvent.ApplicationCommandWithGuildId) => a.command.asJson.deepMerge(Json.obj("guild_id" := a.guildId))
    )

  implicit val integrationWithGuildIdCodec: Codec[GatewayEvent.IntegrationWithGuildId] = Codec.from(
    (c: HCursor) =>
      for {
        guildId     <- c.get[GuildId]("guild_id")
        integration <- c.as[Integration]
      } yield GatewayEvent.IntegrationWithGuildId(guildId, integration),
    (a: GatewayEvent.IntegrationWithGuildId) => a.integration.asJson.deepMerge(Json.obj("guild_id" := a.guildId))
  )

  implicit val deletedIntegrationCodec: Codec[GatewayEvent.DeletedIntegration] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawPartialMessageEncoder: Encoder[GatewayEvent.RawPartialMessage] =
    (a: GatewayEvent.RawPartialMessage) => {
      val base = JsonOption.removeUndefined(
        Seq(
          "id"               -> JsonSome(a.id.asJson),
          "channel_id"       -> JsonSome(a.channelId.asJson),
          "content"          -> a.content.toJson,
          "timestamp"        -> a.timestamp.toJson,
          "edited_timestamp" -> a.editedTimestamp.toJson,
          "tts"              -> a.tts.toJson,
          "mention_everyone" -> a.mentionEveryone.toJson,
          "mentions"         -> a.mentions.toJson,
          "mention_roles"    -> a.mentionRoles.toJson,
          "attachments"      -> a.attachment.toJson,
          "embeds"           -> a.embeds.toJson,
          "reactions"        -> a.reactions.toJson,
          "nonce"            -> a.nonce.map(_.fold(_.asJson, _.asJson)),
          "pinned"           -> a.pinned.toJson,
          "webhook_id"       -> a.webhookId.toJson
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
      member          <- c.get[JsonOption[PartialRawGuildMember]]("member")
      content         <- c.get[JsonOption[String]]("content")
      timestamp       <- c.get[JsonOption[OffsetDateTime]]("timestamp")
      editedTimestamp <- c.get[JsonOption[OffsetDateTime]]("edited_timestamp")
      tts             <- c.get[JsonOption[Boolean]]("tts")
      mentionEveryone <- c.get[JsonOption[Boolean]]("mention_everyone")
      mentions        <- c.get[JsonOption[Seq[User]]]("mentions")
      mentionRoles    <- c.get[JsonOption[Seq[RoleId]]]("mention_roles")
      mentionChannels <- c.get[JsonOption[Seq[ChannelMention]]]("mention_channels")
      attachment      <- c.get[JsonOption[Seq[Attachment]]]("attachments")
      embeds          <- c.get[JsonOption[Seq[ReceivedEmbed]]]("embeds")
      reactions       <- c.get[JsonOption[Seq[Reaction]]]("reactions")
      nonce <-
        c
          .downField("nonce")
          .as[JsonOption[Long]]
          .map(_.map(Left.apply))
          .orElse(c.get[JsonOption[String]]("nonce").map(_.map(Right.apply)))

      pinned            <- c.get[JsonOption[Boolean]]("pinned")
      webhookId         <- c.get[JsonOption[String]]("webhook_id")
      messageType       <- c.get[JsonOption[MessageType]]("message_type")
      activity          <- c.get[JsonOption[RawMessageActivity]]("activity")
      application       <- c.get[JsonOption[PartialApplication]]("application")
      applicationId     <- c.get[JsonOption[ApplicationId]]("application_id")
      messageReference  <- c.get[JsonOption[MessageReference]]("message_reference")
      flags             <- c.get[JsonOption[MessageFlags]]("flags")
      stickers          <- c.get[JsonOption[Seq[Sticker]]]("stickers")
      referencedMessage <- c.get[JsonOption[GatewayEvent.RawPartialMessage]]("referenced_message")
      interaction       <- c.get[JsonOption[MessageInteraction]]("interaction")
      components        <- c.get[JsonOption[Seq[ActionRow]]]("components")
    } yield GatewayEvent.RawPartialMessage(
      id,
      channelId,
      author,
      member,
      content,
      timestamp,
      editedTimestamp,
      tts,
      mentionEveryone,
      mentions,
      mentionRoles,
      mentionChannels,
      attachment,
      embeds,
      reactions,
      nonce,
      pinned,
      webhookId,
      messageType,
      activity,
      application,
      applicationId,
      messageReference,
      flags,
      stickers,
      referencedMessage,
      interaction,
      components
    )
  }

  implicit val wsMessageEncoder: Encoder[GatewayMessage[_]] = {
    case dispatch: Dispatch[_] => encodeDispatch(dispatch)
    case a =>
      val d = a match {
        case Heartbeat(d, _)              => JsonSome(d.asJson)
        case Identify(d)                  => JsonSome(d.asJson)
        case PresenceUpdate(d, _)         => JsonSome(d.asJson)
        case VoiceStateUpdate(d)          => JsonSome(d.asJson)
        case VoiceServerUpdate(d, _)      => JsonSome(d.asJson)
        case Resume(d, _)                 => JsonSome(d.asJson)
        case Reconnect(_)                 => JsonUndefined
        case RequestGuildMembers(d)       => JsonSome(d.asJson)
        case InvalidSession(resumable, _) => JsonSome(resumable.asJson)
        case Hello(d, _)                  => JsonSome(d.asJson)
        case HeartbeatACK(_)              => JsonUndefined
        case UnknownGatewayMessage(_, _)  => JsonUndefined
        case _ @Dispatch(_, _, _)         => sys.error("impossible")
      }

      JsonOption.removeUndefinedToObj(
        "op" -> JsonSome(a.op.asJson),
        "d"  -> d,
        "s"  -> a.s.toJson,
        "t"  -> a.t.map(_.name.asJson)
      )
  }

  def decodeWsMessage(
      decoders: EventDecoders,
      gatewayInfo: GatewayInfo,
      c: HCursor
  ): Decoder.Result[GatewayMessage[_]] = {
    val dCursor = c.downField("d")

    val op = c.get[GatewayOpCode]("op")

    //We use the apply method on the companion object here
    op.flatMap {
      case GatewayOpCode.Dispatch         => decodeDispatch(c, decoders, gatewayInfo.shardInfo)
      case GatewayOpCode.Heartbeat        => dCursor.as[Option[Int]].map(Heartbeat(_, gatewayInfo))
      case GatewayOpCode.Identify         => dCursor.as[IdentifyData].map(Identify)
      case GatewayOpCode.StatusUpdate     => dCursor.as[PresenceData].map(PresenceUpdate(_, gatewayInfo))
      case GatewayOpCode.VoiceStateUpdate => dCursor.as[VoiceStateUpdateData].map(VoiceStateUpdate)
      case GatewayOpCode.VoiceServerPing  => dCursor.as[VoiceServerUpdateData].map(VoiceServerUpdate(_, gatewayInfo))
      case GatewayOpCode.Resume           => dCursor.as[ResumeData].map(Resume(_, gatewayInfo))
      case GatewayOpCode.Reconnect        => Right(Reconnect(gatewayInfo))
      case GatewayOpCode.RequestGuildMembers =>
        dCursor.as[RequestGuildMembersData].map(RequestGuildMembers)
      case GatewayOpCode.InvalidSession  => dCursor.as[Boolean].map(InvalidSession(_, gatewayInfo))
      case GatewayOpCode.Hello           => dCursor.as[HelloData].map(Hello(_, gatewayInfo))
      case GatewayOpCode.HeartbeatACK    => Right(HeartbeatACK(gatewayInfo))
      case op @ GatewayOpCode.Unknown(_) => Right(UnknownGatewayMessage(op, gatewayInfo))
    }
  }

  private def encodeDispatch[D](dispatch: Dispatch[D]): Json = {
    JsonOption.removeUndefinedToObj(
      "op" -> JsonSome(dispatch.op.asJson),
      "d"  -> JsonSome(dispatch.event.rawData),
      "s"  -> dispatch.s.toJson,
      "t"  -> dispatch.t.map(_.name.asJson)
    )
  }

  type EventDecoder[A] = (Int, Json, ACursor, ShardInfo) => Decoder.Result[Dispatch[A]]
  type EventDecoders   = Map[String, EventDecoder[_]]
  val ackcordEventDecoders: EventDecoders = {
    //We use the apply method on the companion object here
    def createDispatch[Data: Decoder: Encoder](
        create: (Json, Later[Decoder.Result[Data]]) => GatewayEvent[Data]
    ): EventDecoder[Data] = (seq, rawJson, dataCursor, shardInfo) =>
      Right(Dispatch(seq, create(rawJson, Later(dataCursor.as[Data])), GatewayInfo(shardInfo, seq)))

    def ignored(name: String): (String, EventDecoder[Unit]) =
      name -> ((seq, rawJson, _, shardInfo) =>
        Right(Dispatch(seq, GatewayEvent.IgnoredEvent(name, rawJson, Later(Right(()))), GatewayInfo(shardInfo, seq)))
      )

    //Seperate var here to make type inference happy
    val res: Map[String, EventDecoder[_]] = Map(
      "READY" -> createDispatch(GatewayEvent.Ready),
      "RESUMED" -> ((seq: Int, rawJson: Json, _: ACursor, shardInfo: ShardInfo) =>
        Right(Dispatch(seq, GatewayEvent.Resumed(rawJson), GatewayInfo(shardInfo, seq)))
      ),
      "CHANNEL_CREATE"                -> createDispatch(GatewayEvent.ChannelCreate),
      "CHANNEL_UPDATE"                -> createDispatch(GatewayEvent.ChannelUpdate),
      "CHANNEL_DELETE"                -> createDispatch(GatewayEvent.ChannelDelete),
      "CHANNEL_PINS_UPDATE"           -> createDispatch(GatewayEvent.ChannelPinsUpdate),
      "GUILD_CREATE"                  -> createDispatch(GatewayEvent.GuildCreate),
      "GUILD_UPDATE"                  -> createDispatch(GatewayEvent.GuildUpdate),
      "GUILD_DELETE"                  -> createDispatch(GatewayEvent.GuildDelete),
      "GUILD_BAN_ADD"                 -> createDispatch(GatewayEvent.GuildBanAdd),
      "GUILD_BAN_REMOVE"              -> createDispatch(GatewayEvent.GuildBanRemove),
      "GUILD_EMOJIS_UPDATE"           -> createDispatch(GatewayEvent.GuildEmojisUpdate),
      "GUILD_INTEGRATIONS_UPDATE"     -> createDispatch(GatewayEvent.GuildIntegrationsUpdate),
      "GUILD_MEMBER_ADD"              -> createDispatch(GatewayEvent.GuildMemberAdd),
      "GUILD_MEMBER_REMOVE"           -> createDispatch(GatewayEvent.GuildMemberRemove),
      "GUILD_MEMBER_UPDATE"           -> createDispatch(GatewayEvent.GuildMemberUpdate),
      "GUILD_MEMBERS_CHUNK"           -> createDispatch(GatewayEvent.GuildMemberChunk),
      "GUILD_ROLE_CREATE"             -> createDispatch(GatewayEvent.GuildRoleCreate),
      "GUILD_ROLE_UPDATE"             -> createDispatch(GatewayEvent.GuildRoleUpdate),
      "GUILD_ROLE_DELETE"             -> createDispatch(GatewayEvent.GuildRoleDelete),
      "INVITE_CREATE"                 -> createDispatch(GatewayEvent.InviteCreate),
      "INVITE_DELETE"                 -> createDispatch(GatewayEvent.InviteDelete),
      "MESSAGE_CREATE"                -> createDispatch(GatewayEvent.MessageCreate),
      "MESSAGE_UPDATE"                -> createDispatch(GatewayEvent.MessageUpdate),
      "MESSAGE_DELETE"                -> createDispatch(GatewayEvent.MessageDelete),
      "MESSAGE_DELETE_BULK"           -> createDispatch(GatewayEvent.MessageDeleteBulk),
      "MESSAGE_REACTION_ADD"          -> createDispatch(GatewayEvent.MessageReactionAdd),
      "MESSAGE_REACTION_REMOVE"       -> createDispatch(GatewayEvent.MessageReactionRemove),
      "MESSAGE_REACTION_REMOVE_ALL"   -> createDispatch(GatewayEvent.MessageReactionRemoveAll),
      "MESSAGE_REACTION_REMOVE_EMOJI" -> createDispatch(GatewayEvent.MessageReactionRemoveEmoji),
      "PRESENCE_UPDATE"               -> createDispatch(GatewayEvent.PresenceUpdate),
      "TYPING_START"                  -> createDispatch(GatewayEvent.TypingStart),
      "USER_UPDATE"                   -> createDispatch(GatewayEvent.UserUpdate),
      "VOICE_STATE_UPDATE"            -> createDispatch(GatewayEvent.VoiceStateUpdate),
      "VOICE_SERVER_UPDATE"           -> createDispatch(GatewayEvent.VoiceServerUpdate),
      "WEBHOOKS_UPDATE"               -> createDispatch(GatewayEvent.WebhookUpdate),
      "INTERACTION_CREATE"            -> createDispatch(GatewayEvent.InteractionCreate),
      ignored("PRESENCES_REPLACE"),
      "APPLICATION_COMMAND_CREATE" -> createDispatch(GatewayEvent.ApplicationCommandCreate),
      "APPLICATION_COMMAND_UPDATE" -> createDispatch(GatewayEvent.ApplicationCommandUpdate),
      "APPLICATION_COMMAND_DELETE" -> createDispatch(GatewayEvent.ApplicationCommandDelete),
      "INTEGRATION_CREATE"         -> createDispatch(GatewayEvent.IntegrationCreate),
      "INTEGRATION_UPDATE"         -> createDispatch(GatewayEvent.IntegrationUpdate),
      "INTEGRATION_DELETE"         -> createDispatch(GatewayEvent.IntegrationDelete),
      "STAGE_INSTANCE_CREATE"      -> createDispatch(GatewayEvent.StageInstanceCreate),
      "STAGE_INSTANCE_UPDATE"      -> createDispatch(GatewayEvent.StageInstanceUpdate),
      "STAGE_INSTANCE_DELETE"      -> createDispatch(GatewayEvent.StageInstanceDelete),
      ignored("GUILD_JOIN_REQUEST_DELETE")
    )
    res
  }

  private def decodeDispatch(c: HCursor, decoders: EventDecoders, shardInfo: ShardInfo): Decoder.Result[Dispatch[_]] =
    for {
      seq <- c.get[Int]("s")
      tpe <- c.get[String]("t")
      res <- decoders.getOrElse(
        tpe,
        (_: Int, _: Json, _: ACursor, _: ShardInfo) =>
          Left(DecodingFailure(s"Unhandled gateway type $tpe", c.downField("t").history))
      )(seq, c.value, c.downField("d"), shardInfo)
    } yield res
}
