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

import ackcord.MockedGateway.{HasSetClient, SendMessage, SetClient, SetUseCompression}
import ackcord.gateway.{GatewayHandler, _}
import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Stash}
import akka.http.scaladsl.coding.Deflate
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Merge, Sink, Source}
import akka.stream.{FlowShape, Materializer, OverflowStrategy}
import akka.util.ByteString
import io.circe.{Encoder, parser}

class MockedGatewayHandler(settings: GatewaySettings, gateway: ActorRef)(implicit mat: Materializer)
    extends GatewayHandler(
      Uri./,
      settings,
      Source.maybe[GatewayMessage[_]].mapMaterializedValue(_ => NotUsed),
      Sink.ignore.mapMaterializedValue(_ => NotUsed)
    ) {

  implicit val system: ActorSystem = context.system

  override def wsFlow
      : Flow[GatewayMessage[_], Dispatch[_], (Future[WebSocketUpgradeResponse], Future[Option[ResumeData]])] = {
    val response = ValidUpgrade(HttpResponse(), None)

    val sendToServer = Sink.foreach[Message](gateway ! _)
    val sendToClient = Source.actorRef[Message](128, OverflowStrategy.fail).mapMaterializedValue { actor =>
      gateway ! SetClient(actor)
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

    val wsGraphStage = new GatewayHandlerGraphStage(settings, resume)

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
object MockedGatewayHandler {
  def props(settings: GatewaySettings, gateway: ActorRef)(implicit mat: Materializer): Props =
    Props(new MockedGatewayHandler(settings, gateway))
}

class MockedGateway(sendMessagesTo: ActorRef) extends Actor with Stash {
  import GatewayProtocol._

  var client: ActorRef = _
  var useCompression   = false

  override def receive: Receive = {
    case SetClient(newClient) =>
      client = newClient
      unstashAll()
      sendMessagesTo ! HasSetClient
    case send @ SendMessage(msg) if client != null =>
      val payload =
        if (useCompression) BinaryMessage(Deflate.encode(ByteString.fromString(send.encoder(msg).noSpaces)))
        else TextMessage(send.encoder(msg).noSpaces)

      client ! payload
    case SendMessage(_) => stash()
    case TextMessage.Strict(text) =>
      parser.parse(text).flatMap(_.as[GatewayMessage[_]]) match {
        case Right(msg) => sendMessagesTo ! msg
        case Left(e)    => sendMessagesTo ! e
      }
    case SetUseCompression(compression) => useCompression = compression
  }
}
object MockedGateway {
  def props(sendMessagesTo: ActorRef): Props = Props(new MockedGateway(sendMessagesTo))
  case class SetClient(ref: ActorRef)
  case object HasSetClient
  case class SendMessage[A](msg: GatewayMessage[A])(implicit val encoder: Encoder[GatewayMessage[A]])
  case class SetUseCompression(useCompression: Boolean)
}
