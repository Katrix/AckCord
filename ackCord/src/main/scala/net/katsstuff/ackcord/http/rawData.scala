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

import java.time.OffsetDateTime

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
    applicationId: Option[Snowflake],
    parentId: Option[ChannelId]
)

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
)

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
    author: Author,
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
    nonce: Option[Snowflake],
    pinned: Boolean,
    `type`: MessageType
)

/**
  * A a raw guild before going through the cache.
  * @param id The id of the guild.
  * @param name The name of the guild.
  * @param icon The icon hash.
  * @param splash The splash hash.
  * @param ownerId The userId of the owner.
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
    icon: Option[String], //Icon can be null
    splash: Option[String], //Splash can be null
    ownerId: UserId,
    region: String,
    afkChannelId: Option[ChannelId], //AfkChannelId can be null
    afkTimeout: Int,
    embedEnabled: Option[Boolean], //embedEnabled can be missing
    embedChannelId: Option[ChannelId], //embedChannelId can be missing
    verificationLevel: VerificationLevel,
    defaultMessageNotifications: NotificationLevel,
    explicitContentFilter: FilterLevel,
    roles: Seq[RawRole],
    emojis: Seq[Emoji],
    features: Seq[String],
    mfaLevel: MFALevel,
    applicationId: Option[Snowflake],
    widgetEnabled: Option[Boolean], //Can be missing
    widgetChannelId: Option[ChannelId], //Can be missing
    joinedAt: Option[OffsetDateTime],
    large: Option[Boolean],
    unavailable: Option[Boolean],
    memberCount: Option[Int],
    voiceStates: Option[Seq[VoiceState]],
    members: Option[Seq[RawGuildMember]],
    channels: Option[Seq[RawChannel]],
    presences: Option[Seq[RawPresence]]
)

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
  def makeRole(guildId: GuildId): Role =
    Role(id, guildId, name, color, hoist, position, permissions, managed, mentionable)
}

/**
  * The content of a presence.
  * @param name The text show.
  * @param `type` The type of the presence.
  * @param url A uri if the type is streaming.
  */
case class RawPresenceGame(name: String, `type`: Int, url: Option[String])

/**
  * A raw presence.
  * @param user A partial user.
  * @param game The content of the presence.
  * @param status The presence status.
  */
case class RawPresence(user: PartialUser, game: Option[RawPresenceGame], status: Option[PresenceStatus])

/**
  * A user where fields can be missing.
  * @param id The id of the user.
  * @param username The name of the user.
  * @param discriminator The discriminator for the user. Those four last
  *                      digits when clicking in a users name.
  * @param avatar The users avatar hash
  * @param bot If this user is a bot
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
    bot: Option[Boolean], //Bot can be missing
    mfaEnabled: Option[Boolean], //mfaEnabled can be missing
    verified: Option[Boolean], //verified can be missing
    email: Option[String] //Email can be null
)

/**
  * A raw ban before going through the cache.
  * @param reason Why the user was banned.
  * @param user The user that was baned.
  */
case class RawBan(
    reason: Option[String],
    user: User
)