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

import scala.language.higherKinds

import java.nio.file.Path

import ackcord.data._
import ackcord.requests._
import ackcord.util.{JsonOption, JsonSome, JsonUndefined}
import akka.NotUsed
import cats.data.OptionT
import cats.{Functor, Monad, Traverse}

package object syntax {

  implicit class ChannelSyntax(private val channel: Channel) extends AnyVal {

    /**
      * Delete or close this channel.
      */
    def delete[Ctx](context: Ctx = NotUsed: NotUsed) = DeleteCloseChannel(channel.id, context)

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
    def sendMessage[Ctx](
        content: String = "",
        tts: Boolean = false,
        files: Seq[Path] = Seq.empty,
        embed: Option[OutgoingEmbed] = None,
        context: Ctx = NotUsed: NotUsed
    ) = CreateMessage(tChannel.id, CreateMessageData(content, None, tts, files, embed), context)

    /**
      * Fetch messages around a message id.
      * @param around The message to get messages around.
      * @param limit The max amount of messages to return.
      */
    def fetchMessagesAround[Ctx](around: MessageId, limit: Option[Int] = None, context: Ctx = NotUsed: NotUsed) =
      GetChannelMessages(tChannel.id, GetChannelMessagesData(Some(around), None, None, limit), context)

    /**
      * Fetch messages before a message id.
      * @param before The message to get messages before.
      * @param limit The max amount of messages to return.
      */
    def fetchMessagesBefore[Ctx](before: MessageId, limit: Option[Int] = None, context: Ctx = NotUsed: NotUsed) =
      GetChannelMessages(tChannel.id, GetChannelMessagesData(None, Some(before), None, limit), context)

    /**
      * Fetch messages after a message id.
      * @param after The message to get messages after.
      * @param limit The max amount of messages to return.
      */
    def fetchMessagesAfter[Ctx](after: MessageId, limit: Option[Int] = None, context: Ctx = NotUsed: NotUsed) =
      GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, Some(after), limit), context)

    /**
      * Fetch messages in this channel.
      * @param limit The max amount of messages to return.
      */
    def fetchMessages[Ctx](limit: Option[Int] = None, context: Ctx = NotUsed: NotUsed) =
      GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, None, limit), context)

    /**
      * Fetch a message in this channel.
      */
    def fetchMessage[Ctx](id: MessageId, context: Ctx = NotUsed: NotUsed) = GetChannelMessage(tChannel.id, id, context)

    /**
      * Triggers typing in a channel.
      */
    def triggerTyping[Ctx](context: Ctx = NotUsed: NotUsed) = TriggerTypingIndicator(tChannel.id, context)
  }

  implicit class GuildChannelSyntax(private val channel: GuildChannel) extends AnyVal {

    /**
      * Get the category of this channel.
      */
    def category[F[_]](implicit snapshot: CacheSnapshot[F], F: Monad[F]): OptionT[F, GuildCategory] =
      for {
        guild <- channel.guild
        cat   <- OptionT.fromOption[F](category(guild))
      } yield cat

    /**
      * Get the category of this channel using a preexisting guild.
      */
    def category(guild: Guild): Option[GuildCategory] =
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
    def editChannelPermissionsRole[Ctx](
        roleId: RoleId,
        allow: Permission,
        deny: Permission,
        context: Ctx = NotUsed: NotUsed
    ) = EditChannelPermissions(
      channel.id,
      roleId,
      EditChannelPermissionsData(allow, deny, PermissionOverwriteType.Role),
      context
    )

    /**
      * Edit the permission overrides of a user
      * @param userId The user to edit the permissions for.
      * @param allow The new allowed permissions.
      * @param deny The new denied permissions.
      */
    def editChannelPermissionsUser[Ctx](
        userId: UserId,
        allow: Permission,
        deny: Permission,
        context: Ctx = NotUsed: NotUsed
    ) = EditChannelPermissions(
      channel.id,
      userId,
      EditChannelPermissionsData(allow, deny, PermissionOverwriteType.Member),
      context
    )

    /**
      * Delete the permission overwrites for a user
      * @param userId The user to remove the permission overwrites for
      */
    def deleteChannelPermissionsUser[Ctx](userId: UserId, context: Ctx = NotUsed: NotUsed) =
      DeleteChannelPermission(channel.id, userId, context)

    /**
      * Delete the permission overwrites for a role
      * @param roleId The role to remove the permission overwrites for
      */
    def deleteChannelPermissionsRole[Ctx](roleId: RoleId, context: Ctx = NotUsed: NotUsed) =
      DeleteChannelPermission(channel.id, roleId, context)
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
    def modify[Ctx](
        name: JsonOption[String] = JsonUndefined,
        position: JsonOption[Int] = JsonUndefined,
        topic: JsonOption[String] = JsonUndefined,
        nsfw: JsonOption[Boolean] = JsonUndefined,
        rateLimitPerUser: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[SnowflakeMap[UserOrRoleTag, PermissionOverwrite]] = JsonUndefined,
        category: JsonOption[ChannelId] = JsonUndefined,
        context: Ctx = NotUsed: NotUsed
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
      ),
      context
    )

    /**
      * Fetch all the invites created for this channel.
      */
    def fetchInvites[Ctx](context: Ctx = NotUsed: NotUsed) = GetChannelInvites(channel.id, context)

    /**
      * Create an invite for this channel.
      * @param maxAge Duration in seconds before this invite expires.
      * @param maxUses Amount of times this invite can be used before expiring,
      *                or 0 for unlimited.
      * @param temporary If this invite only grants temporary membership.
      * @param unique If true, guarantees to create a new invite.
      */
    def createInvite[Ctx](
        maxAge: Int = 86400,
        maxUses: Int = 0,
        temporary: Boolean = false,
        unique: Boolean = false,
        context: Ctx = NotUsed: NotUsed
    ) = CreateChannelInvite(channel.id, CreateChannelInviteData(maxAge, maxUses, temporary, unique), context)

    /**
      * Delete multiple messages at the same time.
      * @param ids The messages to delete.
      */
    def bulkDelete[Ctx](ids: Seq[MessageId], context: Ctx = NotUsed: NotUsed) =
      BulkDeleteMessages(channel.id, BulkDeleteMessagesData(ids), context)

    /**
      * Fetch all the pinned messages in this channel.
      */
    def fetchPinnedMessages[Ctx](context: Ctx = NotUsed: NotUsed) = GetPinnedMessages(channel.id, context)

    /**
      * Create a webhook for this channel.
      * @param name The webhook name.
      * @param avatar The webhook avatar.
      */
    def createWebhook[Ctx](name: String, avatar: Option[ImageData], context: Ctx = NotUsed: NotUsed) =
      CreateWebhook(channel.id, CreateWebhookData(name, avatar), context)

    /**
      * Fetch the webhooks for this channel.
      */
    def fetchWebhooks[Ctx](context: Ctx = NotUsed: NotUsed) = GetChannelWebhooks(channel.id, context)
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
    def modify[Ctx](
        name: JsonOption[String] = JsonUndefined,
        position: JsonOption[Int] = JsonUndefined,
        bitrate: JsonOption[Int] = JsonUndefined,
        userLimit: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[SnowflakeMap[UserOrRoleTag, PermissionOverwrite]] = JsonUndefined,
        category: JsonOption[ChannelId] = JsonUndefined,
        context: Ctx = NotUsed: NotUsed
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
      ),
      context
    )

    /**
      * Get the users connected to this voice channel.
      */
    def connectedUsers[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): F[Seq[User]] = {
      c.getGuild(channel.guildId)
        .semiflatMap { g =>
          import cats.instances.list._
          Traverse[List].traverse(connectedUsers(g).toList)(_.resolve[F].value)
        }
        .cata(Nil, _.flatten.toSeq)
    }

    /**
      * Get the users connected to this voice channel using an preexisting guild.
      */
    def connectedUsers(guild: Guild): Seq[UserId] =
      guild.voiceStates.filter(_._2.channelId.contains(channel.id)).keys.toSeq

    /**
      * Get the guild members connected to this voice channel.
      */
    def connectedMembers[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): F[Seq[GuildMember]] =
      c.getGuild(channel.guildId).cata(Nil, g => connectedUsers(g).flatMap(g.memberById(_)))

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
    def channels[F[_]](implicit snapshot: CacheSnapshot[F], F: Functor[F]): F[Seq[GuildChannel]] =
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
    def tChannels[F[_]](implicit snapshot: CacheSnapshot[F], F: Functor[F]): F[Seq[TGuildChannel]] =
      Functor[F].map(channels)(_.collect { case tChannel: TGuildChannel => tChannel })

    /**
      * Get all the text channels in this category using an preexisting guild.
      */
    def tChannels(guild: Guild): Seq[TGuildChannel] =
      channels(guild).collect { case tChannel: TGuildChannel => tChannel }

    /**
      * Get all the voice channels in this category.
      */
    def vChannels[F[_]](implicit snapshot: CacheSnapshot[F], F: Functor[F]): F[Seq[VGuildChannel]] =
      Functor[F].map(channels)(_.collect { case tChannel: VGuildChannel => tChannel })

    /**
      * Get all the voice channels in this category using an preexisting guild.
      */
    def vChannels(guild: Guild): Seq[VGuildChannel] =
      channels(guild).collect { case tChannel: VGuildChannel => tChannel }

    /**
      * Get a channel by id in this category.
      * @param id The id of the channel.
      */
    def channelById[F[_]](id: ChannelId)(implicit snapshot: CacheSnapshot[F], F: Functor[F]): OptionT[F, GuildChannel] =
      OptionT(Functor[F].map(channels)(_.find(_.id == id)))

    /**
      * Get a channel by id in this category using an preexisting guild.
      * @param id The id of the channel.
      */
    def channelById(id: ChannelId, guild: Guild): Option[GuildChannel] = channels(guild).find(_.id == id)

    /**
      * Get a text channel by id in this category.
      * @param id The id of the channel.
      */
    def tChannelById[F[_]](
        id: ChannelId
    )(implicit snapshot: CacheSnapshot[F], F: Functor[F]): OptionT[F, TGuildChannel] =
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
    )(implicit snapshot: CacheSnapshot[F], F: Functor[F]): OptionT[F, VGuildChannel] =
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
    def channelsByName[F[_]](name: String)(implicit snapshot: CacheSnapshot[F], F: Functor[F]): F[Seq[GuildChannel]] =
      Functor[F].map(channels)(_.filter(_.name == name))

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
    def tChannelsByName[F[_]](name: String)(implicit snapshot: CacheSnapshot[F], F: Functor[F]): F[Seq[TGuildChannel]] =
      Functor[F].map(tChannels)(_.filter(_.name == name))

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
    def vChannelsByName[F[_]](name: String)(implicit snapshot: CacheSnapshot[F], F: Functor[F]): F[Seq[VGuildChannel]] =
      Functor[F].map(vChannels)(_.filter(_.name == name))

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
    def modify[Ctx](
        name: JsonOption[String] = JsonUndefined,
        position: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[SnowflakeMap[UserOrRoleTag, PermissionOverwrite]] = JsonUndefined,
        context: Ctx = NotUsed: NotUsed
    ) = ModifyChannel(
      category.id,
      ModifyChannelData(
        name = name,
        position = position,
        nsfw = JsonUndefined,
        permissionOverwrites = permissionOverwrites.map(_.values.toSeq),
        parentId = JsonUndefined
      ),
      context
    )
  }

  implicit class GuildSyntax(private val guild: Guild) extends AnyVal {

    /**
      * Get the owner of this guild.
      */
    def owner[F[_]](implicit snapshot: CacheSnapshot[F]): OptionT[F, User] = snapshot.getUser(guild.ownerId)

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
    def modify[Ctx](
        name: Option[String] = None,
        region: Option[String] = None,
        verificationLevel: Option[VerificationLevel] = None,
        defaultMessageNotifications: Option[NotificationLevel] = None,
        afkChannelId: Option[ChannelId] = None,
        afkTimeout: Option[Int] = None,
        icon: Option[ImageData] = None,
        ownerId: Option[UserId] = None,
        splash: Option[ImageData] = None,
        context: Ctx = NotUsed: NotUsed
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
      ),
      context
    )

    /**
      * Fetch all channels in this guild.
      */
    def fetchAllChannels[Ctx](context: Ctx = NotUsed: NotUsed) = GetGuildChannels(guild.id, context)

    /**
      * Create a text channel in this guild.
      * @param name The name of the channel.
      * @param topic The topic to give this channel.
      * @param rateLimitPerUser The user ratelimit to give this channel.
      * @param permissionOverwrites The permission overwrites for the channel.
      * @param category The category id for the channel.
      * @param nsfw If the channel is NSFW.
      */
    def createTextChannel[Ctx](
        name: String,
        topic: JsonOption[String] = JsonUndefined,
        rateLimitPerUser: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
        category: JsonOption[ChannelId] = JsonUndefined,
        nsfw: JsonOption[Boolean] = JsonUndefined,
        context: Ctx = NotUsed: NotUsed
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
      ),
      context
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
    def createVoiceChannel[Ctx](
        name: String,
        bitrate: JsonOption[Int] = JsonUndefined,
        userLimit: JsonOption[Int] = JsonUndefined,
        permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
        category: JsonOption[ChannelId] = JsonUndefined,
        nsfw: JsonOption[Boolean] = JsonUndefined,
        context: Ctx = NotUsed: NotUsed
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
      ),
      context
    )

    /**
      * Create a category in this guild.
      * @param name The name of the channel.
      * @param permissionOverwrites The permission overwrites for the channel.
      * @param nsfw If the channel is NSFW.
      */
    def createCategory[Ctx](
        name: String,
        permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
        nsfw: JsonOption[Boolean] = JsonUndefined,
        context: Ctx = NotUsed: NotUsed
    ) = CreateGuildChannel(
      guild.id,
      CreateGuildChannelData(
        name = name,
        `type` = JsonSome(ChannelType.GuildCategory),
        permissionOverwrites = permissionOverwrites,
        nsfw = nsfw
      ),
      context
    )

    /**
      * Modify the positions of several channels.
      * @param newPositions A map between the channelId and the new positions.
      */
    def modifyChannelPositions[Ctx](newPositions: SnowflakeMap[Channel, Int], context: Ctx = NotUsed: NotUsed) =
      ModifyGuildChannelPositions(
        guild.id,
        newPositions.map(t => ModifyGuildChannelPositionsData(t._1, t._2)).toSeq,
        context
      )

    /**
      * Fetch a guild member by id.
      */
    def fetchGuildMember[Ctx](userId: UserId, context: Ctx = NotUsed: NotUsed) =
      GetGuildMember(guild.id, userId, context)

    /**
      * Fetch a ban for a specific user.
      */
    def fetchBan[Ctx](userId: UserId, context: Ctx = NotUsed: NotUsed) = GetGuildBan(guild.id, userId)

    /**
      * Fetch all the bans for this guild.
      */
    def fetchBans[Ctx](context: Ctx = NotUsed: NotUsed) = GetGuildBans(guild.id, context)

    /**
      * Unban a user.
      * @param userId The user to unban.
      */
    def unban[Ctx](userId: UserId, context: Ctx = NotUsed: NotUsed) = RemoveGuildBan(guild.id, userId, context)

    /**
      * Get all the guild members in this guild.
      * @param limit The max amount of members to get
      * @param after Get userIds after this id
      */
    def fetchAllGuildMember[Ctx](
        limit: Option[Int] = None,
        after: Option[UserId] = None,
        context: Ctx = NotUsed: NotUsed
    ) = ListGuildMembers(guild.id, ListGuildMembersData(limit, after), context)

    /**
      * Add a guild member to this guild. Requires the `guilds.join` OAuth2 scope.
      * @param accessToken The OAuth2 access token.
      * @param nick The nickname to give to the user.
      * @param roles The roles to give to the user.
      * @param mute If the user should be muted.
      * @param deaf If the user should be deafened.
      */
    def addGuildMember[Ctx](
        userId: UserId,
        accessToken: String,
        nick: Option[String] = None,
        roles: Option[Seq[RoleId]] = None,
        mute: Option[Boolean] = None,
        deaf: Option[Boolean] = None,
        context: Ctx = NotUsed: NotUsed
    ) = AddGuildMember(guild.id, userId, AddGuildMemberData(accessToken, nick, roles, mute, deaf), context)

    /**
      * Fetch all the roles in this guild.
      */
    def fetchRoles[Ctx](context: Ctx = NotUsed: NotUsed) = GetGuildRoles(guild.id, context)

    /**
      * Create a new role.
      * @param name The name of the role.
      * @param permissions The permissions this role has.
      * @param color The color of the role.
      * @param hoist If this role is shown in the right sidebar.
      * @param mentionable If this role is mentionable.
      */
    def createRole[Ctx](
        name: Option[String] = None,
        permissions: Option[Permission] = None,
        color: Option[Int] = None,
        hoist: Option[Boolean] = None,
        mentionable: Option[Boolean] = None,
        context: Ctx = NotUsed: NotUsed
    ) = CreateGuildRole(guild.id, CreateGuildRoleData(name, permissions, color, hoist, mentionable), context)

    /**
      * Modify the positions of several roles
      * @param newPositions A map from the role id to their new position.
      */
    def modifyRolePositions[Ctx](newPositions: SnowflakeMap[Role, Int], context: Ctx = NotUsed: NotUsed) =
      ModifyGuildRolePositions(guild.id, newPositions.map(t => ModifyGuildRolePositionsData(t._1, t._2)).toSeq, context)

    /**
      * Check how many members would be removed if a prune was started now.
      * @param days The number of days to prune for.
      */
    def fetchPruneCount[Ctx](days: Int, context: Ctx = NotUsed: NotUsed) =
      GetGuildPruneCount(guild.id, GuildPruneCountData(days), context)

    /**
      * Begin a prune.
      * @param days The number of days to prune for.
      */
    def beginPrune[Ctx](
        days: Int,
        computePruneCount: Boolean = guild.memberCount < 1000,
        context: Ctx = NotUsed: NotUsed
    ) = BeginGuildPrune(guild.id, BeginGuildPruneData(days, Some(computePruneCount)), context)

    /**
      * Fetch the voice regions for this guild.
      */
    def fetchVoiceRegions[Ctx](context: Ctx = NotUsed: NotUsed) = GetGuildVoiceRegions(guild.id, context)

    /**
      * Fetch the invites for this guild.
      */
    def fetchInvites[Ctx](context: Ctx = NotUsed: NotUsed) = GetGuildInvites(guild.id, context)

    /**
      * Fetch the integrations for this guild.
      */
    def fetchIntegrations[Ctx](context: Ctx = NotUsed: NotUsed) = GetGuildIntegrations(guild.id, context)

    /**
      * Attach an integration to this guild.
      * @param tpe The integration type.
      * @param id The integration id.
      */
    def createIntegration[Ctx](tpe: String, id: IntegrationId, context: Ctx = NotUsed: NotUsed) =
      CreateGuildIntegration(guild.id, CreateGuildIntegrationData(tpe, id), context)

    /**
      * Modify an existing integration for this guild.
      * @param id The id of the integration
      * @param expireBehavior The behavior of expiring subscribers.
      * @param expireGracePeriod The grace period before expiring subscribers.
      * @param enableEmoticons If emojis should be synced for this integration.
      *                        (Twitch only)
      */
    def modifyIntegration[Ctx](
        id: IntegrationId,
        expireBehavior: Int,
        expireGracePeriod: Int,
        enableEmoticons: Boolean,
        context: Ctx = NotUsed: NotUsed
    ) =
      ModifyGuildIntegration(
        guild.id,
        id,
        ModifyGuildIntegrationData(expireBehavior, expireGracePeriod, enableEmoticons),
        context
      )

    /**
      * Delete an integration.
      * @param id The integration id.
      */
    def removeIntegration[Ctx](id: IntegrationId, context: Ctx = NotUsed: NotUsed) =
      DeleteGuildIntegration(guild.id, id, context)

    /**
      * Sync an integration
      * @param id The integration id.
      */
    def syncIntegration[Ctx](id: IntegrationId, context: Ctx = NotUsed: NotUsed) =
      SyncGuildIntegration(guild.id, id, context)

    /**
      * Fetch the guild embed for this guild.
      */
    def fetchEmbed[Ctx](context: Ctx = NotUsed: NotUsed) =
      GetGuildEmbed(guild.id, context)

    /**
      * Modify a guild embed for this guild.
      */
    def modifyEmbed[Ctx](embed: GuildEmbed, context: Ctx = NotUsed: NotUsed) =
      ModifyGuildEmbed(guild.id, embed, context)

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
    def fetchEmojis[Ctx](context: Ctx = NotUsed: NotUsed) = ListGuildEmojis(guild.id, context)

    /**
      * Fetch a single emoji from this guild.
      * @param emojiId The id of the emoji to fetch.
      */
    def fetchSingleEmoji[Ctx](emojiId: EmojiId, context: Ctx = NotUsed: NotUsed) =
      GetGuildEmoji(emojiId, guild.id, context)

    /**
      * Create a new emoji in this guild.
      * @param name The name of the emoji.
      * @param image The image for the emoji.
      */
    def createEmoji[Ctx](name: String, image: ImageData, roles: Seq[RoleId], context: Ctx = NotUsed: NotUsed) =
      CreateGuildEmoji(guild.id, CreateGuildEmojiData(name, image, roles), context)

    /**
      * Get a voice state for a user.
      */
    def voiceStateFor(userId: UserId): Option[VoiceState] = guild.voiceStates.get(userId)

    /**
      * Modify the clients nickname.
      * @param nick The new nickname
      */
    def setNick[Ctx](nick: String, context: Ctx = NotUsed: NotUsed) =
      ModifyBotUsersNick(guild.id, ModifyBotUsersNickData(nick), context)

    /**
      * Fetch an audit log for a this guild.
      */
    def fetchAuditLog[Ctx](context: Ctx = NotUsed: NotUsed) = GetGuildAuditLog(guild.id, context)

    /**
      * Fetch the webhooks in this guild.
      */
    def fetchWebhooks[Ctx](context: Ctx = NotUsed: NotUsed) = GetGuildWebhooks(guild.id, context)

    /**
      * Leave this guild.
      */
    def leaveGuild[Ctx](context: Ctx = NotUsed: NotUsed) = LeaveGuild(guild.id, context)

    /**
      * Delete this guild. Must be the owner.
      */
    def delete[Ctx](context: Ctx = NotUsed: NotUsed) = DeleteGuild(guild.id, context)
  }

  implicit class GuildMemberSyntax(private val guildMember: GuildMember) extends AnyVal {

    /**
      * Get all the roles for this guild member.
      */
    def rolesForUser[F[_]](implicit snapshot: CacheSnapshot[F], F: Functor[F]): F[Seq[Role]] =
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
    def modify[Ctx](
        nick: JsonOption[String] = JsonUndefined,
        roles: JsonOption[Seq[RoleId]] = JsonUndefined,
        mute: JsonOption[Boolean] = JsonUndefined,
        deaf: JsonOption[Boolean] = JsonUndefined,
        channelId: JsonOption[ChannelId] = JsonUndefined,
        context: Ctx = NotUsed: NotUsed
    ) =
      ModifyGuildMember(
        guildMember.guildId,
        guildMember.userId,
        ModifyGuildMemberData(nick, roles, mute, deaf, channelId),
        context
      )

    /**
      * Add a role to this member.
      * @param roleId The role to add
      */
    def addRole[Ctx](roleId: RoleId, context: Ctx = NotUsed: NotUsed) =
      AddGuildMemberRole(guildMember.guildId, guildMember.userId, roleId, context)

    /**
      * Remove a role from this member.
      * @param roleId The role to remove
      */
    def removeRole[Ctx](roleId: RoleId, context: Ctx = NotUsed: NotUsed) =
      RemoveGuildMemberRole(guildMember.guildId, guildMember.userId, roleId, context)

    /**
      * Kick this guild member.
      */
    def kick[Ctx](context: Ctx = NotUsed: NotUsed) = RemoveGuildMember(guildMember.guildId, guildMember.userId, context)

    /**
      * Ban this guild member.
      * @param deleteMessageDays The number of days to delete messages for
      *                              this banned user.
      */
    def ban[Ctx](deleteMessageDays: Int, reason: String, context: Ctx = NotUsed: NotUsed) =
      CreateGuildBan(guildMember.guildId, guildMember.userId, CreateGuildBanData(deleteMessageDays, reason), context)

    /**
      * Unban this user.
      */
    def unban[Ctx](context: Ctx = NotUsed: NotUsed) = RemoveGuildBan(guildMember.guildId, guildMember.userId, context)
  }

  implicit class EmojiSyntax(private val emoji: Emoji) extends AnyVal {

    /**
      * Modify this emoji.
      * @param name The new name of the emoji.
      * @param guildId The guildId of this emoji.
      */
    def modify[Ctx](name: String, roles: Seq[RoleId], guildId: GuildId, context: Ctx = NotUsed: NotUsed) =
      ModifyGuildEmoji(emoji.id, guildId, ModifyGuildEmojiData(name, roles), context)

    /**
      * Delete this emoji.
      * @param guildId The guildId of this emoji.
      */
    def delete[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed) = DeleteGuildEmoji(emoji.id, guildId, context)
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
    def modify[Ctx](
        name: Option[String] = None,
        permissions: Option[Permission] = None,
        color: Option[Int] = None,
        hoist: Option[Boolean] = None,
        mentionable: Option[Boolean] = None,
        context: Ctx = NotUsed: NotUsed
    ) =
      ModifyGuildRole(role.guildId, role.id, ModifyGuildRoleData(name, permissions, color, hoist, mentionable), context)

    /**
      * Delete this role.
      */
    def delete[Ctx](context: Ctx = NotUsed: NotUsed) = DeleteGuildRole(role.guildId, role.id, context)
  }

  implicit class MessageSyntax(private val message: Message) extends AnyVal {

    /**
      * Create a reaction for a message.
      * @param guildEmoji The emoji to react with.
      */
    def createReaction[Ctx](guildEmoji: Emoji, context: Ctx = NotUsed: NotUsed) =
      CreateReaction(message.channelId, message.id, guildEmoji.asString, context)

    /**
      * Delete the clients reaction to a message.
      * @param guildEmoji The emoji to remove a reaction for.
      */
    def deleteOwnReaction[Ctx](guildEmoji: Emoji, context: Ctx = NotUsed: NotUsed) =
      DeleteOwnReaction(message.channelId, message.id, guildEmoji.asString, context)

    /**
      * Delete the reaction of a user with an emoji.
      * @param guildEmoji The emoji of the reaction to remove.
      * @param userId The userId to remove for.
      */
    def deleteUserReaction[Ctx](guildEmoji: Emoji, userId: UserId, context: Ctx = NotUsed: NotUsed) =
      DeleteUserReaction(message.channelId, message.id, guildEmoji.asString, userId, context)

    /**
      * Fetch all the users that have reacted with an emoji for this message.
      * @param guildEmoji The emoji the get the reactors for.
      */
    def fetchReactions[Ctx](
        guildEmoji: Emoji,
        before: Option[UserId] = None,
        after: Option[UserId] = None,
        limit: Option[Int] = None,
        context: Ctx = NotUsed: NotUsed
    ) =
      GetReactions(message.channelId, message.id, guildEmoji.asString, GetReactionsData(before, after, limit), context)

    /**
      * Clear all the reactions on this message.
      */
    def deleteAllReactions[Ctx](context: Ctx = NotUsed: NotUsed) =
      DeleteAllReactions(message.channelId, message.id, context)

    /**
      * Edit this message.
      * @param content The new content of this message
      * @param embed The new embed of this message
      */
    def edit[Ctx](
        content: JsonOption[String] = JsonUndefined,
        embed: JsonOption[OutgoingEmbed] = JsonUndefined,
        context: Ctx = NotUsed: NotUsed
    ) = EditMessage(message.channelId, message.id, EditMessageData(content, embed), context)

    /**
      * Delete this message.
      */
    def delete[Ctx](context: Ctx = NotUsed: NotUsed) = DeleteMessage(message.channelId, message.id, context)

    /**
      * Pin this message.
      */
    def pin[Ctx](context: Ctx = NotUsed: NotUsed) = AddPinnedChannelMessages(message.channelId, message.id, context)

    /**
      * Unpin this message.
      */
    def unpin[Ctx](context: Ctx = NotUsed: NotUsed) =
      DeletePinnedChannelMessages(message.channelId, message.id, context)
  }

  implicit class UserSyntax(private val user: User) extends AnyVal {

    /**
      * Get an existing DM channel for this user.
      */
    def getDMChannel[F[_]](implicit snapshot: CacheSnapshot[F]): OptionT[F, DMChannel] =
      snapshot.getUserDmChannel(user.id)

    /**
      * Create a new dm channel for this user.
      */
    def createDMChannel[Ctx](context: Ctx = NotUsed: NotUsed) = CreateDm(CreateDMData(user.id), context)
  }

  implicit class InviteSyntax(private val invite: Invite) extends AnyVal {

    /**
      * Delete this invite.
      */
    def delete[Ctx](context: Ctx = NotUsed: NotUsed) = DeleteInvite(invite.code, context)
  }

  implicit class WebhookSyntax(private val webhook: Webhook) extends AnyVal {

    /**
      * Modify this webhook.
      * @param name Name of the webhook.
      * @param avatar The avatar data of the webhook.
      * @param channelId The channel this webhook should be moved to.
      */
    def modify[Ctx](
        name: Option[String] = None,
        avatar: Option[ImageData] = None,
        channelId: Option[ChannelId] = None,
        context: Ctx = NotUsed: NotUsed
    ) = ModifyWebhook(webhook.id, ModifyWebhookData(name, avatar, channelId), context)

    /**
      * Modify this webhook with a token. Doesn't require authentication.
      * @param name Name of the webhook.
      * @param avatar The avatar data of the webhook.
      * @param channelId The channel this webhook should be moved to.
      */
    def modifyWithToken[Ctx](
        name: Option[String] = None,
        avatar: Option[ImageData] = None,
        channelId: Option[ChannelId] = None,
        context: Ctx = NotUsed: NotUsed
    ) = ModifyWebhookWithToken(webhook.id, webhook.token, ModifyWebhookData(name, avatar, channelId), context)

    /**
      * Delete this webhook.
      */
    def delete[Ctx](context: Ctx = NotUsed: NotUsed) = DeleteWebhook(webhook.id, context)

    /**
      * Delete this webhook with a token. Doesn't require authentication.
      */
    def deleteWithToken[Ctx](context: Ctx = NotUsed: NotUsed) =
      DeleteWebhookWithToken(webhook.id, webhook.token, context)
  }

  implicit class AckCordSyntax(private val ackCord: AckCord.type) extends AnyVal {
    //Global methods which are not tied to a specific object

    /**
      * Fetch a channel by id.
      */
    def fetchChannel[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed) = GetChannel(channelId, context)

    /**
      * Fetch a guild by id.
      */
    def fetchGuild[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed) = GetGuild(guildId, context)

    /**
      * Fetch a user by id.
      */
    def fetchUser[Ctx](userId: UserId, context: Ctx = NotUsed: NotUsed) = GetUser(userId, context)

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
    def createGuild[Ctx](
        name: String,
        region: String,
        icon: Option[ImageData],
        verificationLevel: VerificationLevel,
        defaultMessageNotifications: NotificationLevel,
        explicitContentFilter: FilterLevel,
        roles: Seq[Role],
        channels: Seq[CreateGuildChannelData],
        context: Ctx = NotUsed: NotUsed
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
          channels
        ),
        context
      )

    /**
      * Fetch the client user.
      */
    def fetchClientUser[Ctx](context: Ctx = NotUsed: NotUsed) = GetCurrentUser(context)

    /**
      * Get the guilds of the client user.
      * @param before Get guilds before this id.
      * @param after Get guilds after this id.
      * @param limit The max amount of guilds to return.
      */
    def fetchCurrentUserGuilds[Ctx](
        before: Option[GuildId] = None,
        after: Option[GuildId] = None,
        limit: Option[Int] = None,
        context: Ctx = NotUsed: NotUsed
    ) = GetCurrentUserGuilds(GetCurrentUserGuildsData(before, after, limit), context)

    /**
      * Create a group DM to a few users.
      * @param accessTokens The access tokens of users that have granted the bot
      *                     the `gdm.join` scope.
      * @param nicks A map specifying the nicknames for the users in this group DM.
      */
    def createGroupDM[Ctx](
        accessTokens: Seq[String],
        nicks: SnowflakeMap[User, String],
        context: Ctx = NotUsed: NotUsed
    ) = CreateGroupDm(CreateGroupDMData(accessTokens, nicks), context)

    /**
      * Fetch an invite by code.
      * @param inviteCode The invite code.
      * @param withCounts If the returned invite object should return approximate
      *                   counts for members and people online.
      */
    def fetchInvite[Ctx](inviteCode: String, withCounts: Boolean = false, context: Ctx = NotUsed: NotUsed) =
      GetInvite(inviteCode, withCounts, context)

    /**
      * Fetch a list of voice regions that can be used when creating a guild.
      */
    def fetchVoiceRegions[Ctx](context: Ctx = NotUsed: NotUsed) = ListVoiceRegions(context)

    /**
      * Fetch a webhook by id.
      */
    def fetchWebhook[Ctx](id: SnowflakeType[Webhook], context: Ctx = NotUsed: NotUsed) = GetWebhook(id, context)

    /**
      * Fetch a webhook by id with token. Doesn't require authentication.
      */
    def fetchWebhookWithToken[Ctx](id: SnowflakeType[Webhook], token: String, context: Ctx = NotUsed: NotUsed) =
      GetWebhookWithToken(id, token, context)
  }
}
