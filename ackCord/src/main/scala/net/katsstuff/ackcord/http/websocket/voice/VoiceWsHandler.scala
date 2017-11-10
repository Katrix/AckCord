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
import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.event.Logging
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Compression, Flow}
import akka.stream.{Attributes, Materializer}
import akka.util.ByteString
import io.circe
import io.circe.parser
import io.circe.syntax._
import net.katsstuff.ackcord.data.{RawSnowflake, UserId}
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler
import net.katsstuff.ackcord.http.websocket.voice.VoiceUDPHandler.{Disconnect, DoIPDiscovery, FoundIP, StartConnection}
import net.katsstuff.ackcord.util.AckCordSettings
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
  * @param mat The [[Materializer]] to use
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

  private implicit val system: ActorSystem = context.system
  private var ssrc:            Int         = -1
  private var previousNonce:   Option[Int] = None
  private var localAddress:    String      = _
  private var localPort:       Int         = -1
  private var connectionActor: ActorRef    = _

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

  override def createMessage: Flow[VoiceMessage[_], Message, NotUsed] = Flow[VoiceMessage[_]].map { msg =>
    val payload = msg.asJson.noSpaces
    if (AckCordSettings().LogSentWs) {
      log.debug("Sending payload: {}", payload)
    }
    TextMessage(payload)
  }

  override def wsUri: Uri = Uri(s"wss://$address").withQuery(Query("v" -> AckCord.DiscordVoiceVersion))

  override def preStart(): Unit = {
    self ! SendIdentify
    resume = Some(ResumeData(serverId, sessionId, token))
  }

  override def becomeActive(): Unit = {
    super.becomeActive()
    self ! SendIdentify //We act first here
  }

  override def becomeInactive(): Unit = {
    super.becomeInactive()
    ssrc = -1
  }

  override def inactive: Receive = whenInactiveBase.orElse {
    case ConnectionDied =>
      if (shuttingDown) {
        log.info("UDP connection stopped when shut down in inactive state. Stopping.")
        context.stop(self)
      }
      connectionActor = null
  }

  override def active: Receive = {
    val base: Receive = {
      case InitSink =>
        sender() ! AckSink
      case CompletedSink =>
        queue = null
        timers.cancel(heartbeatTimerKey)

        if (connectionActor == null) {
          if (shuttingDown) {
            log.info("Websocket and UDP connection completed when shutting down. Stopping.")
            context.stop(self)
          } else {
            log.info("Websocket and UDP connection completed. Logging in again.")
            if (!timers.isTimerActive(restartLoginKey)) {
              self ! Login
            }
            becomeInactive()
          }
        } else {
          log.info("Websocket connection completed. Waiting for UDP connection.")
        }
      case ConnectionDied =>
        connectionActor = null

        if (queue == null) {
          if (shuttingDown) {
            log.info("Websocket and UDP connection completed when shutting down. Stopping.")
            context.stop(self)
          } else {
            log.info("Websocket and UDP connection completed. Logging in again.")
            if (!timers.isTimerActive(restartLoginKey)) {
              self ! Login
            }
            becomeInactive()
          }
        } else {
          log.info("UDP connection completed. Waiting for websocket connection.")
        }
      case Status.Failure(e) =>
        //TODO: Inspect error
        log.error(e, "Encountered websocket error")
        self ! Restart(fresh = false, 1.seconds)
      case Left(NonFatal(e)) =>
        log.error(e, "Encountered websocket parsing error")
        self ! Restart(fresh = false, 1.seconds)
      case SendIdentify =>
        queue.offer(Identify(IdentifyData(serverId, userId, sessionId, token)))
        resume = Some(ResumeData(serverId, sessionId, token))
      case SendSelectProtocol =>
        require(localPort != -1)

        val data = SelectProtocolData("udp", SelectProtocolConnectionData(localAddress, localPort, "xsalsa20_poly1305"))
        queue.offer(SelectProtocol(data))
        localAddress = null
        localPort = -1
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
      case FoundIP(foundLocalAddress, foundPort) =>
        self ! SendSelectProtocol
        localAddress = foundLocalAddress
        localPort = foundPort
      case SetSpeaking(speaking) =>
        if (queue != null && ssrc != -1) {
          queue.offer(Speaking(SpeakingData(speaking, None, ssrc, Some(userId))))
        }
      case Logout =>
        queue.complete()
        connectionActor ! Disconnect
        log.info("Logging out")
        shuttingDown = true
      case Restart(fresh, waitDur) =>
        queue.complete()
        connectionActor ! Disconnect
        timers.startSingleTimer(restartLoginKey, Login, waitDur)
        log.info("Restarting")
        if (fresh) {
          resume = None
        }
    }

    base.orElse(handleWsMessages)
  }

  /**
    * Handles all websocket messages received
    */
  def handleWsMessages: Receive = {
    case Right(Ready(ReadyData(readySsrc, port, _, _))) =>
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
    case Right(SessionDescription(SessionDescriptionData(_, secretKey))) =>
      connectionActor ! StartConnection(secretKey)
    case Right(Speaking(SpeakingData(isSpeaking, delay, userSsrc, Some(speakingUserId)))) =>
      sendTo.foreach(_ ! AudioAPIMessage.UserSpeaking(speakingUserId, userSsrc, isSpeaking, delay, serverId, userId))
    case Right(IgnoreMessage12)        => //NO-OP
    case Right(IgnoreClientDisconnect) => //NO-OP
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

  private case object SendIdentify
  private case object SendSelectProtocol
  private case object ConnectionDied

  /**
    * Sent to [[VoiceWsHandler]]. Used to set the client as speaking or not.
    */
  case class SetSpeaking(speaking: Boolean)
}
