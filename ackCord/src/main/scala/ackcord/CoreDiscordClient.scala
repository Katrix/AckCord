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

import scala.language.higherKinds

import scala.concurrent.Future
import scala.reflect.ClassTag

import ackcord.commands._
import akka.actor.ActorRef
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.{Done, NotUsed}

case class CoreDiscordClient(shards: Seq[ActorRef], cache: Cache, commands: Commands, requests: RequestHelper)
    extends DiscordClient {
  import cache.mat

  override def newCommandsHelper(settings: CommandSettings): (UniqueKillSwitch, CommandsHelper) = {
    val (killSwitch, newCommands) = CoreCommands.create(
      settings,
      cache.subscribeAPI.viaMat(KillSwitches.single)(Keep.right),
      requests
    )

    killSwitch -> SeperateCommandsHelper(newCommands, requests)
  }

  override val sourceRequesterRunner: RequestRunner[Source[?, NotUsed]] =
    RequestRunner.sourceRequestRunner(requests)

  override def registerHandler[G[_], A <: APIMessage](
      handler: EventHandler[G, A]
  )(implicit classTag: ClassTag[A], streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) =
    cache.subscribeAPI
      .collectType[A]
      .map { a =>
        implicit val c: MemoryCacheSnapshot = a.cache.current
        handler.handle(a)
      }
      .flatMapConcat(streamable.toSource)
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.ignore)(Keep.both)
      .run()

  override def onEvent[G[_]](
      handler: APIMessage => G[Unit]
  )(implicit streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) =
    cache.subscribeAPI
      .flatMapConcat(handler.andThen(streamable.toSource))
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.ignore)(Keep.both)
      .run()
}
