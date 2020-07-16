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

import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.collection.mutable

import ackcord.CacheSnapshot
import ackcord.data.Message
import ackcord.requests.{CreateMessage, CreateMessageData, Requests}
import akka.{Done, NotUsed}
import cats.data.OptionT
import cats.instances.future._
import cats.syntax.all._

/**
  * The basic structure for a help command. Only accepts commands that use
  * [[StructuredPrefixParser]].
  */
abstract class HelpCommand(requests: Requests) extends CommandController(requests) {
  import requests.system.executionContext

  protected val registeredCommands: mutable.Set[HelpCommand.HelpCommandEntry] =
    ConcurrentHashMap.newKeySet[HelpCommand.HelpCommandEntry]().asScala

  val command: ComplexCommand[Option[HelpCommand.Args], NotUsed] =
    Command.parsing[Option[HelpCommand.Args]].asyncOptRequest { implicit m =>
      m.parsed match {
        case Some(HelpCommand.Args.CommandArgs(query)) =>
          OptionT
            .liftF(filterCommands(query))
            .semiflatMap(createSearchReply(m.message, query, _))
            .map(CreateMessage(m.textChannel.id, _))
        case Some(HelpCommand.Args.PageArgs(page)) =>
          OptionT.liftF(createReplyAll(m.message, page)).map(CreateMessage(m.textChannel.id, _))
        case None =>
          OptionT.liftF(createReplyAll(m.message, 0)).map(CreateMessage(m.textChannel.id, _))
      }
    }

  def registerCommand(prefix: StructuredPrefixParser, description: CommandDescription, done: Future[Done]): Unit = {
    val entry = HelpCommand.HelpCommandEntry(prefix, description)
    registeredCommands.add(entry)

    done.foreach(_ => removeEntry(entry))
  }

  protected def filterCommands(
      query: String
  )(implicit m: UserCommandMessage[_]): Future[Seq[HelpCommand.HelpCommandProcessedEntry]] = {
    Future
      .traverse(registeredCommands)(entry => entry.prefixParser.canExecute(m.cache, m.message).map(entry -> _))
      .flatMap { entries =>
        Future.traverse(entries.collect { case (entry, execute) if execute => entry })(entry =>
          entry.prefixParser.symbols(m.cache, m.message).map(entry -> _)
        )
      }
      .flatMap { withSymbols =>
        val entriesWithSymbolMatch = withSymbols.filter(_._2.exists(query.startsWith))
        Future.traverse(entriesWithSymbolMatch) {
          case (entry, symbols) =>
            entry.prefixParser.aliases(m.cache, m.message).map(aliases => (entry, symbols, aliases))
        }
      }
      .flatMap { withAliases =>
        val entriesWithAliasMatch = withAliases.filter {
          case (_, symbols, aliases) =>
            val symbolsWithLength = symbols.map(_.length)
            aliases.exists(alias => symbolsWithLength.exists(query.startsWith(alias, _)))
        }
        Future.traverse(entriesWithAliasMatch) {
          case (entry, symbols, aliases) =>
            entry.prefixParser
              .needsMention(m.cache, m.message)
              .zipWith(entry.prefixParser.caseSensitive(m.cache, m.message)) { (needsMention, caseSensitive) =>
                HelpCommand.HelpCommandProcessedEntry(needsMention, symbols, aliases, caseSensitive, entry.description)
              }

        }
      }
      .map(_.toSeq)
  }

  protected def removeEntry(entry: HelpCommand.HelpCommandEntry): Unit =
    registeredCommands.remove(entry)

  /**
    * Create a reply for a search result
    * @param matches All the commands that matched the arguments
    * @return Data to create a message describing the search
    */
  def createSearchReply(message: Message, query: String, matches: Seq[HelpCommand.HelpCommandProcessedEntry])(
      implicit c: CacheSnapshot
  ): Future[CreateMessageData]

  /**
    * Create a reply for all the commands tracked by this help command.
    * @param page The page to use. Starts at 0.
    * @return Data to create a message describing the commands tracked
    *         by this help command.
    */
  def createReplyAll(message: Message, page: Int)(implicit c: CacheSnapshot): Future[CreateMessageData]

  def unknownCmd(command: String): Option[CreateMessageData] =
    Some(CreateMessageData(s"Unknown command $command"))
}
object HelpCommand {
  case class HelpCommandEntry(prefixParser: StructuredPrefixParser, description: CommandDescription)
  case class HelpCommandProcessedEntry(
      needsMention: Boolean,
      symbols: Seq[String],
      aliases: Seq[String],
      caseSensitive: Boolean,
      description: CommandDescription
  )

  sealed trait Args
  object Args {
    case class CommandArgs(command: String) extends Args
    case class PageArgs(page: Int)          extends Args

    //We write out the parser ourself as string parses any string
    implicit val parser: MessageParser[Args] =
      MessageParser.intParser.map(PageArgs).orElse(MessageParser.stringParser.map(CommandArgs)).map(_.merge)
  }
}
