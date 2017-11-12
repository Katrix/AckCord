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
package net.katsstuff.ackcord

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{Materializer, OverflowStrategy, SourceShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source, Zip}
import net.katsstuff.ackcord.data.{ChannelId, GuildId}
import net.katsstuff.ackcord.http.requests.{BaseRESTRequest, BotAuthentication, RequestAnswer, RequestResponse, RequestStreams, RequestWrapper}
import net.katsstuff.ackcord.http.websocket.gateway.{ComplexGatewayEvent, GatewayEvent}
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.util.{GuildRouter, RepeatLast}

object AckCordStreams {

  /**
    * A simple reasonable request flow for short lived streams.
    * @param token The bot token.
    */
  def requestFlow[Data, Ctx](token: String)(
      implicit system: ActorSystem,
      mat: Materializer
  ): Flow[RequestWrapper[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed] = {
    RequestStreams.requestFlowWithRatelimit[Data, Ctx](
      bufferSize = 32,
      overflowStrategy = OverflowStrategy.backpressure,
      maxAllowedWait = 2.minutes,
      credentials = BotAuthentication(token)
    )
  }

  /**
    * Sends a single request.
    * @param token The bot token.
    * @param wrapper The request to send.
    */
  def singleRequest[Data, Ctx](
      token: String,
      wrapper: RequestWrapper[Data, Ctx]
  )(implicit system: ActorSystem, mat: Materializer): Source[RequestAnswer[Data, Ctx], NotUsed] =
    Source.single(wrapper).via(requestFlow(token))

  /**
    * Sends a single request and gets the response as a future.
    * @param token The bot token.
    * @param wrapper The request to send.
    */
  def singleRequestFuture[Data, Ctx](
      token: String,
      wrapper: RequestWrapper[Data, Ctx]
  )(implicit system: ActorSystem, mat: Materializer): Future[RequestAnswer[Data, Ctx]] =
    singleRequest(token, wrapper).toMat(Sink.head)(Keep.right).run()

  /**
    * Create a request whose answer will make a trip to the cache to get a nicer response value.
    * @param token The bot token.
    * @param restRequest The base REST request.
    * @param ctx The context to send with the request.
    */
  def requestToCache[Data, Ctx, Response](
      token: String,
      restRequest: BaseRESTRequest[Data, _, Response],
      ctx: Ctx
  )(implicit system: ActorSystem, mat: Materializer, cache: Cache): Source[Response, NotUsed] = {

    val graph = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val request     = builder.add(singleRequest(token, RequestWrapper(restRequest, ctx, ActorRef.noSender)))
      val broadcaster = builder.add(Broadcast[RequestAnswer[Data, Ctx]](2))

      val cacheEvent = builder.add(Flow[RequestAnswer[Data, Ctx]].collect {
        case RequestResponse(
        data,
        _,
        _,
        _,
        RequestWrapper(restRequest: BaseRESTRequest[Data @unchecked, _, Response @unchecked], _, _)
        ) =>
          MiscHandlerEvent(restRequest.convertToCacheHandlerType(data), restRequest.cacheHandler)
            .asInstanceOf[CacheHandlerEvent[Any]] //FIXME
      })

      val repeater = builder.add(RepeatLast.flow[RequestAnswer[Data, Ctx]])

      val zipper = builder.add(Zip[RequestAnswer[Data, Ctx], (CacheHandlerEvent[Any], CacheState)])

      val findPublished = builder.add(Flow[(RequestAnswer[Data, Ctx], (CacheHandlerEvent[Any], CacheState))].collect {
        case (RequestResponse(data, _, _, _, _), (MiscHandlerEvent(data2, _), state)) if data == data2 =>
          restRequest.findData(data)(state)
      })

      // format: OFF

      request ~> broadcaster ~> cacheEvent ~>               cache.publish
      broadcaster ~> repeater    ~> zipper.in0
      zipper.in1 <~ cache.subscribe
      zipper.out ~> findPublished

      // format: ON

      SourceShape(findPublished.out)
    }

    Source.fromGraph(graph).flatMapConcat(_.fold[Source[Response, NotUsed]](Source.empty)(Source.single))
  }

  /**
    * Serves the opposite function of [[GuildRouter]]. The job of
    * the guild filter is to only allow messages that belong to a
    * specific guild.
    *
    * Handles
    * - [[APIMessage.ChannelMessage]]
    * - [[APIMessage.GuildMessage]]
    * - [[APIMessage.MessageMessage]]
    * - [[APIMessage.VoiceStateUpdate]]
    *
    * Global events like [[APIMessage.Ready]], [[APIMessage.Resumed]] and
    * [[APIMessage.UserUpdate]] are sent no matter what.
    *
    * @param guildId The only guildID to allow through.
    */
  def guildFilterApiMessage[Msg <: APIMessage](guildId: GuildId): Flow[Msg, Msg, NotUsed] = {
    val channelToGuild = collection.mutable.Map.empty[ChannelId, GuildId]

    Flow[Msg].statefulMapConcat { () => msg =>
      val isGuildEvent = msg match {
        case _@(_: APIMessage.Ready | _: APIMessage.Resumed | _: APIMessage.UserUpdate) =>
          true
        case msg: APIMessage.GuildMessage =>
          msg.guild.id == guildId
        case msg: APIMessage.ChannelMessage =>
          msg.channel.asGuildChannel.map(_.guildId).contains(guildId)
        case msg: APIMessage.MessageMessage =>
          msg.message.channel(msg.cache.current).flatMap(_.asGuildChannel).map(_.guildId).contains(guildId)
        case _@APIMessage.VoiceStateUpdate(state, _) => state.guildId.contains(guildId)
        case msg: GatewayEvent.GuildCreate =>
          msg.data.channels.foreach(channelToGuild ++= _.map(_.id -> msg.guildId))
          msg.guildId == guildId
        case msg: GatewayEvent.ChannelCreate =>
          msg.guildId.foreach { guildId =>
            channelToGuild.put(msg.data.id, guildId)
          }
          msg.guildId.contains(guildId)
        case msg: GatewayEvent.ChannelDelete =>
          channelToGuild.remove(msg.data.id)
          msg.guildId.contains(guildId)
        case msg: GatewayEvent.GuildEvent[_] => msg.guildId == guildId
        case msg: GatewayEvent.OptGuildEvent[_] => msg.guildId.contains(guildId)
        case msg: GatewayEvent.ChannelEvent[_] =>
          channelToGuild.get(msg.channelId).contains(guildId)
      }

      if(isGuildEvent) List(msg) else Nil
    }
  }

  /**
    * GuildFilter serves the opposite function of [[GuildRouter]]. The job of
    * the guild filter is to only send messages to one actor that matches a
    * specific guild.
    *
    * Handles
    * - [[GatewayEvent.GuildEvent]]
    * - [[GatewayEvent.OptGuildEvent]]
    * - [[GatewayEvent.ChannelEvent]]
    *
    * This actor has a small cache for figuring out what actor to send messages
    * to for the gateway channel events.
    *
    * Global events like [[GatewayEvent.Ready]], [[GatewayEvent.Resumed]] and
    * [[GatewayEvent.UserUpdate]] are sent no matter what.
    *
    * @param guildId The only guildID to allow through.
    */
  def guildFilterGatewayEvent[Msg <: ComplexGatewayEvent[_, _]](guildId: GuildId): Flow[Msg, Msg, NotUsed] = {
    val channelToGuild = collection.mutable.Map.empty[ChannelId, GuildId]

    Flow[Msg].statefulMapConcat { () => msg =>
      val isGuildEvent = msg match {
        case _@(_: GatewayEvent.Ready | _: GatewayEvent.Resumed | _: GatewayEvent.UserUpdate) =>
          true
        case msg: GatewayEvent.GuildCreate =>
          msg.data.channels.foreach(channelToGuild ++= _.map(_.id -> msg.guildId))
          msg.guildId == guildId
        case msg: GatewayEvent.ChannelCreate =>
          msg.guildId.foreach { guildId =>
            channelToGuild.put(msg.data.id, guildId)
          }

          msg.guildId.contains(guildId)
        case msg: GatewayEvent.ChannelDelete =>
          channelToGuild.remove(msg.data.id)
          msg.guildId.contains(guildId)
        case msg: GatewayEvent.GuildEvent[_] =>
          msg.guildId == guildId
        case msg: GatewayEvent.OptGuildEvent[_] =>
          msg.guildId.contains(guildId)
        case msg: GatewayEvent.ChannelEvent[_] =>
          channelToGuild.get(msg.channelId).contains(guildId)
      }

      if(isGuildEvent) List(msg) else Nil
    }
  }


}
