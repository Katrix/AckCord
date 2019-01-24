/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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

import scala.language.implicitConversions

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{HttpMethod, Uri}
import net.katsstuff.ackcord.AckCord
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.requests.RequestRoute

/**
  * All the routes used by AckCord
  */
object Routes {

  val discord = "discordapp.com"

  val base: Route =
    Route(s"https://$discord/api/v${AckCord.DiscordApiVersion}", s"https://$discord/api/v${AckCord.DiscordApiVersion}")
  val cdn: Route = Route(s"https://cdn.$discord/", s"https://cdn.$discord/")

  //WS
  val gateway: Route    = base / "gateway"
  val botGateway: Route = gateway / "bot"

  //REST
  type InviteCode = String

  /**
    * Emoji is a bit more complicated than the others.
    * If it's a custom emoji, the format is `name:id` for example `rust:232722868583006209`.
    * If it's a normal emoji, it's encoded using percent encoding, for example `%F0%9F%92%A9`.
    */
  type Emoji = String

  case class Route(rawRoute: String, applied: Uri) {
    require(
      rawRoute.count(_ == '/') == applied.toString.count(_ == '/'),
      "Raw route and applied route are unbalanced"
    )

    def toRequest(method: HttpMethod): RequestRoute = RequestRoute(this, method)

    def /(next: String): Route =
      if (next.isEmpty) this
      else Route(s"$rawRoute/$next", s"$applied/$next")

    def /(parameter: MinorParameter): Route =
      Route(s"$rawRoute/${parameter.name}", s"$applied/${parameter.value}")

    def /(parameter: MajorParameter): Route =
      Route(s"$rawRoute/${parameter.value}", s"$applied/${parameter.value}")

    def /(other: Route): Route =
      if (other.rawRoute.isEmpty) this
      else Route(s"$rawRoute/${other.rawRoute}", s"$applied/${other.applied}")

    def ++(other: String) = Route(s"$rawRoute$other", s"$applied$other")
  }

  sealed trait Parameter
  case class MinorParameter(name: String, value: String)
  case class MajorParameter(value: String)

  implicit def guildIdParam(id: GuildId): MajorParameter                  = MajorParameter(id.asString)
  implicit def channelIdParam(id: ChannelId): MajorParameter              = MajorParameter(id.asString)
  implicit def webhookIdParam(id: SnowflakeType[Webhook]): MajorParameter = MajorParameter(id.asString)

  implicit def messageIdParam(id: MessageId): MinorParameter = MinorParameter("messageId", id.asString)
  def emojiParam(emoji: Emoji): MinorParameter               = MinorParameter("emoji", emoji)
  implicit def emojiIdParam(id: EmojiId): MinorParameter     = MinorParameter("emojiId", id.asString)
  implicit def userIdParam(id: UserId): MinorParameter       = MinorParameter("userId", id.asString)
  implicit def permissionOverwriteIdParam(id: UserOrRoleId): MinorParameter =
    MinorParameter("permissionOverwriteId", id.asString)
  implicit def roldIdParam(id: RoleId): MinorParameter               = MinorParameter("roldId", id.asString)
  implicit def integrationIdParam(id: IntegrationId): MinorParameter = MinorParameter("integrationId", id.asString)
  def inviteCodeParam(code: InviteCode): MinorParameter              = MinorParameter("inviteCode", code)
  def tokenParam(token: String): MinorParameter                      = MinorParameter("token", token)
  def hashParam(hash: String): MinorParameter                        = MinorParameter("hash", hash)
  implicit def rawSnowflakeIdParam(id: RawSnowflake): MinorParameter = MinorParameter("applicationId", id.asString)

  implicit class Func1ToRequestSyntax[T1](val f: T1 => Route) extends AnyVal {
    def toRequest(method: HttpMethod): T1 => RequestRoute = f.andThen(RequestRoute(_, method))

    def /(next: String): T1 => Route = f.andThen(_ / next)

    def /(parameter: MinorParameter): T1 => Route = f.andThen(_ / parameter)

    def /(parameter: MajorParameter): T1 => Route = f.andThen(_ / parameter)

    def /(other: Route): T1 => Route = f.andThen(_ / other)

    def ++(other: String): T1 => Route = f.andThen(_ ++ other)
  }

  implicit class Func2ToRequestSyntax[T1, T2](val f: (T1, T2) => Route) extends AnyVal {
    def toRequest(method: HttpMethod): (T1, T2) => RequestRoute = f.andThen(RequestRoute(_, method))

    def /(next: String): (T1, T2) => Route = f.andThen(_ / next)

    def /(parameter: MinorParameter): (T1, T2) => Route = f.andThen(_ / parameter)

    def /(parameter: MajorParameter): (T1, T2) => Route = f.andThen(_ / parameter)

    def /(other: Route): (T1, T2) => Route = f.andThen(_ / other)

    def ++(other: String): (T1, T2) => Route = f.andThen(_ ++ other)
  }

  implicit class Func3ToRequestSyntax[T1, T2, T3](val f: (T1, T2, T3) => Route) extends AnyVal {
    def toRequest(method: HttpMethod): (T1, T2, T3) => RequestRoute = f.andThen(RequestRoute(_, method))

    def /(next: String): (T1, T2, T3) => Route = f.andThen(_ / next)

    def /(parameter: MinorParameter): (T1, T2, T3) => Route = f.andThen(_ / parameter)

    def /(parameter: MajorParameter): (T1, T2, T3) => Route = f.andThen(_ / parameter)

    def /(other: Route): (T1, T2, T3) => Route = f.andThen(_ / other)

    def ++(other: String): (T1, T2, T3) => Route = f.andThen(_ ++ other)
  }

  implicit class Func2Syntax[T1, T2, R](val f: (T1, T2) => R) extends AnyVal {
    def andThen[A](g: R => A): (T1, T2) => A = (t1, t2) => g(f(t1, t2))
  }

  implicit class Func3Syntax[T1, T2, T3, R](val f: (T1, T2, T3) => R) extends AnyVal {
    def andThen[A](g: R => A): (T1, T2, T3) => A = (t1, t2, t3) => g(f(t1, t2, t3))
  }

  //Audit log

  val guilds: Route           = base / "guilds"
  val guild: GuildId => Route = guildId => guilds / guildId

  val getGuildAuditLogs: GuildId => RequestRoute = guild / "audit-logs" toRequest GET

  //Channel routes

  val channel: ChannelId => Route = channelId => base / "channels" / channelId

  val getChannel: ChannelId => RequestRoute         = channel.toRequest(GET)
  val modifyChannelPut: ChannelId => RequestRoute   = channel.toRequest(PUT)
  val modifyChannelPatch: ChannelId => RequestRoute = channel.toRequest(PATCH)
  val deleteCloseChannel: ChannelId => RequestRoute = channel.toRequest(DELETE)

  val channelMessages: ChannelId => Route             = channel / "messages"
  val channelMessage: (MessageId, ChannelId) => Route = (messageId, channelId) => channelMessages(channelId) / messageId

  val getChannelMessages: ChannelId => RequestRoute             = channelMessages.toRequest(GET)
  val getChannelMessage: (MessageId, ChannelId) => RequestRoute = channelMessage.toRequest(GET)
  val createMessage: ChannelId => RequestRoute                  = channelMessages.toRequest(POST)
  val editMessage: (MessageId, ChannelId) => RequestRoute       = channelMessage.toRequest(PATCH)
  val deleteMessage: (MessageId, ChannelId) => RequestRoute     = channelMessage.toRequest(DELETE)
  val bulkDeleteMessages: ChannelId => RequestRoute             = channelMessages / "bulk-delete" toRequest POST

  val reactions: (MessageId, ChannelId) => Route = channelMessage / "reactions"
  val emojiReactions: (Emoji, MessageId, ChannelId) => Route =
    Function.uncurried(emoji => (reactions / emojiParam(emoji)).curried)
  val modifyMeReaction: (Emoji, MessageId, ChannelId) => Route = emojiReactions / "@me"

  val createReaction: (Emoji, MessageId, ChannelId) => RequestRoute    = modifyMeReaction.toRequest(PUT)
  val deleteOwnReaction: (Emoji, MessageId, ChannelId) => RequestRoute = modifyMeReaction.toRequest(DELETE)
  val deleteUserReaction: (UserId, Emoji, MessageId, ChannelId) => RequestRoute =
    Function.uncurried(userId => (emojiReactions / userId toRequest DELETE).curried)

  val getReactions: (Emoji, MessageId, ChannelId) => RequestRoute = emojiReactions.toRequest(GET)
  val deleteAllReactions: (MessageId, ChannelId) => RequestRoute  = reactions.toRequest(DELETE)

  val channelPermissions: (UserOrRoleId, ChannelId) => Route = (overwrite, channelId) =>
    channel(channelId) / "permissions" / overwrite

  val editChannelPermissions: (UserOrRoleId, ChannelId) => RequestRoute   = channelPermissions.toRequest(PUT)
  val deleteChannelPermissions: (UserOrRoleId, ChannelId) => RequestRoute = channelPermissions.toRequest(DELETE)

  val channelInvites: ChannelId => Route = channel / "invites"

  val getChannelInvites: ChannelId => RequestRoute    = channelInvites.toRequest(GET)
  val createChannelInvites: ChannelId => RequestRoute = channelInvites.toRequest(POST)

  val triggerTyping: ChannelId => RequestRoute = channel / "typing" toRequest POST

  val pinnedMessage: ChannelId => Route           = channel / "pins"
  val getPinnedMessage: ChannelId => RequestRoute = pinnedMessage.toRequest(GET)

  val channelPinnedMessage: (MessageId, ChannelId) => Route = (messageId, channelId) =>
    pinnedMessage(channelId) / messageId
  val addPinnedChannelMessage: (MessageId, ChannelId) => RequestRoute    = channelPinnedMessage.toRequest(PUT)
  val deletePinnedChannelMessage: (MessageId, ChannelId) => RequestRoute = channelPinnedMessage.toRequest(DELETE)

  val groupDmRecipient: (UserId, ChannelId) => Route              = (userId, channelId) => channel(channelId) / userId
  val groupDmAddRecipient: (UserId, ChannelId) => RequestRoute    = groupDmRecipient.toRequest(PUT)
  val groupDmRemoveRecipient: (UserId, ChannelId) => RequestRoute = groupDmRecipient.toRequest(DELETE)

  //Emoji routes

  val guildEmojis: GuildId => Route             = guild / "emojis"
  val listGuildEmojis: GuildId => RequestRoute  = guildEmojis.toRequest(GET)
  val createGuildEmoji: GuildId => RequestRoute = guildEmojis.toRequest(POST)

  val guildEmoji: (EmojiId, GuildId) => Route              = (emojiId, guildId) => guildEmojis(guildId) / emojiId
  val getGuildEmoji: (EmojiId, GuildId) => RequestRoute    = guildEmoji.toRequest(GET)
  val modifyGuildEmoji: (EmojiId, GuildId) => RequestRoute = guildEmoji.toRequest(PATCH)
  val deleteGuildEmoji: (EmojiId, GuildId) => RequestRoute = guildEmoji.toRequest(DELETE)

  //Guild routes
  val createGuild: RequestRoute            = guilds.toRequest(POST)
  val getGuild: GuildId => RequestRoute    = guild.toRequest(GET)
  val modifyGuild: GuildId => RequestRoute = guild.toRequest(PATCH)
  val deleteGuild: GuildId => RequestRoute = guild.toRequest(DELETE)

  val guildChannels: GuildId => Route                       = guild / "channels"
  val getGuildChannels: GuildId => RequestRoute             = guildChannels.toRequest(GET)
  val createGuildChannel: GuildId => RequestRoute           = guildChannels.toRequest(POST)
  val modifyGuildChannelsPositions: GuildId => RequestRoute = guildChannels.toRequest(PATCH)

  val guildMembers: GuildId => Route                       = guild / "members"
  val guildMember: (UserId, GuildId) => Route              = (userId, guildId) => guildMembers(guildId) / userId
  val getGuildMember: (UserId, GuildId) => RequestRoute    = guildMember.toRequest(GET)
  val listGuildMembers: GuildId => RequestRoute            = guildMembers.toRequest(GET)
  val addGuildMember: (UserId, GuildId) => RequestRoute    = guildMember.toRequest(PUT)
  val modifyGuildMember: (UserId, GuildId) => RequestRoute = guildMember.toRequest(PATCH)
  val removeGuildMember: (UserId, GuildId) => RequestRoute = guildMember.toRequest(DELETE)
  val modifyCurrentNick: GuildId => RequestRoute           = guildMembers / "@me" / "nick" toRequest PATCH

  val guildMemberRole: (RoleId, UserId, GuildId) => Route =
    Function.uncurried(roleId => (guildMember / "roles" / roleId).curried)

  val addGuildMemberRole: (RoleId, UserId, GuildId) => RequestRoute    = guildMemberRole.toRequest(PUT)
  val removeGuildMemberRole: (RoleId, UserId, GuildId) => RequestRoute = guildMemberRole.toRequest(DELETE)

  val guildBans: GuildId => Route                = guild / "bans"
  val guildMemberBan: (UserId, GuildId) => Route = (userId, guildId) => guildBans(guildId) / userId

  val getGuildBans: GuildId => RequestRoute                   = guildBans.toRequest(GET)
  val getGuildBan: (UserId, GuildId) => RequestRoute          = guildMemberBan.toRequest(GET)
  val createGuildMemberBan: (UserId, GuildId) => RequestRoute = guildMemberBan.toRequest(PUT)
  val removeGuildMemberBan: (UserId, GuildId) => RequestRoute = guildMemberBan.toRequest(DELETE)

  val guildRoles: GuildId => Route                      = guild / "roles"
  val getGuildRole: GuildId => RequestRoute             = guildRoles.toRequest(GET)
  val createGuildRole: GuildId => RequestRoute          = guildRoles.toRequest(POST)
  val modifyGuildRolePositions: GuildId => RequestRoute = guildRoles.toRequest(PATCH)

  val guildRole: (RoleId, GuildId) => Route              = (roleId, guildId) => guildRoles(guildId) / roleId
  val modifyGuildRole: (RoleId, GuildId) => RequestRoute = guildRole.toRequest(PATCH)
  val deleteGuildRole: (RoleId, GuildId) => RequestRoute = guildRole.toRequest(DELETE)

  val guildPrune: GuildId => Route                = guild / "prune"
  val getGuildPruneCount: GuildId => RequestRoute = guildPrune.toRequest(GET)
  val beginGuildPrune: GuildId => RequestRoute    = guildPrune.toRequest(POST)

  val getGuildVoiceRegions: GuildId => RequestRoute = guild / "regions" toRequest GET
  val getGuildInvites: GuildId => RequestRoute      = guild / "invites" toRequest GET

  val guildIntegrations: GuildId => Route              = guild / "integrations"
  val getGuildIntegrations: GuildId => RequestRoute    = guildIntegrations.toRequest(GET)
  val createGuildIntegrations: GuildId => RequestRoute = guildIntegrations.toRequest(POST)

  val guildIntegration: (IntegrationId, GuildId) => Route = (integrationId, guildId) =>
    guildIntegrations(guildId) / integrationId
  val modifyGuildIntegration: (IntegrationId, GuildId) => RequestRoute = guildIntegration.toRequest(PATCH)
  val deleteGuildIntegration: (IntegrationId, GuildId) => RequestRoute = guildIntegration.toRequest(DELETE)
  val syncGuildIntegration: (IntegrationId, GuildId) => RequestRoute   = guildIntegration / "sync" toRequest PATCH

  val guildEmbed: GuildId => Route               = guild / "embed"
  val getGuildEmbed: GuildId => RequestRoute     = guildEmbed.toRequest(GET)
  val modifyGuildEmbed: GuildId => RequestRoute  = guildEmbed.toRequest(PATCH)
  val getGuildVanityUrl: GuildId => RequestRoute = guild / "vanity-url" toRequest GET

  val getGuildWidgetImage: (WidgetImageStyle, GuildId) => RequestRoute =
    (style, guildId) => guild(guildId) / "widget.png" ++ s"?style=${style.name}" toRequest GET

  //Invites
  val invites: Route                           = base / "invites"
  val inviteCode: InviteCode => Route          = code => invites / inviteCodeParam(code)
  val getInvite: InviteCode => RequestRoute    = inviteCode.toRequest(GET)
  val deleteInvite: InviteCode => RequestRoute = inviteCode.toRequest(DELETE)

  //Users
  val users: Route                 = base / "users"
  val currentUser: Route           = users / "@me"
  val getCurrentUser: RequestRoute = currentUser.toRequest(GET)

  val getUser: UserId => RequestRoute     = userId => users / userId toRequest GET
  val modifyCurrentUser: RequestRoute     = currentUser.toRequest(PATCH)
  val currentUserGuilds: Route            = currentUser / "guilds"
  val getCurrentUserGuilds: RequestRoute  = currentUserGuilds.toRequest(GET)
  val leaveGuild: GuildId => RequestRoute = guildId => currentUserGuilds / guildId toRequest DELETE

  val userDMs: Route           = currentUser / "channels"
  val getUserDMs: RequestRoute = userDMs.toRequest(GET)
  val createDM: RequestRoute   = userDMs.toRequest(POST)

  val getUserConnections: RequestRoute = currentUser / "connections" toRequest GET

  //Voice
  val listVoiceRegions: RequestRoute = base / "voice" / "regions" toRequest GET

  //WebHook
  val webhook: SnowflakeType[Webhook] => Route = id => base / "webhooks" / id
  val webhookWithToken: (String, SnowflakeType[Webhook]) => Route = (token, webhookId) =>
    webhook(webhookId) / tokenParam(token)
  val channelWebhooks: ChannelId => Route = channel / "webhooks"

  val createWebhook: ChannelId => RequestRoute                                 = channelWebhooks.toRequest(POST)
  val getChannelWebhooks: ChannelId => RequestRoute                            = channelWebhooks.toRequest(GET)
  val getGuildWebhooks: GuildId => RequestRoute                                = guild / "webhooks" toRequest GET
  val getWebhook: SnowflakeType[Webhook] => RequestRoute                       = webhook.toRequest(GET)
  val getWebhookWithToken: (String, SnowflakeType[Webhook]) => RequestRoute    = webhookWithToken.toRequest(GET)
  val modifyWebhook: SnowflakeType[Webhook] => RequestRoute                    = webhook.toRequest(PATCH)
  val modifyWebhookWithToken: (String, SnowflakeType[Webhook]) => RequestRoute = webhookWithToken.toRequest(PATCH)
  val deleteWebhook: SnowflakeType[Webhook] => RequestRoute                    = webhook.toRequest(DELETE)
  val deleteWebhookWithToken: (String, SnowflakeType[Webhook]) => RequestRoute = webhookWithToken.toRequest(DELETE)
  val executeWebhook: (String, SnowflakeType[Webhook]) => RequestRoute         = webhookWithToken.toRequest(POST)
  val executeSlackWebhook: (String, SnowflakeType[Webhook]) => RequestRoute    = webhookWithToken / "slack" toRequest POST
  val executeGithubWebhook
    : (String, SnowflakeType[Webhook]) => RequestRoute = webhookWithToken / "github" toRequest POST

  def imageExtra(format: ImageFormat, size: Int): String = s".${format.extension}?size=$size"

  //Images
  val emojiImage: (EmojiId, ImageFormat, Int) => RequestRoute = (emojiId, format, size) =>
    cdn / "emojis" / emojiId ++ imageExtra(format, size) toRequest GET
  val guildIconImage: (GuildId, String, ImageFormat, Int) => RequestRoute = (guildId, hash, format, size) =>
    cdn / "icons" / guildId / hashParam(hash) ++ imageExtra(format, size) toRequest GET
  val guildSplashImage: (GuildId, String, ImageFormat, Int) => RequestRoute = (guildId, hash, format, size) =>
    cdn / "splashes" / guildId / hashParam(hash) ++ imageExtra(format, size) toRequest GET
  val defaultUserAvatarImage: (Int, ImageFormat, Int) => RequestRoute = (discriminator, format, size) =>
    cdn / "embed" / "avatars" / s"${discriminator % 5}" ++ imageExtra(format, size) toRequest GET
  val userAvatarImage: (UserId, String, ImageFormat, Int) => RequestRoute = (userId, hash, format, size) =>
    cdn / "avatars" / userId / hashParam(hash) ++ imageExtra(format, size) toRequest GET
  val applicationIconImage: (RawSnowflake, String, ImageFormat, Int) => RequestRoute =
    (applicationId, hash, format, size) =>
      cdn / "app-icons" / applicationId / hashParam(hash) ++ imageExtra(format, size) toRequest GET

  //OAuth
  val oAuth2: Route          = base / "oauth2"
  val oAuth2Authorize: Route = oAuth2 / "authorize"
  val oAuth2Token: Route     = oAuth2 / "token"
  val oAuth2Revoke: Route    = oAuth2Token / "revoke"
}
