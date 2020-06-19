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

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import ackcord.cachehandlers.CacheTypeRegistry
import ackcord.gateway.{GatewayEvent, GatewayHandler}
import ackcord.requests.{RequestStreams, Routes}
import akka.Done
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ThrottleMode
import akka.stream.scaladsl.{Sink, Source}
import org.slf4j.Logger

object DiscordShard {

  sealed trait Command

  case class Parameters(
      gatewayUri: Uri,
      settings: GatewaySettings,
      cache: Cache,
      ignoredEvents: Seq[Class[_ <: GatewayEvent[_]]],
      cacheTypeRegistry: Logger => CacheTypeRegistry,
      context: ActorContext[Command],
      timers: TimerScheduler[Command],
      log: Logger
  )

  case class State(
      gatewayHandler: ActorRef[GatewayHandler.Command],
      isShuttingDown: Boolean = false,
      isRestarting: Boolean = false
  )

  /**
    * The core actor that controls all the other used actors of AckCord
    * @param wsUri The gateway websocket uri
    * @param settings The settings to use
    * @param cache The cache to use for this shard
    * @param ignoredEvents The events that the cache will completely ignore.
    *                      EXPERIMENTAL: Not completely tested and may produce bugs.
    * @param cacheTypeRegistry Provides a more fine grained way to ignore certain
    *                          parts of events based on the data being updated or deleted.
    *                          EXPERIMENTAL: Not completely tested, and may
    *                          break your bot.
    */
  def apply(
      wsUri: Uri,
      settings: GatewaySettings,
      cache: Cache,
      ignoredEvents: Seq[Class[_ <: GatewayEvent[_]]] = Nil,
      cacheTypeRegistry: Logger => CacheTypeRegistry = CacheTypeRegistry.default
  ): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      val log = context.log
      val gatewayHandler = context.spawn(
        GatewayHandlerCache(wsUri, settings, cache, ignoredEvents, cacheTypeRegistry(log), log, context.system),
        "GatewayHandler"
      )

      context.watchWith(gatewayHandler, GatewayHandlerTerminated)

      shard(
        Parameters(wsUri, settings, cache, ignoredEvents, cacheTypeRegistry, context, timers, log),
        State(gatewayHandler)
      )
    }
  }

  private def shard(parameters: Parameters, state: State): Behavior[Command] = {
    import parameters._
    import state._

    Behaviors.receiveMessage {
      case DiscordShard.StopShard =>
        gatewayHandler ! GatewayLogout
        shard(parameters, state.copy(isShuttingDown = true))

      case DiscordShard.StartShard =>
        gatewayHandler ! GatewayLogin
        Behaviors.same

      case GatewayHandlerTerminated if isShuttingDown =>
        log.info("Actor shut down: {}", gatewayHandler.path)
        Behaviors.stopped

      case GatewayHandlerTerminated =>
        val restartTime = if (isRestarting) 1.second else 5.minutes
        log.info(s"Gateway handler shut down. Restarting in ${if (isRestarting) "1 second" else "5 minutes"}")
        timers.startSingleTimer("RestartGateway", CreateGateway, restartTime)

        shard(parameters, state.copy(isRestarting = false))

      case CreateGateway =>
        val newGatewayHandler = context.spawn(
          GatewayHandlerCache(gatewayUri, settings, cache, ignoredEvents, cacheTypeRegistry(log), log, context.system),
          "GatewayHandler"
        )
        newGatewayHandler ! GatewayLogin
        context.watchWith(gatewayHandler, GatewayHandlerTerminated)
        shard(parameters, state.copy(gatewayHandler = newGatewayHandler))

      case RestartShard =>
        gatewayHandler ! GatewayLogout
        shard(parameters, state.copy(isRestarting = true))
    }
  }

  /**
    * Create many shard actors, given the needed arguments.
    * @param wsUri The websocket gateway uri.
    * @param shardTotal The amount of shards to create.
    * @param settings The settings to use.
    */
  def many(
      wsUri: Uri,
      shardTotal: Int,
      settings: GatewaySettings,
      cache: Cache,
      ignoredEvents: Seq[Class[_ <: GatewayEvent[_]]] = Nil,
      cacheTypeRegistry: Logger => CacheTypeRegistry = CacheTypeRegistry.default
  ): Seq[Behavior[Command]] = for (i <- 0 until shardTotal) yield {
    apply(
      wsUri,
      settings.copy(shardTotal = shardTotal, shardNum = i),
      cache,
      ignoredEvents,
      cacheTypeRegistry
    )
  }

  /**
    * Sends a login message to all the shards in the sequence, while obeying
    * IDENTIFY ratelimits.
    */
  def startShards(shards: Seq[ActorRef[Command]])(implicit system: ActorSystem[Nothing]): Future[Done] =
    Source(shards.toIndexedSeq)
      .throttle(shards.size, 5.seconds, 0, ThrottleMode.Shaping)
      .runForeach(shard => shard ! StartShard)

  /**
    * Send this to the client to log out and stop gracefully.
    */
  case object StopShard extends Command

  /**
    * Send this to the client to log in.
    */
  case object StartShard extends Command

  private case object CreateGateway            extends Command
  private case object GatewayHandlerTerminated extends Command

  /**
    * Send this to log out and log in again this shard.
    */
  case object RestartShard extends Command

  /**
    * Fetch the websocket gateway.
    * @param system The actor system to use.
    * @return An URI with the websocket gateway uri.
    */
  def fetchWsGateway(implicit system: ActorSystem[Nothing]): Future[Uri] = {
    import akka.actor.typed.scaladsl.adapter._
    import system.executionContext
    val http = Http(system.toClassic)

    http
      .singleRequest(HttpRequest(uri = Routes.gateway.applied))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Source.single(entity).via(RequestStreams.jsonDecode).runWith(Sink.head)
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
    * @return An URI with the websocket gateway uri.
    */
  def fetchWsGatewayWithShards(token: String)(implicit system: ActorSystem[Nothing]): Future[(Uri, Int)] = {
    import akka.actor.typed.scaladsl.adapter._
    import system.executionContext
    val http = Http(system.toClassic)
    val auth = Authorization(BotAuthentication(token))

    http
      .singleRequest(HttpRequest(uri = Routes.botGateway.applied, headers = List(auth)))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Source.single(entity).via(RequestStreams.jsonDecode).runWith(Sink.head)
        case HttpResponse(code, headers, entity, _) =>
          entity.discardBytes()
          Future.failed(
            new IllegalStateException(
              s"Could not get WS gateway.\nStatusCode: ${code.value}\nHeaders:\n${headers.mkString("\n")}"
            )
          )
      }
      .flatMap { json =>
        val c          = json.hcursor
        val startLimit = c.downField("session_start_limit")
        val res = for {
          gateway <- c.get[String]("url")
          shards  <- c.get[Int]("shards")
          // TODO: Use these
          total      <- startLimit.get[Int]("total")
          remaining  <- startLimit.get[Int]("remaining")
          resetAfter <- startLimit.get[Int]("reset_after")
        } yield {
          http.system.log.info("Got WS gateway: {}", gateway)
          (gateway: Uri, shards)
        }

        Future.fromTry(res.fold(Failure.apply, Success.apply))
      }
  }
}
