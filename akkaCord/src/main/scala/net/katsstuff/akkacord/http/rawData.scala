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

import java.time.OffsetDateTime

import net.katsstuff.akkacord.data._

case class RawChannel(
  id: ChannelId,
  `type`: ChannelType,
  guildId: Option[GuildId],
  position: Option[Int],
  permissionOverwrites: Option[Seq[PermissionValue]],
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
case class RawGuildMember(user: User, nick: Option[String], roles: Seq[RoleId], joinedAt: OffsetDateTime, deaf: Boolean, mute: Boolean)

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
    webhookId: Option[String],
    `type`: MessageType
)

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
    roles: Seq[Role],
    emojis: Seq[GuildEmoji],
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

case class RawPresenceGame(name: String, `type`: Int, url: Option[String])
case class RawPresence(user: PartialUser, game: Option[RawPresenceGame], status: Option[PresenceStatus])

//Remember to edit User when editing this
//TODO: Remove once shapeless PartialUser works again
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