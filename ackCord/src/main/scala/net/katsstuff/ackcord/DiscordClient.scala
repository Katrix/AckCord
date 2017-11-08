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

import akka.actor._
import akka.event.EventStream
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, RestartFlow, Sink, Source}
import akka.stream.{DelayOverflowStrategy, Materializer, OverflowStrategy}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import net.katsstuff.ackcord.DiscordClient.{ClientActor, CreateGateway}
import net.katsstuff.ackcord.data.{CacheSnapshot, PresenceStatus}
import net.katsstuff.ackcord.http.requests.{BaseRESTRequest, GlobalRatelimit, RequestAnswer, RequestResponse, RequestStreams, RequestWrapper}
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler
import net.katsstuff.ackcord.http.websocket.gateway.{GatewayHandler, GatewayMessage}
import net.katsstuff.ackcord.http.{RawPresenceGame, Routes}
import shapeless.tag.@@

/**
  * The core actor that controls all the other used actors of AckCord
  * @param gatewayWsUri The gateway websocket uri
  * @param eventStream The eventStream to publish events to
  * @param settings The settings to use
  * @param mat The materializer to use
  */
class DiscordClient(gatewayWsUri: Uri, eventStream: EventStream, settings: ClientSettings)(implicit mat: Materializer)
    extends Actor
    with ActorLogging
    with Timers {
  private implicit val system: ActorSystem = context.system

  private val cache = context.actorOf(SnowflakeCache.props(eventStream), "SnowflakeCache")
  private var gatewayHandler =
    context.actorOf(GatewayHandler.cacheProps(gatewayWsUri, settings, cache), "GatewayHandler")
  private val requestHandler: ActorRef = {
    val source = Source.actorRef[RequestWrapper[Any, Any]](100, OverflowStrategy.fail)
    val flow = RestartFlow.withBackoff(30.seconds, 2.minutes, 0.2D) { () =>
      log.info("(Re)Starting request flow")
      RequestStreams.requestFlowWithRatelimit[Any, Any](
        bufferSize = 100,
        overflowStrategy = OverflowStrategy.backpressure,
        maxAllowedWait = 2.minutes,
        credentials = RequestStreams.botCredentials(settings.token)
      )
    }

    val sink = Sink.foreach[RequestAnswer[Any, Any]] {
      case RequestResponse(
          data,
          ctx,
          remainingRequests,
          tilReset,
          wrapper @ RequestWrapper(request: BaseRESTRequest[Any @unchecked, Any @unchecked, Any @unchecked], _, sendTo)
          ) if request.hasCustomResponseData =>
        val withWrapper = request
          .findData(data)(_: CacheSnapshot, _: CacheSnapshot)
          .map(newData => RequestResponse(newData, ctx, remainingRequests, tilReset, wrapper))

        cache ! SendHandledDataEvent(data, request.cacheHandler, withWrapper, sendTo)

      case answer => answer.toWrapper.sendResponseTo ! answer
    }

    source.via(flow).to(sink).run()
  }

  private var shutdownCount  = 0
  private var isShuttingDown = false

  context.watch(gatewayHandler)

  //If we fail more than 5 time in 3 minutes we want to wait to restart the gateway handler
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(5, 3.minutes)(SupervisorStrategy.defaultDecider)

  override def receive: Receive = {
    case DiscordClient.ShutdownClient =>
      isShuttingDown = true
      context.watch(cache)
      context.watch(requestHandler)
      context.stop(cache)
      requestHandler ! Status.Success(1)
      gatewayHandler.forward(AbstractWsHandler.Logout)
    case DiscordClient.StartClient =>
      gatewayHandler.forward(AbstractWsHandler.Login)
    case request: GatewayMessage[_]    => gatewayHandler.forward(request)
    case request: RequestWrapper[_, _] => requestHandler.forward(request)
    case Terminated(act) if isShuttingDown =>
      shutdownCount += 1
      log.info("Actor shut down: {} Shutdown count: {}", act.path, shutdownCount)
      if (shutdownCount == 3) {
        context.stop(self)
      }
    case Terminated(ref) =>
      if (ref == gatewayHandler) {
        log.info("Gateway handler shut down. Restarting in 5 minutes")
        timers.startSingleTimer("RestartGateway", CreateGateway, 5.minutes)
      }
    case CreateGateway =>
      gatewayHandler = context.actorOf(GatewayHandler.cacheProps(gatewayWsUri, settings, cache), "GatewayHandler")
  }
}
object DiscordClient extends FailFastCirceSupport {
  def props(wsUri: Uri, eventStream: EventStream, settings: ClientSettings)(implicit mat: Materializer): Props =
    Props(new DiscordClient(wsUri, eventStream, settings))
  def props(wsUri: Uri, eventStream: EventStream, token: String)(implicit mat: Materializer): Props =
    props(wsUri, eventStream, ClientSettings(token))

  def tagClient(actor: ActorRef): ActorRef @@ DiscordClient = shapeless.tag[DiscordClient](actor)
  type ClientActor = ActorRef @@ DiscordClient

  /**
    * Create a client actor given the needed arguments
    * @param wsUri The websocket gateway uri
    * @param eventStream The event stream to use for the cache
    * @param token The bot token to use for authentication
    * @param system The actor system to use for creating the client actor
    * @param mat The materializer to use
    */
  def connect(wsUri: Uri, eventStream: EventStream, token: String)(
      implicit system: ActorSystem,
      mat: Materializer
  ): ClientActor = tagClient(system.actorOf(props(wsUri, eventStream, token), "DiscordClient"))

  /**
    * Create a client actor given the needed arguments
    * @param wsUri The websocket gateway uri
    * @param eventStream The event stream to use for the cache
    * @param settings The settings to use
    * @param system The actor system to use for creating the client actor
    * @param mat The materializer to use
    */
  def connect(wsUri: Uri, eventStream: EventStream, settings: ClientSettings)(
      implicit system: ActorSystem,
      mat: Materializer
  ): ClientActor = tagClient(system.actorOf(props(wsUri, eventStream, settings), "DiscordClient"))

  /**
    * Send this to the client to log out and stop gracefully
    */
  case object ShutdownClient

  /**
    * Send this to the client to log in
    */
  case object StartClient

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
    val auth = Authorization(RequestStreams.botCredentials(token))
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
case class ClientSettings(
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
    * @param eventStream The eventStream to publish events to
    * @param wsUri The websocket uri to use
    * @param system The actor system to use
    * @param mat The materializer to use
    * @return The discord client actor
    */
  def connect(eventStream: EventStream, wsUri: Uri)(implicit system: ActorSystem, mat: Materializer): ClientActor =
    DiscordClient.connect(wsUri, eventStream, this)
}
