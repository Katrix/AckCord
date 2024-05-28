package ackcord.data

import java.lang.{Long => JLong}
import java.time.Instant

import io.circe._

trait SnowflakeDefs {

  type Snowflake[+A] = Snowflake.Snowflake[A]

  type RawSnowflake = Snowflake[Any]
  object RawSnowflake extends SnowflakeCompanion[Any]

  type GuildId = Snowflake[Guild]
  object GuildId extends SnowflakeCompanion[Guild]

  type ChannelId = Snowflake[Channel]
  object ChannelId extends SnowflakeCompanion[Channel]

  type TextChannelId = Snowflake[TextChannel]
  object TextChannelId extends SnowflakeCompanion[TextChannel]

  type GuildChannelId = Snowflake[GuildChannel]
  object GuildChannelId extends SnowflakeCompanion[GuildChannel]

  type TextGuildChannelId = Snowflake[TextGuildChannel]
  object TextGuildChannelId extends SnowflakeCompanion[TextGuildChannel]

  type VoiceGuildChannelId = Snowflake[VoiceGuildChannel]
  object VoiceGuildChannelId extends SnowflakeCompanion[VoiceGuildChannel]

  type TopLevelTextGuildChannelId = Snowflake[TopLevelTextGuildChannel]
  object TopLevelTextGuildChannelId extends SnowflakeCompanion[TopLevelTextGuildChannel]

  type NormalVoiceGuildChannelId = Snowflake[NormalVoiceGuildChannel]
  object NormalVoiceGuildChannelId extends SnowflakeCompanion[NormalVoiceGuildChannel]

  type GuildCategoryId = Snowflake[GuildCategory]
  object GuildCategoryId extends SnowflakeCompanion[GuildCategory]

  type ThreadChannelId = Snowflake[ThreadChannel]
  object ThreadChannelId extends SnowflakeCompanion[ThreadChannel]

  type StageChannelId = Snowflake[StageChannel]
  object StageChannelId extends SnowflakeCompanion[StageChannel]

  type ForumChannelId = Snowflake[ForumChannel]
  object ForumChannelId extends SnowflakeCompanion[ForumChannel]

  type DMChannelId = Snowflake[DMChannel]
  object DMChannelId extends SnowflakeCompanion[DMChannel]

  type GroupDMChannelId = Snowflake[GroupDMChannel]
  object GroupDMChannelId extends SnowflakeCompanion[GroupDMChannel]

  type MessageId = Snowflake[Message]
  object MessageId extends SnowflakeCompanion[Message]

  type UserId = Snowflake[User]
  object UserId extends SnowflakeCompanion[User]

  type RoleId = Snowflake[Role]
  object RoleId extends SnowflakeCompanion[Role]

  type UserOrRoleId = Snowflake[UserOrRole]
  object UserOrRoleId extends SnowflakeCompanion[UserOrRole]

  type EmojiId = Snowflake[Emoji]
  object EmojiId extends SnowflakeCompanion[Emoji]

  type ApplicationId = Snowflake[Application]
  object ApplicationId extends SnowflakeCompanion[Application]

  type WebhookId = Snowflake[Webhook]
  object WebhookId extends SnowflakeCompanion[Webhook]

  type GuildScheduledEventId = Snowflake[GuildScheduledEvent]
  object GuildScheduledEventId extends SnowflakeCompanion[GuildScheduledEvent]
}

object Snowflake {
  private[data] type Base
  private[data] trait Tag extends Any

  type Snowflake[+A] <: Base with Tag

  final private val DiscordEpoch = 1420070400000L

  def apply[A](long: Long): Snowflake[A] = long.asInstanceOf[Snowflake[A]]

  def apply[A](content: String): Snowflake[A] = apply[A](JLong.parseUnsignedLong(content))

  def apply[A, B](other: Snowflake[A]): Snowflake[B] =
    other.asInstanceOf[Snowflake[B]]

  /**
    * Creates a snowflake tag for the earliest moment in time. Use this for
    * pagination.
    */
  def epoch[A]: Snowflake[A] = apply[A]("0")

  /** Creates a snowflake for a specific moment. Use this for pagination. */
  def fromInstant[A](instant: Instant): Snowflake[A] = apply(instant.toEpochMilli - DiscordEpoch << 22)

  implicit def snowflakeOrdering[A]: Ordering[Snowflake[A]] = (x: Snowflake[A], y: Snowflake[A]) =>
    JLong.compareUnsigned(
      x.toUnsignedLong,
      y.toUnsignedLong
    )

  implicit def codec[A]: Codec[Snowflake[A]] = Codec.from(
    Decoder[String].emap(s => Right(Snowflake[A](s))),
    Encoder[String].contramap(_.asString)
  )

  implicit def snowflakeTypeKeyDecoder[A]: KeyDecoder[Snowflake[A]] =
    KeyDecoder.decodeKeyString.map(s => Snowflake[A](s))

  implicit def snowflakeTypeKeyEncoder[A]: KeyEncoder[Snowflake[A]] =
    KeyEncoder.encodeKeyString.contramap(_.asString)

  implicit class SnowflakeTypeSyntax[A](private val snowflake: Snowflake[A]) extends AnyVal with Ordered[Snowflake[A]] {
    def creationDate: Instant = {
      val DiscordEpoch = 1420070400000L
      Instant.ofEpochMilli(DiscordEpoch + (toUnsignedLong >> 22))
    }

    def asString: String = JLong.toUnsignedString(toUnsignedLong)

    override def compare(that: Snowflake[A]): Int =
      JLong.compareUnsigned(snowflake.toUnsignedLong, that.toUnsignedLong)

    def toUnsignedLong: Long = snowflake.asInstanceOf[Long]
  }
}

trait SnowflakeCompanion[Type] {
  def apply(content: String): Snowflake[Type]     = JLong.parseUnsignedLong(content).asInstanceOf[Snowflake[Type]]
  def apply(long: Long): Snowflake[Type]          = long.asInstanceOf[Snowflake[Type]]
  def apply[A](other: Snowflake[A]): Snowflake[Type] = other.asInstanceOf[Snowflake[Type]]
}
