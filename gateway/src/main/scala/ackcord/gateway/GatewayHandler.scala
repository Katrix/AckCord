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
package ackcord.gateway

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import ackcord.AckCord
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{
  InvalidUpgradeResponse,
  PeerClosedConnectionException,
  ValidUpgrade,
  WebSocketUpgradeResponse
}
import akka.stream._
import akka.stream.scaladsl._
import akka.{NotUsed, actor => classic}
import org.slf4j.Logger

object GatewayHandler {

  private[ackcord] case class Parameters(
      rawWsUri: Uri,
      settings: GatewaySettings,
      source: Source[GatewayMessage[_], NotUsed],
      sink: Sink[Dispatch[_], NotUsed],
      context: ActorContext[Command],
      timers: TimerScheduler[Command],
      log: Logger
  )

  private[ackcord] case class State(
      shuttingDown: Boolean = false,
      resume: Option[ResumeData] = None,
      killSwitch: Option[UniqueKillSwitch] = None,
      retryCount: Int = 0
  )

  type WsFlowFunc = (
      Uri,
      Parameters,
      State
  ) => Flow[GatewayMessage[_], Dispatch[_], (Future[WebSocketUpgradeResponse], Future[Option[ResumeData]])]

  /**
    * Responsible for normal websocket communication with Discord.
    * Some REST messages can't be sent until this has authenticated.
    * @param rawWsUri The raw uri to connect to without params
    * @param settings The settings to use.
    * @param source A source of gateway messages.
    * @param sink A sink which will be sent all the dispatches of the gateway.
    */
  def apply(
      rawWsUri: Uri,
      settings: GatewaySettings,
      source: Source[GatewayMessage[_], NotUsed],
      sink: Sink[Dispatch[_], NotUsed],
      wsFlow: WsFlowFunc = defaultWsFlow
  ): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      inactive(Parameters(rawWsUri, settings, source, sink, context, timers, context.log), State(), wsFlow)
    }
  }

  def defaultWsFlow(
      wsUri: Uri,
      parameters: Parameters,
      state: State
  ): Flow[GatewayMessage[_], Dispatch[_], (Future[WebSocketUpgradeResponse], Future[Option[ResumeData]])] =
    GatewayHandlerGraphStage.flow(wsUri, parameters.settings, state.resume)(parameters.context.system)

  private def retryLogin(
      parameters: Parameters,
      state: State,
      timers: TimerScheduler[Command],
      wsFlow: WsFlowFunc
  ): Behavior[Command] = {
    if (state.retryCount < 5) {
      //TODO: Guard against repeatedly sending identify and failing here. Ratelimits and that stuff
      timers.startSingleTimer("RetryLogin", Login, 5.seconds)
      inactive(parameters, state.copy(killSwitch = None, retryCount = state.retryCount + 1), wsFlow)
    } else {
      throw new Exception("Max retry count exceeded")
    }
  }

  private def shutdownStream(state: State): Unit =
    state.killSwitch.foreach(_.shutdown())

  private def inactive(parameters: Parameters, state: State, wsFlow: WsFlowFunc): Behavior[Command] = {
    import akka.actor.typed.scaladsl.adapter._
    import parameters._
    import state._
    implicit val oldSystem: classic.ActorSystem = context.system.toClassic

    val wsUri: Uri = rawWsUri.withQuery(Query("v" -> AckCord.DiscordApiVersion, "encoding" -> "json"))

    Behaviors
      .receiveMessage[Command] {
        case Login =>
          log.info("Logging in")

          val (switch, (wsUpgrade, newResumeData)) = source
            .viaMat(KillSwitches.single)(Keep.right)
            .viaMat(wsFlow(wsUri, parameters, state))(Keep.both)
            .toMat(sink)(Keep.left)
            .addAttributes(ActorAttributes.supervisionStrategy(e => {
              log.error("Error in stream", e)
              Supervision.Resume
            }))
            .named("GatewayWebsocket")
            .run()

          context.pipeToSelf(newResumeData) {
            case Success(value) => ConnectionDied(value)
            case Failure(e)     => SendException(e)
          }

          context.pipeToSelf(wsUpgrade) {
            case Success(value) => UpgradeResponse(value)
            case Failure(e)     => SendException(e)
          }

          inactive(parameters, state.copy(killSwitch = Some(switch)), wsFlow)

        case UpgradeResponse(ValidUpgrade(response, _)) =>
          log.info("Valid login. Going to active. Response: {}", response.entity.toString)
          response.discardEntityBytes()
          active(parameters, state.copy(retryCount = 0), wsFlow)

        case UpgradeResponse(InvalidUpgradeResponse(response, cause)) =>
          response.discardEntityBytes()
          shutdownStream(state)
          throw new IllegalStateException(s"Could not connect to gateway: $cause") //TODO

        case SendException(e: PeerClosedConnectionException) =>
          e.closeCode match {
            //TODO: Maybe bubble up some of these errors up higher instead of stopping the JVM
            //Authenticate failed
            case 4004 =>
              log.error("Authentication failed to WS gateway. Stopping JVM")
              sys.exit(-1)

            //Invalid shard
            case 4010 =>
              log.error("Invalid shard passed to WS gateway. Stopping JVM")
              sys.exit(-1)

            //
            case 4011 =>
              log.error("Sharding required to log into WS gateway. Stopping JVM")
              sys.exit(-1)

            //Invalid seq when resuming or session timed out
            case 4007 | 4009 =>
              retryLogin(parameters, state.copy(resume = None), timers, wsFlow)

            case 4012 =>
              log.error("Invalid intents specified. Stopping JVM")
              sys.exit(-1)

            case _ => throw e
          }

        case SendException(e) =>
          log.error("Websocket error. Retry count {}", retryCount, e)
          shutdownStream(state)
          retryLogin(parameters, state, timers, wsFlow)

        case GatewayHandler.ConnectionDied(_) =>
          log.error("Connection died before starting. Retry count {}", retryCount)
          shutdownStream(state)
          retryLogin(parameters, state, timers, wsFlow)

        case Logout =>
          //TODO: Fix receiving logout right before going to active
          Behaviors.same
      }
      .receiveSignal {
        case (_, PostStop) =>
          shutdownStream(state)
          Behaviors.stopped
      }
  }

  private def active(parameters: Parameters, state: State, wsFlow: WsFlowFunc): Behavior[Command] = {
    import parameters._
    import state._

    Behaviors
      .receiveMessage[Command] {
        case ConnectionDied(newResume) =>
          if (shuttingDown) {
            log.info("Websocket connection completed. Stopping.")
            Behaviors.stopped
          } else {
            shutdownStream(state)

            log.info("Websocket connection died. Logging in again. Retry count {}", retryCount)
            retryLogin(parameters, state.copy(resume = newResume), timers, wsFlow)
          }

        case SendException(e: PeerClosedConnectionException) =>
          e.closeCode match {
            //TODO: Maybe bubble up some of these errors up higher instead of stopping the JVM
            //Authenticate failed
            case 4004 =>
              log.error("Authentication failed to WS gateway. Stopping JVM")
              sys.exit(-1)

            //Invalid shard
            case 4010 =>
              log.error("Invalid shard passed to WS gateway. Stopping JVM")
              sys.exit(-1)

            //
            case 4011 =>
              log.error("Sharding required to log into WS gateway. Stopping JVM")
              sys.exit(-1)

            //Invalid seq when resuming or session timed out
            case 4007 | 4009 =>
              retryLogin(parameters, state.copy(resume = None), timers, wsFlow)

            case 4012 =>
              log.error("Invalid intents specified. Stopping JVM")
              sys.exit(-1)

            case _ => throw e
          }

        case SendException(e) =>
          log.error("Websocket error. Retry count {}", retryCount, e)
          shutdownStream(state)
          retryLogin(parameters, state, timers, wsFlow)

        case Logout =>
          log.info("Shutting down")
          shutdownStream(state)
          active(parameters, state.copy(shuttingDown = true), wsFlow)

        case Login =>
          Behaviors.same

        case UpgradeResponse(_) =>
          Behaviors.same
      }
      .receiveSignal {
        case (_, PostStop) =>
          shutdownStream(state)
          Behaviors.stopped
      }
  }

  sealed trait Command

  /**
    * Send this to a [[GatewayHandler]] to make it go from inactive to active
    */
  case object Login extends Command

  /**
    * Send this to a [[GatewayHandler]] to stop it gracefully.
    */
  case object Logout extends Command

  private case class ConnectionDied(resume: Option[ResumeData])          extends Command
  private case class UpgradeResponse(response: WebSocketUpgradeResponse) extends Command
  private case class SendException(e: Throwable)                         extends Command
}
