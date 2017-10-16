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
package net.katsstuff.ackcord.http.websocket

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Cancellable, FSM}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, ValidUpgrade, WebSocketUpgradeResponse}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import io.circe
import io.circe.syntax._
import io.circe.{Encoder, Error}

/**
  * An abstract websocket handler. Handles going from inactive to active, and termination
  * @param wsUri The uri to connect to
  * @param mat The [[Materializer]] to use
  * @tparam WsMessage The type of the websocket messages
  * @tparam Resume The resume data type
  */
abstract class AbstractWsHandler[WsMessage[_], Resume](wsUri: Uri)(implicit mat: Materializer)
    extends FSM[AbstractWsHandler.State, AbstractWsHandler.Data[Resume]] {
  import AbstractWsHandler._

  private implicit val system:  ActorSystem      = context.system
  private var sendFirstSinkAck: Option[ActorRef] = None

  import system.dispatcher

  startWith(Inactive, WithResumeData(None))

  onTermination {
    case StopEvent(reason, _, data) =>
      if(reason == FSM.Shutdown) {
        data.heartbeatCancelableOpt.foreach(_.cancel())
        data.queueOpt.foreach(_.complete())
      }
  }

  def parseMessage: Flow[Message, Either[circe.Error, WsMessage[_]], NotUsed]

  /**
    * Add extra params to the ws uri
    */
  def wsParams(uri: Uri): Uri

  /**
    * Utility method to create a payload from a message
    */
  def createPayload[A](msg: WsMessage[A])(implicit encoder: Encoder[WsMessage[A]]): String = {
    val payload = msg.asJson.noSpaces
    log.debug(s"Sending payload: $payload")
    payload
  }

  /**
    * The base handler when inactive
    */
  val whenInactiveBase: StateFunction = {
    case Event(Login, data) =>
      log.info("Logging in")
      val src = Source.queue[Message](64, OverflowStrategy.backpressure)
      val sink = Sink.actorRefWithAck[Either[Error, WsMessage[_]]](
        ref = self,
        onInitMessage = InitSink,
        ackMessage = AckSink,
        onCompleteMessage = CompletedSink
      )

      log.info(s"WS uri: ${wsParams(wsUri)}")
      val (queue, future) = src.viaMat(wsFlow(wsParams(wsUri)))(Keep.both).via(parseMessage).toMat(sink)(Keep.left).run()

      future.foreach {
        case InvalidUpgradeResponse(response, cause) =>
          response.discardEntityBytes()
          queue.complete()
          throw new IllegalStateException(s"Could not connect to gateway: $cause")
        case ValidUpgrade(response, _) =>
          log.info(s"Valid login: ${response.entity.toString}")
          response.discardEntityBytes()
          self ! ValidWsUpgrade
      }

      stay using WithQueue(queue, data.resumeOpt)
    case Event(ValidWsUpgrade, _) =>
      log.info("Logged in, going to Active")
      sendFirstSinkAck.foreach { act =>
        act ! AckSink
      }
      goto(Active)
    case Event(InitSink, _) =>
      sendFirstSinkAck = Some(sender())
      stay()
  }

  /**
    * What to do when inactive. If you override this, remember to call
    * [[whenInactiveBase.andThen()]].
    */
  def whenInactive: StateFunction = whenInactiveBase

  when(Inactive)(whenInactive)
}
object AbstractWsHandler {
  sealed trait State
  case object Inactive extends State
  case object Active   extends State

  case object InitSink
  case object AckSink
  case object CompletedSink

  case object SendHeartbeat
  case object ValidWsUpgrade

  /**
    * Send this to an [[AbstractWsHandler]] to make it go from inactive to active
    */
  case object Login

  /**
    * Send this to an [[AbstractWsHandler]] to stop it gracefully.
    */
  case object Logout

  /**
    * Send this to an [[AbstractWsHandler]] to restart the connection
    * @param fresh If it should start fresh. If this is false, it will try to continue the connection.
    * @param waitDur The amount of time to wait until connecting again
    */
  case class Restart(fresh: Boolean, waitDur: FiniteDuration)

  /**
    * And exception throw when something is wrong with the ack received
    * (or if no ack was received) from Discord.
    */
  class AckException(msg: String) extends Exception(msg)

  def wsFlow(uri: Uri)(implicit system: ActorSystem): Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
    Http().webSocketClientFlow(uri)

  private[websocket] trait Data[Resume] {
    def resumeOpt:              Option[Resume]
    def queueOpt:               Option[SourceQueueWithComplete[Message]]
    def heartbeatCancelableOpt: Option[Cancellable]
  }
  case class WithResumeData[Resume](resumeOpt: Option[Resume]) extends Data[Resume] {
    override def queueOpt:               Option[SourceQueueWithComplete[Message]] = None
    override def heartbeatCancelableOpt: Option[Cancellable]                      = None
  }
  case class WithQueue[Resume](queue: SourceQueueWithComplete[Message], resumeOpt: Option[Resume])
      extends Data[Resume] {
    override def heartbeatCancelableOpt: Option[Cancellable]                      = None
    override def queueOpt:               Option[SourceQueueWithComplete[Message]] = Some(queue)
  }
}
