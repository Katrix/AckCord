/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
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
package ackcord.voice

import java.net.InetSocketAddress

import ackcord.AckCord
import ackcord.data.{RawSnowflake, UserId}
import ackcord.util.{AckCordVoiceSettings, JsonSome, JsonUndefined}
import ackcord.voice.VoiceWsProtocol._
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.ws._
import akka.stream._
import akka.stream.scaladsl.{Compression, Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.util.ByteString
import io.circe
import io.circe.syntax._
import io.circe.{Error, parser}
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

import akka.event.Logging
import akka.stream.typed.scaladsl.ActorSink
import org.slf4j.Logger

object VoiceWsHandler {
  private case class Parameters(
      address: String,
      serverId: RawSnowflake,
      userId: UserId,
      sessionId: String,
      token: String,
      sendTo: Option[ActorRef[AudioAPIMessage]],
      soundProducer: Source[ByteString, NotUsed],
      soundConsumer: Sink[AudioAPIMessage, NotUsed],
      context: ActorContext[Command],
      timers: TimerScheduler[Command],
      log: Logger
  )

  private case class State(
      shuttingDown: Boolean = false,
      resume: Option[ResumeData] = None,
      ssrc: Int = -1,
      previousNonce: Option[Int] = None,
      killSwitch: Option[UniqueKillSwitch] = None,
      secretKeyPromise: Option[Promise[ByteString]] = None,
      sendFirstSinkAck: Option[ActorRef[AckSink.type]] = None,
      queue: Option[SourceQueueWithComplete[VoiceMessage[_]]] = None,
      receivedAck: Boolean = true
  )

  /**
    * Responsible for handling the websocket connection part of voice data.
    * @param address The address to connect to, not including the websocket protocol.
    * @param serverId The serverId
    * @param userId The client userId
    * @param sessionId The session id received in [[ackcord.APIMessage.VoiceStateUpdate]]
    * @param token The token received in [[ackcord.APIMessage.VoiceServerUpdate]]
    * @param sendTo The actor to send all [[AudioAPIMessage]]s to unless noted otherwise
    * @param soundProducer A source which will produce the sound to send.
    * @param soundConsumer A sink which will consume [[AudioAPIMessage.ReceivedData]].
    */
  def apply(
      address: String,
      serverId: RawSnowflake,
      userId: UserId,
      sessionId: String,
      token: String,
      sendTo: Option[ActorRef[AudioAPIMessage]],
      soundProducer: Source[ByteString, NotUsed],
      soundConsumer: Sink[AudioAPIMessage, NotUsed]
  ): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      inactive(
        Parameters(
          address,
          serverId,
          userId,
          sessionId,
          token,
          sendTo,
          soundProducer,
          soundConsumer,
          context,
          timers,
          context.log
        ),
        State()
      )
    }
  }

  private val heartbeatTimerKey: String = "SendHeartbeats"

  private val restartLoginKey: String = "RestartLogin"

  def inactive(parameters: Parameters, state: State): Behavior[Command] = {
    import parameters._
    import state._
    implicit val system: ActorSystem[Nothing] = context.system

    def parseMessage: Flow[Message, Either[circe.Error, VoiceMessage[_]], NotUsed] = {
      val jsonFlow = Flow[Message]
        .collect {
          case t: TextMessage => t.textStream
          case b: BinaryMessage =>
            b.dataStream
              .fold(ByteString.empty)(_ ++ _)
              .via(Compression.inflate())
              .map(_.utf8String)
        }
        .flatMapConcat(_.fold("")(_ + _))

      val withLogging =
        if (AckCordVoiceSettings().LogReceivedWs)
          jsonFlow.log("Received payload").withAttributes(Attributes.logLevels(onElement = Logging.DebugLevel))
        else jsonFlow

      withLogging.map(parser.parse(_).flatMap(_.as[VoiceMessage[_]]))
    }

    def createMessage: Flow[VoiceMessage[_], Message, NotUsed] = {
      val baseFlow = Flow[VoiceMessage[_]].map(_.asJson.noSpaces)

      val withLogging =
        if (AckCordVoiceSettings().LogSentWs)
          baseFlow.log("Sending payload")
        else baseFlow

      withLogging.map(TextMessage.apply)
    }

    def wsUri: Uri = Uri(s"wss://$address").withQuery(Query("v" -> AckCord.DiscordVoiceVersion))

    def wsFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
      Http(system.toClassic).webSocketClientFlow(wsUri)

    def becomeActive(): Behavior[Command] = {
      //Send identify
      val msg = resume match {
        case Some(resumeData) => Resume(resumeData)
        case None             => Identify(IdentifyData(serverId, userId, sessionId, token))
      }
      queue.get.offer(msg)

      active(parameters, state.copy(sendFirstSinkAck = None, resume = Some(ResumeData(serverId, sessionId, token))))
    }

    Behaviors
      .receiveMessage[Command] {
        case Login =>
          log.info("Logging in")
          val src = Source.queue[VoiceMessage[_]](64, OverflowStrategy.fail).named("GatewayQueue")

          val sink = ActorSink
            .actorRefWithBackpressure(
              ref = context.self,
              messageAdapter = SinkMessage,
              onInitMessage = InitSink,
              ackMessage = AckSink,
              onCompleteMessage = CompletedSink,
              onFailureMessage = FailedSink
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
            .addAttributes(ActorAttributes.supervisionStrategy { e =>
              context.log.error("Error in stream: ", e)
              Supervision.Resume
            })
            .run()

          context.pipeToSelf(future) {
            case Success(value) => UpgradeResponse(value)
            case Failure(e)     => SendExeption(e)
          }

          inactive(parameters, state.copy(queue = Some(sourceQueue)))

        case UpgradeResponse(InvalidUpgradeResponse(response, cause)) =>
          response.discardEntityBytes()
          queue.foreach(_.complete())
          throw new IllegalStateException(s"Could not connect to gateway: $cause") //TODO

        case UpgradeResponse(ValidUpgrade(response, _)) =>
          log.debug("Valid login: {}\nGoing to active", response.entity.toString)
          response.discardEntityBytes()

          sendFirstSinkAck.foreach(act => act ! AckSink)

          becomeActive()

        case InitSink(replyTo) => inactive(parameters, state.copy(sendFirstSinkAck = Some(replyTo)))

        case ConnectionDied =>
          if (shuttingDown) {
            log.info("UDP connection stopped when shut down in inactive state. Stopping.")
            Behaviors.stopped
          } else {
            inactive(parameters, state.copy(killSwitch = None))
          }
      }
      .receiveSignal {
        case (_, PostStop) =>
          queue.foreach(_.complete())
          Behaviors.stopped
      }
  }

  private def active(parameters: Parameters, state: State): Behavior[Command] = {
    import parameters._
    import state._

    def becomeInactive(newState: State): Behavior[Command] =
      inactive(parameters, newState.copy(queue = None, receivedAck = true, ssrc = -1))

    def stopOrGotoInactive(newState: State): Behavior[Command] = {
      if (shuttingDown) {
        log.info("Websocket and UDP connection completed when shutting down. Stopping.")
        Behaviors.stopped
      } else {
        log.info("Websocket and UDP connection died. Logging in again.")
        if (!timers.isTimerActive(restartLoginKey)) {
          context.self ! Login
        }
        becomeInactive(newState)
      }
    }

    Behaviors
      .receiveMessage[Command] {
        case InitSink(replyTo) =>
          replyTo ! AckSink
          Behaviors.same

        case CompletedSink =>
          timers.cancel(heartbeatTimerKey)

          if (killSwitch.isEmpty) {
            stopOrGotoInactive(state.copy(queue = None))
          } else {
            log.info("Websocket connection completed. Waiting for UDP connection.")
            active(parameters, state.copy(queue = None))
          }

        case ConnectionDied =>
          if (queue.isEmpty) {
            stopOrGotoInactive(state.copy(killSwitch = None))
          } else {
            log.info("UDP connection completed. Waiting for websocket connection.")
            active(parameters, state.copy(killSwitch = None))
          }

        case FailedSink(e) =>
          //TODO: Inspect error and only do fresh if needed
          log.error("Encountered websocket error", e)
          context.self ! Restart(fresh = true, 1.seconds)
          Behaviors.same

        case SendHeartbeat =>
          if (receivedAck) {
            val nonce = System.currentTimeMillis().toInt

            queue.get.offer(Heartbeat(nonce))
            log.debug("Sent Heartbeat")

            active(parameters, state.copy(receivedAck = false, previousNonce = Some(nonce)))
          } else {
            log.warn("Did not receive HeartbeatACK between heartbeats. Restarting.")
            context.self ! Restart(fresh = false, 0.millis)
            Behaviors.same
          }

        case IPDiscoveryResult(VoiceUDPFlow.FoundIP(localAddress, localPort)) =>
          log.info("Found IP and port")
          queue.get.offer(
            SelectProtocol(
              protocol = "udp",
              address = localAddress,
              port = localPort,
              mode = "xsalsa20_poly1305"
            )
          )
          Behaviors.same

        case SetSpeaking(speaking) =>
          queue.foreach(
            _.offer(Speaking(speaking = speaking, delay = JsonSome(0), ssrc = JsonUndefined, userId = JsonUndefined))
          )
          Behaviors.same

        case Logout =>
          log.info("Logging out")
          queue.get.complete()
          killSwitch.get.shutdown()
          active(parameters, state.copy(shuttingDown = true))

        case Restart(fresh, waitDur) =>
          log.info("Restarting")
          queue.get.complete()
          killSwitch.foreach(_.shutdown())
          timers.startSingleTimer(restartLoginKey, Login, waitDur)
          if (fresh) {
            active(parameters, state.copy(resume = None))
          } else {
            Behaviors.same
          }
        case SinkMessage(replyTo, Left(e)) =>
          log.error("Encountered websocket parsing error", e)
          context.self ! Restart(fresh = false, 1.seconds)
          replyTo ! AckSink
          Behaviors.same
        case SinkMessage(replyTo, Right(voiceMessage)) =>
          val newState = handleWsMessage(parameters, state, voiceMessage)

          replyTo ! AckSink
          active(parameters, newState)
      }
      .receiveSignal {
        case (_, PostStop) =>
          queue.foreach(_.complete())
          Behaviors.stopped
      }
  }

  private def handleWsMessage(parameters: Parameters, state: State, voiceMessage: VoiceMessage[_]): State = {
    import parameters._
    import state._
    implicit val system: ActorSystem[Nothing] = context.system

    voiceMessage match {
      case Ready(ReadyData(readySsrc, port, address, _, _)) =>
        log.debug("Received ready")
        val keyPromise = Promise[ByteString]

        val (killSwitch, (futIp, watchDone)) = soundProducer
          .viaMat(KillSwitches.single)(Keep.right)
          .viaMat(
            VoiceUDPFlow
              .flow(
                new InetSocketAddress(address, port),
                readySsrc,
                serverId,
                userId,
                keyPromise.future
              )
              .watchTermination()(Keep.both)
          )(Keep.both)
          .to(soundConsumer)
          .run()

        context.pipeToSelf(futIp) {
          case Success(value) => IPDiscoveryResult(value)
          case Failure(e)     => SendExeption(e)
        }
        context.pipeToSelf(watchDone)(_ => ConnectionDied)

        state.copy(ssrc = readySsrc, secretKeyPromise = Some(keyPromise), killSwitch = Some(killSwitch))

      case Hello(heartbeatInterval) =>
        context.self ! SendHeartbeat
        timers.startTimerAtFixedRate(heartbeatTimerKey, SendHeartbeat, (heartbeatInterval * 0.75).toInt.millis)

        state.copy(receivedAck = true, previousNonce = None)

      case HeartbeatACK(nonce) =>
        log.debug("Received HeartbeatACK")
        if (previousNonce.contains(nonce))
          state.copy(receivedAck = true)
        else {
          log.warn("Did not receive correct nonce in HeartbeatACK. Restarting.")
          context.self ! Restart(fresh = false, 500.millis)
          state
        }

      case SessionDescription(SessionDescriptionData(_, secretKey)) =>
        log.debug("Received session description")
        sendTo.foreach(_ ! AudioAPIMessage.Ready(serverId, userId))
        secretKeyPromise.get.success(secretKey)
        state

      case Speaking(SpeakingData(isSpeaking, delay, userSsrc, JsonSome(speakingUserId))) =>
        sendTo.foreach { actor =>
          actor ! AudioAPIMessage.UserSpeaking(
            speakingUserId,
            userSsrc.toOption,
            isSpeaking,
            delay.toOption,
            serverId,
            userId
          )
        }
        state

      case Resumed                => state //NO-OP
      case IgnoreMessage12        => state //NO-OP
      case IgnoreClientDisconnect => state //NO-OP
    }
  }

  sealed trait Command

  private case class InitSink(replyTo: ActorRef[AckSink.type]) extends Command
  private case object AckSink                                  extends Command
  private case object CompletedSink                            extends Command
  private case class FailedSink(e: Throwable)                  extends Command
  private case class SinkMessage(replyTo: ActorRef[AckSink.type], message: Either[Error, VoiceMessage[_]])
      extends Command

  private case class UpgradeResponse(response: WebSocketUpgradeResponse) extends Command
  private case class SendExeption(e: Throwable)                          extends Command

  private case object SendHeartbeat                                   extends Command
  private case object ConnectionDied                                  extends Command
  private case class IPDiscoveryResult(foundIP: VoiceUDPFlow.FoundIP) extends Command

  /**
    * Send this to a [[VoiceWsHandler]] to make it go from inactive to active
    */
  case object Login extends Command

  /**
    * Send this to a [[VoiceWsHandler]] to stop it gracefully.
    */
  case object Logout extends Command

  /**
    * Sent to [[VoiceWsHandler]]. Used to set the client as speaking or not.
    */
  case class SetSpeaking(speaking: Boolean) extends Command

  /**
    * Send this to an [[VoiceWsHandler]] to restart the connection.
    * @param fresh If it should start fresh. If this is false, it will try to continue the connection.
    * @param waitDur The amount of time to wait until connecting again.
    */
  case class Restart(fresh: Boolean, waitDur: FiniteDuration) extends Command
}
