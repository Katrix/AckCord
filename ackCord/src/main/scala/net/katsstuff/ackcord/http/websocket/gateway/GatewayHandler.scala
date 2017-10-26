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
import akka.actor.{ActorRef, ActorSystem, Cancellable, Props, Status}
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
import net.katsstuff.ackcord.http.websocket.gateway.GatewayEvent.ReadyData
import net.katsstuff.ackcord.util.AckCordSettings
import net.katsstuff.ackcord.{APIMessageHandlerEvent, AckCord, DiscordClientSettings}

/**
  * Responsible for normal websocket communication with Discord.
  * Some REST messages can't be sent until this has authenticated.
  * @param wsUri The uri to connect to
  * @param token The secret token
  * @param cache The cache to send events to
  * @param settings The settings to use
  * @param mat The [[Materializer]] to use
  */
class GatewayHandler(wsUri: Uri, token: String, cache: ActorRef, settings: DiscordClientSettings)(
    implicit mat: Materializer
) extends AbstractWsHandler[GatewayMessage, ResumeData](wsUri) {
  import AbstractWsHandler._
  import GatewayHandler._
  import GatewayProtocol._

  private implicit val system: ActorSystem = context.system
  import context.dispatcher

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

  def wsParams(uri: Uri): Uri = uri.withQuery(Query("v" -> AckCord.DiscordApiVersion, "encoding" -> "json"))

  when(Active) {
    case Event(InitSink, _) =>
      sender() ! AckSink
      stay()
    case Event(CompletedSink, _) =>
      log.info("Websocket connection completed")
      self ! Logout
      stay()
    case Event(Status.Failure(e), _) =>
      log.error(e, "Connection interrupted")
      throw e
    case Event(Left(NonFatal(e)), _) => throw e
    case event @ Event(Right(_: GatewayMessage[_]), _) =>
      val res = handleWsMessages(event)
      sender() ! AckSink
      res
    case event @ Event(_: GatewayMessage[_], _) => handleExternalMessage(event)
    case Event(SendHeartbeat, data @ WithHeartbeat(_, receivedAck, source, resume)) =>
      if (receivedAck) {
        val seq = resume.map(_.seq)

        val payload = createPayload(Heartbeat(seq))
        source.offer(TextMessage(payload))
        log.debug("Sent Heartbeat")

        stay using data.copy(receivedAck = false)
      } else throw new AckException("Did not receive a Heartbeat ACK between heartbeats")
    case Event(Logout, data) =>
      data.heartbeatCancelableOpt.foreach(_.cancel())
      data.queueOpt.foreach(_.complete())
      stop()
    case Event(Restart(fresh, waitDur), data) =>
      data.heartbeatCancelableOpt.foreach(_.cancel())
      data.queueOpt.foreach(_.complete())
      system.scheduler.scheduleOnce(waitDur, self, Login)
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
          val identifyObject = IdentifyObject(
            token = token,
            properties = IdentifyObject.createProperties,
            compress = true,
            largeThreshold = settings.largeThreshold,
            shard = Seq(settings.shardNum, settings.shardTotal),
            presence = StatusData(settings.idleSince, settings.gameStatus, settings.status, afk = settings.afk)
          )

          createPayload(Identify(identifyObject))
      }

      queue.offer(TextMessage(payload))
      val cancellable = system.scheduler.schedule(0.seconds, data.heartbeatInterval.millis, self, SendHeartbeat)
      stay using WithHeartbeat(cancellable, receivedAck = true, queue, resume)
    case Event(Right(dispatch: Dispatch[_]), data: WithHeartbeat[ResumeData @unchecked]) =>
      val seq = dispatch.sequence
      val event = dispatch.event.asInstanceOf[ComplexGatewayEvent[Any, Any]] //Makes stuff compile
      val d = dispatch.d

      val stayData = dispatch.event match {
        case GatewayEvent.Ready =>
          val readyData  = d.asInstanceOf[ReadyData]
          val resumeData = ResumeData(token, readyData.sessionId, seq)
          data.copy(resumeOpt = Some(resumeData))
        case _ =>
          data.copy(resumeOpt = data.resumeOpt.map(_.copy(seq = seq)))
      }

      cache ! APIMessageHandlerEvent(d, event.transformData, event.createEvent, event.handler)
      stay using stayData
    case Event(Right(HeartbeatACK), data: WithHeartbeat[ResumeData @unchecked]) =>
      log.debug("Received HeartbeatACK")
      stay using data.copy(receivedAck = true)
    case Event(Right(Reconnect), _) =>
      log.info("Was told to reconnect by gateway")
      self ! Restart(fresh = false, 500.millis)
      stay()
    case Event(Right(InvalidSession), _) =>
      log.error("Invalid session. Trying to establish new session in 5 seconds")
      self ! Restart(fresh = true, 5.seconds)
      stay()
  }

  /**
    * Handle external messages sent from neither this nor Discord
    */
  def handleExternalMessage: StateFunction = {
    case Event(request: RequestGuildMembers, WithHeartbeat(_, _, queue, _)) =>
      val payload = createPayload(request)
      queue.offer(TextMessage(payload))
      log.debug("Requested guild data for {}", request.d.guildId)

      stay
    case Event(request: VoiceStateUpdate, WithHeartbeat(_, _, queue, _)) =>
      val payload = createPayload(request)
      queue.offer(TextMessage(payload))

      stay
  }

  initialize()
}
object GatewayHandler {

  def props(wsUri: Uri, token: String, cache: ActorRef, settings: DiscordClientSettings)(
      implicit mat: Materializer
  ): Props =
    Props(new GatewayHandler(wsUri, token, cache, settings))

  private case class WithHeartbeat[Resume](
      heartbeatCancelable: Cancellable,
      receivedAck: Boolean,
      queue: SourceQueueWithComplete[Message],
      resumeOpt: Option[Resume]
  ) extends Data[Resume] {
    override def heartbeatCancelableOpt: Option[Cancellable]                      = Some(heartbeatCancelable)
    override def queueOpt:               Option[SourceQueueWithComplete[Message]] = Some(queue)
  }
}
