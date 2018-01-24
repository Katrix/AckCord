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

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri
import net.katsstuff.ackcord.AckCord
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.requests.ImageRequests.ImageFormat
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
  val gateway:    Route = base / "gateway"
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
      rawRoute.count(_ == '/') == applied.toString().count(_ == '/'),
      "Raw route and applied route are unbalanced"
    )

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

  def guildIdParam(id: GuildId)                  = MajorParameter(id.asString)
  def channelIdParam(id: ChannelId)              = MajorParameter(id.asString)
  def webhookIdParam(id: SnowflakeType[Webhook]) = MajorParameter(id.asString)

  def messageIdParam(id: MessageId)                = MinorParameter("messageId", id.asString)
  def emojiParam(emoji: Emoji)                     = MinorParameter("emoji", emoji)
  def emojiIdParam(id: EmojiId)                    = MinorParameter("emojiId", id.asString)
  def userIdParam(id: UserId)                      = MinorParameter("userId", id.asString)
  def permissionOverwriteIdParam(id: UserOrRoleId) = MinorParameter("permissionOverwriteId", id.asString)
  def roldIdParam(id: RoleId)                      = MinorParameter("roldId", id.asString)
  def integrationIdParam(id: IntegrationId)        = MinorParameter("integrationId", id.asString)
  def inviteCodeParam(code: InviteCode)            = MinorParameter("inviteCode", code)
  def tokenParam(token: String)                    = MinorParameter("token", token)
  def hashParam(hash: String)                      = MinorParameter("hash", hash)
  def applicationIdParam(id: RawSnowflake)         = MinorParameter("applicationId", id.asString)

  trait HasParam[A] {
    def param: Parameter
  }

  implicit class Func2Syntax[T1, T2, R](val f: (T1, T2) => R) extends AnyVal {
    def andThen[A](g: R => A): (T1, T2) => A = (t1, t2) => g(f(t1, t2))
  }

  implicit class Func3Syntax[T1, T2, T3, R](val f: (T1, T2, T3) => R) extends AnyVal {
    def andThen[A](g: R => A): (T1, T2, T3) => A = (t1, t2, t3) => g(f(t1, t2, t3))
  }

  //Audit log

  val guilds: Route            = base / "guilds"
  val guild:  GuildId => Route = guildId => guilds / guildIdParam(guildId)

  val getGuildAuditLogs: GuildId => RequestRoute = guild.andThen(route => RequestRoute(route / "audit-logs", GET))

  //Channel routes

  val channel: ChannelId => Route = channelId => base / "channels" / channelIdParam(channelId)

  val getChannel:         ChannelId => RequestRoute = channel.andThen(RequestRoute(_, GET))
  val modifyChannelPut:   ChannelId => RequestRoute = channel.andThen(RequestRoute(_, PUT))
  val modifyChannelPatch: ChannelId => RequestRoute = channel.andThen(RequestRoute(_, PATCH))
  val deleteCloseChannel: ChannelId => RequestRoute = channel.andThen(RequestRoute(_, DELETE))

  val channelMessages: ChannelId => Route = channel.andThen(_ / "messages")
  val channelMessage: (MessageId, ChannelId) => Route = (messageId, channelId) =>
    channelMessages(channelId) / messageIdParam(messageId)

  val getChannelMessages: ChannelId => RequestRoute              = channelMessages.andThen(RequestRoute(_, GET))
  val getChannelMessage:  (MessageId, ChannelId) => RequestRoute = channelMessage.andThen(RequestRoute(_, GET))
  val createMessage:      ChannelId => RequestRoute              = channelMessages.andThen(RequestRoute(_, POST))
  val editMessage:        (MessageId, ChannelId) => RequestRoute = channelMessage.andThen(RequestRoute(_, PATCH))
  val deleteMessage:      (MessageId, ChannelId) => RequestRoute = channelMessage.andThen(RequestRoute(_, DELETE))
  val bulkDeleteMessages: (ChannelId) => RequestRoute =
    channelMessages.andThen(route => RequestRoute(route / "bulk-delete", POST))

  val reactions: (MessageId, ChannelId) => Route = channelMessage.andThen(_ / "reactions")
  val emojiReactions: (Emoji, MessageId, ChannelId) => Route =
    Function.uncurried(emoji => reactions.andThen(_ / emojiParam(emoji)).curried)
  val modifyMeReaction: (Emoji, MessageId, ChannelId) => Route = emojiReactions.andThen(_ / "@me")

  val createReaction: (Emoji, MessageId, ChannelId) => RequestRoute = modifyMeReaction.andThen(RequestRoute(_, PUT))
  val deleteOwnReaction: (Emoji, MessageId, ChannelId) => RequestRoute =
    modifyMeReaction.andThen(RequestRoute(_, DELETE))
  val deleteUserReaction: (UserId, Emoji, MessageId, ChannelId) => RequestRoute = Function.uncurried(
    userId => emojiReactions.andThen(route => RequestRoute(route / userIdParam(userId), DELETE)).curried
  )

  val getReactions:       (Emoji, MessageId, ChannelId) => RequestRoute = emojiReactions.andThen(RequestRoute(_, GET))
  val deleteAllReactions: (MessageId, ChannelId) => RequestRoute        = reactions.andThen(RequestRoute(_, DELETE))

  val channelPermissions: (UserOrRoleId, ChannelId) => Route = (overwrite, channelId) =>
    channel(channelId) / "permissions" / permissionOverwriteIdParam(overwrite)

  val editChannelPermissions: (UserOrRoleId, ChannelId) => RequestRoute =
    channelPermissions.andThen(RequestRoute(_, PUT))
  val deleteChannelPermissions: (UserOrRoleId, ChannelId) => RequestRoute =
    channelPermissions.andThen(RequestRoute(_, DELETE))

  val channelInvites: ChannelId => Route = channel.andThen(_ / "invites")

  val getChannelInvites:    ChannelId => RequestRoute = channelInvites.andThen(RequestRoute(_, GET))
  val createChannelInvites: ChannelId => RequestRoute = channelInvites.andThen(RequestRoute(_, POST))

  val triggerTyping: ChannelId => RequestRoute = channel.andThen(route => RequestRoute(route / "typing", POST))

  val pinnedMessage:    ChannelId => Route        = channel.andThen(_ / "pins")
  val getPinnedMessage: ChannelId => RequestRoute = pinnedMessage.andThen(RequestRoute(_, GET))

  val channelPinnedMessage: (MessageId, ChannelId) => Route = (messageId, channelId) =>
    pinnedMessage(channelId) / messageIdParam(messageId)
  val addPinnedChannelMessage: (MessageId, ChannelId) => RequestRoute =
    channelPinnedMessage.andThen(RequestRoute(_, PUT))
  val deletePinnedChannelMessage: (MessageId, ChannelId) => RequestRoute =
    channelPinnedMessage.andThen(RequestRoute(_, DELETE))

  val groupDmRecipient:       (UserId, ChannelId) => Route        = (userId, channelId) => channel(channelId) / userIdParam(userId)
  val groupDmAddRecipient:    (UserId, ChannelId) => RequestRoute = groupDmRecipient.andThen(RequestRoute(_, PUT))
  val groupDmRemoveRecipient: (UserId, ChannelId) => RequestRoute = groupDmRecipient.andThen(RequestRoute(_, DELETE))

  //Emoji routes

  val guildEmojis:      GuildId => Route        = guild.andThen(_ / "emojis")
  val listGuildEmojis:  GuildId => RequestRoute = guildEmojis.andThen(RequestRoute(_, GET))
  val createGuildEmoji: GuildId => RequestRoute = guildEmojis.andThen(RequestRoute(_, POST))

  val guildEmoji:       (EmojiId, GuildId) => Route        = (emojiId, guildId) => guildEmojis(guildId) / emojiIdParam(emojiId)
  val getGuildEmoji:    (EmojiId, GuildId) => RequestRoute = guildEmoji.andThen(RequestRoute(_, GET))
  val modifyGuildEmoji: (EmojiId, GuildId) => RequestRoute = guildEmoji.andThen(RequestRoute(_, PATCH))
  val deleteGuildEmoji: (EmojiId, GuildId) => RequestRoute = guildEmoji.andThen(RequestRoute(_, DELETE))

  //Guild routes
  val createGuild = RequestRoute(guilds, POST)
  val getGuild:    GuildId => RequestRoute = guild.andThen(RequestRoute(_, GET))
  val modifyGuild: GuildId => RequestRoute = guild.andThen(RequestRoute(_, PATCH))
  val deleteGuild: GuildId => RequestRoute = guild.andThen(RequestRoute(_, DELETE))

  val guildChannels:                GuildId => Route        = guild.andThen(_ / "channels")
  val getGuildChannels:             GuildId => RequestRoute = guildChannels.andThen(RequestRoute(_, GET))
  val createGuildChannel:           GuildId => RequestRoute = guildChannels.andThen(RequestRoute(_, POST))
  val modifyGuildChannelsPositions: GuildId => RequestRoute = guildChannels.andThen(RequestRoute(_, PATCH))

  val guildMembers:      GuildId => Route                  = guild.andThen(_ / "members")
  val guildMember:       (UserId, GuildId) => Route        = (userId, guildId) => guildMembers(guildId) / userIdParam(userId)
  val getGuildMember:    (UserId, GuildId) => RequestRoute = guildMember.andThen(RequestRoute(_, GET))
  val listGuildMembers:  GuildId => RequestRoute           = guildMembers.andThen(RequestRoute(_, GET))
  val addGuildMember:    (UserId, GuildId) => RequestRoute = guildMember.andThen(RequestRoute(_, PUT))
  val modifyGuildMember: (UserId, GuildId) => RequestRoute = guildMember.andThen(RequestRoute(_, PATCH))
  val removeGuildMember: (UserId, GuildId) => RequestRoute = guildMember.andThen(RequestRoute(_, DELETE))
  val modifyCurrentNick: GuildId => RequestRoute =
    guildMembers.andThen(route => RequestRoute(route / "@me" / "nick", PATCH))

  val guildMemberRole: (RoleId, UserId, GuildId) => Route =
    Function.uncurried(roleId => guildMember.andThen(route => route / "roles" / roldIdParam(roleId)).curried)

  val addGuildMemberRole: (RoleId, UserId, GuildId) => RequestRoute = guildMemberRole.andThen(RequestRoute(_, PUT))
  val removeGuildMemberRole: (RoleId, UserId, GuildId) => RequestRoute =
    guildMemberRole.andThen(RequestRoute(_, DELETE))

  val guildBans:      GuildId => Route           = guild.andThen(_ / "bans")
  val guildMemberBan: (UserId, GuildId) => Route = (userId, guildId) => guildBans(guildId) / userIdParam(userId)

  val getGuildBans:         GuildId => RequestRoute           = guildBans.andThen(RequestRoute(_, GET))
  val createGuildMemberBan: (UserId, GuildId) => RequestRoute = guildMemberBan.andThen(RequestRoute(_, PUT))
  val removeGuildMemberBan: (UserId, GuildId) => RequestRoute = guildMemberBan.andThen(RequestRoute(_, DELETE))

  val guildRoles:               GuildId => Route        = guild.andThen(_ / "roles")
  val getGuildRole:             GuildId => RequestRoute = guildRoles.andThen(RequestRoute(_, GET))
  val createGuildRole:          GuildId => RequestRoute = guildRoles.andThen(RequestRoute(_, POST))
  val modifyGuildRolePositions: GuildId => RequestRoute = guildRoles.andThen(RequestRoute(_, PATCH))

  val guildRole:       (RoleId, GuildId) => Route        = (roleId, guildId) => guildRoles(guildId) / roldIdParam(roleId)
  val modifyGuildRole: (RoleId, GuildId) => RequestRoute = guildRole.andThen(RequestRoute(_, PATCH))
  val deleteGuildRole: (RoleId, GuildId) => RequestRoute = guildRole.andThen(RequestRoute(_, DELETE))

  val guildPrune:         GuildId => Route        = guild.andThen(_ / "prune")
  val getGuildPruneCount: GuildId => RequestRoute = guildPrune.andThen(RequestRoute(_, GET))
  val beginGuildPrune:    GuildId => RequestRoute = guildPrune.andThen(RequestRoute(_, POST))

  val getGuildVoiceRegions: GuildId => RequestRoute = guild.andThen(route => RequestRoute(route / "regions", GET))
  val getGuildInvites:      GuildId => RequestRoute = guild.andThen(route => RequestRoute(route / "invites", GET))

  val guildIntegrations:       GuildId => Route        = guild.andThen(_ / "integrations")
  val getGuildIntegrations:    GuildId => RequestRoute = guildIntegrations.andThen(RequestRoute(_, GET))
  val createGuildIntegrations: GuildId => RequestRoute = guildIntegrations.andThen(RequestRoute(_, POST))

  val guildIntegration: (IntegrationId, GuildId) => Route = (integrationId, guildId) =>
    guildIntegrations(guildId) / integrationIdParam(integrationId)
  val modifyGuildIntegration: (IntegrationId, GuildId) => RequestRoute =
    guildIntegration.andThen(RequestRoute(_, PATCH))
  val deleteGuildIntegration: (IntegrationId, GuildId) => RequestRoute =
    guildIntegration.andThen(RequestRoute(_, DELETE))
  val syncGuildIntegration: (IntegrationId, GuildId) => RequestRoute =
    guildIntegration.andThen(route => RequestRoute(route / "sync", PATCH))

  val guildEmbed:       GuildId => Route        = guild.andThen(_ / "embed")
  val getGuildEmbed:    GuildId => RequestRoute = guildEmbed.andThen(RequestRoute(_, GET))
  val modifyGuildEmbed: GuildId => RequestRoute = guildEmbed.andThen(RequestRoute(_, PATCH))

  //Invites
  val invites:      Route                      = base / "invites"
  val inviteCode:   InviteCode => Route        = code => invites / inviteCodeParam(code)
  val getInvite:    InviteCode => RequestRoute = inviteCode.andThen(RequestRoute(_, GET))
  val deleteInvite: InviteCode => RequestRoute = inviteCode.andThen(RequestRoute(_, DELETE))
  val acceptInvite: InviteCode => RequestRoute = inviteCode.andThen(RequestRoute(_, POST))

  //Users
  val users:          Route        = base / "users"
  val currentUser:    Route        = users / "@me"
  val getCurrentUser: RequestRoute = RequestRoute(currentUser, GET)

  val getUser:              UserId => RequestRoute  = userId => RequestRoute(users / userIdParam(userId), GET)
  val modifyCurrentUser:    RequestRoute            = RequestRoute(currentUser, PATCH)
  val currentUserGuilds:    Route                   = currentUser / "guilds"
  val getCurrentUserGuilds: RequestRoute            = RequestRoute(currentUserGuilds, GET)
  val leaveGuild:           GuildId => RequestRoute = guildId => RequestRoute(currentUserGuilds / guildIdParam(guildId), DELETE)

  val userDMs:    Route        = currentUser / "channels"
  val getUserDMs: RequestRoute = RequestRoute(userDMs, GET)
  val createDM:   RequestRoute = RequestRoute(userDMs, POST)

  val getUserConnections: RequestRoute = RequestRoute(currentUser / "connections", GET)

  //Voice
  val listVoiceRegions = RequestRoute(base / "voice" / "regions", GET)

  //WebHook
  val webhook: SnowflakeType[Webhook] => Route = id => base / "webhooks" / webhookIdParam(id)
  val webhookWithToken: (String, SnowflakeType[Webhook]) => Route = (token, webhookId) =>
    webhook(webhookId) / tokenParam(token)
  val channelWebhooks: ChannelId => Route = channel.andThen(_ / "webhooks")

  val createWebhook:      ChannelId => RequestRoute              = channelWebhooks.andThen(RequestRoute(_, POST))
  val getChannelWebhooks: ChannelId => RequestRoute              = channelWebhooks.andThen(RequestRoute(_, GET))
  val getGuildWebhooks:   GuildId => RequestRoute                = guild.andThen(route => RequestRoute(route / "webhooks", GET))
  val getWebhook:         SnowflakeType[Webhook] => RequestRoute = webhook.andThen(RequestRoute(_, GET))
  val getWebhookWithToken: (String, SnowflakeType[Webhook]) => RequestRoute =
    webhookWithToken.andThen(RequestRoute(_, GET))
  val modifyWebhook: SnowflakeType[Webhook] => RequestRoute = webhook.andThen(RequestRoute(_, PATCH))
  val modifyWebhookWithToken: (String, SnowflakeType[Webhook]) => RequestRoute =
    webhookWithToken.andThen(RequestRoute(_, PATCH))
  val deleteWebhook: SnowflakeType[Webhook] => RequestRoute = webhook.andThen(RequestRoute(_, DELETE))
  val deleteWebhookWithToken: (String, SnowflakeType[Webhook]) => RequestRoute =
    webhookWithToken.andThen(RequestRoute(_, DELETE))
  val executeWebhook: (String, SnowflakeType[Webhook]) => RequestRoute = webhookWithToken.andThen(RequestRoute(_, POST))
  val executeSlackWebhook: (String, SnowflakeType[Webhook]) => RequestRoute =
    webhookWithToken.andThen(route => RequestRoute(route / "slack", POST))
  val executeGithubWebhook: (String, SnowflakeType[Webhook]) => RequestRoute =
    webhookWithToken.andThen(route => RequestRoute(route / "github", POST))

  //Images
  val emojiImage: (EmojiId, ImageFormat, Int) => RequestRoute = (emojiId, format, size) =>
    RequestRoute(cdn / "emojis" / emojiIdParam(emojiId) ++ s".${format.extension}?size=$size", GET)
  val guildIconImage: (GuildId, String, ImageFormat, Int) => RequestRoute = (guildId, hash, format, size) =>
    RequestRoute(cdn / "icons" / guildIdParam(guildId) / hashParam(hash) ++ s".${format.extension}?size=$size", GET)
  val guildSplashImage: (GuildId, String, ImageFormat, Int) => RequestRoute = (guildId, hash, format, size) =>
    RequestRoute(cdn / "splashes" / guildIdParam(guildId) / hashParam(hash) ++ s".${format.extension}?size=$size", GET)
  val defaultUserAvatarImage: (Int, ImageFormat, Int) => RequestRoute = (discriminator, format, size) =>
    RequestRoute(cdn / "embed" / "avatars" / s"${discriminator % 5}" ++ s".${format.extension}?size=$size", GET)
  val userAvatarImage: (UserId, String, ImageFormat, Int) => RequestRoute = (userId, hash, format, size) =>
    RequestRoute(cdn / "avatars" / userIdParam(userId) / hashParam(hash) ++ s".${format.extension}?size=$size", GET)
  val applicationIconImage: (RawSnowflake, String, ImageFormat, Int) => RequestRoute =
    (applicationId, hash, format, size) =>
      RequestRoute(
        cdn / "app-icons" / applicationIdParam(applicationId) / hashParam(hash) ++ s".${format.extension}?size=$size",
        GET
    )

  //OAuth
  val oAuth2:          Route = base / "oauth2"
  val oAuth2Authorize: Route = oAuth2 / "authorize"
  val oAuth2Token:     Route = oAuth2 / "token"
  val oAuth2Revoke:    Route = oAuth2Token / "revoke"
}
