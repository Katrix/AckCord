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

import java.time.Instant

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import ackcord.DiscordShard.FetchWSGatewayBotInfo
import ackcord.MemoryCacheSnapshot.CacheProcessor
import ackcord.cachehandlers.CacheTypeRegistry
import ackcord.data.PresenceStatus
import ackcord.data.raw.RawActivity
import ackcord.gateway.{Compress, GatewayEvent, GatewayIntents, GatewayProtocol}
import ackcord.requests.{Ratelimiter, RatelimiterActor}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.OverflowStrategy
import akka.util.Timeout

/**
  * Settings used when connecting to Discord.
  * @param token
  *   The token for the bot.
  * @param largeThreshold
  *   The large threshold.
  * @param shardNum
  *   The shard index of this shard.
  * @param shardTotal
  *   The amount of shards.
  * @param idleSince
  *   If the bot has been idle, set the time since.
  * @param activities
  *   Send one or more activities when connecting.
  * @param status
  *   The status to use when connecting.
  * @param afk
  *   If the bot should be afk when connecting.
  * @param compress
  *   What sort of compression the gateway should use.
  * @param intents
  *   Fine grained control over which events Discord should sent to your bot.
  * @param system
  *   The actor system to use.
  * @param requestSettings
  *   The request settings to use.
  * @param cacheSettings
  *   Settings the cache will use.
  */
case class ClientSettings(
    token: String,
    largeThreshold: Int = 50,
    shardNum: Int = 0,
    shardTotal: Int = 1,
    idleSince: Option[Instant] = None,
    activities: Seq[RawActivity] = Nil,
    status: PresenceStatus = PresenceStatus.Online,
    afk: Boolean = false,
    compress: Compress = Compress.ZLibStreamCompress,
    eventDecoders: GatewayProtocol.EventDecoders = GatewayProtocol.ackcordEventDecoders,
    intents: GatewayIntents = GatewayIntents.AllNonPrivileged,
    system: ActorSystem[Nothing] = ActorSystem(Behaviors.ignore, "AckCord"),
    requestSettings: RequestSettings = RequestSettings(),
    cacheSettings: CacheSettings = CacheSettings()
) {

  val gatewaySettings: GatewaySettings = GatewaySettings(
    token,
    largeThreshold,
    shardNum,
    shardTotal,
    idleSince,
    activities,
    status,
    afk,
    intents,
    compress,
    eventDecoders
  )

  implicit val executionContext: ExecutionContext = system.executionContext

  private def createClientWithShards(
      shards: Events => Seq[Behavior[DiscordShard.Command]]
  )(implicit actorSystem: ActorSystem[Nothing]): Future[DiscordClientCore] = {
    val clientActor = actorSystem.systemActorOf(
      DiscordClientActor(requestSettings.maxRequestsPerSecond, requestSettings.counter404s, shards, cacheSettings),
      "DiscordClient"
    )

    implicit val timeout: Timeout = Timeout(1.second)
    clientActor.ask[DiscordClientActor.GetRatelimiterAndEventsReply](DiscordClientActor.GetRatelimiterAndEvents).map {
      case DiscordClientActor.GetRatelimiterAndEventsReply(ratelimiter, cache) =>
        val requests = requestSettings.toRequestsActor(token, ratelimiter)

        new DiscordClientCore(
          cache,
          requests,
          clientActor
        )
    }
  }

  /** Create a [[DiscordClient]] from these settings. */
  def createClient(): Future[DiscordClient] = {
    implicit val actorSystem: ActorSystem[Nothing] = system

    DiscordShard.fetchWsGateway.flatMap { uri =>
      createClientWithShards(events => Seq(DiscordShard(uri, gatewaySettings, events)))
    }
  }

  /**
    * Create a [[DiscordClient]] from these settings while letting Discord set
    * the shard amount.
    */
  def createClientAutoShards(): Future[DiscordClient] = {
    implicit val actorSystem: ActorSystem[Nothing] = system

    DiscordShard.fetchWsGatewayWithShards(token).flatMap { case FetchWSGatewayBotInfo(uri, shards, _) =>
      createClientWithShards(
        DiscordShard.many(uri, shards, gatewaySettings, _)
      )
    }
  }
}

/**
  * @param parallelism
  *   Parallelism to use for requests.
  * @param bufferSize
  *   The buffer size to use for waiting requests.
  * @param maxRetryCount
  *   The maximum amount of times a request will be retried. Only affects
  *   requests that uses retries.
  * @param overflowStrategy
  *   The overflow strategy to use when the buffer is full.
  * @param maxAllowedWait
  *   The max allowed wait time before giving up on a request.
  * @param maxRequestsPerSecond
  *   Max amount of requests per second before the ratelimiter will assume it's
  *   globally ratelimited, and hold off on sending requests.
  * @param counter404s
  *   If the ratelimiter should keep track of previous 404s, and stop letting
  *   URIs with the same destination pass.
  */
case class RequestSettings(
    relativeTime: Boolean = false,
    parallelism: Int = 4,
    bufferSize: Int = 32,
    maxRetryCount: Int = 3,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
    maxAllowedWait: FiniteDuration = 2.minutes,
    maxRequestsPerSecond: Int = 50,
    counter404s: Boolean = true
) {

  def toRequestsActor(token: String, ratelimitActor: ActorRef[RatelimiterActor.Command])(implicit
      system: ActorSystem[Nothing]
  ): Requests =
    new Requests(
      ackcord.requests.RequestSettings(
        Some(BotAuthentication(token)),
        Ratelimiter.ofActor(ratelimitActor, parallelism)(system, maxAllowedWait),
        relativeTime,
        parallelism,
        maxRetryCount,
        bufferSize,
        overflowStrategy
      )
    )
}

/**
  * @param processor
  *   A function that runs on the cache right before a cache snapshot is
  *   produced.
  * @param parallelism
  *   How many cache updates are constructed in parallel
  * @param cacheBufferSize
  *   Size of the buffer for the cache
  * @param sendGatewayEventsBufferSize
  *   Size of the buffer for sending gateway events
  * @param receiveGatewayEventsBufferSize
  *   Size of the buffer for receiving gateway events
  * @param ignoredEvents
  *   Events the cache will ignore. [[APIMessage]] s aren't sent for these
  *   either.
  * @param cacheTypeRegistry
  *   Gives you control over how entities in the cache are updated, and what
  *   values are retained in the cache.
  * @param partitionCacheByGuild
  *   Instead of sharing a single cache for the entire application, this
  *   partitions the cache by guild. Each guild will in effect receive it's own
  *   cache. Cache events not specific to one guild will be sent to all caches.
  *
  * Unlike then default cache, this one is faster, as cache updates can be done
  * in parallel, but might use more memory, and you need to handle cross guild
  * cache actions yourself.
  */
case class CacheSettings(
    processor: CacheProcessor = MemoryCacheSnapshot.defaultCacheProcessor,
    parallelism: Int = 4,
    cacheBufferSize: PubSubBufferSize = PubSubBufferSize(),
    sendGatewayEventsBufferSize: PubSubBufferSize = PubSubBufferSize(),
    receiveGatewayEventsBufferSize: PubSubBufferSize = PubSubBufferSize(),
    ignoredEvents: Seq[Class[_ <: GatewayEvent[_]]] = Nil,
    cacheTypeRegistry: CacheTypeRegistry = CacheTypeRegistry.default,
    partitionCacheByGuild: Boolean = false
)
