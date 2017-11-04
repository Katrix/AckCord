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
package net.katsstuff.ackcord.http

import java.time.{Instant, OffsetDateTime}

import scala.util.Try

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.auto._
import io.circe.syntax._
import io.circe._
import net.katsstuff.ackcord.data._
import shapeless._
import shapeless.tag._

trait DiscordProtocol {

  implicit val config: Configuration = Configuration.default.withSnakeCaseKeys.withDefaults

  implicit val channelTypeEncoder: Encoder[ChannelType] = Encoder[Int].contramap(ChannelType.idFor)
  implicit val channelTypeDecoder: Decoder[ChannelType] =
    Decoder[Int].emap(ChannelType.forId(_).toRight("Not a valid channel type"))

  implicit val verificationLevelEncoder: Encoder[VerificationLevel] = Encoder[Int].contramap(VerificationLevel.idFor)
  implicit val verificationLevelDecoder: Decoder[VerificationLevel] =
    Decoder[Int].emap(VerificationLevel.forId(_).toRight("Not a valid verification level"))

  implicit val notificationLevelEncoder: Encoder[NotificationLevel] = Encoder[Int].contramap(NotificationLevel.idFor)
  implicit val notificationLevelDecoder: Decoder[NotificationLevel] =
    Decoder[Int].emap(NotificationLevel.forId(_).toRight("Not a valid notification level"))

  implicit val filterLevelEncoder: Encoder[FilterLevel] = Encoder[Int].contramap(FilterLevel.idFor)
  implicit val filterLevelDecoder: Decoder[FilterLevel] =
    Decoder[Int].emap(FilterLevel.forId(_).toRight("Not a valid filter level"))

  implicit val mfaLevelEncoder: Encoder[MFALevel] = Encoder[Int].contramap(MFALevel.idFor)
  implicit val mfaLevelDecoder: Decoder[MFALevel] =
    Decoder[Int].emap(MFALevel.forId(_).toRight("Not a valid MFA level"))

  implicit val messageTypeEncoder: Encoder[MessageType] = Encoder[Int].contramap(MessageType.idFor)
  implicit val messageTypeDecoder: Decoder[MessageType] =
    Decoder[Int].emap(MessageType.forId(_).toRight("Not a valid message type"))

  implicit val permissionValueTypeEncoder: Encoder[PermissionOverwriteType] =
    Encoder[String].contramap(PermissionOverwriteType.nameOf)
  implicit val permissionValueTypeDecoder: Decoder[PermissionOverwriteType] =
    Decoder[String].emap(PermissionOverwriteType.forName(_).toRight("Not a permission value type"))

  implicit val presenceStatusEncoder: Encoder[PresenceStatus] = Encoder[String].contramap(PresenceStatus.nameOf)
  implicit val presenceStatusDecoder: Decoder[PresenceStatus] =
    Decoder[String].emap(PresenceStatus.forName(_).toRight("Not a presence status"))

  implicit val auditLogEventDecoder: Decoder[AuditLogEvent] =
    Decoder[Int].emap(i => AuditLogEvent.fromId(i).toRight(s"No valid event for id $i"))

  implicit val snowflakeEncoder: Encoder[Snowflake] = Encoder[String].contramap(_.toString)
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

  implicit val rawChannelEncoder: Encoder[RawChannel] = deriveEncoder
  implicit val rawChannelDecoder: Decoder[RawChannel] = deriveDecoder

  implicit val rawGuildEncoder: Encoder[RawGuild] = deriveEncoder
  implicit val rawGuildDecoder: Decoder[RawGuild] = deriveDecoder

  implicit val unavailableGuildEncoder: Encoder[UnavailableGuild] = deriveEncoder
  implicit val unavailableGuildDecoder: Decoder[UnavailableGuild] = deriveDecoder

  implicit val permissionEncoder: Encoder[Permission] = Encoder[Int].contramap(_.int)
  implicit val permissionDecoder: Decoder[Permission] = Decoder[Int].emap(i => Right(Permission.fromInt(i)))

  implicit val permissionValueEncoder: Encoder[PermissionOverwrite] = deriveEncoder
  implicit val permissionValueDecoder: Decoder[PermissionOverwrite] = deriveDecoder

  implicit val userEncoder: Encoder[User] = deriveEncoder
  implicit val userDecoder: Decoder[User] = deriveDecoder

  implicit val webhookAuthorEncoder: Encoder[WebhookAuthor] = deriveEncoder
  implicit val webhookAuthorDecoder: Decoder[WebhookAuthor] = deriveDecoder

  implicit val roleEncoder: Encoder[Role] = deriveEncoder //Encoding roles is fine, decoding them is not

  implicit val rawRoleEncoder: Encoder[RawRole] = deriveEncoder
  implicit val rawRoleDecoder: Decoder[RawRole] = deriveDecoder

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
      "type"             -> a.`type`.asJson
    )

    a.author match {
      case user: User => Json.obj(base :+ "author" -> user.asJson: _*)
      case webhook: WebhookAuthor =>
        Json.obj(base ++ Seq("author" -> webhook.asJson, "webhook_id" -> webhook.id.asJson): _*)
    }
  }
  implicit val rawMessageDecoder: Decoder[RawMessage] = (c: HCursor) => {
    val isWebhook = c.fields.exists(_.contains("webhook_id"))

    for {
      id              <- c.get[MessageId]("id")
      channelId       <- c.get[ChannelId]("channel_id")
      author          <- if (isWebhook) c.get[WebhookAuthor]("author") else c.get[User]("author")
      content         <- c.get[String]("content")
      timestamp       <- c.get[OffsetDateTime]("timestamp")
      editedTimestamp <- c.get[Option[OffsetDateTime]]("edited_timestamp")
      tts             <- c.get[Boolean]("tts")
      mentionEveryone <- c.get[Boolean]("mention_everyone")
      mentions        <- c.get[Seq[User]]("mentions")
      mentionRoles    <- c.get[Seq[RoleId]]("mention_roles")
      attachment      <- c.get[Seq[Attachment]]("attachments")
      embeds          <- c.get[Seq[ReceivedEmbed]]("embeds")
      reactions       <- c.get[Option[Seq[Reaction]]]("reactions")
      nonce           <- c.get[Option[Snowflake]]("nonce")
      pinned          <- c.get[Boolean]("pinned")
      tpe             <- c.get[MessageType]("type")
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
        tpe
      )
  }

  implicit val offsetDateTimeEncoder: Encoder[OffsetDateTime] = Encoder[String].contramap(_.toString)
  implicit val offsetDateTimeDecoder: Decoder[OffsetDateTime] =
    Decoder[String].emapTry(s => Try(OffsetDateTime.parse(s)))

  implicit val voiceStateEncoder: Encoder[VoiceState] = deriveEncoder
  implicit val voiceStateDecoder: Decoder[VoiceState] = deriveDecoder

  implicit val inviteEncoder: Encoder[Invite] = deriveEncoder
  implicit val inviteDecoder: Decoder[Invite] = deriveDecoder

  implicit val inviteWithMetadataEncoder: Encoder[InviteWithMetadata] = deriveEncoder
  implicit val inviteWithMetadataDecoder: Decoder[InviteWithMetadata] = deriveDecoder

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

  implicit val guildEmojiEncoder: Encoder[Emoji] = deriveEncoder
  implicit val guildEmojiDecoder: Decoder[Emoji] = deriveDecoder

  implicit val imageDataEncoder: Encoder[ImageData] = Encoder[String].contramap(_.rawData)
  implicit val imageDataDecoder: Decoder[ImageData] = Decoder[String].emap(s => Right(new ImageData(s)))

  implicit val connectionEncoder: Encoder[Connection] = deriveEncoder
  implicit val connectionDecoder: Decoder[Connection] = deriveDecoder

  implicit val webhookDecoder: Decoder[Webhook] = deriveDecoder

  implicit val auditLogDecoder: Decoder[AuditLog] = deriveDecoder

  implicit val auditLogChangeDecoder: Decoder[AuditLogChange[_]] = (c: HCursor) => {

    def mkChange[A: Decoder, B](create: (A, A) => B): Either[DecodingFailure, B] =
      for {
        oldVal <- c.get[A]("old_value")
        newVal <- c.get[A]("new_value")
      } yield create(oldVal, newVal)

    c.get[String]("key").flatMap {
      case "name"                          => mkChange(AuditLogChange.Name)
      case "icon_hash"                     => mkChange(AuditLogChange.IconHash)
      case "splash_hash"                   => mkChange(AuditLogChange.SplashHash)
      case "owner_id"                      => mkChange(AuditLogChange.OwnerId)
      case "region"                        => mkChange(AuditLogChange.Region)
      case "afk_channel_id"                => mkChange(AuditLogChange.AfkChannelId)
      case "afk_timeout"                   => mkChange(AuditLogChange.AfkTimeout)
      case "mfa_level"                     => mkChange(AuditLogChange.MfaLevel)
      case "verification_level"            => mkChange(AuditLogChange.VerificationLevel)
      case "explicit_content_filter"       => mkChange(AuditLogChange.ExplicitContentFilter)
      case "default_message_notifications" => mkChange(AuditLogChange.DefaultMessageNotification)
      case "vanity_url_code"               => mkChange(AuditLogChange.VanityUrlCode)
      case "$add"                          => mkChange(AuditLogChange.$Add)
      case "$remove"                       => mkChange(AuditLogChange.$Remove)
      case "prune_delete_days"             => mkChange(AuditLogChange.PruneDeleteDays)
      case "widget_enabled"                => mkChange(AuditLogChange.WidgetEnabled)
      case "widget_channel_id"             => mkChange(AuditLogChange.WidgetChannelId)
      case "position"                      => mkChange(AuditLogChange.Position)
      case "topic"                         => mkChange(AuditLogChange.Topic)
      case "bitrate"                       => mkChange(AuditLogChange.Bitrate)
      case "permission_overwrites"         => mkChange(AuditLogChange.PermissionOverwrites)
      case "nsfw"                          => mkChange(AuditLogChange.NSFW)
      case "application_id"                => mkChange(AuditLogChange.ApplicationId)
      case "permissions"                   => mkChange(AuditLogChange.Permissions)
      case "color"                         => mkChange(AuditLogChange.Color)
      case "hoist"                         => mkChange(AuditLogChange.Hoist)
      case "mentionable"                   => mkChange(AuditLogChange.Mentionable)
      case "allow"                         => mkChange(AuditLogChange.Allow)
      case "deny"                          => mkChange(AuditLogChange.Deny)
      case "code"                          => mkChange(AuditLogChange.Code)
      case "channel_id"                    => mkChange(AuditLogChange.InviteChannelId)
      case "inviter_id"                    => mkChange(AuditLogChange.InviterId)
      case "max_uses"                      => mkChange(AuditLogChange.MaxUses)
      case "uses"                          => mkChange(AuditLogChange.Uses)
      case "max_age"                       => mkChange(AuditLogChange.MaxAge)
      case "temporary"                     => mkChange(AuditLogChange.Temporary)
      case "deaf"                          => mkChange(AuditLogChange.Deaf)
      case "mute"                          => mkChange(AuditLogChange.Mute)
      case "nick"                          => mkChange(AuditLogChange.Nick)
      case "avatar_hash"                   => mkChange(AuditLogChange.AvatarHash)
      case "id"                            => mkChange(AuditLogChange.Id)
      case "type"                          => mkChange(AuditLogChange.TypeInt).left.flatMap(_ => mkChange(AuditLogChange.TypeString))
    }
  }

  implicit val rawBanEncoder: Encoder[RawBan] = deriveEncoder
  implicit val rawBanDecoder: Decoder[RawBan] = deriveDecoder
}
object DiscordProtocol extends DiscordProtocol
