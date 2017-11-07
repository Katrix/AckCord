/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
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
package net.katsstuff.ackcord.http

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri
import net.katsstuff.ackcord.AckCord
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.rest.RequestRoute
import net.katsstuff.ackcord.http.rest.Requests.ImageFormat

/**
  * All the routes used by AckCord
  */
object Routes {

  val discord = "discordapp.com"

  val base: Uri = s"https://$discord/api/v${AckCord.DiscordApiVersion}"
  val cdn:  Uri = s"https://cdn.$discord/"

  //WS
  val gateway:    Uri = s"$base/gateway"
  val botGateway: Uri = s"$gateway/bo"

  //REST
  type InviteCode = String

  /**
    * Emoji is a bit more complicated than the others.
    * If it's a custom emoji, the format is `name:id` for example `rust:232722868583006209`.
    * If it's a normal emoji, it's encoded using percent encoding, for example `%F0%9F%92%A9`.
    */
  type Emoji = String

  implicit class Func2Syntax[T1, T2, R](val f: (T1, T2) => R) extends AnyVal {
    def andThen[A](g: R => A): (T1, T2) => A = (t1, t2) => g(f(t1, t2))
  }

  implicit class Func3Syntax[T1, T2, T3, R](val f: (T1, T2, T3) => R) extends AnyVal {
    def andThen[A](g: R => A): (T1, T2, T3) => A = (t1, t2, t3) => g(f(t1, t2, t3))
  }

  //Audit log

  val guilds: Uri            = s"$base/guilds"
  val guild:  GuildId => Uri = guildId => s"$guilds/$guildId"

  val getGuildAuditLogs: GuildId => RequestRoute = guild.andThen(uri => RequestRoute(s"$uri/audit-logs", GET))

  //Channel routes

  val channel: ChannelId => Uri = channelId => s"$base/channels/$channelId"

  val getChannel:         ChannelId => RequestRoute = channel.andThen(RequestRoute(_, GET))
  val modifyChannelPut:   ChannelId => RequestRoute = channel.andThen(RequestRoute(_, PUT))
  val modifyChannelPatch: ChannelId => RequestRoute = channel.andThen(RequestRoute(_, PATCH))
  val deleteCloseChannel: ChannelId => RequestRoute = channel.andThen(RequestRoute(_, DELETE))

  val channelMessages: ChannelId => Uri = channel.andThen(uri => s"$uri/messages")
  val channelMessage: (MessageId, ChannelId) => Uri =
    Function.uncurried(messageId => channelMessages.andThen(uri => s"$uri/$messageId"))

  val getChannelMessages: ChannelId => RequestRoute              = channelMessages.andThen(RequestRoute(_, GET))
  val getChannelMessage:  (MessageId, ChannelId) => RequestRoute = channelMessage.andThen(RequestRoute(_, GET))
  val createMessage:      ChannelId => RequestRoute              = channelMessages.andThen(RequestRoute(_, POST))
  val editMessage:        (MessageId, ChannelId) => RequestRoute = channelMessage.andThen(RequestRoute(_, PATCH))
  val deleteMessage:      (MessageId, ChannelId) => RequestRoute = channelMessage.andThen(RequestRoute(_, DELETE))
  val bulkDeleteMessages: (ChannelId) => RequestRoute =
    channelMessages.andThen(uri => RequestRoute(s"$uri/bulk-delete", POST))

  val reactions: (MessageId, ChannelId) => Uri = channelMessage.andThen(uri => s"$uri/reactions")
  val emojiReactions: (Emoji, MessageId, ChannelId) => Uri =
    Function.uncurried(emoji => reactions.andThen(uri => s"$uri/$emoji": Uri).curried)
  val modifyMeReaction: (Emoji, MessageId, ChannelId) => Uri = emojiReactions.andThen(uri => s"$uri/@me")

  val createReaction: (Emoji, MessageId, ChannelId) => RequestRoute = modifyMeReaction.andThen(RequestRoute(_, PUT))
  val deleteOwnReaction: (Emoji, MessageId, ChannelId) => RequestRoute =
    modifyMeReaction.andThen(RequestRoute(_, DELETE))
  val deleteUserReaction: (UserId, Emoji, MessageId, ChannelId) => RequestRoute =
    Function.uncurried(userId => emojiReactions.andThen(uri => RequestRoute(s"$uri/$userId", DELETE)).curried)

  val getReactions:       (Emoji, MessageId, ChannelId) => RequestRoute = emojiReactions.andThen(RequestRoute(_, GET))
  val deleteAllReactions: (MessageId, ChannelId) => RequestRoute        = reactions.andThen(RequestRoute(_, DELETE))

  val channelPermissions: (UserOrRoleId, ChannelId) => Uri =
    Function.uncurried(overwrite => channel.andThen(uri => s"$uri/permissions/$overwrite"))

  val editChannelPermissions: (UserOrRoleId, ChannelId) => RequestRoute =
    channelPermissions.andThen(RequestRoute(_, PUT))
  val deleteChannelPermissions: (UserOrRoleId, ChannelId) => RequestRoute =
    channelPermissions.andThen(RequestRoute(_, DELETE))

  val channelInvites: ChannelId => Uri = channel.andThen(uri => s"$uri/invites")

  val getChannelInvites:    ChannelId => RequestRoute = channelInvites.andThen(RequestRoute(_, GET))
  val createChannelInvites: ChannelId => RequestRoute = channelInvites.andThen(RequestRoute(_, POST))

  val triggerTyping: ChannelId => RequestRoute = channel.andThen(uri => RequestRoute(s"$uri/typing", POST))

  val pinnedMessage:    ChannelId => Uri          = channel.andThen(uri => s"$uri/pins")
  val getPinnedMessage: ChannelId => RequestRoute = pinnedMessage.andThen(RequestRoute(_, GET))

  val channelPinnedMessage: (MessageId, ChannelId) => Uri =
    Function.uncurried(messageId => pinnedMessage.andThen(uri => s"$uri/$messageId"))
  val addPinnedChannelMessage: (MessageId, ChannelId) => RequestRoute =
    channelPinnedMessage.andThen(RequestRoute(_, PUT))
  val deletePinnedChannelMessage: (MessageId, ChannelId) => RequestRoute =
    channelPinnedMessage.andThen(RequestRoute(_, DELETE))

  val groupDmRecipient: (UserId, ChannelId) => Uri =
    Function.uncurried(userId => channel.andThen(uri => s"$uri/$userId"))
  val groupDmAddRecipient:    (UserId, ChannelId) => RequestRoute = groupDmRecipient.andThen(RequestRoute(_, PUT))
  val groupDmRemoveRecipient: (UserId, ChannelId) => RequestRoute = groupDmRecipient.andThen(RequestRoute(_, DELETE))

  //Emoji routes

  val guildEmojis:      GuildId => Uri          = guild.andThen(uri => s"$uri/emojis")
  val listGuildEmojis:  GuildId => RequestRoute = guildEmojis.andThen(RequestRoute(_, GET))
  val createGuildEmoji: GuildId => RequestRoute = guildEmojis.andThen(RequestRoute(_, POST))

  val guildEmoji: (EmojiId, GuildId) => Uri =
    Function.uncurried(emojiId => guildEmojis.andThen(uri => s"$uri/$emojiId"))
  val getGuildEmoji:    (EmojiId, GuildId) => RequestRoute = guildEmoji.andThen(RequestRoute(_, GET))
  val modifyGuildEmoji: (EmojiId, GuildId) => RequestRoute = guildEmoji.andThen(RequestRoute(_, PATCH))
  val deleteGuildEmoji: (EmojiId, GuildId) => RequestRoute = guildEmoji.andThen(RequestRoute(_, DELETE))

  //Guild routes
  val createGuild = RequestRoute(guilds, POST)
  val getGuild:    GuildId => RequestRoute = guild.andThen(RequestRoute(_, GET))
  val modifyGuild: GuildId => RequestRoute = guild.andThen(RequestRoute(_, PATCH))
  val deleteGuild: GuildId => RequestRoute = guild.andThen(RequestRoute(_, DELETE))

  val guildChannels:                GuildId => Uri          = guild.andThen(uri => s"$uri/channels")
  val getGuildChannels:             GuildId => RequestRoute = guildChannels.andThen(RequestRoute(_, GET))
  val createGuildChannel:           GuildId => RequestRoute = guildChannels.andThen(RequestRoute(_, POST))
  val modifyGuildChannelsPositions: GuildId => RequestRoute = guildChannels.andThen(RequestRoute(_, PATCH))

  val guildMembers:      GuildId => Uri                    = guild.andThen(uri => s"$uri/members")
  val guildMember:       (UserId, GuildId) => Uri          = Function.uncurried(userId => guildMembers.andThen(uri => s"$uri/$userId"))
  val getGuildMember:    (UserId, GuildId) => RequestRoute = guildMember.andThen(RequestRoute(_, GET))
  val listGuildMembers:  GuildId => RequestRoute           = guildMembers.andThen(RequestRoute(_, GET))
  val addGuildMember:    (UserId, GuildId) => RequestRoute = guildMember.andThen(RequestRoute(_, PUT))
  val modifyGuildMember: (UserId, GuildId) => RequestRoute = guildMember.andThen(RequestRoute(_, PATCH))
  val removeGuildMember: (UserId, GuildId) => RequestRoute = guildMember.andThen(RequestRoute(_, DELETE))
  val modifyCurrentNick: GuildId => RequestRoute           = guildMembers.andThen(uri => RequestRoute(s"$uri/@me/nick", PATCH))

  val guildMemberRole: (RoleId, UserId, GuildId) => Uri =
    Function.uncurried(roleId => guildMember.andThen(uri => s"$uri/roles/$roleId": Uri).curried)

  val addGuildMemberRole: (RoleId, UserId, GuildId) => RequestRoute = guildMemberRole.andThen(RequestRoute(_, PUT))
  val removeGuildMemberRole: (RoleId, UserId, GuildId) => RequestRoute =
    guildMemberRole.andThen(RequestRoute(_, DELETE))

  val guildBans: (GuildId) => String = guild.andThen(uri => s"$uri/bans")
  val guildMemberBan: (UserId, GuildId) => String =
    Function.uncurried(userId => guildBans.andThen(uri => s"$uri/$userId"))

  val getGuildBans:         GuildId => RequestRoute           = guildBans.andThen(RequestRoute(_, GET))
  val createGuildMemberBan: (UserId, GuildId) => RequestRoute = guildMemberBan.andThen(RequestRoute(_, PUT))
  val removeGuildMemberBan: (UserId, GuildId) => RequestRoute = guildMemberBan.andThen(RequestRoute(_, DELETE))

  val guildRoles:               GuildId => Uri          = guild.andThen(uri => s"$uri/roles")
  val getGuildRole:             GuildId => RequestRoute = guildRoles.andThen(RequestRoute(_, GET))
  val createGuildRole:          GuildId => RequestRoute = guildRoles.andThen(RequestRoute(_, POST))
  val modifyGuildRolePositions: GuildId => RequestRoute = guildRoles.andThen(RequestRoute(_, PATCH))

  val guildRole:       (RoleId, GuildId) => Uri          = Function.uncurried(roleId => guildRoles.andThen(uri => s"$uri/$roleId"))
  val modifyGuildRole: (RoleId, GuildId) => RequestRoute = guildRole.andThen(RequestRoute(_, PATCH))
  val deleteGuildRole: (RoleId, GuildId) => RequestRoute = guildRole.andThen(RequestRoute(_, DELETE))

  val guildPrune:         GuildId => Uri          = guild.andThen(uri => s"$uri/prune")
  val getGuildPruneCount: GuildId => RequestRoute = guildPrune.andThen(RequestRoute(_, GET))
  val beginGuildPrune:    GuildId => RequestRoute = guildPrune.andThen(RequestRoute(_, POST))

  val getGuildVoiceRegions: GuildId => RequestRoute = guild.andThen(uri => RequestRoute(s"$uri/regions", GET))
  val getGuildInvites:      GuildId => RequestRoute = guild.andThen(uri => RequestRoute(s"$uri/invites", GET))

  val guildIntegrations:       GuildId => Uri          = guild.andThen(uri => s"$uri/integrations")
  val getGuildIntegrations:    GuildId => RequestRoute = guildIntegrations.andThen(RequestRoute(_, GET))
  val createGuildIntegrations: GuildId => RequestRoute = guildIntegrations.andThen(RequestRoute(_, POST))

  val guildIntegration: (IntegrationId, GuildId) => Uri =
    Function.uncurried(integrationId => guildIntegrations.andThen(uri => s"$uri/$integrationId"))
  val modifyGuildIntegration: (IntegrationId, GuildId) => RequestRoute =
    guildIntegration.andThen(RequestRoute(_, PATCH))
  val deleteGuildIntegration: (IntegrationId, GuildId) => RequestRoute =
    guildIntegration.andThen(RequestRoute(_, DELETE))
  val syncGuildIntegration: (IntegrationId, GuildId) => RequestRoute =
    guildIntegration.andThen(uri => RequestRoute(s"$uri/sync", PATCH))

  val guildEmbed:       GuildId => Uri          = guild.andThen(uri => s"$uri/embed")
  val getGuildEmbed:    GuildId => RequestRoute = guildEmbed.andThen(RequestRoute(_, GET))
  val modifyGuildEmbed: GuildId => RequestRoute = guildEmbed.andThen(RequestRoute(_, PATCH))

  //Invites
  val invites:      Uri                        = s"$base/invites"
  val inviteCode:   InviteCode => Uri          = code => s"$invites/$code"
  val getInvite:    InviteCode => RequestRoute = inviteCode.andThen(RequestRoute(_, GET))
  val deleteInvite: InviteCode => RequestRoute = inviteCode.andThen(RequestRoute(_, DELETE))
  val acceptInvite: InviteCode => RequestRoute = inviteCode.andThen(RequestRoute(_, POST))

  //Users
  val users:          Uri          = s"$base/users"
  val currentUser:    Uri          = s"$users/@me"
  val getCurrentUser: RequestRoute = RequestRoute(currentUser, GET)

  val getUser:              UserId => RequestRoute  = userId => RequestRoute(s"$users/$userId", GET)
  val modifyCurrentUser:    RequestRoute            = RequestRoute(currentUser, PATCH)
  val currentUserGuilds:    Uri                     = s"$currentUser/guilds"
  val getCurrentUserGuilds: RequestRoute            = RequestRoute(currentUserGuilds, GET)
  val leaveGuild:           GuildId => RequestRoute = guildId => RequestRoute(s"$currentUserGuilds/$guildId", DELETE)

  val userDMs:    Uri          = s"$currentUser/channels"
  val getUserDMs: RequestRoute = RequestRoute(userDMs, GET)
  val createDM:   RequestRoute = RequestRoute(userDMs, POST)

  val getUserConnections: RequestRoute = RequestRoute(s"$currentUser/connections", GET)

  //Voice
  val listVoiceRegions = RequestRoute(s"$base/voice/regions", GET)

  //WebHook
  val webhook: SnowflakeType[Webhook] => Uri = id => s"$base/webhooks/$id"
  val webhookWithToken: (String, SnowflakeType[Webhook]) => Uri =
    Function.uncurried(token => webhook.andThen(uri => s"$uri/$token"))
  val channelWebhooks: ChannelId => String = channel.andThen(uri => s"$uri/webhooks")

  val createWebhook:      ChannelId => RequestRoute              = channelWebhooks.andThen(RequestRoute(_, POST))
  val getChannelWebhooks: ChannelId => RequestRoute              = channelWebhooks.andThen(RequestRoute(_, GET))
  val getGuildWebhooks:   GuildId => RequestRoute                = guild.andThen(uri => RequestRoute(s"$uri/webhooks", GET))
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
    webhookWithToken.andThen(uri => RequestRoute(s"$uri/slack", POST))
  val executeGithubWebhook: (String, SnowflakeType[Webhook]) => RequestRoute =
    webhookWithToken.andThen(uri => RequestRoute(s"$uri/github", POST))

  //Images
  val emojiImage: (EmojiId, ImageFormat, Int) => RequestRoute = (emojiId, format, size) =>
    RequestRoute(s"$cdn/emojis/$emojiId.${format.extension}?size=$size", GET)
  val guildIconImage: (GuildId, String, ImageFormat, Int) => RequestRoute = (guildId, hash, format, size) =>
    RequestRoute(s"$cdn/icons/$guildId/$hash.${format.extension}?size=$size", GET)
  val guildSplashImage: (GuildId, String, ImageFormat, Int) => RequestRoute = (guildId, hash, format, size) =>
    RequestRoute(s"$cdn/splashes/$guildId/$hash.${format.extension}?size=$size", GET)
  val defaultUserAvatarImage: (Int, ImageFormat, Int) => RequestRoute = (discriminator, format, size) =>
    RequestRoute(s"$cdn/embed/avatars/${discriminator % 5}.${format.extension}?size=$size", GET)
  val userAvatarImage: (UserId, String, ImageFormat, Int) => RequestRoute = (userId, hash, format, size) =>
    RequestRoute(s"$cdn/avatars/$userId/$hash.${format.extension}?size=$size", GET)
  val applicationIconImage: (RawSnowflake, String, ImageFormat, Int) => RequestRoute =
    (applicationId, hash, format, size) =>
      RequestRoute(s"$cdn/app-icons/$applicationId/$hash.${format.extension}?size=$size", GET)
}
