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

import ackcord.data.{RawSnowflake, UserId}
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.slf4j.Logger

object VoiceHandler {

  private case class Parameters(
      context: ActorContext[Command],
      log: Logger,
      serverId: RawSnowflake,
      userId: UserId,
      sendTo: Option[ActorRef[AudioAPIMessage]],
      soundProducer: Source[ByteString, NotUsed],
      soundConsumer: Sink[AudioAPIMessage, NotUsed],
      wsHandler: ActorRef[VoiceWsHandler.Command]
  )

  private case class State(
      udpHandler: Option[ActorRef[VoiceUDPHandler.Command]],
      currentUdpSettings: Option[UDPSettings],
      wsHandlerStopped: Boolean
  )

  private case class UDPSettings(
      address: String,
      port: Int,
      ssrc: Int,
      localIp: Option[GotLocalIP]
  )

  def apply(
      address: String,
      serverId: RawSnowflake,
      userId: UserId,
      sessionId: String,
      token: String,
      sendTo: Option[ActorRef[AudioAPIMessage]],
      soundProducer: Source[ByteString, NotUsed],
      soundConsumer: Sink[AudioAPIMessage, NotUsed]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    val wsHandler = ctx.spawn(
      VoiceWsHandler(address, ctx.self, sendTo, serverId, userId, sessionId, token),
      "WsHandler"
    )
    ctx.watch(wsHandler)

    handler(
      Parameters(
        ctx,
        ctx.log,
        serverId,
        userId,
        sendTo,
        soundProducer,
        soundConsumer,
        wsHandler
      ),
      State(None, None, wsHandlerStopped = false)
    )
  }

  def handler(
      parameters: Parameters,
      state: State
  ): Behavior[Command] = {
    import parameters._
    import state._
    Behaviors
      .receiveMessage[Command] {
        case ip @ GotLocalIP(localAddress, localPort) =>
          wsHandler ! VoiceWsHandler.GotLocalIP(localAddress, localPort)
          handler(parameters, state.copy(currentUdpSettings = currentUdpSettings.map(_.copy(localIp = Some(ip)))))

        case gotIp @ GotServerIP(address, port, ssrc) =>
          currentUdpSettings match {
            case Some(UDPSettings(`address`, `port`, `ssrc`, localIp)) =>
              //Same as our existing connection, so we don't recreate it
              //Instead we send the local ip to the WS handler if we have it
              //If we don't have it, it will be sent when we do

              udpHandler.get ! VoiceUDPHandler.SetSecretKey(None)
              localIp.foreach(ip => wsHandler ! VoiceWsHandler.GotLocalIP(ip.localAddress, ip.localPort))
              Behaviors.same

            case Some(_) =>
              context.watchWith(udpHandler.get, gotIp)
              udpHandler.get ! VoiceUDPHandler.Shutdown

              Behaviors.same

            case None =>
              val udpHandler = context.spawn(
                VoiceUDPHandler(
                  address,
                  port,
                  ssrc,
                  serverId,
                  userId,
                  soundProducer,
                  soundConsumer,
                  context.self
                ),
                "UDPHandler"
              )
              context.watch(udpHandler)

              handler(
                parameters,
                state.copy(
                  udpHandler = Some(udpHandler),
                  currentUdpSettings = Some(UDPSettings(address, port, ssrc, None))
                )
              )
          }

        case GotSecretKey(key) =>
          udpHandler.get ! VoiceUDPHandler.SetSecretKey(Some(key))
          sendTo.foreach(_ ! AudioAPIMessage.Ready(serverId, userId))

          Behaviors.same

        case SetSpeaking(speaking, soundshare, priority) =>
          val flags = Seq(
            speaking   -> SpeakingFlag.Microphone,
            soundshare -> SpeakingFlag.Soundshare,
            priority   -> SpeakingFlag.Priority
          ).collect {
              case (b, f) if b => f
            }
            .fold(SpeakingFlag.None)(_ ++ _)

          wsHandler ! VoiceWsHandler.SetSpeaking(flags)
          Behaviors.same

        case Logout =>
          wsHandler ! VoiceWsHandler.Shutdown
          udpHandler.foreach(_ ! VoiceUDPHandler.Shutdown)

          shutdownPhase(parameters, state)
      }
      .receiveSignal {
        case (_, Terminated(_)) =>
          throw new IllegalStateException("Voice Handler stopped unexpectedly")
      }
  }

  private def shutdownPhase(parameters: Parameters, state: State): Behavior[Command] = Behaviors.receiveSignal {
    case (ctx, Terminated(actor)) if actor == parameters.wsHandler =>
      if (state.udpHandler.isEmpty) {
        ctx.log.info("Both WS and UDP handler shut down. Stopping")
        Behaviors.stopped
      } else {
        ctx.log.info("WS handler shut down. Waiting for UDP handler")
        shutdownPhase(parameters, state.copy(wsHandlerStopped = true))
      }
    case (ctx, Terminated(actor)) if state.udpHandler.contains(actor) =>
      if (state.wsHandlerStopped) {
        ctx.log.info("Both WS and UDP handler shut down. Stopping")
        Behaviors.stopped
      } else {
        ctx.log.info("UDP handler shut down. Waiting for WS handler")
        shutdownPhase(parameters, state.copy(udpHandler = None))
      }
  }

  sealed trait Command

  private[voice] case class GotLocalIP(localAddress: String, localPort: Int)   extends Command
  private[voice] case class GotSecretKey(key: ByteString)                      extends Command
  private[voice] case class GotServerIP(address: String, port: Int, ssrc: Int) extends Command

  case class SetSpeaking(speaking: Boolean, soundshare: Boolean = false, priority: Boolean = false) extends Command

  /**
    * Send this to a [[VoiceWsHandler]] to stop it gracefully.
    */
  case object Logout extends Command

}
