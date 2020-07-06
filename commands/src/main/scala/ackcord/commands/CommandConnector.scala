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

import scala.concurrent.Future

import ackcord.CacheSnapshot
import ackcord.data.{Message, User}
import ackcord.requests.{Requests, SupervisionStreams}
import ackcord.syntax._
import ackcord.util.StreamBalancer
import akka.NotUsed
import akka.stream.SourceShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition, RunnableGraph, Source}
import cats.instances.future._
import cats.syntax.all._
import cats.{Monad, MonadError}

/**
  * @param messages A source of messages and their cache. All the messages
  *                 that will be considered for commands.
  * @param requests A request helper to send errors and pass to command
  *                 messages
  * @param parallelism How many command messages are constructed at once.
  */
class CommandConnector(
    messages: Source[(Message, CacheSnapshot), NotUsed],
    requests: Requests,
    parallelism: Int
) {
  import requests.system
  import requests.system.executionContext

  //https://stackoverflow.com/questions/249791/regex-for-quoted-string-with-escaping-quotes
  private val quotedRegex = """(?:"((?:[^"\\]|\\.)+)")|((?:\S)+)""".r

  private def stringToArgsQuoted(arguments: String): List[String] = {
    if (arguments.isEmpty) Nil
    else {
      quotedRegex
        .findAllMatchIn(arguments)
        .map { m =>
          val quoted = m.group(1) != null
          val group  = if (quoted) 1 else 2
          m.group(group)
        }
        .toList
    }
  }

  type PrefixParser = (CacheSnapshot, Message) => Future[MessageParser[Unit]]

  private val mentionParser: (CacheSnapshot, Message) => MessageParser[Unit] = { (cache, message) =>
    val botUser = cache.botUser

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

  /**
    * Creates a prefix parser for a command.
    * @param symbol The symbol to parse before the command.
    * @param aliases A list of aliases for this command.
    * @param mustMention If the command requires a mention before the prefix and alias.
    */
  def prefix(
      symbol: String,
      aliases: Seq[String],
      mustMention: Boolean = true,
      caseSensitive: Boolean = false
  ): PrefixParser = {
    val messageParserF = MessageParser.messageParserMonad

    val first: (CacheSnapshot, Message) => MessageParser[Unit] =
      if (mustMention) mentionParser
      else (_, _) => messageParserF.unit

    Function.untupled(
      first.tupled
        .andThen(_ *> MessageParser.startsWith(symbol) *> MessageParser.oneOf(aliases, caseSensitive).void)
        .andThen(Future.successful)
    )
  }

  /**
    * Composes a command's flow and the source of messages to consider
    * for commands.
    * @param prefix A prefix parser to use for this command. In most cases
    *               create it by calling [[prefix]].
    * @param command A command the compose with.
    * @tparam A The type of arguments this command uses.
    * @tparam Mat The materialized result of running the command graph.
    * @return A source of command errors that can be used however you want.
    */
  def newCommandWithErrors[A, Mat](
      prefix: PrefixParser,
      command: ComplexCommand[A, Mat]
  ): Source[CommandError, CommandRegistration[Mat]] = {
    val messageSource = messages.mapAsyncUnordered(parallelism) {
      case t @ (message, cache) =>
        prefix(cache, message).tupleLeft(t)
    }

    val getCommandMessages = Flow[((Message, CacheSnapshot), MessageParser[Unit])].mapConcat {
      case ((message, cache), prefixParser) =>
        implicit val c: CacheSnapshot = cache

        val res = for {
          args    <- MessageParser.parseEither(stringToArgsQuoted(message.content), prefixParser).map(_._1).toOption
          channel <- message.channelId.resolve
        } yield MessageParser
          .parseResultEither(args, command.parser)
          .map(a => CommandMessage.Default(requests, cache, channel, message, a): CommandMessage[A])
          .leftMap(e => CommandError(e, channel, cache))

        res.toList
    }

    val commandMessageSource = messageSource.via(StreamBalancer.balanceMerge(parallelism, getCommandMessages))

    CommandRegistration.withRegistration(
      Source.fromGraph(
        GraphDSL.create(SupervisionStreams.logAndContinue(command.flow)) { implicit b => thatFlow =>
          import GraphDSL.Implicits._

          val selfSource = b.add(commandMessageSource)

          val selfPartition = b.add(
            Partition[Either[CommandError, CommandMessage[A]]](
              2,
              {
                case Left(_)  => 0
                case Right(_) => 1
              }
            )
          )
          val selfErr = selfPartition.out(0).map(_.swap.getOrElse(sys.error("impossible")))
          val selfOut = selfPartition.out(1).map(_.getOrElse(sys.error("impossible")))

          val resMerge = b.add(Merge[CommandError](2))

          // format: OFF
            selfSource ~> selfPartition
            selfOut ~> thatFlow ~> resMerge
            selfErr ~>             resMerge
            // format: ON

          SourceShape(resMerge.out)
        }
      )
    )
  }

  /**
    * Composes a named command's flow and the source of messages to consider
    * for commands.
    * @param command A named command the compose with.
    * @tparam A The type of arguments this command uses.
    * @tparam Mat The materialized result of running the command graph.
    * @return A source of command errors that can be used however you want.
    */
  def newNamedCommandWithErrors[A, Mat](
      command: NamedComplexCommand[A, Mat]
  ): Source[CommandError, CommandRegistration[Mat]] =
    newCommandWithErrors(
      prefix(command.symbol, command.aliases, command.requiresMention, command.caseSensitive),
      command
    )

  /**
    * Creates a [[RunnableGraph]] for a command.
    * @param prefix The prefix to parse before the command.
    * @param command The command to use when creating the graph.
    * @tparam A The type of arguments this command uses.
    * @tparam Mat The materialized result of running the command graph.
    * @return A runnable graph representing the execution of this command.
    *         Errors are sent as messages to the channel the command was used
    *         from.
    * @see [[newCommandWithErrors]]
    */
  def newCommand[A, Mat](
      prefix: PrefixParser,
      command: ComplexCommand[A, Mat]
  ): RunnableGraph[CommandRegistration[Mat]] = {
    SupervisionStreams.addLogAndContinueFunction(
      newCommandWithErrors(prefix, command)
        .map {
          case CommandError(error, channel, _) =>
            channel.sendMessage(error)
        }
        .to(requests.sinkIgnore)
        .addAttributes
    )
  }

  /**
    * Creates a [[RunnableGraph]] for a named command.
    * @param command The named command to use when creating the graph.
    * @tparam A The type of arguments this command uses.
    * @tparam Mat The materialized result of running the command graph.
    * @return A runnable graph representing the execution of this command.
    *         Errors are sent as messages to the channel the command was used
    *         from.
    * @see [[newCommandWithErrors]]
    */
  def newNamedCommand[A, Mat](command: NamedComplexCommand[A, Mat]): RunnableGraph[CommandRegistration[Mat]] =
    newCommand(prefix(command.symbol, command.aliases, command.requiresMention, command.caseSensitive), command)

  /**
    * Starts a command execution.
    * @param prefix The prefix to use for the command.
    * @param command The command to run.
    * @tparam A The type of arguments this command uses.
    * @tparam Mat The materialized result of running the command graph.
    * @return The materialized result of running the command, in addition to
    *         a future signaling when the command is done running.
    */
  def runNewCommand[A, Mat](prefix: PrefixParser, command: ComplexCommand[A, Mat]): CommandRegistration[Mat] =
    newCommand(prefix, command).run()

  /**
    * Starts a named command execution.
    * @param command The named command to run.
    * @tparam A The type of arguments this command uses.
    * @tparam Mat The materialized result of running the command graph.
    * @return The materialized result of running the command, in addition to
    *         a future signaling when the command is done running.
    */
  def runNewNamedCommand[A, Mat](command: NamedComplexCommand[A, Mat]): CommandRegistration[Mat] =
    runNewCommand(prefix(command.symbol, command.aliases, command.requiresMention, command.caseSensitive), command)

  /**
    * Starts many named commands at the same time. They must all have a
    * materialized value of NotUsed.
    * @param commands The commands to run.
    * @return The commands together with their completions.
    */
  def bulkRunNamed(commands: NamedCommand[_]*): Seq[(NamedCommand[_], CommandRegistration[_])] =
    commands.map(c => c -> runNewNamedCommand(c))
}
