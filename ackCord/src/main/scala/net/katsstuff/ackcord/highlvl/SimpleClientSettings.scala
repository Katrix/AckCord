/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord.highlvl

import java.time.Instant

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import net.katsstuff.ackcord.commands.{CmdCategory, Commands}
import net.katsstuff.ackcord.data.PresenceStatus
import net.katsstuff.ackcord.http.RawPresenceGame
import net.katsstuff.ackcord.http.requests.{BotAuthentication, RequestHelper}
import net.katsstuff.ackcord.{Cache, ClientSettings, DiscordShard}

class SimpleClientSettings(
    token: String,
    largeThreshold: Int = 100,
    shardNum: Int = 0,
    shardTotal: Int = 1,
    idleSince: Option[Instant] = None,
    gameStatus: Option[RawPresenceGame] = None,
    status: PresenceStatus = PresenceStatus.Online,
    afk: Boolean = false,
    system: ActorSystem = ActorSystem("AckCord"),
    commandSettings: CommandSettings = CommandSettings(),
    requestSettings: RequestSettings = RequestSettings()
) extends ClientSettings(token, largeThreshold, shardNum, shardTotal, idleSince, gameStatus, status, afk) {

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  def connect(): Future[DiscordClient] = {
    implicit val actorSystem: ActorSystem       = system
    implicit val mat:         ActorMaterializer = ActorMaterializer()
    val requests = RequestHelper.apply(
      BotAuthentication(token),
      requestSettings.parallelism,
      requestSettings.bufferSize,
      requestSettings.overflowStrategy,
      requestSettings.maxAllowedWait
    )
    val cache    = Cache.create
    val commands = Commands.create(commandSettings.needMention, commandSettings.categories, cache, requests)

    DiscordShard.fetchWsGateway.map(
      uri => DiscordClient(Seq(this.connect(uri, cache, "DiscordClient")), cache, commands, requests)
    )
  }

  def connectAutoShards(): Future[DiscordClient] = {
    implicit val actorSystem: ActorSystem       = system
    implicit val mat:         ActorMaterializer = ActorMaterializer()
    val requests = RequestHelper.apply(
      BotAuthentication(token),
      requestSettings.parallelism,
      requestSettings.bufferSize,
      requestSettings.overflowStrategy,
      requestSettings.maxAllowedWait
    )
    val cache    = Cache.create
    val commands = Commands.create(commandSettings.needMention, commandSettings.categories, cache, requests)

    DiscordShard.fetchWsGatewayWithShards(token).map {
      case (uri, receivedShardTotal) =>
        val shards = DiscordShard.connectShards(uri, receivedShardTotal, this, cache, "DiscordClient")
        DiscordClient(shards, cache, commands, requests)
    }
  }
}
object SimpleClientSettings {
  def apply(
      token: String,
      largeThreshold: Int = 100,
      shardNum: Int = 0,
      shardTotal: Int = 1,
      idleSince: Option[Instant] = None,
      gameStatus: Option[RawPresenceGame] = None,
      status: PresenceStatus = PresenceStatus.Online,
      afk: Boolean = false,
      system: ActorSystem = ActorSystem("AckCord"),
      commandSettings: CommandSettings = CommandSettings(),
      requestSettings: RequestSettings = RequestSettings()
  ): SimpleClientSettings =
    new SimpleClientSettings(
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

case class RequestSettings(
    parallelism: Int = 4,
    bufferSize: Int = 32,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
    maxAllowedWait: FiniteDuration = 2.minutes
)

case class CommandSettings(needMention: Boolean = true, categories: Set[CmdCategory] = Set.empty)
