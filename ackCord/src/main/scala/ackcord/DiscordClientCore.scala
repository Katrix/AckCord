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

import ackcord.commands._
import ackcord.requests.SupervisionStreams
import akka.actor.CoordinatedShutdown
import akka.actor.typed._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.scaladsl.Keep
import akka.util.Timeout
import akka.NotUsed

class DiscordClientCore(
    val events: Events,
    val requests: Requests,
    actor: ActorRef[DiscordClientActor.Command]
) extends DiscordClient {
  import requests.system

  val commands = new CommandConnector(
    events.subscribeAPI.collectType[APIMessage.MessageCreate].map(m => (m.message, m.cache.current)),
    requests,
    requests.parallelism
  )

  val requestsHelper: RequestsHelper = new RequestsHelper(requests)

  override def onEventStreamable[G[_]](handler: CacheSnapshot => PartialFunction[APIMessage, G[Unit]])(
      implicit streamable: Streamable[G]
  ): EventRegistration[NotUsed] =
    SupervisionStreams
      .addLogAndContinueFunction(
        EventRegistration
          .toSink(
            events.subscribeAPI
              .collect {
                case m if handler(m.cache.current).isDefinedAt(m) => handler(m.cache.current)(m)
              }
              .flatMapConcat(streamable.toSource)
          )
          .addAttributes
      )
      .run()

  override def registerListener[A <: APIMessage, Mat](listener: EventListener[A, Mat]): EventRegistration[Mat] = {
    val (reg, mat) = SupervisionStreams
      .addLogAndContinueFunction(
        EventRegistration
          .withRegistration(
            events.subscribeAPI
              .collect {
                case msg if listener.refineEvent(msg).isDefined => listener.refineEvent(msg).get
              }
              .map(a => EventListenerMessage.Default(a))
          )
          .toMat(listener.sink)(Keep.both)
          .addAttributes
      )
      .run()

    reg.copy(materialized = mat)
  }

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

  override def shutdownJVM(timeout: FiniteDuration): Future[Unit] =
    CoordinatedShutdown(system.toClassic)
      .run(CoordinatedShutdown.JvmExitReason)
      .map { _ =>
        println("Stopping")
        sys.exit(0)
      }(scala.concurrent.ExecutionContext.global) //Just in case CoordinatedShutdown doesn't stop the JVM
}
