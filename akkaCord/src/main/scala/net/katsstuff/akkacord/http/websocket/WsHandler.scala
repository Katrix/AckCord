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

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ActorRef, Cancellable, FSM, Props, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, TextMessage, ValidUpgrade}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import io.circe.{parser, _}
import net.katsstuff.akkacord.DiscordClient.ShutdownClient
import net.katsstuff.akkacord.http.Routes
import net.katsstuff.akkacord.http.websocket.WsEvent.ReadyData
import net.katsstuff.akkacord.{APIMessageHandlerEvent, DiscordClientSettings, Request}

class WsHandler(token: String, cache: ActorRef, settings: DiscordClientSettings)
    extends FSM[WsHandler.State, WsHandler.Data]
    with FailFastCirceSupport {
  import WsHandler._
  import WsProtocol._

  private implicit val system = context.system
  private implicit val mat    = ActorMaterializer()

  import system.dispatcher

  private var reconnectAttempts    = 0
  private val maxReconnectAttempts = settings.maxReconnectAttempts

  def wsUri(uri: Uri): Uri = uri.withQuery(Query("v" -> "5", "encoding" -> "json"))

  startWith(Inactive, WithResumeData(None))

  onTransition {
    case Active -> Inactive     => self ! TryToConnect
    case Active -> ShuttingDown => self ! ShutdownStart
  }

  override def preStart(): Unit = self ! TryToConnect

  when(Inactive) {
    case Event(TryToConnect, data) =>
      if (reconnectAttempts < maxReconnectAttempts) {
        data.heartbeatCancelableOpt.foreach(_.cancel())
        data.sourceOpt.foreach(_.complete())
        reconnectAttempts += 1

        log.info("Trying to get gateway")
        Http()
          .singleRequest(HttpRequest(uri = Routes.gateway))
          .flatMap {
            case HttpResponse(StatusCodes.OK, _, entity, _) =>
              log.debug(s"Got WS gateway.")
              Unmarshal(entity).to[Json]
            case HttpResponse(code, headers, entity, _) =>
              entity.discardBytes()
              throw new IllegalStateException(s"Could not get WS gateway.\nStatusCode: ${code.value}\nHeaders:\n${headers.mkString("\n")}")
          }
          .foreach { js =>
            js.hcursor.get[String]("url") match {
              case Right(gateway) => self ! ReceivedGateway(gateway)
              case Left(e)        => throw e
            }
          }

        stay using WithResumeData(data.resume)
      } else {
        goto(ShuttingDown)
      }
    case Event(ReceivedGateway(uri), WithResumeData(resume)) =>
      reconnectAttempts = 0

      log.info(s"Got gateway: $uri")
      val sourceQueue = Source.queue[Message](64, OverflowStrategy.fail)
      val sink        = Sink.actorRef[Message](self, QueueCompleted)

      val flow = Flow.fromSinkAndSourceMat(sink, sourceQueue)(Keep.right)

      val (futureResponse, source) = Http().singleWebSocketRequest(wsUri(uri), flow)

      futureResponse.foreach {
        case InvalidUpgradeResponse(response, cause) =>
          response.discardEntityBytes()
          source.complete()
          throw new IllegalStateException(s"Could not connect to gateway: $cause")
        case ValidUpgrade(response, _) =>
          response.discardEntityBytes()
          self ! ValidWsUpgrade
      }

      stay using WithSource(source, resume)
    case Event(ValidWsUpgrade, _) => goto(Active)
  }

  when(ShuttingDown) {
    case Event(ShutdownStart, data) =>
      log.info("Starting shut down")
      data.heartbeatCancelableOpt.foreach(_.cancel())
      data.sourceOpt.foreach { source =>
        source.watchCompletion().foreach { _ =>
          log.info("Queue completed")
        }
        source.complete()
      }
      stay()
    case Event(QueueCompleted, _) =>
      log.info("Queue completed message")
      mat.shutdown()
      self ! ShutdownFinish
      stay()
    case Event(ShutdownFinish, _) =>
      log.info("Shutdown complete")
      stop()
  }

  when(Active) {
    case Event(QueueCompleted, _) =>
      log.info("Websocket connection completed")
      goto(Inactive)
    case Event(Status.Failure(e), _) =>
      log.error(e, "Connection interrupted")
      goto(Inactive)
    case event @ Event(_: WsMessage[_], _) => handleWsMessages(event)
    case Event(msg: TextMessage, _) =>
      msg.textStream.runWith(Sink.fold("")(_ + _)).foreach { payload =>
        log.debug(s"Received payload:\n$payload")
        parser.parse(payload).flatMap(_.as[WsMessage[_]]) match {
          case Right(message) => self ! message
          case Left(e)        => throw e
        }
      }

      stay
    case Event(SendHeartbeat, data @ WithHeartbeat(_, _, receivedAck, source, resume)) =>
      if (!receivedAck) {
        log.error("Did not receive a Heartbeat ACK between heartbeats, reconnecting")
        goto(Inactive) using WithResumeData(resume)
      } else {
        val seq = resume.map(_.seq)

        val payload = (Heartbeat(seq): WsMessage[Option[Int]]).asJson.noSpaces
        log.debug(s"Sending payload: $payload")
        source.offer(TextMessage(payload))
        log.debug("Sent Heartbeat")

        stay using data.copy(receivedAck = false)
      }
    case Event(ShutdownClient, _) =>
      goto(ShuttingDown)
  }

  def handleWsMessages: StateFunction = {
    case Event(Hello(data), WithSource(source, resume)) =>
      val payload = resume match {
        case Some(resumeData) => (Resume(resumeData): WsMessage[ResumeData]).asJson.noSpaces
        case None =>
          val identifyObject = IdentifyObject(
            token = token,
            properties = IdentifyObject.createProperties,
            compress = false,
            largeThreshold = settings.largeThreshold,
            shard = Seq(settings.shardNum, settings.shardTotal)
          )

          (Identify(identifyObject): WsMessage[IdentifyObject]).asJson.noSpaces
      }

      log.debug(s"Sending payload: $payload")
      source.offer(TextMessage(payload))
      val cancellable = system.scheduler.schedule(0.seconds, data.heartbeatInterval.millis, self, SendHeartbeat)
      stay using WithHeartbeat(data.heartbeatInterval, cancellable, receivedAck = true, source, resume)
    case Event(dispatch: Dispatch[_], data: WithHeartbeat) =>
      val seq   = dispatch.sequence
      val event = dispatch.event
      val d     = dispatch.d

      val stayData = event match {
        case WsEvent.Ready =>
          val readyData = d.asInstanceOf[ReadyData]
          log.debug("Ready trace:")
          readyData._trace.foreach(log.debug)
          val resumeData = ResumeData(token, readyData.sessionId, seq)

          data.copy(resume = Some(resumeData))
        case _ =>
          data.copy(resume = data.resume.map(_.copy(seq = seq)))
      }

      cache ! APIMessageHandlerEvent(d, event.createEvent)(event.handler)
      stay using stayData
    case Event(HeartbeatACK(_), data: WithHeartbeat) =>
      log.debug("Received HeartbeatACK")
      stay using data.copy(receivedAck = true)
    case Event(Reconnect, data) =>
      log.info("Was told to reconnect by gateway")
      goto(Inactive) using WithResumeData(data.resume)
    case Event(InvalidSession, _) =>
      log.error("Invalid session. Trying to establish new session")
      goto(Inactive) using WithResumeData(None)

    //External messages
    case Event(
        Request(request: RequestGuildMembers, requestCtx /*TODO: Find a way to send back the ctx here*/ ),
        WithHeartbeat(_, _, _, source, _)
        ) =>
      val payload = (request: WsMessage[RequestGuildMembersData]).asJson.noSpaces
      log.debug(s"Sending payload: $payload")
      source.offer(TextMessage(payload))
      log.debug("Requested guild data for {}", request.d.guildId)

      stay
  }

  initialize()
}
object WsHandler {
  def props(token: String, cache: ActorRef, settings: DiscordClientSettings): Props = Props(classOf[WsHandler], token, cache, settings)

  private case object TryToConnect
  private case class ReceivedGateway(uri: Uri)
  private case object SendHeartbeat
  private case object ValidWsUpgrade
  private case object ShutdownStart
  private case object ShutdownFinish
  private case object QueueCompleted

  sealed trait State
  case object Inactive     extends State
  case object Active       extends State
  case object ShuttingDown extends State

  sealed trait Data {
    def resume:                 Option[ResumeData]
    def sourceOpt:              Option[SourceQueueWithComplete[Message]]
    def heartbeatCancelableOpt: Option[Cancellable]
  }
  case class WithResumeData(resume: Option[ResumeData]) extends Data {
    override def sourceOpt:              Option[SourceQueueWithComplete[Message]] = None
    override def heartbeatCancelableOpt: Option[Cancellable]                      = None
  }
  case class WithSource(source: SourceQueueWithComplete[Message], resume: Option[ResumeData]) extends Data {
    override def heartbeatCancelableOpt: Option[Cancellable]                      = None
    override def sourceOpt:              Option[SourceQueueWithComplete[Message]] = Some(source)
  }
  case class WithHeartbeat(
      heartbeatInterval: Int,
      heartbeatCancelable: Cancellable,
      receivedAck: Boolean,
      source: SourceQueueWithComplete[Message],
      resume: Option[ResumeData]
  ) extends Data {
    override def heartbeatCancelableOpt: Option[Cancellable]                      = Some(heartbeatCancelable)
    override def sourceOpt:              Option[SourceQueueWithComplete[Message]] = Some(source)
  }
}
