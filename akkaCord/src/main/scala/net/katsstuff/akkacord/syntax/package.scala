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
package net.katsstuff.akkacord

import java.nio.file.Path

import akka.NotUsed
import net.katsstuff.akkacord.CacheSnapshotLike.BotUser
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.http.rest.Requests._
import shapeless.tag.@@

package object syntax {

  implicit class ChannelSyntax(val channel: Channel) extends AnyVal {
    def delete[Context](context: Context = NotUsed) = Request(DeleteCloseChannel(channel.id), context)
    def mention: String = s"<#${channel.id}>"
  }

  implicit class TChannelSyntax(val tChannel: TChannel) extends AnyVal {
    def sendMessage[Context](
        content: String,
        tts: Boolean = false,
        file: Option[Path] = None,
        embed: Option[OutgoingEmbed] = None,
        context: Context = NotUsed
    ) = Request(CreateMessage(tChannel.id, CreateMessageData(content, None, tts, file, embed)), NotUsed)

    def fetchMessagesAround[Context](around: MessageId, limit: Option[Int] = Some(50), context: Context = NotUsed) =
      Request(GetChannelMessages(tChannel.id, GetChannelMessagesData(Some(around), None, None, limit)), context)
    def fetchMessagesBefore[Context](before: MessageId, limit: Option[Int] = Some(50), context: Context = NotUsed) =
      Request(GetChannelMessages(tChannel.id, GetChannelMessagesData(None, Some(before), None, limit)), context)
    def fetchMessagesAfter[Context](after: MessageId, limit: Option[Int] = Some(50), context: Context = NotUsed) =
      Request(GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, Some(after), limit)), context)
    def fetchMessages[Context](limit: Option[Int] = Some(50), context: Context = NotUsed) =
      Request(GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, None, limit)), context)

    def fetchMessage[Context](id: MessageId, context: Context = NotUsed) =
      Request(GetChannelMessage(tChannel.id, id), context)

    def bulkDelete[Context](ids: Seq[MessageId], context: Context = NotUsed) =
      Request(BulkDeleteMessages(tChannel.id, BulkDeleteMessagesData(ids)), context)

    def editChannelPermissions[Context](role: Role, allow: Permission, deny: Permission, context: Context = NotUsed) =
      Request(EditChannelPermissions(tChannel.id, role.id, EditChannelPermissionsData(allow, deny, "role")), context)

    def deleteChannelPermissions[Context](user: User, context: Context = NotUsed) =
      Request(DeleteChannelPermission(tChannel.id, user.id), context)

    def triggerTyping[Context](context: Context = NotUsed) = Request(TriggerTypingIndicator(tChannel.id), context)

    def fetchPinnedMessages[Context](context: Context = NotUsed) = Request(GetPinnedMessages(tChannel.id), context)

    def bulkDelete[Context](messages: Seq[MessageId]) =
      Request(BulkDeleteMessages(tChannel.id, BulkDeleteMessagesData(messages)))
  }

  implicit class GuildChannelSyntax(val channel: GuildChannel) extends AnyVal {
    def category(snapshot: CacheSnapshot): Option[GuildCategory] =
      for {
        catId <- channel.parentId
        guild <- channel.guild
        cat <- guild.channels.collectFirst {
          case (_, ch: GuildCategory) if ch.id == catId => ch
        }
      } yield cat
  }

  implicit class TGuildChannelSyntax(val channel: TGuildChannel) extends AnyVal {
    def modify[Context](
        name: String = channel.name,
        position: Int = channel.position,
        topic: Option[String] = channel.topic,
        nsfw: Boolean = channel.nsfw,
        permissionOverwrites: Seq[PermissionValue] = channel.permissionOverwrites,
        category: Option[ChannelId],
        context: Context = NotUsed
    ) =
      Request(
        ModifyChannel(
          channel.id,
          ModifyChannelData(name, position, topic, Some(nsfw), None, None, permissionOverwrites, category)
        ),
        context
      )

    def fetchInvites[Context](context: Context = NotUsed) = Request(GetChannelInvites(channel.id), context)

    def createInvite[Context](
        maxAge: Int = 86400,
        maxUses: Int = 0,
        temporary: Boolean = false,
        unique: Boolean = false,
        context: Context = NotUsed
    ) = Request(CreateChannelInvite(channel.id, CreateChannelInviteData(maxAge, maxUses, temporary, unique)), context)
  }

  implicit class VGuildChannelSyntax(val channel: VGuildChannel) extends AnyVal {
    def modify[Context](
        name: String = channel.name,
        position: Int = channel.position,
        bitrate: Int = channel.bitrate,
        userLimit: Int = channel.userLimit,
        permissionOverwrites: Seq[PermissionValue] = channel.permissionOverwrites,
        category: Option[ChannelId],
        context: Context = NotUsed
    ) =
      Request(
        ModifyChannel(
          channel.id,
          ModifyChannelData(
            name = name,
            position = position,
            topic = None,
            nsfw = Some(channel.nsfw),
            bitrate = Some(bitrate),
            userLimit = Some(userLimit),
            permissionOverwrites = permissionOverwrites,
            parentId = category
          )
        ),
        context
      )
  }

  implicit class CategorySyntax(val category: GuildCategory) extends AnyVal {
    def channels(implicit snapshot: CacheSnapshot): Seq[GuildChannel] =
      category.guild
        .map { g =>
          g.channels.collect {
            case (_, ch) if ch.parentId.contains(category.id) => ch
          }.toSeq
        }
        .getOrElse(Seq.empty)

    def tChannels(implicit snapshot: CacheSnapshot): Seq[TGuildChannel] =
      channels.collect { case tChannel: TGuildChannel => tChannel }

    def vChannels(implicit snapshot: CacheSnapshot): Seq[VGuildChannel] =
      channels.collect { case tChannel: VGuildChannel => tChannel }

    def channelById(id: ChannelId)(implicit snapshot: CacheSnapshot): Option[GuildChannel] = channels.find(_.id == id)
    def tChannelById(id: ChannelId)(implicit snapshot: CacheSnapshot): Option[TGuildChannel] = channelById(id).collect {
      case tChannel: TGuildChannel => tChannel
    }
    def vChannelById(id: ChannelId): Option[VGuildChannel] = channelById(id).collect {
      case vChannel: VGuildChannel => vChannel
    }

    def channelsByName(name: String)(implicit snapshot: CacheSnapshot): Seq[GuildChannel] =
      channels.filter(_.name == name)
    def tChannelsByName(name: String)(implicit snapshot: CacheSnapshot): Seq[TGuildChannel] =
      tChannels.filter(_.name == name)
    def vChannelsByName(name: String)(implicit snapshot: CacheSnapshot): Seq[VGuildChannel] =
      vChannels.filter(_.name == name)

    def modify[Context](
        name: String = category.name,
        position: Int = category.position,
        permissionOverwrites: Seq[PermissionValue] = category.permissionOverwrites,
        context: Context = NotUsed
    ) =
      Request(
        ModifyChannel(
          category.id,
          ModifyChannelData(
            name = name,
            position = position,
            topic = None,
            nsfw = Some(category.nsfw),
            bitrate = None,
            userLimit = None,
            permissionOverwrites = permissionOverwrites,
            parentId = category.parentId
          )
        ),
        context
      )
  }

  implicit class GuildSyntax(val guild: Guild) extends AnyVal {
    def owner(implicit snapshot: CacheSnapshot): Option[User] = snapshot.getUser(guild.ownerId)

    def modify[Context](
        name: Option[String] = None,
        region: Option[String] = None,
        verificationLevel: Option[VerificationLevel] = None,
        defaultMessageNotification: Option[NotificationLevel] = None,
        afkChannelId: Option[ChannelId] = None,
        afkTimeout: Option[Int] = None,
        icon: Option[String] = None,
        ownerId: Option[UserId] = None,
        splash: Option[String] = None,
        context: Context = NotUsed
    ) =
      Request(
        ModifyGuild(
          guild.id,
          ModifyGuildData(
            name = name,
            region = region,
            verificationLevel = verificationLevel,
            defaultMessageNotification = defaultMessageNotification,
            afkChannelId = afkChannelId,
            afkTimeout = afkTimeout,
            icon = icon,
            ownerId = ownerId,
            splash = splash
          )
        ),
        context
      )

    def fetchAllChannels[Context](channelId: ChannelId, context: Context = NotUsed) =
      Request(GetGuildChannels(guild.id), context)

    def createChannel[Context](
        name: String,
        `type`: Option[ChannelType],
        bitrate: Option[Int],
        userLimit: Option[Int],
        permissionOverwrites: Option[Seq[PermissionValue]],
        category: Option[ChannelId],
        nsfw: Option[Boolean],
        context: Context = NotUsed
    ) =
      Request(
        CreateGuildChannel(
          guild.id,
          CreateGuildChannelData(name, `type`, bitrate, userLimit, permissionOverwrites, category, nsfw)
        ),
        context
      )

    def modifyChannelPositions[Context](newPositions: Map[ChannelId, Int], context: Context = NotUsed) =
      Request(
        ModifyGuildChannelPositions(guild.id, newPositions.map(t => ModifyGuildChannelPositionsData(t._1, t._2)).toSeq),
        context
      )

    def fetchGuildMember[Context](userId: UserId, context: Context = NotUsed) =
      Request(GetGuildMember(guild.id, userId), context)

    def fetchBans[Context](context: Context = NotUsed) = Request(GetGuildBans(guild.id), context)

    def unban[Context](userId: UserId, context: Context = NotUsed) = Request(RemoveGuildBan(guild.id, userId), context)

    def fetchAllGuildMember[Context](
        limit: Option[Int] = None,
        after: Option[UserId] = None,
        context: Context = NotUsed
    ) = Request(ListGuildMembers(guild.id, ListGuildMembersData(limit, after)), context)

    def addGuildMember[Context](
        userId: UserId,
        accessToken: String,
        nick: Option[String],
        roles: Option[Seq[RoleId]],
        mute: Option[Boolean],
        deaf: Option[Boolean],
        context: Context = NotUsed
    ) = Request(AddGuildMember(guild.id, userId, AddGuildMemberData(accessToken, nick, roles, mute, deaf)), context)

    def fetchRoles[Context](context: Context = NotUsed) = Request(GetGuildRoles(guild.id), context)
    def createRoles[Context](
        name: Option[String],
        permissions: Option[Permission],
        color: Option[Int],
        hoist: Option[Boolean],
        mentionable: Option[Boolean],
        context: Context = NotUsed
    ) = Request(CreateGuildRole(guild.id, CreateGuildRoleData(name, permissions, color, hoist, mentionable)), context)

    def modifyRolePositions[Context](newPositions: Map[RoleId, Int], context: Context = NotUsed) =
      Request(
        ModifyGuildRolePositions(guild.id, newPositions.map(t => ModifyGuildRolePositionsData(t._1, t._2)).toSeq),
        context
      )

    def fetchPruneCount[Context](days: Int, context: Context = NotUsed) =
      Request(GetGuildPruneCount(guild.id, GuildPruneData(days)), context)
    def beginPrune[Context](days: Int, context: Context = NotUsed) =
      Request(BeginGuildPrune(guild.id, GuildPruneData(days)), context)

    def fetchVoiceRegions[Context](context: Context = NotUsed) = Request(GetGuildVoiceRegions(guild.id), context)

    def fetchInvites[Context](context: Context = NotUsed) = Request(GetGuildInvites(guild.id), context)

    def fetchIntegrations[Context](context: Context = NotUsed) = Request(GetGuildIntegrations(guild.id), context)
    def createIntegration[Context](tpe: String, id: IntegrationId, context: Context = NotUsed) =
      Request(CreateGuildIntegration(guild.id, CreateGuildIntegrationData(tpe, id)), context)
    def modifyIntegration[Context](
        id: IntegrationId,
        expireBehavior: Int,
        expireGracePeriod: Int,
        enableEmoticons: Boolean,
        context: Context = NotUsed
    ) = Request(
      ModifyGuildIntegration(
        guild.id,
        id,
        ModifyGuildIntegrationData(expireBehavior, expireGracePeriod, enableEmoticons)
      ),
      context
    )
    def removeIntegration[Context](id: IntegrationId, context: Context = NotUsed) =
      Request(DeleteGuildIntegration(guild.id, id), context)
    def syncIntegration[Context](id: IntegrationId, context: Context = NotUsed) =
      Request(SyncGuildIntegration(guild.id, id), context)

    def fetchEmbed[Context](context: Context = NotUsed) = Request(GetGuildEmbed(guild.id), context)
    def modifyEmbed[Context](embed: GuildEmbed, context: Context = NotUsed) =
      Request(ModifyGuildEmbed(guild.id, embed), context)

    def tChannels: Seq[TGuildChannel] =
      guild.channels.values.collect {
        case tChannel: TGuildChannel => tChannel
      }.toSeq

    def vChannels: Seq[VGuildChannel] =
      guild.channels.values.collect {
        case tChannel: VGuildChannel => tChannel
      }.toSeq

    def channelById(id: ChannelId): Option[GuildChannel] = guild.channels.get(id)
    def tChannelById(id: ChannelId): Option[TGuildChannel] = channelById(id).collect {
      case tChannel: TGuildChannel => tChannel
    }
    def vChannelById(id: ChannelId): Option[VGuildChannel] = channelById(id).collect {
      case vChannel: VGuildChannel => vChannel
    }

    def channelsByName(name: String):  Seq[GuildChannel]  = guild.channels.values.filter(_.name == name).toSeq
    def tChannelsByName(name: String): Seq[TGuildChannel] = tChannels.filter(_.name == name)
    def vChannelsByName(name: String): Seq[VGuildChannel] = vChannels.filter(_.name == name)

    def afkChannel: Option[VGuildChannel] = guild.afkChannelId.flatMap(vChannelById)

    def roleById(id: RoleId):      Option[Role] = guild.roles.get(id)
    def rolesByName(name: String): Seq[Role]    = guild.roles.values.filter(_.name == name).toSeq

    def emojiById(id: EmojiId):     Option[GuildEmoji] = guild.emojis.get(id)
    def emojisByName(name: String): Seq[GuildEmoji]    = guild.emojis.values.filter(_.name == name).toSeq

    def memberById(id: UserId):     Option[GuildMember] = guild.members.get(id)
    def memberFromUser(user: User): Option[GuildMember] = memberById(user.id)

    def membersWithRole(roleId: RoleId): Seq[GuildMember] =
      guild.members.collect {
        case (_, mem) if mem.roleIds.contains(roleId) => mem
      }.toSeq

    def presenceById(id: UserId):    Option[Presence] = guild.presences.get(id)
    def presenceForUser(user: User): Option[Presence] = presenceById(user.id)

    def fetchEmojis[Context](context: Context = NotUsed) = Request(ListGuildEmojis(guild.id), context)
    def fetchSingleEmoji[Context](emojiId: EmojiId, context: Context = NotUsed) =
      Request(GetGuildEmoji(emojiId, guild.id), context)
    def createEmoji[Context](name: String, image: ImageData, context: Context = NotUsed) =
      Request(CreateGuildEmoji(guild.id, CreateGuildEmojiData(name, image)), context)

    def createRole[Context](
        name: Option[String] = None,
        permissions: Option[Permission] = None,
        color: Option[Int] = None,
        hoist: Option[Boolean] = None,
        mentionable: Option[Boolean] = None,
        context: Context = NotUsed
    ) = Request(CreateGuildRole(guild.id, CreateGuildRoleData(name, permissions, color, hoist, mentionable)), context)

    def leaveGuild[Context](context: Context = NotUsed) = Request(LeaveGuild(guild.id), context)
    def delete[Context](context: Context = NotUsed)     = Request(DeleteGuild(guild.id), context)
  }

  implicit class GuildMemberSyntax(val guildMember: GuildMember) extends AnyVal {
    def rolesForUser(implicit snapshot: CacheSnapshot): Seq[Role] =
      guildMember.guild.map(g => guildMember.roleIds.flatMap(g.roles.get)).toSeq.flatten

    def modify[Context](
        nick: Option[String],
        roles: Option[Seq[RoleId]],
        mute: Option[Boolean],
        deaf: Option[Boolean],
        channelId: Option[ChannelId] = None,
        context: Context = NotUsed
    ) = Request(
      ModifyGuildMember(
        guildMember.guildId,
        guildMember.userId,
        ModifyGuildMemberData(nick, roles, mute, deaf, channelId)
      ),
      context
    )

    def addRole[Context](roleId: RoleId, context: Context = NotUsed) =
      Request(AddGuildMemberRole(guildMember.guildId, guildMember.userId, roleId), context)
    def removeRole[Context](roleId: RoleId, context: Context = NotUsed) =
      Request(RemoveGuildMemberRole(guildMember.guildId, guildMember.userId, roleId), context)
    def kick[Context](context: Context = NotUsed) =
      Request(RemoveGuildMember(guildMember.guildId, guildMember.userId), context)
    def ban[Context](deleteMessageDays: Int, context: Context = NotUsed) =
      Request(CreateGuildBan(guildMember.guildId, guildMember.userId, CreateGuildBanData(deleteMessageDays)), context)
    def unban[Context](context: Context = NotUsed) =
      Request(RemoveGuildBan(guildMember.guildId, guildMember.userId), context)
  }

  implicit class GuildEmojiSyntax(val emoji: GuildEmoji) extends AnyVal {
    def asString: String =
      if (!emoji.managed) s"${emoji.name}:${emoji.id}" else ???
    def modify[Context](name: String, guildId: GuildId, context: Context = NotUsed) =
      Request(ModifyGuildEmoji(emoji.id, guildId, ModifyGuildEmojiData(name)), context)
    def delete[Context](guildId: GuildId, context: Context = NotUsed) =
      Request(DeleteGuildEmoji(emoji.id, guildId), context)
  }

  implicit class RoleSyntax(val role: Role) extends AnyVal {
    def mention: String = s"<@&${role.id}>"
    def modify[Context](
        guildId: GuildId,
        name: Option[String],
        permissions: Option[Permission],
        color: Option[Int],
        hoist: Option[Boolean],
        mentionable: Option[Boolean],
        context: Context = NotUsed
    ) =
      Request(
        ModifyGuildRole(guildId, role.id, ModifyGuildRoleData(name, permissions, color, hoist, mentionable)),
        context
      )

    def delete[Context](guildId: GuildId, context: Context = NotUsed) =
      Request(DeleteGuildRole(guildId, role.id), context)
  }

  implicit class MessageSyntax(val message: Message) extends AnyVal {
    def createReaction[Context](guildEmoji: GuildEmoji, context: Context = NotUsed) =
      Request(CreateReaction(message.channelId, message.id, guildEmoji.asString), context)

    def deleteOwnReaction[Context](guildEmoji: GuildEmoji, context: Context = NotUsed) =
      Request(DeleteOwnReaction(message.channelId, message.id, guildEmoji.asString), context)

    def deleteUserReaction[Context](guildEmoji: GuildEmoji, userId: UserId, context: Context = NotUsed) =
      Request(DeleteUserReaction(message.channelId, message.id, guildEmoji.asString, userId), context)

    def fetchReactions[Context](guildEmoji: GuildEmoji, context: Context = NotUsed) =
      Request(GetReactions(message.channelId, message.id, guildEmoji.asString), context)

    def deleteAllReactions[Context](context: Context = NotUsed) =
      Request(DeleteAllReactions(message.channelId, message.id), context)

    def edit[Context](
        content: Option[String] = Some(message.content),
        embed: Option[OutgoingEmbed] = message.embeds.headOption.map(_.toOutgoing),
        context: Context = NotUsed
    ) = Request(EditMessage(message.channelId, message.id, EditMessageData(content, embed)))

    def delete[Context](context: Context = NotUsed) = Request(DeleteMessage(message.channelId, message.id), context)

    def pin[Context](context: Context = NotUsed) =
      Request(AddPinnedChannelMessages(message.channelId, message.id), context)
    def unpin[Context](context: Context = NotUsed) =
      Request(DeletePinnedChannelMessages(message.channelId, message.id), context)
  }

  implicit class UserSyntax(val user: User) extends AnyVal {
    def getDMChannel(implicit snapshot: CacheSnapshot): Option[DMChannel] = snapshot.dmChannels.collectFirst {
      case (_, ch) if ch.userId == user.id => ch
    }
    def createDMChannel[Context](context: Context = NotUsed) = Request(CreateDm(CreateDMData(user.id)), context)

    def mention:     String = s"<@${user.id}>"
    def mentionNick: String = s"<@!${user.id}>"
  }

  implicit class DiscordClientSyntax(val client: DiscordClient) extends AnyVal {
    def fetchChannel[Context](channelId: ChannelId, context: Context = NotUsed) =
      Request(GetChannel(channelId), context)
    def fetchGuild[Context](guildId: GuildId, context: Context = NotUsed) =
      Request(GetGuild(guildId), context)
    def fetchUser[Context](userId: UserId, context: Context = NotUsed) = Request(GetUser(userId), context)

    def createGuild[Context](
        name: String,
        region: String,
        icon: String,
        verificationLevel: VerificationLevel,
        defaultMessageNotifications: NotificationLevel,
        roles: Seq[Role],
        channels: Seq[CreateGuildChannelData],
        context: Context = NotUsed
    ) =
      Request(
        CreateGuild(
          CreateGuildData(name, region, icon, verificationLevel, defaultMessageNotifications, roles, channels)
        ),
        context
      )

    def fetchCurrentUser[Context](context: Context = NotUsed) = Request(GetCurrentUser, context)
    def fetchCurrentUserGuilds[Context](
        before: Option[GuildId] = None,
        after: Option[GuildId] = None,
        limit: Int = 100,
        context: Context = NotUsed
    ) = Request(GetCurrentUserGuilds(GetCurrentUserGuildsData(before, after, limit)), context)

    def fetchUserDMs[Context](context: Context = NotUsed) = Request(GetUserDMs, context)
    def createGroupDM[Context](accessTokens: Seq[String], nicks: Map[UserId, String], context: Context = NotUsed) =
      Request(CreateGroupDm(CreateGroupDMData(accessTokens, nicks)), context)

    def fetchInvite[Context](inviteCode: String, context: Context = NotUsed) = Request(GetInvite(inviteCode), context)
    def deleteInvite[Context](inviteCode: String, context: Context = NotUsed) =
      Request(DeleteInvite(inviteCode), context)
    def acceptInvite[Context](inviteCode: String, context: Context = NotUsed) =
      Request(AcceptInvite(inviteCode), context)
  }

  implicit class BotUserSyntax(val botUser: User @@ BotUser) extends AnyVal {
    def setNick[Context](guildId: GuildId, nick: String, context: Context = NotUsed) =
      Request(ModifyBotUsersNick(guildId, ModifyBotUsersNickData(nick)), context)
  }
}
