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
import java.time.OffsetDateTime

import ackcord.data._
import ackcord.requests._
import ackcord.util.{JsonOption, JsonSome, JsonUndefined}

package object syntax {

  implicit class ChannelSyntax(private val channel: Channel) extends AnyVal {

    /** Delete or close this channel. */
    def delete = DeleteCloseChannel(channel.id)

    /** If this is a text channel, convert it to one. */
    def asTextChannel: Option[TextChannel] = channel match {
      case gChannel: TextChannel => Some(gChannel)
      case _                     => None
    }

    /** If this is a DM channel, convert it to one. */
    def asDMChannel: Option[DMChannel] = channel match {
      case gChannel: DMChannel => Some(gChannel)
      case _                   => None
    }

    /** If this is a group DM channel, convert it to one. */
    def asGroupDMChannel: Option[GroupDMChannel] = channel match {
      case gChannel: GroupDMChannel => Some(gChannel)
      case _                        => None
    }

    /** If this is a guild channel, convert it to one. */
    def asGuildChannel: Option[GuildChannel] = channel match {
      case gChannel: GuildChannel => Some(gChannel)
      case _                      => None
    }

    /** If this is a text guild channel, convert it to one. */
    def asTextGuildChannel: Option[TextGuildChannel] = channel match {
      case gChannel: TextGuildChannel => Some(gChannel)
      case _                          => None
    }

    /** If this is a voice channel, convert it to one. */
    def asVoiceGuildChannel: Option[VoiceGuildChannel] = channel match {
      case gChannel: VoiceGuildChannel => Some(gChannel)
      case _                           => None
    }

    /** If this is a normal voice channel, convert it to one. */
    def asNormalVoiceGuildChannel: Option[NormalVoiceGuildChannel] =
      channel match {
        case gChannel: NormalVoiceGuildChannel => Some(gChannel)
        case _                                 => None
      }

    /** If this is a stage channel, convert it to one. */
    def asStageGuildChannel: Option[StageGuildChannel] = channel match {
      case gChannel: StageGuildChannel => Some(gChannel)
      case _                           => None
    }

    /** If this is a category, convert it to one. */
    def asCategory: Option[GuildCategory] = channel match {
      case cat: GuildCategory => Some(cat)
      case _                  => None
    }
  }

  implicit class TextChannelSyntax(private val textChannel: TextChannel)
      extends AnyVal {

    /**
      * Send a message to this channel.
      * @param content
      *   The content of the message.
      * @param tts
      *   If this is a text-to-speech message.
      * @param files
      *   The files to send with this message. You can reference these files in
      *   the embed using `attachment://filename`.
      * @param embed
      *   An embed to send with this message.
      */
    def sendMessage(
        content: String = "",
        tts: Boolean = false,
        files: Seq[Path] = Seq.empty,
        embeds: Seq[OutgoingEmbed] = Seq.empty,
        allowedMentions: AllowedMention = AllowedMention.all,
        replyTo: Option[MessageId] = None,
        replyFailIfNotExist: Boolean = true,
        components: Seq[ActionRow] = Nil
    ) = CreateMessage(
      textChannel.id,
      CreateMessageData(
        content,
        None,
        tts,
        files.map(CreateMessageFile.FromPath),
        embeds,
        allowedMentions,
        replyTo,
        replyFailIfNotExist,
        components
      )
    )

    /**
      * Fetch messages around a message id.
      * @param around
      *   The message to get messages around.
      * @param limit
      *   The max amount of messages to return.
      */
    def fetchMessagesAround(around: MessageId, limit: Option[Int] = None) =
      GetChannelMessages(
        textChannel.id,
        GetChannelMessagesData(Some(around), None, None, limit)
      )

    /**
      * Fetch messages before a message id.
      * @param before
      *   The message to get messages before.
      * @param limit
      *   The max amount of messages to return.
      */
    def fetchMessagesBefore(before: MessageId, limit: Option[Int] = None) =
      GetChannelMessages(
        textChannel.id,
        GetChannelMessagesData(None, Some(before), None, limit)
      )

    /**
      * Fetch messages after a message id.
      * @param after
      *   The message to get messages after.
      * @param limit
      *   The max amount of messages to return.
      */
    def fetchMessagesAfter(after: MessageId, limit: Option[Int] = None) =
      GetChannelMessages(
        textChannel.id,
        GetChannelMessagesData(None, None, Some(after), limit)
      )

    /**
      * Fetch messages in this channel.
      * @param limit
      *   The max amount of messages to return.
      */
    def fetchMessages(limit: Option[Int] = None) =
      GetChannelMessages(
        textChannel.id,
        GetChannelMessagesData(None, None, None, limit)
      )

    /** Fetch a message in this channel. */
    def fetchMessage(id: MessageId) = GetChannelMessage(textChannel.id, id)

    /** Triggers typing in a channel. */
    def triggerTyping = TriggerTypingIndicator(textChannel.id)
  }

  implicit class GuildChannelSyntax(private val channel: GuildChannel)
      extends AnyVal {

    /** Get the category of this channel using a preexisting guild. */
    def categoryFromGuild(guild: GatewayGuild): Option[GuildCategory] =
      for {
        catId <- channel.parentId
        cat <- guild.channels.collectFirst {
          case (_, ch: GuildCategory) if ch.id == catId => ch
        }
      } yield cat

    /**
      * Edit the permission overrides of a role
      * @param roleId
      *   The role to edit the permissions for.
      * @param allow
      *   The new allowed permissions.
      * @param deny
      *   The new denied permissions.
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
      * @param userId
      *   The user to edit the permissions for.
      * @param allow
      *   The new allowed permissions.
      * @param deny
      *   The new denied permissions.
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
      * @param userId
      *   The user to remove the permission overwrites for
      */
    def deleteChannelPermissionsUser(userId: UserId) =
      DeleteChannelPermission(channel.id, userId)

    /**
      * Delete the permission overwrites for a role
      * @param roleId
      *   The role to remove the permission overwrites for
      */
    def deleteChannelPermissionsRole(roleId: RoleId) =
      DeleteChannelPermission(channel.id, roleId)
  }

  implicit class TextGuildChannelSyntax(private val channel: TextGuildChannel)
      extends AnyVal {

    /**
      * Update the settings of this channel.
      * @param name
      *   New name of the channel.
      * @param position
      *   New position of the channel.
      * @param topic
      *   The new channel topic for text channels.
      * @param nsfw
      *   If the channel is NSFW for text channels.
      * @param rateLimitPerUser
      *   The new user ratelimit for guild text channels.
      * @param permissionOverwrites
      *   The new channel permission overwrites.
      * @param category
      *   The new category id of the channel.
      */
    def modify(
        name: JsonOption[String] = JsonUndefined,
        tpe: JsonOption[ChannelType] = JsonUndefined,
        position: JsonOption[Int] = JsonUndefined,
        topic: JsonOption[String] = JsonUndefined,
        nsfw: JsonOption[Boolean] = JsonUndefined,
        rateLimitPerUser: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[
          SnowflakeMap[UserOrRole, PermissionOverwrite]
        ] = JsonUndefined,
        category: JsonOption[SnowflakeType[GuildCategory]] = JsonUndefined
    ) = ModifyChannel(
      channel.id,
      ModifyChannelData(
        name = name,
        tpe = tpe,
        position = position,
        topic = topic,
        nsfw = nsfw,
        rateLimitPerUser = rateLimitPerUser,
        bitrate = JsonUndefined,
        userLimit = JsonUndefined,
        permissionOverwrites = permissionOverwrites.map(_.values.toSeq),
        parentId = category,
        rtcRegion = JsonUndefined
      )
    )

    /** Fetch all the invites created for this channel. */
    def fetchInvites = GetChannelInvites(channel.id)

    /**
      * Create an invite for this channel.
      * @param maxAge
      *   Duration in seconds before this invite expires.
      * @param maxUses
      *   Amount of times this invite can be used before expiring, or 0 for
      *   unlimited.
      * @param temporary
      *   If this invite only grants temporary membership.
      * @param unique
      *   If true, guarantees to create a new invite.
      */
    def createInvite(
        maxAge: Int = 86400,
        maxUses: Int = 0,
        temporary: Boolean = false,
        unique: Boolean = false
    ) =
      CreateChannelInvite.mk(
        channel.id,
        maxAge,
        maxUses,
        temporary,
        unique
      )

    /**
      * Delete multiple messages at the same time.
      * @param ids
      *   The messages to delete.
      */
    def bulkDelete(ids: Seq[MessageId]) =
      BulkDeleteMessages(channel.id, BulkDeleteMessagesData(ids))

    /** Fetch all the pinned messages in this channel. */
    def fetchPinnedMessages = GetPinnedMessages(channel.id)

    /**
      * Create a webhook for this channel.
      * @param name
      *   The webhook name.
      * @param avatar
      *   The webhook avatar.
      */
    def createWebhook(name: String, avatar: Option[ImageData]) =
      CreateWebhook(channel.id, CreateWebhookData(name, avatar))

    /** Fetch the webhooks for this channel. */
    def fetchWebhooks = GetChannelWebhooks(channel.id)

    /** Start a new thread in this channel without a message. */
    def startThread(
        name: String,
        autoArchiveDuration: Int = 1440,
        tpe: ChannelType.ThreadChannelType = ChannelType.GuildPrivateThread
    ) =
      StartThreadWithoutMessage(
        channel.id,
        StartThreadWithoutMessageData(name, autoArchiveDuration, tpe)
      )

    /**
      * Lists all the active threads in this channel. Threads are ordered in
      * descending order by their id.
      */
    def listActiveThreads = ListActiveThreads(channel.id)

    /**
      * Lists all the public archived threads in this channel. Threads are
      * ordered in descending order by [[RawThreadMetadata.archiveTimestamp]].
      */
    def listPublicArchivedThreads(
        before: Option[OffsetDateTime] = None,
        limit: Option[Int] = None
    ) =
      ListPublicArchivedThreads(channel.id, before, limit)

    /**
      * Lists all the private archived threads in this channel. Threads are
      * ordered in descending order by [[RawThreadMetadata.archiveTimestamp]].
      */
    def listPrivateArchivedThreads(
        before: Option[OffsetDateTime] = None,
        limit: Option[Int] = None
    ) =
      ListPublicArchivedThreads(channel.id, before, limit)

    /**
      * Lists all the joined private archived threads in this channel. Threads
      * are ordered in descending order by
      * [[RawThreadMetadata.archiveTimestamp]].
      */
    def listJoinedPrivateArchivedThreads(
        before: Option[OffsetDateTime] = None,
        limit: Option[Int] = None
    ) =
      ListPublicArchivedThreads(channel.id, before, limit)
  }

  implicit class VGuildChannelSyntax(
      private val channel: NormalVoiceGuildChannel
  ) extends AnyVal {

    /**
      * Update the settings of this channel.
      * @param name
      *   New name of the channel.
      * @param position
      *   New position of the channel.
      * @param bitrate
      *   The new channel bitrate for voice channels.
      * @param userLimit
      *   The new user limit for voice channel.
      * @param permissionOverwrites
      *   The new channel permission overwrites.
      * @param category
      *   The new category id of the channel.
      */
    def modify(
        name: JsonOption[String] = JsonUndefined,
        position: JsonOption[Int] = JsonUndefined,
        bitrate: JsonOption[Int] = JsonUndefined,
        userLimit: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[
          SnowflakeMap[UserOrRole, PermissionOverwrite]
        ] = JsonUndefined,
        category: JsonOption[SnowflakeType[GuildCategory]] = JsonUndefined,
        rtcRegion: JsonOption[String] = JsonUndefined
    ) = ModifyChannel(
      channel.id,
      ModifyChannelData(
        name = name,
        tpe = JsonUndefined,
        position = position,
        topic = JsonUndefined,
        nsfw = JsonUndefined,
        rateLimitPerUser = JsonUndefined,
        bitrate = bitrate,
        userLimit = userLimit,
        permissionOverwrites = permissionOverwrites.map(_.values.toSeq),
        parentId = category,
        rtcRegion = rtcRegion
      )
    )

    /** Get the users connected to this voice channel. */
    def connectedUsers(implicit c: CacheSnapshot): Seq[User] =
      c.getGuild(channel.guildId)
        .fold(Nil: Seq[User])(g => connectedUsers(g).toList.flatMap(_.resolve))

    /**
      * Get the users connected to this voice channel using an preexisting
      * guild.
      */
    def connectedUsers(guild: GatewayGuild): Seq[UserId] =
      guild.voiceStates.filter(_._2.channelId.contains(channel.id)).keys.toSeq

    /** Get the guild members connected to this voice channel. */
    def connectedMembers(implicit c: CacheSnapshot): Seq[GuildMember] =
      c.getGuild(channel.guildId)
        .fold(Nil: Seq[GuildMember])(g =>
          connectedUsers(g).flatMap(g.memberById(_))
        )

    /**
      * Get the guild members connected to this voice channel using an
      * preexisting guild.
      */
    def connectedMembers(guild: GatewayGuild): Seq[GuildMember] =
      connectedUsers(guild).flatMap(guild.memberById(_))
  }

  implicit class CategorySyntax(private val category: GuildCategory)
      extends AnyVal {

    /** Get all the channels in this category. */
    def channels(implicit snapshot: CacheSnapshot): Seq[GuildChannel] =
      category.guild
        .map { g =>
          g.channels.collect {
            case (_, ch) if ch.parentId.contains(category.id) => ch
          }.toSeq
        }
        .getOrElse(Seq.empty)

    /** Get all the channels in this category using an preexisting guild. */
    def channels(guild: GatewayGuild): Seq[GuildChannel] =
      guild.channels.collect {
        case (_, ch) if ch.parentId.contains(category.id) => ch
      }.toSeq

    /** Get all the text channels in this category. */
    def textChannels(implicit snapshot: CacheSnapshot): Seq[TextGuildChannel] =
      channels.collect { case channel: TextGuildChannel => channel }

    /**
      * Get all the text channels in this category using an preexisting guild.
      */
    def textChannels(guild: GatewayGuild): Seq[TextGuildChannel] =
      channels(guild).collect { case channel: TextGuildChannel => channel }

    /** Get all the voice channels in this category. */
    def voiceChannels(implicit
        snapshot: CacheSnapshot
    ): Seq[VoiceGuildChannel] =
      channels.collect { case channel: VoiceGuildChannel => channel }

    /**
      * Get all the voice channels in this category using an preexisting guild.
      */
    def voiceChannels(guild: GatewayGuild): Seq[VoiceGuildChannel] =
      channels(guild).collect { case channel: VoiceGuildChannel => channel }

    /** Get all the normal voice channels in this category. */
    def normalVoiceChannels(implicit
        snapshot: CacheSnapshot
    ): Seq[NormalVoiceGuildChannel] =
      channels.collect { case channel: NormalVoiceGuildChannel => channel }

    /**
      * Get all the normal voice channels in this category using an preexisting
      * guild.
      */
    def normalVoiceChannels(guild: GatewayGuild): Seq[NormalVoiceGuildChannel] =
      channels(guild).collect { case channel: NormalVoiceGuildChannel =>
        channel
      }

    /** Get all the stage channels in this category. */
    def stageChannels(implicit
        snapshot: CacheSnapshot
    ): Seq[StageGuildChannel] =
      channels.collect { case channel: StageGuildChannel => channel }

    /**
      * Get all the stage channels in this category using an preexisting guild.
      */
    def stageChannels(guild: GatewayGuild): Seq[StageGuildChannel] =
      channels(guild).collect { case channel: StageGuildChannel => channel }

    /**
      * Get a channel by id in this category.
      * @param id
      *   The id of the channel.
      */
    def channelById(id: GuildChannelId)(implicit
        snapshot: CacheSnapshot
    ): Option[GuildChannel] =
      channels.find(_.id == id)

    /**
      * Get a channel by id in this category using an preexisting guild.
      * @param id
      *   The id of the channel.
      */
    def channelById(
        id: GuildChannelId,
        guild: GatewayGuild
    ): Option[GuildChannel] = channels(guild).find(_.id == id)

    /**
      * Get a text channel by id in this category.
      * @param id
      *   The id of the channel.
      */
    def textChannelById(
        id: TextGuildChannelId
    )(implicit snapshot: CacheSnapshot): Option[TextGuildChannel] =
      channelById(id).collect { case channel: TextGuildChannel => channel }

    /**
      * Get a text channel by id in this category using an preexisting guild.
      * @param id
      *   The id of the channel.
      */
    def textChannelById(
        id: TextGuildChannelId,
        guild: GatewayGuild
    ): Option[TextGuildChannel] =
      channelById(id, guild).collect { case channel: TextGuildChannel =>
        channel
      }

    /**
      * Get a voice channel by id in this category.
      * @param id
      *   The id of the channel.
      */
    def voiceChannelById[F[_]](
        id: VoiceGuildChannelId
    )(implicit snapshot: CacheSnapshot): Option[VoiceGuildChannel] =
      channelById(id).collect { case channel: VoiceGuildChannel => channel }

    /**
      * Get a voice channel by id in this category using an preexisting guild.
      * @param id
      *   The id of the channel.
      */
    def voiceChannelById(
        id: VoiceGuildChannelId,
        guild: GatewayGuild
    ): Option[VoiceGuildChannel] =
      channelById(id, guild).collect { case channel: VoiceGuildChannel =>
        channel
      }

    /**
      * Get a normal voice channel by id in this category.
      * @param id
      *   The id of the channel.
      */
    def normalVoiceChannelById[F[_]](
        id: NormalVoiceGuildChannelId
    )(implicit snapshot: CacheSnapshot): Option[NormalVoiceGuildChannel] =
      channelById(id).collect { case channel: NormalVoiceGuildChannel =>
        channel
      }

    /**
      * Get a normal voice channel by id in this category using an preexisting
      * guild.
      * @param id
      *   The id of the channel.
      */
    def normalVoiceChannelById(
        id: NormalVoiceGuildChannelId,
        guild: GatewayGuild
    ): Option[NormalVoiceGuildChannel] =
      channelById(id, guild).collect { case channel: NormalVoiceGuildChannel =>
        channel
      }

    /**
      * Get a voice channel by id in this category.
      * @param id
      *   The id of the channel.
      */
    def stageChannelById[F[_]](
        id: StageGuildChannelId
    )(implicit snapshot: CacheSnapshot): Option[StageGuildChannel] =
      channelById(id).collect { case channel: StageGuildChannel => channel }

    /**
      * Get a voice channel by id in this category using an preexisting guild.
      * @param id
      *   The id of the channel.
      */
    def stageChannelById(
        id: StageGuildChannelId,
        guild: GatewayGuild
    ): Option[StageGuildChannel] =
      channelById(id, guild).collect { case channel: StageGuildChannel =>
        channel
      }

    /**
      * Get all the channels with a name in this category.
      * @param name
      *   The name of the guilds.
      */
    def channelsByName(name: String)(implicit
        snapshot: CacheSnapshot
    ): Seq[GuildChannel] =
      channels.filter(_.name == name)

    /**
      * Get all the channels with a name in this category using an preexisting
      * guild.
      * @param name
      *   The name of the guilds.
      */
    def channelsByName(name: String, guild: GatewayGuild): Seq[GuildChannel] =
      channels(guild).filter(_.name == name)

    /**
      * Get all the text channels with a name in this category.
      * @param name
      *   The name of the guilds.
      */
    def textChannelsByName(name: String)(implicit
        snapshot: CacheSnapshot
    ): Seq[TextGuildChannel] =
      textChannels.filter(_.name == name)

    /**
      * Get all the text channels with a name in this category using an
      * preexisting guild.
      * @param name
      *   The name of the guilds.
      */
    def textChannelsByName(
        name: String,
        guild: GatewayGuild
    ): Seq[TextGuildChannel] =
      textChannels(guild).filter(_.name == name)

    /**
      * Get all the voice channels with a name in this category.
      * @param name
      *   The name of the guilds.
      */
    def voiceChannelsByName[F[_]](name: String)(implicit
        snapshot: CacheSnapshot
    ): Seq[VoiceGuildChannel] =
      voiceChannels.filter(_.name == name)

    /**
      * Get all the voice channels with a name in this category using an
      * preexisting guild.
      * @param name
      *   The name of the guilds.
      */
    def voiceChannelsByName(
        name: String,
        guild: GatewayGuild
    ): Seq[VoiceGuildChannel] =
      voiceChannels(guild).filter(_.name == name)

    /**
      * Get all the normal voice channels with a name in this category.
      * @param name
      *   The name of the guilds.
      */
    def normalVoiceChannelsByName[F[_]](name: String)(implicit
        snapshot: CacheSnapshot
    ): Seq[NormalVoiceGuildChannel] =
      normalVoiceChannels.filter(_.name == name)

    /**
      * Get all the normal voice channels with a name in this category using an
      * preexisting guild.
      * @param name
      *   The name of the guilds.
      */
    def normalVoiceChannelsByName(
        name: String,
        guild: GatewayGuild
    ): Seq[NormalVoiceGuildChannel] =
      normalVoiceChannels(guild).filter(_.name == name)

    /**
      * Get all the voice channels with a name in this category.
      * @param name
      *   The name of the guilds.
      */
    def stageChannelsByName[F[_]](name: String)(implicit
        snapshot: CacheSnapshot
    ): Seq[StageGuildChannel] =
      stageChannels.filter(_.name == name)

    /**
      * Get all the voice channels with a name in this category using an
      * preexisting guild.
      * @param name
      *   The name of the guilds.
      */
    def stageChannelsByName(
        name: String,
        guild: GatewayGuild
    ): Seq[StageGuildChannel] =
      stageChannels(guild).filter(_.name == name)

    /**
      * Update the settings of this category.
      * @param name
      *   New name of the category.
      * @param position
      *   New position of the category.
      * @param permissionOverwrites
      *   The new category permission overwrites.
      */
    def modify(
        name: JsonOption[String] = JsonUndefined,
        position: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[
          SnowflakeMap[UserOrRole, PermissionOverwrite]
        ] = JsonUndefined
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

  implicit class ThreadGuildChannelSyntax(
      private val thread: ThreadGuildChannel
  ) extends AnyVal {

    /** Adds the current user to this thread. */
    def join = JoinThread(thread.id)

    /** Adds the specified user to this thread. */
    def addMember(userId: UserId) = AddThreadMember(thread.id, userId)

    /** Makes the current user leave this thread. */
    def leave = LeaveThread(thread.id)

    /** Removes the specified user from this thread. */
    def removeMember(userId: UserId) = RemoveThreadMember(thread.id, userId)

    /**
      * Gets all the members of this thread. Requires the privileged
      * `GUILD_MEMBERS` intent.
      */
    def listMember = ListThreadMembers(thread.id)
  }

  implicit class GuildSyntax(private val guild: Guild) extends AnyVal {

    /**
      * Modify this guild.
      *
      * @param name
      *   The new name of the guild
      * @param region
      *   The new voice region for the guild
      * @param verificationLevel
      *   The new verification level to use for the guild.
      * @param defaultMessageNotifications
      *   The new notification level to use for the guild.
      * @param afkChannelId
      *   The new afk channel of the guild.
      * @param afkTimeout
      *   The new afk timeout in seconds for the guild.
      * @param icon
      *   The new icon to use for the guild. Must be 1024x1024 png/jpeg/gif. Can
      *   be animated if the guild has the `ANIMATED_ICON` feature.
      * @param ownerId
      *   Transfer ownership of this guild. Must be the owner.
      * @param splash
      *   The new splash for the guild. Must be 16:9 png/jpeg. Only available if
      *   the guild has the `INVITE_SPLASH` feature.
      * @param discoverySplash
      *   Thew new discovery slash for the guild's discovery splash. Only
      *   available if the guild has the `DISCOVERABLE` feature.
      * @param banner
      *   The new banner for the guild. Must be 16:9 png/jpeg. Only available if
      *   the guild has the `BANNER` feature.
      * @param systemChannelId
      *   The new channel which system messages will be sent to.
      * @param systemChannelFlags
      *   The new flags for the system channel.
      * @param preferredLocale
      *   The new preferred locale for the guild.
      * @param features
      *   The new enabled features for the guild.
      * @param description
      *   The new description for the guild if it is discoverable.
      */
    def modify(
        name: JsonOption[String] = JsonUndefined,
        verificationLevel: JsonOption[VerificationLevel] = JsonUndefined,
        defaultMessageNotifications: JsonOption[NotificationLevel] =
          JsonUndefined,
        explicitContentFilter: JsonOption[FilterLevel] = JsonUndefined,
        afkChannelId: JsonOption[NormalVoiceGuildChannelId] = JsonUndefined,
        afkTimeout: JsonOption[Int] = JsonUndefined,
        icon: JsonOption[ImageData] = JsonUndefined,
        ownerId: JsonOption[UserId] = JsonUndefined,
        splash: JsonOption[ImageData] = JsonUndefined,
        discoverySplash: JsonOption[ImageData] = JsonUndefined,
        banner: JsonOption[ImageData] = JsonUndefined,
        systemChannelId: JsonOption[TextGuildChannelId] = JsonUndefined,
        systemChannelFlags: JsonOption[SystemChannelFlags] = JsonUndefined,
        preferredLocale: JsonOption[String] = JsonUndefined,
        features: JsonOption[Seq[String]] = JsonUndefined,
        description: JsonOption[String] = JsonUndefined
    ) = ModifyGuild(
      guild.id,
      ModifyGuildData(
        name = name,
        verificationLevel = verificationLevel,
        defaultMessageNotifications = defaultMessageNotifications,
        explicitContentFilter = explicitContentFilter,
        afkChannelId = afkChannelId,
        afkTimeout = afkTimeout,
        icon = icon,
        ownerId = ownerId,
        splash = splash,
        discoverySplash = discoverySplash,
        banner = banner,
        systemChannelId = systemChannelId,
        systemChannelFlags = systemChannelFlags,
        preferredLocale = preferredLocale,
        features = features,
        description = description
      )
    )

    /** Fetch all channels in this guild. */
    def fetchAllChannels = GetGuildChannels(guild.id)

    /**
      * Create a text channel in this guild.
      *
      * @param name
      *   The name of the channel.
      * @param topic
      *   The topic to give this channel.
      * @param rateLimitPerUser
      *   The user ratelimit to give this channel.
      * @param permissionOverwrites
      *   The permission overwrites for the channel.
      * @param category
      *   The category id for the channel.
      * @param nsfw
      *   If the channel is NSFW.
      */
    def createTextChannel(
        name: String,
        topic: JsonOption[String] = JsonUndefined,
        rateLimitPerUser: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] =
          JsonUndefined,
        category: JsonOption[SnowflakeType[GuildCategory]] = JsonUndefined,
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
      *
      * @param name
      *   The name of the channel.
      * @param bitrate
      *   The bitrate for the channel if it's a voice channel.
      * @param userLimit
      *   The user limit for the channel if it's a voice channel.
      * @param permissionOverwrites
      *   The permission overwrites for the channel.
      * @param category
      *   The category id for the channel.
      * @param nsfw
      *   If the channel is NSFW.
      */
    def createVoiceChannel(
        name: String,
        bitrate: JsonOption[Int] = JsonUndefined,
        userLimit: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] =
          JsonUndefined,
        category: JsonOption[SnowflakeType[GuildCategory]] = JsonUndefined,
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
      *
      * @param name
      *   The name of the channel.
      * @param permissionOverwrites
      *   The permission overwrites for the channel.
      * @param nsfw
      *   If the channel is NSFW.
      */
    def createCategory(
        name: String,
        permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] =
          JsonUndefined,
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
      *
      * @param newPositions
      *   A map between the channelId and the new positions.
      */
    def modifyChannelPositions(newPositions: SnowflakeMap[GuildChannel, Int]) =
      ModifyGuildChannelPositions(
        guild.id,
        newPositions.map(t => ModifyGuildChannelPositionsData(t._1, t._2)).toSeq
      )

    /**
      * Modify the positions of several channels.
      *
      * @param newPositions
      *   A sequence indicating the channels to move, and where
      */
    def modifyChannelPositions(
        newPositions: Seq[ModifyGuildChannelPositionsData]
    ) =
      ModifyGuildChannelPositions(
        guild.id,
        newPositions
      )

    /** Fetch a guild member by id. */
    def fetchGuildMember(userId: UserId) =
      GetGuildMember(guild.id, userId)

    /** Fetch a ban for a specific user. */
    def fetchBan(userId: UserId) = GetGuildBan(guild.id, userId)

    /** Fetch all the bans for this guild. */
    def fetchBans = GetGuildBans(guild.id)

    /**
      * Unban a user.
      *
      * @param userId
      *   The user to unban.
      */
    def unban(userId: UserId) = RemoveGuildBan(guild.id, userId)

    /**
      * Get all the guild members in this guild.
      *
      * @param limit
      *   The max amount of members to get.
      * @param after
      *   Get userIds after this id.
      */
    def fetchAllGuildMember(
        limit: Option[Int] = None,
        after: Option[UserId] = None
    ) = ListGuildMembers(guild.id, ListGuildMembersData(limit, after))

    /**
      * Search for guild members in the given guild, who's username or nickname
      * starts with the query string.
      *
      * @param query
      *   Query to search for.
      * @param limit
      *   How many members to return at most.
      */
    def fetchSearchGuildMembers(query: String, limit: Int = 1) =
      SearchGuildMembers(
        guild.id,
        SearchGuildMembersData(query, JsonSome(limit))
      )

    /**
      * Add a guild member to this guild. Requires the `guilds.join` OAuth2
      * scope.
      *
      * @param accessToken
      *   The OAuth2 access token.
      * @param nick
      *   The nickname to give to the user.
      * @param roles
      *   The roles to give to the user.
      * @param mute
      *   If the user should be muted.
      * @param deaf
      *   If the user should be deafened.
      */
    def addGuildMember(
        userId: UserId,
        accessToken: String,
        nick: Option[String] = None,
        roles: Option[Seq[RoleId]] = None,
        mute: Option[Boolean] = None,
        deaf: Option[Boolean] = None
    ) = AddGuildMember(
      guild.id,
      userId,
      AddGuildMemberData(accessToken, nick, roles, mute, deaf)
    )

    /** Fetch all the roles in this guild. */
    def fetchRoles = GetGuildRoles(guild.id)

    /**
      * Create a new role.
      *
      * @param name
      *   The name of the role.
      * @param permissions
      *   The permissions this role has.
      * @param color
      *   The color of the role.
      * @param hoist
      *   If this role is shown in the right sidebar.
      * @param mentionable
      *   If this role is mentionable.
      */
    def createRole(
        name: Option[String] = None,
        permissions: Option[Permission] = None,
        color: Option[Int] = None,
        hoist: Option[Boolean] = None,
        mentionable: Option[Boolean] = None
    ) = CreateGuildRole(
      guild.id,
      CreateGuildRoleData(name, permissions, color, hoist, mentionable)
    )

    /**
      * Modify the positions of several roles
      *
      * @param newPositions
      *   A map from the role id to their new position.
      */
    def modifyRolePositions(newPositions: SnowflakeMap[Role, Int]) =
      ModifyGuildRolePositions(
        guild.id,
        newPositions.map(t => ModifyGuildRolePositionsData(t._1, t._2)).toSeq
      )

    /**
      * Check how many members would be removed if a prune was started now.
      *
      * @param days
      *   The number of days to prune for.
      */
    def fetchPruneCount(days: Int, includeRoles: Seq[RoleId] = Nil) =
      GetGuildPruneCount(guild.id, GuildPruneCountData(days, includeRoles))

    /**
      * Begin a prune.
      *
      * @param days
      *   The number of days to prune for.
      */
    def beginPrune(
        days: Int,
        computePruneCount: Boolean = guild match {
          case gatewayGuild: GatewayGuild => gatewayGuild.memberCount < 1000
          case _                          => false
        },
        includeRoles: Seq[RoleId] = Nil
    ) = BeginGuildPrune(
      guild.id,
      BeginGuildPruneData(days, Some(computePruneCount), includeRoles)
    )

    /** Fetch the voice regions for this guild. */
    def fetchVoiceRegions = GetGuildVoiceRegions(guild.id)

    /** Fetch the invites for this guild. */
    def fetchInvites = GetGuildInvites(guild.id)

    /** Fetch the integrations for this guild. */
    def fetchIntegrations = GetGuildIntegrations(guild.id)

    /**
      * Delete an integration.
      *
      * @param id
      *   The integration id.
      */
    def removeIntegration(id: IntegrationId) =
      DeleteGuildIntegration(guild.id, id)

    /** Fetch the guild embed for this guild. */
    def fetchWidgetSettings =
      GetGuildWidgetSettings(guild.id)

    /** Modify a guild embed for this guild. */
    def modifyWidgetSettings(embed: GuildWidgetSettings) =
      ModifyGuildWidget(guild.id, embed)
  }

  implicit class GatewayGuildSyntax(private val guild: GatewayGuild)
      extends AnyVal {

    /** Get all the text channels in the guild. */
    def textChannels: Seq[TextGuildChannel] =
      guild.channels.values.collect { case tChannel: TextGuildChannel =>
        tChannel
      }.toSeq

    /** Get all the voice channels in the guild. */
    def voiceChannels: Seq[VoiceGuildChannel] =
      guild.channels.values.collect { case channel: VoiceGuildChannel =>
        channel
      }.toSeq

    /** Get all the normal voice channels in the guild. */
    def normalVoiceChannels: Seq[NormalVoiceGuildChannel] =
      guild.channels.values.collect { case channel: NormalVoiceGuildChannel =>
        channel
      }.toSeq

    /** Get all the stage channels in the guild. */
    def stageChannels: Seq[StageGuildChannel] =
      guild.channels.values.collect { case channel: StageGuildChannel =>
        channel
      }.toSeq

    /**
      * Get all the categories in this guild.
      * @return
      */
    def categories: Seq[GuildCategory] =
      guild.channels.values.collect { case category: GuildCategory =>
        category
      }.toSeq

    /** Get a channel by id in this guild. */
    def channelById(id: GuildChannelId): Option[GuildChannel] =
      guild.channels.get(id)

    /** Get a text channel by id in this guild. */
    def textChannelById(id: TextGuildChannelId): Option[TextGuildChannel] =
      channelById(id).flatMap(_.asTextGuildChannel)

    /** Get a voice channel by id in this guild. */
    def voiceChannelById(id: VoiceGuildChannelId): Option[VoiceGuildChannel] =
      channelById(id).flatMap(_.asVoiceGuildChannel)

    /** Get a voice channel by id in this guild. */
    def normalVoiceChannelById(
        id: VoiceGuildChannelId
    ): Option[NormalVoiceGuildChannel] =
      channelById(id).flatMap(_.asNormalVoiceGuildChannel)

    /** Get a stage channel by id in this guild. */
    def stageChannelById(id: StageGuildChannelId): Option[StageGuildChannel] =
      channelById(id).flatMap(_.asStageGuildChannel)

    /** Get a category by id in this guild. */
    def categoryById(id: SnowflakeType[GuildCategory]): Option[GuildCategory] =
      channelById(id).flatMap(_.asCategory)

    /** Get all the channels with a name. */
    def channelsByName(name: String): Seq[GuildChannel] =
      guild.channels.values.filter(_.name == name).toSeq

    /** Get all the text channels with a name. */
    def textChannelsByName(name: String): Seq[TextGuildChannel] =
      textChannels.filter(_.name == name)

    /** Get all the voice channels with a name. */
    def voiceChannelsByName(name: String): Seq[VoiceGuildChannel] =
      voiceChannels.filter(_.name == name)

    /** Get all the voice channels with a name. */
    def normalVoiceChannelsByName(name: String): Seq[NormalVoiceGuildChannel] =
      normalVoiceChannels.filter(_.name == name)

    /** Get all the stage channels with a name. */
    def stageChannelsByName(name: String): Seq[StageGuildChannel] =
      stageChannels.filter(_.name == name)

    /** Get all the categories with a name. */
    def categoriesByName(name: String): Seq[GuildCategory] =
      categories.filter(_.name == name)

    /** Get the afk channel in this guild. */
    def afkChannel: Option[NormalVoiceGuildChannel] =
      guild.afkChannelId.flatMap(normalVoiceChannelById)

    /** Get a role by id. */
    def roleById(id: RoleId): Option[Role] = guild.roles.get(id)

    /** Get all the roles with a name. */
    def rolesByName(name: String): Seq[Role] =
      guild.roles.values.filter(_.name == name).toSeq

    /** Get an emoji by id. */
    def emojiById(id: EmojiId): Option[Emoji] = guild.emojis.get(id)

    /** Get all the emoji with a name. */
    def emojisByName(name: String): Seq[Emoji] =
      guild.emojis.values.filter(_.name == name).toSeq

    /** Get a guild member by a user id. */
    def memberById(id: UserId): Option[GuildMember] = guild.members.get(id)

    /** Get a guild member from a user. */
    def memberFromUser(user: User): Option[GuildMember] = memberById(user.id)

    /**
      * Get all the guild members with a role
      * @param roleId
      *   The role to check for.
      */
    def membersWithRole(roleId: RoleId): Seq[GuildMember] =
      guild.members.collect {
        case (_, mem) if mem.roleIds.contains(roleId) => mem
      }.toSeq

    /** Get a presence by a user id. */
    def presenceById(id: UserId): Option[Presence] = guild.presences.get(id)

    /** Get a presence for a user. */
    def presenceForUser(user: User): Option[Presence] = presenceById(user.id)

    /** Fetch all the emojis for this guild. */
    def fetchEmojis = ListGuildEmojis(guild.id)

    /**
      * Fetch a single emoji from this guild.
      * @param emojiId
      *   The id of the emoji to fetch.
      */
    def fetchSingleEmoji(emojiId: EmojiId) =
      GetGuildEmoji(emojiId, guild.id)

    /**
      * Create a new emoji in this guild.
      * @param name
      *   The name of the emoji.
      * @param image
      *   The image for the emoji.
      */
    def createEmoji(name: String, image: ImageData, roles: Seq[RoleId]) =
      CreateGuildEmoji(guild.id, CreateGuildEmojiData(name, image, roles))

    /** Get a voice state for a user. */
    def voiceStateFor(userId: UserId): Option[VoiceState] =
      guild.voiceStates.get(userId)

    /**
      * Modify the clients nickname.
      * @param nick
      *   The new nickname
      */
    def setNick(nick: Option[String]) =
      ModifyBotUsersNick(
        guild.id,
        ModifyBotUsersNickData(JsonOption.fromOptionWithNull(nick))
      )

    /** Fetch an audit log for a this guild. */
    def fetchAuditLog(
        userId: Option[UserId] = None,
        actionType: Option[AuditLogEvent] = None,
        before: Option[RawSnowflake] = None,
        limit: Option[Int] = None
    ) = GetGuildAuditLog(
      guild.id,
      GetGuildAuditLogData(userId, actionType, before, limit)
    )

    /** Fetch the webhooks in this guild. */
    def fetchWebhooks = GetGuildWebhooks(guild.id)

    /** Leave this guild. */
    def leaveGuild = LeaveGuild(guild.id)

    /** Delete this guild. Must be the owner. */
    def delete = DeleteGuild(guild.id)

    def fetchWelcomeScreen = GetGuildWelcomeScreen(guild.id)

    def modifyWelcomeScreen(
        enabled: JsonOption[Boolean] = JsonUndefined,
        welcomeChannels: JsonOption[Seq[WelcomeScreenChannel]] = JsonUndefined,
        description: JsonOption[String] = JsonUndefined
    ) = ModifyGuildWelcomeScreen(
      guild.id,
      ModifyGuildWelcomeScreenData(enabled, welcomeChannels, description)
    )
  }

  implicit class GuildMemberSyntax(private val guildMember: GuildMember)
      extends AnyVal {

    /** Get all the roles for this guild member. */
    def rolesForUser(implicit snapshot: CacheSnapshot): Seq[Role] =
      guildMember.guild
        .map(g => guildMember.roleIds.flatMap(g.roles.get))
        .getOrElse(Seq.empty)

    /** Get all the roles for this guild member given a preexisting guild. */
    def rolesForUser(guild: Guild): Seq[Role] =
      guildMember.roleIds.flatMap(guild.roles.get)

    /**
      * Modify this guild member.
      * @param nick
      *   The nickname to give to the user.
      * @param roles
      *   The roles to give to the user.
      * @param mute
      *   If the user should be muted.
      * @param deaf
      *   If the user should be deafened.
      * @param channelId
      *   The id of the channel to move the user to.
      */
    def modify(
        nick: JsonOption[String] = JsonUndefined,
        roles: JsonOption[Seq[RoleId]] = JsonUndefined,
        mute: JsonOption[Boolean] = JsonUndefined,
        deaf: JsonOption[Boolean] = JsonUndefined,
        channelId: JsonOption[VoiceGuildChannelId] = JsonUndefined
    ) =
      ModifyGuildMember(
        guildMember.guildId,
        guildMember.userId,
        ModifyGuildMemberData(nick, roles, mute, deaf, channelId)
      )

    /**
      * Add a role to this member.
      * @param roleId
      *   The role to add
      */
    def addRole(roleId: RoleId) =
      AddGuildMemberRole(guildMember.guildId, guildMember.userId, roleId)

    /**
      * Remove a role from this member.
      * @param roleId
      *   The role to remove
      */
    def removeRole(roleId: RoleId) =
      RemoveGuildMemberRole(guildMember.guildId, guildMember.userId, roleId)

    /** Kick this guild member. */
    def kick = RemoveGuildMember(guildMember.guildId, guildMember.userId)

    /**
      * Ban this guild member.
      * @param deleteMessageDays
      *   The number of days to delete messages for this banned user.
      */
    def ban(deleteMessageDays: Option[Int], reason: Option[String]) =
      CreateGuildBan(
        guildMember.guildId,
        guildMember.userId,
        CreateGuildBanData(deleteMessageDays),
        reason
      )

    /** Unban this user. */
    def unban = RemoveGuildBan(guildMember.guildId, guildMember.userId)
  }

  implicit class EmojiSyntax(private val emoji: Emoji) extends AnyVal {

    /**
      * Modify this emoji.
      * @param name
      *   The new name of the emoji.
      * @param guildId
      *   The guildId of this emoji.
      */
    def modify(name: String, roles: Seq[RoleId], guildId: GuildId) =
      ModifyGuildEmoji(
        emoji.id,
        guildId,
        ModifyGuildEmojiData(name, JsonSome(roles))
      )

    /**
      * Delete this emoji.
      * @param guildId
      *   The guildId of this emoji.
      */
    def delete(guildId: GuildId) = DeleteGuildEmoji(emoji.id, guildId)
  }

  implicit class RoleSyntax(private val role: Role) extends AnyVal {

    /**
      * Modify this role.
      * @param name
      *   The new name of the role.
      * @param permissions
      *   The new permissions this role has.
      * @param color
      *   The new color of the role.
      * @param hoist
      *   If this role is shown in the right sidebar.
      * @param mentionable
      *   If this role is mentionable.
      */
    def modify(
        name: JsonOption[String] = JsonUndefined,
        permissions: JsonOption[Permission] = JsonUndefined,
        color: JsonOption[Int] = JsonUndefined,
        hoist: JsonOption[Boolean] = JsonUndefined,
        mentionable: JsonOption[Boolean] = JsonUndefined
    ) =
      ModifyGuildRole(
        role.guildId,
        role.id,
        ModifyGuildRoleData(name, permissions, color, hoist, mentionable)
      )

    /** Delete this role. */
    def delete = DeleteGuildRole(role.guildId, role.id)
  }

  //noinspection MutatorLikeMethodIsParameterless
  implicit class MessageSyntax(private val message: Message) extends AnyVal {

    /**
      * Create a reaction for a message.
      * @param guildEmoji
      *   The emoji to react with.
      */
    def createReaction(guildEmoji: Emoji) =
      CreateReaction(message.channelId, message.id, guildEmoji.asString)

    /**
      * Create a reaction for a message.
      * @param emoji
      *   The emoji to react with.
      */
    def createReaction(emoji: String) =
      CreateReaction(message.channelId, message.id, emoji)

    /**
      * Delete the clients reaction to a message.
      * @param guildEmoji
      *   The emoji to remove a reaction for.
      */
    def deleteOwnReaction(guildEmoji: Emoji) =
      DeleteOwnReaction(message.channelId, message.id, guildEmoji.asString)

    /**
      * Delete the clients reaction to a message.
      * @param emoji
      *   The emoji to remove a reaction for.
      */
    def deleteOwnReaction(emoji: String) =
      DeleteOwnReaction(message.channelId, message.id, emoji)

    /**
      * Delete the reaction of a user with an emoji.
      * @param guildEmoji
      *   The emoji of the reaction to remove.
      * @param userId
      *   The userId to remove for.
      */
    def deleteUserReaction(guildEmoji: Emoji, userId: UserId) =
      DeleteUserReaction(
        message.channelId,
        message.id,
        guildEmoji.asString,
        userId
      )

    /**
      * Delete the reaction of a user with an emoji.
      * @param emoji
      *   The emoji of the reaction to remove.
      * @param userId
      *   The userId to remove for.
      */
    def deleteUserReaction(emoji: String, userId: UserId) =
      DeleteUserReaction(message.channelId, message.id, emoji, userId)

    /**
      * Fetch all the users that have reacted with an emoji for this message.
      * @param guildEmoji
      *   The emoji the get the reactors for.
      */
    def fetchReactions(
        guildEmoji: Emoji,
        after: Option[UserId] = None,
        limit: Option[Int] = None
    ) = GetReactions(
      message.channelId,
      message.id,
      guildEmoji.asString,
      GetReactionsData(after, limit)
    )

    /**
      * Fetch all the users that have reacted with an emoji for this message.
      * @param emoji
      *   The emoji the get the reactors for.
      */
    def fetchReactionsStr(
        emoji: String,
        after: Option[UserId] = None,
        limit: Option[Int] = None
    ) = GetReactions(
      message.channelId,
      message.id,
      emoji,
      GetReactionsData(after, limit)
    )

    /** Clear all the reactions on this message. */
    def deleteAllReactions =
      DeleteAllReactions(message.channelId, message.id)

    /** Clear all the reactions on this message. */
    def deleteEmojiReactions(emoji: Emoji) =
      DeleteAllReactionsForEmoji(message.channelId, message.id, emoji.asString)

    /** Clear all the reactions on this message. */
    def deleteEmojiReactions(emoji: String) =
      DeleteAllReactionsForEmoji(message.channelId, message.id, emoji)

    /**
      * Edit this message.
      * @param content
      *   The new content of this message
      * @param embed
      *   The new embed of this message
      */
    def edit(
        content: JsonOption[String] = JsonUndefined,
        files: Seq[CreateMessageFile] = Seq.empty,
        allowedMentions: JsonOption[AllowedMention] = JsonUndefined,
        embeds: JsonOption[Seq[OutgoingEmbed]] = JsonUndefined,
        flags: JsonOption[MessageFlags] = JsonUndefined,
        components: JsonOption[Seq[ActionRow]] = JsonUndefined
    ) = EditMessage(
      message.channelId,
      message.id,
      EditMessageData(
        content,
        files,
        allowedMentions,
        embeds,
        flags,
        components
      )
    )

    /** Delete this message. */
    def delete = DeleteMessage(message.channelId, message.id)

    /** Pin this message. */
    def pin = PinMessage(message.channelId, message.id)

    /** Unpin this message. */
    def unpin =
      UnpinMessage(message.channelId, message.id)

    /** Start a new thread from this message. */
    def startThread(name: String, autoArchiveDuration: Int = 1440) =
      StartThreadWithMessage(
        message.channelId.asChannelId[TextGuildChannel],
        message.id,
        StartThreadWithMessageData(name, autoArchiveDuration)
      )
  }

  implicit class UserSyntax(private val user: User) extends AnyVal {

    /** Get an existing DM channel for this user. */
    def getDMChannel(implicit snapshot: CacheSnapshot): Option[DMChannel] =
      snapshot.getUserDmChannel(user.id)

    /** Create a new dm channel for this user. */
    def createDMChannel = CreateDm(CreateDMData(user.id))
  }

  implicit class InviteSyntax(private val invite: Invite) extends AnyVal {

    /** Delete this invite. */
    def delete = DeleteInvite(invite.code)
  }

  //noinspection MutatorLikeMethodIsParameterless
  implicit class WebhookSyntax(private val webhook: Webhook) extends AnyVal {

    /**
      * Modify this webhook.
      * @param name
      *   Name of the webhook.
      * @param avatar
      *   The avatar data of the webhook.
      * @param channelId
      *   The channel this webhook should be moved to.
      */
    def modify(
        name: JsonOption[String] = JsonUndefined,
        avatar: JsonOption[ImageData] = JsonUndefined,
        channelId: JsonOption[TextGuildChannelId] = JsonUndefined
    ) = ModifyWebhook(webhook.id, ModifyWebhookData(name, avatar, channelId))

    /**
      * Modify this webhook with a token. Doesn't require authentication.
      * @param name
      *   Name of the webhook.
      * @param avatar
      *   The avatar data of the webhook.
      * @param channelId
      *   The channel this webhook should be moved to.
      */
    def modifyWithToken(
        name: JsonOption[String] = JsonUndefined,
        avatar: JsonOption[ImageData] = JsonUndefined,
        channelId: JsonOption[TextGuildChannelId] = JsonUndefined
    ) = webhook.token.map(
      ModifyWebhookWithToken(
        webhook.id,
        _,
        ModifyWebhookData(name, avatar, channelId)
      )
    )

    /** Delete this webhook. */
    def delete = DeleteWebhook(webhook.id)

    /** Delete this webhook with a token. Doesn't require authentication. */
    def deleteWithToken =
      webhook.token.map(DeleteWebhookWithToken(webhook.id, _))
  }

  implicit class AckCordSyntax(private val ackCord: AckCord.type)
      extends AnyVal {
    //Global methods which are not tied to a specific object

    /** Fetch a channel by id. */
    def fetchChannel(channelId: ChannelId) = GetChannel(channelId)

    /** Fetch a guild by id. */
    def fetchGuild(guildId: GuildId) = GetGuild(guildId)

    /** Fetch a user by id. */
    def fetchUser(userId: UserId) = GetUser(userId)

    /**
      * Create a new guild. Bots can only have 10 guilds by default.
      * @param name
      *   The name of the guild
      * @param region
      *   The voice region for the guild
      * @param icon
      *   The icon to use for the guild. Must be 128x128 jpeg.
      * @param verificationLevel
      *   The verification level to use for the guild.
      * @param defaultMessageNotifications
      *   The notification level to use for the guild.
      * @param roles
      *   The roles for the new guild. Note, here the snowflake is just a
      *   placeholder.
      * @param channels
      *   The channels for the new guild.
      * @param systemChannelFlags
      *   The flags for the system channel.
      */
    def createGuild(
        name: String,
        icon: Option[ImageData] = None,
        verificationLevel: Option[VerificationLevel] = None,
        defaultMessageNotifications: Option[NotificationLevel] = None,
        explicitContentFilter: Option[FilterLevel] = None,
        roles: Option[Seq[Role]] = None,
        channels: Option[Seq[CreateGuildChannelData]] = None,
        afkChannelId: Option[NormalVoiceGuildChannelId] = None,
        afkTimeout: Option[Int] = None,
        systemChannelId: Option[TextGuildChannelId] = None,
        systemChannelFlags: Option[SystemChannelFlags] = None
    ) =
      CreateGuild(
        CreateGuildData(
          name,
          icon,
          verificationLevel,
          defaultMessageNotifications,
          explicitContentFilter,
          roles,
          channels,
          afkChannelId,
          afkTimeout,
          systemChannelId,
          systemChannelFlags
        )
      )

    /** Fetch the client user. */
    def fetchClientUser: GetCurrentUser.type = GetCurrentUser

    /**
      * Get the guilds of the client user.
      * @param before
      *   Get guilds before this id.
      * @param after
      *   Get guilds after this id.
      * @param limit
      *   The max amount of guilds to return.
      */
    def fetchCurrentUserGuilds(
        before: Option[GuildId] = None,
        after: Option[GuildId] = None,
        limit: Option[Int] = None
    ) = GetCurrentUserGuilds(GetCurrentUserGuildsData(before, after, limit))

    /**
      * Create a group DM to a few users.
      * @param accessTokens
      *   The access tokens of users that have granted the bot the `gdm.join`
      *   scope.
      * @param nicks
      *   A map specifying the nicknames for the users in this group DM.
      */
    @deprecated("Deprecated by Discord", since = "0.13")
    def createGroupDM(
        accessTokens: Seq[String],
        nicks: SnowflakeMap[User, String]
    ) = CreateGroupDm(CreateGroupDMData(accessTokens, nicks))

    /**
      * Fetch an invite by code.
      * @param inviteCode
      *   The invite code.
      * @param withCounts
      *   If the returned invite object should return approximate counts for
      *   members and people online.
      */
    def fetchInvite(inviteCode: String, withCounts: Boolean = false) =
      GetInvite(inviteCode, withCounts)

    /** Fetch a list of voice regions that can be used when creating a guild. */
    def fetchVoiceRegions: ListVoiceRegions.type = ListVoiceRegions

    /** Fetch a webhook by id. */
    def fetchWebhook(id: SnowflakeType[Webhook]) = GetWebhook(id)

    /** Fetch a webhook by id with token. Doesn't require authentication. */
    def fetchWebhookWithToken(id: SnowflakeType[Webhook], token: String) =
      GetWebhookWithToken(id, token)
  }
}
