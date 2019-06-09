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
package ackcord.newcommands

import ackcord.CacheSnapshot
import ackcord.data.{Message, User}
import ackcord.requests.RequestHelper
import ackcord.util.Streamable
import ackcord.syntax._
import akka.stream.SourceShape
import akka.stream.scaladsl.{GraphDSL, Keep, Merge, Partition, RunnableGraph, Source}
import akka.{Done, NotUsed}
import cats.Monad
import cats.syntax.all._

import scala.concurrent.Future
import scala.language.higherKinds

/**
  *
  * @param messages A source of messages and their cache. All the messages
  *                 that will be considered for commands.
  * @param requests A request helper to send errors and pass to command
  *                 messages
  * @tparam F The cache effect type
  */
class CommandConnector[F[_]: Streamable: Monad](
    messages: Source[(Message, CacheSnapshot[F]), NotUsed],
    requests: RequestHelper
) {

  type PrefixParser = (CacheSnapshot[F], Message) => F[MessageParser[Unit]]

  /**
    * Creates a prefix parser for a command.
    * @param symbol The symbol to parse before the command.
    * @param aliases A list of aliases for this command.
    * @param mustMention If the command must need a mention before the prefix and alias.
    */
  def prefix(
      symbol: String,
      aliases: Seq[String],
      mustMention: Boolean
  ): PrefixParser = {
    val mentionParser: (CacheSnapshot[F], Message) => F[MessageParser[Unit]] = if (mustMention) { (cache, message) =>
      cache.botUser.map { botUser =>
        //We do a quick check first before parsing the message
        val quickCheck = message.mentions.contains(botUser.id)
        lazy val err =
          MessageParser.messageParserMonad.raiseError[Unit]("You need to use a mention to use this command")

        if (quickCheck) {
          MessageParser[User].flatMap { user =>
            if (user == botUser) MessageParser.messageParserMonad.unit
            else err
          }
        } else err
      }
    } else { (_, _) =>
      Monad[F].pure(MessageParser.messageParserMonad.unit)
    }

    Function.untupled(
      mentionParser.tupled.andThen(_.map(_ *> MessageParser.startsWith(symbol) *> MessageParser.oneOf(aliases).void))
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
      command: Command[F, A, Mat]
  ): Source[CommandError[F], (Mat, Future[Done])] = {
    val commandMessageSource = messages
      .flatMapConcat(t => Streamable[F].toSource(prefix(t._2, t._1).tupleLeft(t)))
      .flatMapConcat {
        case ((message, cache), prefixParser) =>
          implicit val c: CacheSnapshot[F] = cache

          val parsed = MessageParser.parseEitherT(message.content.split(" ").toList, prefixParser).map(_._1).toOption
          Streamable[F].optionToSource(parsed.map((message, cache, _)))
      }
      .flatMapConcat {
        case (message, cache, args) =>
          implicit val c: CacheSnapshot[F] = cache

          val commandMessageF = message.channelId.tResolve[F].value.flatMap { channel =>
            MessageParser
              .parseResultEitherT(args, command.parser)
              .map { a =>
                CommandMessage.Default(requests, cache, channel.get, message, a): CommandMessage[F, A]
              }
              .leftMap(e => CommandError(e, channel.get, cache))
              .value
          }

          Streamable[F].toSource(commandMessageF)
      }

    Source.fromGraph(GraphDSL.create(command.flow.watchTermination()(Keep.both)) { implicit b => thatFlow =>
      import GraphDSL.Implicits._

      val selfSource = b.add(commandMessageSource)

      val selfPartition = b.add(Partition[Either[CommandError[F], CommandMessage[F, A]]](2, {
        case Left(_)  => 0
        case Right(_) => 1
      }))
      val selfErr = selfPartition.out(0).map(_.left.get)
      val selfOut = selfPartition.out(1).map(_.right.get)

      val resMerge = b.add(Merge[CommandError[F]](2))

      // format: OFF
      selfSource ~> selfPartition
                    selfOut       ~> thatFlow ~> resMerge
                    selfErr       ~>             resMerge
      // format: ON

      SourceShape(resMerge.out)
    })
  }

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
  def newCommand[A, Mat](prefix: PrefixParser, command: Command[F, A, Mat]): RunnableGraph[(Mat, Future[Done])] =
    newCommandWithErrors(prefix, command)
      .map {
        case CommandError(error, channel, _) =>
          channel.sendMessage(error)
      }
      .to(requests.sinkIgnore)

  /**
    * Starts a command execution.
    * @param prefix The prefix to use for the command.
    * @param command The command to run.
    * @tparam A The type of arguments this command uses.
    * @tparam Mat The materialized result of running the command graph.
    * @return The materialized result of running the command, in addition to
    *         a future signaling when the command is done running.
    */
  def runNewCommand[A, Mat](prefix: PrefixParser, command: Command[F, A, Mat]): (Mat, Future[Done]) = {
    import requests.mat
    newCommand(prefix, command).run()
  }
}
