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
package net.katsstuff.ackcord

import java.time.Instant

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import akka.Done
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, ThrottleMode}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import net.katsstuff.ackcord.DiscordShard.{CreateGateway, ShardActor}
import net.katsstuff.ackcord.data.PresenceStatus
import net.katsstuff.ackcord.http.requests._
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler.Login
import net.katsstuff.ackcord.http.websocket.gateway.GatewayHandler
import net.katsstuff.ackcord.http.{RawPresenceGame, Routes}
import shapeless.tag.@@

/**
  * The core actor that controls all the other used actors of AckCord
  * @param gatewayUri The gateway websocket uri
  * @param settings The settings to use
  */
class DiscordShard(gatewayUri: Uri, settings: CoreClientSettings, cache: Cache)
    extends Actor
    with ActorLogging
    with Timers {
  private implicit val system: ActorSystem = context.system

  private var gatewayHandler =
    context.actorOf(GatewayHandler.cacheProps(gatewayUri, settings, cache), "GatewayHandler")

  private var shutdownCount  = 0
  private var isShuttingDown = false

  context.watch(gatewayHandler)

  //If we fail more than 5 time in 3 minutes we want to wait to restart the gateway handler
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(5, 3.minutes)(SupervisorStrategy.defaultDecider)

  override def receive: Receive = {
    case DiscordShard.StopShard =>
      isShuttingDown = true
      gatewayHandler.forward(AbstractWsHandler.Logout)
    case DiscordShard.StartShard =>
      gatewayHandler.forward(AbstractWsHandler.Login)
    case Terminated(act) if isShuttingDown =>
      shutdownCount += 1
      log.info("Actor shut down: {} Shutdown count: {}", act.path, shutdownCount)
      if (shutdownCount == 1) {
        context.stop(self)
      }
    case Terminated(ref) =>
      if (ref == gatewayHandler) {
        log.info("Gateway handler shut down. Restarting in 5 minutes")
        timers.startSingleTimer("RestartGateway", CreateGateway, 5.minutes)
      }
    case CreateGateway =>
      gatewayHandler = context.actorOf(GatewayHandler.cacheProps(gatewayUri, settings, cache), "GatewayHandler")
  }
}
object DiscordShard extends FailFastCirceSupport {
  def props(wsUri: Uri, settings: CoreClientSettings, cache: Cache): Props =
    Props(new DiscordShard(wsUri, settings, cache))
  def props(wsUri: Uri, token: String, cache: Cache): Props = props(wsUri, CoreClientSettings(token), cache)

  def tagClient(actor: ActorRef): ActorRef @@ DiscordShard = shapeless.tag[DiscordShard](actor)
  type ShardActor = ActorRef @@ DiscordShard

  /**
    * Create a shard actor given the needed arguments
    * @param wsUri The websocket gateway uri
    * @param token The bot token to use for authentication
    * @param system The actor system to use for creating the client actor
    */
  def connect(wsUri: Uri, token: String, cache: Cache, actorName: String)(implicit system: ActorSystem): ShardActor =
    tagClient(system.actorOf(props(wsUri, token, cache), actorName))

  /**
    * Create a shard actor given the needed arguments
    * @param wsUri The websocket gateway uri
    * @param settings The settings to use
    * @param system The actor system to use for creating the client actor
    */
  def connect(wsUri: Uri, settings: CoreClientSettings, cache: Cache, actorName: String)(implicit system: ActorSystem): ShardActor =
    tagClient(system.actorOf(props(wsUri, settings, cache), actorName))

  /**
    * Create as multiple shard actors, given the needed arguments
    * @param wsUri The websocket gateway uri
    * @param shardTotal The amount of shards to create
    * @param settings The settings to use
    * @param system The actor system to use for creating the client actor
    */
  def connectShards(wsUri: Uri, shardTotal: Int, settings: CoreClientSettings, cache: Cache, actorName: String)(
      implicit system: ActorSystem
  ): Seq[ShardActor] = for (i <- 0 until shardTotal) yield {
    connect(wsUri, settings.copy(shardTotal = shardTotal, shardNum = i), cache, s"$actorName$i")
  }

  /**
    * Sends a login message to all the shards in the sequence, while obeying
    * IDENTIFY ratelimits.
    */
  def loginShards(shards: Seq[ShardActor])(implicit mat: Materializer): Future[Done] = {
    Source(shards.toIndexedSeq).throttle(shards.size, 5.seconds, 0, ThrottleMode.Shaping).runForeach { shard =>
      shard ! Login
    }
  }

  /**
    * Send this to the client to log out and stop gracefully
    */
  case object StopShard

  /**
    * Send this to the client to log in
    */
  case object StartShard

  private case object CreateGateway

  /**
    * Fetch the websocket gateway
    * @param system The actor system to use
    * @param mat The materializer to use
    * @param ec The execution context to use
    * @return An URI with the websocket gateway uri
    */
  def fetchWsGateway(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Future[Uri] = {
    val http = Http()
    http
      .singleRequest(HttpRequest(uri = Routes.gateway))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[Json]
        case HttpResponse(code, headers, entity, _) =>
          entity.discardBytes()
          Future.failed(
            new IllegalStateException(
              s"Could not get WS gateway.\nStatusCode: ${code.value}\nHeaders:\n${headers.mkString("\n")}"
            )
          )
      }
      .flatMap { json =>
        json.hcursor.get[String]("url") match {
          case Right(gateway) =>
            http.system.log.info("Got WS gateway: {}", gateway)
            Future.successful(gateway)
          case Left(e) => Future.failed(e)
        }
      }
  }

  /**
    * Fetch the websocket gateway with information about how many shards should be used.
    * @param system The actor system to use
    * @param mat The materializer to use
    * @param ec The execution context to use
    * @return An URI with the websocket gateway uri
    */
  def fetchWsGatewayWithShards(
      token: String
  )(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext): Future[(Uri, Int)] = {
    val http = Http()
    val auth = Authorization(BotAuthentication(token))
    http
      .singleRequest(HttpRequest(uri = Routes.botGateway, headers = List(auth)))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[Json]
        case HttpResponse(code, headers, entity, _) =>
          entity.discardBytes()
          Future.failed(
            new IllegalStateException(
              s"Could not get WS gateway.\nStatusCode: ${code.value}\nHeaders:\n${headers.mkString("\n")}"
            )
          )
      }
      .flatMap { json =>
        val res = for {
          gateway <- json.hcursor.get[String]("url")
          shards  <- json.hcursor.get[Int]("shards")
        } yield {
          http.system.log.info("Got WS gateway: {}", gateway)
          (gateway: Uri, shards)
        }

        res.fold(Future.failed, Future.successful)
      }
  }
}

/**
  * All the settings used by AckCord when connecting and similar
  * @param token The token for the bot
  * @param largeThreshold The large threshold
  * @param shardNum The shard index of this
  * @param shardTotal The amount of shards
  * @param idleSince If the bot has been idle, set the time since
  * @param gameStatus Send some presence when connecting
  * @param status The status to use when connecting
  * @param afk If the bot should be afk when connecting
  */
case class CoreClientSettings(
    token: String,
    largeThreshold: Int = 100,
    shardNum: Int = 0,
    shardTotal: Int = 1,
    idleSince: Option[Instant] = None,
    gameStatus: Option[RawPresenceGame] = None,
    status: PresenceStatus = PresenceStatus.Online,
    afk: Boolean = false
) {

  /**
    * Connect to discord using these settings
    * @param wsUri The websocket uri to use
    * @param system The actor system to use
    * @return The discord client actor
    */
  def connect(wsUri: Uri, cache: Cache, actorName: String)(implicit system: ActorSystem): ShardActor =
    DiscordShard.connect(wsUri, this, cache, actorName)
}
