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
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, ValidUpgrade, WebSocketUpgradeResponse}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy}
import io.circe
import io.circe.Error

/**
  * An abstract websocket handler. Handles going from inactive to active, and termination
  * @tparam WsMessage The type of the websocket messages
  * @tparam Resume The resume data type
  */
abstract class AbstractWsHandler[WsMessage, Resume](implicit mat: Materializer)
    extends Actor
    with Timers
    with ActorLogging {
  import AbstractWsHandler._
  import context.dispatcher

  private implicit val system:  ActorSystem      = context.system
  private var sendFirstSinkAck: Option[ActorRef] = None
  var shuttingDown = false

  var resume: Option[Resume]                     = None
  var queue:  SourceQueueWithComplete[WsMessage] = _
  var receivedAck = true

  override def postStop(): Unit =
    if (queue != null) queue.complete()

  def parseMessage: Flow[Message, Either[circe.Error, WsMessage], NotUsed]

  def createMessage: Flow[WsMessage, Message, NotUsed]

  /**
    * The full uri to connect to
    */
  def wsUri: Uri

  /**
    * The flow to use to send and receive messages with
    */
  def wsFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
    Http().webSocketClientFlow(wsUri)

  def heartbeatTimerKey: String = "SendHeartbeats"

  def restartLoginKey: String = "RestartLogin"

  /**
    * The base handler when inactive
    */
  val whenInactiveBase: Receive = {
    case Login =>
      log.info("Logging in")
      val src = Source.queue[WsMessage](64, OverflowStrategy.backpressure)
      val sink = Sink.actorRefWithAck[Either[Error, WsMessage]](
        ref = self,
        onInitMessage = InitSink,
        ackMessage = AckSink,
        onCompleteMessage = CompletedSink
      )
      val flow = createMessage.viaMat(wsFlow)(Keep.right).viaMat(parseMessage)(Keep.left)

      log.debug("WS uri: {}", wsUri)
      val (sourceQueue, future) = src.viaMat(flow)(Keep.both).toMat(sink)(Keep.left).run()

      future.foreach {
        case InvalidUpgradeResponse(response, cause) =>
          response.discardEntityBytes()
          sourceQueue.complete()
          throw new IllegalStateException(s"Could not connect to gateway: $cause")
        case ValidUpgrade(response, _) =>
          log.debug("Valid login: {}", response.entity.toString)
          response.discardEntityBytes()
          self ! ValidWsUpgrade
      }

      queue = sourceQueue
    case ValidWsUpgrade =>
      log.info("Logged in, going to Active")
      sendFirstSinkAck.foreach { act =>
        act ! AckSink
      }

      becomeActive()
    case InitSink => sendFirstSinkAck = Some(sender())
  }

  def becomeActive(): Unit = context.become(active)

  def becomeInactive(): Unit = {
    context.become(inactive)
    queue = null
    receivedAck = true
  }

  def inactive: Receive = whenInactiveBase

  def active: Receive

  override def receive: Receive = inactive
}

object AbstractWsHandler {
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
}
