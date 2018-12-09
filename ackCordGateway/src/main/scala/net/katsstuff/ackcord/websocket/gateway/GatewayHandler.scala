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
package net.katsstuff.ackcord.websocket.gateway

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.{ActorSystem, Props, Status}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, ValidUpgrade, WebSocketUpgradeResponse}
import akka.pattern.pipe
import akka.stream.scaladsl._
import akka.stream.{ActorAttributes, KillSwitches, Materializer, SharedKillSwitch, Supervision}
import net.katsstuff.ackcord.AckCord
import net.katsstuff.ackcord.websocket.AbstractWsHandler
import net.katsstuff.ackcord.websocket.gateway.GatewayHandler.ConnectionDied

/**
  * Responsible for normal websocket communication with Discord.
  * Some REST messages can't be sent until this has authenticated.
  * @param rawWsUri The raw uri to connect to without params
  * @param settings The settings to use.
  * @param mat The [[https://doc.akka.io/api/akka/current/akka/stream/Materializer.html Materializer]] to use.
  * @param source A source of gateway messages.
  * @param sink A sink which will be sent all the dispatches of the gateway.
  */
class GatewayHandler(
    rawWsUri: Uri,
    settings: GatewaySettings,
    source: Source[GatewayMessage[_], NotUsed],
    sink: Sink[Dispatch[_], NotUsed]
)(implicit val mat: Materializer)
    extends AbstractWsHandler[GatewayMessage[_], ResumeData] {
  import AbstractWsHandler._
  import context.dispatcher

  implicit private val system: ActorSystem = context.system
  private var killSwitch: SharedKillSwitch = _
  private var retryCount                   = 0

  val wsUri: Uri = rawWsUri.withQuery(Query("v" -> AckCord.DiscordApiVersion, "encoding" -> "json"))

  def wsFlow: Flow[GatewayMessage[_], Dispatch[_], (Future[WebSocketUpgradeResponse], Future[Option[ResumeData]])] =
    GatewayHandlerGraphStage.flow(wsUri, settings, resume)

  override def postStop(): Unit =
    if (killSwitch != null) killSwitch.shutdown()

  private def retryLogin() = {
    if (retryCount < 5) {
      //TODO: Guard against repeatedly sending identify and failing here. Ratelimits and that stuff
      retryCount += 1
      system.scheduler.scheduleOnce(5.seconds)(self ! Login)
    } else {
      throw new Exception("Max retry count exceeded")
    }
  }

  def inactive: Receive = {
    case Login =>
      log.info("Logging in")
      killSwitch = KillSwitches.shared("GatewayComplete")

      val (wsUpgrade, newResumeData) = source
        .viaMat(wsFlow)(Keep.right)
        .via(killSwitch.flow)
        .toMat(sink)(Keep.left)
        .addAttributes(ActorAttributes.supervisionStrategy(e => {
          log.error(e, "Error in stream")
          Supervision.Resume
        }))
        .run()

      newResumeData.map(ConnectionDied).pipeTo(self)
      wsUpgrade.pipeTo(self)

    case ValidUpgrade(response, _) =>
      retryCount = 0
      log.info("Valid login. Going to active. Response: {}", response.entity.toString)
      response.discardEntityBytes()
      context.become(active)

    case InvalidUpgradeResponse(response, cause) =>
      response.discardEntityBytes()
      killSwitch.shutdown()
      throw new IllegalStateException(s"Could not connect to gateway: $cause") //TODO

    //Handling this too just in case
    case Status.Failure(e) =>
      log.error(e, "Websocket error. Retry count {}", retryCount)
      killSwitch.shutdown()
      killSwitch = null
      retryLogin()

    case GatewayHandler.ConnectionDied(newResume) =>
      log.error("Connection died before starting. Retry count {}", retryCount)
      println(newResume)
      killSwitch.shutdown()
      killSwitch = null
      retryLogin()
  }

  def active: Receive = {
    case ConnectionDied(newResume) =>
      if (shuttingDown) {
        log.info("Websocket connection completed. Stopping.")
        context.stop(self)
      } else {
        resume = newResume
        killSwitch.shutdown()
        killSwitch = null

        log.info("Websocket connection died. Logging in again. Retry count {}", retryCount)
        context.become(inactive)
        retryLogin()
      }

    case Status.Failure(e) =>
      log.error(e, "Websocket error. Retry count {}", retryCount)
      killSwitch.shutdown()
      killSwitch = null
      context.become(inactive)
      retryLogin()

    case Logout =>
      log.info("Shutting down")
      killSwitch.shutdown()
      shuttingDown = true
  }

  override def receive: Receive = inactive
}
object GatewayHandler {

  def props(
      rawWsUri: Uri,
      settings: GatewaySettings,
      source: Source[GatewayMessage[_], NotUsed],
      sink: Sink[Dispatch[_], NotUsed]
  )(implicit mat: Materializer): Props = Props(new GatewayHandler(rawWsUri, settings, source, sink))

  private case class ConnectionDied(resume: Option[ResumeData])
}
