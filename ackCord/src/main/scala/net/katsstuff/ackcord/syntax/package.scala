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
package net.katsstuff.ackcord

import java.nio.file.Path

import akka.NotUsed
import akka.actor.ActorRef
import net.katsstuff.ackcord.CacheSnapshotLike.BotUser
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.rest.Requests._
import shapeless.tag.@@

package object syntax {

  implicit class ChannelSyntax(val channel: Channel) extends AnyVal {
    def delete[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(DeleteCloseChannel(channel.id), context, sendResponseTo)
    def mention: String = s"<#${channel.id}>"
  }

  implicit class TChannelSyntax(val tChannel: TChannel) extends AnyVal {
    def sendMessage[Context](
        content: String,
        tts: Boolean = false,
        file: Option[Path] = None,
        embed: Option[OutgoingEmbed] = None,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(CreateMessage(tChannel.id, CreateMessageData(content, None, tts, file, embed)), NotUsed, sendResponseTo)

    def fetchMessagesAround[Context](
        around: MessageId,
        limit: Option[Int] = Some(50),
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      GetChannelMessages(tChannel.id, GetChannelMessagesData(Some(around), None, None, limit)),
      context,
      sendResponseTo
    )
    def fetchMessagesBefore[Context](
        before: MessageId,
        limit: Option[Int] = Some(50),
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      GetChannelMessages(tChannel.id, GetChannelMessagesData(None, Some(before), None, limit)),
      context,
      sendResponseTo
    )
    def fetchMessagesAfter[Context](
        after: MessageId,
        limit: Option[Int] = Some(50),
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, Some(after), limit)),
      context,
      sendResponseTo
    )
    def fetchMessages[Context](
        limit: Option[Int] = Some(50),
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) =
      Request(GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, None, limit)), context, sendResponseTo)

    def fetchMessage[Context](id: MessageId, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetChannelMessage(tChannel.id, id), context, sendResponseTo)

    def bulkDelete[Context](ids: Seq[MessageId], context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(BulkDeleteMessages(tChannel.id, BulkDeleteMessagesData(ids)), context, sendResponseTo)

    def editChannelPermissions[Context](
        role: Role,
        allow: Permission,
        deny: Permission,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      EditChannelPermissions(tChannel.id, role.id, EditChannelPermissionsData(allow, deny, "role")),
      context,
      sendResponseTo
    )

    def deleteChannelPermissions[Context](
        user: User,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(DeleteChannelPermission(tChannel.id, user.id), context, sendResponseTo)

    def triggerTyping[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(TriggerTypingIndicator(tChannel.id), context, sendResponseTo)

    def fetchPinnedMessages[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetPinnedMessages(tChannel.id), context, sendResponseTo)
  }

  implicit class GuildChannelSyntax(val channel: GuildChannel) extends AnyVal {
    def category(implicit snapshot: CacheSnapshot): Option[GuildCategory] =
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
        permissionOverwrites: Map[UserOrRoleId, PermissionOverwrite] = channel.permissionOverwrites,
        category: Option[ChannelId],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      ModifyChannel(
        channel.id,
        ModifyChannelData(name, position, topic, Some(nsfw), None, None, permissionOverwrites.values.toSeq, category)
      ),
      context,
      sendResponseTo
    )

    def fetchInvites[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetChannelInvites(channel.id), context, sendResponseTo)

    def createInvite[Context](
        maxAge: Int = 86400,
        maxUses: Int = 0,
        temporary: Boolean = false,
        unique: Boolean = false,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      CreateChannelInvite(channel.id, CreateChannelInviteData(maxAge, maxUses, temporary, unique)),
      context,
      sendResponseTo
    )
  }

  implicit class VGuildChannelSyntax(val channel: VGuildChannel) extends AnyVal {
    def modify[Context](
        name: String = channel.name,
        position: Int = channel.position,
        bitrate: Int = channel.bitrate,
        userLimit: Int = channel.userLimit,
        permissionOverwrites: Map[UserOrRoleId, PermissionOverwrite] = channel.permissionOverwrites,
        category: Option[ChannelId],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      ModifyChannel(
        channel.id,
        ModifyChannelData(
          name = name,
          position = position,
          topic = None,
          nsfw = Some(channel.nsfw),
          bitrate = Some(bitrate),
          userLimit = Some(userLimit),
          permissionOverwrites = permissionOverwrites.values.toSeq,
          parentId = category
        )
      ),
      context,
      sendResponseTo
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
    def vChannelById(id: ChannelId)(implicit snapshot: CacheSnapshot): Option[VGuildChannel] = channelById(id).collect {
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
        permissionOverwrites: Map[UserOrRoleId, PermissionOverwrite] = category.permissionOverwrites,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      ModifyChannel(
        category.id,
        ModifyChannelData(
          name = name,
          position = position,
          topic = None,
          nsfw = Some(category.nsfw),
          bitrate = None,
          userLimit = None,
          permissionOverwrites = permissionOverwrites.values.toSeq,
          parentId = category.parentId
        )
      ),
      context,
      sendResponseTo
    )
  }

  implicit class GuildSyntax(val guild: Guild) extends AnyVal {
    def owner(implicit snapshot: CacheSnapshot): Option[User] = snapshot.getUser(guild.ownerId)
    def everyoneRole(implicit snapshot: CacheSnapshot): Role = guild.roles(RoleId(guild.id)) //The everyone role should always be present
    def mentionEveryone: String = "@everyone"

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
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
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
      context,
      sendResponseTo
    )

    def fetchAllChannels[Context](
        channelId: ChannelId,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(GetGuildChannels(guild.id), context, sendResponseTo)

    def createChannel[Context](
        name: String,
        `type`: Option[ChannelType],
        bitrate: Option[Int],
        userLimit: Option[Int],
        permissionOverwrites: Option[Seq[PermissionOverwrite]],
        category: Option[ChannelId],
        nsfw: Option[Boolean],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      CreateGuildChannel(
        guild.id,
        CreateGuildChannelData(name, `type`, bitrate, userLimit, permissionOverwrites, category, nsfw)
      ),
      context,
      sendResponseTo
    )

    def modifyChannelPositions[Context](
        newPositions: Map[ChannelId, Int],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      ModifyGuildChannelPositions(guild.id, newPositions.map(t => ModifyGuildChannelPositionsData(t._1, t._2)).toSeq),
      context,
      sendResponseTo
    )

    def fetchGuildMember[Context](userId: UserId, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuildMember(guild.id, userId), context, sendResponseTo)

    def fetchBans[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuildBans(guild.id), context, sendResponseTo)

    def unban[Context](userId: UserId, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(RemoveGuildBan(guild.id, userId), context, sendResponseTo)

    def fetchAllGuildMember[Context](
        limit: Option[Int] = None,
        after: Option[UserId] = None,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(ListGuildMembers(guild.id, ListGuildMembersData(limit, after)), context, sendResponseTo)

    def addGuildMember[Context](
        userId: UserId,
        accessToken: String,
        nick: Option[String],
        roles: Option[Seq[RoleId]],
        mute: Option[Boolean],
        deaf: Option[Boolean],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      AddGuildMember(guild.id, userId, AddGuildMemberData(accessToken, nick, roles, mute, deaf)),
      context,
      sendResponseTo
    )

    def fetchRoles[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuildRoles(guild.id), context, sendResponseTo)
    def createRoles[Context](
        name: Option[String],
        permissions: Option[Permission],
        color: Option[Int],
        hoist: Option[Boolean],
        mentionable: Option[Boolean],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      CreateGuildRole(guild.id, CreateGuildRoleData(name, permissions, color, hoist, mentionable)),
      context,
      sendResponseTo
    )

    def modifyRolePositions[Context](
        newPositions: Map[RoleId, Int],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      ModifyGuildRolePositions(guild.id, newPositions.map(t => ModifyGuildRolePositionsData(t._1, t._2)).toSeq),
      context,
      sendResponseTo
    )

    def fetchPruneCount[Context](days: Int, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuildPruneCount(guild.id, GuildPruneData(days)), context, sendResponseTo)
    def beginPrune[Context](days: Int, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(BeginGuildPrune(guild.id, GuildPruneData(days)), context, sendResponseTo)

    def fetchVoiceRegions[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuildVoiceRegions(guild.id), context, sendResponseTo)

    def fetchInvites[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuildInvites(guild.id), context, sendResponseTo)

    def fetchIntegrations[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuildIntegrations(guild.id), context, sendResponseTo)
    def createIntegration[Context](
        tpe: String,
        id: IntegrationId,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(CreateGuildIntegration(guild.id, CreateGuildIntegrationData(tpe, id)), context, sendResponseTo)
    def modifyIntegration[Context](
        id: IntegrationId,
        expireBehavior: Int,
        expireGracePeriod: Int,
        enableEmoticons: Boolean,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      ModifyGuildIntegration(
        guild.id,
        id,
        ModifyGuildIntegrationData(expireBehavior, expireGracePeriod, enableEmoticons)
      ),
      context,
      sendResponseTo
    )
    def removeIntegration[Context](
        id: IntegrationId,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(DeleteGuildIntegration(guild.id, id), context, sendResponseTo)
    def syncIntegration[Context](
        id: IntegrationId,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(SyncGuildIntegration(guild.id, id), context, sendResponseTo)

    def fetchEmbed[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuildEmbed(guild.id), context, sendResponseTo)
    def modifyEmbed[Context](embed: GuildEmbed, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(ModifyGuildEmbed(guild.id, embed), context, sendResponseTo)

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

    def fetchEmojis[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(ListGuildEmojis(guild.id), context, sendResponseTo)
    def fetchSingleEmoji[Context](
        emojiId: EmojiId,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(GetGuildEmoji(emojiId, guild.id), context, sendResponseTo)
    def createEmoji[Context](
        name: String,
        image: ImageData,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(CreateGuildEmoji(guild.id, CreateGuildEmojiData(name, image)), context, sendResponseTo)

    def createRole[Context](
        name: Option[String] = None,
        permissions: Option[Permission] = None,
        color: Option[Int] = None,
        hoist: Option[Boolean] = None,
        mentionable: Option[Boolean] = None,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      CreateGuildRole(guild.id, CreateGuildRoleData(name, permissions, color, hoist, mentionable)),
      context,
      sendResponseTo
    )

    def fetchAuditLog[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuildAuditLog(guild.id), context, sendResponseTo)

    def leaveGuild[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(LeaveGuild(guild.id), context, sendResponseTo)
    def delete[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(DeleteGuild(guild.id), context, sendResponseTo)
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
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      ModifyGuildMember(
        guildMember.guildId,
        guildMember.userId,
        ModifyGuildMemberData(nick, roles, mute, deaf, channelId)
      ),
      context,
      sendResponseTo
    )

    def addRole[Context](roleId: RoleId, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(AddGuildMemberRole(guildMember.guildId, guildMember.userId, roleId), context, sendResponseTo)
    def removeRole[Context](roleId: RoleId, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(RemoveGuildMemberRole(guildMember.guildId, guildMember.userId, roleId), context, sendResponseTo)
    def kick[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(RemoveGuildMember(guildMember.guildId, guildMember.userId), context, sendResponseTo)
    def ban[Context](deleteMessageDays: Int, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(
        CreateGuildBan(guildMember.guildId, guildMember.userId, CreateGuildBanData(deleteMessageDays)),
        context,
        sendResponseTo
      )
    def unban[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(RemoveGuildBan(guildMember.guildId, guildMember.userId), context, sendResponseTo)
  }

  implicit class GuildEmojiSyntax(val emoji: GuildEmoji) extends AnyVal {
    def asString: String =
      if (!emoji.managed) s"${emoji.name}:${emoji.id}" else ???
    def modify[Context](
        name: String,
        guildId: GuildId,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(ModifyGuildEmoji(emoji.id, guildId, ModifyGuildEmojiData(name)), context, sendResponseTo)
    def delete[Context](guildId: GuildId, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(DeleteGuildEmoji(emoji.id, guildId), context, sendResponseTo)
  }

  implicit class RoleSyntax(val role: Role) extends AnyVal {
    def mention: String = s"<@&${role.id}>"
    def modify[Context](
        name: Option[String],
        permissions: Option[Permission],
        color: Option[Int],
        hoist: Option[Boolean],
        mentionable: Option[Boolean],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      ModifyGuildRole(role.guildId, role.id, ModifyGuildRoleData(name, permissions, color, hoist, mentionable)),
      context,
      sendResponseTo
    )

    def delete[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(DeleteGuildRole(role.guildId, role.id), context, sendResponseTo)
  }

  implicit class MessageSyntax(val message: Message) extends AnyVal {
    def createReaction[Context](
        guildEmoji: GuildEmoji,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(CreateReaction(message.channelId, message.id, guildEmoji.asString), context, sendResponseTo)

    def deleteOwnReaction[Context](
        guildEmoji: GuildEmoji,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(DeleteOwnReaction(message.channelId, message.id, guildEmoji.asString), context, sendResponseTo)

    def deleteUserReaction[Context](
        guildEmoji: GuildEmoji,
        userId: UserId,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(DeleteUserReaction(message.channelId, message.id, guildEmoji.asString, userId), context, sendResponseTo)

    def fetchReactions[Context](
        guildEmoji: GuildEmoji,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(GetReactions(message.channelId, message.id, guildEmoji.asString), context, sendResponseTo)

    def deleteAllReactions[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(DeleteAllReactions(message.channelId, message.id), context, sendResponseTo)

    def edit[Context](
        content: Option[String] = Some(message.content),
        embed: Option[OutgoingEmbed] = message.embeds.headOption.map(_.toOutgoing),
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(EditMessage(message.channelId, message.id, EditMessageData(content, embed)), content, sendResponseTo)

    def delete[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(DeleteMessage(message.channelId, message.id), context, sendResponseTo)

    def pin[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(AddPinnedChannelMessages(message.channelId, message.id), context, sendResponseTo)
    def unpin[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(DeletePinnedChannelMessages(message.channelId, message.id), context, sendResponseTo)
  }

  implicit class UserSyntax(val user: User) extends AnyVal {
    def getDMChannel(implicit snapshot: CacheSnapshot): Option[DMChannel] = snapshot.dmChannels.collectFirst {
      case (_, ch) if ch.userId == user.id => ch
    }
    def createDMChannel[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(CreateDm(CreateDMData(user.id)), context, sendResponseTo)

    def mention:     String = s"<@${user.id}>"
    def mentionNick: String = s"<@!${user.id}>"
  }

  implicit class DiscordClientSyntax(val client: ActorRef @@ DiscordClient) extends AnyVal {
    def fetchChannel[Context](
        channelId: ChannelId,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(GetChannel(channelId), context, sendResponseTo)
    def fetchGuild[Context](guildId: GuildId, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetGuild(guildId), context, sendResponseTo)
    def fetchUser[Context](userId: UserId, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetUser(userId), context, sendResponseTo)

    def createGuild[Context](
        name: String,
        region: String,
        icon: String,
        verificationLevel: VerificationLevel,
        defaultMessageNotifications: NotificationLevel,
        roles: Seq[Role],
        channels: Seq[CreateGuildChannelData],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(
      CreateGuild(CreateGuildData(name, region, icon, verificationLevel, defaultMessageNotifications, roles, channels)),
      context,
      sendResponseTo
    )

    def fetchCurrentUser[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetCurrentUser, context, sendResponseTo)
    def fetchCurrentUserGuilds[Context](
        before: Option[GuildId] = None,
        after: Option[GuildId] = None,
        limit: Int = 100,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(GetCurrentUserGuilds(GetCurrentUserGuildsData(before, after, limit)), context, sendResponseTo)

    def fetchUserDMs[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetUserDMs, context, sendResponseTo)
    def createGroupDM[Context](
        accessTokens: Seq[String],
        nicks: Map[UserId, String],
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(CreateGroupDm(CreateGroupDMData(accessTokens, nicks)), context, sendResponseTo)

    def fetchInvite[Context](inviteCode: String, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(GetInvite(inviteCode), context, sendResponseTo)
    def deleteInvite[Context](inviteCode: String, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(DeleteInvite(inviteCode), context, sendResponseTo)
    def acceptInvite[Context](inviteCode: String, context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(AcceptInvite(inviteCode), context, sendResponseTo)

    def fetchVoiceRegions[Context](context: Context = NotUsed, sendResponseTo: Option[ActorRef] = None) =
      Request(ListVoiceRegions, context, sendResponseTo)
  }

  implicit class BotUserSyntax(val botUser: User @@ BotUser) extends AnyVal {
    def setNick[Context](
        guildId: GuildId,
        nick: String,
        context: Context = NotUsed,
        sendResponseTo: Option[ActorRef] = None
    ) = Request(ModifyBotUsersNick(guildId, ModifyBotUsersNickData(nick)), context, sendResponseTo)
  }
}
