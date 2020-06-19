/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
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
package ackcord.commands

import scala.language.implicitConversions

import java.util.Locale

import scala.util.Try
import scala.util.matching.Regex

import ackcord.CacheSnapshot
import ackcord.data._
import ackcord.commands.MessageParser.RemainingAsString
import akka.NotUsed
import cats.data.StateT
import cats.mtl.syntax.all._
import cats.mtl.{ApplicativeHandle, MonadState}
import cats.syntax.all._
import cats.{Monad, MonadError}

/**
  * MessageParser is a typeclass to simplify parsing messages. It can derive
  * instances for any ADT, and makes it much easier to work with messages.
  * @tparam A The type to parse.
  */
trait MessageParser[A] { self =>

  /**
    * A program to parse a message into the needed types.
    */
  def parse[F[_]](
      implicit c: CacheSnapshot,
      F: Monad[F],
      E: ApplicativeHandle[F, String],
      S: MonadState[F, List[String]]
  ): F[A]

  /**
    * Create a new parser by filtering the values created by this parser.
    * @param f The predicate.
    * @param error The error message if the value does not match the predicate.
    */
  def filterWithError(f: A => Boolean, error: String): MessageParser[A] = new MessageParser[A] {
    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[A] = self.parse[F].flatMap(res => if (f(res)) res.pure else error.raise)
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
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[B] =
      self.parse.flatMap {
        case obj if pf.isDefinedAt(obj) => pf(obj).pure
        case _                          => error.raise
      }
  }

  /** Try another parser if this one fails. */
  def orElse[B](that: MessageParser[B]): MessageParser[Either[A, B]] =
    MessageParser.orElseWith(this, that)(identity)

  /** Try another parser if this one fails, and combine their types. */
  def orElseWith[B, C](that: MessageParser[B])(f: Either[A, B] => C): MessageParser[C] =
    MessageParser.orElseWith(this, that)(f)

  /** Runs another parser after this one. */
  def andThen[B](that: MessageParser[B]): MessageParser[(A, B)] = (this, that).tupled
}
object MessageParser extends MessageParserInstances with DeriveMessageParser {

  //Just something here to get a different implicit
  case class RemainingAsString(remaining: String)
  implicit def remaining2String(remaining: RemainingAsString): String = remaining.remaining

  def apply[A](implicit parser: MessageParser[A]): MessageParser[A] = parser

  implicit val messageParserMonad: MonadError[MessageParser, String] = new MonadError[MessageParser, String] {
    override def pure[A](x: A): MessageParser[A] = new MessageParser[A] {
      override def parse[F[_]](
          implicit c: CacheSnapshot,
          F: Monad[F],
          E: ApplicativeHandle[F, String],
          S: MonadState[F, List[String]]
      ): F[A] = F.pure(x)
    }

    override def flatMap[A, B](fa: MessageParser[A])(f: A => MessageParser[B]): MessageParser[B] =
      new MessageParser[B] {
        override def parse[F[_]](
            implicit c: CacheSnapshot,
            F: Monad[F],
            E: ApplicativeHandle[F, String],
            S: MonadState[F, List[String]]
        ): F[B] = fa.parse[F].flatMap(f(_).parse[F])
      }

    override def tailRecM[A, B](a: A)(f: A => MessageParser[Either[A, B]]): MessageParser[B] = new MessageParser[B] {
      override def parse[F[_]](
          implicit c: CacheSnapshot,
          F: Monad[F],
          E: ApplicativeHandle[F, String],
          S: MonadState[F, List[String]]
      ): F[B] = F.tailRecM(a)(f(_).parse[F])
    }

    override def raiseError[A](e: String): MessageParser[A] = new MessageParser[A] {
      override def parse[F[_]](
          implicit c: CacheSnapshot,
          F: Monad[F],
          E: ApplicativeHandle[F, String],
          S: MonadState[F, List[String]]
      ): F[A] = e.raise
    }

    override def handleErrorWith[A](fa: MessageParser[A])(f: String => MessageParser[A]): MessageParser[A] =
      new MessageParser[A] {
        override def parse[F[_]](
            implicit c: CacheSnapshot,
            F: Monad[F],
            E: ApplicativeHandle[F, String],
            S: MonadState[F, List[String]]
        ): F[A] = fa.parse[F].handleWith[String](f(_).parse[F])
      }
  }

  /**
    * Parse a message as an type
    * @param args The message to parse
    * @param parser The parser to use
    * @param c The cache to use
    * @tparam A The type to parse the message as
    * @return Left with an error message if it failed to parse, or Right with the parsed type
    */
  def parseResultEither[A](
      args: List[String],
      parser: MessageParser[A]
  )(implicit c: CacheSnapshot): Either[String, A] = parseEither(args, parser).map(_._2)

  def parseEither[A](
      args: List[String],
      parser: MessageParser[A]
  )(implicit c: CacheSnapshot): Either[String, (List[String], A)] = {
    import cats.instances.either._
    import cats.mtl.instances.handle._
    import cats.mtl.instances.state._
    import cats.mtl.instances.statet._

    parser.parse[StateT[Either[String, *], List[String], *]].run(args)
  }

}

trait MessageParserInstances {

  private def eitherToF[F[_], A](either: Either[String, A])(implicit E: ApplicativeHandle[F, String]): F[A] =
    either.fold(_.raise, E.applicative.pure)

  /**
    * Matches a literal string
    * @param lit The string to match
    * @param caseSensitive If the match should be case insensitive
    */
  def literal(lit: String, caseSensitive: Boolean = true): MessageParser[String] = new MessageParser[String] {

    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[String] = {
      def matchesLit(s: String) = if (caseSensitive) s == lit else s.equalsIgnoreCase(lit)

      S.get.flatMap {
        case Nil                        => E.raise("No more arguments left")
        case s :: tail if matchesLit(s) => S.set(tail).as(lit)
        case head :: _                  => E.raise(s"Was expecting $lit, but found $head")
      }
    }
  }

  /**
    * Matches a string that starts with a prefix
    * @param prefix The prefix to look for
    * @param caseSensitive If the matching should be case sensitive
    * @param consumeAll If the whole string should be consumed if only part of it was matched
    */
  def startsWith(prefix: String, caseSensitive: Boolean = true, consumeAll: Boolean = false): MessageParser[String] =
    new MessageParser[String] {
      override def parse[F[_]](
          implicit c: CacheSnapshot,
          F: Monad[F],
          E: ApplicativeHandle[F, String],
          S: MonadState[F, List[String]]
      ): F[String] = {
        val usedPrefix = if (caseSensitive) prefix else prefix.toLowerCase(Locale.ROOT)
        def startsWithPrefix(s: String) =
          if (caseSensitive) s.startsWith(usedPrefix) else s.toLowerCase(Locale.ROOT).startsWith(usedPrefix)

        S.get.flatMap {
          case Nil => E.raise("No more arguments left")
          case head :: tail if startsWithPrefix(head) =>
            val newState =
              if (consumeAll || prefix.length == head.length) tail
              else head.substring(prefix.length) :: tail

            S.set(newState).as(prefix)
          case head :: _ => E.raise(s"Was expecting something starting with $prefix, but found $head")
        }
      }
    }

  /**
    * Matches one of the strings passed in
    * @param seq The strings to try
    * @param caseSensitive If the match should be case sensitive
    */
  def oneOf(seq: Seq[String], caseSensitive: Boolean = true): MessageParser[String] = new MessageParser[String] {
    private val valid = seq.map(s => if (caseSensitive) s else s.toLowerCase(Locale.ROOT)).toSet

    private def validContains(s: String) = valid.contains(if (caseSensitive) s else s.toLowerCase(Locale.ROOT))

    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[String] = S.get.flatMap {
      case Nil                                 => E.raise("No more arguments left")
      case head :: tail if validContains(head) => S.set(tail).as(head)
      case head :: _ =>
        val err =
          if (valid.size <= 5) s"Was expecting one of ${seq.mkString(", ")}, but found $head"
          else s"$head did not match one of the valid choices"
        E.raise(err)
    }
  }

  /**
    * Create a parser from a string
    * @param f The function to transform the string with
    * @tparam A The type to parse
    */
  def fromString[A](f: String => A): MessageParser[A] = new MessageParser[A] {
    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[A] = S.get.flatMap {
      case Nil          => E.raise("No more arguments left")
      case head :: tail => S.set(tail).as(f(head))
    }
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
  def fromTry[A](f: String => Try[A]): MessageParser[A] = fromEither(f(_).toEither.leftMap(_.getMessage))

  /**
    * Parse a string into a try, where the left contains the error message
    * @param f The function to transform the string with.
    * @tparam A The type to parse
    */
  def fromEither[A](f: String => Either[String, A]): MessageParser[A] = new MessageParser[A] {

    /**
      * A program to parse a message into the needed types.
      */
    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[A] = S.get.flatMap {
      case Nil => E.raise("No more arguments left")
      case head :: tail =>
        S.set(tail).as(f(head)).flatMap {
          case Right(value) => value.pure
          case Left(e)      => e.raise
        }
    }
  }

  /**
    * Same as [[withTry]] but with a custom error.
    * @param errorMessage The error message to use
    * @param f The function to transform the string with.
    * @tparam A The type to parse
    */
  def withTryCustomError[A](errorMessage: String => String)(f: String => A): MessageParser[A] =
    fromEither(s => Try(f(s)).toEither.leftMap(_ => errorMessage(s)))

  implicit val remainingStringParser: MessageParser[MessageParser.RemainingAsString] =
    new MessageParser[RemainingAsString] {
      override def parse[F[_]](
          implicit c: CacheSnapshot,
          F: Monad[F],
          E: ApplicativeHandle[F, String],
          S: MonadState[F, List[String]]
      ): F[RemainingAsString] = S.get.map(s => RemainingAsString(s.mkString(" "))) <* S.set(Nil)
    }

  implicit val stringParser: MessageParser[String] = fromString(identity)
  implicit val byteParser: MessageParser[Byte]     = withTryCustomError(s => s"$s is not a valid number")(_.toByte)
  implicit val shortParser: MessageParser[Short]   = withTryCustomError(s => s"$s is not a valid number")(_.toShort)
  implicit val intParser: MessageParser[Int]       = withTryCustomError(s => s"$s is not a valid number")(_.toInt)
  implicit val longParser: MessageParser[Long]     = withTryCustomError(s => s"$s is not a valid number")(_.toLong)
  implicit val floatParser: MessageParser[Float] =
    withTryCustomError(s => s"$s is not a valid decimal number")(_.toFloat)
  implicit val doubleParser: MessageParser[Double] =
    withTryCustomError(s => s"$s is not a valid decimal number")(_.toDouble)
  implicit val booleanParser: MessageParser[Boolean] =
    withTryCustomError(s => s"$s is not a valid boolean")(_.toBoolean)

  val userRegex: Regex    = """<@!?(\d+)>""".r
  val channelRegex: Regex = """<#(\d+)>""".r
  val roleRegex: Regex    = """<@&(\d+)>""".r
  val emojiRegex: Regex   = """<:\w+:(\d+)>""".r

  private def snowflakeParser[C](
      name: String,
      regex: Regex,
      getObj: (CacheSnapshot, SnowflakeType[C]) => Option[C]
  ): MessageParser[C] = new MessageParser[C] {
    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[C] = S.get.flatMap {
      case Nil => "No more arguments left".raise
      case head :: tail =>
        val optMatch = regex
          .findFirstMatchIn(head)
          .filter(m => m.start == 0 && m.end == head.length)
          .toRight(s"Invalid $name specified")

        val res = eitherToF(
          optMatch.flatMap { m =>
            val snowflake = _root_.ackcord.data.SnowflakeType[C](RawSnowflake(m.group(1)))
            getObj(c, snowflake).toRight(s"${name.capitalize} not found")
          }
        )

        res <* S.set(tail)
    }
  }

  implicit val userParser: MessageParser[User] =
    snowflakeParser(
      "user",
      userRegex,
      _.getUser(_)
    )
  implicit val channelParser: MessageParser[Channel] =
    snowflakeParser(
      "channel",
      channelRegex,
      _.getChannel(_)
    )
  implicit val roleParser: MessageParser[Role] =
    snowflakeParser(
      "role",
      roleRegex,
      _.getRole(_)
    )
  implicit val emojiParser: MessageParser[Emoji] =
    snowflakeParser(
      "emoji",
      emojiRegex,
      _.getEmoji(_)
    )

  implicit val textChannelParser: MessageParser[TextChannel] =
    channelParser.collectWithError("Passed in channel is not a text channel") {
      case channel: TextChannel => channel
    }

  implicit val guildChannelParser: MessageParser[GuildChannel] =
    channelParser.collectWithError("Passed in channel is not a guild channel") {
      case channel: GuildChannel => channel
    }

  implicit val textGuildChannelParser: MessageParser[TextGuildChannel] =
    channelParser.collectWithError("Passed in channel is not a guild text channel") {
      case channel: TextGuildChannel => channel
    }

  /**
    * A parser that will return all the strings passed to it.
    */
  val allStringsParser: MessageParser[List[String]] = new MessageParser[List[String]] {
    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[List[String]] = S.get <* S.set(Nil)
  }

  /**
    * A parser that will only succeed if there are no strings left.
    */
  implicit val notUsedParser: MessageParser[NotUsed] = new MessageParser[NotUsed] {
    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[NotUsed] =
      S.inspect(_.isEmpty)
        .ifM(
          F.pure(NotUsed),
          S.get.flatMap(args => E.raise(s"Found dangling arguments: ${args.mkString(", ")}"))
        )
  }

  implicit def optional[A](implicit parser: MessageParser[A]): MessageParser[Option[A]] =
    new MessageParser[Option[A]] {
      override def parse[F[_]](
          implicit c: CacheSnapshot,
          F: Monad[F],
          E: ApplicativeHandle[F, String],
          S: MonadState[F, List[String]]
      ): F[Option[A]] =
        parser.parse.map[Option[A]](Some.apply).handle[String](_ => None)

    }

  def orElseWith[A, B, C](parse1: MessageParser[A], parse2: MessageParser[B])(
      combine: Either[A, B] => C
  ): MessageParser[C] =
    new MessageParser[C] {
      override def parse[F[_]](
          implicit c: CacheSnapshot,
          F: Monad[F],
          E: ApplicativeHandle[F, String],
          S: MonadState[F, List[String]]
      ): F[C] =
        E.handleWith(parse1.parse[F].map(a => combine(Left(a))))(_ => parse2.parse[F].map(b => combine(Right(b))))
    }
}

trait DeriveMessageParser {
  import shapeless._
  implicit lazy val hNilParser: MessageParser[HNil] = Monad[MessageParser].pure(HNil)

  implicit def hListParser[Head, Tail <: HList](
      implicit headParser: Lazy[MessageParser[Head]],
      tailParser: Lazy[MessageParser[Tail]]
  ): MessageParser[Head :: Tail] = new MessageParser[Head :: Tail] {
    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[Head :: Tail] =
      for {
        h <- headParser.value.parse
        t <- tailParser.value.parse
      } yield h :: t

  }

  implicit val cNilParser: MessageParser[CNil] = new MessageParser[CNil] {
    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[CNil] = throw new IllegalStateException("Tried to parse CNil")
  }

  implicit def coProductParser[Head, Tail <: Coproduct](
      implicit headParser: Lazy[MessageParser[Head]],
      tailParser: Lazy[MessageParser[Tail]]
  ): MessageParser[Head :+: Tail] = new MessageParser[Head :+: Tail] {
    override def parse[F[_]](
        implicit c: CacheSnapshot,
        F: Monad[F],
        E: ApplicativeHandle[F, String],
        S: MonadState[F, List[String]]
    ): F[Head :+: Tail] = {
      val head2 = headParser.value.parse.map[Head :+: Tail](Inl.apply)
      val tail2 = tailParser.value.parse.map[Head :+: Tail](Inr.apply)

      head2.handleWith[String](_ => tail2)
    }
  }

  object Auto {
    implicit def deriveParser[A, Repr](
        implicit gen: Generic.Aux[A, Repr],
        ser: Lazy[MessageParser[Repr]]
    ): MessageParser[A] = ser.value.map(gen.from)
  }
}
