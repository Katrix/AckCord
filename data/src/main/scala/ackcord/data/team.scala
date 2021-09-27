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

import scala.collection.immutable

import ackcord.data.raw.PartialUser
import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.{IntEnum, IntEnumEntry}

/**
  * A discord team.
  * @param icon
  *   Icon identifier for the team.
  * @param id
  *   Id of the team.
  * @param members
  *   Members of the team.
  * @param ownerUserId
  *   The id of the current owner of the team
  */
case class Team(
    icon: Option[String],
    id: SnowflakeType[Team],
    members: Seq[TeamMember],
    name: String,
    ownerUserId: UserId
)

/**
  * A member of a team.
  * @param membershipState
  *   The membership state of the member
  * @param permissions
  *   The permissions of team member
  * @param teamId
  *   The id of the team this member belongs to
  * @param user
  *   A partial user containing the avatar, discriminator, id and username of
  *   the user
  */
case class TeamMember(
    membershipState: TeamMembershipState,
    permissions: Seq[String],
    teamId: SnowflakeType[Team],
    user: PartialUser
)

sealed abstract class TeamMembershipState(val value: Int) extends IntEnumEntry
object TeamMembershipState
    extends IntEnum[TeamMembershipState]
    with IntCirceEnumWithUnknown[TeamMembershipState] {
  override def values: immutable.IndexedSeq[TeamMembershipState] = findValues

  case object Invited extends TeamMembershipState(1)
  case object Accepted extends TeamMembershipState(2)
  case class Unknown(i: Int) extends TeamMembershipState(i)

  override def createUnknown(value: Int): TeamMembershipState = Unknown(value)
}
