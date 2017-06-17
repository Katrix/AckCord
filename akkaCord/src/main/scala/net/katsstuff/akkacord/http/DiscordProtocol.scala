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
package net.katsstuff.akkacord.http

import java.time.{Instant, OffsetDateTime}

import scala.util.Try

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.auto._
import io.circe.syntax._
import io.circe._
import net.katsstuff.akkacord.data._
import shapeless._
import shapeless.tag._

trait DiscordProtocol {

  implicit val config: Configuration = Configuration.default.withSnakeCaseKeys.withDefaults

  implicit val channelTypeEncoder: Encoder[ChannelType] = Encoder[String].contramap(ChannelType.nameFor)
  implicit val channelTypeDecoder: Decoder[ChannelType] = Decoder[String].emap(ChannelType.forName(_).toRight("Not a valid channel type"))

  implicit val permissionValueTypeEncoder: Encoder[PermissionValueType] = Encoder[String].contramap(PermissionValueType.nameOf)
  implicit val permissionValueTypeDecoder: Decoder[PermissionValueType] =
    Decoder[String].emap(PermissionValueType.forName(_).toRight("Not a permission value type"))

  implicit val presenceStatusEncoder: Encoder[PresenceStatus] = Encoder[String].contramap(PresenceStatus.nameOf)
  implicit val presenceStatusDecoder: Decoder[PresenceStatus] = Decoder[String].emap(PresenceStatus.forName(_).toRight("Not a presence status"))

  implicit val snowflakeEncoder: Encoder[Snowflake] = Encoder[String].contramap(_.content)
  implicit val snowflakeDecoder: Decoder[Snowflake] = Decoder[String].emap(s => Right(Snowflake(s)))

  def snowflakeTagEncoder[A]: Encoder[Snowflake @@ A] = snowflakeEncoder.contramap(identity)
  def snowflakeTagDecoder[A]: Decoder[Snowflake @@ A] = snowflakeDecoder.emap(s => Right(tag[A](s)))

  implicit val guildIdEncoder:       Encoder[GuildId]       = snowflakeTagEncoder
  implicit val channelIdEncoder:     Encoder[ChannelId]     = snowflakeTagEncoder
  implicit val messageIdEncoder:     Encoder[MessageId]     = snowflakeTagEncoder
  implicit val userIdEncoder:        Encoder[UserId]        = snowflakeTagEncoder
  implicit val roleIdEncoder:        Encoder[RoleId]        = snowflakeTagEncoder
  implicit val emojiIdEncoder:       Encoder[EmojiId]       = snowflakeTagEncoder
  implicit val integrationIdEncoder: Encoder[IntegrationId] = snowflakeTagEncoder

  implicit val guildIdDecoder:       Decoder[GuildId]       = snowflakeTagDecoder
  implicit val channelIdDecoder:     Decoder[ChannelId]     = snowflakeTagDecoder
  implicit val messageIdDecoder:     Decoder[MessageId]     = snowflakeTagDecoder
  implicit val userIdDecoder:        Decoder[UserId]        = snowflakeTagDecoder
  implicit val roleIdDecoder:        Decoder[RoleId]        = snowflakeTagDecoder
  implicit val emojiIdDecoder:       Decoder[EmojiId]       = snowflakeTagDecoder
  implicit val integrationIdDecoder: Decoder[IntegrationId] = snowflakeTagDecoder

  implicit val instantEncoder: Encoder[Instant] = Encoder[Long].contramap(_.getEpochSecond)
  implicit val instantDecoder: Decoder[Instant] = Decoder[Long].emapTry(l => Try(Instant.ofEpochSecond(l)))

  implicit val rawDmChannelEncoder: Encoder[RawDMChannel] = deriveEncoder
  implicit val rawDmChannelDecoder: Decoder[RawDMChannel] = deriveDecoder

  implicit val rawGuildChannelEncoder: Encoder[RawGuildChannel] = deriveEncoder
  implicit val rawGuildChannelDecoder: Decoder[RawGuildChannel] = deriveDecoder

  implicit val rawChannelEncoder: Encoder[RawChannel] = deriveEncoder
  implicit val rawChannelDecoder: Decoder[RawChannel] = deriveDecoder

  implicit val rawGuildEncoder: Encoder[RawGuild] = deriveEncoder
  implicit val rawGuildDecoder: Decoder[RawGuild] = deriveDecoder

  implicit val rawUnavailableGuildEncoder: Encoder[RawUnavailableGuild] = deriveEncoder
  implicit val rawUnavailableGuildDecoder: Decoder[RawUnavailableGuild] = deriveDecoder

  implicit val permissionEncoder: Encoder[Permission] = Encoder[Int].contramap(_.int)
  implicit val permissionDecoder: Decoder[Permission] = Decoder[Int].emap(i => Right(Permission.fromInt(i)))

  implicit val userEncoder: Encoder[User] = deriveEncoder
  implicit val userDecoder: Decoder[User] = deriveDecoder

  implicit val webhookAuthorEncoder: Encoder[WebhookAuthor] = deriveEncoder
  implicit val webhookAuthorDecoder: Decoder[WebhookAuthor] = deriveDecoder

  implicit val roleEncoder: Encoder[Role] = deriveEncoder
  implicit val roleDecoder: Decoder[Role] = deriveDecoder

  implicit val rawGuildMemberEncoder: Encoder[RawGuildMember] = deriveEncoder
  implicit val rawGuildMemberDecoder: Decoder[RawGuildMember] = deriveDecoder

  implicit val attachementEncoder: Encoder[Attachment] = deriveEncoder
  implicit val attachementDecoder: Decoder[Attachment] = deriveDecoder

  implicit val embedEncoder: Encoder[ReceivedEmbed] = deriveEncoder
  implicit val embedDecoder: Decoder[ReceivedEmbed] = deriveDecoder

  implicit val reactionEncoder: Encoder[Reaction] = deriveEncoder
  implicit val reactionDecoder: Decoder[Reaction] = deriveDecoder

  implicit val rawMessageEncoder: Encoder[RawMessage] = (a: RawMessage) => {
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
      case user: User             => Json.obj(base :+ "author" -> user.asJson: _*)
      case webhook: WebhookAuthor => Json.obj(base :+ "author" -> webhook.asJson: _*)
    }
  }
  implicit val rawMessageDecoder: Decoder[RawMessage] = (c: HCursor) => {
    val isWebhook = c.fields.exists(_.contains("webhook_id"))

    for {
      id              <- c.downField("id").as[MessageId]
      channelId       <- c.downField("channel_id").as[ChannelId]
      author          <- if (isWebhook) c.downField("author").as[WebhookAuthor] else c.downField("author").as[User]
      content         <- c.downField("content").as[String]
      timestamp       <- c.downField("timestamp").as[OffsetDateTime]
      editedTimestamp <- c.downField("edited_timestamp").as[Option[OffsetDateTime]]
      tts             <- c.downField("tts").as[Boolean]
      mentionEveryone <- c.downField("mention_everyone").as[Boolean]
      mentions        <- c.downField("mentions").as[Seq[User]]
      mentionRoles    <- c.downField("mention_roles").as[Seq[RoleId]]
      attachment      <- c.downField("attachments").as[Seq[Attachment]]
      embeds          <- c.downField("embeds").as[Seq[ReceivedEmbed]]
      reactions       <- c.downField("reactions").as[Option[Seq[Reaction]]]
      nonce           <- c.downField("nonce").as[Option[Snowflake]]
      pinned          <- c.downField("pinned").as[Boolean]
      webhookId       <- c.downField("webhook_id").as[Option[String]]
    } yield
      RawMessage(
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

  implicit val offsetDateTimeEncoder: Encoder[OffsetDateTime] = Encoder[String].contramap(_.toString)
  implicit val offsetDateTimeDecoder: Decoder[OffsetDateTime] = Decoder[String].emapTry(s => Try(OffsetDateTime.parse(s)))

  implicit val voiceStateEncoder: Encoder[VoiceState] = deriveEncoder
  implicit val voiceStateDecoder: Decoder[VoiceState] = deriveDecoder

  implicit val inviteEncoder: Encoder[Invite] = deriveEncoder
  implicit val inviteDecoder: Decoder[Invite] = deriveDecoder

  implicit val guildEmbedEncoder: Encoder[GuildEmbed] = deriveEncoder
  implicit val guildEmbedDecoder: Decoder[GuildEmbed] = deriveDecoder

  implicit val outgoingEmbedEncoder: Encoder[OutgoingEmbed] = deriveEncoder
  implicit val outgoingEmbedDecoder: Decoder[OutgoingEmbed] = deriveDecoder

  implicit val integrationAccountEncoder: Encoder[IntegrationAccount] = deriveEncoder
  implicit val integrationAccountDecoder: Decoder[IntegrationAccount] = deriveDecoder

  implicit val integrationEncoder: Encoder[Integration] = deriveEncoder
  implicit val integrationDecoder: Decoder[Integration] = deriveDecoder

  implicit val voiceRegionEncoder: Encoder[VoiceRegion] = deriveEncoder
  implicit val voiceRegionDecoder: Decoder[VoiceRegion] = deriveDecoder
}
object DiscordProtocol extends DiscordProtocol
