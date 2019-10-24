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

import ackcord.cachehandlers.CacheTypeRegistry
import ackcord.commands.{AbstractCommandSettings, CommandSettings, CoreCommands}
import ackcord.data.PresenceStatus
import ackcord.data.raw.RawActivity
import ackcord.requests.Ratelimiter
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.OverflowStrategy
import akka.util.Timeout

/**
  * Settings used when connecting to Discord.
  * @param token The token for the bot.
  * @param largeThreshold The large threshold.
  * @param shardNum The shard index of this shard.
  * @param shardTotal The amount of shards.
  * @param idleSince If the bot has been idle, set the time since.
  * @param activity Send an activity when connecting.
  * @param status The status to use when connecting.
  * @param afk If the bot should be afk when connecting.
  * @param system The actor system to use.
  * @param commandSettings The command settings to use.
  * @param requestSettings The request settings to use.
  */
class ClientSettings(
    token: String,
    largeThreshold: Int = 50,
    shardNum: Int = 0,
    shardTotal: Int = 1,
    idleSince: Option[Instant] = None,
    activity: Option[RawActivity] = None,
    status: PresenceStatus = PresenceStatus.Online,
    afk: Boolean = false,
    val system: ActorSystem[Nothing] = ActorSystem(Behaviors.ignore, "AckCord"),
    val commandSettings: AbstractCommandSettings = CommandSettings(needsMention = true, prefixes = Set.empty),
    val requestSettings: RequestSettings = RequestSettings()
    //TODO: Allow setting ignored and cacheTypeRegistry here at some point
) extends GatewaySettings(token, largeThreshold, shardNum, shardTotal, idleSince, activity, status, afk) {

  implicit val executionContext: ExecutionContext = system.executionContext

  /**
    * Create a [[DiscordClient]] from these settings.
    */
  def createClient(): Future[DiscordClient] = {
    implicit val actorSystem: ActorSystem[Nothing] = system

    DiscordShard.fetchWsGateway.flatMap { uri =>
      val cache = Cache.create
      val clientActor = actorSystem.systemActorOf(
        DiscordClientActor(Seq(DiscordShard(uri, this, cache, Nil, CacheTypeRegistry.default)), cache),
        "DiscordClient"
      )

      implicit val timeout: Timeout = Timeout(1.second)
      clientActor.ask[DiscordClientActor.GetRatelimiterReply](DiscordClientActor.GetRatelimiter).map {
        case DiscordClientActor.GetRatelimiterReply(ratelimiter) =>
          val requests = requestSettings.toRequests(token, ratelimiter)
          val commands = CoreCommands.create(commandSettings, cache, requests)

          new DiscordClientCore(
            cache,
            commands,
            requests,
            clientActor
          )
      }
    }
  }

  /**
    * Create a [[DiscordClient]] from these settings while letting Discord
    * set the shard amount.
    */
  def createClientAutoShards(): Future[DiscordClient] = {
    implicit val actorSystem: ActorSystem[Nothing] = system

    DiscordShard.fetchWsGatewayWithShards(token).flatMap {
      case (uri, receivedShardTotal) =>
        val cache = Cache.create
        val shards = (0 until receivedShardTotal).map { i =>
          DiscordShard(
            uri,
            this.copy(shardNum = i, shardTotal = receivedShardTotal),
            cache,
            Nil,
            CacheTypeRegistry.default
          )
        }

        val clientActor = actorSystem.systemActorOf(DiscordClientActor(shards, cache), "DiscordClient")

        implicit val timeout: Timeout = Timeout(1.second)
        clientActor.ask[DiscordClientActor.GetRatelimiterReply](DiscordClientActor.GetRatelimiter).map {
          case DiscordClientActor.GetRatelimiterReply(ratelimiter) =>
            val requests = requestSettings.toRequests(token, ratelimiter)
            val commands = CoreCommands.create(commandSettings, cache, requests)

            new DiscordClientCore(
              cache,
              commands,
              requests,
              clientActor
            )
        }
    }
  }

  override def toString: String =
    s"ClientSettings($token, $largeThreshold, $shardNum, $shardTotal, $idleSince, " +
      s"$activity, $status, $afk, $executionContext, $system, $commandSettings, $requestSettings)"
}
object ClientSettings {

  /**
    * Settings used when connecting to Discord.
    * @param token The token for the bot.
    * @param largeThreshold The large threshold.
    * @param shardNum The shard index of this shard.
    * @param shardTotal The amount of shards.
    * @param idleSince If the bot has been idle, set the time since.
    * @param gameStatus Send some presence when connecting.
    * @param status The status to use when connecting.
    * @param afk If the bot should be afk when connecting.
    * @param system The actor system to use.
    * @param commandSettings The command settings to use.
    * @param requestSettings The request settings to use.
    */
  def apply(
      token: String,
      largeThreshold: Int = 100,
      shardNum: Int = 0,
      shardTotal: Int = 1,
      idleSince: Option[Instant] = None,
      gameStatus: Option[RawActivity] = None,
      status: PresenceStatus = PresenceStatus.Online,
      afk: Boolean = false,
      system: ActorSystem[Nothing] = ActorSystem(Behaviors.ignore, "AckCord"),
      commandSettings: AbstractCommandSettings = CommandSettings(needsMention = true, prefixes = Set.empty),
      requestSettings: RequestSettings = RequestSettings()
  ): ClientSettings =
    new ClientSettings(
      token,
      largeThreshold,
      shardNum,
      shardTotal,
      idleSince,
      gameStatus,
      status,
      afk,
      system,
      commandSettings,
      requestSettings
    )
}

/**
  * @param parallelism Parallelism to use for requests.
  * @param bufferSize The buffer size to use for waiting requests.
  * @param maxRetryCount The maximum amount of times a request will be retried.
  *                      Only affects requests that uses retries.
  * @param overflowStrategy The overflow strategy to use when the buffer is full.
  * @param maxAllowedWait The max allowed wait time before giving up on a request.
  */
case class RequestSettings(
    millisecondPrecision: Boolean = true,
    relativeTime: Boolean = false,
    parallelism: Int = 4,
    bufferSize: Int = 32,
    maxRetryCount: Int = 3,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
    maxAllowedWait: FiniteDuration = 2.minutes
) {

  def toRequests(token: String, ratelimitActor: ActorRef[Ratelimiter.Command])(
      implicit system: ActorSystem[Nothing]
  ): RequestHelper =
    new RequestHelper(
      BotAuthentication(token),
      ratelimitActor,
      millisecondPrecision,
      relativeTime,
      parallelism,
      maxRetryCount,
      bufferSize,
      overflowStrategy,
      maxAllowedWait
    )
}
