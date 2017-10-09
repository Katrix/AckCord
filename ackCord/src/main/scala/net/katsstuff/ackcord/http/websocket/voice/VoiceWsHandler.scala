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
package net.katsstuff.ackcord.http.websocket.voice

import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Cancellable, Status}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, SourceQueueWithComplete}
import akka.util.ByteString
import io.circe
import io.circe.parser
import io.circe.syntax._
import net.katsstuff.ackcord.data.{Snowflake, UserId}
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler.Data

class VoiceWsHandler(address: String, serverId: Snowflake, userId: UserId, sessionId: String, token: String)(
    implicit mat: Materializer
) extends AbstractWsHandler[VoiceMessage, ResumeData](s"wss://$address") {
  import AbstractWsHandler._
  import VoiceWsHandler._
  import VoiceWsProtocol._

  private implicit val system: ActorSystem = context.system
  import system.dispatcher

  def parseMessage: Flow[Message, Either[circe.Error, VoiceMessage[_]], NotUsed] = {
    Flow[Message]
      .collect {
        case t: TextMessage => t.textStream.fold("")(_ + _)
      }
      .flatMapConcat(identity)
      .log("Received payload")
      .map(parser.parse(_).flatMap(_.as[VoiceMessage[_]]))
  }

  onTransition {
    case Inactive -> Active => self ! SendIdentify
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
    case event @ Event(Right(_: VoiceMessage[_]), _) =>
      val res = handleWsMessages(event)
      sender() ! AckSink
      res
    case Event(SendIdentify, WithQueue(queue, _)) =>
      val identifyObject = IdentifyData(serverId, userId, sessionId, token)

      val payload = (Identify(identifyObject): VoiceMessage[IdentifyData]).asJson.noSpaces
      log.debug(s"Sending payload: $payload")
      queue.offer(TextMessage(payload))

      stay()
    case Event(SendSelectProtocol, WithHeartbeat(_, _, _, _, source, _, _)) =>
      val protocolObj = SelectProtocolData("udp", SelectProtocolConnectionData(???, ???, "xsalsa20_poly1305"))
      val payload     = (SelectProtocol(protocolObj): VoiceMessage[SelectProtocolData]).asJson.noSpaces
      log.debug(s"Sending payload: $payload")
      source.offer(TextMessage(payload))
      stay()
    case Event(SendHeartbeat, data @ WithHeartbeat(_, _, receivedAck, _, source, _, _)) =>
      if (receivedAck) {
        val nonce = System.currentTimeMillis().toInt

        val payload = (Heartbeat(nonce): VoiceMessage[Int]).asJson.noSpaces
        log.debug(s"Sending payload: $payload")
        source.offer(TextMessage(payload))
        log.debug("Sent Heartbeat")

        stay using data.copy(receivedAck = false, previousNonce = Some(nonce))
      } else throw new AckException("Did not receive a Heartbeat ACK between heartbeats")
    case event @ Event(_: VoiceMessage[_], _) => handleExternalMessage(event)
    case Event(Logout, data) =>
      data.heartbeatCancelableOpt.foreach(_.cancel())
      data.queueOpt.foreach(_.complete())
      goto(Inactive) using WithResumeData(None)
    case Event(Restart(fresh, waitDur), data) =>
      data.heartbeatCancelableOpt.foreach(_.cancel())
      data.queueOpt.foreach(_.complete())
      system.scheduler.scheduleOnce(waitDur, self, Login)
      goto(Inactive) using WithResumeData(if (fresh) None else data.resumeOpt)
  }

  def handleWsMessages: StateFunction = {
    case Event(Right(Ready(ReadyObject(ssrc, port, _, _))), WithQueue(queue, resume)) =>
      val connectionActor = context.actorOf(VoiceUDPHandler.props(address, ssrc, port))
      stay using WithUDPActor(queue, connectionActor, resume.getOrElse(ResumeData(serverId, sessionId, token)))
    case Event(Right(Hello(heartbeatInterval)), WithUDPActor(queue, connection, resume)) =>
      val cancellable =
        system.scheduler.schedule(0.seconds, (heartbeatInterval * 0.75).toInt.millis, self, SendHeartbeat)
      self ! SendSelectProtocol
      stay using WithHeartbeat(
        heartbeatInterval = heartbeatInterval,
        heartbeatCancelable = cancellable,
        receivedAck = true,
        previousNonce = None,
        queue = queue,
        connectionActor = connection,
        resume = resume
      )
    case Event(Right(HeartbeatACK(nonce)), data: WithHeartbeat) =>
      log.debug("Received HeartbeatACK")
      if (data.previousNonce.contains(nonce)) {
        stay using data.copy(receivedAck = true)
      } else throw new AckException(s"Received unknown nonce $nonce for HeartbeatACK")
  }

  def handleExternalMessage: StateFunction = ???

  initialize()
}
object VoiceWsHandler {
  case object SendIdentify
  case object SendSelectProtocol

  case class WithUDPActor(queue: SourceQueueWithComplete[Message], connectionActor: ActorRef, resume: ResumeData)
      extends Data[ResumeData] {
    def resumeOpt:                       Option[ResumeData]                       = Some(resume)
    override def queueOpt:               Option[SourceQueueWithComplete[Message]] = Some(queue)
    override def heartbeatCancelableOpt: Option[Cancellable]                      = None
  }

  case class WithHeartbeat(
      heartbeatInterval: Int,
      heartbeatCancelable: Cancellable,
      receivedAck: Boolean,
      previousNonce: Option[Int],
      queue: SourceQueueWithComplete[Message],
      connectionActor: ActorRef,
      resume: ResumeData
  ) extends Data[ResumeData] {
    def resumeOpt:                       Option[ResumeData]                       = Some(resume)
    override def queueOpt:               Option[SourceQueueWithComplete[Message]] = Some(queue)
    override def heartbeatCancelableOpt: Option[Cancellable]                      = Some(heartbeatCancelable)
  }

  case class WithDescription(
      secretKey: ByteString,
      heartbeatInterval: Int,
      heartbeatCancelable: Cancellable,
      receivedAck: Boolean,
      previousNonce: Option[Int],
      queue: SourceQueueWithComplete[Message],
      connectionActor: ActorRef,
      resume: ResumeData
  ) extends Data[ResumeData] {
    def resumeOpt:                       Option[ResumeData]                       = Some(resume)
    override def queueOpt:               Option[SourceQueueWithComplete[Message]] = Some(queue)
    override def heartbeatCancelableOpt: Option[Cancellable]                      = Some(heartbeatCancelable)
  }
}
