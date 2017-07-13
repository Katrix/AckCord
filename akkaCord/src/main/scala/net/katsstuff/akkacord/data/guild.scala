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
package net.katsstuff.akkacord.data

import java.time.{Instant, OffsetDateTime}

sealed trait UnknownStatusGuild {
  def id:          GuildId
  def unavailable: Boolean
}

case class Guild(
    id: GuildId,
    name: String,
    icon: Option[String], ////Icon can be null
    splash: Option[String], //Splash can be null
    ownerId: UserId,
    afkChannelId: Option[ChannelId], //AfkChannelId can be null
    afkTimeout: Int,
    embedEnabled: Option[Boolean], //embedEnabled can be missing
    embedChannelId: Option[ChannelId], //embedChannelId can be missing
    verificationLevel: Int, //TODO: Better than Int here
    defaultMessageNotifications: Int, //TODO: Better than int here
    roles: Map[RoleId, Role],
    emojis: Map[EmojiId, GuildEmoji],
    //features:                    Seq[Feature], //TODO: What is a feature?
    mfaLevel: Int, //TODO: Better than int here
    joinedAt: OffsetDateTime,
    large: Boolean,
    memberCount: Int,
    voiceStates: Seq[VoiceState],
    members: Map[UserId, GuildMember],
    channels: Map[ChannelId, GuildChannel],
    presences: Map[UserId, Presence]
) extends UnknownStatusGuild {
  override def unavailable: Boolean = false
}

case class UnavailableGuild(id: GuildId) extends UnknownStatusGuild {
  override def unavailable: Boolean = true
}

case class GuildMember(userId: UserId, guildId: GuildId, nick: Option[String], roles: Seq[RoleId], joinedAt: OffsetDateTime, deaf: Boolean, mute: Boolean)
    extends GetUser with GetGuild
case class GuildEmoji(id: EmojiId, name: String, roles: Seq[RoleId], requireColons: Boolean, managed: Boolean)

sealed trait PresenceContent {
  def name: String
}
case class PresenceGame(name: String)                   extends PresenceContent
case class PresenceStreaming(name: String, uri: String) extends PresenceContent
sealed trait PresenceStatus
object PresenceStatus {
  case object Idle         extends PresenceStatus
  case object Online       extends PresenceStatus
  case object Offline      extends PresenceStatus
  case object DoNotDisturb extends PresenceStatus

  def nameOf(status: PresenceStatus): String = status match {
    case Idle => "idle"
    case Online => "online"
    case Offline => "offline"
    case DoNotDisturb => "dnd"
  }

  def forName(name: String): Option[PresenceStatus] = name match {
    case "idle" => Some(Idle)
    case "online" => Some(Online)
    case "offline" => Some(Offline)
    case "dnd" => Some(DoNotDisturb)
    case _ => None
  }
}
case class Presence(userId: UserId, game: Option[PresenceContent], status: PresenceStatus) extends GetUser

case class Integration(
    id: IntegrationId,
    name: String,
    `type`: String, //TODO: Use enum here
    enabled: Boolean,
    syncing: Boolean,
    roleId: RoleId,
    expireBehavior: Int, //TODO: Better than Int here
    expireGracePeriod: Int,
    user: User,
    account: IntegrationAccount,
    syncedAt: Instant
)

case class IntegrationAccount(id: String /*TODO: Is String correct here*/, name: String)

case class GuildEmbed(enabled: Boolean, channelId: ChannelId)