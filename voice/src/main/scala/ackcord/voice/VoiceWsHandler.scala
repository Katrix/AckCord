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

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import ackcord.AckCord
import ackcord.data.{RawSnowflake, UserId}
import ackcord.util.{AckCordVoiceSettings, JsonSome, JsonUndefined}
import ackcord.voice.VoiceWsProtocol._
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.adapter._
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl.{Compression, Flow, Keep, Source, SourceQueueWithComplete}
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{ActorAttributes, Attributes, OverflowStrategy, Supervision}
import akka.util.ByteString
import io.circe
import io.circe._
import io.circe.syntax._
import org.slf4j.Logger

object VoiceWsHandler {

  private case class Parameters(
      context: ActorContext[Command],
      timers: TimerScheduler[Command],
      log: Logger,
      parent: ActorRef[VoiceHandler.Command],
      queue: SourceQueueWithComplete[VoiceMessage[_]],
      sendTo: Option[ActorRef[AudioAPIMessage]],
      heart: ActorRef[WsHeart.Command],
      address: String,
      serverId: RawSnowflake,
      userId: UserId,
      sessionId: String,
      token: String
  )

  private def parseMessage(
      implicit system: ActorSystem[Nothing]
  ): Flow[Message, Either[circe.Error, VoiceMessage[_]], NotUsed] = {
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

  def createMessage(implicit system: ActorSystem[Nothing]): Flow[VoiceMessage[_], Message, NotUsed] = {
    val baseFlow = Flow[VoiceMessage[_]].map(_.asJson.noSpaces)

    val withLogging =
      if (AckCordVoiceSettings().LogSentWs)
        baseFlow.log("Sending payload")
      else baseFlow

    withLogging.map(TextMessage.apply)
  }

  def wsUri(address: String): Uri = Uri(s"wss://$address").withQuery(Query("v" -> AckCord.DiscordVoiceVersion))

  def wsFlow(address: String, system: ActorSystem[Nothing]): Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
    Http(system.toClassic).webSocketClientFlow(wsUri(address))

  def runWsFlow(ctx: ActorContext[Command], log: Logger, address: String)(
      implicit system: ActorSystem[Nothing]
  ): SourceQueueWithComplete[VoiceMessage[_]] = {
    log.debug("Logging in")
    val src = Source.queue[VoiceMessage[_]](64, OverflowStrategy.fail).named("GatewayQueue")

    val sink = ActorSink
      .actorRefWithBackpressure(
        ref = ctx.self,
        messageAdapter = SinkMessage,
        onInitMessage = InitSink,
        ackMessage = AckSink,
        onCompleteMessage = CompletedSink,
        onFailureMessage = FailedSink
      )
      .named("GatewaySink")

    val flow = createMessage
      .viaMat(wsFlow(address, system))(Keep.right)
      .viaMat(parseMessage)(Keep.left)
      .named("Gateway")

    log.debug("WS uri: {}", wsUri(address))
    val (sourceQueue, future) = src
      .viaMat(flow)(Keep.both)
      .toMat(sink)(Keep.left)
      .addAttributes(ActorAttributes.supervisionStrategy { e =>
        log.error("Error in stream: ", e)
        Supervision.Resume
      })
      .run()

    ctx.pipeToSelf(future) {
      case Success(value) => UpgradeResponse(value)
      case Failure(e)     => SendExeption(e)
    }

    sourceQueue
  }

  def apply(
      address: String,
      parent: ActorRef[VoiceHandler.Command],
      sendTo: Option[ActorRef[AudioAPIMessage]],
      serverId: RawSnowflake,
      userId: UserId,
      sessionId: String,
      token: String
  ): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      implicit val system: ActorSystem[Nothing] = ctx.system
      val log                                   = ctx.log

      val queue = runWsFlow(ctx, log, address)
      val heart = ctx.spawn(WsHeart(ctx.self), "Heart")

      ctx.self ! SendHandshake

      wsHandling(
        Parameters(ctx, timers, log, parent, queue, sendTo, heart, address, serverId, userId, sessionId, token),
        isRestarting = false,
        canResume = false
      )
    }
  }

  def wsHandling(
      parameters: Parameters,
      isRestarting: Boolean,
      canResume: Boolean
  ): Behavior[Command] = {
    import parameters._
    implicit val system: ActorSystem[Nothing] = context.system

    def handleError(e: Throwable): Behavior[Command] = e match {
      case e: PeerClosedConnectionException =>
        e.closeCode match {
          //Disconnected
          case 4014 =>
            log.debug("Got 4014 close code. Stopping")
            Behaviors.stopped
          //Session no longer valid
          case 4006 =>
            log.debug("Got 4006 close code. Stopping")
            Behaviors.stopped
          //Server crashed
          case 4015 =>
            log.debug("Got 4015 close code. Restarting")
            context.self ! Restart(fresh = false, 0.seconds)
            Behaviors.same

          case _ => throw e
        }
      case _ => throw e
    }

    Behaviors
      .receiveMessage[Command] {
        case InitSink(replyTo) =>
          replyTo ! AckSink
          Behaviors.same
        case CompletedSink if isRestarting =>
          if (!timers.isTimerActive("relogin")) {
            context.self ! Relogin
          }

          Behaviors.same

        case Relogin =>
          val queue = runWsFlow(context, log, address)
          context.self ! SendHandshake

          wsHandling(parameters.copy(queue = queue), isRestarting = false, canResume = false)

        case SendHandshake =>
          log.debug("Sending handshake")
          val msg =
            if (canResume) Resume(ResumeData(serverId, sessionId, token))
            else Identify(IdentifyData(serverId, userId, sessionId, token))
          queue.offer(msg)

          Behaviors.same

        case CompletedSink =>
          log.debug("Got CompletedSink when not in restarting mode. Stopping")
          Behaviors.stopped

        case FailedSink(e) =>
          handleError(e)

        case UpgradeResponse(InvalidUpgradeResponse(response, cause)) =>
          response.discardEntityBytes()
          queue.complete()
          throw new IllegalStateException(s"Could not connect to gateway: $cause") //TODO

        case UpgradeResponse(ValidUpgrade(response, _)) =>
          log.debug("Valid login: {}", response.entity.toString)
          response.discardEntityBytes()
          Behaviors.same

        case SendExeption(e) =>
          handleError(e)

        case SinkMessage(replyTo, Left(e)) =>
          log.error("Encountered websocket parsing error", e)
          context.self ! Restart(fresh = false, 1.seconds)
          replyTo ! AckSink
          Behaviors.same
        case SinkMessage(replyTo, Right(voiceMessage)) =>
          handleWsMessage(parameters, voiceMessage)

          replyTo ! AckSink
          wsHandling(parameters, isRestarting, canResume = true)

        case SendHeartbeat(nonce) =>
          queue.offer(Heartbeat(nonce))
          Behaviors.same

        case GotLocalIP(localAddress, localPort) =>
          log.debug(s"Found IP and port: $localAddress $localPort")
          queue.offer(
            SelectProtocol(
              protocol = "udp",
              address = localAddress,
              port = localPort,
              mode = "xsalsa20_poly1305"
            )
          )

          Behaviors.same

        case SetSpeaking(speaking) =>
          queue.offer(Speaking(speaking = speaking, delay = JsonSome(0), ssrc = JsonUndefined, userId = JsonUndefined))
          Behaviors.same

        case Restart(fresh, duration) =>
          queue.complete()

          if (duration > 0.seconds) {
            timers.startSingleTimer("relogin", Relogin, duration)
          }

          heart ! WsHeart.StopBeating

          wsHandling(parameters, isRestarting = true, canResume = canResume && !fresh)

        case Shutdown =>
          queue.complete()
          Behaviors.same
      }
      .receiveSignal {
        case (_, PostStop) =>
          queue.complete()
          Behaviors.stopped
      }
  }

  private def handleWsMessage(
      parameters: Parameters,
      voiceMessage: VoiceMessage[_]
  ): Unit = {
    import parameters._

    voiceMessage match {
      case Ready(ReadyData(readySsrc, port, address, _)) =>
        log.debug(s"Received ready with ssrc port address: $readySsrc $port $address")

        parent ! VoiceHandler.GotServerIP(address, port, readySsrc)

      case Hello(HelloData(heartbeatInterval)) =>
        val nonce = System.currentTimeMillis().toInt
        heart ! WsHeart.StartBeating(heartbeatInterval, nonce)
        context.self ! SendHeartbeat(nonce)

      case HeartbeatACK(nonce) =>
        heart ! WsHeart.BeatAck(nonce)

      case SessionDescription(SessionDescriptionData(_, secretKey)) =>
        log.debug("Received session description")
        parent ! VoiceHandler.GotSecretKey(secretKey)

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

      case Resumed                => //NO-OP
      case IgnoreMessage12        => //NO-OP
      case IgnoreClientDisconnect => //NO-OP

      //Client sent events. Just here to make the exhaustivity checker happy
      //TODO: Fix this at some point
      case Heartbeat(_)      =>
      case Identify(_)       =>
      case Resume(_)         =>
      case SelectProtocol(_) =>
      case Speaking(_)       =>
    }
  }

  sealed trait Command

  private case object Relogin                                  extends Command
  private case object SendHandshake                            extends Command
  case class Restart(fresh: Boolean, duration: FiniteDuration) extends Command
  case object Shutdown                                         extends Command

  private case object AckSink
  private case class InitSink(replyTo: ActorRef[AckSink.type]) extends Command
  private case object CompletedSink                            extends Command
  private case class FailedSink(e: Throwable)                  extends Command
  private case class SinkMessage(replyTo: ActorRef[AckSink.type], message: Either[Error, VoiceMessage[_]])
      extends Command

  private case class UpgradeResponse(response: WebSocketUpgradeResponse) extends Command
  private case class SendExeption(e: Throwable)                          extends Command

  case class GotLocalIP(localAddress: String, localPort: Int) extends Command

  case class SendHeartbeat(nonce: Int)           extends Command
  case class SetSpeaking(speaking: SpeakingFlag) extends Command
}
