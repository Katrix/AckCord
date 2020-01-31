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
package ackcord

import java.nio.file.Path

import ackcord.data._
import ackcord.requests._
import ackcord.util.{JsonOption, JsonSome, JsonUndefined}

package object syntax {

  implicit class ChannelSyntax(private val channel: Channel) extends AnyVal {

    /**
      * Delete or close this channel.
      */
    def delete = DeleteCloseChannel(channel.id)

    /**
      * If this is a text channel, convert it to one.
      */
    def asTChannel: Option[TChannel] = channel match {
      case gChannel: TChannel => Some(gChannel)
      case _                  => None
    }

    /**
      * If this is a DM channel, convert it to one.
      */
    def asDMChannel: Option[DMChannel] = channel match {
      case gChannel: DMChannel => Some(gChannel)
      case _                   => None
    }

    /**
      * If this is a group DM channel, convert it to one.
      */
    def asGroupDMChannel: Option[GroupDMChannel] = channel match {
      case gChannel: GroupDMChannel => Some(gChannel)
      case _                        => None
    }

    /**
      * If this is a guild channel, convert it to one.
      */
    def asGuildChannel: Option[GuildChannel] = channel match {
      case gChannel: GuildChannel => Some(gChannel)
      case _                      => None
    }

    /**
      * If this is a text guild channel, convert it to one.
      */
    def asTGuildChannel: Option[TGuildChannel] = channel match {
      case gChannel: TGuildChannel => Some(gChannel)
      case _                       => None
    }

    /**
      * If this is a text voice channel, convert it to one.
      */
    def asVGuildChannel: Option[VGuildChannel] = channel match {
      case gChannel: VGuildChannel => Some(gChannel)
      case _                       => None
    }

    /**
      * If this is a category, convert it to one.
      */
    def asCategory: Option[GuildCategory] = channel match {
      case cat: GuildCategory => Some(cat)
      case _                  => None
    }
  }

  implicit class TChannelSyntax(private val tChannel: TChannel) extends AnyVal {

    /**
      * Send a message to this channel.
      * @param content The content of the message.
      * @param tts If this is a text-to-speech message.
      * @param files The files to send with this message. You can reference these
      *              files in the embed using `attachment://filename`.
      * @param embed An embed to send with this message.
      */
    def sendMessage(
        content: String = "",
        tts: Boolean = false,
        files: Seq[Path] = Seq.empty,
        embed: Option[OutgoingEmbed] = None
    ) = CreateMessage(tChannel.id, CreateMessageData(content, None, tts, files.map(CreateMessageFile.FromPath), embed))

    /**
      * Fetch messages around a message id.
      * @param around The message to get messages around.
      * @param limit The max amount of messages to return.
      */
    def fetchMessagesAround(around: MessageId, limit: Option[Int] = None) =
      GetChannelMessages(tChannel.id, GetChannelMessagesData(Some(around), None, None, limit))

    /**
      * Fetch messages before a message id.
      * @param before The message to get messages before.
      * @param limit The max amount of messages to return.
      */
    def fetchMessagesBefore(before: MessageId, limit: Option[Int] = None) =
      GetChannelMessages(tChannel.id, GetChannelMessagesData(None, Some(before), None, limit))

    /**
      * Fetch messages after a message id.
      * @param after The message to get messages after.
      * @param limit The max amount of messages to return.
      */
    def fetchMessagesAfter(after: MessageId, limit: Option[Int] = None) =
      GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, Some(after), limit))

    /**
      * Fetch messages in this channel.
      * @param limit The max amount of messages to return.
      */
    def fetchMessages(limit: Option[Int] = None) =
      GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, None, limit))

    /**
      * Fetch a message in this channel.
      */
    def fetchMessage(id: MessageId) = GetChannelMessage(tChannel.id, id)

    /**
      * Triggers typing in a channel.
      */
    def triggerTyping = TriggerTypingIndicator(tChannel.id)
  }

  implicit class GuildChannelSyntax(private val channel: GuildChannel) extends AnyVal {

    /**
      * Get the category of this channel using a preexisting guild.
      */
    def categoryFromGuild(guild: Guild): Option[GuildCategory] =
      for {
        catId <- channel.parentId
        cat <- guild.channels.collectFirst {
          case (_, ch: GuildCategory) if ch.id == catId => ch
        }
      } yield cat

    /**
      * Edit the permission overrides of a role
      * @param roleId The role to edit the permissions for.
      * @param allow The new allowed permissions.
      * @param deny The new denied permissions.
      */
    def editChannelPermissionsRole(
        roleId: RoleId,
        allow: Permission,
        deny: Permission
    ) = EditChannelPermissions(
      channel.id,
      roleId,
      EditChannelPermissionsData(allow, deny, PermissionOverwriteType.Role)
    )

    /**
      * Edit the permission overrides of a user
      * @param userId The user to edit the permissions for.
      * @param allow The new allowed permissions.
      * @param deny The new denied permissions.
      */
    def editChannelPermissionsUser(
        userId: UserId,
        allow: Permission,
        deny: Permission
    ) = EditChannelPermissions(
      channel.id,
      userId,
      EditChannelPermissionsData(allow, deny, PermissionOverwriteType.Member)
    )

    /**
      * Delete the permission overwrites for a user
      * @param userId The user to remove the permission overwrites for
      */
    def deleteChannelPermissionsUser(userId: UserId) =
      DeleteChannelPermission(channel.id, userId)

    /**
      * Delete the permission overwrites for a role
      * @param roleId The role to remove the permission overwrites for
      */
    def deleteChannelPermissionsRole(roleId: RoleId) =
      DeleteChannelPermission(channel.id, roleId)
  }

  implicit class TGuildChannelSyntax(private val channel: TGuildChannel) extends AnyVal {

    /**
      * Update the settings of this channel.
      * @param name New name of the channel.
      * @param position New position of the channel.
      * @param topic The new channel topic for text channels.
      * @param nsfw If the channel is NSFW for text channels.
      * @param rateLimitPerUser The new user ratelimit for guild text channels.
      * @param permissionOverwrites The new channel permission overwrites.
      * @param category The new category id of the channel.
      */
    def modify(
        name: JsonOption[String] = JsonUndefined,
        position: JsonOption[Int] = JsonUndefined,
        topic: JsonOption[String] = JsonUndefined,
        nsfw: JsonOption[Boolean] = JsonUndefined,
        rateLimitPerUser: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[SnowflakeMap[UserOrRoleTag, PermissionOverwrite]] = JsonUndefined,
        category: JsonOption[ChannelId] = JsonUndefined
    ) = ModifyChannel(
      channel.id,
      ModifyChannelData(
        name = name,
        position = position,
        topic = topic,
        nsfw = nsfw,
        rateLimitPerUser = rateLimitPerUser,
        bitrate = JsonUndefined,
        userLimit = JsonUndefined,
        permissionOverwrites = permissionOverwrites.map(_.values.toSeq),
        parentId = category
      )
    )

    /**
      * Fetch all the invites created for this channel.
      */
    def fetchInvites = GetChannelInvites(channel.id)

    /**
      * Create an invite for this channel.
      * @param maxAge Duration in seconds before this invite expires.
      * @param maxUses Amount of times this invite can be used before expiring,
      *                or 0 for unlimited.
      * @param temporary If this invite only grants temporary membership.
      * @param unique If true, guarantees to create a new invite.
      */
    def createInvite(
        maxAge: Int = 86400,
        maxUses: Int = 0,
        temporary: Boolean = false,
        unique: Boolean = false,
        targetUser: Option[UserId],
        targetUserType: Option[Int]
    ) =
      CreateChannelInvite(
        channel.id,
        CreateChannelInviteData(maxAge, maxUses, temporary, unique, targetUser, targetUserType)
      )

    /**
      * Delete multiple messages at the same time.
      * @param ids The messages to delete.
      */
    def bulkDelete(ids: Seq[MessageId]) =
      BulkDeleteMessages(channel.id, BulkDeleteMessagesData(ids))

    /**
      * Fetch all the pinned messages in this channel.
      */
    def fetchPinnedMessages = GetPinnedMessages(channel.id)

    /**
      * Create a webhook for this channel.
      * @param name The webhook name.
      * @param avatar The webhook avatar.
      */
    def createWebhook(name: String, avatar: Option[ImageData]) =
      CreateWebhook(channel.id, CreateWebhookData(name, avatar))

    /**
      * Fetch the webhooks for this channel.
      */
    def fetchWebhooks = GetChannelWebhooks(channel.id)
  }

  implicit class VGuildChannelSyntax(private val channel: VGuildChannel) extends AnyVal {

    /**
      * Update the settings of this channel.
      * @param name New name of the channel.
      * @param position New position of the channel.
      * @param bitrate The new channel bitrate for voice channels.
      * @param userLimit The new user limit for voice channel.
      * @param permissionOverwrites The new channel permission overwrites.
      * @param category The new category id of the channel.
      */
    def modify(
        name: JsonOption[String] = JsonUndefined,
        position: JsonOption[Int] = JsonUndefined,
        bitrate: JsonOption[Int] = JsonUndefined,
        userLimit: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[SnowflakeMap[UserOrRoleTag, PermissionOverwrite]] = JsonUndefined,
        category: JsonOption[ChannelId] = JsonUndefined
    ) = ModifyChannel(
      channel.id,
      ModifyChannelData(
        name = name,
        position = position,
        nsfw = JsonUndefined,
        bitrate = bitrate,
        userLimit = userLimit,
        permissionOverwrites = permissionOverwrites.map(_.values.toSeq),
        parentId = category
      )
    )

    /**
      * Get the users connected to this voice channel.
      */
    def connectedUsers(implicit c: CacheSnapshot): Seq[User] =
      c.getGuild(channel.guildId).fold(Nil: Seq[User]) { g =>
        connectedUsers(g).toList.flatMap(_.resolve)
      }

    /**
      * Get the users connected to this voice channel using an preexisting guild.
      */
    def connectedUsers(guild: Guild): Seq[UserId] =
      guild.voiceStates.filter(_._2.channelId.contains(channel.id)).keys.toSeq

    /**
      * Get the guild members connected to this voice channel.
      */
    def connectedMembers(implicit c: CacheSnapshot): Seq[GuildMember] =
      c.getGuild(channel.guildId).fold(Nil: Seq[GuildMember])(g => connectedUsers(g).flatMap(g.memberById(_)))

    /**
      * Get the guild members connected to this voice channel using an preexisting guild.
      */
    def connectedMembers(guild: Guild): Seq[GuildMember] =
      connectedUsers(guild).flatMap(guild.memberById(_))
  }

  implicit class CategorySyntax(private val category: GuildCategory) extends AnyVal {

    /**
      * Get all the channels in this category.
      */
    def channels(implicit snapshot: CacheSnapshot): Seq[GuildChannel] =
      category.guild
        .map { g =>
          g.channels.collect {
            case (_, ch) if ch.parentId.contains(category.id) => ch
          }.toSeq
        }
        .getOrElse(Seq.empty)

    /**
      * Get all the channels in this category using an preexisting guild.
      */
    def channels(guild: Guild): Seq[GuildChannel] =
      guild.channels.collect { case (_, ch) if ch.parentId.contains(category.id) => ch }.toSeq

    /**
      * Get all the text channels in this category.
      */
    def tChannels(implicit snapshot: CacheSnapshot): Seq[TGuildChannel] =
      channels.collect { case tChannel: TGuildChannel => tChannel }

    /**
      * Get all the text channels in this category using an preexisting guild.
      */
    def tChannels(guild: Guild): Seq[TGuildChannel] =
      channels(guild).collect { case tChannel: TGuildChannel => tChannel }

    /**
      * Get all the voice channels in this category.
      */
    def vChannels(implicit snapshot: CacheSnapshot): Seq[VGuildChannel] =
      channels.collect { case tChannel: VGuildChannel => tChannel }

    /**
      * Get all the voice channels in this category using an preexisting guild.
      */
    def vChannels(guild: Guild): Seq[VGuildChannel] =
      channels(guild).collect { case tChannel: VGuildChannel => tChannel }

    /**
      * Get a channel by id in this category.
      * @param id The id of the channel.
      */
    def channelById(id: ChannelId)(implicit snapshot: CacheSnapshot): Option[GuildChannel] =
      channels.find(_.id == id)

    /**
      * Get a channel by id in this category using an preexisting guild.
      * @param id The id of the channel.
      */
    def channelById(id: ChannelId, guild: Guild): Option[GuildChannel] = channels(guild).find(_.id == id)

    /**
      * Get a text channel by id in this category.
      * @param id The id of the channel.
      */
    def tChannelById(
        id: ChannelId
    )(implicit snapshot: CacheSnapshot): Option[TGuildChannel] =
      channelById(id).collect {
        case tChannel: TGuildChannel => tChannel
      }

    /**
      * Get a text channel by id in this category using an preexisting guild.
      * @param id The id of the channel.
      */
    def tChannelById(id: ChannelId, guild: Guild): Option[TGuildChannel] = channelById(id, guild).collect {
      case tChannel: TGuildChannel => tChannel
    }

    /**
      * Get a voice channel by id in this category.
      * @param id The id of the channel.
      */
    def vChannelById[F[_]](
        id: ChannelId
    )(implicit snapshot: CacheSnapshot): Option[VGuildChannel] =
      channelById(id).collect {
        case vChannel: VGuildChannel => vChannel
      }

    /**
      * Get a voice channel by id in this category using an preexisting guild.
      * @param id The id of the channel.
      */
    def vChannelById(id: ChannelId, guild: Guild): Option[VGuildChannel] = channelById(id, guild).collect {
      case vChannel: VGuildChannel => vChannel
    }

    /**
      * Get all the channels with a name in this category.
      * @param name The name of the guilds.
      */
    def channelsByName(name: String)(implicit snapshot: CacheSnapshot): Seq[GuildChannel] =
      channels.filter(_.name == name)

    /**
      * Get all the channels with a name in this category using an preexisting guild.
      * @param name The name of the guilds.
      */
    def channelsByName(name: String, guild: Guild): Seq[GuildChannel] =
      channels(guild).filter(_.name == name)

    /**
      * Get all the text channels with a name in this category.
      * @param name The name of the guilds.
      */
    def tChannelsByName(name: String)(implicit snapshot: CacheSnapshot): Seq[TGuildChannel] =
      tChannels.filter(_.name == name)

    /**
      * Get all the text channels with a name in this category using an preexisting guild.
      * @param name The name of the guilds.
      */
    def tChannelsByName(name: String, guild: Guild): Seq[TGuildChannel] =
      tChannels(guild).filter(_.name == name)

    /**
      * Get all the voice channels with a name in this category.
      * @param name The name of the guilds.
      */
    def vChannelsByName[F[_]](name: String)(implicit snapshot: CacheSnapshot): Seq[VGuildChannel] =
      vChannels.filter(_.name == name)

    /**
      * Get all the voice channels with a name in this category using an preexisting guild.
      * @param name The name of the guilds.
      */
    def vChannelsByName(name: String, guild: Guild): Seq[VGuildChannel] =
      vChannels(guild).filter(_.name == name)

    /**
      * Update the settings of this category.
      * @param name New name of the category.
      * @param position New position of the category.
      * @param permissionOverwrites The new category permission overwrites.
      */
    def modify(
        name: JsonOption[String] = JsonUndefined,
        position: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[SnowflakeMap[UserOrRoleTag, PermissionOverwrite]] = JsonUndefined
    ) = ModifyChannel(
      category.id,
      ModifyChannelData(
        name = name,
        position = position,
        nsfw = JsonUndefined,
        permissionOverwrites = permissionOverwrites.map(_.values.toSeq),
        parentId = JsonUndefined
      )
    )
  }

  implicit class GuildSyntax(private val guild: Guild) extends AnyVal {

    /**
      * Modify this guild.
      * @param name The new name of the guild
      * @param region The new voice region for the guild
      * @param verificationLevel The new verification level to use for the guild.
      * @param defaultMessageNotifications The new notification level to use
      *                                    for the guild.
      * @param afkChannelId The new afk channel of the guild.
      * @param afkTimeout The new afk timeout in seconds for the guild.
      * @param icon The new icon to use for the guild. Must be 128x128 jpeg.
      * @param ownerId Transfer ownership of this guild. Must be the owner.
      * @param splash The new splash for the guild. Must be 128x128 jpeg. VIP only.
      */
    def modify(
        name: Option[String] = None,
        region: Option[String] = None,
        verificationLevel: Option[VerificationLevel] = None,
        defaultMessageNotifications: Option[NotificationLevel] = None,
        afkChannelId: Option[ChannelId] = None,
        afkTimeout: Option[Int] = None,
        icon: Option[ImageData] = None,
        ownerId: Option[UserId] = None,
        splash: Option[ImageData] = None
    ) = ModifyGuild(
      guild.id,
      ModifyGuildData(
        name = name,
        region = region,
        verificationLevel = verificationLevel,
        defaultMessageNotifications = defaultMessageNotifications,
        afkChannelId = afkChannelId,
        afkTimeout = afkTimeout,
        icon = icon,
        ownerId = ownerId,
        splash = splash
      )
    )

    /**
      * Fetch all channels in this guild.
      */
    def fetchAllChannels = GetGuildChannels(guild.id)

    /**
      * Create a text channel in this guild.
      * @param name The name of the channel.
      * @param topic The topic to give this channel.
      * @param rateLimitPerUser The user ratelimit to give this channel.
      * @param permissionOverwrites The permission overwrites for the channel.
      * @param category The category id for the channel.
      * @param nsfw If the channel is NSFW.
      */
    def createTextChannel(
        name: String,
        topic: JsonOption[String] = JsonUndefined,
        rateLimitPerUser: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
        category: JsonOption[ChannelId] = JsonUndefined,
        nsfw: JsonOption[Boolean] = JsonUndefined
    ) = CreateGuildChannel(
      guild.id,
      CreateGuildChannelData(
        name = name,
        `type` = JsonSome(ChannelType.GuildText),
        topic = topic,
        rateLimitPerUser = rateLimitPerUser,
        permissionOverwrites = permissionOverwrites,
        parentId = category,
        nsfw = nsfw
      )
    )

    /**
      * Create a voice channel in this guild.
      * @param name The name of the channel.
      * @param bitrate The bitrate for the channel if it's a voice channel.
      * @param userLimit The user limit for the channel if it's a voice channel.
      * @param permissionOverwrites The permission overwrites for the channel.
      * @param category The category id for the channel.
      * @param nsfw If the channel is NSFW.
      */
    def createVoiceChannel(
        name: String,
        bitrate: JsonOption[Int] = JsonUndefined,
        userLimit: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
        category: JsonOption[ChannelId] = JsonUndefined,
        nsfw: JsonOption[Boolean] = JsonUndefined
    ) = CreateGuildChannel(
      guild.id,
      CreateGuildChannelData(
        name = name,
        `type` = JsonSome(ChannelType.GuildVoice),
        bitrate = bitrate,
        userLimit = userLimit,
        permissionOverwrites = permissionOverwrites,
        parentId = category,
        nsfw = nsfw
      )
    )

    /**
      * Create a category in this guild.
      * @param name The name of the channel.
      * @param permissionOverwrites The permission overwrites for the channel.
      * @param nsfw If the channel is NSFW.
      */
    def createCategory(
        name: String,
        permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
        nsfw: JsonOption[Boolean] = JsonUndefined
    ) = CreateGuildChannel(
      guild.id,
      CreateGuildChannelData(
        name = name,
        `type` = JsonSome(ChannelType.GuildCategory),
        permissionOverwrites = permissionOverwrites,
        nsfw = nsfw
      )
    )

    /**
      * Modify the positions of several channels.
      * @param newPositions A map between the channelId and the new positions.
      */
    def modifyChannelPositions(newPositions: SnowflakeMap[Channel, Int]) =
      ModifyGuildChannelPositions(
        guild.id,
        newPositions.map(t => ModifyGuildChannelPositionsData(t._1, t._2)).toSeq
      )

    /**
      * Fetch a guild member by id.
      */
    def fetchGuildMember(userId: UserId) =
      GetGuildMember(guild.id, userId)

    /**
      * Fetch a ban for a specific user.
      */
    def fetchBan(userId: UserId) = GetGuildBan(guild.id, userId)

    /**
      * Fetch all the bans for this guild.
      */
    def fetchBans = GetGuildBans(guild.id)

    /**
      * Unban a user.
      * @param userId The user to unban.
      */
    def unban(userId: UserId) = RemoveGuildBan(guild.id, userId)

    /**
      * Get all the guild members in this guild.
      * @param limit The max amount of members to get
      * @param after Get userIds after this id
      */
    def fetchAllGuildMember(
        limit: Option[Int] = None,
        after: Option[UserId] = None
    ) = ListGuildMembers(guild.id, ListGuildMembersData(limit, after))

    /**
      * Add a guild member to this guild. Requires the `guilds.join` OAuth2 scope.
      * @param accessToken The OAuth2 access token.
      * @param nick The nickname to give to the user.
      * @param roles The roles to give to the user.
      * @param mute If the user should be muted.
      * @param deaf If the user should be deafened.
      */
    def addGuildMember(
        userId: UserId,
        accessToken: String,
        nick: Option[String] = None,
        roles: Option[Seq[RoleId]] = None,
        mute: Option[Boolean] = None,
        deaf: Option[Boolean] = None
    ) = AddGuildMember(guild.id, userId, AddGuildMemberData(accessToken, nick, roles, mute, deaf))

    /**
      * Fetch all the roles in this guild.
      */
    def fetchRoles = GetGuildRoles(guild.id)

    /**
      * Create a new role.
      * @param name The name of the role.
      * @param permissions The permissions this role has.
      * @param color The color of the role.
      * @param hoist If this role is shown in the right sidebar.
      * @param mentionable If this role is mentionable.
      */
    def createRole(
        name: Option[String] = None,
        permissions: Option[Permission] = None,
        color: Option[Int] = None,
        hoist: Option[Boolean] = None,
        mentionable: Option[Boolean] = None
    ) = CreateGuildRole(guild.id, CreateGuildRoleData(name, permissions, color, hoist, mentionable))

    /**
      * Modify the positions of several roles
      * @param newPositions A map from the role id to their new position.
      */
    def modifyRolePositions(newPositions: SnowflakeMap[Role, Int]) =
      ModifyGuildRolePositions(guild.id, newPositions.map(t => ModifyGuildRolePositionsData(t._1, t._2)).toSeq)

    /**
      * Check how many members would be removed if a prune was started now.
      * @param days The number of days to prune for.
      */
    def fetchPruneCount(days: Int) =
      GetGuildPruneCount(guild.id, GuildPruneCountData(days))

    /**
      * Begin a prune.
      * @param days The number of days to prune for.
      */
    def beginPrune(
        days: Int,
        computePruneCount: Boolean = guild.memberCount < 1000
    ) = BeginGuildPrune(guild.id, BeginGuildPruneData(days, Some(computePruneCount)))

    /**
      * Fetch the voice regions for this guild.
      */
    def fetchVoiceRegions = GetGuildVoiceRegions(guild.id)

    /**
      * Fetch the invites for this guild.
      */
    def fetchInvites = GetGuildInvites(guild.id)

    /**
      * Fetch the integrations for this guild.
      */
    def fetchIntegrations = GetGuildIntegrations(guild.id)

    /**
      * Attach an integration to this guild.
      * @param tpe The integration type.
      * @param id The integration id.
      */
    def createIntegration(tpe: String, id: IntegrationId) =
      CreateGuildIntegration(guild.id, CreateGuildIntegrationData(tpe, id))

    /**
      * Modify an existing integration for this guild.
      * @param id The id of the integration
      * @param expireBehavior The behavior of expiring subscribers.
      * @param expireGracePeriod The grace period before expiring subscribers.
      * @param enableEmoticons If emojis should be synced for this integration.
      *                        (Twitch only)
      */
    def modifyIntegration(
        id: IntegrationId,
        expireBehavior: Int,
        expireGracePeriod: Int,
        enableEmoticons: Boolean
    ) =
      ModifyGuildIntegration(
        guild.id,
        id,
        ModifyGuildIntegrationData(expireBehavior, expireGracePeriod, enableEmoticons)
      )

    /**
      * Delete an integration.
      * @param id The integration id.
      */
    def removeIntegration(id: IntegrationId) =
      DeleteGuildIntegration(guild.id, id)

    /**
      * Sync an integration
      * @param id The integration id.
      */
    def syncIntegration(id: IntegrationId) =
      SyncGuildIntegration(guild.id, id)

    /**
      * Fetch the guild embed for this guild.
      */
    def fetchEmbed =
      GetGuildEmbed(guild.id)

    /**
      * Modify a guild embed for this guild.
      */
    def modifyEmbed(embed: GuildEmbed) =
      ModifyGuildEmbed(guild.id, embed)

    /**
      * Get all the text channels in the guild.
      */
    def tChannels: Seq[TGuildChannel] =
      guild.channels.values.collect {
        case tChannel: TGuildChannel => tChannel
      }.toSeq

    /**
      * Get all the voice channels in the guild.
      */
    def vChannels: Seq[VGuildChannel] =
      guild.channels.values.collect {
        case tChannel: VGuildChannel => tChannel
      }.toSeq

    /**
      * Get all the categories in this guild.
      * @return
      */
    def categories: Seq[GuildCategory] =
      guild.channels.values.collect {
        case category: GuildCategory => category
      }.toSeq

    /**
      * Get a channel by id in this guild.
      */
    def channelById(id: ChannelId): Option[GuildChannel] = guild.channels.get(id)

    /**
      * Get a text channel by id in this guild.
      */
    def tChannelById(id: ChannelId): Option[TGuildChannel] = channelById(id).flatMap(_.asTGuildChannel)

    /**
      * Get a voice channel by id in this guild.
      */
    def vChannelById(id: ChannelId): Option[VGuildChannel] = channelById(id).flatMap(_.asVGuildChannel)

    /**
      * Get a category by id in this guild.
      */
    def categoryById(id: ChannelId): Option[GuildCategory] = channelById(id).flatMap(_.asCategory)

    /**
      * Get all the channels with a name.
      */
    def channelsByName(name: String): Seq[GuildChannel] = guild.channels.values.filter(_.name == name).toSeq

    /**
      * Get all the text channels with a name.
      */
    def tChannelsByName(name: String): Seq[TGuildChannel] = tChannels.filter(_.name == name)

    /**
      * Get all the voice channels with a name.
      */
    def vChannelsByName(name: String): Seq[VGuildChannel] = vChannels.filter(_.name == name)

    /**
      * Get all the categories with a name.
      */
    def categoriesByName(name: String): Seq[GuildCategory] = categories.filter(_.name == name)

    /**
      * Get the afk channel in this guild.
      */
    def afkChannel: Option[VGuildChannel] = guild.afkChannelId.flatMap(vChannelById)

    /**
      * Get a role by id.
      */
    def roleById(id: RoleId): Option[Role] = guild.roles.get(id)

    /**
      * Get all the roles with a name.
      */
    def rolesByName(name: String): Seq[Role] = guild.roles.values.filter(_.name == name).toSeq

    /**
      * Get an emoji by id.
      */
    def emojiById(id: EmojiId): Option[Emoji] = guild.emojis.get(id)

    /**
      * Get all the emoji with a name.
      */
    def emojisByName(name: String): Seq[Emoji] = guild.emojis.values.filter(_.name == name).toSeq

    /**
      * Get a guild member by a user id.
      */
    def memberById(id: UserId): Option[GuildMember] = guild.members.get(id)

    /**
      * Get a guild member from a user.
      */
    def memberFromUser(user: User): Option[GuildMember] = memberById(user.id)

    /**
      * Get all the guild members with a role
      * @param roleId The role to check for.
      */
    def membersWithRole(roleId: RoleId): Seq[GuildMember] =
      guild.members.collect {
        case (_, mem) if mem.roleIds.contains(roleId) => mem
      }.toSeq

    /**
      * Get a presence by a user id.
      */
    def presenceById(id: UserId): Option[Presence] = guild.presences.get(id)

    /**
      * Get a presence for a user.
      */
    def presenceForUser(user: User): Option[Presence] = presenceById(user.id)

    /**
      * Fetch all the emojis for this guild.
      */
    def fetchEmojis = ListGuildEmojis(guild.id)

    /**
      * Fetch a single emoji from this guild.
      * @param emojiId The id of the emoji to fetch.
      */
    def fetchSingleEmoji(emojiId: EmojiId) =
      GetGuildEmoji(emojiId, guild.id)

    /**
      * Create a new emoji in this guild.
      * @param name The name of the emoji.
      * @param image The image for the emoji.
      */
    def createEmoji(name: String, image: ImageData, roles: Seq[RoleId]) =
      CreateGuildEmoji(guild.id, CreateGuildEmojiData(name, image, roles))

    /**
      * Get a voice state for a user.
      */
    def voiceStateFor(userId: UserId): Option[VoiceState] = guild.voiceStates.get(userId)

    /**
      * Modify the clients nickname.
      * @param nick The new nickname
      */
    def setNick(nick: String) =
      ModifyBotUsersNick(guild.id, ModifyBotUsersNickData(nick))

    /**
      * Fetch an audit log for a this guild.
      */
    def fetchAuditLog(
        userId: Option[UserId] = None,
        actionType: Option[AuditLogEvent] = None,
        before: Option[RawSnowflake] = None,
        limit: Option[Int] = None
    ) = GetGuildAuditLog(guild.id, GetGuildAuditLogData(userId, actionType, before, limit))

    /**
      * Fetch the webhooks in this guild.
      */
    def fetchWebhooks = GetGuildWebhooks(guild.id)

    /**
      * Leave this guild.
      */
    def leaveGuild = LeaveGuild(guild.id)

    /**
      * Delete this guild. Must be the owner.
      */
    def delete = DeleteGuild(guild.id)
  }

  implicit class GuildMemberSyntax(private val guildMember: GuildMember) extends AnyVal {

    /**
      * Get all the roles for this guild member.
      */
    def rolesForUser(implicit snapshot: CacheSnapshot): Seq[Role] =
      guildMember.guild.map(g => guildMember.roleIds.flatMap(g.roles.get)).getOrElse(Seq.empty)

    /**
      * Get all the roles for this guild member given a preexisting guild.
      */
    def rolesForUser(guild: Guild): Seq[Role] =
      guildMember.roleIds.flatMap(guild.roles.get)

    /**
      * Modify this guild member.
      * @param nick The nickname to give to the user.
      * @param roles The roles to give to the user.
      * @param mute If the user should be muted.
      * @param deaf If the user should be deafened.
      * @param channelId The id of the channel to move the user to.
      */
    def modify(
        nick: JsonOption[String] = JsonUndefined,
        roles: JsonOption[Seq[RoleId]] = JsonUndefined,
        mute: JsonOption[Boolean] = JsonUndefined,
        deaf: JsonOption[Boolean] = JsonUndefined,
        channelId: JsonOption[ChannelId] = JsonUndefined
    ) =
      ModifyGuildMember(
        guildMember.guildId,
        guildMember.userId,
        ModifyGuildMemberData(nick, roles, mute, deaf, channelId)
      )

    /**
      * Add a role to this member.
      * @param roleId The role to add
      */
    def addRole(roleId: RoleId) =
      AddGuildMemberRole(guildMember.guildId, guildMember.userId, roleId)

    /**
      * Remove a role from this member.
      * @param roleId The role to remove
      */
    def removeRole(roleId: RoleId) =
      RemoveGuildMemberRole(guildMember.guildId, guildMember.userId, roleId)

    /**
      * Kick this guild member.
      */
    def kick = RemoveGuildMember(guildMember.guildId, guildMember.userId)

    /**
      * Ban this guild member.
      * @param deleteMessageDays The number of days to delete messages for
      *                              this banned user.
      */
    def ban(deleteMessageDays: Option[Int], reason: Option[String]) =
      CreateGuildBan(guildMember.guildId, guildMember.userId, CreateGuildBanData(deleteMessageDays, reason))

    /**
      * Unban this user.
      */
    def unban = RemoveGuildBan(guildMember.guildId, guildMember.userId)
  }

  implicit class EmojiSyntax(private val emoji: Emoji) extends AnyVal {

    /**
      * Modify this emoji.
      * @param name The new name of the emoji.
      * @param guildId The guildId of this emoji.
      */
    def modify(name: String, roles: Seq[RoleId], guildId: GuildId) =
      ModifyGuildEmoji(emoji.id, guildId, ModifyGuildEmojiData(name, roles))

    /**
      * Delete this emoji.
      * @param guildId The guildId of this emoji.
      */
    def delete(guildId: GuildId) = DeleteGuildEmoji(emoji.id, guildId)
  }

  implicit class RoleSyntax(private val role: Role) extends AnyVal {

    /**
      * Modify this role.
      * @param name The new name of the role.
      * @param permissions The new permissions this role has.
      * @param color The new color of the role.
      * @param hoist If this role is shown in the right sidebar.
      * @param mentionable If this role is mentionable.
      */
    def modify(
        name: Option[String] = None,
        permissions: Option[Permission] = None,
        color: Option[Int] = None,
        hoist: Option[Boolean] = None,
        mentionable: Option[Boolean] = None
    ) =
      ModifyGuildRole(role.guildId, role.id, ModifyGuildRoleData(name, permissions, color, hoist, mentionable))

    /**
      * Delete this role.
      */
    def delete = DeleteGuildRole(role.guildId, role.id)
  }

  //noinspection MutatorLikeMethodIsParameterless
  implicit class MessageSyntax(private val message: Message) extends AnyVal {

    /**
      * Create a reaction for a message.
      * @param guildEmoji The emoji to react with.
      */
    def createReaction(guildEmoji: Emoji) =
      CreateReaction(message.channelId, message.id, guildEmoji.asString)

    /**
      * Delete the clients reaction to a message.
      * @param guildEmoji The emoji to remove a reaction for.
      */
    def deleteOwnReaction(guildEmoji: Emoji) =
      DeleteOwnReaction(message.channelId, message.id, guildEmoji.asString)

    /**
      * Delete the reaction of a user with an emoji.
      * @param guildEmoji The emoji of the reaction to remove.
      * @param userId The userId to remove for.
      */
    def deleteUserReaction(guildEmoji: Emoji, userId: UserId) =
      DeleteUserReaction(message.channelId, message.id, guildEmoji.asString, userId)

    /**
      * Fetch all the users that have reacted with an emoji for this message.
      * @param guildEmoji The emoji the get the reactors for.
      */
    def fetchReactions(
        guildEmoji: Emoji,
        before: Option[UserId] = None,
        after: Option[UserId] = None,
        limit: Option[Int] = None
    ) =
      GetReactions(message.channelId, message.id, guildEmoji.asString, GetReactionsData(before, after, limit))

    /**
      * Clear all the reactions on this message.
      */
    def deleteAllReactions =
      DeleteAllReactions(message.channelId, message.id)

    /**
      * Edit this message.
      * @param content The new content of this message
      * @param embed The new embed of this message
      */
    def edit(
        content: JsonOption[String] = JsonUndefined,
        embed: JsonOption[OutgoingEmbed] = JsonUndefined
    ) = EditMessage(message.channelId, message.id, EditMessageData(content, embed))

    /**
      * Delete this message.
      */
    def delete = DeleteMessage(message.channelId, message.id)

    /**
      * Pin this message.
      */
    def pin = AddPinnedChannelMessages(message.channelId, message.id)

    /**
      * Unpin this message.
      */
    def unpin =
      DeletePinnedChannelMessages(message.channelId, message.id)
  }

  implicit class UserSyntax(private val user: User) extends AnyVal {

    /**
      * Get an existing DM channel for this user.
      */
    def getDMChannel(implicit snapshot: CacheSnapshot): Option[DMChannel] =
      snapshot.getUserDmChannel(user.id)

    /**
      * Create a new dm channel for this user.
      */
    def createDMChannel = CreateDm(CreateDMData(user.id))
  }

  implicit class InviteSyntax(private val invite: Invite) extends AnyVal {

    /**
      * Delete this invite.
      */
    def delete = DeleteInvite(invite.code)
  }

  //noinspection MutatorLikeMethodIsParameterless
  implicit class WebhookSyntax(private val webhook: Webhook) extends AnyVal {

    /**
      * Modify this webhook.
      * @param name Name of the webhook.
      * @param avatar The avatar data of the webhook.
      * @param channelId The channel this webhook should be moved to.
      */
    def modify(
        name: Option[String] = None,
        avatar: Option[ImageData] = None,
        channelId: Option[ChannelId] = None
    ) = ModifyWebhook(webhook.id, ModifyWebhookData(name, avatar, channelId))

    /**
      * Modify this webhook with a token. Doesn't require authentication.
      * @param name Name of the webhook.
      * @param avatar The avatar data of the webhook.
      * @param channelId The channel this webhook should be moved to.
      */
    def modifyWithToken(
        name: Option[String] = None,
        avatar: Option[ImageData] = None,
        channelId: Option[ChannelId] = None
    ) = webhook.token.map(ModifyWebhookWithToken(webhook.id, _, ModifyWebhookData(name, avatar, channelId)))

    /**
      * Delete this webhook.
      */
    def delete = DeleteWebhook(webhook.id)

    /**
      * Delete this webhook with a token. Doesn't require authentication.
      */
    def deleteWithToken = webhook.token.map(DeleteWebhookWithToken(webhook.id, _))
  }

  implicit class AckCordSyntax(private val ackCord: AckCord.type) extends AnyVal {
    //Global methods which are not tied to a specific object

    /**
      * Fetch a channel by id.
      */
    def fetchChannel(channelId: ChannelId) = GetChannel(channelId)

    /**
      * Fetch a guild by id.
      */
    def fetchGuild(guildId: GuildId) = GetGuild(guildId)

    /**
      * Fetch a user by id.
      */
    def fetchUser(userId: UserId) = GetUser(userId)

    /**
      * Create a new guild. Bots can only have 10 guilds by default.
      * @param name The name of the guild
      * @param region The voice region for the guild
      * @param icon The icon to use for the guild. Must be 128x128 jpeg.
      * @param verificationLevel The verification level to use for the guild.
      * @param defaultMessageNotifications The notification level to use for
      *                                    the guild.
      * @param roles The roles for the new guild. Note, here the snowflake is
      *              just a placeholder.
      * @param channels The channels for the new guild.
      */
    def createGuild(
        name: String,
        region: Option[String] = None,
        icon: Option[ImageData] = None,
        verificationLevel: Option[VerificationLevel] = None,
        defaultMessageNotifications: Option[NotificationLevel] = None,
        explicitContentFilter: Option[FilterLevel] = None,
        roles: Option[Seq[Role]] = None,
        channels: Option[Seq[CreateGuildChannelData]] = None,
        afkChannelId: Option[ChannelId] = None,
        afkTimeout: Option[Int] = None,
        systemChannelId: Option[ChannelId] = None
    ) =
      CreateGuild(
        CreateGuildData(
          name,
          region,
          icon,
          verificationLevel,
          defaultMessageNotifications,
          explicitContentFilter,
          roles,
          channels,
          afkChannelId,
          afkTimeout,
          systemChannelId
        )
      )

    /**
      * Fetch the client user.
      */
    def fetchClientUser: GetCurrentUser.type = GetCurrentUser

    /**
      * Get the guilds of the client user.
      * @param before Get guilds before this id.
      * @param after Get guilds after this id.
      * @param limit The max amount of guilds to return.
      */
    def fetchCurrentUserGuilds(
        before: Option[GuildId] = None,
        after: Option[GuildId] = None,
        limit: Option[Int] = None
    ) = GetCurrentUserGuilds(GetCurrentUserGuildsData(before, after, limit))

    /**
      * Create a group DM to a few users.
      * @param accessTokens The access tokens of users that have granted the bot
      *                     the `gdm.join` scope.
      * @param nicks A map specifying the nicknames for the users in this group DM.
      */
    @deprecated("Deprecated by Discord", since = "0.13")
    def createGroupDM(
        accessTokens: Seq[String],
        nicks: SnowflakeMap[User, String]
    ) = CreateGroupDm(CreateGroupDMData(accessTokens, nicks))

    /**
      * Fetch an invite by code.
      * @param inviteCode The invite code.
      * @param withCounts If the returned invite object should return approximate
      *                   counts for members and people online.
      */
    def fetchInvite(inviteCode: String, withCounts: Boolean = false) =
      GetInvite(inviteCode, withCounts)

    /**
      * Fetch a list of voice regions that can be used when creating a guild.
      */
    def fetchVoiceRegions: ListVoiceRegions.type = ListVoiceRegions

    /**
      * Fetch a webhook by id.
      */
    def fetchWebhook(id: SnowflakeType[Webhook]) = GetWebhook(id)

    /**
      * Fetch a webhook by id with token. Doesn't require authentication.
      */
    def fetchWebhookWithToken(id: SnowflakeType[Webhook], token: String) =
      GetWebhookWithToken(id, token)
  }
}
