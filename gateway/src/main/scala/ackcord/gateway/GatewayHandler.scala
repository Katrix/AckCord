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

import java.util.concurrent.ThreadLocalRandom

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
      handlerFlow: Flow[GatewayMessage[_], GatewayMessage[_], NotUsed],
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
  ) => Flow[GatewayMessage[_], GatewayMessage[
    _
  ], (Future[WebSocketUpgradeResponse], Future[(Option[ResumeData], Boolean)], Future[Unit])]

  /**
    * Responsible for normal websocket communication with Discord.
    * Some REST messages can't be sent until this has authenticated.
    * @param rawWsUri The raw uri to connect to without params
    * @param settings The settings to use.
    * @param handlerFlow A flow that sends messages to the gateway, and handles received messages.
    */
  def apply(
      rawWsUri: Uri,
      settings: GatewaySettings,
      handlerFlow: Flow[GatewayMessage[_], GatewayMessage[_], NotUsed],
      wsFlow: WsFlowFunc = defaultWsFlow
  ): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      inactive(Parameters(rawWsUri, settings, handlerFlow, context, timers, context.log), State(), wsFlow)
    }
  }

  def defaultWsFlow(
      wsUri: Uri,
      parameters: Parameters,
      state: State
  ): Flow[GatewayMessage[_], GatewayMessage[
    _
  ], (Future[WebSocketUpgradeResponse], Future[(Option[ResumeData], Boolean)], Future[Unit])] =
    GatewayHandlerGraphStage.flow(wsUri, parameters.settings, state.resume)(parameters.context.system)

  private def retryLogin(
      forceWait: Boolean,
      parameters: Parameters,
      state: State,
      timers: TimerScheduler[Command],
      wsFlow: WsFlowFunc
  ): Behavior[Command] = {
    if (state.retryCount < 8) {
      val backoffWaitTime =
        if (state.retryCount == 0) 0.seconds
        else Math.pow(2, state.retryCount).seconds

      val waitTime =
        if (forceWait) Seq(ThreadLocalRandom.current().nextDouble(1, 5).seconds, backoffWaitTime).max
        else backoffWaitTime

      timers.startSingleTimer("RetryLogin", Login, waitTime)
      inactive(parameters, state.copy(killSwitch = None, retryCount = state.retryCount + 1), wsFlow)
    } else {
      throw new Exception("Max retry count exceeded")
    }
  }

  private def handlePeerClosedConnection(
      e: PeerClosedConnectionException,
      parameters: Parameters,
      state: State,
      timers: TimerScheduler[Command],
      wsFlow: WsFlowFunc,
      log: Logger
  ) = {
    e.closeCode match {
      //Unknown error
      case 4000 =>
        log.error("An unknown error happened in gateway. Reconnecting")
        retryLogin(forceWait = false, parameters, state, timers, wsFlow)

      //TODO: Maybe bubble up some of these errors up higher instead of stopping the JVM
      //Authenticate failed
      case 4004 =>
        log.error("Authentication failed to WS gateway. Stopping JVM")
        sys.exit(-1)

      //Invalid shard
      case 4010 =>
        log.error("Invalid shard passed to WS gateway. Stopping JVM")
        sys.exit(-1)

      //Sharding required
      case 4011 =>
        log.error("Sharding required to log into WS gateway. Stopping JVM")
        sys.exit(-1)

      // Invalid seq
      case 4007 =>
        log.warn("""|Tried to resume with an invalid seq. Likely a bug in AckCord. 
                    |Submit a bug with a debug log on the issue tracker""".stripMargin)
        retryLogin(forceWait = true, parameters, state.copy(resume = None), timers, wsFlow)

      //Session timed out
      case 4009 =>
        log.debug("""|Tried to resume with a timed out session""".stripMargin)
        retryLogin(forceWait = true, parameters, state.copy(resume = None), timers, wsFlow)

      //Ratelimited
      case 4008 =>
        throw new GatewayRatelimitedException(e)

      //Disallowed or invalid intents
      case 4013 | 4014 =>
        log.error("Invalid or disallow intents specified. Stopping JVM")
        sys.exit(-1)

      case _ => throw e
    }
  }

  private def shutdownStream(state: State): Unit =
    state.killSwitch.foreach(_.shutdown())

  private def inactive(parameters: Parameters, state: State, wsFlow: WsFlowFunc): Behavior[Command] = {
    import akka.actor.typed.scaladsl.adapter._
    import parameters._
    import state._
    implicit val oldSystem: classic.ActorSystem = context.system.toClassic

    val wsUri: Uri = {
      val alwaysPresent = Seq("v" -> AckCord.DiscordApiVersion, "encoding" -> "json")
      val query =
        if (settings.compress == Compress.ZLibStreamCompress) alwaysPresent :+ ("compress" -> "zlib") else alwaysPresent

      rawWsUri.withQuery(Query(query: _*))
    }

    Behaviors
      .receiveMessage[Command] {
        case Login =>
          log.info("Logging in")

          val (switch, (wsUpgrade, newResumeData, loginSuccess)) =
            Flow
              .fromGraph(KillSwitches.single[GatewayMessage[_]])
              .viaMat(wsFlow(wsUri, parameters, state))(Keep.both)
              .join(handlerFlow)
              .addAttributes(ActorAttributes.supervisionStrategy { e =>
                log.error("Error in stream", e)
                Supervision.Resume
              })
              .named("GatewayWebsocket")
              .run()

          context.pipeToSelf(newResumeData) {
            case Success((resumeData, shouldWait)) => ConnectionDied(resumeData, shouldWait)
            case Failure(e)                        => SendException(e)
          }

          context.pipeToSelf(wsUpgrade) {
            case Success(value) => UpgradeResponse(value)
            case Failure(e)     => SendException(e)
          }

          context.pipeToSelf(loginSuccess) {
            case Success(_) => ResetRetryCount
            case Failure(e) => SendException(e)
          }

          inactive(parameters, state.copy(killSwitch = Some(switch)), wsFlow)

        case UpgradeResponse(ValidUpgrade(response, _)) =>
          log.info("Valid login. Going to active. Response: {}", response.entity.toString)
          response.discardEntityBytes()
          active(parameters, state, wsFlow)

        case UpgradeResponse(InvalidUpgradeResponse(response, cause)) =>
          response.discardEntityBytes()
          shutdownStream(state)
          throw new IllegalStateException(s"Could not connect to gateway: $cause") //TODO

        case ResetRetryCount =>
          log.debug("Managed to connect successfully, resetting retry count")
          active(parameters, state.copy(retryCount = 0), wsFlow)

        case SendException(e: PeerClosedConnectionException) =>
          handlePeerClosedConnection(e, parameters, state, timers, wsFlow, log)

        case SendException(e) =>
          log.error("Websocket error. Retry count {}", retryCount, e)
          shutdownStream(state)
          retryLogin(forceWait = true, parameters, state, timers, wsFlow)

        case GatewayHandler.ConnectionDied(_, _) =>
          log.error("Connection died before starting. Retry count {}", retryCount)
          shutdownStream(state)
          retryLogin(forceWait = false, parameters, state, timers, wsFlow)

        case Logout =>
          log.warn("Logged out before connection could be established. This is likely a bug")
          Behaviors.stopped
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
        case ConnectionDied(newResume, waitBeforeRestart) =>
          if (shuttingDown) {
            log.info("Websocket connection completed. Stopping.")
            Behaviors.stopped
          } else {
            shutdownStream(state)

            log.info("Websocket connection died. Logging in again. Retry count {}", retryCount)
            retryLogin(waitBeforeRestart, parameters, state.copy(resume = newResume), timers, wsFlow)
          }

        case ResetRetryCount =>
          log.debug("Managed to connect successfully, resetting retry count")
          active(parameters, state.copy(retryCount = 0), wsFlow)

        case SendException(e: PeerClosedConnectionException) =>
          handlePeerClosedConnection(e, parameters, state, timers, wsFlow, log)

        case SendException(e) =>
          log.error("Websocket error. Retry count {}", retryCount, e)
          shutdownStream(state)
          retryLogin(forceWait = true, parameters, state, timers, wsFlow)

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

  private case object ResetRetryCount                                                       extends Command
  private case class ConnectionDied(resume: Option[ResumeData], waitBeforeRestart: Boolean) extends Command
  private case class UpgradeResponse(response: WebSocketUpgradeResponse)                    extends Command
  private case class SendException(e: Throwable)                                            extends Command
}
