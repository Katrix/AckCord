//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/Webhook.yaml

import ackcord.data.base._
import io.circe.Json

/**
  * Webhooks are a low-effort way to post messages to channels in Discord. They
  * do not require a bot user or authentication to use.
  */
class Webhook(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

  /** The id of the webhook */
  @inline def id: WebhookId = selectDynamic[WebhookId]("id")

  @inline def withId(newValue: WebhookId): Webhook = objWith(Webhook, "id", newValue)

  /** The type of the webhook */
  @inline def tpe: Webhook.WebhookType = selectDynamic[Webhook.WebhookType]("type")

  @inline def withTpe(newValue: Webhook.WebhookType): Webhook = objWith(Webhook, "type", newValue)

  /** The guild id this webhook is for, if any */
  @inline def guildId: JsonOption[GuildId] = selectDynamic[JsonOption[GuildId]]("guild_id")

  @inline def withGuildId(newValue: JsonOption[GuildId]): Webhook = objWithUndef(Webhook, "guild_id", newValue)

  /** The channel id this webhook is for, if any */
  @inline def channelId: Option[TextGuildChannelId] = selectDynamic[Option[TextGuildChannelId]]("channel_id")

  @inline def withChannelId(newValue: Option[TextGuildChannelId]): Webhook =
    objWith(Webhook, "channel_id", newValue)

  /**
    * The user this webhook was created by (not returned when getting a webhook
    * with its token)
    */
  @inline def user: UndefOr[User] = selectDynamic[UndefOr[User]]("user")

  @inline def withUser(newValue: UndefOr[User]): Webhook = objWithUndef(Webhook, "user", newValue)

  /** The default name of the webhook */
  @inline def name: Option[String] = selectDynamic[Option[String]]("name")

  @inline def withName(newValue: Option[String]): Webhook = objWith(Webhook, "name", newValue)

  /** The default user avatar hash of the webhook */
  @inline def avatar: Option[ImageHash] = selectDynamic[Option[ImageHash]]("avatar")

  @inline def withAvatar(newValue: Option[ImageHash]): Webhook = objWith(Webhook, "avatar", newValue)

  /** The secure token of the webhook (returned for Incoming Webhooks) */
  @inline def token: UndefOr[String] = selectDynamic[UndefOr[String]]("token")

  @inline def withToken(newValue: UndefOr[String]): Webhook = objWithUndef(Webhook, "token", newValue)

  /** The bot/OAuth2 application that created this webhook */
  @inline def applicationId: Option[ApplicationId] = selectDynamic[Option[ApplicationId]]("application_id")

  @inline def withApplicationId(newValue: Option[ApplicationId]): Webhook =
    objWith(Webhook, "application_id", newValue)

  /**
    * The guild of the channel that this webhook is following (returned for
    * Channel Follower Webhooks)
    */
  @inline def sourceGuild: UndefOr[Webhook.WebhookGuild] = selectDynamic[UndefOr[Webhook.WebhookGuild]]("source_guild")

  @inline def withSourceGuild(newValue: UndefOr[Webhook.WebhookGuild]): Webhook =
    objWithUndef(Webhook, "source_guild", newValue)

  /**
    * The channel that this webhook is following (returned for Channel Follower
    * Webhooks)
    */
  @inline def sourceChannel: UndefOr[Webhook.WebhookChannel] =
    selectDynamic[UndefOr[Webhook.WebhookChannel]]("source_channel")

  @inline def withSourceChannel(newValue: UndefOr[Webhook.WebhookChannel]): Webhook =
    objWithUndef(Webhook, "source_channel", newValue)

  /**
    * The url used for executing the webhook (returned by the webhooks OAuth2
    * flow)
    */
  @inline def url: UndefOr[String] = selectDynamic[UndefOr[String]]("url")

  @inline def withUrl(newValue: UndefOr[String]): Webhook = objWithUndef(Webhook, "url", newValue)

  override def values: Seq[() => Any] = Seq(
    () => id,
    () => tpe,
    () => guildId,
    () => channelId,
    () => user,
    () => name,
    () => avatar,
    () => token,
    () => applicationId,
    () => sourceGuild,
    () => sourceChannel,
    () => url
  )
}
object Webhook extends DiscordObjectCompanion[Webhook] {
  def makeRaw(json: Json, cache: Map[String, Any]): Webhook = new Webhook(json, cache)

  /**
    * @param id
    *   The id of the webhook
    * @param tpe
    *   The type of the webhook
    * @param guildId
    *   The guild id this webhook is for, if any
    * @param channelId
    *   The channel id this webhook is for, if any
    * @param user
    *   The user this webhook was created by (not returned when getting a
    *   webhook with its token)
    * @param name
    *   The default name of the webhook
    * @param avatar
    *   The default user avatar hash of the webhook
    * @param token
    *   The secure token of the webhook (returned for Incoming Webhooks)
    * @param applicationId
    *   The bot/OAuth2 application that created this webhook
    * @param sourceGuild
    *   The guild of the channel that this webhook is following (returned for
    *   Channel Follower Webhooks)
    * @param sourceChannel
    *   The channel that this webhook is following (returned for Channel
    *   Follower Webhooks)
    * @param url
    *   The url used for executing the webhook (returned by the webhooks OAuth2
    *   flow)
    */
  def make20(
      id: WebhookId,
      tpe: Webhook.WebhookType,
      guildId: JsonOption[GuildId] = JsonUndefined(Some("guild_id")),
      channelId: Option[TextGuildChannelId],
      user: UndefOr[User] = UndefOrUndefined(Some("user")),
      name: Option[String],
      avatar: Option[ImageHash],
      token: UndefOr[String] = UndefOrUndefined(Some("token")),
      applicationId: Option[ApplicationId],
      sourceGuild: UndefOr[Webhook.WebhookGuild] = UndefOrUndefined(Some("source_guild")),
      sourceChannel: UndefOr[Webhook.WebhookChannel] = UndefOrUndefined(Some("source_channel")),
      url: UndefOr[String] = UndefOrUndefined(Some("url"))
  ): Webhook = makeRawFromFields(
    "id"              := id,
    "type"            := tpe,
    "guild_id"       :=? guildId,
    "channel_id"      := channelId,
    "user"           :=? user,
    "name"            := name,
    "avatar"          := avatar,
    "token"          :=? token,
    "application_id"  := applicationId,
    "source_guild"   :=? sourceGuild,
    "source_channel" :=? sourceChannel,
    "url"            :=? url
  )

  class WebhookGuild(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    @inline def id: GuildId = selectDynamic[GuildId]("id")

    @inline def withId(newValue: GuildId): WebhookGuild = objWith(WebhookGuild, "id", newValue)

    @inline def name: String = selectDynamic[String]("name")

    @inline def withName(newValue: String): WebhookGuild = objWith(WebhookGuild, "name", newValue)

    @inline def icon: Option[ImageHash] = selectDynamic[Option[ImageHash]]("icon")

    @inline def withIcon(newValue: Option[ImageHash]): WebhookGuild = objWith(WebhookGuild, "icon", newValue)

    override def values: Seq[() => Any] = Seq(() => id, () => name, () => icon)
  }
  object WebhookGuild extends DiscordObjectCompanion[WebhookGuild] {
    def makeRaw(json: Json, cache: Map[String, Any]): WebhookGuild = new WebhookGuild(json, cache)

    def make20(id: GuildId, name: String, icon: Option[ImageHash]): WebhookGuild =
      makeRawFromFields("id" := id, "name" := name, "icon" := icon)
  }

  class WebhookChannel(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    @inline def id: TextGuildChannelId = selectDynamic[TextGuildChannelId]("id")

    @inline def withId(newValue: TextGuildChannelId): WebhookChannel =
      objWith(WebhookChannel, "id", newValue)

    @inline def name: String = selectDynamic[String]("name")

    @inline def withName(newValue: String): WebhookChannel = objWith(WebhookChannel, "name", newValue)

    override def values: Seq[() => Any] = Seq(() => id, () => name)
  }
  object WebhookChannel extends DiscordObjectCompanion[WebhookChannel] {
    def makeRaw(json: Json, cache: Map[String, Any]): WebhookChannel =
      new WebhookChannel(json, cache)

    def make20(id: TextGuildChannelId, name: String): WebhookChannel =
      makeRawFromFields("id" := id, "name" := name)
  }

  sealed case class WebhookType private (value: Int) extends DiscordEnum[Int]
  object WebhookType                                 extends DiscordEnumCompanion[Int, WebhookType] {

    /**
      * Incoming Webhooks can post messages to channels with a generated token
      */
    val Incoming: WebhookType = WebhookType(1)

    /**
      * Channel Follower Webhooks are internal webhooks used with Channel
      * Following to post new messages into channels
      */
    val ChannelFollower: WebhookType = WebhookType(2)

    /** Application webhooks are webhooks used with Interactions */
    val Application: WebhookType = WebhookType(3)

    def unknown(value: Int): WebhookType = new WebhookType(value)

    val values: Seq[WebhookType] = Seq(Incoming, ChannelFollower, Application)
  }
}
