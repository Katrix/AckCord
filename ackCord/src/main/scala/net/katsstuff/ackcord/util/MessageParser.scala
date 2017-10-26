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

trait MessageParser[A] { self =>
  def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)]

  def filterWithError(f: A => Boolean, error: String): MessageParser[A] = new MessageParser[A] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)] =
      self.parse(strings)(c).filterOrElse({ case (_, obj) => f(obj) }, error)
  }

  def map[B](f: A => B): MessageParser[B] = new MessageParser[B] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], B)] =
      self.parse(strings)(c).map { case (tail, obj) => tail -> f(obj) }
  }

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

  def parseHlist[L <: HList](message: String)(implicit parser: MessageParser[L], c: CacheSnapshot): Either[String, L] =
    parser.parse(message.split(" ").toList).map(_._2)
  def parse[A](message: String)(implicit parser: MessageParser[A], c: CacheSnapshot): Either[String, A] =
    parser.parse(message.split(" ").toList).map(_._2)

}

trait MessageParserInstances {

  def fromString[A](f: String => A): MessageParser[A] = new MessageParser[A] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)] =
      if (strings.nonEmpty) Right((strings.tail, f(strings.head)))
      else Left("No more arguments left")
  }

  def withTry[A](f: String => A): MessageParser[A] = fromTry(s => Try(f(s)))
  def fromTry[A](f: String => Try[A]): MessageParser[A] = new MessageParser[A] {
    override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], A)] =
      if (strings.nonEmpty) f(strings.head).toEither.fold(e => Left(e.getMessage), a => Right(strings.tail -> a))
      else Left("No more arguments left")
  }

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

  def regexParser[A](
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

  implicit val userParser:    MessageParser[User]    = regexParser("user", userRegex, _.getUser(_))
  implicit val channelParser: MessageParser[Channel] = regexParser("channel", channelRegex, _.getChannel(_))
  implicit val roleParser:    MessageParser[Role]    = regexParser("role", roleRegex, _.getRole(_))
  implicit val emojiParser:   MessageParser[Emoji]   = regexParser("emoji", emojiRegex, _.getEmoji(_))

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
