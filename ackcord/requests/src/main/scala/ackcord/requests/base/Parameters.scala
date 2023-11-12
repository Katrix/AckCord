package ackcord.requests.base

import ackcord.data._

object Parameters {

  /** A typeclass describing how to print a type in an URI. */
  trait ParameterPrintable[A] {

    /** Print a value for use within an URI. */
    def print(a: A): String
  }

  object ParameterPrintable extends LowPriorityParameterPrintable {
    implicit def snowflakePrintable[A]: ParameterPrintable[Snowflake[A]] = (a: Snowflake[A]) => a.asString
  }

  trait LowPriorityParameterPrintable {
    implicit def printableToString[A]: ParameterPrintable[A] = (a: A) => a.toString
  }

  case class NormalParameter[A](name: String, value: A, major: Boolean = false)(
      implicit printable: ParameterPrintable[A]
  ) {
    def print: String = printable.print(value)
  }

  case class QueryParameter[A](name: String, value: UndefOr[A])(implicit printable: ParameterPrintable[A]) {
    def print(a: A): String = printable.print(a)
  }

  case class SeqQueryParameter[A](name: String, value: UndefOr[Seq[A]])(implicit printable: ParameterPrintable[A]) {
    def print(a: A): String = printable.print(a)
  }

  case class ConcatParameter[A](value: A)(implicit printable: ParameterPrintable[A]) {
    def print: String = printable.print(value)
  }

  def apply[A](name: String, value: A, major: Boolean = false): NormalParameter[A] = NormalParameter(name, value, major)

  def query[A](name: String, value: UndefOr[A]): QueryParameter[A] =
    QueryParameter(name, value)

  def queryAlways[A](name: String, value: A): QueryParameter[A] = query(name, UndefOrSome(value))

}
