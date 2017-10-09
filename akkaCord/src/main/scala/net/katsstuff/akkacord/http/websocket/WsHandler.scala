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
package net.katsstuff.akkacord.http.websocket

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Cancellable, FSM, Props, Status}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, TextMessage, ValidUpgrade, WebSocketUpgradeResponse}
import akka.stream.scaladsl._
import akka.stream.{Materializer, OverflowStrategy}
import io.circe
import io.circe.syntax._
import io.circe.{parser, _}
import net.katsstuff.akkacord.http.websocket.WsEvent.ReadyData
import net.katsstuff.akkacord.{APIMessageHandlerEvent, AkkaCord, DiscordClientSettings}

class WsHandler(wsUri: Uri, token: String, cache: ActorRef, settings: DiscordClientSettings)(implicit mat: Materializer)
    extends FSM[WsHandler.State, WsHandler.Data] {
  import WsHandler._
  import WsProtocol._

  private implicit val system:  ActorSystem      = context.system
  private var sendFirstSinkAck: Option[ActorRef] = None

  import system.dispatcher

  startWith(Inactive, WithResumeData(None))

  when(Inactive) {
    case Event(Login, data) =>
      val src = Source.queue[Message](64, OverflowStrategy.backpressure)
      val sink = Sink.actorRefWithAck[Either[Error, WsMessage[_]]](
        ref = self,
        onInitMessage = InitSink,
        ackMessage = AckSink,
        onCompleteMessage = CompletedSink
      )
      implicit val logger: LoggingAdapter = log
      val (queue, future) = WsHandler.matRunFlow(wsUri, src, sink)(Keep.both)(Keep.left)

      future.foreach {
        case InvalidUpgradeResponse(response, cause) =>
          response.discardEntityBytes()
          queue.complete()
          throw new IllegalStateException(s"Could not connect to gateway: $cause")
        case ValidUpgrade(response, _) =>
          response.discardEntityBytes()
          self ! ValidWsUpgrade
      }

      stay using WithSource(queue, data.resume)
    case Event(ValidWsUpgrade, _) =>
      sendFirstSinkAck.foreach { act =>
        act ! AckSink
      }
      goto(Active)
    case Event(InitSink, _) =>
      sendFirstSinkAck = Some(sender())
      stay()
  }

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
    case event @ Event(Right(_: WsMessage[_]), _) =>
      val res = handleWsMessages(event)
      sender() ! AckSink
      res
    case event @ Event(_: WsMessage[_], _) => handleExternalMessage(event)
    case Event(SendHeartbeat, data @ WithHeartbeat(_, _, receivedAck, source, resume)) =>
      if (receivedAck) {
        val seq = resume.map(_.seq)

        val payload = (Heartbeat(seq): WsMessage[Option[Int]]).asJson.noSpaces
        log.debug(s"Sending payload: $payload")
        source.offer(TextMessage(payload))
        log.debug("Sent Heartbeat")

        stay using data.copy(receivedAck = false)
      } else throw new NoAckException("Did not receive a Heartbeat ACK between heartbeats")
    case Event(Logout, data) =>
      data.heartbeatCancelableOpt.foreach(_.cancel())
      data.queueOpt.foreach(_.complete())
      goto(Inactive) using WithResumeData(None)
    case Event(Restart(fresh, waitDur), data) =>
      data.heartbeatCancelableOpt.foreach(_.cancel())
      data.queueOpt.foreach(_.complete())
      system.scheduler.scheduleOnce(waitDur, self, Login)
      goto(Inactive) using WithResumeData(if (fresh) data.resume else None)
  }

  def handleWsMessages: StateFunction = {
    case Event(Right(Hello(data)), WithSource(queue, resume)) =>
      val payload = resume match {
        case Some(resumeData) => (Resume(resumeData): WsMessage[ResumeData]).asJson.noSpaces
        case None =>
          val identifyObject = IdentifyObject(
            token = token,
            properties = IdentifyObject.createProperties,
            compress = false,
            largeThreshold = settings.largeThreshold,
            shard = Seq(settings.shardNum, settings.shardTotal),
            presence = StatusData(settings.idleSince, settings.gameStatus, settings.status, afk = settings.afk)
          )

          (Identify(identifyObject): WsMessage[IdentifyObject]).asJson.noSpaces
      }

      log.debug(s"Sending payload: $payload")
      queue.offer(TextMessage(payload))
      val cancellable = system.scheduler.schedule(0.seconds, data.heartbeatInterval.millis, self, SendHeartbeat)
      stay using WithHeartbeat(data.heartbeatInterval, cancellable, receivedAck = true, queue, resume)
    case Event(Right(dispatch: Dispatch[_]), data: WithHeartbeat) =>
      val seq   = dispatch.sequence
      val event = dispatch.event
      val d     = dispatch.d

      val stayData = event match {
        case WsEvent.Ready =>
          val readyData  = d.asInstanceOf[ReadyData]
          val resumeData = ResumeData(token, readyData.sessionId, seq)
          data.copy(resume = Some(resumeData))
        case _ =>
          data.copy(resume = data.resume.map(_.copy(seq = seq)))
      }

      cache ! APIMessageHandlerEvent(d, event.createEvent)(event.handler)
      stay using stayData
    case Event(Right(HeartbeatACK), data: WithHeartbeat) =>
      log.debug("Received HeartbeatACK")
      stay using data.copy(receivedAck = true)
    case Event(Right(Reconnect), _) =>
      log.info("Was told to reconnect by gateway")
      self ! Restart(fresh = false, 500.millis)
      stay()
    case Event(Right(InvalidSession), _) =>
      log.error("Invalid session. Trying to establish new session") //TODO

      goto(Inactive) using WithResumeData(None)
  }

  def handleExternalMessage: StateFunction = {
    case Event(request: RequestGuildMembers, WithHeartbeat(_, _, _, queue, _)) =>
      val payload = (request: WsMessage[RequestGuildMembersData]).asJson.noSpaces
      log.debug(s"Sending payload: $payload")
      queue.offer(TextMessage(payload))
      log.debug("Requested guild data for {}", request.d.guildId)

      stay
  }

  initialize()
}
object WsHandler {
  import WsProtocol._

  def props(wsUri: Uri, token: String, cache: ActorRef, settings: DiscordClientSettings)(
      implicit mat: Materializer
  ): Props =
    Props(new WsHandler(wsUri, token, cache, settings))

  class NoAckException(msg: String) extends Exception(msg)

  case object Login
  case object Logout
  case class Restart(fresh: Boolean, waitDur: FiniteDuration)

  private case object SendHeartbeat
  private case object ValidWsUpgrade
  private case object InitSink
  private case object AckSink
  private case object CompletedSink

  sealed trait State
  case object Inactive extends State
  case object Active   extends State

  sealed trait Data {
    def resume:                 Option[ResumeData]
    def queueOpt:               Option[SourceQueueWithComplete[Message]]
    def heartbeatCancelableOpt: Option[Cancellable]
  }
  case class WithResumeData(resume: Option[ResumeData]) extends Data {
    override def queueOpt:               Option[SourceQueueWithComplete[Message]] = None
    override def heartbeatCancelableOpt: Option[Cancellable]                      = None
  }
  case class WithSource(queue: SourceQueueWithComplete[Message], resume: Option[ResumeData]) extends Data {
    override def heartbeatCancelableOpt: Option[Cancellable]                      = None
    override def queueOpt:               Option[SourceQueueWithComplete[Message]] = Some(queue)
  }
  case class WithHeartbeat(
      heartbeatInterval: Int,
      heartbeatCancelable: Cancellable,
      receivedAck: Boolean,
      queue: SourceQueueWithComplete[Message],
      resume: Option[ResumeData]
  ) extends Data {
    override def heartbeatCancelableOpt: Option[Cancellable]                      = Some(heartbeatCancelable)
    override def queueOpt:               Option[SourceQueueWithComplete[Message]] = Some(queue)
  }

  def matRunFlow[Mat1, Mat2, Mat3, Mat4](
      uri: Uri,
      src: Source[Message, Mat1],
      sink: Sink[Either[circe.Error, WsMessage[_]], Mat3]
  )(
      combine1: (Mat1, Future[WebSocketUpgradeResponse]) => Mat2
  )(combine2: (Mat2, Mat3) => Mat4)(implicit system: ActorSystem, mat: Materializer, log: LoggingAdapter): Mat4 =
    src.viaMat(wsFlow(uri).via(parseMessage))(combine1).toMat(sink)(combine2).run()

  def parseMessage(implicit log: LoggingAdapter): Flow[Message, Either[circe.Error, WsMessage[_]], NotUsed] = {
    Flow[Message]
      .collect {
        case t: TextMessage => t.textStream.fold("")(_ + _)
      }
      .flatMapConcat(identity)
      .log("Received payload")
      .map(parser.parse(_).flatMap(_.as[WsMessage[_]]))
  }

  private def wsParams(uri: Uri): Uri = uri.withQuery(Query("v" -> AkkaCord.DiscordApiVersion, "encoding" -> "json"))

  def wsFlow(uri: Uri)(implicit system: ActorSystem): Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
    Http().webSocketClientFlow(wsParams(uri))
}
