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
  * @param approximatePresenceCount Approximate amount of people online.
  * @param approximateMemberCount Approximate amount of total members.
  */
case class Invite(
    code: String,
    guild: InviteGuild,
    channel: InviteChannel,
    approximatePresenceCount: Option[Int],
    approximateMemberCount: Option[Int]
)

/**
  * An invite with extra information.
  * @param code An invite code.
  * @param guild The guild the invite is for.
  * @param channel The channel the invite is for.
  * @param inviter The user that created the invite.
  * @param uses How many times the invite has been used.
  * @param maxUses How many times this invite can be used.
  * @param maxAge The duration in seconds when the invite will expire
  * @param temporary If this invite is temporary
  * @param createdAt When this invite was created
  * @param revoked If this invite has been revoked
  */
case class InviteWithMetadata(
    code: String,
    guild: InviteGuild,
    channel: InviteChannel,
    inviter: User,
    uses: Int,
    maxUses: Int,
    maxAge: Int,
    temporary: Boolean,
    createdAt: OffsetDateTime,
    revoked: Boolean
)

/**
  * A partial guild with the information used by an invite
  * @param id The guild id
  * @param name The guild name
  * @param splash The guild splash hash
  * @param icon The guild icon hash
  */
case class InviteGuild(id: GuildId, name: String, splash: Option[String], icon: Option[String])

/**
  * A partial channel with the information used by an invite
  * @param id The channel id
  * @param name The channel name
  * @param `type` The type of channel
  */
case class InviteChannel(id: ChannelId, name: String, `type`: ChannelType)
