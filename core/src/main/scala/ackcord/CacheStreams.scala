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

import ackcord.cachehandlers.CacheSnapshotBuilder
import ackcord.data.{User, UserId}
import ackcord.gateway.GatewayMessage
import ackcord.requests.SupervisionStreams
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import org.slf4j.Logger

object CacheStreams {

  /**
    * Creates a set of publish subscribe streams that go through the cache updated.
    */
  def cacheStreams(
      cacheProcessor: MemoryCacheSnapshot.CacheProcessor,
      bufferSize: PubSubBufferSize = PubSubBufferSize()
  )(
      implicit system: ActorSystem[Nothing]
  ): (Sink[CacheEvent, NotUsed], Source[(CacheEvent, CacheState), NotUsed]) =
    cacheStreamsCustom(cacheUpdater(emptyStartingCache(cacheProcessor)), bufferSize)

  /**
    * Creates a set of publish subscribe streams that go through a custom cache
    * update procedure you decide.
    */
  def cacheStreamsCustom(
      updater: Flow[CacheEvent, (CacheEvent, CacheState), NotUsed],
      bufferSize: PubSubBufferSize = PubSubBufferSize()
  )(implicit system: ActorSystem[Nothing]): (Sink[CacheEvent, NotUsed], Source[(CacheEvent, CacheState), NotUsed]) = {
    SupervisionStreams
      .addLogAndContinueFunction(
        MergeHub
          .source[CacheEvent](bufferSize.perProducer)
          .via(updater)
          .toMat(BroadcastHub.sink(bufferSize.consumer))(Keep.both)
          .addAttributes
      )
      .run()
  }

  /**
    * Creates a set of publish subscribe streams for gateway events.
    */
  def gatewayEvents[D](bufferSize: PubSubBufferSize = PubSubBufferSize())(
      implicit system: ActorSystem[Nothing]
  ): (Sink[GatewayMessage[D], NotUsed], Source[GatewayMessage[D], NotUsed]) =
    SupervisionStreams
      .addLogAndContinueFunction(
        MergeHub
          .source[GatewayMessage[D]](bufferSize.perProducer)
          .toMat(BroadcastHub.sink(bufferSize.consumer))(Keep.both)
          .addAttributes
      )
      .run()

  /**
    * A flow that creates [[APIMessage]]s from update events.
    */
  def createApiMessages: Flow[(CacheEvent, CacheState), APIMessage, NotUsed] = {
    Flow[(CacheEvent, CacheState)]
      .collect {
        case (APIMessageCacheUpdate(_, sendEvent, _, _, _), state) => sendEvent(state)
      }
      .mapConcat(_.toList)
  }

  /**
    * Creates a new empty cache snapshot builder. This is not thread safe, and
    * should not be updated from multiple threads at the same time.
    */
  def emptyStartingCache(cacheProcessor: MemoryCacheSnapshot.CacheProcessor): CacheSnapshotBuilder = {
    val dummyUser = User(
      UserId("0"),
      "Placeholder",
      "0000",
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None
    )

    new CacheSnapshotBuilder(
      0,
      shapeless.tag[CacheSnapshot.BotUser](dummyUser), //The ready event will populate this,
      SnowflakeMap.empty,
      SnowflakeMap.empty,
      SnowflakeMap.empty,
      SnowflakeMap.empty,
      SnowflakeMap.empty,
      SnowflakeMap.empty,
      SnowflakeMap.empty,
      SnowflakeMap.empty,
      cacheProcessor
    )
  }

  /**
    * A flow that keeps track of the current cache state, and updates it
    * from cache update events.
    */
  def cacheUpdater(
      cacheBuilder: CacheSnapshotBuilder
  )(implicit system: ActorSystem[Nothing]): Flow[CacheEvent, (CacheEvent, CacheState), NotUsed] =
    Flow[CacheEvent].statefulMapConcat { () =>
      var state: CacheState = CacheState(cacheBuilder.toImmutable, cacheBuilder.toImmutable)

      implicit val log: Logger = system.log

      { handlerEvent: CacheEvent =>
        handlerEvent.process(cacheBuilder)
        cacheBuilder.executeProcessor()

        state = state.update(cacheBuilder.toImmutable)
        List(handlerEvent -> state)
      }
    }
}
