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

import scala.concurrent.{ExecutionContext, Future}

import ackcord.CacheSnapshot
import ackcord.data.{Message, User}
import cats.{Monad, MonadError}
import cats.syntax.all._

trait PrefixParser {

  def apply(message: Message)(implicit c: CacheSnapshot, ec: ExecutionContext): Future[MessageParser[Unit]]

  def showInvocation(
      message: Message
  )(implicit c: CacheSnapshot, ec: ExecutionContext): Future[Option[String]] = Future.successful(None)
}
object PrefixParser {

  def structured(
      needsMention: Boolean,
      symbols: Seq[String],
      aliases: Seq[String],
      caseSensitive: Boolean = false
  ): StructuredPrefixParser =
    StructuredPrefixParser(
      (_, _) => Future.successful(needsMention),
      (_, _) => Future.successful(symbols),
      (_, _) => Future.successful(aliases),
      (_, _) => Future.successful(caseSensitive),
      (_, _) => Future.successful(true)
    )

  def structuredFunction(
      needsMention: (CacheSnapshot, Message) => Boolean,
      symbols: (CacheSnapshot, Message) => Seq[String],
      aliases: (CacheSnapshot, Message) => Seq[String],
      caseSensitive: (CacheSnapshot, Message) => Boolean = (_, _) => false,
      canExecute: (CacheSnapshot, Message) => Boolean = (_, _) => true
  ): StructuredPrefixParser =
    StructuredPrefixParser(
      Function.untupled(needsMention.tupled.andThen(Future.successful)),
      Function.untupled(symbols.tupled.andThen(Future.successful)),
      Function.untupled(aliases.tupled.andThen(Future.successful)),
      Function.untupled(caseSensitive.tupled.andThen(Future.successful)),
      Function.untupled(canExecute.tupled.andThen(Future.successful))
    )

  def structuredAsync(
      needsMention: (CacheSnapshot, Message) => Future[Boolean],
      symbols: (CacheSnapshot, Message) => Future[Seq[String]],
      aliases: (CacheSnapshot, Message) => Future[Seq[String]],
      caseSensitive: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(false),
      canExecute: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(true)
  ): StructuredPrefixParser = StructuredPrefixParser(needsMention, symbols, aliases, caseSensitive, canExecute)

  def fromFunctionAsync(f: (CacheSnapshot, Message) => Future[MessageParser[Unit]]): PrefixParser =
    new PrefixParser {
      override def apply(
          message: Message
      )(implicit c: CacheSnapshot, ec: ExecutionContext): Future[MessageParser[Unit]] = f(c, message)
    }

  def fromFunction(f: (CacheSnapshot, Message) => MessageParser[Unit]): PrefixParser =
    new PrefixParser {
      override def apply(
          message: Message
      )(implicit c: CacheSnapshot, ec: ExecutionContext): Future[MessageParser[Unit]] =
        Future.successful(f(c, message))
    }
}

/**
  * Represents information about how a command can be invoked in a structural way.
  * @param needsMention If the command needs a mention
  * @param symbols The valid prefix symbols for the command
  * @param aliases The aliases for the command
  */
case class StructuredPrefixParser(
    needsMention: (CacheSnapshot, Message) => Future[Boolean],
    symbols: (CacheSnapshot, Message) => Future[Seq[String]],
    aliases: (CacheSnapshot, Message) => Future[Seq[String]],
    caseSensitive: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(false),
    canExecute: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(true)
) extends PrefixParser {

  override def apply(message: Message)(implicit c: CacheSnapshot, ec: ExecutionContext): Future[MessageParser[Unit]] =
    for {
      execute           <- canExecute(c, message)
      mentionHere       <- needsMention(c, message)
      symbolsHere       <- symbols(c, message)
      aliasesHere       <- aliases(c, message)
      caseSensitiveHere <- caseSensitive(c, message)
    } yield {
      if (execute) {
        val mentionParser: MessageParser[Unit] = {
          val botUser = c.botUser

          //We do a quick check first before parsing the message
          val quickCheck = message.mentions.contains(botUser.id)
          lazy val err   = MonadError[MessageParser, String].raiseError[Unit]("")

          if (quickCheck) {
            MessageParser[User].flatMap { user =>
              if (user == botUser) Monad[MessageParser].unit
              else err
            }
          } else err
        }

        val mention =
          if (mentionHere) mentionParser
          else MessageParser.unit

        val symbols = MessageParser.oneOf(symbolsHere.map(MessageParser.startsWith(_)))
        val aliases = MessageParser.oneOf(aliasesHere.map(MessageParser.literal(_, caseSensitiveHere)))

        mention *> symbols *> aliases.void
      } else {
        MessageParser.fail("Can't execute")
      }
    }

  override def showInvocation(
      message: Message
  )(implicit c: CacheSnapshot, ec: ExecutionContext): Future[Option[String]] = for {
    execute     <- canExecute(c, message)
    mentionHere <- needsMention(c, message)
    symbolsHere <- symbols(c, message)
    aliasesHere <- aliases(c, message)
  } yield {
    if (execute) {
      val mention = if (mentionHere) s"${c.botUser.mention} " else ""
      val symbol  = if (symbolsHere.length > 1) symbolsHere.mkString("(", "|", ")") else symbolsHere.head
      val alias   = if (aliasesHere.length > 1) aliasesHere.mkString("(", "|", ")") else aliasesHere.head

      Some(mention + symbol + alias)
    } else None
  }
}
