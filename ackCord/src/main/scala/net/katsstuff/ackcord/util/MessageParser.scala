/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord.util

import scala.util.Try
import scala.util.matching.Regex

import akka.NotUsed
import net.katsstuff.ackcord.data.{CacheSnapshot, Channel, GuildChannel, Emoji, Role, Snowflake, TChannel, TGuildChannel, User}
import net.katsstuff.ackcord.util.MessageParser.RemainingAsString
import shapeless._
import shapeless.tag._

/**
  * MessageParser is a typeclass to simplify parsing messages. It can derive
  * instances for any ADT, and makes it much easier to work with messages.
  * @tparam A The type to parse.
  */
trait MessageParser[A] { self =>

  /**
    * Parse a message into the needed types.
    * @param strings The content of the message where each string is
    *                separated by a space.
    * @param c The cache to use.
    * @return Left with an error message if it failed to parse, or Right with
    *         the remaining arguments, and the parsed value.
    */
  def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)]

  /**
    * Create a new parser by filtering the values created by this parser.
    * @param f The predicate.
    * @param error The error message if the value does not match the predicate.
    */
  def filterWithError(f: A => Boolean, error: String): MessageParser[A] = new MessageParser[A] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)] =
      self.parse(strings)(c).filterOrElse({ case (_, obj) => f(obj) }, error)
  }

  /**
    * Create a new parser by applying a function to the result of this parser
    * @param f The function to apply
    * @tparam B The new parser type
    */
  def map[B](f: A => B): MessageParser[B] = new MessageParser[B] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], B)] =
      self.parse(strings)(c).map { case (tail, obj) => tail -> f(obj) }
  }

  /**
    * Apply a partial function of this parser, returning the error if the
    * function isn't defined.
    * @param error The error to return if the partial function isn't defined.
    * @param pf The partial function to apply.
    * @tparam B The new parser type.
    */
  def collectWithError[B](error: String)(pf: PartialFunction[A, B]): MessageParser[B] = new MessageParser[B] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], B)] = {
      val base = self.parse(strings)(c)

      base.flatMap {
        case (tail, obj) if pf.isDefinedAt(obj) => Right(tail -> pf(obj))
        case (_, _)                             => Left(error)
      }
    }
  }
}
object MessageParser extends MessageParserInstances with DeriveMessageParser {

  //Just something here to get a different implicit
  type RemainingAsString = String @@ String

  def apply[A](implicit parser: MessageParser[A]): MessageParser[A] = parser

  /**
    * Parse a message as an type
    * @param message The message to parse
    * @param parser The parser to use
    * @param c The cache to use
    * @tparam A The type to parse the message as
    * @return Left with an error message if it failed to parse, or Right with the parsed type
    */
  def parse[A](message: String)(implicit parser: MessageParser[A], c: CacheSnapshot): Either[String, A] =
    parser.parse(message.split(" ").toList).map(_._2)

}

trait MessageParserInstances {

  /**
    * Create a parser from a string
    * @param f The function to transform the string with
    * @tparam A The type to parse
    */
  def fromString[A](f: String => A): MessageParser[A] = new MessageParser[A] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)] =
      if (strings.nonEmpty) Right((strings.tail, f(strings.head)))
      else Left("No more arguments left")
  }

  /**
    * Parse a string with a function that can throw
    * @param f The function to transform the string with. This can throw an exception
    * @tparam A The type to parse
    */
  def withTry[A](f: String => A): MessageParser[A] = fromTry(s => Try(f(s)))

  /**
    * Parse a string with a try
    * @param f The function to transform the string with.
    * @tparam A The type to parse
    */
  def fromTry[A](f: String => Try[A]): MessageParser[A] = new MessageParser[A] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)] =
      if (strings.nonEmpty) f(strings.head).toEither.fold(e => Left(e.getMessage), a => Right(strings.tail -> a))
      else Left("No more arguments left")
  }

  /**
    * Same as [[fromTry]] but with a custom error.
    * @param errorMessage The error message to use
    * @param f The function to transform the string with.
    * @tparam A The type to parse
    */
  def fromTryCustomError[A](errorMessage: String)(f: String => Try[A]): MessageParser[A] = new MessageParser[A] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)] =
      if (strings.nonEmpty) f(strings.head).toEither.fold(_ => Left(errorMessage), a => Right(strings.tail -> a))
      else Left("No more arguments left")
  }

  implicit val stringParser: MessageParser[String] = fromString(identity)
  implicit val remainingStringParser: MessageParser[MessageParser.RemainingAsString] =
    new MessageParser[RemainingAsString] {
      override def parse(
          strings: List[String]
      )(implicit c: CacheSnapshot): Either[String, (List[String], RemainingAsString)] = {
        val tagged = tag[String](strings.mkString(" "))
        Right((Nil, tagged))
      }
    }
  implicit val byteParser:   MessageParser[Byte]   = withTry(_.toByte)
  implicit val shortParser:  MessageParser[Short]  = withTry(_.toShort)
  implicit val intParser:    MessageParser[Int]    = withTry(_.toInt)
  implicit val longParser:   MessageParser[Long]   = withTry(_.toLong)
  implicit val floatParser:  MessageParser[Float]  = withTry(_.toFloat)
  implicit val doubleParser: MessageParser[Double] = withTry(_.toDouble)

  val userRegex:    Regex = """<@!?(\d+)>""".r
  val channelRegex: Regex = """<#(\d+)>""".r
  val roleRegex:    Regex = """<@&(\d+)>""".r
  val emojiRegex:   Regex = """<:\w+:(\d+)>""".r

  private def snowflakeParser[A](
      name: String,
      regex: Regex,
      getObj: (CacheSnapshot, Snowflake @@ A) => Option[A]
  ): MessageParser[A] = new MessageParser[A] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)] = {
      if (strings.nonEmpty) {
        val head = strings.head

        for {
          m   <- userRegex.findFirstMatchIn(head).toRight(s"Invalid $name specified")
          _   <- Either.cond(m.start == 0 && m.end == head.length, (), s"Invalid $name specified")
          obj <- getObj(c, tag[A](Snowflake(m.group(1)))).toRight(s"${name.capitalize} not found")
        } yield strings.tail -> obj
      } else Left("No more arguments left")
    }
  }

  implicit val userParser:    MessageParser[User]    = snowflakeParser("user", userRegex, _.getUser(_))
  implicit val channelParser: MessageParser[Channel] = snowflakeParser("channel", channelRegex, _.getChannel(_))
  implicit val roleParser:    MessageParser[Role]    = snowflakeParser("role", roleRegex, _.getRole(_))
  implicit val emojiParser:   MessageParser[Emoji]   = snowflakeParser("emoji", emojiRegex, _.getEmoji(_))

  implicit val tChannelParser: MessageParser[TChannel] =
    channelParser.collectWithError("Passed in channel is not a text channel") {
      case channel: TChannel => channel
    }

  implicit val guildChannelParser: MessageParser[GuildChannel] =
    channelParser.collectWithError("Passed in channel is not a guild channel") {
      case channel: GuildChannel => channel
    }

  implicit val tGuildChannelParser: MessageParser[TGuildChannel] =
    channelParser.collectWithError("Passed in channel is not a guild text channel") {
      case channel: TGuildChannel => channel
    }

  /**
    * A parser that will return all the strings passed to it.
    */
  val allStringsParser: MessageParser[List[String]] = new MessageParser[List[String]] {
    override def parse(strings: List[String])(
        implicit
        c: CacheSnapshot
    ): Either[String, (List[String], List[String])] = Right((Nil, strings))
  }

  /**
    * A parser that will only succeed if there are no strings left.
    */
  implicit val notUsedParser: MessageParser[NotUsed] = new MessageParser[NotUsed] {
    override def parse(strings: List[String])(
        implicit
        c: CacheSnapshot
    ): Either[String, (List[String], NotUsed)] =
      if (strings.isEmpty) Right((Nil, NotUsed)) else Left(s"Found dangling arguments: ${strings.mkString(", ")}")
  }
}

trait DeriveMessageParser {
  implicit val hNilParser: MessageParser[HNil] = new MessageParser[HNil] {
    override def parse(string: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], HNil)] =
      Either.cond(string.isEmpty, (Nil, HNil), "Found content after end")
  }

  implicit def hListParser[Head, Tail <: HList](
      implicit headParser: Lazy[MessageParser[Head]],
      tailParser: Lazy[MessageParser[Tail]]
  ): MessageParser[Head :: Tail] = new MessageParser[Head :: Tail] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], Head :: Tail)] =
      headParser.value.parse(strings).flatMap {
        case (remaining, head) =>
          tailParser.value.parse(remaining).map { case (lastRemaining, tail) => lastRemaining -> (head :: tail) }
      }
  }

  implicit def caseSerializer[A, Repr](
      implicit gen: LabelledGeneric.Aux[A, Repr],
      ser: Lazy[MessageParser[Repr]]
  ): MessageParser[A] =
    new MessageParser[A] {
      override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)] =
        ser.value.parse(strings).map { case (remaining, repr) => remaining -> gen.from(repr) }
    }
}
