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
package ackcord

import scala.concurrent.Future

import ackcord.commands._
import akka.Done
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{KillSwitches, UniqueKillSwitch}

/**
  * An object which manages a [[ackcord.commands.Commands]] instance.
  */
trait CommandsHelper {

  /**
    * The commands object specific to this command helper.
    */
  def commands: Commands

  /**
    * The request helper to use when sending messages from this command helper.
    */
  def requests: RequestHelper

  /**
    * Runs a partial function whenever a raw command object is received.
    *
    * If you use IntelliJ you might have to specify the execution type.
    * (Normally Id or SourceRequest)
    * @param handler The handler function.
    * @param streamable A way to convert your execution type to a stream.
    * @tparam G The execution type
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def onRawCmd[G[_]](
      handler: RawCmd => G[Unit]
  )(implicit streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) = {
    val req = requests
    import req.system
    commands.subscribeRaw
      .collectType[RawCmd]
      .flatMapConcat(handler.andThen(streamable.toSource))
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  /**
    * Registers an [[CommandHandler]] that will be called when that command is used.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def registerHandler[G[_]](
      handler: RawCommandHandler[G]
  )(implicit streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) = {
    val req = requests
    import req.system
    commands.subscribeRaw
      .collect {
        case cmd: RawCmd => handler.handle(cmd)(cmd.c)
      }
      .flatMapConcat(streamable.toSource)
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  /**
    * Registers an [[CommandHandler]] that will be called when that command is used.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def registerHandler[G[_], A: MessageParser](
      handler: CommandHandler[G, A]
  )(implicit streamableG: Streamable[G]): (UniqueKillSwitch, Future[Done]) = {
    val sink: RequestHelper => Sink[ParsedCmd[A], UniqueKillSwitch] = _ => {
      ParsedCmdFlow[A]
        .map(implicit c => cmd => handler.handle(cmd.msg, cmd.args, cmd.remaining))
        .flatMapConcat(streamableG.toSource)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
    }

    val factory = ParsedCmdFactory(handler.refiner, sink, handler.description)

    commands.subscribe(factory)(Keep.both).swap
  }

  /**
    * Register a command which runs some code.
    *
    * @param handler The handler function.
    * @param streamable A way to convert your execution type to a stream.
    * @tparam G The execution type
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def registerCmd[A: MessageParser, G[_]](
      prefix: String,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(
      handler: ParsedCmd[A] => G[Unit]
  )(implicit streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) =
    registerCmd(CmdInfo(prefix, aliases, filters), description)(handler)

  /**
    * Register a command which runs some code.
    *
    * @param handler The handler function.
    * @param streamable A way to convert your execution type to a stream.
    * @tparam G The execution type
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def registerCmd[A: MessageParser, G[_]](
      refiner: CmdRefiner,
      description: Option[CmdDescription]
  )(
      handler: ParsedCmd[A] => G[Unit]
  )(implicit streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) = {
    val sink = (_: RequestHelper) => {
      ParsedCmdFlow[A]
        .map(_ => handler)
        .flatMapConcat(streamable.toSource)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
    }

    val factory = ParsedCmdFactory(refiner, sink, description)

    commands.subscribe(factory)(Keep.right)
  }
}
case class SeperateCommandsHelper[F[_]](commands: Commands, requests: RequestHelper) extends CommandsHelper
