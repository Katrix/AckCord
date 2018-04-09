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
package net.katsstuff.ackcord.commands

import scala.concurrent.Future
import scala.language.higherKinds

import akka.stream.scaladsl.{Keep, Source}
import akka.{Done, NotUsed}
import cats.syntax.functor._
import cats.{Monad, Traverse}
import net.katsstuff.ackcord.http.requests.RequestHelper
import net.katsstuff.ackcord.util.{MessageParser, Streamable}
import net.katsstuff.ackcord.CacheSnapshotLike

/**
  * Represents a command handler, which will try to parse commands with
  * categories it's been told about.
  * @param subscribe A source that represents the parsed commands. Can be
  *                  materialized as many times as needed.
  * @param categories The categories this handler knows about.
  * @param requests A request helper object which will be passed to handlers.
  */
case class Commands[F[_]](
    subscribe: Source[RawCmdMessage[F], NotUsed],
    categories: Set[CmdCategory],
    requests: RequestHelper
) {
  import requests.mat

  /**
    * Subscribe to a specific command using a category, aliases, and filters.
    * @return A source representing the individual command.
    */
  def subscribeCmd(category: CmdCategory, aliases: Seq[String], filters: Seq[CmdFilter] = Seq.empty)(
      implicit streamable: Streamable[F],
      F: Monad[F]
  ): Source[CmdMessage[F], NotUsed] = {
    require(categories.contains(category), "Tried to register command with wrong category")
    subscribe
      .collect {
        case cmd @ RawCmd(msg, `category`, command, args, c) if aliases.contains(command) =>
          import cats.instances.list._
          implicit val cache: CacheSnapshotLike[F] = c
          Traverse[List].traverse(filters.toList)(filter => filter.isAllowed[F](msg).map(_ -> filter)).map {
            processedFilters =>
              val filtersNotPassed = processedFilters.collect {
                case (passed, filter) if !passed => filter
              }
              if (filtersNotPassed.isEmpty) Cmd(msg, args, c) else FilteredCmd(filtersNotPassed, cmd)
          }
      }
      .flatMapConcat(streamable.toSource)
  }

  /**
    * Subscribe to a specific command using a category, aliases, filters,
    * and a parser.
    * @return A source representing the individual parsed command.
    */
  def subscribeCmdParsed[A](category: CmdCategory, aliases: Seq[String], filters: Seq[CmdFilter] = Seq.empty)(
      implicit parser: MessageParser[A],
      F: Monad[F],
      streamable: Streamable[F]
  ): Source[ParsedCmdMessage[F, A], NotUsed] =
    subscribeCmd(category, aliases, filters)
      .collect[F[ParsedCmdMessage[F, A]]] {
        case cmd: Cmd[F] =>
          implicit val c: CacheSnapshotLike[F] = cmd.cache
          parser
            .parse(cmd.args)
            .fold(e => CmdParseError(cmd.msg, e, cmd.cache), res => ParsedCmd(cmd.msg, res._2, res._1, cmd.cache))

        case filtered: FilteredCmd[F] => F.pure(filtered)
      }
      .flatMapConcat(streamable.toSource)

  /**
    * Subscribe to a command using a unparsed command factory.
    */
  def subscribe[Mat, Mat2](
      factory: BaseCmdFactory[F, Mat]
  )(combine: (Future[Done], Mat) => Mat2)(implicit F: Monad[F], streamable: Streamable[F]): Mat2 =
    subscribeCmd(factory.category, factory.lowercaseAliases, factory.filters)
      .via(CmdHelper.handleErrorsUnparsed(requests))
      .watchTermination()(Keep.right)
      .toMat(factory.sink(requests))(combine)
      .run()

  /**
    * Subscribe to a command using a parsed command factory.
    */
  def subscribe[A, Mat, Mat2](
      factory: ParsedCmdFactory[F, A, Mat]
  )(combine: (Future[Done], Mat) => Mat2)(implicit F: Monad[F], streamable: Streamable[F]): Mat2 =
    subscribeCmdParsed(factory.category, factory.lowercaseAliases, factory.filters)(factory.parser, F, streamable)
      .via(CmdHelper.handleErrorsParsed(requests))
      .watchTermination()(Keep.right)
      .toMat(factory.sink(requests))(combine)
      .run()
}
