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
import ackcord.requests.RequestHelper
import akka.stream.scaladsl.{Keep, Source}
import akka.{Done, NotUsed}

/**
  * Represents a command handler, which will try to parse commands.
  * @param subscribeRaw A source that represents the parsed commands. Can be
  *                     materialized as many times as needed.
  * @param requests A request helper object which will be passed to handlers.
  */
case class Commands(
    subscribeRaw: Source[RawCmdMessage, NotUsed],
    requests: RequestHelper
) {
  import requests.system

  /**
    * Subscribe to a specific command using a refiner.
    * @return A source representing the individual command.
    */
  def subscribeCmd(refiner: CmdRefiner): Source[CmdMessage, NotUsed] =
    subscribeRaw
      .collectType[RawCmd]
      .map(raw => refiner.refine(raw))
      .collect {
        case Left(Some(error)) => error
        case Right(cmd)        => cmd
      }

  /**
    * Subscribe to a specific command using a refiner and a parser.
    * @return A source representing the individual parsed command.
    */
  def subscribeCmdParsed[A](refiner: CmdRefiner)(
      implicit parser: MessageParser[A]
  ): Source[ParsedCmdMessage[A], NotUsed] =
    subscribeCmd(refiner)
      .collect[ParsedCmdMessage[A]] {
        case Cmd(msg, args, cache) =>
          implicit val c: CacheSnapshot = cache
          MessageParser
            .parseEither(args, parser)
            .fold(e => CmdParseError(msg, e, cache), res => ParsedCmd(msg, res._2, res._1, cache))
        case filtered: FilteredCmd  => filtered
        case error: GenericCmdError => error
      }

  /**
    * Subscribe to a command using a unparsed command factory.
    */
  def subscribe[Mat, Mat2](
      factory: BaseCmdFactory[Mat]
  )(combine: (Future[Done], Mat) => Mat2): Mat2 =
    subscribeCmd(factory.refiner)
      .via(CmdHelper.addErrorHandlingUnparsed(requests))
      .watchTermination()(Keep.right)
      .toMat(factory.sink(requests))(combine)
      .run()

  /**
    * Subscribe to a command using a parsed command factory.
    */
  def subscribe[A, Mat, Mat2](
      factory: ParsedCmdFactory[A, Mat]
  )(combine: (Future[Done], Mat) => Mat2): Mat2 =
    subscribeCmdParsed(factory.refiner)(factory.parser)
      .via(CmdHelper.addErrorHandlingParsed(requests))
      .watchTermination()(Keep.right)
      .toMat(factory.sink(requests))(combine)
      .run()
}
