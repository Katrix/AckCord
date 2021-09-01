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

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

import ackcord.requests.RatelimiterActor
import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.adapter._
import akka.pattern.gracefulStop

class DiscordClientActor(
    ctx: ActorContext[DiscordClientActor.Command],
    shardBehaviors: Events => Seq[Behavior[DiscordShard.Command]],
    cacheSettings: CacheSettings
) extends AbstractBehavior[DiscordClientActor.Command](ctx) {
  import DiscordClientActor._
  implicit val system: ActorSystem[Nothing] = context.system
  import system.executionContext

  val events: Events = if (cacheSettings.partitionCacheByGuild) {
    Events.createGuildCache(
      context.spawn(
        CacheStreams.guildCacheBehavior(CacheStreams.emptyStartingCache(cacheSettings.processor)),
        "GuildCacheHandler"
      ),
      cacheSettings.parallelism,
      cacheSettings.ignoredEvents,
      cacheSettings.cacheTypeRegistry,
      cacheBufferSize = cacheSettings.cacheBufferSize,
      sendGatewayEventsBufferSize = cacheSettings.sendGatewayEventsBufferSize,
      receiveGatewayEventsBufferSize = cacheSettings.receiveGatewayEventsBufferSize
    )
  } else {
    Events.create(
      cacheSettings.processor,
      cacheSettings.parallelism,
      cacheSettings.ignoredEvents,
      cacheSettings.cacheTypeRegistry,
      cacheBufferSize = cacheSettings.cacheBufferSize,
      sendGatewayEventsBufferSize = cacheSettings.sendGatewayEventsBufferSize,
      receiveGatewayEventsBufferSize = cacheSettings.receiveGatewayEventsBufferSize
    )
  }

  var shards: Seq[ActorRef[DiscordShard.Command]] = _

  var shardShutdownManager: ActorRef[DiscordShard.StopShard.type] = _

  val musicManager: ActorRef[MusicManager.Command] = context.spawn(MusicManager(events), "MusicManager")

  val rateLimiter: ActorRef[RatelimiterActor.Command] = context.spawn(RatelimiterActor(), "Ratelimiter")

  private val shutdown = CoordinatedShutdown(system.toClassic)

  shutdown.addTask("service-stop", "stop-discord") { () =>
    gracefulStop(shardShutdownManager.toClassic, shutdown.timeout("service-stop"), DiscordShard.StopShard)
      .map(_ => Done)
  }

  private def spawnShards(): Unit =
    shards = shardBehaviors(events).zipWithIndex.map(t => context.spawn(t._1, s"Shard${t._2}"))

  def login(): Unit = {
    require(shardShutdownManager == null, "Already logged in")
    spawnShards()
    shardShutdownManager = context.spawn(ShardShutdownManager(shards), "ShardShutdownManager")

    DiscordShard.startShards(shards)
  }

  def logout(timeout: FiniteDuration): Future[Boolean] = {
    import akka.actor.typed.scaladsl.adapter._

    val promise = Promise[Boolean]()

    require(shardShutdownManager != null, "Not logged in")
    promise.completeWith(gracefulStop(shardShutdownManager.toClassic, timeout, DiscordShard.StopShard))

    context.pipeToSelf(promise.future)(_ => LoggedOut)

    promise.future
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case DiscordClientActor.Login => login()
      case LoggedOut =>
        shardShutdownManager = null
        shards = null
      case Logout(timeout, replyTo)         => replyTo ! LogoutReply(logout(timeout))
      case GetShards(replyTo)               => replyTo ! GetShardsReply(shards)
      case GetMusicManager(replyTo)         => replyTo ! GetMusicManagerReply(musicManager)
      case GetRatelimiterAndEvents(replyTo) => replyTo ! GetRatelimiterAndEventsReply(rateLimiter, events)
    }

    Behaviors.same
  }
}
object DiscordClientActor {
  def apply(
      shardBehaviors: Events => Seq[Behavior[DiscordShard.Command]],
      cacheSettings: CacheSettings
  ): Behavior[Command] = Behaviors.setup(ctx => new DiscordClientActor(ctx, shardBehaviors, cacheSettings))

  sealed trait Command

  case object Login                                                                   extends Command
  case class Logout(timeout: FiniteDuration, replyTo: ActorRef[LogoutReply])          extends Command
  case class GetShards(replyTo: ActorRef[GetShardsReply])                             extends Command
  case class GetMusicManager(replyTo: ActorRef[GetMusicManagerReply])                 extends Command
  case class GetRatelimiterAndEvents(replyTo: ActorRef[GetRatelimiterAndEventsReply]) extends Command

  private case object LoggedOut extends Command

  case class LogoutReply(done: Future[Boolean])
  case class GetShardsReply(shards: Seq[ActorRef[DiscordShard.Command]])
  case class GetMusicManagerReply(musicManager: ActorRef[MusicManager.Command])
  case class GetRatelimiterAndEventsReply(ratelimiter: ActorRef[RatelimiterActor.Command], events: Events)
}
