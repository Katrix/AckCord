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
package net.katsstuff.ackcord.commands

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Source}
import akka.{Done, NotUsed}
import net.katsstuff.ackcord.{APIMessage, Cache}
import net.katsstuff.ackcord.data.CacheSnapshot
import net.katsstuff.ackcord.util.MessageParser

/**
  * Represents a command handler, which will try to parse commands with
  * categories it's been told about.
  * @param subscribe A source that represents the parsed commands. Can be
  *                  materialized as many times as needed.
  * @param categories The categories this handler knows about.
  * @param token The bot token.
  */
case class Commands(subscribe: Source[RawCmdMessage, NotUsed], categories: Set[CmdCategory], token: String) {

  /**
    * Subscribe to a specific command using a category, aliases, and filters.
    * @return A source representing the individual command.
    */
  def subscribeCmd(
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Seq.empty
  ): Source[CmdMessage, NotUsed] = {
    require(categories.contains(category), "Tried to register command with wrong category")
    subscribe.collect {
      case cmd @ RawCmd(msg, `category`, command, args, c) if aliases.contains(command) =>
        implicit val cache: CacheSnapshot = c
        val filtersNotPassed = filters.filterNot(_.isAllowed(msg))
        if (filtersNotPassed.isEmpty) Cmd(msg, args, c)
        else FilteredCmd(filtersNotPassed, cmd)
    }
  }

  /**
    * Subscribe to a specific command using a category, aliases, filters,
    * and a parser.
    * @return A source representing the individual parsed command.
    */
  def subscribeCmdParsed[A](category: CmdCategory, aliases: Seq[String], filters: Seq[CmdFilter] = Seq.empty)(
      implicit parser: MessageParser[A]
  ): Source[ParsedCmdMessage[A], NotUsed] = subscribeCmd(category, aliases, filters).collect {
    case cmd: Cmd =>
      parser
        .parse(cmd.args)(cmd.cache)
        .fold(e => CmdParseError(cmd.msg, e, cmd.cache), res => ParsedCmd(cmd.msg, res._2, res._1, cmd.cache))
    case filtered: FilteredCmd => filtered
  }

  /**
    * Subscribe to a command using a unparsed command factory.
    */
  def subscribe[Mat, Mat2](
      factory: BaseCmdFactory[Mat]
  )(combine: (Future[Done], Mat) => Mat2)(implicit system: ActorSystem, mat: Materializer): Mat2 =
    subscribeCmd(factory.category, factory.lowercaseAliases, factory.filters)
      .via(CmdStreams.handleErrorsUnparsed(token))
      .watchTermination()(Keep.right)
      .toMat(factory.sink(token, system, mat))(combine)
      .run()

  /**
    * Subscribe to a command using a parsed command factory.
    */
  def subscribe[A, Mat, Mat2](
      factory: ParsedCmdFactory[A, Mat]
  )(combine: (Future[Done], Mat) => Mat2)(implicit system: ActorSystem, mat: Materializer): Mat2 =
    subscribeCmdParsed(factory.category, factory.lowercaseAliases, factory.filters)(factory.parser)
      .via(CmdStreams.handleErrorsParsed(token))
      .watchTermination()(Keep.right)
      .toMat(factory.sink(token, system, mat))(combine)
      .run()
}
object Commands {

  /**
    * Create a new command handler using a cache.
    * @param needMention If this handler should require mentions before
    *                    the commands.
    * @param categories The categories this handler should know about.
    * @param cache The cache to use for subscribing to created messages.
    * @param token The bot token.
    */
  def create(needMention: Boolean, categories: Set[CmdCategory], cache: Cache, token: String)(
      implicit system: ActorSystem,
      mat: Materializer
  ) = Commands(CmdStreams.cmdStreams(needMention, categories, cache.subscribeAPI), categories, token)

  /**
    * Create a new command handler using an [[APIMessage]] source.
    * @param needMention If this handler should require mentions before
    *                    the commands.
    * @param categories The categories this handler should know about.
    * @param apiMessages The source of [[APIMessage]]s.
    * @param token The bot token.
    */
  def create(
      needMention: Boolean,
      categories: Set[CmdCategory],
      apiMessages: Source[APIMessage, NotUsed],
      token: String
  )(implicit system: ActorSystem, mat: Materializer) =
    Commands(CmdStreams.cmdStreams(needMention, categories, apiMessages), categories, token)
}
