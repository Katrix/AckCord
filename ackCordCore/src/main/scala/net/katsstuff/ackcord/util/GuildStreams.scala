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
package net.katsstuff.ackcord.util

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.scaladsl.Flow
import cats.Eval
import io.circe.Decoder
import net.katsstuff.ackcord.{APIMessage, MemoryCacheSnapshot}
import net.katsstuff.ackcord.data.{ChannelId, GuildId}
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.gateway.{ComplexGatewayEvent, GatewayEvent}

object GuildStreams {

  /**
    * A flow which tries to find out which guild a given APIMessage event belongs to.
    *
    * Handles
    * - [[APIMessage.ChannelMessage]]
    * - [[APIMessage.GuildMessage]]
    * - [[APIMessage.MessageMessage]]
    * - [[APIMessage.VoiceStateUpdate]]
    */
  def withGuildInfoApiMessage[Msg <: APIMessage]: Flow[Msg, (Msg, Option[GuildId]), NotUsed] =
    Flow[Msg].map { msg =>
      val optGuildId = msg match {
        case _ @(_: APIMessage.Ready | _: APIMessage.Resumed | _: APIMessage.UserUpdate) =>
          None
        case msg: APIMessage.GuildMessage =>
          Some(msg.guild.id)
        case msg: APIMessage.ChannelMessage =>
          msg.channel.asGuildChannel.map(_.guildId)
        case msg: APIMessage.MessageMessage =>
          implicit val c: MemoryCacheSnapshot = msg.cache.current
          msg.message.tGuildChannel.value.map(_.guildId)
        case APIMessage.VoiceStateUpdate(state, _) => state.guildId
      }

      msg -> optGuildId
    }

  /**
    * A flow which tries to find out which guild a given GatewayEvent event belongs to.
    *
    * Handles
    * - [[net.katsstuff.ackcord.gateway.GatewayEvent.GuildEvent]]
    * - [[net.katsstuff.ackcord.gateway.GatewayEvent.ComplexGuildEvent]]
    * - [[net.katsstuff.ackcord.gateway.GatewayEvent.OptGuildEvent]]
    * - [[net.katsstuff.ackcord.gateway.GatewayEvent.ChannelEvent]]
    */
  def withGuildInfoGatewayEvent[Msg <: ComplexGatewayEvent[_, _]](
      log: LoggingAdapter
  ): Flow[Msg, (Msg, Option[GuildId]), NotUsed] =
    Flow[Msg].statefulMapConcat { () =>
      val channelToGuild = collection.mutable.Map.empty[ChannelId, GuildId]

      def handleLazy[A, B](later: Eval[Decoder.Result[A]])(f: A => B): Option[B] = {
        later.value match {
          case Right(value) => Some(f(value))
          case Left(e) =>
            log.error(e, "Failed to parse payload")
            None
        }
      }

      def handleLazyOpt[A, B](later: Eval[Decoder.Result[Option[A]]])(f: A => B): Option[B] = {
        later.value match {
          case Right(value) => value.map(f)
          case Left(e) =>
            log.error(e, "Failed to parse payload")
            None
        }
      }

      def lazyToOption(later: Eval[Decoder.Result[GuildId]]): Option[GuildId] = handleLazy(later)(identity)

      def lazyOptToOption(later: Eval[Decoder.Result[Option[GuildId]]]): Option[GuildId] =
        handleLazyOpt(later)(identity)

      msg =>
        {
          val optGuildId = msg match {
            case _ @(_: GatewayEvent.Ready | _: GatewayEvent.Resumed | _: GatewayEvent.UserUpdate) =>
              None
            case msg: GatewayEvent.GuildCreate =>
              handleLazy(msg.guildId) { guildId =>
                handleLazy(msg.data) { data =>
                  data.channels.foreach(channelToGuild ++= _.map(_.id -> guildId))
                }
                guildId
              }
            case msg: GatewayEvent.ChannelCreate =>
              handleLazyOpt(msg.guildId) { guildId =>
                handleLazy(msg.channelId)(channelToGuild.put(_, guildId))
                guildId
              }
            case msg: GatewayEvent.ChannelDelete =>
              handleLazy(msg.channelId)(channelToGuild.remove)
              lazyOptToOption(msg.guildId)
            case msg: GatewayEvent.GuildEvent[_] =>
              lazyToOption(msg.guildId)
            case msg: GatewayEvent.ComplexGuildEvent[_, _] =>
              lazyToOption(msg.guildId)
            case msg: GatewayEvent.OptGuildEvent[_] =>
              lazyOptToOption(msg.guildId)
            case msg: GatewayEvent.ChannelEvent[_] =>
              handleLazy(msg.channelId)(channelToGuild.get).flatten
          }

          List(msg -> optGuildId)
        }
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
  def guildFilterApiMessage[Msg <: APIMessage](guildId: GuildId): Flow[Msg, Msg, NotUsed] =
    withGuildInfoApiMessage[Msg].collect {
      case (msg @ (_: APIMessage.Ready | _: APIMessage.Resumed | _: APIMessage.UserUpdate), _) => msg
      case (msg, Some(`guildId`))                                                              => msg
    }

  /**
    * GuildFilter serves the opposite function of [[GuildRouter]]. The job of
    * the guild filter is to only send messages to one actor that matches a
    * specific guild.
    *
    * Handles
    * - [[net.katsstuff.ackcord.gateway.GatewayEvent.GuildEvent]]
    * - [[net.katsstuff.ackcord.gateway.GatewayEvent.OptGuildEvent]]
    * - [[net.katsstuff.ackcord.gateway.GatewayEvent.ChannelEvent]]
    *
    * Global events like [[net.katsstuff.ackcord.gateway.GatewayEvent.Ready]],
    * [[net.katsstuff.ackcord.gateway.GatewayEvent.Resumed]] and
    * [[net.katsstuff.ackcord.gateway.GatewayEvent.UserUpdate]] are sent no matter what.
    *
    * @param guildId The only guildID to allow through.
    */
  def guildFilterGatewayEvent[Msg <: ComplexGatewayEvent[_, _]](
      guildId: GuildId,
      log: LoggingAdapter
  ): Flow[Msg, Msg, NotUsed] =
    withGuildInfoGatewayEvent[Msg](log).collect {
      case (msg @ (_: GatewayEvent.Ready | _: GatewayEvent.Resumed | _: GatewayEvent.UserUpdate), _) => msg
      case (msg, Some(`guildId`))                                                                    => msg
    }

  /**
    * Creates a subflow grouped by what GuildId a message belongs to.
    */
  def apiMessageGroupByGuildId[Msg <: APIMessage] = {
    withGuildInfoApiMessage[Msg]
      .collect {
        case (msg, Some(guildId)) => msg -> guildId
      }
      .groupBy(Int.MaxValue, _._2)
      .map(_._1)
  }

  /**
    * Creates a subflow grouped by what GuildId an event belongs to.
    */
  def gatewayEventGroupByGuildId[Msg <: ComplexGatewayEvent[_, _]](log: LoggingAdapter) =
    withGuildInfoGatewayEvent[Msg](log)
      .collect {
        case (msg, Some(guildId)) => msg -> guildId
      }
      .groupBy(Int.MaxValue, _._2)
      .map(_._1)
}
