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

import akka.actor.{ActorRef, Cancellable, FSM, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, TextMessage, ValidUpgrade}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, OneByOneRequestStrategy}
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import net.katsstuff.akkacord.RawWsEvent
import net.katsstuff.akkacord.http.websocket.WsEvent.ReadyData
import net.katsstuff.akkacord.http.{RawMessage, Routes}
import spray.json._

class WsHandler(token: String, cache: ActorRef) extends FSM[WsHandler.State, WsHandler.Data] with ActorSubscriber {
  import WsHandler._
  import net.katsstuff.akkacord.http.websocket.WsProtocol._

  private implicit val system = context.system
  private implicit val mat    = ActorMaterializer()

  import system.dispatcher

  override protected def requestStrategy = OneByOneRequestStrategy

  val wsQuery                      = Query("v" -> "5", "encoding" -> "json")
  private var shouldShutDown       = false
  private var reconnectAttempts    = 0
  private val maxReconnectAttempts = 5 //TODO: Expose this later

  def wsUri(uri: Uri): Uri = uri.withQuery(wsQuery)

  startWith(Inactive, WithResumeData(None))

  onTransition {
    case Active -> Inactive => self ! TryToConnect
  }

  override def preStart(): Unit = self ! TryToConnect

  when(Inactive) {
    case Event(TryToConnect, data) =>
      data.sourceOpt.foreach(_.complete())
      data.heartbeatCancelableOpt.foreach(_.cancel())

      if (!shouldShutDown && reconnectAttempts < maxReconnectAttempts) {
        reconnectAttempts += 1

        log.info("Trying to get gateway")
        Http()
          .singleRequest(HttpRequest(uri = Routes.Gateway))
          .flatMap {
            case HttpResponse(StatusCodes.OK, headers, entity, _) =>
              log.debug(s"Got WS gateway.\nHeaders:\n${headers.mkString("\n")}\n Entity: $entity")
              Unmarshal(entity).to[JsObject]
            case HttpResponse(code, headers, entity, protocol) =>
              throw new IllegalStateException(
                s"Could not get WS gateway.\nStatusCode: ${code.value}\nHeaders:\n${headers.mkString("\n")}\nEntity: $entity\nProtocol: ${protocol.value}"
              )
          }
          .foreach { js =>
            self ! ReceivedGateway(js.fields("url").convertTo[String])
          }

        stay using WithResumeData(data.resume)
      } else {
        data.sourceOpt.foreach(_.watchCompletion().foreach { _ =>
          system.terminate()
        })
        stay
      }
    case Event(ReceivedGateway(uri), WithResumeData(resume)) =>
      reconnectAttempts = 0

      log.info(s"Got gateway: $uri")
      val sourceQueue   = Source.queue[Message](64, OverflowStrategy.fail)
      val destPublisher = Sink.asPublisher[Message](fanout = false)

      val flow = Flow.fromSinkAndSourceMat(destPublisher, sourceQueue)(Keep.both)

      val (futureResponse, (dest, source)) = Http().singleWebSocketRequest(wsUri(uri), flow)

      futureResponse.foreach {
        case InvalidUpgradeResponse(_, cause) => throw new IllegalStateException(s"Could not connect to gateway: $cause")
        case ValidUpgrade(_, _) =>
          dest.subscribe(ActorSubscriber(self)) //Is this safe to do in another thread?
          self ! ValidWsUpgrade
      }

      stay using WithSource(source, resume)
    case Event(ValidWsUpgrade, _) => goto(Active)
  }

  when(Active) {
    case Event(Shutdown, _) =>
      shouldShutDown = true

      goto(Inactive)
    case Event(OnComplete, _) =>
      goto(Inactive)
    case Event(OnError(e), _) =>
      log.error(e, "Connection interrupted")
      goto(Inactive)
    case Event(OnNext(msg: TextMessage), _) =>
      msg.textStream.runWith(Sink.fold("")(_ + _)).foreach { payload =>
        log.debug(s"Received payload:\n$payload")
        val message = payload.parseJson.convertTo[WsMessage[_]]
        self ! message
      }

      stay
    case Event(Hello(data), WithSource(source, resume)) =>
      val payload = resume match {
        case Some(resumeData) => (Resume(resumeData): WsMessage[ResumeData]).toJson.compactPrint
        case None =>
          //TODO: Allow customizing
          (Identify(IdentifyObject(token, IdentifyObject.createProperties, compress = false, 100, Seq(0, 1))): WsMessage[IdentifyObject]).toJson.compactPrint
      }

      source.offer(TextMessage(payload))
      val cancellable = system.scheduler.schedule(0 seconds, data.heartbeatInterval millis, self, SendHeartbeat)
      stay using WithHeartbeat(data.heartbeatInterval, cancellable, receivedAck = true, source, resume)
    case Event(Dispatch(seq, event, d), data: WithHeartbeat) =>
      val untyped = event.asInstanceOf[WsEvent]
      val updatedResume = data.resume.map(_.copy(seq = seq))
      val updatedData = data.copy(resume = updatedResume)

      val stayRes = untyped match {
        case WsEvent.Ready =>
          val readyData = d.asInstanceOf[ReadyData]
          log.debug("Ready trace:")
          readyData._trace.foreach(log.debug)
          val resumeData = ResumeData(token, readyData.sessionId, seq)

          stay using data.copy(resume = Some(resumeData))
        case otherEvents =>
          if (otherEvents == WsEvent.MessageCreate && d.asInstanceOf[RawMessage].content == "!kill") {
            self ! Shutdown
          }

          stay using updatedData
      }

      cache ! RawWsEvent(untyped, d)

      stayRes
    case Event(HeartbeatACK(_), data: WithHeartbeat) =>
      log.debug("Received HeartbeatACK")
      stay using data.copy(receivedAck = true)
    case Event(SendHeartbeat, data @ WithHeartbeat(_, _, receivedAck, source, resume)) =>
      if (!receivedAck) {
        log.error("Did not receive a Heartbeat ACK between heartbeats")
        goto(Inactive) using WithResumeData(resume)
      } else {
        val seq = resume.map(_.seq)

        val payload = (Heartbeat(seq): WsMessage[Option[Int]]).toJson.compactPrint
        source.offer(TextMessage(payload))
        log.debug("Sent Heartbeat")

        stay using data.copy(receivedAck = false)
      }
    case Event(Reconnect, data) =>
      log.info("Was told to reconnect by gateway")
      goto(Inactive) using WithResumeData(data.resume)
    case Event(InvalidSession, _) =>
      log.error("Invalid session. Trying to establish new session")
      goto(Inactive) using WithResumeData(None)
  }

  initialize()
}
object WsHandler {
  def props(token: String, cache: ActorRef): Props = Props(classOf[WsHandler], token, cache)

  case object TryToConnect
  case class ReceivedGateway(uri: Uri)
  case object SendHeartbeat
  case object ValidWsUpgrade
  case object Shutdown

  sealed trait State
  case object Inactive extends State
  case object Active   extends State

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
  case class WithHeartbeat(heartbeatInterval:   Int,
                           heartbeatCancelable: Cancellable,
                           receivedAck:         Boolean,
                           source:              SourceQueueWithComplete[Message],
                           resume:              Option[ResumeData])
      extends Data {
    override def heartbeatCancelableOpt: Option[Cancellable]                      = Some(heartbeatCancelable)
    override def sourceOpt:              Option[SourceQueueWithComplete[Message]] = Some(source)
  }
}
