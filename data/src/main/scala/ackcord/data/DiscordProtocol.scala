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
package ackcord.data

import java.time.{Instant, OffsetDateTime}

import scala.util.Try

import ackcord.data.AuditLogChange.PartialRole
import ackcord.data.raw._
import cats.syntax.either._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.syntax._

//noinspection NameBooleanParameters
trait DiscordProtocol {

  implicit val circeConfiguration: Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults

  implicit def snowflakeTypeCodec[A]: Codec[SnowflakeType[A]] = Codec.from(
    Decoder[String].emap(s => Right(SnowflakeType[A](s))),
    Encoder[String].contramap(_.asString)
  )

  implicit val instantCodec: Codec[Instant] = Codec.from(
    Decoder[Long].emapTry(l => Try(Instant.ofEpochSecond(l))),
    Encoder[Long].contramap(_.getEpochSecond)
  )

  implicit val permissionCodec: Codec[Permission] = Codec.from(
    Decoder[Long].emap(i => Right(Permission.fromLong(i))),
    Encoder[Long].contramap(identity)
  )

  implicit val userFlagsCodec: Codec[UserFlags] = Codec.from(
    Decoder[Int].emap(i => Right(UserFlags.fromInt(i))),
    Encoder[Int].contramap(identity)
  )

  implicit val messageFlagsCodec: Codec[MessageFlags] = Codec.from(
    Decoder[Int].emap(i => Right(MessageFlags.fromInt(i))),
    Encoder[Int].contramap(identity)
  )

  implicit val systemChannelFlagsCodec: Codec[SystemChannelFlags] = Codec.from(
    Decoder[Int].emap(i => Right(SystemChannelFlags.fromInt(i))),
    Encoder[Int].contramap(identity)
  )

  implicit val offsetDateTimeCodec: Codec[OffsetDateTime] = Codec.from(
    Decoder[String].emapTry(s => Try(OffsetDateTime.parse(s))),
    Encoder[String].contramap[OffsetDateTime](_.toString)
  )

  implicit val imageDataCodec: Codec[ImageData] = Codec.from(
    Decoder[String].emap(s => Right(new ImageData(s))),
    Encoder[String].contramap(_.rawData)
  )

  implicit val rawChannelCodec: Codec[RawChannel] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawGuildCodec: Codec[RawGuild] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawGuildPreviewCodec: Codec[GuildPreview] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val partialUserCodec: Codec[PartialUser] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawActivityCodec: Codec[RawActivity] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val activityTimestampsCodec: Codec[ActivityTimestamps] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val activityAssetCodec: Codec[ActivityAsset] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawActivityPartyCodec: Codec[RawActivityParty] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val activityEmojiCodec: Codec[ActivityEmoji] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawPresenceCodec: Codec[RawPresence] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val unavailableGuildCodec: Codec[UnavailableGuild] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val permissionValueCodec: Codec[PermissionOverwrite] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val userCodec: Codec[User] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val webhookAuthorCodec: Codec[WebhookAuthor] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val roleCodec
      : Codec[Role] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None) //Encoding roles is fine, decoding them is not

  implicit val rawRoleCodec: Codec[RawRole] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawGuildMemberCodec: Codec[RawGuildMember] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val attachementCodec: Codec[Attachment] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val embedFieldCodec: Codec[EmbedField] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val receivedEmbedFooterCodec: Codec[ReceivedEmbedFooter] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val receivedEmbedImageCodec: Codec[ReceivedEmbedImage] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val receivedEmbedThumbnailCodec: Codec[ReceivedEmbedThumbnail] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val receivedEmbedVideoCodec: Codec[ReceivedEmbedVideo] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val receivedEmbedProviderCodec: Codec[ReceivedEmbedProvider] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val receivedEmbedAuthorCodec: Codec[ReceivedEmbedAuthor] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val receivedEmbedCodec: Codec[ReceivedEmbed] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val outgoingEmbedFooterCodec: Codec[OutgoingEmbedFooter] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val outgoingEmbedImageCodec: Codec[OutgoingEmbedImage] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val outgoingEmbedVideoCodec: Codec[OutgoingEmbedVideo] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val outgoingEmbedThumbnailCodec: Codec[OutgoingEmbedThumbnail] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val outgoingEmbedAuthorCodec: Codec[OutgoingEmbedAuthor] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val outgoingEmbedCodec: Codec[OutgoingEmbed] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val partialEmojiCodec: Codec[PartialEmoji] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val reactionCodec: Codec[Reaction] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawMessageActivityCodec: Codec[RawMessageActivity] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val messageApplicationCodec: Codec[MessageApplication] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val partialRawGuildMemberCodec: Codec[PartialRawGuildMember] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val channelMentionCodec: Codec[ChannelMention] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val messageReferenceCodec: Codec[MessageReference] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

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
      "nonce"            -> a.nonce.map(_.fold(_.asJson, _.asJson)).asJson,
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
      id              <- c.get[MessageId]("id")
      channelId       <- c.get[ChannelId]("channel_id")
      guildId         <- c.get[Option[GuildId]]("guild_id")
      author          <- if (isWebhook) c.get[WebhookAuthor]("author") else c.get[User]("author")
      member          <- c.get[Option[PartialRawGuildMember]]("member")
      content         <- c.get[String]("content")
      timestamp       <- c.get[OffsetDateTime]("timestamp")
      editedTimestamp <- c.get[Option[OffsetDateTime]]("edited_timestamp")
      tts             <- c.get[Boolean]("tts")
      mentionEveryone <- c.get[Boolean]("mention_everyone")
      mentions        <- c.get[Seq[User]]("mentions")
      mentionRoles    <- c.get[Seq[RoleId]]("mention_roles")
      mentionChannels <- c.get[Option[Seq[ChannelMention]]]("mention_channels")
      attachment      <- c.get[Seq[Attachment]]("attachments")
      embeds          <- c.get[Seq[ReceivedEmbed]]("embeds")
      reactions       <- c.get[Option[Seq[Reaction]]]("reactions")
      nonce <- c
        .get[Option[Int]]("nonce")
        .map(_.map(Left.apply))
        .orElse(c.get[Option[String]]("nonce").map(_.map(Right.apply)))
      pinned           <- c.get[Boolean]("pinned")
      tpe              <- c.get[MessageType]("type")
      activity         <- c.get[Option[RawMessageActivity]]("activity")
      application      <- c.get[Option[MessageApplication]]("application")
      messageReference <- c.get[Option[MessageReference]]("message_reference")
      flags            <- c.get[Option[MessageFlags]]("flags")
    } yield RawMessage(
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
      mentionChannels,
      attachment,
      embeds,
      reactions,
      nonce,
      pinned,
      tpe,
      activity,
      application,
      messageReference,
      flags
    )
  }

  implicit val voiceStateCodec: Codec[VoiceState] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val inviteGuildCodec: Codec[InviteGuild] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val inviteChannelCodec: Codec[InviteChannel] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val inviteTargetUserCodec: Codec[InviteTargetUser] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val inviteCodec: Codec[Invite] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val inviteWithMetadataCodec: Codec[InviteWithMetadata] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val guildEmbedCodec: Codec[GuildEmbed] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val integrationAccountCodec: Codec[IntegrationAccount] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val partialIntegrationCodec: Codec[PartialIntegration] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val integrationCodec: Codec[Integration] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val voiceRegionCodec: Codec[VoiceRegion] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val rawEmojiCodec: Codec[RawEmoji] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val connectionCodec: Codec[Connection] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val webhookDecoder: Decoder[Webhook] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)

  implicit val auditLogDecoder: Decoder[AuditLog] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)

  implicit val auditLogEntryDecoder: Decoder[AuditLogEntry] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)

  implicit val optionalAuditLogInfoDecoder: Decoder[OptionalAuditLogInfo] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)

  implicit val partialRoleCodec: Codec[PartialRole] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

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
      case "system_channel_id"             => mkChange(AuditLogChange.SystemChannelId)
      case "position"                      => mkChange(AuditLogChange.Position)
      case "topic"                         => mkChange(AuditLogChange.Topic)
      case "bitrate"                       => mkChange(AuditLogChange.Bitrate)
      case "permission_overwrites"         => mkChange(AuditLogChange.PermissionOverwrites)
      case "nsfw"                          => mkChange(AuditLogChange.NSFW)
      case "application_id"                => mkChange(AuditLogChange.ApplicationId)
      case "rate_limit_per_user"           => mkChange(AuditLogChange.RateLimitPerUser)
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
      case "enable_emoticons"              => mkChange(AuditLogChange.EnableEmoticons)
      case "expire_behavior"               => mkChange(AuditLogChange.ExpireBehavior)
      case "expire_grace_period"           => mkChange(AuditLogChange.ExpireGracePeriod)
    }
  }

  implicit val rawBanCodec: Codec[RawBan] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val clientStatusCodec: Codec[ClientStatus] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val teamCodec: Codec[Team] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val teamMemberCodec: Codec[TeamMember] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)
}
object DiscordProtocol extends DiscordProtocol
