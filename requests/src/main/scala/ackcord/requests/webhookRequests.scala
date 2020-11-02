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

import ackcord.CacheSnapshot
import ackcord.data.DiscordProtocol._
import ackcord.data._
import ackcord.util.{JsonOption, JsonUndefined}
import io.circe._
import io.circe.syntax._

/**
  * @param name Name of the webhook
  * @param avatar The avatar data of the webhook
  */
case class CreateWebhookData(name: String, avatar: Option[ImageData]) {
  require(name.length >= 2 && name.length <= 32, "Webhook name must be between 2 and 32 characters")
}

/**
  * Create a new webhook in a channel.
  */
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

/**
  * Get the webhooks in a channel.
  */
case class GetChannelWebhooks(channelId: TextChannelId) extends NoParamsNiceResponseRequest[Seq[Webhook]] {
  override def route: RequestRoute = Routes.getChannelWebhooks(channelId)

  override def responseDecoder: Decoder[Seq[Webhook]] = Decoder[Seq[Webhook]]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * Get the webhooks in a guild.
  */
case class GetGuildWebhooks(guildId: GuildId) extends NoParamsNiceResponseRequest[Seq[Webhook]] {
  override def route: RequestRoute = Routes.getGuildWebhooks(guildId)

  override def responseDecoder: Decoder[Seq[Webhook]] = Decoder[Seq[Webhook]]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Get a webhook by id.
  */
case class GetWebhook(id: SnowflakeType[Webhook]) extends NoParamsNiceResponseRequest[Webhook] {
  override def route: RequestRoute = Routes.getWebhook(id)

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
}

/**
  * Get a webhook by id with a token. Doesn't require authentication.
  */
case class GetWebhookWithToken(id: SnowflakeType[Webhook], token: String) extends NoParamsNiceResponseRequest[Webhook] {
  override def route: RequestRoute = Routes.getWebhookWithToken(id, token)

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
}

/**
  * @param name Name of the webhook.
  * @param avatar The avatar data of the webhook.
  * @param channelId The channel this webhook should be moved to.
  */
case class ModifyWebhookData(
    name: JsonOption[String] = JsonUndefined,
    avatar: JsonOption[ImageData] = JsonUndefined,
    channelId: JsonOption[TextGuildChannelId] = JsonUndefined
)
object ModifyWebhookData {
  implicit val encoder: Encoder[ModifyWebhookData] = (a: ModifyWebhookData) =>
    JsonOption.removeUndefinedToObj(
      "name"       -> a.name.map(_.asJson),
      "avatar"     -> a.avatar.map(_.asJson),
      "channel_id" -> a.channelId.map(_.asJson)
    )
}

/**
  * Modify a webhook.
  */
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

/**
  * Modify a webhook with a token. Doesn't require authentication
  */
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

/**
  * Delete a webhook.
  */
case class DeleteWebhook(
    id: SnowflakeType[Webhook],
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteWebhook] {
  override def route: RequestRoute = Routes.deleteWebhook(id)

  override def requiredPermissions: Permission = Permission.ManageWebhooks

  override def withReason(reason: String): DeleteWebhook = copy(reason = Some(reason))
}

/**
  * Delete a webhook with a token. Doesn't require authentication
  */
case class DeleteWebhookWithToken(
    id: SnowflakeType[Webhook],
    token: String,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteWebhookWithToken] {
  override def route: RequestRoute = Routes.deleteWebhookWithToken(id, token)

  override def requiredPermissions: Permission = Permission.ManageWebhooks

  override def withReason(reason: String): DeleteWebhookWithToken = copy(reason = Some(reason))
}

/*
TODO
case class ExecuteWebhook(id: Snowflake, token: String, params: Nothing) extends SimpleRESTRequest[Nothing, Nothing] {
  override def route: RestRoute = Routes.deleteWebhookWithToken(token, id)
}
 */
