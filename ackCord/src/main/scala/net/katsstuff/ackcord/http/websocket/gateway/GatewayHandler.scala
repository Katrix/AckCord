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
package net.katsstuff.ackcord.http.websocket.gateway

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.{ActorSystem, Props, Stash, Status}
import akka.event.Logging
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl._
import akka.stream.{Attributes, Materializer}
import akka.util.ByteString
import io.circe
import io.circe.parser
import io.circe.syntax._
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler
import net.katsstuff.ackcord.util.AckCordSettings
import net.katsstuff.ackcord.{APIMessageCacheUpdate, AckCord, Cache, ClientSettings}

/**
  * Responsible for normal websocket communication with Discord.
  * Some REST messages can't be sent until this has authenticated.
  * @param rawWsUri The raw uri to connect to without params
  * @param settings The settings to use.
  * @param mat The [[Materializer]] to use.
  * @param outSink A sink which will be materialized for each new event that
  *                is sent to this gateway.
  */
class GatewayHandler(
    rawWsUri: Uri,
    settings: ClientSettings,
    outSink: Sink[Dispatch[_], NotUsed]
)(implicit val mat: Materializer)
    extends AbstractWsHandler[GatewayMessage[_], ResumeData]
    with Stash {
  import AbstractWsHandler._
  import GatewayProtocol._

  private implicit val system: ActorSystem = context.system
  private var restartDur: FiniteDuration = _

  def parseMessage: Flow[Message, Either[circe.Error, GatewayMessage[_]], NotUsed] = {
    val jsonFlow = Flow[Message]
      .collect {
        case t: TextMessage => t.textStream.fold("")(_ + _)
        case b: BinaryMessage =>
          b.dataStream.fold(ByteString.empty)(_ ++ _).via(Compression.inflate()).map(_.utf8String)
      }
      .flatMapConcat(identity)

    val withLogging = if (AckCordSettings().LogReceivedWs) {
      jsonFlow.log("Received payload").withAttributes(Attributes.logLevels(onElement = Logging.DebugLevel))
    } else jsonFlow

    withLogging.map(parser.parse(_).flatMap(_.as[GatewayMessage[_]]))
  }

  override def createMessage: Flow[GatewayMessage[_], Message, NotUsed] = Flow[GatewayMessage[_]].map { msg =>
    val payload = msg.asJson.noSpaces
    if (AckCordSettings().LogSentWs) {
      log.debug("Sending payload: {}", payload)
    }
    TextMessage(payload)
  }

  def wsUri: Uri = rawWsUri.withQuery(Query("v" -> AckCord.DiscordApiVersion, "encoding" -> "json"))

  override def active: Receive = {
    val base: Receive = {
      case InitSink =>
        sender() ! AckSink
      case CompletedSink =>
        queue = null
        timers.cancel(heartbeatTimerKey)

        if (shuttingDown) {
          log.info("Websocket connection completed. Stopping.")
          context.stop(self)
        } else {
          log.info("Websocket connection completed. Logging in again.")
          if(restartDur != null && restartDur > 10.millis) {
            timers.startSingleTimer(restartLoginKey, Login, restartDur)
          }
          else {
            self ! Login
          }
          becomeInactive()
        }
      case Status.Failure(e) =>
        //TODO: Inspect error
        log.error(e, "Encountered websocket error")
        self ! Restart(fresh = false, 1.second)
      case Left(NonFatal(e)) =>
        log.error(e, "Encountered websocket parsing error")
        self ! Restart(fresh = false, 0.millis)
      case SendHeartbeat =>
        if (receivedAck) {
          val seq = resume.map(_.seq)

          queue.offer(Heartbeat(seq))
          log.debug("Sent Heartbeat")
          receivedAck = false
        } else {
          //TODO: Non 1000 close code
          log.warning("Did not receive HeartbeatACK between heartbeats. Restarting.")
          self ! Restart(fresh = false, 0.millis)
        }
      case Logout =>
        log.info("Shutting down")
        queue.complete()
        shuttingDown = true
      case Restart(fresh, waitDur) =>
        log.info("Restarting")
        queue.complete()

        restartDur = waitDur
        if (fresh) {
          resume = None
        }
    }

    base.orElse(handleWsMessages).orElse(handleExternalMessage)
  }

  /**
    * Handles all websocket messages received
    */
  def handleWsMessages: Receive = {
    case Right(Hello(data)) =>
      val message = resume match {
        case Some(resumeData) => Resume(resumeData)
        case None =>
          val identifyObject = IdentifyData(
            token = settings.token,
            properties = IdentifyData.createProperties,
            compress = true,
            largeThreshold = settings.largeThreshold,
            shard = Seq(settings.shardNum, settings.shardTotal),
            presence = StatusData(settings.idleSince, settings.gameStatus, settings.status, afk = settings.afk)
          )

          Identify(identifyObject)
      }

      queue.offer(message)
      self ! SendHeartbeat
      timers.startPeriodicTimer(heartbeatTimerKey, SendHeartbeat, data.heartbeatInterval.millis)
      sender() ! AckSink
      unstashAll()

      receivedAck = true
    case Right(dispatch: Dispatch[_]) =>
      resume = dispatch.event match {
        case GatewayEvent.Ready(readyData) =>
          val resumeData = ResumeData(settings.token, readyData.sessionId, dispatch.sequence)
          Some(resumeData)
        case _ =>
          resume.map(_.copy(seq = dispatch.sequence))
      }

      outSink.runWith(Source.single(dispatch))

      sender() ! AckSink
    case Right(Heartbeat(_)) =>
      self ! SendHeartbeat

      sender() ! AckSink
    case Right(HeartbeatACK) =>
      log.debug("Received HeartbeatACK")

      sender() ! AckSink
      receivedAck = true
    case Right(Reconnect) =>
      log.info("Was told to reconnect by gateway")
      self ! Restart(fresh = false, 100.millis)

      sender() ! AckSink
    case Right(InvalidSession(resumable)) =>
      log.error("Invalid session. Trying to establish new session in 5 seconds")
      self ! Restart(fresh = !resumable, 5.seconds)

      sender() ! AckSink
  }

  /**
    * Handle external messages sent from neither this nor Discord
    */
  def handleExternalMessage: Receive = {
    case request: RequestGuildMembers =>
      if (queue != null) queue.offer(request)
      else stash()

      log.debug("Requested guild data for {}", request.d.guildId)
    case request: VoiceStateUpdate =>
      if (queue != null) queue.offer(request)
      else stash()
  }
}
object GatewayHandler {

  def props(
      wsUri: Uri,
      settings: ClientSettings,
      outSink: Sink[Dispatch[_], NotUsed]
  )(implicit mat: Materializer): Props =
    Props(new GatewayHandler(wsUri, settings, outSink))

  def cacheProps(wsUri: Uri, settings: ClientSettings, cache: Cache)(implicit mat: Materializer): Props = {
    val sink = cache.publish.contramap { (dispatch: Dispatch[_]) =>
      val event = dispatch.event.asInstanceOf[ComplexGatewayEvent[Any, Any]] //Makes stuff compile
      APIMessageCacheUpdate(event.handlerData, event.createEvent, event.cacheHandler)
    }

    Props(new GatewayHandler(wsUri, settings, sink))
  }
}
