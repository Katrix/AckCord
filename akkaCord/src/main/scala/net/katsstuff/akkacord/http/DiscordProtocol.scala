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

import java.time.OffsetDateTime

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.auto._
import io.circe.shapes._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}
import net.katsstuff.akkacord.data._

trait DiscordProtocol {

  implicit val config: Configuration = Configuration.default.withSnakeCaseKeys.withDefaults

  implicit val channelTypeEncoder: Encoder[ChannelType] = deriveEnumerationEncoder
  implicit val channelTypeDecoder: Decoder[ChannelType] = deriveEnumerationDecoder

  implicit val permissionValueTypeEncoder: Encoder[PermissionValueType] = deriveEnumerationEncoder
  implicit val permissionValueTypeDecoder: Decoder[PermissionValueType] = deriveEnumerationDecoder

  implicit val presenceStatusEncoder: Encoder[PresenceStatus] = deriveEnumerationEncoder
  implicit val presenceStatusDecoder: Decoder[PresenceStatus] = deriveEnumerationDecoder

  implicit val snowflakeEncoder: Encoder[Snowflake] = (a: Snowflake) => Json.fromString(a.content)
  implicit val snowflakeDecoder: Decoder[Snowflake] = (c: HCursor) => c.as[String].map(Snowflake.apply)

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

  implicit val permissionEncoder: Encoder[Permission] = (a: Permission) => Json.fromInt(a.int)
  implicit val permissionDecoder: Decoder[Permission] = (c: HCursor) => c.as[Int].map(Permission.fromInt)

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

  implicit val embedEncoder: Encoder[Embed] = deriveEncoder
  implicit val embedDecoder: Decoder[Embed] = deriveDecoder

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
      case user:    User          => Json.obj(base :+ "author" -> user.asJson:    _*)
      case webhook: WebhookAuthor => Json.obj(base :+ "author" -> webhook.asJson: _*)
    }
  }
  implicit val rawMessageDecoder: Decoder[RawMessage] = (c: HCursor) => {
    val isWebhook = c.fields.exists(_.contains("webhook_id"))

    for {
      id              <- c.downField("id").as[Snowflake]
      channelId       <- c.downField("channel_id").as[Snowflake]
      author          <- if (isWebhook) c.downField("author").as[WebhookAuthor] else c.downField("author").as[User]
      content         <- c.downField("content").as[String]
      timestamp       <- c.downField("timestamp").as[OffsetDateTime]
      editedTimestamp <- c.downField("edited_timestamp").as[Option[OffsetDateTime]]
      tts             <- c.downField("tts").as[Boolean]
      mentionEveryone <- c.downField("mention_everyone").as[Boolean]
      mentions        <- c.downField("mentions").as[Seq[User]]
      mentionRoles    <- c.downField("mention_roles").as[Seq[Snowflake]]
      attachment      <- c.downField("attachments").as[Seq[Attachment]]
      embeds          <- c.downField("embeds").as[Seq[Embed]]
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

  implicit val offsetDateTimeEncoder: Encoder[OffsetDateTime] = (a: OffsetDateTime) => Json.fromString(a.toString)
  implicit val offsetDateTimeDecoder: Decoder[OffsetDateTime] = (c: HCursor) => c.as[String].map(OffsetDateTime.parse)

  implicit val voiceStateEncoder: Encoder[VoiceState] = deriveEncoder
  implicit val voiceStateDecoder: Decoder[VoiceState] = deriveDecoder
}
