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
package ackcord.data

import java.time.OffsetDateTime

import scala.collection.immutable

import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.{IntEnum, IntEnumEntry}

/**
  * A simple invite.
  * @param code
  *   An invite code.
  * @param guild
  *   The guild the invite is for.
  * @param channel
  *   The channel the invite is for.
  * @param targetUser
  *   The user who's stream should be displayed for this invite.
  * @param targetType
  *   The type of target for this voice channel invite.
  * @param targetApplication
  *   The embedded application to open for this voice channel embedded
  *   application invite.
  * @param approximatePresenceCount
  *   Approximate amount of people online.
  * @param approximateMemberCount
  *   Approximate amount of total members.
  * @param stageInstance
  *   Stage instance if there is a public stage instance in the stage channel
  *   this invite leads to.
  */
case class Invite(
    code: String,
    guild: Option[InviteGuild],
    channel: Option[InviteChannel],
    inviter: Option[User],
    targetUser: Option[User],
    targetType: Option[InviteTargetType],
    targetApplication: Option[PartialApplication],
    approximatePresenceCount: Option[Int],
    approximateMemberCount: Option[Int],
    expiresAt: Option[OffsetDateTime],
    @deprecated("Discord has deprecated stage discovery", since = "0.19.0") stageInstance: Option[InviteStageInstance],
    guildScheduledInvite: Option[GuildScheduledEvent]
)

/**
  * An invite with extra information.
  * @param code
  *   An invite code.
  * @param guild
  *   The guild the invite is for.
  * @param channel
  *   The channel the invite is for.
  * @param targetUser
  *   The user who's stream should be displayed for this invite.
  * @param targetType
  *   The type of target for this voice channel invite.
  * @param targetApplication
  *   The embedded application to open for this voice channel embedded
  *   application invite.
  * @param approximatePresenceCount
  *   Approximate amount of people online.
  * @param approximateMemberCount
  *   Approximate amount of total members.
  * @param stageInstance
  *   Stage instance if there is a public stage instance in the stage channel
  *   this invite leads to.
  * @param uses
  *   How many times the invite has been used.
  * @param maxUses
  *   How many times this invite can be used.
  * @param maxAge
  *   The duration in seconds when the invite will expire
  * @param temporary
  *   If this invite is temporary
  * @param createdAt
  *   When this invite was created
  */
case class InviteWithMetadata(
    code: String,
    guild: Option[InviteGuild],
    channel: Option[InviteChannel],
    inviter: Option[User],
    targetUser: Option[User],
    targetType: Option[InviteTargetType],
    targetApplication: Option[PartialApplication],
    approximatePresenceCount: Option[Int],
    approximateMemberCount: Option[Int],
    expiresAt: Option[OffsetDateTime],
    stageInstance: Option[InviteStageInstance],
    uses: Int,
    maxUses: Int,
    maxAge: Int,
    temporary: Boolean,
    createdAt: OffsetDateTime
)

@deprecated("Discord has deprecated stage discovery", since = "0.19.0")
case class InviteStageInstance(
    members: Seq[InviteStageInstanceMember],
    participantCount: Int,
    speakerCount: Int,
    topic: String
)

case class InviteStageInstanceMember(
    roles: Seq[RoleId],
    nick: Option[String],
    avatar: Option[String],
    premiumSince: Option[OffsetDateTime],
    joinedAt: OffsetDateTime,
    pending: Option[Boolean],
    user: User
)

/**
  * A newly created invite.
  * @param channelId
  *   The channel the invite is for.
  * @param code
  *   An invite code.
  * @param createdAt
  *   When this invite was created
  * @param guildId
  *   The guild the invite is for.
  * @param inviter
  *   The user that created the invite.
  * @param maxAge
  *   The duration in seconds when the invite will expire
  * @param maxUses
  *   How many times this invite can be used.
  * @param targetType
  *   The type of target for this voice channel invite.
  * @param targetUser
  *   The user who's stream should be displayed for this invite.
  * @param targetApplication
  *   The embedded application to open for this voice channel embedded
  *   application invite.
  * @param temporary
  *   If this invite is temporary
  * @param uses
  *   How many times the invite has been used.
  */
case class CreatedInvite(
    channelId: GuildChannelId,
    code: String,
    createdAt: OffsetDateTime,
    guildId: Option[GuildId],
    inviter: Option[User],
    maxAge: Int,
    maxUses: Int,
    targetUser: Option[User],
    targetType: Option[InviteTargetType],
    targetApplication: Option[PartialApplication],
    temporary: Boolean,
    uses: Int
)

/**
  * A partial guild with the information used by an invite
  * @param id
  *   The guild id
  * @param name
  *   The guild name
  * @param splash
  *   The guild splash hash
  * @param banner
  *   The banner of the guild
  * @param description
  *   The description for the guild
  * @param icon
  *   The guild icon hash
  * @param features
  *   The guild features for the guild
  * @param verificationLevel
  *   The verification level of the guild
  * @param vanityUrlCode
  *   The vanity URL code for the guild
  * @param nsfwLevel
  *   The guild NSFW level
  * @param premiumSubscriptionCount
  *   How many users that are boosting the server
  */
case class InviteGuild(
    id: GuildId,
    name: String,
    splash: Option[String],
    banner: Option[String],
    description: Option[String],
    icon: Option[String],
    features: Seq[GuildFeature],
    verificationLevel: VerificationLevel,
    vanityUrlCode: Option[String],
    welcomeScreen: Option[WelcomeScreen],
    nsfwLevel: Option[NSFWLevel],
    premiumSubscriptionCount: Option[Int]
)

/**
  * A partial channel with the information used by an invite
  * @param id
  *   The channel id
  * @param name
  *   The channel name
  * @param `type`
  *   The type of channel
  */
case class InviteChannel(id: GuildChannelId, name: String, `type`: ChannelType)

sealed abstract class InviteTargetType(val value: Int) extends IntEnumEntry
object InviteTargetType extends IntEnum[InviteTargetType] with IntCirceEnumWithUnknown[InviteTargetType] {
  override def values: immutable.IndexedSeq[InviteTargetType] = findValues

  case object Stream                          extends InviteTargetType(1)
  case object EmbeddedApplication             extends InviteTargetType(2)
  case class Unknown(override val value: Int) extends InviteTargetType(value)

  override def createUnknown(value: Int): InviteTargetType = Unknown(value)
}
