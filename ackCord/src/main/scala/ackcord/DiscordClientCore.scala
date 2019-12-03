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
import scala.concurrent.duration._
import scala.reflect.ClassTag

import ackcord.commands._
import ackcord.requests.SupervisionStreams
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.util.Timeout
import akka.{Done, NotUsed}

class DiscordClientCore(
    val cache: Cache,
    val commands: Commands,
    val requests: RequestHelper,
    actor: ActorRef[DiscordClientActor.Command]
) extends DiscordClient {
  import requests.system

  override def newCommandsHelper(settings: CommandSettings): (UniqueKillSwitch, CommandsHelper) = {
    val (killSwitch, newCommands) = CoreCommands.create(
      settings,
      cache.subscribeAPI.viaMat(KillSwitches.single)(Keep.right),
      requests
    )

    killSwitch -> SeperateCommandsHelper(newCommands, requests)
  }

  override val sourceRequesterRunner: RequestRunner[Source[*, NotUsed]] =
    RequestRunner.sourceRequestRunner(requests)

  override def registerHandler[G[_], A <: APIMessage](
      handler: EventHandler[G, A]
  )(implicit classTag: ClassTag[A], streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) =
    SupervisionStreams
      .addLogAndContinueFunction(
        cache.subscribeAPI
          .collectType[A]
          .map { a =>
            implicit val c: MemoryCacheSnapshot = a.cache.current
            handler.handle(a)
          }
          .flatMapConcat(streamable.toSource)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(Sink.ignore)(Keep.both)
          .addAttributes
      )
      .run()

  override def onEvent[G[_]](
      handler: APIMessage => G[Unit]
  )(implicit streamable: Streamable[G]): (UniqueKillSwitch, Future[Done]) =
    SupervisionStreams
      .addLogAndContinueFunction(
        cache.subscribeAPI
          .flatMapConcat(handler.andThen(streamable.toSource))
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(Sink.ignore)(Keep.both)
          .addAttributes
      )
      .run()

  override def shards: Future[Seq[ActorRef[DiscordShard.Command]]] = {
    implicit val timeout: Timeout = Timeout(1.second)
    actor.ask(DiscordClientActor.GetShards).map(_.shards)
  }

  override def musicManager: Future[ActorRef[MusicManager.Command]] = {
    implicit val timeout: Timeout = Timeout(1.second)
    actor.ask(DiscordClientActor.GetMusicManager).map(_.musicManager)
  }

  override def login(): Unit = actor ! DiscordClientActor.Login

  override def logout(timeout: FiniteDuration): Future[Boolean] = {
    implicit val impTimeout: Timeout = Timeout(1.second + timeout)
    actor.ask[DiscordClientActor.LogoutReply](DiscordClientActor.Logout(timeout, _)).flatMap(_.done)
  }
}
