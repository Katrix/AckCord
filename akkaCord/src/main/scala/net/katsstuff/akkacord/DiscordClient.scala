/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
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
package net.katsstuff.akkacord

import java.time.Instant

import scala.concurrent.{ExecutionContext, Future}

import akka.AkkaException
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.event.EventStream
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.GenericHttpCredentials
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import net.katsstuff.akkacord.data.PresenceStatus
import net.katsstuff.akkacord.http.rest.{ComplexRESTRequest, RESTHandler}
import net.katsstuff.akkacord.http.websocket.{WsHandler, WsMessage}
import net.katsstuff.akkacord.http.{RawPresenceGame, Routes}

class DiscordClient(wsUri: Uri, token: String, eventStream: EventStream, settings: DiscordClientSettings)(
    implicit mat: Materializer
) extends Actor
    with ActorLogging {
  private implicit val system: ActorSystem = context.system

  private val cache     = system.actorOf(SnowflakeCache.props(eventStream), "SnowflakeCache")
  private val wsHandler = system.actorOf(WsHandler.props(wsUri, token, cache, settings), "WsHandler")
  private val restHandler =
    system.actorOf(RESTHandler.props(GenericHttpCredentials("Bot", token), cache), "RestHandler")

  private var shutdownCount = 0

  override def preStart(): Unit = {
    context.watch(wsHandler)
    context.watch(restHandler)
  }

  override def receive: Receive = {
    case DiscordClient.ShutdownClient =>
      restHandler.forward(DiscordClient.ShutdownClient)
      wsHandler.forward(WsHandler.Logout)
    case DiscordClient.StartClient =>
      wsHandler.forward(WsHandler.Login)
    case request @ Request(_: WsMessage[_], _)                => wsHandler.forward(request)
    case request @ Request(_: ComplexRESTRequest[_, _, _], _) => restHandler.forward(request)
    case Terminated(_) =>
      shutdownCount += 1
      if (shutdownCount == 2) {
        system.terminate()
      }
  }
}
object DiscordClient extends FailFastCirceSupport {
  def props(wsUri: Uri, token: String, eventStream: EventStream, settings: DiscordClientSettings)(
      implicit mat: Materializer
  ): Props =
    Props(new DiscordClient(wsUri, token, eventStream, settings))
  case object ShutdownClient
  case object StartClient

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
            http.system.log.info(s"Got WS gateway: $gateway")
            Future.successful(gateway)
          case Left(e) => Future.failed(e)
        }
      }
  }
}

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
  def connect(wsUri: Uri)(implicit mat: Materializer): ActorRef =
    system.actorOf(DiscordClient.props(wsUri, token, eventStream, this), "DiscordClient")
}
