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
package net.katsstuff.ackcord.data

import java.time.{Instant, OffsetDateTime}

import io.circe.Decoder.Result

import scala.util.Try
import io.circe.{derivation, _}
import io.circe.generic.extras.Configuration
import io.circe.syntax._
import net.katsstuff.ackcord.data.raw._

trait DiscordProtocol {

  implicit val circeConfiguration: Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults

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
    Decoder[Int].emap(AuditLogEvent.fromId(_).toRight(s"No valid event type"))

  implicit def snowflakeTypeEncoder[A]: Encoder[SnowflakeType[A]] = Encoder[String].contramap(_.asString)
  implicit def snowflakeTypeDecoder[A]: Decoder[SnowflakeType[A]] =
    Decoder[String].emap(s => Right(SnowflakeType[A](s)))

  implicit val instantEncoder: Encoder[Instant] = Encoder[Long].contramap(_.getEpochSecond)
  implicit val instantDecoder: Decoder[Instant] = Decoder[Long].emapTry(l => Try(Instant.ofEpochSecond(l)))

  implicit val permissionEncoder: Encoder[Permission] = Encoder[Long].contramap(identity)
  implicit val permissionDecoder: Decoder[Permission] = Decoder[Long].emap(i => Right(Permission.fromLong(i)))

  implicit val offsetDateTimeEncoder: Encoder[OffsetDateTime] = Encoder[String].contramap(_.toString)
  implicit val offsetDateTimeDecoder: Decoder[OffsetDateTime] =
    Decoder[String].emapTry(s => Try(OffsetDateTime.parse(s)))

  implicit val imageDataEncoder: Encoder[ImageData] = Encoder[String].contramap(_.rawData)
  implicit val imageDataDecoder: Decoder[ImageData] = Decoder[String].emap(s => Right(new ImageData(s)))

  implicit val messageActivityTypeEncoder: Encoder[MessageActivityType] =
    Encoder[Int].contramap(MessageActivityType.idOf)
  implicit val messageActivityTypeDecoder: Decoder[MessageActivityType] =
    Decoder[Int].emap(MessageActivityType.fromId(_).toRight("Not a valid MFA level"))

  implicit val rawChannelEncoder: Encoder[RawChannel] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawChannelDecoder: Decoder[RawChannel] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val rawGuildEncoder: Encoder[RawGuild] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawGuildDecoder: Decoder[RawGuild] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val partialUserEncoder: Encoder[PartialUser] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val partialUserDecoder: Decoder[PartialUser] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val rawActivityEncoder: Encoder[RawActivity] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawActivityDecoder: Decoder[RawActivity] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val activityTimestampsEncoder: Encoder[ActivityTimestamps] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val activityTimestampsDecoder: Decoder[ActivityTimestamps] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val activityAssetEncoder: Encoder[ActivityAsset] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val activityAssetDecoder: Decoder[ActivityAsset] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val rawActivityPartyEncoder: Encoder[RawActivityParty] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawActivityPartyDecoder: Decoder[RawActivityParty] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val rawPresenceEncoder: Encoder[RawPresence] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawPresenceDecoder: Decoder[RawPresence] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val unavailableGuildEncoder: Encoder[UnavailableGuild] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val unavailableGuildDecoder: Decoder[UnavailableGuild] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val permissionValueEncoder: Encoder[PermissionOverwrite] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val permissionValueDecoder: Decoder[PermissionOverwrite] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val userEncoder: Encoder[User] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val userDecoder: Decoder[User] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val webhookAuthorEncoder: Encoder[WebhookAuthor] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val webhookAuthorDecoder: Decoder[WebhookAuthor] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val roleEncoder
    : Encoder[Role] = derivation.deriveEncoder(derivation.renaming.snakeCase) //Encoding roles is fine, decoding them is not

  implicit val rawRoleEncoder: Encoder[RawRole] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawRoleDecoder: Decoder[RawRole] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val rawGuildMemberEncoder: Encoder[RawGuildMember] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawGuildMemberDecoder: Decoder[RawGuildMember] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val attachementEncoder: Encoder[Attachment] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val attachementDecoder: Decoder[Attachment] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val embedFieldEncoder: Encoder[EmbedField] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val embedFieldDecoder: Decoder[EmbedField] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val receivedEmbedFooterEncoder: Encoder[ReceivedEmbedFooter] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val receivedEmbedFooterDecoder: Decoder[ReceivedEmbedFooter] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val receivedEmbedImageEncoder: Encoder[ReceivedEmbedImage] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val receivedEmbedImageDecoder: Decoder[ReceivedEmbedImage] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val receivedEmbedThumbnailEncoder: Encoder[ReceivedEmbedThumbnail] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val receivedEmbedThumbnailDecoder: Decoder[ReceivedEmbedThumbnail] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val receivedEmbedVideoEncoder: Encoder[ReceivedEmbedVideo] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val receivedEmbedVideoDecoder: Decoder[ReceivedEmbedVideo] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val receivedEmbedProviderEncoder: Encoder[ReceivedEmbedProvider] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val receivedEmbedProviderDecoder: Decoder[ReceivedEmbedProvider] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val receivedEmbedAuthorEncoder: Encoder[ReceivedEmbedAuthor] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val receivedEmbedAuthorDecoder: Decoder[ReceivedEmbedAuthor] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val receivedEmbedEncoder: Encoder[ReceivedEmbed] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val receivedEmbedDecoder: Decoder[ReceivedEmbed] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val outgoingEmbedFooterEncoder: Encoder[OutgoingEmbedFooter] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val outgoingEmbedFooterDecoder: Decoder[OutgoingEmbedFooter] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val outgoingEmbedImageEncoder: Encoder[OutgoingEmbedImage] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val outgoingEmbedImageDecoder: Decoder[OutgoingEmbedImage] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val outgoingEmbedVideoEncoder: Encoder[OutgoingEmbedVideo] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val outgoingEmbedVideoDecoder: Decoder[OutgoingEmbedVideo] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val outgoingEmbedThumbnailEncoder: Encoder[OutgoingEmbedThumbnail] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val outgoingEmbedThumbnailDecoder: Decoder[OutgoingEmbedThumbnail] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val outgoingEmbedAuthorEncoder: Encoder[OutgoingEmbedAuthor] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val outgoingEmbedAuthorDecoder: Decoder[OutgoingEmbedAuthor] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val outgoingEmbedEncoder: Encoder[OutgoingEmbed] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val outgoingEmbedDecoder: Decoder[OutgoingEmbed] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val partialEmojiEncoder: Encoder[PartialEmoji] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val partialEmojiDecoder: Decoder[PartialEmoji] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val reactionEncoder: Encoder[Reaction] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val reactionDecoder: Decoder[Reaction] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val rawMessageActivityEncoder: Encoder[RawMessageActivity] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawMessageActivityDecoder: Decoder[RawMessageActivity] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val messageApplicationEncoder: Encoder[MessageApplication] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val messageApplicationDecoder: Decoder[MessageApplication] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val partialRawGuildMemberEncoder: Encoder[PartialRawGuildMember] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val partialRawGuildMemberDecoder: Decoder[PartialRawGuildMember] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

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
      "type"             -> a.`type`.asJson,
      "activity"         -> a.activity.asJson,
      "application"      -> a.application.asJson
    )

    a.author match {
      case user: User => Json.obj(base :+ "author" -> user.asJson: _*)
      case webhook: WebhookAuthor =>
        Json.obj(base ++ Seq("author" -> webhook.asJson, "webhook_id" -> webhook.id.asJson): _*)
    }
  }
  implicit val rawMessageDecoder: Decoder[RawMessage] = (c: HCursor) => {
    val isWebhook = c.keys.exists(_.toSeq.contains("webhook_id"))

    for {
      id              <- c.get[MessageId]("id").right
      channelId       <- c.get[ChannelId]("channel_id").right
      guildId         <- c.get[Option[GuildId]]("guild_id").right
      author          <- (if (isWebhook) c.get[WebhookAuthor]("author") else c.get[User]("author")).right
      member          <- c.get[Option[PartialRawGuildMember]]("member").right
      content         <- c.get[String]("content").right
      timestamp       <- c.get[OffsetDateTime]("timestamp").right
      editedTimestamp <- c.get[Option[OffsetDateTime]]("edited_timestamp").right
      tts             <- c.get[Boolean]("tts").right
      mentionEveryone <- c.get[Boolean]("mention_everyone").right
      mentions        <- c.get[Seq[User]]("mentions").right
      mentionRoles    <- c.get[Seq[RoleId]]("mention_roles").right
      attachment      <- c.get[Seq[Attachment]]("attachments").right
      embeds          <- c.get[Seq[ReceivedEmbed]]("embeds").right
      reactions       <- c.get[Option[Seq[Reaction]]]("reactions").right
      nonce           <- c.get[Option[RawSnowflake]]("nonce").right
      pinned          <- c.get[Boolean]("pinned").right
      tpe             <- c.get[MessageType]("type").right
      activity        <- c.get[Option[RawMessageActivity]]("activity").right
      application     <- c.get[Option[MessageApplication]]("application").right
    } yield
      RawMessage(
        id,
        channelId,
        guildId,
        author,
        member,
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
        tpe,
        activity,
        application
      )
  }

  implicit val voiceStateEncoder: Encoder[VoiceState] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val voiceStateDecoder: Decoder[VoiceState] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val inviteGuildEncoder: Encoder[InviteGuild] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val inviteGuildDecoder: Decoder[InviteGuild] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val inviteChannelEncoder: Encoder[InviteChannel] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val inviteChannelDecoder: Decoder[InviteChannel] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val inviteEncoder: Encoder[Invite] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val inviteDecoder: Decoder[Invite] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val inviteWithMetadataEncoder: Encoder[InviteWithMetadata] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val inviteWithMetadataDecoder: Decoder[InviteWithMetadata] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val guildEmbedEncoder: Encoder[GuildEmbed] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val guildEmbedDecoder: Decoder[GuildEmbed] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val integrationAccountEncoder: Encoder[IntegrationAccount] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val integrationAccountDecoder: Decoder[IntegrationAccount] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val integrationEncoder: Encoder[Integration] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val integrationDecoder: Decoder[Integration] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val voiceRegionEncoder: Encoder[VoiceRegion] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val voiceRegionDecoder: Decoder[VoiceRegion] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val rawEmojiEncoder: Encoder[RawEmoji] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawEmojiDecoder: Decoder[RawEmoji] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val connectionEncoder: Encoder[Connection] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val connectionDecoder: Decoder[Connection] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val webhookDecoder: Decoder[Webhook] = derivation.deriveDecoder(derivation.renaming.snakeCase)

  implicit val auditLogDecoder: Decoder[AuditLog] = {
    import io.circe.generic.extras.auto._
    io.circe.generic.extras.semiauto.deriveDecoder
  }

  //Scala 2.11
  //noinspection ConvertExpressionToSAM
  implicit val auditLogChangeDecoder: Decoder[AuditLogChange[_]] = new Decoder[AuditLogChange[_]] {
    override def apply(c: HCursor): Result[AuditLogChange[_]] = {

      def mkChange[A: Decoder, B](create: (A, A) => B): Either[DecodingFailure, B] =
        for {
          oldVal <- c.get[A]("old_value").right
          newVal <- c.get[A]("new_value").right
        } yield create(oldVal, newVal)

      c.get[String]("key").right.flatMap {
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
  }

  implicit val rawBanEncoder: Encoder[RawBan] = derivation.deriveEncoder(derivation.renaming.snakeCase)
  implicit val rawBanDecoder: Decoder[RawBan] = derivation.deriveDecoder(derivation.renaming.snakeCase)
}
object DiscordProtocol extends DiscordProtocol
