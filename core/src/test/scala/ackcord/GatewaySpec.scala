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
package ackcord

import scala.concurrent.Future

import ackcord.gateway.{GatewayHandler, _}
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.stream.scaladsl.{Compression, Flow, GraphDSL, Keep, Merge, Sink, Source}
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{FlowShape, OverflowStrategy}
import akka.util.ByteString
import io.circe.{Encoder, parser}

object MockedGatewayHandler {
  def apply(settings: GatewaySettings, gateway: ActorRef[MockedGateway.GatewayCommand]) =
    GatewayHandler(
      Uri./,
      settings,
      Flow
        .fromSinkAndSourceCoupled(
          Sink.ignore.mapMaterializedValue(_ => NotUsed),
          Source.maybe[GatewayMessage[_]].mapMaterializedValue(_ => NotUsed)
        ),
      testingWsFlow(gateway)
    )

  def testingWsFlow(gateway: ActorRef[MockedGateway.GatewayCommand])(
      wsUri: Uri,
      parameters: GatewayHandler.Parameters,
      state: GatewayHandler.State
  ): Flow[GatewayMessage[_], GatewayMessage[_], (Future[WebSocketUpgradeResponse], Future[Option[ResumeData]])] = {
    implicit val system: ActorSystem[Nothing] = parameters.context.system
    val response                              = ValidUpgrade(HttpResponse(), None)

    val sendToServer =
      Sink.foreach[MockedGateway.MessageFromClient](gateway ! _).contramap[Message](MockedGateway.MessageFromClient)
    val sendToClient = ActorSource
      .actorRef[Message](PartialFunction.empty, PartialFunction.empty, 128, OverflowStrategy.fail)
      .mapMaterializedValue { actor =>
        gateway ! MockedGateway.SetClient(actor)
        Future.successful(response)
      }

    val wsFlow = Flow.fromSinkAndSourceCoupledMat(sendToServer, sendToClient)(Keep.right)

    val msgFlow =
      GatewayHandlerGraphStage.createMessage
        .viaMat(wsFlow)(Keep.right)
        .viaMat(GatewayHandlerGraphStage.parseMessage)(Keep.left)
        .named("Gateway")
        .collect {
          case Right(msg) => msg
          case Left(e)    => throw e
        }

    val wsGraphStage = new GatewayHandlerGraphStage(parameters.settings, state.resume)

    val graph = GraphDSL.create(msgFlow, wsGraphStage)(Keep.both) { implicit builder => (msgFlowG, wsGraph) =>
      import GraphDSL.Implicits._

      val wsMessages = builder.add(Merge[GatewayMessage[_]](2))
      val buffer     = builder.add(Flow[GatewayMessage[_]].buffer(32, OverflowStrategy.dropHead))

      // format: OFF

      msgFlowG.out ~> buffer ~> wsGraph.in
      wsGraph.out0 ~> wsMessages.in(1)
      msgFlowG.in                            <~ wsMessages.out

      // format: ON

      FlowShape(wsMessages.in(0), wsGraph.out1)
    }

    Flow.fromGraph(graph)
  }
}

object MockedGateway {

  def apply(sendMessageTo: ActorRef[ProcessorCommand]): Behavior[GatewayCommand] = Behaviors.setup { ctx =>
    Behaviors.withStash(32)(stash => mocked(ctx, stash, sendMessageTo, null, useCompression = false))
  }

  def mocked(
      ctx: ActorContext[GatewayCommand],
      stash: StashBuffer[GatewayCommand],
      sendMessagesTo: ActorRef[ProcessorCommand],
      client: ActorRef[Message],
      useCompression: Boolean
  ): Behavior[GatewayCommand] = {
    import GatewayProtocol._
    Behaviors.receiveMessage {
      case SetClient(newClient) =>
        sendMessagesTo ! HasSetClient
        stash.unstashAll(mocked(ctx, stash, sendMessagesTo, newClient, useCompression))
      case send @ MessageToClient(msg) if client != null =>
        val strData = send.encoder(msg).noSpaces
        val data    = ByteString.fromString(strData)

        val payload =
          if (useCompression) BinaryMessage(Source.single(data).via(Compression.deflate))
          else TextMessage(strData)

        client ! payload
        Behaviors.same
      case msg @ MessageToClient(_) =>
        stash.stash(msg)
        Behaviors.same
      case MessageFromClient(TextMessage.Strict(text)) =>
        sendMessagesTo ! DecodedGatewayMessage(parser.parse(text).flatMap(_.as[GatewayMessage[_]]))
        Behaviors.same
      case SetUseCompression(compression) => mocked(ctx, stash, sendMessagesTo, client, compression)
    }
  }

  sealed trait GatewayCommand
  sealed trait ProcessorCommand

  case class SetClient(ref: ActorRef[Message])   extends GatewayCommand
  case class MessageFromClient(message: Message) extends GatewayCommand
  case class MessageToClient[A](msg: GatewayMessage[A])(implicit val encoder: Encoder[GatewayMessage[A]])
      extends GatewayCommand
  case class SetUseCompression(useCompression: Boolean) extends GatewayCommand

  case object HasSetClient                                                       extends ProcessorCommand
  case class DecodedGatewayMessage(e: Either[io.circe.Error, GatewayMessage[_]]) extends ProcessorCommand
}
