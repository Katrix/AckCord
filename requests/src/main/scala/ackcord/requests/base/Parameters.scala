package ackcord.requests.base

import ackcord.data._

object Parameters {

  /** A typeclass describing how to print a type in an URI. */
  trait ParameterPrintable[A] {

    /** Print a value for use within an URI. */
    def print(a: A): String
  }

  object ParameterPrintable extends LowPriorityParameterPrintable {
    implicit def snowflakePrintable[A]: ParameterPrintable[SnowflakeType[A]] = (a: SnowflakeType[A]) => a.asString
  }

  trait LowPriorityParameterPrintable {
    implicit def printableToString[A]: ParameterPrintable[A] = (a: A) => a.toString
  }

  case class MajorParameter[A](name: String, value: A)(implicit printable: ParameterPrintable[A]) {
    def print: String = printable.print(value)
  }

  case class MinorParameter[A](name: String, value: A)(implicit printable: ParameterPrintable[A]) {
    def print: String = printable.print(value)
  }

  case class QueryParameter[A](name: String, value: Option[A])(implicit printable: ParameterPrintable[A]) {
    def print(a: A): String = printable.print(a)
  }

  case class SeqQueryParameter[A](name: String, value: Option[Seq[A]])(implicit printable: ParameterPrintable[A]) {
    def print(a: A): String = printable.print(a)
  }

  case class ConcatParameter[A](value: A)(implicit printable: ParameterPrintable[A]) {
    def print: String = printable.print(value)
  }

  def ofGuildId(guildId: GuildId): MajorParameter[GuildId] = new MajorParameter[GuildId]("guildId", guildId)

  def ofChannelId(channelId: ChannelId): MinorParameter[ChannelId] = new MinorParameter[ChannelId]("channelId", channelId)

  def query[A](name: String, value: Option[A]): QueryParameter[A] =
    QueryParameter(name, value)

}
