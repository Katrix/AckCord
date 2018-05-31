/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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

import scala.language.{higherKinds, implicitConversions}
import scala.util.Try
import scala.util.matching.Regex

import akka.NotUsed
import cats.Monad
import cats.data.{EitherT, OptionT}
import net.katsstuff.ackcord.CacheSnapshot
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.util.MessageParser.RemainingAsString
import shapeless._

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
  def parse[F[_]](
      strings: List[String]
  )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], A)]

  /**
    * Parse a message into the needed types, tossing away the remaining string.
    * @param strings The content of the message where each string is
    *                separated by a space.
    * @param c The cache to use.
    * @return Left with an error message if it failed to parse, or Right with
    *         the parsed value.
    */
  def parseResult[F[_]](strings: List[String])(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, A] =
    parse(strings).map(_._2)

  /**
    * Create a new parser by filtering the values created by this parser.
    * @param f The predicate.
    * @param error The error message if the value does not match the predicate.
    */
  def filterWithError(f: A => Boolean, error: String): MessageParser[A] = new MessageParser[A] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], A)] =
      self.parse(strings).ensure(error) { case (_, obj) => f(obj) }
  }

  /**
    * Create a new parser by applying a function to the result of this parser
    * @param f The function to apply
    * @tparam B The new parser type
    */
  def map[B](f: A => B): MessageParser[B] = new MessageParser[B] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], B)] =
      self.parse(strings).map { case (tail, obj) => tail -> f(obj) }
  }

  /**
    * Apply a partial function of this parser, returning the error if the
    * function isn't defined.
    * @param error The error to return if the partial function isn't defined.
    * @param pf The partial function to apply.
    * @tparam B The new parser type.
    */
  def collectWithError[B](error: String)(pf: PartialFunction[A, B]): MessageParser[B] = new MessageParser[B] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], B)] =
      self.parse(strings).subflatMap {
        case (tail, obj) if pf.isDefinedAt(obj) => Right(tail -> pf(obj))
        case (_, _)                             => Left(error)
      }
  }
}
object MessageParser extends MessageParserInstances with DeriveMessageParser {

  //Just something here to get a different implicit
  case class RemainingAsString(remaining: String)
  implicit def remaining2String(remaining: RemainingAsString): String = remaining.remaining

  def apply[A](implicit parser: MessageParser[A]): MessageParser[A] = parser

  /**
    * Parse a message as an type
    * @param message The message to parse
    * @param parser The parser to use
    * @param c The cache to use
    * @tparam A The type to parse the message as
    * @return Left with an error message if it failed to parse, or Right with the parsed type
    */
  def parseResult[F[_], A](
      message: String
  )(implicit parser: MessageParser[A], c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, A] =
    parser.parseResult(message.split(" ").toList)

}

trait MessageParserInstances {

  /**
    * Create a parser from a string
    * @param f The function to transform the string with
    * @tparam A The type to parse
    */
  def fromString[A](f: String => A): MessageParser[A] = new MessageParser[A] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], A)] =
      if (strings.nonEmpty) EitherT.rightT[F, String](strings.tail -> f(strings.head))
      else EitherT.leftT[F, (List[String], A)]("No more arguments left")
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

    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], A)] =
      if (strings.nonEmpty)
        EitherT.fromEither[F](f(strings.head).toEither).bimap(_.getMessage, strings.tail -> _)
      else
        EitherT.leftT[F, (List[String], A)]("No more arguments left")
  }

  /**
    * Same as [[fromTry]] but with a custom error.
    * @param errorMessage The error message to use
    * @param f The function to transform the string with.
    * @tparam A The type to parse
    */
  def fromTryCustomError[A](errorMessage: String)(f: String => Try[A]): MessageParser[A] = new MessageParser[A] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], A)] =
      if (strings.nonEmpty)
        EitherT.fromEither[F](f(strings.head).toEither).bimap(_ => errorMessage, strings.tail -> _)
      else
        EitherT.leftT[F, (List[String], A)]("No more arguments left")
  }

  implicit val remainingStringParser: MessageParser[MessageParser.RemainingAsString] =
    new MessageParser[RemainingAsString] {
      override def parse[F[_]](
          strings: List[String]
      )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], RemainingAsString)] =
        EitherT.rightT[F, String](Nil -> RemainingAsString(strings.mkString(" ")))
    }

  implicit val stringParser: MessageParser[String]   = fromString(identity)
  implicit val byteParser: MessageParser[Byte]       = withTry(_.toByte)
  implicit val shortParser: MessageParser[Short]     = withTry(_.toShort)
  implicit val intParser: MessageParser[Int]         = withTry(_.toInt)
  implicit val longParser: MessageParser[Long]       = withTry(_.toLong)
  implicit val floatParser: MessageParser[Float]     = withTry(_.toFloat)
  implicit val doubleParser: MessageParser[Double]   = withTry(_.toDouble)
  implicit val booleanParser: MessageParser[Boolean] = withTry(_.toBoolean)

  val userRegex: Regex    = """<@!?(\d+)>""".r
  val channelRegex: Regex = """<#(\d+)>""".r
  val roleRegex: Regex    = """<@&(\d+)>""".r
  val emojiRegex: Regex   = """<:\w+:(\d+)>""".r

  trait HighFunc[F[_[_]], G[_[_]]] {
    def apply[A[_]](fa: F[A]): G[A]
  }

  private def snowflakeParser[C](
      name: String,
      regex: Regex,
      getObj: SnowflakeType[C] => HighFunc[CacheSnapshot, OptionT[?[_], C]]
  ): MessageParser[C] = new MessageParser[C] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], C)] = {
      if (strings.nonEmpty) {
        val head = strings.head
        for {
          m <- EitherT.fromOption[F](regex.findFirstMatchIn(head), s"Invalid $name specified")
          _ <- EitherT.cond[F](m.start == 0 && m.end == head.length, (), s"Invalid $name specified")
          obj <- {
            val snowflake = SnowflakeType[C](RawSnowflake(m.group(1)))
            getObj(snowflake)(c).toRight(s"${name.capitalize} not found")
          }
        } yield strings.tail -> obj
      } else EitherT.leftT[F, (List[String], C)]("No more arguments left")
    }
  }

  implicit val userParser: MessageParser[User] =
    snowflakeParser(
      "user",
      userRegex,
      id =>
        new HighFunc[CacheSnapshot, OptionT[?[_], User]] {
          override def apply[A[_]](fa: CacheSnapshot[A]): OptionT[A, User] = fa.getUser(id)
      }
    )
  implicit val channelParser: MessageParser[Channel] =
    snowflakeParser(
      "channel",
      channelRegex,
      id =>
        new HighFunc[CacheSnapshot, OptionT[?[_], Channel]] {
          override def apply[A[_]](fa: CacheSnapshot[A]): OptionT[A, Channel] = fa.getChannel(id)
      }
    )
  implicit val roleParser: MessageParser[Role] =
    snowflakeParser(
      "role",
      roleRegex,
      id =>
        new HighFunc[CacheSnapshot, OptionT[?[_], Role]] {
          override def apply[A[_]](fa: CacheSnapshot[A]): OptionT[A, Role] = fa.getRole(id)
      }
    )
  implicit val emojiParser: MessageParser[Emoji] =
    snowflakeParser(
      "emoji",
      emojiRegex,
      id =>
        new HighFunc[CacheSnapshot, OptionT[?[_], Emoji]] {
          override def apply[A[_]](fa: CacheSnapshot[A]): OptionT[A, Emoji] = fa.getEmoji(id)
      }
    )

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
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], List[String])] =
      EitherT.rightT[F, String](Nil -> strings)
  }

  /**
    * A parser that will only succeed if there are no strings left.
    */
  implicit val notUsedParser: MessageParser[NotUsed] = new MessageParser[NotUsed] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], NotUsed)] =
      if (strings.isEmpty) EitherT.rightT[F, String](Nil -> NotUsed)
      else EitherT.leftT[F, (List[String], NotUsed)](s"Found dangling arguments: ${strings.mkString(", ")}")
  }
}

trait DeriveMessageParser {
  implicit val hNilParser: MessageParser[HNil] = new MessageParser[HNil] {
    override def parse[F[_]](
        string: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], HNil)] =
      EitherT.rightT[F, String](string -> HNil)
  }

  implicit def hListParser[Head, Tail <: HList](
      implicit
      headParser: Lazy[MessageParser[Head]],
      tailParser: Lazy[MessageParser[Tail]]
  ): MessageParser[Head :: Tail] = new MessageParser[Head :: Tail] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], Head :: Tail)] =
      for {
        t1 <- headParser.value.parse(strings)
        t2 <- tailParser.value.parse(t1._1)
      } yield t2._1 -> (t1._2 :: t2._2)
  }

  implicit val cNilParser: MessageParser[CNil] = new MessageParser[CNil] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], CNil)] =
      throw new IllegalStateException("Tried to parse CNil")
  }

  implicit def coProductParser[Head, Tail <: Coproduct](
      implicit
      headParser: Lazy[MessageParser[Head]],
      tailParser: Lazy[MessageParser[Tail]]
  ): MessageParser[Head :+: Tail] = new MessageParser[Head :+: Tail] {
    override def parse[F[_]](
        strings: List[String]
    )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], Head :+: Tail)] = {
      //TODO: Check if we can remove the type annotations here
      val head: EitherT[F, String, (List[String], Head :+: Tail)] =
        headParser.value.parse(strings).map(t => t._1 -> Inl(t._2))
      lazy val tail: EitherT[F, String, (List[String], Head :+: Tail)] =
        tailParser.value.parse(strings).map(t => t._1 -> Inr(t._2))

      head.orElse(tail)
    }
  }

  implicit def caseSerializer[A, Repr](
      implicit gen: Generic.Aux[A, Repr],
      ser: Lazy[MessageParser[Repr]]
  ): MessageParser[A] =
    new MessageParser[A] {
      override def parse[F[_]](
          strings: List[String]
      )(implicit c: CacheSnapshot[F], F: Monad[F]): EitherT[F, String, (List[String], A)] =
        ser.value.parse(strings).map { case (remaining, repr) => remaining -> gen.from(repr) }
    }
}
