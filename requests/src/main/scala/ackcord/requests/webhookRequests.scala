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
package ackcord.requests

import scala.concurrent.Future

import ackcord.CacheSnapshot
import ackcord.data.DiscordProtocol._
import ackcord.data._
import ackcord.data.raw.RawMessage
import ackcord.util.{JsonOption, JsonUndefined, Verifier}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity, ResponseEntity}
import io.circe._
import io.circe.syntax._

/**
  * @param name
  *   Name of the webhook
  * @param avatar
  *   The avatar data of the webhook
  */
case class CreateWebhookData(name: String, avatar: Option[ImageData]) {
  Verifier.requireLength(name, "Webhook name", min = 2, max = 32)
}

/** Create a new webhook in a channel. */
case class CreateWebhook(
    channelId: TextChannelId,
    params: CreateWebhookData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[CreateWebhook, CreateWebhookData, Webhook] {
  override def route: RequestRoute                       = Routes.createWebhook(channelId)
  override def paramsEncoder: Encoder[CreateWebhookData] = derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): CreateWebhook = copy(reason = Some(reason))
}

/** Get the webhooks in a channel. */
case class GetChannelWebhooks(channelId: TextChannelId) extends NoParamsNiceResponseRequest[Seq[Webhook]] {
  override def route: RequestRoute = Routes.getChannelWebhooks(channelId)

  override def responseDecoder: Decoder[Seq[Webhook]] = Decoder[Seq[Webhook]]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/** Get the webhooks in a guild. */
case class GetGuildWebhooks(guildId: GuildId) extends NoParamsNiceResponseRequest[Seq[Webhook]] {
  override def route: RequestRoute = Routes.getGuildWebhooks(guildId)

  override def responseDecoder: Decoder[Seq[Webhook]] = Decoder[Seq[Webhook]]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/** Get a webhook by id. */
case class GetWebhook(id: SnowflakeType[Webhook]) extends NoParamsNiceResponseRequest[Webhook] {
  override def route: RequestRoute = Routes.getWebhook(id)

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
}

/** Get a webhook by id with a token. Doesn't require authentication. */
case class GetWebhookWithToken(id: SnowflakeType[Webhook], token: String) extends NoParamsNiceResponseRequest[Webhook] {
  override def route: RequestRoute = Routes.getWebhookWithToken(id, token)

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
}

/**
  * @param name
  *   Name of the webhook.
  * @param avatar
  *   The avatar data of the webhook.
  * @param channelId
  *   The channel this webhook should be moved to.
  */
case class ModifyWebhookData(
    name: JsonOption[String] = JsonUndefined,
    avatar: JsonOption[ImageData] = JsonUndefined,
    channelId: JsonOption[TextGuildChannelId] = JsonUndefined
)
object ModifyWebhookData {
  implicit val encoder: Encoder[ModifyWebhookData] = (a: ModifyWebhookData) =>
    JsonOption.removeUndefinedToObj(
      "name"       -> a.name.toJson,
      "avatar"     -> a.avatar.toJson,
      "channel_id" -> a.channelId.toJson
    )
}

/** Modify a webhook. */
case class ModifyWebhook(
    id: SnowflakeType[Webhook],
    params: ModifyWebhookData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[ModifyWebhook, ModifyWebhookData, Webhook] {
  override def route: RequestRoute                       = Routes.getWebhook(id)
  override def paramsEncoder: Encoder[ModifyWebhookData] = ModifyWebhookData.encoder

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions: Permission           = Permission.ManageWebhooks
  override def withReason(reason: String): ModifyWebhook = copy(reason = Some(reason))
}

/** Modify a webhook with a token. Doesn't require authentication */
case class ModifyWebhookWithToken(
    id: SnowflakeType[Webhook],
    token: String,
    params: ModifyWebhookData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[ModifyWebhookWithToken, ModifyWebhookData, Webhook] {
  require(params.channelId.isEmpty, "ModifyWebhookWithToken does not accept a channelId in the request")
  override def route: RequestRoute = Routes.getWebhookWithToken(id, token)

  override def paramsEncoder: Encoder[ModifyWebhookData] = ModifyWebhookData.encoder
  override def responseDecoder: Decoder[Webhook]         = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks

  override def withReason(reason: String): ModifyWebhookWithToken = copy(reason = Some(reason))
}

/** Delete a webhook. */
case class DeleteWebhook(
    id: SnowflakeType[Webhook],
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteWebhook] {
  override def route: RequestRoute = Routes.deleteWebhook(id)

  override def requiredPermissions: Permission = Permission.ManageWebhooks

  override def withReason(reason: String): DeleteWebhook = copy(reason = Some(reason))
}

/** Delete a webhook with a token. Doesn't require authentication */
case class DeleteWebhookWithToken(
    id: SnowflakeType[Webhook],
    token: String,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteWebhookWithToken] {
  override def route: RequestRoute = Routes.deleteWebhookWithToken(id, token)

  override def requiredPermissions: Permission = Permission.ManageWebhooks

  override def withReason(reason: String): DeleteWebhookWithToken = copy(reason = Some(reason))
}

/**
  * @param content
  *   The content of the message.
  * @param username
  *   The username to use with the message.
  * @param avatarUrl
  *   The avatar url to use with the message.
  * @param tts
  *   If this is a text-to-speech message.
  * @param files
  *   The files to send with this message. You can reference these files in the
  *   embed using `attachment://filename`.
  * @param embeds
  *   Embeds to send with this message.
  */
case class ExecuteWebhookData(
    content: String = "",
    username: Option[String] = None,
    avatarUrl: Option[String] = None,
    tts: Option[Boolean] = None,
    files: Seq[CreateMessageFile] = Seq.empty,
    embeds: Seq[OutgoingEmbed] = Nil,
    allowedMentions: Option[AllowedMention] = None,
    components: Option[Seq[ActionRow]] = None,
    attachments: Option[Seq[PartialAttachment]] = None,
    flags: Option[MessageFlags] = None
) {
  files.foreach(file => require(file.isValid))
  require(
    files.map(_.fileName).distinct.lengthCompare(files.length) == 0,
    "Please use unique filenames for all files"
  )
  Verifier.requireLength(content, "Content", max = 4000)
  Verifier.requireLengthS(embeds, "Embeds", max = 10)
  Verifier.requireLengthOS(components, "Components", max = 5)
}
object ExecuteWebhookData {

  //We handle this here as the file argument needs special treatment
  implicit val encoder: Encoder[ExecuteWebhookData] = (a: ExecuteWebhookData) =>
    Json.obj(
      "content"          := a.content,
      "username"         := a.username,
      "avatar_url"       := a.avatarUrl,
      "tts"              := a.tts,
      "embeds"           := a.embeds,
      "allowed_mentions" := a.allowedMentions,
      "components"       := a.components,
      "attachments"      := a.attachments,
      "flags"            := a.flags
    )
}

case class ExecuteWebhook(
    id: SnowflakeType[Webhook],
    token: String,
    waitQuery: Boolean = false,
    threadId: Option[ThreadGuildChannelId],
    params: ExecuteWebhookData
) extends RESTRequest[ExecuteWebhookData, Option[RawMessage], Option[Message]] {
  override def route: RequestRoute = Routes.executeWebhook(id, token, Some(waitQuery), threadId)

  override def requestBody: RequestEntity = {
    if (params.files.nonEmpty) {
      val jsonPart = FormData.BodyPart(
        "payload_json",
        HttpEntity(ContentTypes.`application/json`, jsonParams.printWith(jsonPrinter))
      )

      FormData(params.files.zipWithIndex.map(t => t._1.toBodyPart(t._2)) :+ jsonPart: _*).toEntity()
    } else {
      super.requestBody
    }
  }

  override def parseResponse(
      entity: ResponseEntity
  )(implicit system: ActorSystem[Nothing]): Future[Option[RawMessage]] = {
    if (waitQuery) super.parseResponse(entity)
    else {
      entity.discardBytes()
      Future.successful(None)
    }
  }

  override def paramsEncoder: Encoder[ExecuteWebhookData] =
    ExecuteWebhookData.encoder

  override def responseDecoder: Decoder[Option[RawMessage]] =
    Decoder[Option[RawMessage]]

  override def toNiceResponse(response: Option[RawMessage]): Option[Message] = response.map(_.toMessage)
}

case class CreateFollowupMessage(
    id: SnowflakeType[Webhook],
    token: String,
    params: ExecuteWebhookData
) extends RESTRequest[ExecuteWebhookData, Option[RawMessage], Option[Message]] {
  override def route: RequestRoute = Routes.postFollowupMessage(id, token)

  override def paramsEncoder: Encoder[ExecuteWebhookData] =
    ExecuteWebhookData.encoder

  override def responseDecoder: Decoder[Option[RawMessage]] =
    Decoder[Option[RawMessage]]

  override def toNiceResponse(response: Option[RawMessage]): Option[Message] = response.map(_.toMessage)
}

/**
  * @param content
  *   The new content of the message.
  * @param embeds
  *   The new embeds of the message.
  * @param files
  *   The new files of the message.
  * @param allowedMentions
  *   The new allowed mentions of the message.
  * @param attachments
  *   The attachments to keep in the new message.
  */
case class EditWebhookMessageData(
    content: JsonOption[String] = JsonUndefined,
    embeds: JsonOption[Seq[OutgoingEmbed]] = JsonUndefined,
    files: JsonOption[Seq[CreateMessageFile]] = JsonUndefined,
    allowedMentions: JsonOption[AllowedMention] = JsonUndefined,
    components: JsonOption[Seq[ActionRow]] = JsonUndefined,
    attachments: JsonOption[Seq[PartialAttachment]] = JsonUndefined
) {
  files.foreach(_.foreach(file => require(file.isValid)))
  require(
    files.forall(files => files.map(_.fileName).distinct.lengthCompare(files.length) == 0),
    "Please use unique filenames for all files"
  )
  Verifier.requireLengthJO(content, "Content", max = 4000)
  Verifier.requireLengthJOS(embeds, "Embeds", max = 10)
  Verifier.requireLengthJOS(components, "Components", max = 5)
}
object EditWebhookMessageData {
  implicit val encoder: Encoder[EditWebhookMessageData] = (a: EditWebhookMessageData) =>
    JsonOption.removeUndefinedToObj(
      "content"          -> a.content.toJson,
      "embeds"           -> a.embeds.toJson,
      "allowed_mentions" -> a.allowedMentions.toJson,
      "components"       -> a.components.toJson,
      "attachments"      -> a.attachments.toJson
    )
}

case class GetOriginalWebhookMessage(id: SnowflakeType[Webhook], token: String)
    extends NoParamsRequest[RawMessage, Message] {
  override def route: RequestRoute = Routes.getOriginalWebhookMessage(id, token)

  override def responseDecoder: Decoder[RawMessage]          = Decoder[RawMessage]
  override def toNiceResponse(response: RawMessage): Message = response.toMessage
}

case class EditOriginalWebhookMessage(id: SnowflakeType[Webhook], token: String, params: EditWebhookMessageData)
    extends NoNiceResponseRequest[EditWebhookMessageData, Json] {
  override def route: RequestRoute                            = Routes.editOriginalWebhookMessage(id, token)
  override def paramsEncoder: Encoder[EditWebhookMessageData] = EditWebhookMessageData.encoder

  override def requestBody: RequestEntity = {
    if (params.files != JsonUndefined) {
      val jsonPart = FormData.BodyPart(
        "payload_json",
        HttpEntity(ContentTypes.`application/json`, jsonParams.printWith(jsonPrinter))
      )

      FormData(params.files.toList.flatMap(_.zipWithIndex.map(t => t._1.toBodyPart(t._2))) :+ jsonPart: _*).toEntity()
    } else {
      super.requestBody
    }
  }

  override def responseDecoder: Decoder[Json] = Decoder[Json]
}

case class DeleteOriginalWebhookMessage(id: SnowflakeType[Webhook], token: String) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteOriginalWebhookMessage(id, token)
}

case class GetWebhookMessage(
    id: SnowflakeType[Webhook],
    token: String,
    messageId: MessageId,
    threadId: Option[ThreadGuildChannelId] = None
) extends NoParamsRequest[RawMessage, Message] {
  override def route: RequestRoute = Routes.getWebhookMessage(id, token, messageId, threadId)

  override def responseDecoder: Decoder[RawMessage]          = Decoder[RawMessage]
  override def toNiceResponse(response: RawMessage): Message = response.toMessage
}

case class EditWebhookMessage(
    id: SnowflakeType[Webhook],
    token: String,
    messageId: MessageId,
    params: EditWebhookMessageData,
    threadId: Option[ThreadGuildChannelId] = None
) extends NoNiceResponseRequest[EditWebhookMessageData, Json] {
  override def route: RequestRoute = Routes.editWebhookMessage(id, token, messageId, threadId)
  override def paramsEncoder: Encoder[EditWebhookMessageData] = EditWebhookMessageData.encoder

  override def responseDecoder: Decoder[Json] = Decoder[Json]
}

case class DeleteWebhookMessage(
    id: SnowflakeType[Webhook],
    token: String,
    messageId: MessageId,
    threadId: Option[ThreadGuildChannelId] = None
) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteWebhookMessage(id, token, messageId, threadId)
}
