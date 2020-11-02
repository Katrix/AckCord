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

/**
  * A simple invite.
  * @param code An invite code.
  * @param guild The guild the invite is for.
  * @param channel The channel the invite is for.
  * @param targetUser The target user of this invite.
  * @param targetUserType The target user type of this invite.
  * @param approximatePresenceCount Approximate amount of people online.
  * @param approximateMemberCount Approximate amount of total members.
  */
case class Invite(
    code: String,
    guild: Option[InviteGuild],
    channel: InviteChannel,
    inviter: Option[User],
    targetUser: Option[InviteTargetUser],
    targetUserType: Option[Int],
    approximatePresenceCount: Option[Int],
    approximateMemberCount: Option[Int]
)

/**
  * An invite with extra information.
  * @param code An invite code.
  * @param guild The guild the invite is for.
  * @param channel The channel the invite is for.
  * @param targetUser The target user of this invite.
  * @param targetUserType The target user type of this invite.
  * @param approximatePresenceCount Approximate amount of people online.
  * @param approximateMemberCount Approximate amount of total members.
  * @param uses How many times the invite has been used.
  * @param maxUses How many times this invite can be used.
  * @param maxAge The duration in seconds when the invite will expire
  * @param temporary If this invite is temporary
  * @param createdAt When this invite was created
  */
case class InviteWithMetadata(
    code: String,
    guild: Option[InviteGuild],
    channel: InviteChannel,
    inviter: Option[User],
    targetUser: Option[InviteTargetUser],
    targetUserType: Option[Int],
    approximatePresenceCount: Option[Int],
    approximateMemberCount: Option[Int],
    uses: Int,
    maxUses: Int,
    maxAge: Int,
    temporary: Boolean,
    createdAt: OffsetDateTime
)

/**
  * A newly created invite.
  * @param code An invite code.
  * @param guildId The guild the invite is for.
  * @param channelId The channel the invite is for.
  * @param uses How many times the invite has been used.
  * @param maxUses How many times this invite can be used.
  * @param maxAge The duration in seconds when the invite will expire
  * @param temporary If this invite is temporary
  * @param createdAt When this invite was created
  */
case class CreatedInvite(
    code: String,
    guildId: Option[GuildId],
    channelId: GuildChannelId,
    inviter: Option[User],
    uses: Int,
    maxUses: Int,
    maxAge: Int,
    temporary: Boolean,
    createdAt: OffsetDateTime,
    targetUser: Option[InviteTargetUser],
    targetUserType: Option[Int]
)

/**
  * A partial guild with the information used by an invite
  * @param id The guild id
  * @param name The guild name
  * @param splash The guild splash hash
  * @param banner The banner of the guild
  * @param description The description for the guild
  * @param icon The guild icon hash
  * @param features The guild features for the guild
  * @param verificationLevel The verification level of the guild
  * @param vanityUrlCode The vanity URL code for the guild
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
    vanityUrlCode: Option[String]
)

/**
  * A partial channel with the information used by an invite
  * @param id The channel id
  * @param name The channel name
  * @param `type` The type of channel
  */
case class InviteChannel(id: GuildChannelId, name: String, `type`: ChannelType)

/**
  * The target user of an invite.
  * @param id The user id
  * @param name The name of the user
  * @param avatar The avatar hash of the user
  * @param discriminator The discriminator of the user
  */
case class InviteTargetUser(
    id: UserId,
    name: String,
    avatar: Option[String],
    discriminator: String
)
