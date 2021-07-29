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

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

import ackcord.gateway.GatewayProtocol._
import ackcord.util.AckCordGatewaySettings
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketUpgradeResponse}
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Compression, Flow, GraphDSL, Keep, Merge, Partition}
import akka.stream.stage._
import akka.util.ByteString
import cats.syntax.all._
import io.circe
import io.circe.{Encoder, parser}
import io.circe.syntax._

class GatewayHandlerGraphStage(settings: GatewaySettings, prevResume: Option[ResumeData])
    extends GraphStageWithMaterializedValue[
      FlowShape[GatewayMessage[_], GatewayMessage[_]],
      (Future[(Option[ResumeData], Boolean)], Future[Unit])
    ] {
  val in: Inlet[GatewayMessage[_]]   = Inlet("GatewayHandlerGraphStage.in")
  val out: Outlet[GatewayMessage[_]] = Outlet("GatewayHandlerGraphStage.out")

  override def shape: FlowShape[GatewayMessage[_], GatewayMessage[_]] =
    new FlowShape(in, out)

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes
  ): (GraphStageLogic, (Future[(Option[ResumeData], Boolean)], Future[Unit])) = {
    val resumePromise          = Promise[(Option[ResumeData], Boolean)]()
    val successullStartPromise = Promise[Unit]()

    val logic = new TimerGraphStageLogicWithLogging(shape) with InHandler with OutHandler {
      var resume: ResumeData = prevResume.orNull
      var receivedAck        = false
      var currentSeq: Int    = -1

      val HeartbeatTimerKey: String  = "HeartbeatTimer"
      val ReidentifyTimerKey: String = "ReidentifyTimer"

      def currentGatewayInfo(): GatewayInfo = GatewayInfo(
        ShardInfo(
          settings.shardTotal,
          settings.shardNum
        ),
        currentSeq
      )

      def restart(resumable: Boolean, waitBeforeRestart: Boolean): Unit = {
        resumePromise.success(if (resumable) (Some(resume), waitBeforeRestart) else (None, waitBeforeRestart))
        completeStage()
      }

      def identify(): Unit = {
        val message =
          if (resume != null) Resume(resume, currentGatewayInfo())
          else
            Identify(
              IdentifyData(
                token = settings.token,
                properties = IdentifyData.createProperties,
                compress = settings.compress == Compress.PerMessageCompress,
                largeThreshold = settings.largeThreshold,
                shard = Seq(settings.shardNum, settings.shardTotal),
                presence = PresenceData(settings.idleSince, settings.activities, settings.status, afk = settings.afk),
                intents = settings.intents
              )
            )

        push(out, message)
      }

      def handleHello(data: HelloData): Unit = {
        identify()
        receivedAck = true
        scheduleAtFixedRate(HeartbeatTimerKey, 0.millis, data.heartbeatInterval.millis)
      }

      override def onPush(): Unit = {
        val event = grab(in)

        event match {
          case Hello(data, _) => handleHello(data)
          case Dispatch(seq, event, _) =>
            currentSeq = seq
            event match {
              case GatewayEvent.Ready(_, _) | GatewayEvent.Resumed(_) =>
                successullStartPromise.trySuccess(())
              case _ =>
            }

            resume = event match {
              case GatewayEvent.Ready(_, readyData) =>
                readyData.value match {
                  case Right(ready) => ResumeData(settings.token, ready.sessionId, seq)
                  case Left(e) =>
                    log.error(e, "Failed to decode ready event. Stuff will probably break on resume")
                    null
                }

              case _ =>
                if (resume != null) {
                  resume.copy(seq = seq)
                } else null
            }

          case Heartbeat(_, _) => onTimer(HeartbeatTimerKey)
          case HeartbeatACK(_) =>
            log.debug("Received HeartbeatACK")
            receivedAck = true
          case Reconnect(_) =>
            log.debug("Restarting connection because of reconnect")
            restart(resumable = true, waitBeforeRestart = false)
          case InvalidSession(resumable, _) =>
            log.debug(s"Restarting connection because of invalid session. Resumable: $resumable")
            if (!resumable) {
              resume = null
            }

            scheduleOnce(ReidentifyTimerKey, ThreadLocalRandom.current().nextDouble(1, 5).seconds)
          case _ => //Ignore
        }

        if (!hasBeenPulled(in) && !isClosed(in)) pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        if (!resumePromise.isCompleted) {
          resumePromise.trySuccess((Option(resume), false))
        }

        super.onUpstreamFinish()
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        resumePromise.failure(ex)
        successullStartPromise.tryFailure(ex)
        super.onUpstreamFailure(ex)
      }

      override def onDownstreamFinish(cause: Throwable): Unit = {
        if (!resumePromise.isCompleted) resumePromise.trySuccess((Option(resume), false))

        super.onDownstreamFinish(cause)
      }

      override protected def onTimer(timerKey: Any): Unit = {
        timerKey match {
          case HeartbeatTimerKey =>
            if (receivedAck) {
              log.debug("Sending heartbeat")
              emit(out, Heartbeat(Option(resume).map(_.seq), currentGatewayInfo()))
            } else {
              val e = new IllegalStateException("Did not receive HeartbeatACK between heartbeats")
              failStage(e)
              resumePromise.failure(e)
              successullStartPromise.tryFailure(e)
            }

          case ReidentifyTimerKey =>
            identify()
          case _ => //NO-OP
        }
      }

      setHandler(in, this)

      override def onPull(): Unit = if (!hasBeenPulled(in)) pull(in)

      override def postStop(): Unit = {
        val e = new AbruptStageTerminationException(this)
        if (!resumePromise.isCompleted) resumePromise.tryFailure(e)
        if (!successullStartPromise.isCompleted) successullStartPromise.tryFailure(e)
      }

      setHandler(out, this)
    }

    (logic, (resumePromise.future, successullStartPromise.future))
  }
}
object GatewayHandlerGraphStage {

  def flow(wsUri: Uri, settings: GatewaySettings, prevResume: Option[ResumeData])(
      implicit system: ActorSystem[Nothing]
  ): Flow[GatewayMessage[_], GatewayMessage[
    _
  ], (Future[WebSocketUpgradeResponse], Future[(Option[ResumeData], Boolean)], Future[Unit])] = {
    val msgFlow =
      createMessage
        .viaMat(wsFlow(wsUri))(Keep.right)
        .viaMat(
          parseMessage(settings.compress, settings.eventDecoders, ShardInfo(settings.shardTotal, settings.shardNum))
        )(Keep.left)
        .collect {
          case Right(msg) => msg
          case Left(e)    => throw new GatewayJsonException(e.show, e)
        }
        .named("GatewayMessageProcessing")

    val gatewayLifecycle = new GatewayHandlerGraphStage(settings, prevResume).named("GatewayLogic")

    val graph = GraphDSL.create(msgFlow, gatewayLifecycle)(Keep.both) {
      implicit builder => (network, gatewayLifecycle) =>
        import GraphDSL.Implicits._

        val sendMessages     = builder.add(Merge[GatewayMessage[_]](2, eagerComplete = true))
        val receivedMessages = builder.add(Broadcast[GatewayMessage[_]](2, eagerCancel = true))

        // format: OFF
        network ~> receivedMessages
                   receivedMessages ~> gatewayLifecycle ~> sendMessages
        network                                         <~ sendMessages
        // format: ON

        FlowShape(sendMessages.in(1), receivedMessages.out(1))
    }

    Flow.fromGraph(graph).mapMaterializedValue(t => (t._1, t._2._1, t._2._2))
  }

  /** Turn a websocket [[akka.http.scaladsl.model.ws.Message]] into a [[GatewayMessage]]. */
  def parseMessage(compress: Compress, eventDecoders: GatewayProtocol.EventDecoders, shardInfo: ShardInfo)(
      implicit system: ActorSystem[Nothing]
  ): Flow[Message, Either[circe.Error, GatewayMessage[_]], NotUsed] = {
    val stringFlow = compress match {
      case Compress.NoCompress =>
        Flow[Message]
          .collect {
            case t: TextMessage   => t.textStream
            case b: BinaryMessage => b.dataStream.fold(ByteString.empty)(_ ++ _).map(_.utf8String)
          }
          .flatMapConcat(_.fold("")(_ + _))
      case Compress.PerMessageCompress =>
        Flow[Message]
          .collect {
            case t: TextMessage => t.textStream
            case b: BinaryMessage =>
              b.dataStream.fold(ByteString.empty)(_ ++ _).via(Compression.inflate()).map(_.utf8String)
          }
          .flatMapConcat(_.fold("")(_ + _))
      case Compress.ZLibStreamCompress =>
        val graph = GraphDSL.create() { implicit builder =>
          import GraphDSL.Implicits._

          val in = builder.add(Flow[Message])
          val splitBinary = builder.add(
            Partition[Message](
              2,
              {
                case _: TextMessage   => 0
                case _: BinaryMessage => 1
              }
            )
          )
          val splitBinaryText   = splitBinary.out(0).map(_.asInstanceOf[TextMessage])
          val splitBinaryBinary = splitBinary.out(1).map(_.asInstanceOf[BinaryMessage])
          val allStrings        = builder.add(Merge[String](2))

          val unwrapText = Flow[TextMessage].flatMapConcat(_.textStream.fold("")(_ + _))

          // format: OFF
          in ~> splitBinary
                splitBinaryText   ~> unwrapText   ~> allStrings
                splitBinaryBinary.flatMapConcat(_.dataStream) ~> Compression.inflate().map(_.utf8String) ~> allStrings
          // format: ON

          FlowShape(in.in, allStrings.out)
        }

        Flow.fromGraph(graph)
    }

    implicit val logger: LoggingAdapter = Logging(system.classicSystem, "ackcord.gateway.ReceivedWSMessage")

    val withLogging = if (AckCordGatewaySettings().LogReceivedWs) {
      stringFlow.log("Received payload").withAttributes(Attributes.logLevels(onElement = Logging.DebugLevel))
    } else stringFlow

    withLogging.map(parser.parse).statefulMapConcat { () =>
      var seq = -1

      parsed => {
        val wsMessageParsed = parsed
          .flatMap(json => GatewayProtocol.decodeWsMessage(eventDecoders, GatewayInfo(shardInfo, seq), json.hcursor))
        wsMessageParsed.foreach {
          case Dispatch(sequence, _, _) => seq = sequence
          case _                        =>
        }

        List(wsMessageParsed)
      }
    }
  }

  /** Turn a [[GatewayMessage]] into a websocket [[akka.http.scaladsl.model.ws.Message]]. */
  def createMessage(implicit system: ActorSystem[Nothing]): Flow[GatewayMessage[_], Message, NotUsed] = {
    implicit val logger: LoggingAdapter = Logging(system.classicSystem, "ackcord.gateway.SentWSMessage")

    val flow = Flow[GatewayMessage[_]].map { case msg: GatewayMessage[d] =>
      msg match {
        case PresenceUpdate(data, _) => data.activities.foreach(_.requireCanSend())
        case _                       =>
      }

      val json = msg.asJson(wsMessageEncoder.asInstanceOf[Encoder[GatewayMessage[d]]]).noSpaces
      require(json.getBytes.length < 4096, "Can only send at most 4096 bytes in a message over the gateway")
      TextMessage(json)
    }

    if (AckCordGatewaySettings().LogSentWs) flow.log("Sending payload", _.text) else flow
  }

  private def wsFlow(
      wsUri: Uri
  )(implicit system: ActorSystem[Nothing]): Flow[Message, Message, Future[WebSocketUpgradeResponse]] = {
    import akka.actor.typed.scaladsl.adapter._
    Http(system.toClassic).webSocketClientFlow(wsUri)
  }
}
