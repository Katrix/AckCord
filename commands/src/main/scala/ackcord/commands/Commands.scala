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

import scala.language.higherKinds

import scala.concurrent.Future

import ackcord.CacheSnapshot
import ackcord.requests.RequestHelper
import ackcord.util.Streamable
import akka.stream.scaladsl.{Keep, Source}
import akka.{Done, NotUsed}
import cats.Monad

/**
  * Represents a command handler, which will try to parse commands.
  * @param subscribeRaw A source that represents the parsed commands. Can be
  *                     materialized as many times as needed.
  * @param requests A request helper object which will be passed to handlers.
  */
case class Commands[F[_]](
    subscribeRaw: Source[RawCmdMessage[F], NotUsed],
    requests: RequestHelper
) {
  import requests.mat

  /**
    * Subscribe to a specific command using a refiner.
    * @return A source representing the individual command.
    */
  def subscribeCmd(refiner: CmdRefiner[F])(
      implicit streamable: Streamable[F]
  ): Source[CmdMessage[F], NotUsed] =
    subscribeRaw
      .collectType[RawCmd[F]]
      .flatMapConcat(raw => streamable.toSource(refiner.refine(raw).value))
      .collect {
        case Left(Some(error)) => error
        case Right(cmd)        => cmd
      }

  /**
    * Subscribe to a specific command using a refiner and a parser.
    * @return A source representing the individual parsed command.
    */
  def subscribeCmdParsed[A](refiner: CmdRefiner[F])(
      implicit parser: MessageParser[A],
      F: Monad[F],
      streamable: Streamable[F]
  ): Source[ParsedCmdMessage[F, A], NotUsed] =
    subscribeCmd(refiner)
      .collect[F[ParsedCmdMessage[F, A]]] {
        case Cmd(msg, args, cache) =>
          implicit val c: CacheSnapshot[F] = cache
          MessageParser.parseEitherT(args, parser)
            .fold(e => CmdParseError(msg, e, cache), res => ParsedCmd(msg, res._2, res._1, cache))
        case filtered: FilteredCmd[F]  => F.pure(filtered)
        case error: GenericCmdError[F] => F.pure(error)
      }
      .flatMapConcat(streamable.toSource)

  /**
    * Subscribe to a command using a unparsed command factory.
    */
  def subscribe[Mat, Mat2](
      factory: BaseCmdFactory[F, Mat]
  )(combine: (Future[Done], Mat) => Mat2)(implicit F: Monad[F], streamable: Streamable[F]): Mat2 =
    subscribeCmd(factory.refiner)
      .via(CmdHelper.addErrorHandlingUnparsed(requests))
      .watchTermination()(Keep.right)
      .toMat(factory.sink(requests))(combine)
      .run()

  /**
    * Subscribe to a command using a parsed command factory.
    */
  def subscribe[A, Mat, Mat2](
      factory: ParsedCmdFactory[F, A, Mat]
  )(combine: (Future[Done], Mat) => Mat2)(implicit F: Monad[F], streamable: Streamable[F]): Mat2 =
    subscribeCmdParsed(factory.refiner)(factory.parser, F, streamable)
      .via(CmdHelper.addErrorHandlingParsed(requests))
      .watchTermination()(Keep.right)
      .toMat(factory.sink(requests))(combine)
      .run()
}
