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

import java.time.OffsetDateTime

import cats.Traverse
import net.katsstuff.ackcord.SnowflakeMap
import net.katsstuff.ackcord.data._

/**
  * A raw channel before going through the cache.
  * @param id The channel id.
  * @param `type` The channel type.
  * @param guildId The guildId this channel belongs to if it's a guild channel.
  * @param position The position of this channel if it's a guild channel.
  * @param permissionOverwrites The permission overwrites of this channel if
  *                             it's a guild channel.
  * @param name The name of this channel if it's a guild channel.
  * @param topic The topic of this channel if it's a guild voice channel.
  * @param nsfw If this channel is NSFW if it's a guild channel.
  * @param lastMessageId The last message id if it's a text channel. The id
  *                      may be invalid.
  * @param bitrate The bitrate of this channel if it's a guild voice channel.
  * @param userLimit The user limit of this channel if it's a guild voice channel.
  * @param recipients The recipients of this channel if it's a group DM channel.
  * @param icon The icon of this channel if it has one.
  * @param ownerId The owner of this channel if it's a DM or group DM channel.
  * @param applicationId The application id of this channel if it's a
  *                      guild channel.
  * @param parentId The category of this channel if it's a guild channel.
  */
case class RawChannel(
    id: ChannelId,
    `type`: ChannelType,
    guildId: Option[GuildId],
    position: Option[Int],
    permissionOverwrites: Option[Seq[PermissionOverwrite]],
    name: Option[String],
    topic: Option[String],
    nsfw: Option[Boolean],
    lastMessageId: Option[MessageId],
    bitrate: Option[Int],
    userLimit: Option[Int],
    recipients: Option[Seq[User]],
    icon: Option[String],
    ownerId: Option[UserId],
    applicationId: Option[RawSnowflake],
    parentId: Option[ChannelId],
    lastPinTimestamp: Option[OffsetDateTime]
) {

  /**
    * Try to convert this to a normal channel.
    */
  def toChannel: Option[Channel] = {
    `type` match {
      case ChannelType.GuildText =>
        for {
          guildId              <- guildId
          name                 <- name
          position             <- position
          permissionOverwrites <- permissionOverwrites
        } yield {
          TGuildChannel(
            id,
            guildId,
            name,
            position,
            SnowflakeMap(permissionOverwrites.map(p => p.id -> p): _*),
            topic,
            lastMessageId,
            nsfw.getOrElse(false),
            parentId,
            lastPinTimestamp
          )
        }
      case ChannelType.DM =>
        for {
          recipients <- recipients
          if recipients.nonEmpty
        } yield {
          DMChannel(id, lastMessageId, recipients.head.id)
        }
      case ChannelType.GuildVoice =>
        for {
          guildId              <- guildId
          name                 <- name
          position             <- position
          permissionOverwrites <- permissionOverwrites
          bitrate              <- bitrate
          userLimit            <- userLimit
        } yield {
          VGuildChannel(
            id,
            guildId,
            name,
            position,
            SnowflakeMap(permissionOverwrites.map(p => p.id -> p): _*),
            bitrate,
            userLimit,
            nsfw.getOrElse(false),
            parentId
          )
        }
      case ChannelType.GroupDm =>
        for {
          name       <- name
          recipients <- recipients
          ownerId    <- ownerId
        } yield {
          GroupDMChannel(id, name, recipients.map(_.id), lastMessageId, ownerId, applicationId, icon)
        }
      case ChannelType.GuildCategory =>
        for {
          guildId              <- guildId
          name                 <- name
          position             <- position
          permissionOverwrites <- permissionOverwrites
        } yield {
          GuildCategory(
            id,
            guildId,
            name,
            position,
            SnowflakeMap(permissionOverwrites.map(p => p.id -> p): _*),
            nsfw.getOrElse(false),
            parentId
          )
        }
    }
  }
}

//Remember to edit RawGuildMemberWithGuild when editing this
/**
  * Represents a user in a guild.
  * @param user The user of this member.
  * @param nick The nickname of this user in this guild.
  * @param roles The roles of this user.
  * @param joinedAt When this user joined the guild.
  * @param deaf If this user is deaf.
  * @param mute IF this user is mute.
  */
case class RawGuildMember(
    user: User,
    nick: Option[String],
    roles: Seq[RoleId],
    joinedAt: OffsetDateTime,
    deaf: Boolean,
    mute: Boolean
) {

  /**
    * Convert this to a normal guild member.
    */
  def toGuildMember(guildId: GuildId) = GuildMember(user.id, guildId, nick, roles, joinedAt, deaf, mute)
}

/**
  * A raw message before going through the cache.
  * @param id The id of the message.
  * @param channelId The channel this message was sent to.
  * @param author The author that sent this message.
  * @param content The content of this message.
  * @param timestamp The timestamp this message was created.
  * @param editedTimestamp The timestamp this message was last edited.
  * @param tts If this message is has text-to-speech enabled.
  * @param mentionEveryone If this message mentions everyone.
  * @param mentions All the users this message mentions.
  * @param mentionRoles All the roles this message mentions.
  * @param attachment All the attachments of this message.
  * @param embeds All the embeds of this message.
  * @param reactions All the reactions on this message.
  * @param nonce A nonce for this message.
  * @param pinned If this message is pinned.
  * @param `type` The message type
  */
case class RawMessage(
    id: MessageId,
    channelId: ChannelId,
    author: Author[_],
    content: String,
    timestamp: OffsetDateTime,
    editedTimestamp: Option[OffsetDateTime],
    tts: Boolean,
    mentionEveryone: Boolean,
    mentions: Seq[User],
    mentionRoles: Seq[RoleId],
    attachment: Seq[Attachment],
    embeds: Seq[ReceivedEmbed],
    reactions: Option[Seq[Reaction]], //reactions can be missing
    nonce: Option[RawSnowflake],
    pinned: Boolean,
    `type`: MessageType
) {

  /**
    * Convert this to a normal message.
    */
  def toMessage: Message =
    Message(
      id,
      channelId,
      RawSnowflake(author.id),
      author.isUser,
      content,
      timestamp,
      editedTimestamp,
      tts,
      mentionEveryone,
      mentions.map(_.id),
      mentionRoles,
      attachment,
      embeds,
      reactions.getOrElse(Seq.empty),
      nonce,
      pinned,
      `type`
    )
}

/**
  * A a raw guild before going through the cache.
  * @param id The id of the guild.
  * @param name The name of the guild.
  * @param icon The icon hash.
  * @param splash The splash hash.
  * @param owner If the current user is the owner of the guild.
  * @param ownerId The userId of the owner.
  * @param permissions The permissions of the current user without overwrites.
  * @param region The voice region
  * @param afkChannelId The channelId of the AFK channel.
  * @param afkTimeout The amount of seconds you need to be AFK before being
  *                   moved to the AFK channel.
  * @param embedEnabled If the embed is enabled.
  * @param embedChannelId The channelId for the embed.
  * @param verificationLevel The verification level for the guild.
  * @param defaultMessageNotifications The notification level for the guild.
  * @param explicitContentFilter The explicit content filter level for the guild.
  * @param roles The roles of the guild.
  * @param emojis The emojis of the guild.
  * @param features The enabled guild features.
  * @param mfaLevel The MFA level.
  * @param applicationId The application id if this guild is bot created.
  * @param widgetEnabled If the widget is enabled.
  * @param widgetChannelId The channel id for the widget.
  * @param systemChannelId The channel which system messages are sent to.
  * @param joinedAt When the client joined the guild.
  * @param large If this guild is above the large threshold.
  * @param unavailable If this guild is unavailable.
  * @param memberCount The amount of members in the guild.
  * @param voiceStates The voice states of the guild.
  * @param members The guild members in the guild.
  * @param channels The channels in the guild.
  * @param presences The presences in the guild.
  */
case class RawGuild(
    id: GuildId,
    name: String,
    icon: Option[String],
    splash: Option[String],
    owner: Option[Boolean],
    ownerId: UserId,
    permissions: Option[Permission],
    region: String,
    afkChannelId: Option[ChannelId], //AfkChannelId can be null
    afkTimeout: Int,
    embedEnabled: Option[Boolean],
    embedChannelId: Option[ChannelId],
    verificationLevel: VerificationLevel,
    defaultMessageNotifications: NotificationLevel,
    explicitContentFilter: FilterLevel,
    roles: Seq[RawRole],
    emojis: Seq[RawEmoji],
    features: Seq[String],
    mfaLevel: MFALevel,
    applicationId: Option[RawSnowflake],
    widgetEnabled: Option[Boolean],
    widgetChannelId: Option[ChannelId],
    systemChannelId: Option[ChannelId],
    joinedAt: Option[OffsetDateTime],
    large: Option[Boolean],
    unavailable: Option[Boolean],
    memberCount: Option[Int],
    voiceStates: Option[Seq[VoiceState]],
    members: Option[Seq[RawGuildMember]],
    channels: Option[Seq[RawChannel]],
    presences: Option[Seq[RawPresence]]
) {

  /**
    * Try to convert this to a normal guild.
    */
  def toGuild: Option[Guild] = {
    import cats.implicits._

    for {
      joinedAt    <- joinedAt
      large       <- large
      memberCount <- memberCount
      voiceStates <- voiceStates
      members     <- members
      rawChannels <- channels
      channels <- Traverse[List].sequence(
        rawChannels
          .map(_.toChannel.collect {
            case ch: GuildChannel => ch
          })
          .toList
      )
      presences <- presences
    } yield {

      Guild(
        id,
        name,
        icon,
        splash,
        owner,
        ownerId,
        permissions,
        region,
        afkChannelId,
        afkTimeout,
        embedEnabled,
        embedChannelId,
        verificationLevel,
        defaultMessageNotifications,
        explicitContentFilter,
        SnowflakeMap(roles.map(r => r.id  -> r.toRole(id)): _*),
        SnowflakeMap(emojis.map(e => e.id -> e.toEmoji): _*),
        features,
        mfaLevel,
        applicationId,
        widgetEnabled,
        widgetChannelId,
        systemChannelId,
        joinedAt,
        large,
        memberCount,
        SnowflakeMap(voiceStates.map(v => v.userId  -> v): _*),
        SnowflakeMap(members.map(mem => mem.user.id -> mem.toGuildMember(id)): _*),
        SnowflakeMap(channels.map(ch => ch.id       -> ch): _*),
        SnowflakeMap(presences.map(p => p.user.id   -> p.toPresence): _*)
      )
    }
  }
}

/**
  * A raw role before going through the cache.
  * @param id The id of this role.
  * @param name The name of this role.
  * @param color The color of this role.
  * @param hoist If this role is listed in the sidebar.
  * @param position The position of this role.
  * @param permissions The permissions this role grant.
  * @param managed If this is a bot role.
  * @param mentionable If you can mention this role.
  */
case class RawRole(
    id: RoleId,
    name: String,
    color: Int,
    hoist: Boolean,
    position: Int,
    permissions: Permission,
    managed: Boolean,
    mentionable: Boolean
) {

  def toRole(guildId: GuildId): Role =
    Role(id, guildId, name, color, hoist, position, permissions, managed, mentionable)
}

/**
  * The content of a presence.
  * @param name The text show.
  * @param `type` The type of the presence.
  * @param url A uri if the type is streaming.
  */
case class RawPresenceGame(name: String, `type`: Int, url: Option[String]) {

  def toContent: PresenceContent = `type` match {
    case 0 => PresenceGame(name)
    case 1 => PresenceStreaming(name, url.get)
    case 2 => PresenceListening(name)
    case 3 => PresenceWatching(name)
  }
}

/**
  * A raw presence.
  * @param user A partial user.
  * @param game The content of the presence.
  * @param status The presence status.
  */
case class RawPresence(user: PartialUser, game: Option[RawPresenceGame], status: Option[PresenceStatus]) {

  def toPresence: Presence = Presence(user.id, game.map(_.toContent), status.getOrElse(PresenceStatus.Online))
}

/**
  * A user where fields can be missing.
  * @param id The id of the user.
  * @param username The name of the user.
  * @param discriminator The discriminator for the user. Those four last
  *                      digits when clicking in a users name.
  * @param avatar The users avatar hash.
  * @param bot If this user belongs to a OAuth2 application.
  * @param mfaEnabled If this user has two factor authentication enabled.
  * @param verified If this user is verified. Requires the email OAuth scope.
  * @param email The users email. Requires the email OAuth scope.
  */
//Remember to edit User when editing this
case class PartialUser(
    id: UserId,
    username: Option[String],
    discriminator: Option[String],
    avatar: Option[String], //avatar can be null
    bot: Option[Boolean],
    mfaEnabled: Option[Boolean],
    verified: Option[Boolean],
    email: Option[String]
)

/**
  * A raw ban before going through the cache.
  * @param reason Why the user was banned.
  * @param user The user that was baned.
  */
case class RawBan(reason: Option[String], user: User) {
  def toBan: Ban = Ban(reason, user.id)
}

/**
  * A raw emoji before going through the cache.
  * @param id The id of the emoji.
  * @param name The emoji name.
  * @param roles The roles that can use this emoji.
  * @param user The user that created this emoji.
  * @param requireColons If the emoji requires colons.
  * @param managed If the emoji is managed.
  */
case class RawEmoji(
    id: EmojiId,
    name: String,
    roles: Seq[RoleId],
    user: Option[User],
    requireColons: Boolean,
    managed: Boolean,
    animated: Boolean
) {

  def toEmoji: Emoji = Emoji(id, name, roles, user.map(_.id), requireColons, managed, animated)
}
