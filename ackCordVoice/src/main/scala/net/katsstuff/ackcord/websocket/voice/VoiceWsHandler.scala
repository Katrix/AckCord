/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord.websocket.voice

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.NotUsed
import akka.pattern.pipe
import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl.{Compression, Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{Attributes, Materializer, OverflowStrategy}
import akka.util.ByteString
import io.circe
import io.circe.syntax._
import io.circe.{parser, Error}
import net.katsstuff.ackcord.data.{RawSnowflake, UserId}
import net.katsstuff.ackcord.util.{AckCordSettings, JsonSome, JsonUndefined}
import net.katsstuff.ackcord.websocket.AbstractWsHandler
import net.katsstuff.ackcord.websocket.voice.VoiceUDPHandler._
import net.katsstuff.ackcord.{AckCord, AudioAPIMessage}

/**
  * Responsible for handling the websocket connection part of voice data.
  * @param address The address to connect to, not including the websocket protocol.
  * @param serverId The serverId
  * @param userId The client userId
  * @param sessionId The session id received in [[net.katsstuff.ackcord.APIMessage.VoiceStateUpdate]]
  * @param token The token received in [[net.katsstuff.ackcord.APIMessage.VoiceServerUpdate]]
  * @param sendTo The actor to send all [[AudioAPIMessage]]s to unless noted otherwise
  * @param sendSoundTo The actor to send [[AudioAPIMessage.ReceivedData]] to.
  * @param mat The [[https://doc.akka.io/api/akka/current/akka/stream/Materializer.html Materializer]] to use
  */
class VoiceWsHandler(
    address: String,
    serverId: RawSnowflake,
    userId: UserId,
    sessionId: String,
    token: String,
    sendTo: Option[ActorRef],
    sendSoundTo: Option[ActorRef]
)(implicit val mat: Materializer)
    extends AbstractWsHandler[VoiceMessage[_], ResumeData] {

  import AbstractWsHandler._
  import VoiceWsHandler._
  import VoiceWsProtocol._
  import context.dispatcher

  private implicit val system: ActorSystem = context.system

  private var ssrc:            Int         = -1
  private var previousNonce:   Option[Int] = None
  private var connectionActor: ActorRef    = _

  private var sendFirstSinkAck: Option[ActorRef]                         = None
  var queue:                    SourceQueueWithComplete[VoiceMessage[_]] = _

  var receivedAck = true

  def heartbeatTimerKey: String = "SendHeartbeats"

  def restartLoginKey: String = "RestartLogin"

  def parseMessage: Flow[Message, Either[circe.Error, VoiceMessage[_]], NotUsed] = {
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

    withLogging.map(parser.parse(_).flatMap(_.as[VoiceMessage[_]]))
  }

  def createMessage: Flow[VoiceMessage[_], Message, NotUsed] = {
    val baseFlow = Flow[VoiceMessage[_]].map(_.asJson.noSpaces)

    val withLogging = if (AckCordSettings().LogSentWs) {
      baseFlow.log("Sending payload")
    } else baseFlow

    withLogging.map(TextMessage.apply)
  }

  override def wsUri: Uri = Uri(s"wss://$address").withQuery(Query("v" -> AckCord.DiscordVoiceVersion))

  /**
    * The flow to use to send and receive messages with
    */
  def wsFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
    Http().webSocketClientFlow(wsUri)

  override def postStop(): Unit =
    if (queue != null) queue.complete()

  def becomeActive(): Unit = {
    context.become(active)

    //Send identify
    val msg = resume match {
      case Some(resumeData) => Resume(resumeData)
      case None             => Identify(IdentifyData(serverId, userId, sessionId, token))
    }
    queue.offer(msg)

    resume = Some(ResumeData(serverId, sessionId, token))
  }

  def becomeInactive(): Unit = {
    context.become(inactive)
    queue = null
    receivedAck = true
    ssrc = -1
  }

  def inactive: Receive = {
    case Login =>
      log.info("Logging in")
      val src = Source.queue[VoiceMessage[_]](64, OverflowStrategy.fail).named("GatewayQueue")

      val sink = Sink
        .actorRefWithAck[Either[Error, VoiceMessage[_]]](
          ref = self,
          onInitMessage = InitSink,
          ackMessage = AckSink,
          onCompleteMessage = CompletedSink
        )
        .named("GatewaySink")

      val flow = createMessage
        .viaMat(wsFlow)(Keep.right)
        .viaMat(parseMessage)(Keep.left)
        .named("Gateway")

      log.debug("WS uri: {}", wsUri)
      val (sourceQueue, future) = src
        .viaMat(flow)(Keep.both)
        .toMat(sink)(Keep.left)
        .run()

      future.pipeTo(self)

      queue = sourceQueue

    case InvalidUpgradeResponse(response, cause) =>
      response.discardEntityBytes()
      queue.complete()
      throw new IllegalStateException(s"Could not connect to gateway: $cause") //TODO

    case ValidUpgrade(response, _) =>
      log.debug("Valid login: {}\nGoing to active", response.entity.toString)
      response.discardEntityBytes()

      sendFirstSinkAck.foreach(act => act ! AckSink)
      sendFirstSinkAck = null

      becomeActive()

    case InitSink => sendFirstSinkAck = Some(sender())

    case ConnectionDied =>
      if (shuttingDown) {
        log.info("UDP connection stopped when shut down in inactive state. Stopping.")
        context.stop(self)
      }
      connectionActor = null
  }

  def active: Receive = {
    val base: Receive = {
      case InitSink =>
        sender() ! AckSink

      case CompletedSink =>
        queue = null
        timers.cancel(heartbeatTimerKey)

        if (connectionActor == null) {
          stopOrGotoInactive()
        } else {
          log.info("Websocket connection completed. Waiting for UDP connection.")
        }

      case ConnectionDied =>
        connectionActor = null

        if (queue == null) {
          stopOrGotoInactive()
        } else {
          log.info("UDP connection completed. Waiting for websocket connection.")
        }

      case Status.Failure(e) =>
        //TODO: Inspect error and only do fresh if needed
        log.error(e, "Encountered websocket error")
        self ! Restart(fresh = true, 1.seconds)

      case Left(NonFatal(e)) =>
        log.error(e, "Encountered websocket parsing error")
        self ! Restart(fresh = false, 1.seconds)

      case SendHeartbeat =>
        if (receivedAck) {
          val nonce = System.currentTimeMillis().toInt

          queue.offer(Heartbeat(nonce))
          log.debug("Sent Heartbeat")

          receivedAck = false
          previousNonce = Some(nonce)
        } else {
          log.warning("Did not receive HeartbeatACK between heartbeats. Restarting.")
          self ! Restart(fresh = false, 0.millis)
        }

      case FoundIP(localAddress, localPort) =>
        log.info("Found IP and port")
        queue.offer(
          SelectProtocol(
            SelectProtocolData("udp", SelectProtocolConnectionData(localAddress, localPort, "xsalsa20_poly1305"))
          )
        )

      case SetSpeaking(speaking) =>
        if (queue != null && ssrc != -1) {
          queue.offer(Speaking(SpeakingData(speaking, None, ssrc, Some(userId))))
        }

      case Logout =>
        log.info("Logging out")
        queue.complete()
        connectionActor ! Disconnect
        shuttingDown = true

      case Restart(fresh, waitDur) =>
        log.info("Restarting")
        queue.complete()
        if (connectionActor != null) {
          connectionActor ! Disconnect
        }
        timers.startSingleTimer(restartLoginKey, Login, waitDur)
        if (fresh) {
          resume = None
        }
    }

    base.orElse(handleWsMessages)
  }

  override def receive: Receive = inactive

  def stopOrGotoInactive(): Unit = {
    if (shuttingDown) {
      log.info("Websocket and UDP connection completed when shutting down. Stopping.")
      context.stop(self)
    } else {
      log.info("Websocket and UDP connection died. Logging in again.")
      if (!timers.isTimerActive(restartLoginKey)) {
        self ! Login
      }
      becomeInactive()
    }
  }

  /**
    * Handles all websocket messages received
    */
  def handleWsMessages: Receive = {
    case Right(Ready(ReadyData(readySsrc, port, _, _))) =>
      log.debug("Received ready")
      ssrc = readySsrc
      connectionActor = context.actorOf(
        VoiceUDPHandler.props(address, port, ssrc, sendTo, sendSoundTo, serverId, userId),
        "VoiceUDPHandler"
      )
      connectionActor ! DoIPDiscovery(self)
      context.watchWith(connectionActor, ConnectionDied)

      sender() ! AckSink

    case Right(Hello(heartbeatInterval)) =>
      self ! SendHeartbeat
      timers.startPeriodicTimer(heartbeatTimerKey, SendHeartbeat, (heartbeatInterval * 0.75).toInt.millis)
      receivedAck = true
      previousNonce = None

      sender() ! AckSink

    case Right(HeartbeatACK(nonce)) =>
      log.debug("Received HeartbeatACK")
      if (previousNonce.contains(nonce)) {
        receivedAck = true
      } else {
        log.warning("Did not receive correct nonce in HeartbeatACK. Restarting.")
        self ! Restart(fresh = false, 500.millis)
      }

      sender() ! AckSink

    case Right(SessionDescription(SessionDescriptionData(_, secretKey))) =>
      log.debug("Received session description")
      connectionActor ! StartConnection(secretKey)
      sender() ! AckSink

    case Right(Speaking(SpeakingData(isSpeaking, delay, userSsrc, JsonSome(speakingUserId)))) =>
      sendTo.foreach(_ ! AudioAPIMessage.UserSpeaking(speakingUserId, userSsrc.toOption, isSpeaking, delay.toOption, serverId, userId))
      sender() ! AckSink

    case Right(Resumed)                => sender() ! AckSink //NO-OP
    case Right(IgnoreMessage12)        => sender() ! AckSink //NO-OP
    case Right(IgnoreClientDisconnect) => sender() ! AckSink //NO-OP
  }
}
object VoiceWsHandler {
  def props(
      address: String,
      serverId: RawSnowflake,
      userId: UserId,
      sessionId: String,
      token: String,
      sendTo: Option[ActorRef],
      sendSoundTo: Option[ActorRef]
  )(implicit mat: Materializer): Props =
    Props(new VoiceWsHandler(address, serverId, userId, sessionId, token, sendTo, sendSoundTo))

  private case object InitSink
  private case object AckSink
  private case object CompletedSink

  case object SendHeartbeat

  private case object ConnectionDied

  /**
    * Sent to [[VoiceWsHandler]]. Used to set the client as speaking or not.
    */
  case class SetSpeaking(speaking: Boolean)

  /**
    * Send this to an [[VoiceWsHandler]] to restart the connection.
    * @param fresh If it should start fresh. If this is false, it will try to continue the connection.
    * @param waitDur The amount of time to wait until connecting again.
    */
  case class Restart(fresh: Boolean, waitDur: FiniteDuration)
}
