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
  def id:          Snowflake
  def unavailable: Boolean
}

case class Guild(
    id: Snowflake,
    name: String,
    icon: Option[String], ////Icon can be null
    splash: Option[String], //Splash can be null
    ownerId: Snowflake,
    afkChannelId: Option[Snowflake], //AfkChannelId can be null
    afkTimeout: Int,
    embedEnabled: Option[Boolean], //embedEnabled can be missing
    embedChannelId: Option[Snowflake], //embedChannelId can be missing
    verificationLevel: Int, //TODO: Better than Int here
    defaultMessageNotifications: Int, //TODO: Better than int here
    roles: Map[Snowflake, Role],
    emojis: Map[Snowflake, GuildEmoji],
    //features:                    Seq[Feature], //TODO: What is a feature?
    mfaLevel: Int, //TODO: Better than int here
    joinedAt: OffsetDateTime,
    large: Boolean,
    memberCount: Int,
    voiceStates: Seq[VoiceState],
    members: Map[Snowflake, GuildMember],
    channels: Map[Snowflake, GuildChannel],
    presences: Map[Snowflake, Presence]
) extends UnknownStatusGuild {
  override def unavailable: Boolean = false
}

case class UnavailableGuild(id: Snowflake) extends UnknownStatusGuild {
  override def unavailable: Boolean = true
}

case class GuildMember(userId: Snowflake, nick: Option[String], roles: Seq[Snowflake], joinedAt: OffsetDateTime, deaf: Boolean, mute: Boolean)
    extends GetUser
case class GuildEmoji(id: Snowflake, name: String, roles: Seq[Snowflake], requireColons: Boolean, managed: Boolean)

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
}
case class Presence(userId: Snowflake, game: Option[PresenceContent], status: PresenceStatus) extends GetUser

case class Integration(
    id: Snowflake,
    name: String,
    `type`: String, //TODO: Use enum here
    enabled: Boolean,
    syncing: Boolean,
    roleId: Snowflake,
    expireBehavior: Int, //TODO: Better than Int here
    expireGracePeriod: Int,
    user: User,
    account: IntegrationAccount,
    syncedAt: Instant
)

case class IntegrationAccount(id: String /*TODO: Is String correct here*/, name: String)

case class GuildEmbed(enabled: Boolean, channelId: Snowflake)