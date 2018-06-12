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

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.{Done, NotUsed}
import cats.Monad
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.util.StreamConveniences

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

  private def runDSL(source: RequestDSL[Source[?, Any]] => Source[Unit, NotUsed]): (UniqueKillSwitch, Future[Done]) = {
    implicit val helper: RequestHelper = requests
    val dsl                            = RequestDSL[Source[?, Any]]
    import helper.mat

    source(dsl)
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  def registerHandler(handler: RawCommandHandler[F]): (UniqueKillSwitch, Future[Done]) = runDSL { _ =>
    commands.subscribe.collect {
      case cmd: RawCmd[F] => handler.handle(cmd)(cmd.c)
    }
  }

  def registerHandler(handler: RawCommandHandlerDSL[F]): (UniqueKillSwitch, Future[Done]) = runDSL { dsl =>
    commands.subscribe.collect {
      case cmd: RawCmd[F] => handler.handle[Source[?, Any]](cmd)(cmd.c, dsl, StreamConveniences.sourceInstance)
    }.flatMapConcat(identity)
  }

  /**
    * Registers an [[CommandHandler]] that will be called when that command is used.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerHandler[A: MessageParser](
      handler: CommandHandler[F, A]
  )(implicit F: Monad[F], streamable: Streamable[F]): (UniqueKillSwitch, Future[Done]) = {
    val sink: RequestHelper => Sink[ParsedCmd[F, A], UniqueKillSwitch] = _ => {
      ParsedCmdFlow[F, A]
        .map(implicit c => cmd => handler.handle(cmd.msg, cmd.args, cmd.remaining))
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
    }

    val factory = ParsedCmdFactory(handler.category, handler.aliases, sink, handler.filters, handler.description)

    commands.subscribe(factory)(Keep.both).swap
  }

  /**
    * Registers an [[CommandHandlerDSL]] that will be run when that command is used.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerHandler[A: MessageParser](
      handler: CommandHandlerDSL[F, A]
  )(implicit F: Monad[F], streamable: Streamable[F]): (UniqueKillSwitch, Future[Done]) = {
    val flow: RequestDSL[Source[?, Any]] => Flow[ParsedCmd[F, A], Source[Unit, Any], UniqueKillSwitch] =
      implicit dsl => {
        ParsedCmdFlow[F, A]
          .map(implicit c => cmd => handler.handle[Source[?, Any]](cmd.msg, cmd.args, cmd.remaining))
          .viaMat(KillSwitches.single)(Keep.right)
      }

    val factory =
      ParsedCmdFactory.requestDSL(handler.category, handler.aliases, flow, handler.filters, handler.description)

    commands.subscribe(factory)(Keep.both).swap
  }
}
case class SeperateCommandsHelper[F[_]](commands: Commands[F], requests: RequestHelper) extends CommandsHelper[F]
