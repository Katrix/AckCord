package ackcord.data

import scala.collection.immutable

import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.{IntEnum, IntEnumEntry}

/**
  * Holds info about a live stage.
  *
  * @param id
  *   Id of the stage.
  * @param guildId
  *   Id of the belonging guild.
  * @param channelId
  *   Id of the stage channel tied to this stage.
  * @param topic
  *   Topic of the stage instance.
  * @param privacyLevel
  *   Who the stage instance is visible to.
  * @param discoverableDisabled
  *   If stage discovery is disabled or not.
  */
case class StageInstance(
    id: SnowflakeType[StageInstance],
    guildId: GuildId,
    channelId: StageGuildChannelId,
    topic: String,
    privacyLevel: StageInstancePrivacyLevel,
    discoverableDisabled: Boolean
)

sealed abstract class StageInstancePrivacyLevel(val value: Int) extends IntEnumEntry
object StageInstancePrivacyLevel
    extends IntEnum[StageInstancePrivacyLevel]
    with IntCirceEnumWithUnknown[StageInstancePrivacyLevel] {
  override def values: immutable.IndexedSeq[StageInstancePrivacyLevel] = findValues

  case object Public    extends StageInstancePrivacyLevel(1)
  case object GuildOnly extends StageInstancePrivacyLevel(2)

  case class Unknown(override val value: Int) extends StageInstancePrivacyLevel(value)

  override def createUnknown(value: Int): StageInstancePrivacyLevel = Unknown(value)
}
