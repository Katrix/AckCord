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
package net.katsstuff.ackcord

import scala.collection.immutable

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Status}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import net.katsstuff.ackcord.http.websocket.gateway.GatewayMessage

/**
  * Represents a cache that can be published and subscribed to.
  * @param publish A sink used for publishing. Any elements connected to this
  *                sink is published to the cache.
  * @param subscribe A source to subscribe to. All updates are pushed here.
  */
case class Cache(
    publish: Sink[CacheUpdate[Any], NotUsed],
    subscribe: Source[(CacheUpdate[Any], CacheState), NotUsed],
    gatewayPublish: Sink[GatewayMessage[Any], NotUsed],
    gatewaySubscribe: Source[GatewayMessage[Any], NotUsed],
)(implicit val mat: Materializer) {

  /**
    * Publish a single element to this cache.
    */
  def publishSingle(elem: CacheUpdate[Any]): Unit = publish.runWith(Source.single(elem))

  /**
    * Publish many elements to this cache.
    */
  def publishMany(it: immutable.Iterable[CacheUpdate[Any]]): Unit = publish.runWith(Source(it))

  /**
    * A source used to subscribe to [[APIMessage]]s sent to this cache.
    */
  def subscribeAPI: Source[APIMessage, NotUsed] = subscribe.via(CacheStreams.createApiMessages[Any])

  /**
    * Subscribe an actor to this cache using [[https://doc.akka.io/api/akka/current/akka/stream/scaladsl/Sink$.html#actorRef[T](ref:akka.actor.ActorRef,onCompleteMessage:Any):akka.stream.scaladsl.Sink[T,akka.NotUsed] Sink.actorRef]].
    */
  def subscribeAPIActor(actor: ActorRef, completeMessage: Any, specificEvent: Class[_ <: APIMessage]*): Unit =
    subscribeAPI.filter(msg => specificEvent.exists(_.isInstance(msg))).runWith(Sink.actorRef(actor, completeMessage))

  /**
    * Subscribe an actor to this cache using [[https://doc.akka.io/api/akka/current/akka/stream/scaladsl/Sink$.html#actorRefWithAck[T](ref:akka.actor.ActorRef,onInitMessage:Any,ackMessage:Any,onCompleteMessage:Any,onFailureMessage:Throwable=%3EAny):akka.stream.scaladsl.Sink[T,akka.NotUsed] Sink.actorRefWithAck]].
    */
  def subscribeAPIActorWithAck(
      actor: ActorRef,
      initMessage: Any,
      ackMessage: Any,
      completeMessage: Any,
      specificEvent: Class[_ <: APIMessage],
      failureMessage: Throwable => Any = Status.Failure
  ): Unit =
    subscribeAPI
      .filter(specificEvent.isInstance(_))
      .runWith(Sink.actorRefWithAck(actor, initMessage, ackMessage, completeMessage, failureMessage))
}
object Cache {
  def create(implicit system: ActorSystem, mat: Materializer): Cache = {
    val (publish, subscribe)               = CacheStreams.cacheStreams[Any]
    val (gatewayPublish, gatewaySubscribe) = CacheStreams.gatewayEvents[Any]
    Cache(publish, subscribe, gatewayPublish, gatewaySubscribe)
  }
}
