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
import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.http.scaladsl.coding.Deflate
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import io.circe
import io.circe.parser
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler.Data
import net.katsstuff.ackcord.util.AckCordSettings
import net.katsstuff.ackcord.{APIMessageHandlerEvent, AckCord, DiscordClientSettings}

/**
  * Responsible for normal websocket communication with Discord.
  * Some REST messages can't be sent until this has authenticated.
  * @param rawWsUri The raw uri to connect to without params
  * @param settings The settings to use
  * @param responseProcessor An actor which receive all responses sent through this actor
  * @param responseFunc A function to apply to all responses before sending
  *                     them to the [[responseProcessor]].
  * @param mat The [[Materializer]] to use
  */
class GatewayHandler(
    rawWsUri: Uri,
    settings: DiscordClientSettings,
    responseProcessor: Option[ActorRef],
    responseFunc: Dispatch[_] => Any
)(implicit mat: Materializer)
    extends AbstractWsHandler[GatewayMessage, ResumeData] {
  import AbstractWsHandler._
  import GatewayHandler._
  import GatewayProtocol._

  private implicit val system: ActorSystem = context.system

  def parseMessage: Flow[Message, Either[circe.Error, GatewayMessage[_]], NotUsed] = {
    val jsonFlow = Flow[Message]
      .collect {
        case t: TextMessage   => t.textStream.fold("")(_ + _)
        case b: BinaryMessage => b.dataStream.fold(ByteString.empty)(_ ++ _).via(Deflate.decoderFlow).map(_.utf8String)
      }
      .flatMapConcat(identity)

    val withLogging = if (AckCordSettings().LogReceivedWs) {
      jsonFlow.log("Received payload")
    } else jsonFlow

    withLogging.map(parser.parse(_).flatMap(_.as[GatewayMessage[_]]))
  }

  def wsUri: Uri = rawWsUri.withQuery(Query("v" -> AckCord.DiscordApiVersion, "encoding" -> "json"))

  when(Active) {
    case Event(InitSink, _) =>
      sender() ! AckSink
      stay()
    case Event(CompletedSink, _) =>
      log.info("Websocket connection completed")
      self ! Logout
      stay()
    case Event(Status.Failure(e), _) => throw e
    case Event(Left(NonFatal(e)), _) => throw e
    case event @ Event(Right(_: GatewayMessage[_]), _) =>
      val res = handleWsMessages(event)
      sender() ! AckSink
      res
    case event @ Event(_: GatewayMessage[_], _) => handleExternalMessage(event)
    case Event(SendHeartbeat, data @ WithHeartbeat(receivedAck, source, resume)) =>
      if (receivedAck) {
        val seq = resume.map(_.seq)

        val payload = createPayload(Heartbeat(seq))
        source.offer(TextMessage(payload))
        log.debug("Sent Heartbeat")

        stay using data.copy(receivedAck = false)
      } else throw new AckException("Did not receive a Heartbeat ACK between heartbeats")
    case Event(Logout, data) =>
      data.queueOpt.foreach(_.complete())
      stop()
    case Event(Restart(fresh, waitDur), data) =>
      data.queueOpt.foreach(_.complete())
      setTimer("RestartLogin", Login, waitDur)
      goto(Inactive) using WithResumeData(if (fresh) None else data.resumeOpt)
  }

  /**
    * Handles all websocket messages received
    */
  def handleWsMessages: StateFunction = {
    case Event(Right(Hello(data)), WithQueue(queue, resume)) =>
      val payload = resume match {
        case Some(resumeData) => createPayload(Resume(resumeData))
        case None =>
          val identifyObject = IdentifyData(
            token = settings.token,
            properties = IdentifyData.createProperties,
            compress = true,
            largeThreshold = settings.largeThreshold,
            shard = Seq(settings.shardNum, settings.shardTotal),
            presence = StatusData(settings.idleSince, settings.gameStatus, settings.status, afk = settings.afk)
          )

          createPayload(Identify(identifyObject))
      }

      queue.offer(TextMessage(payload))
      self ! SendHeartbeat
      setTimer("SendHeartbeats", SendHeartbeat, data.heartbeatInterval.millis, repeat = true)
      stay using WithHeartbeat(receivedAck = true, queue, resume)
    case Event(Right(dispatch: Dispatch[_]), data: WithHeartbeat[ResumeData @unchecked]) =>
      val stayData = dispatch.event match {
        case GatewayEvent.Ready(readyData) =>
          val resumeData = ResumeData(settings.token, readyData.sessionId, dispatch.sequence)
          data.copy(resumeOpt = Some(resumeData))
        case _ =>
          data.copy(resumeOpt = data.resumeOpt.map(_.copy(seq = dispatch.sequence)))
      }

      responseProcessor.foreach(_ ! responseFunc(dispatch))
      stay using stayData
    case Event(Right(HeartbeatACK), data: WithHeartbeat[ResumeData @unchecked]) =>
      log.debug("Received HeartbeatACK")
      stay using data.copy(receivedAck = true)
    case Event(Right(Reconnect), _) =>
      log.info("Was told to reconnect by gateway")
      self ! Restart(fresh = false, 100.millis)
      stay()
    case Event(Right(InvalidSession(resumable)), _) =>
      log.error("Invalid session. Trying to establish new session in 5 seconds")
      self ! Restart(fresh = !resumable, 5.seconds)
      stay()
  }

  /**
    * Handle external messages sent from neither this nor Discord
    */
  def handleExternalMessage: StateFunction = {
    case Event(request: RequestGuildMembers, WithHeartbeat(_, queue, _)) =>
      val payload = createPayload(request)
      queue.offer(TextMessage(payload))
      log.debug("Requested guild data for {}", request.d.guildId)

      stay
    case Event(request: VoiceStateUpdate, WithHeartbeat(_, queue, _)) =>
      val payload = createPayload(request)
      queue.offer(TextMessage(payload))

      stay
  }

  initialize()
}
object GatewayHandler {

  def props(
      wsUri: Uri,
      settings: DiscordClientSettings,
      responseProcessor: Option[ActorRef],
      responseFunc: Dispatch[_] => Any
  )(implicit mat: Materializer): Props =
    Props(new GatewayHandler(wsUri, settings, responseProcessor, responseFunc))

  def cacheProps(wsUri: Uri, settings: DiscordClientSettings, snowflakeCache: ActorRef)(
      implicit mat: Materializer
  ): Props = {
    val f = (dispatch: Dispatch[_]) => {
      val event = dispatch.event.asInstanceOf[ComplexGatewayEvent[Any, Any]] //Makes stuff compile

      APIMessageHandlerEvent(event.handlerData, event.createEvent, event.cacheHandler)
    }

    Props(new GatewayHandler(wsUri, settings, Some(snowflakeCache), f))
  }

  private case class WithHeartbeat[Resume](
      receivedAck: Boolean,
      queue: SourceQueueWithComplete[Message],
      resumeOpt: Option[Resume]
  ) extends Data[Resume] {
    override def queueOpt: Option[SourceQueueWithComplete[Message]] = Some(queue)
  }
}
