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

import scala.collection.immutable
import scala.concurrent.Future

import ackcord.cachehandlers.{CacheHandler, CacheTypeRegistry}
import ackcord.data.RawInteraction
import ackcord.gateway.{Dispatch, GatewayEvent, GatewayMessage}
import ackcord.requests.SupervisionStreams
import ackcord.util.AckCordGatewaySettings
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{NotUsed, actor => classic}
import org.slf4j.{Logger, LoggerFactory}

/**
  * Houses streams to interact with events and messages sent to and from Discord.
  * @param publish A sink used for publishing. Any elements connected to this
  *                sink is published to the cache.
  * @param subscribe A source to subscribe to. All updates are pushed here.
  * @param parallelism How many cache updates to construct at the same time.
  */
case class Events(
    publish: Sink[CacheEvent, NotUsed],
    subscribe: Source[(CacheEvent, CacheState), NotUsed],
    sendGatewayPublish: Sink[GatewayMessage[Any], NotUsed],
    sendGatewaySubscribe: Source[GatewayMessage[Any], NotUsed],
    receiveGatewayPublish: Sink[GatewayMessage[Any], NotUsed],
    receiveGatewaySubscribe: Source[GatewayMessage[Any], NotUsed],
    parallelism: Int
)(implicit system: ActorSystem[Nothing]) {

  /**
    * Messages sent to this flow will be sent to the gateway.
    * Messages coming out of this flow are received from the gateway.
    */
  def gatewayClientConnection: Flow[GatewayMessage[_], GatewayMessage[_], NotUsed] =
    Flow.fromSinkAndSourceCoupled(receiveGatewayPublish, sendGatewaySubscribe)

  @deprecated("Prefer publishToCache", since = "0.17")
  def publish(elem: CacheEvent): Unit = publishCacheEvent(elem)

  /**
    * Publish a single cache event.
    */
  def publishCacheEvent(elem: CacheEvent): Unit = publish.runWith(Source.single(elem))

  @deprecated("Prefer publishCacheEvents", since = "0.17")
  def publishMany(it: immutable.Iterable[CacheEvent]): Unit = publish.runWith(Source(it))

  /**
    * Publish many cache events.
    */
  @deprecated("Prefer publishToCache", since = "0.17")
  def publishCacheEvents(it: immutable.Iterable[CacheEvent]): Unit = publish.runWith(Source(it))

  /**
    * A source used to subscribe to [[APIMessage]]s sent to this cache.
    */
  def subscribeAPI: Source[APIMessage, NotUsed] = subscribe.via(CacheStreams.createApiMessages)

  /**
    * Subscribe an actor to this cache using [[https://doc.akka.io/api/akka/current/akka/stream/scaladsl/Sink$.html#actorRef[T](ref:akka.actor.ActorRef,onCompleteMessage:Any):akka.stream.scaladsl.Sink[T,akka.NotUsed] Sink.actorRef]].
    */
  def subscribeAPIActor(actor: classic.ActorRef, completeMessage: Any, onFailureMessage: Throwable => Any)(
      specificEvent: Class[_ <: APIMessage]*
  ): Unit =
    subscribeAPI
      .filter(msg => specificEvent.exists(_.isInstance(msg)))
      .runWith(Sink.actorRef(actor, completeMessage, onFailureMessage))

  /**
    * Subscribe an actor to this cache using [[https://doc.akka.io/api/akka/current/akka/stream/scaladsl/Sink$.html#actorRefWithAck[T](ref:akka.actor.ActorRef,onInitMessage:Any,ackMessage:Any,onCompleteMessage:Any,onFailureMessage:Throwable=%3EAny):akka.stream.scaladsl.Sink[T,akka.NotUsed] Sink.actorRefWithAck]].
    */
  def subscribeAPIActorWithAck(
      actor: classic.ActorRef,
      initMessage: Any,
      ackMessage: Any,
      completeMessage: Any,
      failureMessage: Throwable => Any = classic.Status.Failure
  )(specificEvent: Class[_ <: APIMessage]*): Unit =
    subscribeAPI
      .filter(msg => specificEvent.exists(_.isInstance(msg)))
      .runWith(Sink.actorRefWithBackpressure(actor, initMessage, ackMessage, completeMessage, failureMessage))

  /**
    * Exposes the command interactions sent to this bot.
    */
  def interactions: Source[(RawInteraction, Option[CacheSnapshot]), NotUsed] =
    subscribeAPI
      .collectType[APIMessage.InteractionCreate]
      .map(e => e.rawInteraction -> Some(e.cache.current))
}
object Events {

  def createWithPubSub(
      publish: Sink[CacheEvent, NotUsed],
      subscribe: Source[(CacheEvent, CacheState), NotUsed],
      parallelism: Int = 4,
      ignoredEvents: Seq[Class[_ <: GatewayEvent[_]]],
      cacheTypeRegistry: Logger => CacheTypeRegistry,
      maxBatch: Long = 1,
      batchCostFun: APIMessageCacheUpdate[_] => Long = _ => 1,
      gatewayToApiMessageConverter: Option[
        (CacheTypeRegistry, Logger) => Dispatch[_] => Option[APIMessageCacheUpdate[_]]
      ] = Some((registry, logger) => CacheEventCreator.ackcordGatewayToCacheUpdateOnly(registry, logger)),
      sendGatewayEventsBufferSize: PubSubBufferSize = PubSubBufferSize(),
      receiveGatewayEventsBufferSize: PubSubBufferSize = PubSubBufferSize()
  )(implicit system: ActorSystem[Nothing]): Events = {
    val (sendGatewayPublish, sendGatewaySubscribe) = CacheStreams.gatewayEvents[Any](sendGatewayEventsBufferSize)
    val (receiveGatewayPublish, receiveGatewaySubscribe) =
      CacheStreams.gatewayEvents[Any](receiveGatewayEventsBufferSize)

    //Keep it drained if nothing else is using it
    subscribe.runWith(Sink.ignore)

    import system.executionContext

    //Pipe events gotten into the main pubsub
    gatewayToApiMessageConverter.foreach { apiMessageCreatorFun =>
      val cacheHandlerLog = LoggerFactory.getLogger(classOf[CacheHandler[_]])
      val settings        = AckCordGatewaySettings()(system)
      val apiMessageConverter =
        apiMessageCreatorFun(cacheTypeRegistry(cacheHandlerLog), cacheHandlerLog)

      val baseSource: Source[APIMessageCacheUpdate[Any], NotUsed] =
        receiveGatewaySubscribe
          .collectType[Dispatch[_]]
          .filter(dispatch => !ignoredEvents.exists(_.isInstance(dispatch.event)))
          .mapAsync(parallelism)(dispatch =>
            Future(
              CacheEventCreator.eventToCacheUpdate(dispatch, apiMessageConverter, cacheHandlerLog, settings).toList
            )
          )
          .mapConcat(identity)
          .map(update => update.asInstanceOf[APIMessageCacheUpdate[Any]])

      val sourceWithBatching =
        if (maxBatch != 1)
          baseSource
            .batchWeighted(maxBatch, batchCostFun, update => update :: Nil)((xs, update) => update :: xs)
            .map(xs => BatchedAPIMessageCacheUpdate(xs.reverse))
        else
          baseSource

      SupervisionStreams
        .addLogAndContinueFunction(
          sourceWithBatching.to(publish).addAttributes
        )
        .run()
    }

    Events(
      publish,
      subscribe,
      sendGatewayPublish,
      sendGatewaySubscribe,
      receiveGatewayPublish,
      receiveGatewaySubscribe,
      parallelism
    )
  }

  /**
    * Creates a cache for a bot.
    * @param cacheProcessor A function to run on each cache update.
    * @param parallelism How many cache updates to construct at the same time.
    */
  def create(
      cacheProcessor: MemoryCacheSnapshot.CacheProcessor = MemoryCacheSnapshot.defaultCacheProcessor,
      parallelism: Int = 4,
      ignoredEvents: Seq[Class[_ <: GatewayEvent[_]]] = Nil,
      cacheTypeRegistry: Logger => CacheTypeRegistry = CacheTypeRegistry.default,
      maxBatch: Long = 1,
      batchCostFun: APIMessageCacheUpdate[_] => Long = _ => 1,
      gatewayToApiMessageConverter: Option[
        (CacheTypeRegistry, Logger) => Dispatch[_] => Option[APIMessageCacheUpdate[_]]
      ] = Some((registry, logger) => CacheEventCreator.ackcordGatewayToCacheUpdateOnly(registry, logger)),
      cacheBufferSize: PubSubBufferSize = PubSubBufferSize(),
      sendGatewayEventsBufferSize: PubSubBufferSize = PubSubBufferSize(),
      receiveGatewayEventsBufferSize: PubSubBufferSize = PubSubBufferSize()
  )(implicit system: ActorSystem[Nothing]): Events = {
    val (publish, subscribe) = CacheStreams.cacheStreams(cacheProcessor, cacheBufferSize)

    createWithPubSub(
      publish,
      subscribe,
      parallelism,
      ignoredEvents,
      cacheTypeRegistry,
      maxBatch,
      batchCostFun,
      gatewayToApiMessageConverter,
      sendGatewayEventsBufferSize,
      receiveGatewayEventsBufferSize
    )
  }

  /**
    * Creates a guild partitioned cache for a bot. Each guild will in effect receive
    * it's own cache. Cache events not specific to one guild will be sent to
    * all caches.
    *
    * Unlike then default cache, this one is faster, as cache updates can
    * be done in parallel, but might use more memory, and you need to handle
    * cross guild cache actions yourself.
    *
    * @param parallelism How many cache updates to construct at the same time.
    */
  def createGuildCache(
      guildCacheActor: ActorRef[CacheStreams.GuildCacheEvent],
      parallelism: Int = 4,
      ignoredEvents: Seq[Class[_ <: GatewayEvent[_]]],
      cacheTypeRegistry: Logger => CacheTypeRegistry,
      maxBatch: Long = 1,
      batchCostFun: APIMessageCacheUpdate[_] => Long = _ => 1,
      gatewayToApiMessageConverter: Option[
        (CacheTypeRegistry, Logger) => Dispatch[_] => Option[APIMessageCacheUpdate[_]]
      ] = Some((registry, logger) => CacheEventCreator.ackcordGatewayToCacheUpdateOnly(registry, logger)),
      cacheBufferSize: PubSubBufferSize = PubSubBufferSize(),
      sendGatewayEventsBufferSize: PubSubBufferSize = PubSubBufferSize(),
      receiveGatewayEventsBufferSize: PubSubBufferSize = PubSubBufferSize()
  )(implicit system: ActorSystem[Nothing]): Events = {
    val (publish, subscribe) =
      CacheStreams.cacheStreamsCustom(CacheStreams.guildCacheUpdater(guildCacheActor), cacheBufferSize)
    createWithPubSub(
      publish,
      subscribe,
      parallelism,
      ignoredEvents,
      cacheTypeRegistry,
      maxBatch,
      batchCostFun,
      gatewayToApiMessageConverter,
      sendGatewayEventsBufferSize,
      receiveGatewayEventsBufferSize
    )
  }
}
