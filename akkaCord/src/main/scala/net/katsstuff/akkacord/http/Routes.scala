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

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri
import net.katsstuff.akkacord.AkkaCord
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.http.rest.RestRoute

object Routes {

  val base: Uri = s"https://discordapp.com/api/v${AkkaCord.DiscordApiVersion}"

  //WS
  val gateway: Uri = s"$base/gateway"

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

  //Channel routes

  val channel: ChannelId => Uri = channelId => s"$base/channels/$channelId"

  val getChannel:         ChannelId => RestRoute = channel.andThen(RestRoute(_, GET))
  val modifyChannelPut:   ChannelId => RestRoute = channel.andThen(RestRoute(_, PUT))
  val modifyChannelPatch: ChannelId => RestRoute = channel.andThen(RestRoute(_, PATCH))
  val deleteCloseChannel: ChannelId => RestRoute = channel.andThen(RestRoute(_, DELETE))

  val channelMessages: ChannelId => Uri = channel.andThen(uri => s"$uri/messages")
  val channelMessage: (MessageId, ChannelId) => Uri =
    Function.uncurried(messageId => channelMessages.andThen(uri => s"$uri/$messageId"))

  val getChannelMessages: ChannelId => RestRoute              = channelMessages.andThen(RestRoute(_, GET))
  val getChannelMessage:  (MessageId, ChannelId) => RestRoute = channelMessage.andThen(RestRoute(_, GET))
  val createMessage:      ChannelId => RestRoute              = channelMessages.andThen(RestRoute(_, POST))
  val editMessage:        (MessageId, ChannelId) => RestRoute = channelMessage.andThen(RestRoute(_, PATCH))
  val deleteMessage:      (MessageId, ChannelId) => RestRoute = channelMessage.andThen(RestRoute(_, DELETE))
  val bulkDeleteMessages: (ChannelId) => RestRoute =
    channelMessages.andThen(uri => RestRoute(s"$uri/bulk-delete", POST))

  val reactions: (MessageId, ChannelId) => Uri = channelMessage.andThen(uri => s"$uri/reactions")
  val emojiReactions: (Emoji, MessageId, ChannelId) => Uri =
    Function.uncurried(emoji => reactions.andThen(uri => s"$uri/$emoji": Uri).curried)
  val modifyMeReaction: (Emoji, MessageId, ChannelId) => Uri = emojiReactions.andThen(uri => s"$uri/@me")

  val createReaction:    (Emoji, MessageId, ChannelId) => RestRoute = modifyMeReaction.andThen(RestRoute(_, PUT))
  val deleteOwnReaction: (Emoji, MessageId, ChannelId) => RestRoute = modifyMeReaction.andThen(RestRoute(_, DELETE))
  val deleteUserReaction: (UserId, Emoji, MessageId, ChannelId) => RestRoute =
    Function.uncurried(userId => emojiReactions.andThen(uri => RestRoute(s"$uri/$userId", DELETE)).curried)

  val getReactions:       (Emoji, MessageId, ChannelId) => RestRoute = emojiReactions.andThen(RestRoute(_, GET))
  val deleteAllReactions: (MessageId, ChannelId) => RestRoute        = reactions.andThen(RestRoute(_, DELETE))

  val channelPermissions: (UserOrRoleId, ChannelId) => Uri =
    Function.uncurried(overwrite => channel.andThen(uri => s"$uri/permissions/$overwrite"))

  val editChannelPermissions: (UserOrRoleId, ChannelId) => RestRoute = channelPermissions.andThen(RestRoute(_, PUT))
  val deleteChannelPermissions: (UserOrRoleId, ChannelId) => RestRoute =
    channelPermissions.andThen(RestRoute(_, DELETE))

  val channelInvites: ChannelId => Uri = channel.andThen(uri => s"$uri/invites")

  val getChannelInvites:    ChannelId => RestRoute = channelInvites.andThen(RestRoute(_, GET))
  val createChannelInvites: ChannelId => RestRoute = channelInvites.andThen(RestRoute(_, POST))

  val triggerTyping: ChannelId => RestRoute = channel.andThen(uri => RestRoute(s"$uri/typing", POST))

  val pinnedMessage:    ChannelId => Uri       = channel.andThen(uri => s"$uri/pins")
  val getPinnedMessage: ChannelId => RestRoute = pinnedMessage.andThen(RestRoute(_, GET))

  val channelPinnedMessage: (MessageId, ChannelId) => Uri =
    Function.uncurried(messageId => pinnedMessage.andThen(uri => s"$uri/$messageId"))
  val addPinnedChannelMessage: (MessageId, ChannelId) => RestRoute = channelPinnedMessage.andThen(RestRoute(_, PUT))
  val deletePinnedChannelMessage: (MessageId, ChannelId) => RestRoute =
    channelPinnedMessage.andThen(RestRoute(_, DELETE))

  val groupDmRecipient: (UserId, ChannelId) => Uri =
    Function.uncurried(userId => channel.andThen(uri => s"$uri/$userId"))
  val groupDmAddRecipient:    (UserId, ChannelId) => RestRoute = groupDmRecipient.andThen(RestRoute(_, PUT))
  val groupDmRemoveRecipient: (UserId, ChannelId) => RestRoute = groupDmRecipient.andThen(RestRoute(_, DELETE))

  //Emoji routes
  val guilds: Uri            = s"$base/guilds"
  val guild:  GuildId => Uri = guildId => s"$guilds/$guildId"

  val guildEmojis:      GuildId => Uri       = guild.andThen(uri => s"$uri/emojis")
  val listGuildEmojis:  GuildId => RestRoute = guildEmojis.andThen(RestRoute(_, GET))
  val createGuildEmoji: GuildId => RestRoute = guildEmojis.andThen(RestRoute(_, POST))

  val guildEmoji: (EmojiId, GuildId) => Uri =
    Function.uncurried(emojiId => guildEmojis.andThen(uri => s"$uri/$emojiId"))
  val getGuildEmoji:    (EmojiId, GuildId) => RestRoute = guildEmoji.andThen(RestRoute(_, GET))
  val modifyGuildEmoji: (EmojiId, GuildId) => RestRoute = guildEmoji.andThen(RestRoute(_, PATCH))
  val deleteGuildEmoji: (EmojiId, GuildId) => RestRoute = guildEmoji.andThen(RestRoute(_, DELETE))

  //Guild routes
  val createGuild = RestRoute(guilds, POST)
  val getGuild:    GuildId => RestRoute = guild.andThen(RestRoute(_, GET))
  val modifyGuild: GuildId => RestRoute = guild.andThen(RestRoute(_, PATCH))
  val deleteGuild: GuildId => RestRoute = guild.andThen(RestRoute(_, DELETE))

  val guildChannels:                GuildId => Uri       = guild.andThen(uri => s"$uri/channels")
  val getGuildChannels:             GuildId => RestRoute = guildChannels.andThen(RestRoute(_, GET))
  val createGuildChannel:           GuildId => RestRoute = guildChannels.andThen(RestRoute(_, POST))
  val modifyGuildChannelsPositions: GuildId => RestRoute = guildChannels.andThen(RestRoute(_, PATCH))

  val guildMembers:      GuildId => Uri                 = guild.andThen(uri => s"$uri/members")
  val guildMember:       (UserId, GuildId) => Uri       = Function.uncurried(userId => guildMembers.andThen(uri => s"$uri/$userId"))
  val getGuildMember:    (UserId, GuildId) => RestRoute = guildMember.andThen(RestRoute(_, GET))
  val listGuildMembers:  GuildId => RestRoute           = guildMembers.andThen(RestRoute(_, GET))
  val addGuildMember:    (UserId, GuildId) => RestRoute = guildMember.andThen(RestRoute(_, PUT))
  val modifyGuildMember: (UserId, GuildId) => RestRoute = guildMember.andThen(RestRoute(_, PATCH))
  val removeGuildMember: (UserId, GuildId) => RestRoute = guildMember.andThen(RestRoute(_, DELETE))
  val modifyCurrentNick: GuildId => RestRoute           = guildMembers.andThen(uri => RestRoute(s"$uri/@me/nick", PATCH))

  val guildMemberRole: (RoleId, UserId, GuildId) => Uri =
    Function.uncurried(roleId => guildMember.andThen(uri => s"$uri/roles/$roleId": Uri).curried)

  val addGuildMemberRole:    (RoleId, UserId, GuildId) => RestRoute = guildMemberRole.andThen(RestRoute(_, PUT))
  val removeGuildMemberRole: (RoleId, UserId, GuildId) => RestRoute = guildMemberRole.andThen(RestRoute(_, DELETE))

  val guildBans: (GuildId) => String = guild.andThen(uri => s"$uri/bans")
  val guildMemberBan: (UserId, GuildId) => String =
    Function.uncurried(userId => guildBans.andThen(uri => s"$uri/$userId"))

  val getGuildBans:         GuildId => RestRoute           = guildBans.andThen(RestRoute(_, GET))
  val createGuildMemberBan: (UserId, GuildId) => RestRoute = guildMemberBan.andThen(RestRoute(_, PUT))
  val removeGuildMemberBan: (UserId, GuildId) => RestRoute = guildMemberBan.andThen(RestRoute(_, DELETE))

  val guildRoles:               GuildId => Uri       = guild.andThen(uri => s"$uri/roles")
  val getGuildRole:             GuildId => RestRoute = guildRoles.andThen(RestRoute(_, GET))
  val createGuildRole:          GuildId => RestRoute = guildRoles.andThen(RestRoute(_, POST))
  val modifyGuildRolePositions: GuildId => RestRoute = guildRoles.andThen(RestRoute(_, PATCH))

  val guildRole:       (RoleId, GuildId) => Uri       = Function.uncurried(roleId => guildRoles.andThen(uri => s"$uri/$roleId"))
  val modifyGuildRole: (RoleId, GuildId) => RestRoute = guildRole.andThen(RestRoute(_, PATCH))
  val deleteGuildRole: (RoleId, GuildId) => RestRoute = guildRole.andThen(RestRoute(_, DELETE))

  val guildPrune:         GuildId => Uri       = guild.andThen(uri => s"$uri/prune")
  val getGuildPruneCount: GuildId => RestRoute = guildPrune.andThen(RestRoute(_, GET))
  val beginGuildPrune:    GuildId => RestRoute = guildPrune.andThen(RestRoute(_, POST))

  val getGuildVoiceRegions: GuildId => RestRoute = guild.andThen(uri => RestRoute(s"$uri/regions", GET))
  val getGuildInvites:      GuildId => RestRoute = guild.andThen(uri => RestRoute(s"$uri/invites", GET))

  val guildIntegrations:       GuildId => Uri       = guild.andThen(uri => s"$uri/integrations")
  val getGuildIntegrations:    GuildId => RestRoute = guildIntegrations.andThen(RestRoute(_, GET))
  val createGuildIntegrations: GuildId => RestRoute = guildIntegrations.andThen(RestRoute(_, POST))

  val guildIntegration: (IntegrationId, GuildId) => Uri =
    Function.uncurried(integrationId => guildIntegrations.andThen(uri => s"$uri/$integrationId"))
  val modifyGuildIntegration: (IntegrationId, GuildId) => RestRoute = guildIntegration.andThen(RestRoute(_, PATCH))
  val deleteGuildIntegration: (IntegrationId, GuildId) => RestRoute = guildIntegration.andThen(RestRoute(_, DELETE))
  val syncGuildIntegration: (IntegrationId, GuildId) => RestRoute =
    guildIntegration.andThen(uri => RestRoute(s"$uri/sync", PATCH))

  val guildEmbed:       GuildId => Uri       = guild.andThen(uri => s"$uri/embed")
  val getGuildEmbed:    GuildId => RestRoute = guildEmbed.andThen(RestRoute(_, GET))
  val modifyGuildEmbed: GuildId => RestRoute = guildEmbed.andThen(RestRoute(_, PATCH))

  //Invites
  val invites:      Uri                     = s"$base/invites"
  val inviteCode:   InviteCode => Uri       = code => s"$invites/$code"
  val getInvite:    InviteCode => RestRoute = inviteCode.andThen(RestRoute(_, GET))
  val deleteInvite: InviteCode => RestRoute = inviteCode.andThen(RestRoute(_, DELETE))
  val acceptInvite: InviteCode => RestRoute = inviteCode.andThen(RestRoute(_, POST))

  //Users
  val users:          Uri       = s"$base/users"
  val currentUser:    Uri       = s"$users/@me"
  val getCurrentUser: RestRoute = RestRoute(currentUser, GET)

  val getUser             :               UserId => RestRoute = userId => RestRoute(s"$users/$userId", GET)
  val modifyCurrentUser   :     RestRoute                     = RestRoute(currentUser, PATCH)
  val currentUserGuilds   :     Uri                           = s"$currentUser/guilds"
  val getCurrentUserGuilds:  RestRoute                        = RestRoute(currentUserGuilds, GET)
  val leaveGuild          : GuildId => RestRoute              = guildId => RestRoute(s"$currentUserGuilds/$guildId", DELETE)

  val userDMs   :             Uri = s"$currentUser/channels"
  val getUserDMs:   RestRoute     = RestRoute(userDMs, GET)
  val createDM  : RestRoute       = RestRoute(userDMs, POST)

  val getUserConnections: RestRoute = RestRoute(s"$currentUser/connections", GET)
}
