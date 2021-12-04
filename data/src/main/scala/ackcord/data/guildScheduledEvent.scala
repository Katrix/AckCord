package ackcord.data

import java.time.OffsetDateTime

import scala.collection.immutable

import ackcord.data.raw.RawGuildMember
import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.IntEnumEntry

/**
  * A scheduled event within a guild.
  *
  * @param id
  *   The id of the event
  * @param guildId
  *   The id of the guild the event takes place in.
  * @param channelId
  *   The id of the channel the event takes place in, if it takes place in a
  *   channel.
  * @param creatorId
  *   The creator id of the event.
  * @param name
  *   Name of the event.
  * @param description
  *   Description of the event.
  * @param scheduledStartTime
  *   Time the event will start.
  * @param scheduledEndTime
  *   Time the event will end.
  * @param privacyLevel
  *   Privacy level of the event.
  * @param status
  *   Status of the event.
  * @param entityType
  *   The type of the event.
  * @param entityId
  *   Id of an entity associated with the event.
  * @param entityMetadata
  *   Additional metadata.
  * @param creator
  *   The creator of the event.
  * @param userCount
  *   Number of users subscribed to the event.
  */
case class GuildScheduledEvent(
    id: SnowflakeType[GuildScheduledEvent],
    guildId: GuildId,
    channelId: Option[VoiceGuildChannelId],
    creatorId: Option[UserId],
    name: String,
    description: Option[String],
    scheduledStartTime: OffsetDateTime,
    scheduledEndTime: Option[OffsetDateTime],
    privacyLevel: GuildScheduledEventPrivacyLevel,
    status: GuildScheduledEventStatus,
    entityType: GuildScheduledEventEntityType,
    entityId: Option[SnowflakeType[StageInstance]],
    entityMetadata: Option[GuildScheduledEventEntityMetadata],
    creator: Option[User],
    userCount: Option[Int]
)

sealed abstract class GuildScheduledEventPrivacyLevel(val value: Int) extends IntEnumEntry
object GuildScheduledEventPrivacyLevel extends IntCirceEnumWithUnknown[GuildScheduledEventPrivacyLevel] {
  override def values: immutable.IndexedSeq[GuildScheduledEventPrivacyLevel] = findValues

  case object GuildOnly extends GuildScheduledEventPrivacyLevel(2)

  case class Unknown(i: Int) extends GuildScheduledEventPrivacyLevel(i)
  override def createUnknown(value: Int): GuildScheduledEventPrivacyLevel = Unknown(value)
}

sealed abstract class GuildScheduledEventEntityType(val value: Int) extends IntEnumEntry
object GuildScheduledEventEntityType extends IntCirceEnumWithUnknown[GuildScheduledEventEntityType] {
  override def values: immutable.IndexedSeq[GuildScheduledEventEntityType] = findValues

  case object StageInstance extends GuildScheduledEventEntityType(1)
  case object Voice         extends GuildScheduledEventEntityType(2)
  case object External      extends GuildScheduledEventEntityType(3)

  case class Unknown(i: Int) extends GuildScheduledEventEntityType(i)
  override def createUnknown(value: Int): GuildScheduledEventEntityType = Unknown(value)
}

sealed abstract class GuildScheduledEventStatus(val value: Int) extends IntEnumEntry
object GuildScheduledEventStatus extends IntCirceEnumWithUnknown[GuildScheduledEventStatus] {
  override def values: immutable.IndexedSeq[GuildScheduledEventStatus] = findValues

  case object Scheduled extends GuildScheduledEventStatus(1)
  case object Active    extends GuildScheduledEventStatus(2)
  case object Completed extends GuildScheduledEventStatus(3)
  case object Cancelled extends GuildScheduledEventStatus(4)

  case class Unknown(i: Int) extends GuildScheduledEventStatus(i)
  override def createUnknown(value: Int): GuildScheduledEventStatus = Unknown(value)
}

case class GuildScheduledEventEntityMetadata(
    location: Option[String]
)

case class GuildScheduledEventUser(
    guildScheduledEventId: SnowflakeType[GuildScheduledEvent],
    user: User,
    member: RawGuildMember
)
