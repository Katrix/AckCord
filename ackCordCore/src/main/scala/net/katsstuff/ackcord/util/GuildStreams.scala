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
import akka.stream.scaladsl.Flow
import net.katsstuff.ackcord.APIMessage
import net.katsstuff.ackcord.data.{ChannelId, GuildId}
import net.katsstuff.ackcord.network.websocket.gateway.{ComplexGatewayEvent, GatewayEvent}
import net.katsstuff.ackcord.syntax._

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
    Flow[Msg].statefulMapConcat { () =>
      val channelToGuild = collection.mutable.Map.empty[ChannelId, GuildId]

      msg =>
        {
          val optGuildId = msg match {
            case _ @(_: APIMessage.Ready | _: APIMessage.Resumed | _: APIMessage.UserUpdate) =>
              None
            case msg: APIMessage.GuildMessage =>
              Some(msg.guild.id)
            case msg: APIMessage.ChannelMessage =>
              msg.channel.asGuildChannel.map(_.guildId)
            case msg: APIMessage.MessageMessage =>
              msg.message.tGuildChannel(msg.cache.current).map(_.guildId)
            case APIMessage.VoiceStateUpdate(state, _) => state.guildId
            case msg: GatewayEvent.GuildCreate =>
              msg.data.channels.foreach(channelToGuild ++= _.map(_.id -> msg.guildId))
              Some(msg.guildId)
            case msg: GatewayEvent.ChannelCreate =>
              msg.guildId.foreach { guildId =>
                channelToGuild.put(msg.data.id, guildId)
              }
              msg.guildId
            case msg: GatewayEvent.ChannelDelete =>
              channelToGuild.remove(msg.data.id)
              msg.guildId
            case msg: GatewayEvent.GuildEvent[_]    => Some(msg.guildId)
            case msg: GatewayEvent.OptGuildEvent[_] => msg.guildId
            case msg: GatewayEvent.ChannelEvent[_]  => channelToGuild.get(msg.channelId)
          }

          List(msg -> optGuildId)
        }
    }

  /**
    * A flow which tries to find out which guild a given GatewayEvent event belongs to.
    *
    * Handles
    * - [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.GuildEvent]]
    * - [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.ComplexGuildEvent]]
    * - [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.OptGuildEvent]]
    * - [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.ChannelEvent]]
    */
  def withGuildInfoGatewayEvent[Msg <: ComplexGatewayEvent[_, _]]: Flow[Msg, (Msg, Option[GuildId]), NotUsed] =
    Flow[Msg].statefulMapConcat { () =>
      val channelToGuild = collection.mutable.Map.empty[ChannelId, GuildId]

      msg =>
        {
          val optGuildId = msg match {
            case _ @(_: GatewayEvent.Ready | _: GatewayEvent.Resumed | _: GatewayEvent.UserUpdate) =>
              None
            case msg: GatewayEvent.GuildCreate =>
              msg.data.channels.foreach(channelToGuild ++= _.map(_.id -> msg.guildId))
              Some(msg.guildId)
            case msg: GatewayEvent.ChannelCreate =>
              msg.guildId.foreach { guildId =>
                channelToGuild.put(msg.data.id, guildId)
              }

              msg.guildId
            case msg: GatewayEvent.ChannelDelete =>
              channelToGuild.remove(msg.data.id)
              msg.guildId
            case msg: GatewayEvent.GuildEvent[_] =>
              Some(msg.guildId)
            case msg: GatewayEvent.ComplexGuildEvent[_, _] =>
              Some(msg.guildId)
            case msg: GatewayEvent.OptGuildEvent[_] =>
              msg.guildId
            case msg: GatewayEvent.ChannelEvent[_] =>
              channelToGuild.get(msg.channelId)
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
  def guildFilterApiMessage[Msg <: APIMessage](guildId: GuildId): Flow[Msg, Msg, NotUsed] = {
    withGuildInfoApiMessage[Msg].collect {
      case (msg @ (_: APIMessage.Ready | _: APIMessage.Resumed | _: APIMessage.UserUpdate), _) => msg
      case (msg, Some(`guildId`))                                                              => msg
    }
  }

  /**
    * GuildFilter serves the opposite function of [[GuildRouter]]. The job of
    * the guild filter is to only send messages to one actor that matches a
    * specific guild.
    *
    * Handles
    * - [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.GuildEvent]]
    * - [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.OptGuildEvent]]
    * - [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.ChannelEvent]]
    *
    * Global events like [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.Ready]],
    * [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.Resumed]] and
    * [[net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent.UserUpdate]] are sent no matter what.
    *
    * @param guildId The only guildID to allow through.
    */
  def guildFilterGatewayEvent[Msg <: ComplexGatewayEvent[_, _]](guildId: GuildId): Flow[Msg, Msg, NotUsed] = {
    withGuildInfoGatewayEvent[Msg].collect {
      case (msg @ (_: GatewayEvent.Ready | _: GatewayEvent.Resumed | _: GatewayEvent.UserUpdate), _) => msg
      case (msg, Some(`guildId`))                                                                    => msg
    }
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
  def gatewayEventGroupByGuildId[Msg <: ComplexGatewayEvent[_, _]] =
    withGuildInfoGatewayEvent[Msg]
      .collect {
        case (msg, Some(guildId)) => msg -> guildId
      }
      .groupBy(Int.MaxValue, _._2)
      .map(_._1)
}
