/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord.benchmark

import scala.concurrent.Future

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.WebSocketUpgradeResponse
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Merge, Sink, Source}
import akka.stream.{FlowShape, Materializer, OverflowStrategy}
import net.katsstuff.ackcord.data.{UnavailableGuild, User}
import net.katsstuff.ackcord.http.websocket.gateway._
import net.katsstuff.ackcord.{APIMessageCacheUpdate, Cache, ClientSettings}

class MockedGatewayHandler(
    settings: ClientSettings,
    readyUser: User,
    readyGuilds: Seq[UnavailableGuild],
    gateway: Source[Int => Dispatch[_], Future[WebSocketUpgradeResponse]],
    source: Source[GatewayMessage[_], NotUsed],
    sink: Sink[Dispatch[_], NotUsed]
)(implicit mat: Materializer)
    extends GatewayHandler(Uri./, settings, source, sink) {

  implicit val system: ActorSystem = context.system

  override def wsFlow: Flow[GatewayMessage[_], Dispatch[_], (Future[WebSocketUpgradeResponse], Future[Option[ResumeData]])] = {
    val wsHandlerGraphStage = new GatewayHandlerGraphStage(settings, resume)

    val graph = GraphDSL.create(gateway, wsHandlerGraphStage)(Keep.both) {
      implicit builder => (gatewayGraph, gatewayHandlerGraph) =>
        import GraphDSL.Implicits._

        val wsGraph = builder.add(new GatewayGraphStage(settings, readyUser, readyGuilds))
        val parseMsg = builder.add(GatewayHandlerGraphStage.parseMessage.collect {
          case Right(value) => value
          case Left(e)      => throw e
        })
        val createMsg = builder.add(GatewayHandlerGraphStage.createMessage)

        val wsMessages = builder.add(Merge[GatewayMessage[_]](2))
        val buffer     = builder.add(Flow[GatewayMessage[_]].buffer(32, OverflowStrategy.dropHead))

        // format: OFF
        
        gatewayGraph ~> wsGraph.in1
                        wsGraph.out ~> parseMsg ~>  buffer ~>           gatewayHandlerGraph.in
                                                    wsMessages.in(1) <~ gatewayHandlerGraph.out0
                        wsGraph.in0 <~ createMsg <~ wsMessages.out

        // format: ON

        FlowShape(wsMessages.in(0), gatewayHandlerGraph.out1)
    }

    Flow.fromGraph(graph)
  }
}
object MockedGatewayHandler {
  def props(
      settings: ClientSettings,
      readyUser: User,
      readyGuilds: Seq[UnavailableGuild],
      gateway: Source[Int => Dispatch[_], Future[WebSocketUpgradeResponse]],
      source: Source[GatewayMessage[_], NotUsed],
      sink: Sink[Dispatch[_], NotUsed]
  )(implicit mat: Materializer): Props = Props(new MockedGatewayHandler(settings, readyUser, readyGuilds, gateway, source, sink))

  def cacheProps(
      settings: ClientSettings,
      readyUser: User,
      readyGuilds: Seq[UnavailableGuild],
      gateway: Source[Int => Dispatch[_], Future[WebSocketUpgradeResponse]],
      cache: Cache
  )(implicit mat: Materializer): Props = {
    val sink = cache.publish.contramap { (dispatch: Dispatch[_]) =>
      val event = dispatch.event.asInstanceOf[ComplexGatewayEvent[Any, Any]] //Makes stuff compile
      APIMessageCacheUpdate(event.handlerData, event.createEvent, event.cacheHandler)
    }

    Props(new MockedGatewayHandler(settings, readyUser, readyGuilds, gateway, cache.gatewaySubscribe, sink))
  }
}
