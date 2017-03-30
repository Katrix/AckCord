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

import net.katsstuff.akkacord.data._
import spray.json._

trait DiscordProtocol extends SnakeCaseProtocol {

  implicit object SnowflakeFormat extends JsonFormat[Snowflake] {
    override def write(obj: Snowflake): JsValue   = JsString(obj.content)
    override def read(json: JsValue):   Snowflake = Snowflake(json.convertTo[String])
  }

  implicit object OffsetDateTimeFormat extends JsonFormat[OffsetDateTime] {
    override def read(json: JsValue):        OffsetDateTime = OffsetDateTime.parse(json.convertTo[String])
    override def write(obj: OffsetDateTime): JsValue        = JsString(obj.toString)
  }

  implicit object InstantFormat extends JsonFormat[Instant] {
    override def read(json: JsValue): Instant = Instant.ofEpochSecond(json.convertTo[Long])
    override def write(obj: Instant): JsValue = JsNumber(obj.getEpochSecond)
  }

  implicit object PermissionValueTypeFormat extends JsonFormat[PermissionValueType] {
    override def write(obj: PermissionValueType): JsValue = JsString(obj.name)
    override def read(json: JsValue): PermissionValueType =
      PermissionValueType.forName(json.convertTo[String]).getOrElse(throw DeserializationException("Wrong permission type"))
  }
  implicit object PermissionsFormat extends JsonFormat[Permission] {
    override def write(obj: Permission): JsValue    = JsNumber(obj.int)
    override def read(json: JsValue):    Permission = Permission.fromInt(json.convertTo[Int])
  }
  implicit object ChannelTypeFormat extends JsonFormat[ChannelType] {
    override def write(obj: ChannelType): JsValue = JsString(obj.name)
    override def read(json: JsValue): ChannelType =
      ChannelType.forName(json.convertTo[String]).getOrElse(throw DeserializationException("Wrong permission type"))
  }

  implicit val PermissionValueFormat: RootJsonFormat[PermissionValue] = jsonFormat4(PermissionValue)
  implicit val GuildChannelFormat:    RootJsonFormat[RawGuildChannel] = jsonFormat11(RawGuildChannel)
  implicit val DMChannelFormat:       RootJsonFormat[RawDMChannel]    = jsonFormat4(RawDMChannel)
  implicit val MessageEmojiFormat:    RootJsonFormat[MessageEmoji]    = jsonFormat2(MessageEmoji)
  implicit val ReactionFormat:        RootJsonFormat[Reaction]        = jsonFormat3(Reaction)

  implicit object ChannelFormat extends RootJsonFormat[RawChannel] {
    override def read(json: JsValue): RawChannel =
      if (json.asJsObject.fields("is_private").convertTo[Boolean]) json.convertTo[RawDMChannel] else json.convertTo[RawGuildChannel]
    override def write(obj: RawChannel): JsValue = obj match {
      case dmChannel:    RawDMChannel    => dmChannel.toJson
      case guildChannel: RawGuildChannel => guildChannel.toJson
    }
  }

  implicit object MessageObject extends RootJsonFormat[RawMessage] {
    override def read(json: JsValue): RawMessage = {
      val fields    = json.asJsObject.fields
      val isWebhook = fields.contains("webhook_id")

      RawMessage(
        id = fields("id").convertTo[Snowflake],
        channelId = fields("channel_id").convertTo[Snowflake],
        author = if (isWebhook) fields("author").convertTo[WebhookAuthor] else fields("author").convertTo[User],
        content = fields("content").convertTo[String],
        timestamp = fields("timestamp").convertTo[OffsetDateTime],
        editedTimestamp = fields.get("edited_timestamp").flatMap(_.convertTo[Option[OffsetDateTime]]),
        tts = fields("tts").convertTo[Boolean],
        mentionEveryone = fields("mention_everyone").convertTo[Boolean],
        mentions = fields("mentions").convertTo[Seq[User]],
        mentionRoles = fields("mention_roles").convertTo[Seq[Snowflake]],
        attachment = fields("attachments").convertTo[Seq[Attachment]],
        embeds = fields("embeds").convertTo[Seq[Embed]],
        reactions = fields.get("reactions").flatMap(_.convertTo[Option[Seq[Reaction]]]),
        nonce = fields.get("nonce").flatMap(_.convertTo[Option[Snowflake]]),
        pinned = fields("pinned").convertTo[Boolean],
        webhookId = fields.get("webhook_id").flatMap(_.convertTo[Option[String]])
      )
    }
    override def write(obj: RawMessage): JsValue = {
      val base = Seq(
        "id"               -> obj.id.toJson,
        "channel_id"       -> obj.channelId.toJson,
        "content"          -> obj.content.toJson,
        "timestamp"        -> obj.timestamp.toJson,
        "edited_timestamp" -> obj.editedTimestamp.toJson,
        "tts"              -> obj.tts.toJson,
        "mention_everyone" -> obj.mentionEveryone.toJson,
        "mentions"         -> obj.mentions.toJson,
        "mention_roles"    -> obj.mentionRoles.toJson,
        "attachments"      -> obj.attachment.toJson,
        "embeds"           -> obj.embeds.toJson,
        "reactions"        -> obj.reactions.toJson,
        "nonce"            -> obj.nonce.toJson,
        "pinned"           -> obj.pinned.toJson,
        "webhook_id"       -> obj.webhookId.toJson
      )

      obj.author match {
        case user:    User          => JsObject(base :+ "author" -> user.toJson:    _*)
        case webhook: WebhookAuthor => JsObject(base :+ "author" -> webhook.toJson: _*)
      }
    }
  }

  implicit val WebhookAuthorFormat: RootJsonFormat[WebhookAuthor] = jsonFormat3(WebhookAuthor)

  implicit val UserFormat:           RootJsonFormat[User]           = jsonFormat8(User)
  implicit val EmbedFormat:          RootJsonFormat[Embed]          = jsonFormat13(Embed)
  implicit val EmbedThumbnailFormat: RootJsonFormat[EmbedThumbnail] = jsonFormat4(EmbedThumbnail)
  implicit val EmbedVideoFormat:     RootJsonFormat[EmbedVideo]     = jsonFormat3(EmbedVideo)
  implicit val EmbedImageFormat:     RootJsonFormat[EmbedImage]     = jsonFormat4(EmbedImage)
  implicit val EmbedProviderFormat:  RootJsonFormat[EmbedProvider]  = jsonFormat2(EmbedProvider)
  implicit val EmbedAuthorFormat:    RootJsonFormat[EmbedAuthor]    = jsonFormat4(EmbedAuthor)
  implicit val EmbedFooterFormat:    RootJsonFormat[EmbedFooter]    = jsonFormat3(EmbedFooter)
  implicit val EmbedFieldFormat:     RootJsonFormat[EmbedField]     = jsonFormat3(EmbedField)
  implicit val AttachmentFormat:     RootJsonFormat[Attachment]     = jsonFormat7(Attachment)
  implicit val RoleFormat:           RootJsonFormat[Role]           = jsonFormat8(Role)
  implicit val GuildEmojiFormat:     RootJsonFormat[GuildEmoji]     = jsonFormat5(GuildEmoji)
  implicit val VoiceStateFormat:     RootJsonFormat[VoiceState]     = jsonFormat9(VoiceState)
  implicit val GuildMemberFormat:    RootJsonFormat[RawGuildMember] = jsonFormat6(RawGuildMember)
  implicit val RawGuildFormat: RootJsonFormat[RawGuild] = jsonFormat(
    RawGuild,
    "id",
    "name",
    "icon",
    "splash",
    "owner_id",
    "region",
    "afk_channel_id",
    "afk_timeout",
    "embed_enabled",
    "embed_channel_id",
    "verification_level",
    "default_message_notifications",
    "roles",
    "emojis", /*"features",*/ "mfa_level",
    "joined_at",
    "large",
    "unavailable",
    "member_count",
    "voice_states",
    "members",
    "channels" /*, "presences"*/
  )
  implicit val RawUnavailableGuildFormat: RootJsonFormat[RawUnavailableGuild] = jsonFormat2(RawUnavailableGuild)
}
