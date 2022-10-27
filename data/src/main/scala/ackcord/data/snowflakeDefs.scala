package ackcord.data

import scala.language.implicitConversions

import java.lang.{Long => JLong}
import java.time.Instant

import io.circe._

trait SnowflakeDefs {

  type SnowflakeType[+A] = SnowflakeType.SnowflakeType[A]

  type RawSnowflake = SnowflakeType[Any]
  object RawSnowflake extends SnowflakeCompanion[Any]
}

object SnowflakeType {
  private[data] type Base
  private[data] trait Tag extends Any

  type SnowflakeType[+A] <: Base with Tag

  final private val DiscordEpoch = 1420070400000L

  def apply[A](long: Long): SnowflakeType[A] = long.asInstanceOf[SnowflakeType[A]]

  def apply[A](content: String): SnowflakeType[A] = apply[A](JLong.parseUnsignedLong(content))

  def apply[A](other: SnowflakeType[_]): SnowflakeType[A] =
    other.asInstanceOf[SnowflakeType[A]]

  /**
    * Creates a snowflake tag for the earliest moment in time. Use this for
    * pagination.
    */
  def epoch[A]: SnowflakeType[A] = apply[A]("0")

  /** Creates a snowflake for a specific moment. Use this for pagination. */
  def fromInstant[A](instant: Instant): SnowflakeType[A] = apply(instant.toEpochMilli - DiscordEpoch << 22)

  implicit def snowflakeOrdering[A]: Ordering[SnowflakeType[A]] = (x: SnowflakeType[A], y: SnowflakeType[A]) =>
    JLong.compareUnsigned(
      x.toUnsignedLong,
      y.toUnsignedLong
    )

  implicit def codec[A]: Codec[SnowflakeType[A]] = Codec.from(
    Decoder[String].emap(s => Right(SnowflakeType[A](s))),
    Encoder[String].contramap(_.asString)
  )

  implicit def snowflakeTypeKeyDecoder[A]: KeyDecoder[SnowflakeType[A]] =
    KeyDecoder.decodeKeyString.map(s => SnowflakeType[A](s))

  implicit def snowflakeTypeKeyEncoder[A]: KeyEncoder[SnowflakeType[A]] =
    KeyEncoder.encodeKeyString.contramap(_.asString)

  implicit class SnowflakeTypeSyntax[A](private val snowflake: SnowflakeType[A]) extends AnyVal with Ordered[SnowflakeType[A]] {
    def creationDate: Instant = {
      val DiscordEpoch = 1420070400000L
      Instant.ofEpochMilli(DiscordEpoch + (toUnsignedLong >> 22))
    }

    def asString: String = JLong.toUnsignedString(toUnsignedLong)

    override def compare(that: SnowflakeType[A]): Int =
      JLong.compareUnsigned(snowflake.toUnsignedLong, that.toUnsignedLong)

    def toUnsignedLong: Long = snowflake.asInstanceOf[Long]
  }
}

private[data] trait SnowflakeCompanion[Type] {
  def apply(content: String): SnowflakeType[Type] = JLong.parseUnsignedLong(content).asInstanceOf[SnowflakeType[Type]]
  def apply(long: Long): SnowflakeType[Type]      = long.asInstanceOf[SnowflakeType[Type]]
  def apply(other: SnowflakeType[_]): SnowflakeType[Type] = other.asInstanceOf[SnowflakeType[Type]]
}
