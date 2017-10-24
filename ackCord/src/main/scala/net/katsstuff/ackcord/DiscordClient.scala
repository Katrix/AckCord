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

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.event.EventStream
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.GenericHttpCredentials
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import net.katsstuff.ackcord.data.PresenceStatus
import net.katsstuff.ackcord.http.rest.{ComplexRESTRequest, RESTHandler}
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler
import net.katsstuff.ackcord.http.websocket.gateway.{GatewayHandler, GatewayMessage}
import net.katsstuff.ackcord.http.{RawPresenceGame, Routes}
import shapeless.tag.@@

/**
  * The core actor that controls all the other used actors of AckCord
  * @param gatewayWsUri The gateway websocket uri
  * @param token The token for the bot
  * @param eventStream The eventStream to publish events to
  * @param settings The settings to use
  * @param mat The materializer to use
  */
class DiscordClient(gatewayWsUri: Uri, token: String, eventStream: EventStream, settings: DiscordClientSettings)(
    implicit mat: Materializer
) extends Actor
    with ActorLogging {
  private implicit val system: ActorSystem = context.system

  private val cache          = context.actorOf(SnowflakeCache.props(eventStream), "SnowflakeCache")
  private val gatewayHandler = context.actorOf(GatewayHandler.props(gatewayWsUri, token, cache, settings), "WsHandler")
  private val restHandler =
    context.actorOf(RESTHandler.cacheProps(RESTHandler.botCredentials(token), cache), "RestHandler")

  private var shutdownCount = 0

  override def preStart(): Unit = {
    context.watch(gatewayHandler)
    context.watch(restHandler)
  }

  override def receive: Receive = {
    case DiscordClient.ShutdownClient =>
      restHandler.forward(DiscordClient.ShutdownClient)
      gatewayHandler.forward(AbstractWsHandler.Logout)
    case DiscordClient.StartClient =>
      gatewayHandler.forward(AbstractWsHandler.Login)
    case request: GatewayMessage[_]                              => gatewayHandler.forward(request)
    case request @ Request(_: ComplexRESTRequest[_, _, _], _, _) => restHandler.forward(request)
    case Terminated(act) =>
      shutdownCount += 1
      log.info("Actor shut down: {} Shutdown count: {}", act.path, shutdownCount)
      if (shutdownCount == 2) {
        system.terminate()
      }
  }
}
object DiscordClient extends FailFastCirceSupport {
  def props(wsUri: Uri, token: String, eventStream: EventStream, settings: DiscordClientSettings)(
      implicit mat: Materializer
  ): Props = Props(new DiscordClient(wsUri, token, eventStream, settings))

  def tagClient(actor: ActorRef): ActorRef @@ DiscordClient = shapeless.tag[DiscordClient](actor)
  type ClientActor = ActorRef @@ DiscordClient

  /**
    * Send this to the client to log out
    */
  case object ShutdownClient

  /**
    * Send this to the client to log in
    */
  case object StartClient

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
}

/**
  * All the settings used by AckCord when connecting and similar
  * @param token The token for the bot
  * @param system The actor system to use
  * @param eventStream The eventStream to publish events to
  * @param largeThreshold The large threshold
  * @param shardNum The shard index of this
  * @param shardTotal The amount of shards
  * @param idleSince If the bot has been idle, set the time since
  * @param gameStatus Send some presence when connecting
  * @param status The status to use when connecting
  * @param afk If the bot should be afk when connecting
  */
case class DiscordClientSettings(
    token: String,
    system: ActorSystem,
    eventStream: EventStream,
    largeThreshold: Int = 100,
    shardNum: Int = 0,
    shardTotal: Int = 1,
    idleSince: Option[Instant] = None,
    gameStatus: Option[RawPresenceGame] = None,
    status: PresenceStatus = PresenceStatus.Online,
    afk: Boolean = false
) {
  def connect(wsUri: Uri)(implicit mat: Materializer): ActorRef @@ DiscordClient =
    DiscordClient.tagClient(system.actorOf(DiscordClient.props(wsUri, token, eventStream, this), "DiscordClient"))
}
