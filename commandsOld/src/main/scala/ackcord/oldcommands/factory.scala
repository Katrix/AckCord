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
package ackcord.oldcommands

import ackcord.requests.Requests
import ackcord.{CacheSnapshot, RequestRunner}
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}

/**
  * Represents non essential information about a command intended to be
  * displayed to an end user.
  * @param name The display name of a command.
  * @param description The description of what a command does.
  * @param usage How to use the command. Does not include the name or prefix.
  * @param extra Extra stuff about the command that you yourself decide on.
  */
case class CmdDescription(name: String, description: String, usage: String = "", extra: Map[String, String] = Map.empty)

/**
  * A factory for a command, that also includes other information about
  * the command.
  */
sealed trait CmdFactory[A, +Mat] {

  /**
    * The refiner to use to determine if, and how commands should be accepted.
    */
  def refiner: CmdRefiner

  /**
    * A sink which defines the behavior of this command.
    */
  def sink: Requests => Sink[A, Mat]

  /**
    * A description of this command.
    */
  def description: Option[CmdDescription]
}

/**
  * The factory for an unparsed command.
  *
  * @param refiner The refiner to use to determine if, and how commands
  *                should be accepted.
  * @param sink A sink which defines the behavior of this command.
  * @param description A description of this command.
  */
case class BaseCmdFactory[+Mat](
                                 refiner: CmdRefiner,
                                 sink: Requests => Sink[Cmd, Mat],
                                 description: Option[CmdDescription] = None
) extends CmdFactory[Cmd, Mat]
object BaseCmdFactory {

  type SourceRequest[A] = Source[A, NotUsed]

  def requestRunner(
      refiner: CmdRefiner,
      run: CacheSnapshot => (RequestRunner[SourceRequest], Cmd) => SourceRequest[Unit],
      description: Option[CmdDescription] = None
  ): BaseCmdFactory[NotUsed] =
    flowRequestRunner(
      refiner,
      runner => CmdFlow.map(c => cmd => run(c)(runner, cmd)),
      description
    )

  def flowRequestRunner[Mat](
      refiner: CmdRefiner,
      flow: RequestRunner[SourceRequest] => Flow[Cmd, SourceRequest[Unit], Mat],
      description: Option[CmdDescription] = None
  ): BaseCmdFactory[Mat] = {
    val sink: Requests => Sink[Cmd, Mat] = implicit requests => {
      val runner = RequestRunner[Source[?, NotUsed]]
      flow(runner).flatMapConcat(s => s).to(Sink.ignore)
    }

    BaseCmdFactory(refiner, sink, description)
  }
}

/**
  * The factory for a parsed command.
  * @param refiner The refiner to use to determine if, and how commands
  *                should be accepted.
  * @param sink A sink which defines the behavior of this command.
  * @param description A description of this command.
  */
case class ParsedCmdFactory[A, +Mat](
                                      refiner: CmdRefiner,
                                      sink: Requests => Sink[ParsedCmd[A], Mat],
                                      description: Option[CmdDescription] = None
)(implicit val parser: MessageParser[A])
    extends CmdFactory[ParsedCmd[A], Mat]
object ParsedCmdFactory {

  type SourceRequest[A] = Source[A, NotUsed]

  def requestRunner[A](
      refiner: CmdRefiner,
      run: CacheSnapshot => (RequestRunner[SourceRequest], ParsedCmd[A]) => SourceRequest[Unit],
      description: Option[CmdDescription] = None
  )(implicit parser: MessageParser[A]): ParsedCmdFactory[A, NotUsed] =
    flowRequestRunner[A, NotUsed](
      refiner,
      runner => ParsedCmdFlow[A].map(c => cmd => run(c)(runner, cmd)),
      description
    )

  def flowRequestRunner[A, Mat](
      refiner: CmdRefiner,
      flow: RequestRunner[SourceRequest] => Flow[ParsedCmd[A], SourceRequest[Unit], Mat],
      description: Option[CmdDescription] = None
  )(implicit parser: MessageParser[A]): ParsedCmdFactory[A, Mat] = {
    val sink: Requests => Sink[ParsedCmd[A], Mat] = implicit requests => {
      val runner = RequestRunner[SourceRequest]
      flow(runner).flatMapConcat(s => s).to(Sink.ignore)
    }

    ParsedCmdFactory(refiner, sink, description)
  }
}
