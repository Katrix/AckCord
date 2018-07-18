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
package net.katsstuff.ackcord

import scala.concurrent.Future
import scala.language.higherKinds

import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.{Done, NotUsed}
import cats.Monad
import net.katsstuff.ackcord.commands._

/**
  * An object which manages a [[Commands]] instance.
  */
trait CommandsHelper[F[_]] {

  /**
    * The commands object specific to this command helper.
    */
  def commands: Commands[F]

  /**
    * The request helper to use when sending messages from this command helper.
    */
  def requests: RequestHelper

  private def runDSL(source: Source[RequestDSL[Unit], NotUsed]): (UniqueKillSwitch, Future[Done]) = {
    val helper = requests
    import helper.mat

    source
      .viaMat(KillSwitches.single)(Keep.right)
      .flatMapConcat(_.toSource(helper.flow))
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  /**
    * Run a [[RequestDSL]] with a [[CacheSnapshot]] when raw command arrives.
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  @deprecated("Use the handlers or the new onRawCmd instead", since = "0.11")
  def onRawCommandDSLC(
      handler: CacheSnapshot[F] => PartialFunction[RawCmd[F], RequestDSL[Unit]]
  ): (UniqueKillSwitch, Future[Done]) = {
    runDSL {
      commands.subscribe.collect {
        case cmd: RawCmd[F] if handler(cmd.c).isDefinedAt(cmd) => handler(cmd.c)(cmd)
      }
    }
  }

  /**
    * Run a [[RequestDSL]] when raw command arrives.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  @deprecated("Use the handlers or the new onRawCmd instead", since = "0.11")
  def onRawCommandDSL(handler: PartialFunction[RawCmd[F], RequestDSL[Unit]]): (UniqueKillSwitch, Future[Done]) =
    onRawCommandDSLC { _ =>
      {
        case cmd if handler.isDefinedAt(cmd) => handler(cmd)
      }
    }

  /**
    * Run some code with a [[CacheSnapshot]] when raw command arrives.
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  @deprecated("Use the handlers or the new onRawCmd instead", since = "0.11")
  def onRawCommandC(handler: CacheSnapshot[F] => PartialFunction[RawCmd[F], Unit]): (UniqueKillSwitch, Future[Done]) = {
    onRawCommandDSLC { c =>
      {
        case msg if handler(c).isDefinedAt(msg) => RequestDSL.pure(handler(c)(msg))
      }
    }
  }

  /**
    * Run some code when raw command arrives.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  @deprecated("Use the handlers or the new onRawCmd instead", since = "0.11")
  def onRawCommand(handler: PartialFunction[RawCmd[F], Unit]): (UniqueKillSwitch, Future[Done]) =
    onRawCommandC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }

  /**
    * Register a command which runs a [[RequestDSL]] with a [[CacheSnapshot]].
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  @deprecated("Use the handlers or the new registerCmd instead", since = "0.11")
  def registerCommandDSLC[A: MessageParser](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(
      handler: CacheSnapshot[F] => ParsedCmd[F, A] => RequestDSL[Unit]
  )(implicit F: Monad[F], streamable: Streamable[F]): (UniqueKillSwitch, Future[Done]) = {
    val sink = (requests: RequestHelper) => {
      ParsedCmdFlow[F, A]
        .map(handler)
        .flatMapConcat(_.toSource(requests.flow))
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.right)
  }

  /**
    * Register a command which runs a [[RequestDSL]].
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  @deprecated("Use the handlers the new registerCmd instead", since = "0.11")
  def registerCommandDSL[A: MessageParser](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(
      handler: ParsedCmd[F, A] => RequestDSL[Unit]
  )(implicit F: Monad[F], streamable: Streamable[F]): (UniqueKillSwitch, Future[Done]) = {
    val sink = (requests: RequestHelper) => {
      ParsedCmdFlow[F, A]
        .map(_ => handler)
        .flatMapConcat(_.toSource(requests.flow))
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.right)
  }

  /**
    * Register a command which runs some code with a [[CacheSnapshot]].
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  @deprecated("Use the handlers the new registerCmd instead", since = "0.11")
  def registerCommandC[A: MessageParser](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(
      handler: CacheSnapshot[F] => ParsedCmd[F, A] => Unit
  )(implicit F: Monad[F], streamable: Streamable[F]): (UniqueKillSwitch, Future[Done]) = {
    val sink = (_: RequestHelper) => {
      ParsedCmdFlow[F, A]
        .map(handler)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.right)
  }

  /**
    * Register a command which runs some code.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  @deprecated("Use the handlers the new registerCmd instead", since = "0.11")
  def registerCommand[A: MessageParser](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(
      handler: ParsedCmd[F, A] => Unit
  )(implicit F: Monad[F], streamable: Streamable[F]): (UniqueKillSwitch, Future[Done]) = {
    val sink = (_: RequestHelper) => {
      ParsedCmdFlow[F, A]
        .map(_ => handler)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.right)
  }

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
      handler: RawCmd[F] => G[Unit]
  )(implicit streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) = {
    val req = requests
    import req.mat
    commands.subscribe
      .collectType[RawCmd[F]]
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
      handler: RawCommandHandler[F, G]
  )(implicit streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) = {
    val req = requests
    import req.mat
    commands.subscribe
      .collect {
        case cmd: RawCmd[F] => handler.handle(cmd)(cmd.c)
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
      handler: CommandHandler[F, G, A]
  )(implicit streamableG: Streamable[G], F: Monad[F], streamableF: Streamable[F]): (UniqueKillSwitch, Future[Done]) = {
    val sink: RequestHelper => Sink[ParsedCmd[F, A], UniqueKillSwitch] = _ => {
      ParsedCmdFlow[F, A]
        .map(implicit c => cmd => handler.handle(cmd.msg, cmd.args, cmd.remaining))
        .flatMapConcat(streamableG.toSource)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
    }

    val factory = ParsedCmdFactory(handler.category, handler.aliases, sink, handler.filters, handler.description)

    commands.subscribe(factory)(Keep.both).swap
  }

  /**
    * Registers an [[CommandHandlerDSL]] that will be run when that command is used.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  @deprecated("Use the handlers the new registerCmd instead", since = "0.11")
  def registerHandler[A: MessageParser](
      handler: CommandHandlerDSL[A]
  )(implicit F: Monad[F], streamable: Streamable[F]): (UniqueKillSwitch, Future[Done]) =
    registerCommandDSLC[A](handler.category, handler.aliases, handler.filters, handler.description) {
      implicit c => parsed =>
        handler.handle(parsed.msg, parsed.args, parsed.remaining)
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
    category: CmdCategory,
    aliases: Seq[String],
    filters: Seq[CmdFilter] = Nil,
    description: Option[CmdDescription] = None
  )(
    handler: ParsedCmd[F, A] => G[Unit]
  )(implicit F: Monad[F], streamableF: Streamable[F], streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) = {
    val sink = (_: RequestHelper) => {
      ParsedCmdFlow[F, A]
        .map(_ => handler)
        .flatMapConcat(streamable.toSource)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.right)
  }
}
case class SeperateCommandsHelper[F[_]](commands: Commands[F], requests: RequestHelper) extends CommandsHelper[F]
