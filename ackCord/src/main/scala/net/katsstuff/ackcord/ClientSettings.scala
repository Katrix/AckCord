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

import java.time.Instant

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import cats.Id
import net.katsstuff.ackcord.commands.{CmdCategory, CoreCommands}
import net.katsstuff.ackcord.data.PresenceStatus
import net.katsstuff.ackcord.data.raw.RawActivity
import net.katsstuff.ackcord.http.requests.{BotAuthentication, RequestHelper}
import net.katsstuff.ackcord.websocket.gateway.GatewaySettings

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
    largeThreshold: Int = 100,
    shardNum: Int = 0,
    shardTotal: Int = 1,
    idleSince: Option[Instant] = None,
    activity: Option[RawActivity] = None,
    status: PresenceStatus = PresenceStatus.Online,
    afk: Boolean = false,
    system: ActorSystem = ActorSystem("AckCord"),
    commandSettings: CommandSettings = CommandSettings(),
    requestSettings: RequestSettings = RequestSettings()
) extends GatewaySettings(token, largeThreshold, shardNum, shardTotal, idleSince, activity, status, afk) {

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  /**
    * Create a [[DiscordClient]] from these settings.
    */
  def build(): Future[DiscordClient[Id]] = {
    implicit val actorSystem: ActorSystem       = system
    implicit val mat:         ActorMaterializer = ActorMaterializer()

    val requests = RequestHelper.create(
      BotAuthentication(token),
      requestSettings.parallelism,
      requestSettings.bufferSize,
      requestSettings.overflowStrategy,
      requestSettings.maxAllowedWait
    )
    val cache    = Cache.create
    val commands = CoreCommands.create(commandSettings.needMention, commandSettings.categories, cache, requests)

    DiscordShard.fetchWsGateway.map(
      uri => CoreDiscordClient(Seq(DiscordShard.connect(uri, this, cache, "DiscordClient")), cache, commands, requests)
    )
  }

  /**
    * Create a [[DiscordClient]] from these settings while letting Discord
    * set the shard amount.
    */
  def buildAutoShards(): Future[DiscordClient[Id]] = {
    implicit val actorSystem: ActorSystem       = system
    implicit val mat:         ActorMaterializer = ActorMaterializer()

    val requests = RequestHelper.create(
      BotAuthentication(token),
      requestSettings.parallelism,
      requestSettings.bufferSize,
      requestSettings.overflowStrategy,
      requestSettings.maxAllowedWait
    )
    val cache    = Cache.create
    val commands = CoreCommands.create(commandSettings.needMention, commandSettings.categories, cache, requests)

    DiscordShard.fetchWsGatewayWithShards(token).map {
      case (uri, receivedShardTotal) =>
        val shards = DiscordShard.connectMultiple(uri, receivedShardTotal, this, cache, "DiscordClient")
        CoreDiscordClient(shards, cache, commands, requests)
    }
  }
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
      system: ActorSystem = ActorSystem("AckCord"),
      commandSettings: CommandSettings = CommandSettings(),
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
  * @param overflowStrategy The overflow strategy to use when the buffer is full.
  * @param maxAllowedWait The max allowed wait time before giving up on a request.
  */
case class RequestSettings(
    parallelism: Int = 4,
    bufferSize: Int = 32,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
    maxAllowedWait: FiniteDuration = 2.minutes
)

/**
  * @param needMention If a mention is needed before the category when using a prefix.
  * @param categories The valid command categories AckCord should keep a lookout for.
  */
case class CommandSettings(needMention: Boolean = true, categories: Set[CmdCategory] = Set.empty)
