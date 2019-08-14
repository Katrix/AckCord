package ackcord.data

import scala.collection.immutable

import ackcord.data.raw.PartialUser
import enumeratum.values.{IntCirceEnum, IntEnum, IntEnumEntry}

/**
  * A discord team.
  * @param icon Icon identifier for the team.
  * @param id Id of the team.
  * @param members Members of the team.
  * @param ownerUserId The id of the current owner of the team
  */
case class Team(
    icon: Option[String],
    id: SnowflakeType[Team],
    members: Seq[TeamMember],
    ownerUserId: UserId
)

/**
  * A member of a team.
  * @param membershipState The membership state of the member
  * @param permissions The permissions of team member
  * @param teamId The id of the team this member belongs to
  * @param user A partial user containing the avatar, discriminator, id and username of the user
  */
case class TeamMember(
    membershipState: TeamMembershipState,
    permissions: Seq[String],
    teamId: SnowflakeType[Team],
    user: PartialUser
)

sealed abstract class TeamMembershipState(val value: Int) extends IntEnumEntry
object TeamMembershipState extends IntEnum[TeamMembershipState] with IntCirceEnum[TeamMembershipState] {
  case object Invited  extends TeamMembershipState(1)
  case object Accepted extends TeamMembershipState(2)

  override def values: immutable.IndexedSeq[TeamMembershipState] = findValues
}
