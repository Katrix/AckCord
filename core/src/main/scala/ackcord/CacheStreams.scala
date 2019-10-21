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

import scala.collection.mutable

import ackcord.cachehandlers.CacheSnapshotBuilder
import ackcord.gateway.GatewayEvent.ReadyData
import ackcord.gateway.GatewayMessage
import ackcord.requests.SupervisionStreams
import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}

object CacheStreams {

  /**
    * Creates a set of publish subscribe streams that go through the cache updated.
    */
  def cacheStreams(
      implicit system: ActorSystem
  ): (Sink[CacheEvent, NotUsed], Source[(CacheEvent, CacheState), NotUsed]) = {
    SupervisionStreams
      .addLogAndContinueFunction(
        MergeHub
          .source[CacheEvent](perProducerBufferSize = 16)
          .via(cacheUpdater)
          .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
          .addAttributes
      )
      .run()
  }

  /**
    * Creates a set of publish subscribe streams for gateway events.
    */
  def gatewayEvents[D](
      implicit system: ActorSystem
  ): (Sink[GatewayMessage[D], NotUsed], Source[GatewayMessage[D], NotUsed]) =
    SupervisionStreams
      .addLogAndContinueFunction(
        MergeHub
          .source[GatewayMessage[D]](perProducerBufferSize = 16)
          .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
          .addAttributes
      )
      .run()

  /**
    * A flow that creates [[APIMessage]]s from update events.
    */
  def createApiMessages: Flow[(CacheEvent, CacheState), APIMessage, NotUsed] = {
    Flow[(CacheEvent, CacheState)]
      .collect {
        case (APIMessageCacheUpdate(_, sendEvent, _, _), state) => sendEvent(state)
      }
      .mapConcat(_.toList)
  }

  /**
    * A flow that keeps track of the current cache state, and updates it
    * from cache update events.
    */
  def cacheUpdater(implicit system: ActorSystem): Flow[CacheEvent, (CacheEvent, CacheState), NotUsed] =
    Flow[CacheEvent].statefulMapConcat { () =>
      var state: CacheState            = null
      implicit val log: LoggingAdapter = system.log

      //We only handle events when we are ready to, and we have received the ready event.
      def isReady: Boolean = state != null

      {
        case readyEvent @ APIMessageCacheUpdate(_: ReadyData, _, _, _) =>
          val builder = new CacheSnapshotBuilder(
            null, //The event will populate this,
            mutable.Map.empty,
            mutable.Map.empty,
            mutable.Map.empty,
            mutable.Map.empty,
            mutable.Map.empty,
            mutable.Map.empty,
            mutable.Map.empty,
            mutable.Map.empty
          )

          readyEvent.process(builder)

          val snapshot = builder.toImmutable
          state = CacheState(snapshot, snapshot)
          List(readyEvent -> state)
        case handlerEvent: CacheEvent if isReady =>
          val builder = CacheSnapshotBuilder(state.current)
          handlerEvent.process(builder)

          state = state.update(builder.toImmutable)
          List(handlerEvent -> state)
        case _ if !isReady =>
          log.error("Received event before ready")
          Nil
      }
    }
}
