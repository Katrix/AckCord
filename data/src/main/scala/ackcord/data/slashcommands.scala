package ackcord.data

import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class InteractionType(val value: Int) extends IntEnumEntry
object InteractionType extends IntEnum[InteractionType] with IntCirceEnumWithUnknown[InteractionType] {
  override def values: collection.immutable.IndexedSeq[InteractionType] = findValues

  case object Ping               extends InteractionType(1)
  case object ApplicationCommand extends InteractionType(2)
  case class Unknown(i: Int)     extends InteractionType(i)

  override def createUnknown(value: Int): InteractionType = Unknown(value)
}

sealed abstract class InteractionResponseType(val value: Int) extends IntEnumEntry
object InteractionResponseType
    extends IntEnum[InteractionResponseType]
    with IntCirceEnumWithUnknown[InteractionResponseType] {
  override def values: collection.immutable.IndexedSeq[InteractionResponseType] = findValues

  @deprecated("Deprecated by Discord", since = "CHANGEME")
  case object Acknowledge extends InteractionResponseType(2)
  @deprecated("Deprecated by Discord", since = "CHANGEME")
  case object ChannelMessage extends InteractionResponseType(3)

  case object Pong                     extends InteractionResponseType(1)
  case object ChannelMessageWithSource extends InteractionResponseType(4)
  case object ACKWithSource            extends InteractionResponseType(5)
  case class Unknown(i: Int)           extends InteractionResponseType(i)

  override def createUnknown(value: Int): InteractionResponseType = Unknown(value)
}
