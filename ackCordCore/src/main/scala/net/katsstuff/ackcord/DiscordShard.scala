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
import scala.concurrent.duration._

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
import net.katsstuff.ackcord.DiscordShard.CreateGateway
import net.katsstuff.ackcord.http.Routes
import net.katsstuff.ackcord.http.requests.BotAuthentication
import net.katsstuff.ackcord.websocket.AbstractWsHandler
import net.katsstuff.ackcord.websocket.gateway.GatewaySettings

/**
  * The core actor that controls all the other used actors of AckCord
  * @param gatewayUri The gateway websocket uri
  * @param settings The settings to use
  */
class DiscordShard(gatewayUri: Uri, settings: GatewaySettings, cache: Cache)
    extends Actor
    with ActorLogging
    with Timers {

  private var gatewayHandler =
    context.actorOf(GatewayHandlerCache.props(gatewayUri, settings, cache, log), "GatewayHandler")

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
      gatewayHandler = context.actorOf(GatewayHandlerCache.props(gatewayUri, settings, cache, log), "GatewayHandler")
  }
}
object DiscordShard extends FailFastCirceSupport {

  def props(wsUri: Uri, settings: GatewaySettings, cache: Cache): Props =
    Props(new DiscordShard(wsUri, settings, cache))

  def props(wsUri: Uri, token: String, cache: Cache): Props = props(wsUri, GatewaySettings(token), cache)

  /**
    * Create a shard actor given the needed arguments.
    * @param wsUri The websocket gateway uri.
    * @param token The bot token to use for authentication.
    * @param system The actor system to use for creating the client actor.
    */
  def connect(wsUri: Uri, token: String, cache: Cache, actorName: String)(implicit system: ActorSystem): ActorRef =
    system.actorOf(props(wsUri, token, cache), actorName)

  /**
    * Create a shard actor given the needed arguments.
    * @param wsUri The websocket gateway uri.
    * @param settings The settings to use.
    * @param system The actor system to use for creating the client actor.
    */
  def connect(wsUri: Uri, settings: GatewaySettings, cache: Cache, actorName: String)(
      implicit system: ActorSystem
  ): ActorRef = system.actorOf(props(wsUri, settings, cache), actorName)

  /**
    * Create as multiple shard actors, given the needed arguments.
    * @param wsUri The websocket gateway uri.
    * @param shardTotal The amount of shards to create.
    * @param settings The settings to use.
    * @param system The actor system to use for creating the client actor.
    */
  def connectMultiple(wsUri: Uri, shardTotal: Int, settings: GatewaySettings, cache: Cache, actorName: String)(
      implicit system: ActorSystem
  ): Seq[ActorRef] = for (i <- 0 until shardTotal) yield {
    connect(wsUri, settings.copy(shardTotal = shardTotal, shardNum = i), cache, s"$actorName$i")
  }

  /**
    * Sends a login message to all the shards in the sequence, while obeying
    * IDENTIFY ratelimits.
    */
  def startShards(shards: Seq[ActorRef])(implicit mat: Materializer): Future[Done] =
    Source(shards.toIndexedSeq)
      .throttle(shards.size, 5.seconds, 0, ThrottleMode.Shaping)
      .runForeach(shard => shard ! StartShard)

  /**
    * Send this to the client to log out and stop gracefully.
    */
  case object StopShard

  /**
    * Send this to the client to log in.
    */
  case object StartShard

  private case object CreateGateway

  /**
    * Fetch the websocket gateway.
    * @param system The actor system to use.
    * @param mat The materializer to use.
    * @return An URI with the websocket gateway uri.
    */
  def fetchWsGateway(implicit system: ActorSystem, mat: Materializer): Future[Uri] = {
    import system.dispatcher
    val http = Http()

    http
      .singleRequest(HttpRequest(uri = Routes.gateway.applied))
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
    * @param system The actor system to use.
    * @param mat The materializer to use.
    * @return An URI with the websocket gateway uri.
    */
  def fetchWsGatewayWithShards(
      token: String
  )(implicit system: ActorSystem, mat: Materializer): Future[(Uri, Int)] = {
    import system.dispatcher
    val http = Http()
    val auth = Authorization(BotAuthentication(token))

    http
      .singleRequest(HttpRequest(uri = Routes.botGateway.applied, headers = List(auth)))
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

        Future.fromTry(res.toTry)
      }
  }
}
