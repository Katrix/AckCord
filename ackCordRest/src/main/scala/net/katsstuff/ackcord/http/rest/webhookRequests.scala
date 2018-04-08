package net.katsstuff.ackcord.http.rest

import scala.language.higherKinds

import akka.NotUsed
import cats.Monad
import io.circe._
import io.circe.generic.extras.semiauto._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.Routes
import net.katsstuff.ackcord.http.requests.RequestRoute
import net.katsstuff.ackcord.CacheSnapshotLike
import net.katsstuff.ackcord.data.DiscordProtocol._

/**
  * @param name Name of the webhook
  * @param avatar The avatar data of the webhook
  */
case class CreateWebhookData(name: String, avatar: ImageData) {
  require(name.length >= 2 && name.length <= 32, "Webhook name must be between 2 and 32 characters")
}

/**
  * Create a new webhook in a channel.
  */
case class CreateWebhook[Ctx](
    channelId: ChannelId,
    params: CreateWebhookData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[CreateWebhook[Ctx], CreateWebhookData, Webhook, Ctx] {
  override def route:         RequestRoute               = Routes.createWebhook(channelId)
  override def paramsEncoder: Encoder[CreateWebhookData] = deriveEncoder[CreateWebhookData]

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshotLike[F]): F[Boolean] =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): CreateWebhook[Ctx] = copy(reason = Some(reason))
}

/**
  * Get the webhooks in a channel.
  */
case class GetChannelWebhooks[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Seq[Webhook], Ctx] {
  override def route: RequestRoute = Routes.getChannelWebhooks(channelId)

  override def responseDecoder: Decoder[Seq[Webhook]] = Decoder[Seq[Webhook]]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshotLike[F]): F[Boolean] =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * Get the webhooks in a guild.
  */
case class GetGuildWebhooks[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Seq[Webhook], Ctx] {
  override def route: RequestRoute = Routes.getGuildWebhooks(guildId)

  override def responseDecoder: Decoder[Seq[Webhook]] = Decoder[Seq[Webhook]]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshotLike[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Get a webhook by id.
  */
case class GetWebhook[Ctx](id: SnowflakeType[Webhook], context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Webhook, Ctx] {
  override def route: RequestRoute = Routes.getWebhook(id)

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
}

/**
  * Get a webhook by id with a token. Doesn't require authentication.
  */
case class GetWebhookWithToken[Ctx](id: SnowflakeType[Webhook], token: String, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Webhook, Ctx] {
  override def route: RequestRoute = Routes.getWebhookWithToken(token, id)

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks
}

/**
  * @param name Name of the webhook.
  * @param avatar The avatar data of the webhook.
  * @param channelId The channel this webhook should be moved to.
  */
case class ModifyWebhookData(
    name: Option[String] = None,
    avatar: Option[ImageData] = None,
    channelId: Option[ChannelId] = None
)

/**
  * Modify a webhook.
  */
case class ModifyWebhook[Ctx](
    id: SnowflakeType[Webhook],
    params: ModifyWebhookData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[ModifyWebhook[Ctx], ModifyWebhookData, Webhook, Ctx] {
  override def route:         RequestRoute               = Routes.getWebhook(id)
  override def paramsEncoder: Encoder[ModifyWebhookData] = deriveEncoder[ModifyWebhookData]

  override def responseDecoder: Decoder[Webhook] = Decoder[Webhook]

  override def requiredPermissions:        Permission         = Permission.ManageWebhooks
  override def withReason(reason: String): ModifyWebhook[Ctx] = copy(reason = Some(reason))
}

/**
  * Modify a webhook with a token. Doesn't require authentication
  */
case class ModifyWebhookWithToken[Ctx](
    id: SnowflakeType[Webhook],
    token: String,
    params: ModifyWebhookData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[ModifyWebhookWithToken[Ctx], ModifyWebhookData, Webhook, Ctx] {
  require(params.channelId.isEmpty, "ModifyWebhookWithToken does not accept a channelId in the request")
  override def route: RequestRoute = Routes.getWebhookWithToken(token, id)

  override def paramsEncoder:   Encoder[ModifyWebhookData] = deriveEncoder[ModifyWebhookData]
  override def responseDecoder: Decoder[Webhook]           = Decoder[Webhook]

  override def requiredPermissions: Permission = Permission.ManageWebhooks

  override def withReason(reason: String): ModifyWebhookWithToken[Ctx] = copy(reason = Some(reason))
}

/**
  * Delete a webhook.
  */
case class DeleteWebhook[Ctx](
    id: SnowflakeType[Webhook],
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteWebhook[Ctx], Ctx] {
  override def route: RequestRoute = Routes.deleteWebhook(id)

  override def requiredPermissions: Permission = Permission.ManageWebhooks

  override def withReason(reason: String): DeleteWebhook[Ctx] = copy(reason = Some(reason))
}

/**
  * Delete a webhook with a token. Doesn't require authentication
  */
case class DeleteWebhookWithToken[Ctx](
    id: SnowflakeType[Webhook],
    token: String,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteWebhookWithToken[Ctx], Ctx] {
  override def route: RequestRoute = Routes.deleteWebhookWithToken(token, id)

  override def requiredPermissions: Permission = Permission.ManageWebhooks

  override def withReason(reason: String): DeleteWebhookWithToken[Ctx] = copy(reason = Some(reason))
}

/*
TODO
case class ExecuteWebhook[Ctx](id: Snowflake, token: String, params: Nothing, context: Ctx = NotUsed: NotUsed) extends SimpleRESTRequest[Nothing, Nothing, Ctx] {
  override def route: RestRoute = Routes.deleteWebhookWithToken(token, id)
}
 */
