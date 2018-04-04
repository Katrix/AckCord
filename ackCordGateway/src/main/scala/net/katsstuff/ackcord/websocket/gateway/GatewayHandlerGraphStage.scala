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

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketUpgradeResponse}
import akka.stream.scaladsl.{Compression, Flow, GraphDSL, Keep, Merge}
import akka.stream.stage._
import akka.stream.{Attributes, FanOutShape2, FlowShape, Inlet, Outlet, OverflowStrategy}
import akka.util.ByteString
import io.circe
import io.circe.parser
import io.circe.syntax._
import net.katsstuff.ackcord.websocket.gateway.GatewayHandlerGraphStage.Restart
import net.katsstuff.ackcord.websocket.gateway.GatewayProtocol._
import net.katsstuff.ackcord.util.AckCordSettings

class GatewayHandlerGraphStage(settings: GatewaySettings, prevResume: Option[ResumeData])
  extends GraphStageWithMaterializedValue[FanOutShape2[GatewayMessage[_], GatewayMessage[_], Dispatch[_]], Future[
    Option[ResumeData]
    ]] {
  val in:          Inlet[GatewayMessage[_]] = Inlet("GatewayHandlerGraphStage.in")
  val dispatchOut: Outlet[Dispatch[_]]      = Outlet("GatewayHandlerGraphStage.dispatchOut")

  val out: Outlet[GatewayMessage[_]] = Outlet("GatewayHandlerGraphStage.out")

  override def shape: FanOutShape2[GatewayMessage[_], GatewayMessage[_], Dispatch[_]] =
    new FanOutShape2(in, out, dispatchOut)

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes
  ): (GraphStageLogic, Future[Option[ResumeData]]) = {
    val promise = Promise[Option[ResumeData]]

    val logic = new TimerGraphStageLogicWithLogging(shape) with InHandler with OutHandler {
      var resume: ResumeData = _
      var receivedAck = false
      var restarting  = false

      val HeartbeatTimerKey:                   String  = "HeartbeatTimer"
      def restartTimerKey(resumable: Boolean): Restart = Restart(resumable)

      def restart(resumable: Boolean, time: FiniteDuration): Unit = {
        complete(out)
        restarting = true
        scheduleOnce(restartTimerKey(resumable), time)
      }

      def handleHello(data: HelloData): Unit = {
        val response = prevResume match {
          case Some(resumeData) =>
            resume = resumeData
            Resume(resumeData)
          case None =>
            val identifyObject = IdentifyData(
              token = settings.token,
              properties = IdentifyData.createProperties,
              compress = true,
              largeThreshold = settings.largeThreshold,
              shard = Seq(settings.shardNum, settings.shardTotal),
              presence = StatusData(settings.idleSince, settings.activity, settings.status, afk = settings.afk)
            )

            Identify(identifyObject)
        }

        push(out, response)

        receivedAck = true
        onTimer(HeartbeatTimerKey)
        schedulePeriodically(HeartbeatTimerKey, data.heartbeatInterval.millis)
      }

      override def onPush(): Unit = {
        grab(in) match {
          case Hello(data) => handleHello(data)
          case dispatch @ Dispatch(seq, event) =>
            resume = event match {
              case GatewayEvent.Ready(readyData) => ResumeData(settings.token, readyData.sessionId, seq)
              case _ =>
                if (resume != null) {
                  resume.copy(seq = seq)
                } else null
            }

            emit(dispatchOut, dispatch)
          case Heartbeat(_) => onTimer(HeartbeatTimerKey)
          case HeartbeatACK =>
            log.debug("Received HeartbeatACK")
            receivedAck = true
          case Reconnect                 => restart(resumable = true, 100.millis)
          case InvalidSession(resumable) => restart(resumable, 5.seconds)
          case _                         => //Ignore
        }

        if (!hasBeenPulled(in)) pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        if (!restarting) {
          if (!promise.isCompleted) {
            promise.success(Option(resume))
          }

          super.onUpstreamFinish()
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        promise.failure(ex)
        super.onUpstreamFailure(ex)
      }

      override protected def onTimer(timerKey: Any): Unit = {
        timerKey match {
          case HeartbeatTimerKey =>
            if (receivedAck) {
              log.debug("Sending heartbeat")
              emit(out, Heartbeat(Option(resume).map(_.seq)))
            } else {
              val e = new IllegalStateException("Did not receive HeartbeatACK between heartbeats")
              fail(out, e)
              promise.failure(e)
            }

          case Restart(resumable) =>
            restarting = false
            promise.success(if (resumable) Some(resume) else None)
            completeStage()
        }
      }

      setHandler(in, this)

      override def onPull(): Unit = if (!hasBeenPulled(in)) pull(in)

      override def onDownstreamFinish(): Unit =
        if (!restarting) {
          if (!promise.isCompleted) {
            promise.success(Option(resume))
          }

          super.onDownstreamFinish()
        }

      setHandler(out, this)
      setHandler(dispatchOut, this)
    }

    (logic, promise.future)
  }
}
object GatewayHandlerGraphStage {
  private case class Restart(resumable: Boolean)

  def flow(wsUri: Uri, settings: GatewaySettings, prevResume: Option[ResumeData])(
      implicit system: ActorSystem
  ): Flow[GatewayMessage[_], Dispatch[_], (Future[WebSocketUpgradeResponse], Future[Option[ResumeData]])] = {
    val msgFlow =
      createMessage
        .viaMat(wsFlow(wsUri))(Keep.right)
        .viaMat(parseMessage)(Keep.left)
        .collect {
          case Right(msg) => msg
          case Left(e)    => throw e
        }
        .named("GatewayMessageProcessing")

    val wsGraphStage = new GatewayHandlerGraphStage(settings, prevResume).named("GatewayLogic")

    val graph = GraphDSL.create(msgFlow, wsGraphStage)(Keep.both) {
      implicit builder => (msgFlowShape, wsHandlerShape) =>
        import GraphDSL.Implicits._

        val wsMessages = builder.add(Merge[GatewayMessage[_]](2))

        //TODO: Make overflow strategy configurable
        val buffer = builder.add(Flow[GatewayMessage[_]].buffer(32, OverflowStrategy.dropHead))

        // format: OFF

        msgFlowShape.out ~> buffer ~> wsHandlerShape.in
        wsHandlerShape.out0 ~> wsMessages.in(1)
        msgFlowShape.in                                   <~ wsMessages.out

        // format: ON

        FlowShape(wsMessages.in(0), wsHandlerShape.out1)
    }

    Flow.fromGraph(graph)
  }

  /**
    * Turn a websocket [[Message]] into a [[GatewayMessage]].
    */
  def parseMessage(implicit system: ActorSystem): Flow[Message, Either[circe.Error, GatewayMessage[_]], NotUsed] = {
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

    withLogging.map(parser.parse(_).flatMap(_.as[GatewayMessage[_]]))
  }

  /**
    * Turn a [[GatewayMessage]] into a websocket [[Message]].
    */
  def createMessage(implicit system: ActorSystem): Flow[GatewayMessage[_], Message, NotUsed] = {
    val flow = Flow[GatewayMessage[_]].map { msg =>
      msg match {
        case StatusUpdate(data) => data.game.foreach(_.requireCanSend())
        case _ =>
      }

      val json = msg.asJson.noSpaces
      require(json.getBytes.length < 4096, "Can only send at most 4096 bytes in a message over the gateway")
      TextMessage(json)
    }

    if (AckCordSettings().LogSentWs) flow.log("Sending payload", _.text) else flow
  }

  private def wsFlow(
      wsUri: Uri
  )(implicit system: ActorSystem): Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
    Http().webSocketClientFlow(wsUri)
}
