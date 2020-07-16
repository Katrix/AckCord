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

import scala.concurrent.duration._

import ackcord.cachehandlers.CacheSnapshotBuilder
import ackcord.data.{GuildId, User, UserId}
import ackcord.gateway.GatewayEvent.GuildDelete
import ackcord.gateway.{Dispatch, GatewayEvent, GatewayMessage}
import ackcord.requests.SupervisionStreams
import ackcord.util.GuildStreams
import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}

object CacheStreams {

  private val logger = LoggerFactory.getLogger(this.getClass)

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

  private val expectedFailedApiMessageCreation: Set[Class[_]] = Set(
    classOf[GatewayEvent.MessageReactionAdd],
    classOf[GatewayEvent.MessageReactionRemove],
    classOf[GatewayEvent.MessageReactionRemoveAll],
    classOf[GatewayEvent.MessageReactionRemoveEmoji],
    classOf[GatewayEvent.MessageDelete],
    classOf[GatewayEvent.MessageDeleteBulk]
  )

  /**
    * A flow that creates [[APIMessage]]s from update events.
    */
  def createApiMessages: Flow[(CacheEvent, CacheState), APIMessage, NotUsed] = {
    Flow[(CacheEvent, CacheState)]
      .collect {
        case (APIMessageCacheUpdate(_, sendEvent, _, _, d), state) =>
          val event = sendEvent(state)
          if (event.isEmpty) {
            if (expectedFailedApiMessageCreation.contains(d.event.getClass)) {
              logger.debug(s"Failed to create API message for ${d.event.getClass}")
            } else {
              logger.warn(s"Failed to create API message for ${d.event.getClass}")
            }
          }

          event.toList
        case (BatchedAPIMessageCacheUpdate(updates), state) => updates.flatMap(_.sendEvent(state).toList).toList
      }
      .mapConcat(identity)
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

  case class GuildCacheEvent(event: CacheEvent, respondTo: ActorRef[(CacheEvent, CacheState)])

  def guildCacheBehavior(cacheBuilder: CacheSnapshotBuilder): Behavior[GuildCacheEvent] = {
    def guildUpdaterBehavior(guildCacheBuilder: CacheSnapshotBuilder): Behavior[GuildCacheEvent] = Behaviors.setup {
      context =>
        var state: CacheState = CacheState(guildCacheBuilder.toImmutable, guildCacheBuilder.toImmutable)

        Behaviors.receiveMessage {
          case GuildCacheEvent(event, respondTo) =>
            event.process(guildCacheBuilder)(context.log)
            guildCacheBuilder.executeProcessor()

            state = state.update(guildCacheBuilder.toImmutable)
            respondTo ! ((event, state))
            Behaviors.same
        }
    }

    Behaviors.setup[GuildCacheEvent] { context =>
      val extract = GuildStreams.createGatewayGuildInfoExtractor(context.log)

      var state: CacheState = CacheState(cacheBuilder.toImmutable, cacheBuilder.toImmutable)
      val guildHandlers: collection.mutable.Map[GuildId, ActorRef[GuildCacheEvent]] =
        collection.mutable.Map.empty[GuildId, ActorRef[GuildCacheEvent]]

      def sendToAll(event: CacheEvent, respondTo: ActorRef[(CacheEvent, CacheState)]): Unit = {
        event.process(cacheBuilder)(context.log)
        cacheBuilder.executeProcessor()

        state = state.update(cacheBuilder.toImmutable)
        respondTo ! ((event, state))
      }

      def sendToGuild(id: GuildId, event: CacheEvent, respondTo: ActorRef[(CacheEvent, CacheState)]): Unit = {
        guildHandlers.getOrElseUpdate(
          id,
          context.spawn(guildUpdaterBehavior(cacheBuilder.copy), "GuildCacheUpdater" + id.asString)
        ) ! GuildCacheEvent(event, respondTo)

        event match {
          case APIMessageCacheUpdate(_, _, _, _, Dispatch(_, event: GuildDelete)) =>
            event.data.value.foreach { guild =>
              if (!guild.unavailable.getOrElse(false)) {
                guildHandlers.remove(guild.id)
              }
            }

          case _ =>
        }
      }

      def handleApiUpdate(
          update: APIMessageCacheUpdate[_],
          respondTo: ActorRef[(CacheEvent, CacheState)]
      ): Unit = {
        extract(update.dispatch.event) match {
          case Some(guildId) =>
            sendToGuild(guildId, update, respondTo)
          case None =>
            sendToAll(update, respondTo)
        }

        Behaviors.same
      }

      Behaviors.receiveMessage[GuildCacheEvent] {
        case GuildCacheEvent(update: APIMessageCacheUpdate[_], respondTo) =>
          handleApiUpdate(update, respondTo)
          Behaviors.same
        case GuildCacheEvent(BatchedAPIMessageCacheUpdate(updates), respondTo) =>
          updates.foreach(handleApiUpdate(_, respondTo))
          Behaviors.same
        case GuildCacheEvent(event, respondTo) =>
          sendToAll(event, respondTo)
          Behaviors.same
      }
    }
  }

  def guildCacheUpdater(
      guildCacheUpdateActor: ActorRef[GuildCacheEvent]
  ): Flow[CacheEvent, (CacheEvent, CacheState), NotUsed] = {
    implicit val timeout: Timeout = Timeout(10.seconds)
    ActorFlow.ask(4)(guildCacheUpdateActor)(GuildCacheEvent)
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
