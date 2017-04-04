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

sealed trait RawChannel {
  def id:        Snowflake
  def isPrivate: Boolean
}

case class RawGuildChannel(
    id:                   Snowflake,
    guildId:              Option[Snowflake], //guildId can be missing
    name:                 String,
    `type`:               String,
    position:             Int,
    isPrivate:            Boolean,
    permissionOverwrites: Seq[PermissionValue],
    topic:                Option[String],
    lastMessageId:        Option[Snowflake],
    bitrate:              Option[Int],
    userLimit:            Option[Int]
) extends RawChannel

case class RawGuildMember(user: User, nick: Option[String], roles: Seq[Snowflake], joinedAt: OffsetDateTime, deaf: Boolean, mute: Boolean)

//Can't lastMessageId be null here?
case class RawDMChannel(id: Snowflake, isPrivate: Boolean, recipient: User, lastMessageId: Option[Snowflake]) extends RawChannel

case class RawMessage(
    id:              Snowflake,
    channelId:       Snowflake,
    author:          Author,
    content:         String,
    timestamp:       OffsetDateTime,
    editedTimestamp: Option[OffsetDateTime],
    tts:             Boolean,
    mentionEveryone: Boolean,
    mentions:        Seq[User],
    mentionRoles:    Seq[Snowflake],
    attachment:      Seq[Attachment],
    embeds:          Seq[Embed],
    reactions:       Option[Seq[Reaction]], //reactions can be missing
    nonce:           Option[Snowflake],
    pinned:          Boolean,
    webhookId:       Option[String]
)

case class RawGuild(
    id:                          Snowflake,
    name:                        String,
    icon:                        Option[String], //Icon can be null
    splash:                      Option[String], //Splash can be null
    ownerId:                     Snowflake,
    region:                      String,
    afkChannelId:                Option[Snowflake], //AfkChannelId can be null
    afkTimeout:                  Int,
    embedEnabled:                Option[Boolean], //embedEnabled can be missing
    embedChannelId:              Option[Snowflake], //embedChannelId can be missing
    verificationLevel:           Int,
    defaultMessageNotifications: Int,
    roles:                       Seq[Role],
    emojis:                      Seq[GuildEmoji],
    //features: Seq[Feature],
    mfaLevel:    Int,
    joinedAt:    Option[OffsetDateTime],
    large:       Option[Boolean],
    unavailable: Option[Boolean],
    memberCount: Option[Int],
    voiceStates: Option[Seq[VoiceState]],
    members:     Option[Seq[RawGuildMember]],
    channels:    Option[Seq[RawGuildChannel]],
    presences:   Option[Seq[RawPresence]]
)

case class RawUnavailableGuild(id: Snowflake, unavailable: Boolean)
case class RawPresenceGame(name:   Option[String], `type`: Option[Int], url: Option[String])
case class RawPresence(user:       PartialUser, game:      Option[RawPresenceGame], status: Option[String])
