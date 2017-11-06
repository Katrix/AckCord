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
package net.katsstuff.ackcord.util

import scala.collection.mutable

import akka.actor.{Actor, ActorLogging, Props, Terminated}
import akka.event.Logging
import net.katsstuff.ackcord.DiscordClient.ShutdownClient
import net.katsstuff.ackcord.data.{ChannelId, GuildChannel, GuildId}
import net.katsstuff.ackcord.http.websocket.gateway.GatewayEvent
import net.katsstuff.ackcord.{APIMessage, DiscordClient}

/**
  * GuildFilter serves the opposite function of [[GuildRouter]]. The job of
  * the guild filter is to only send messages to one actor that matches a
  * specific guild.
  *
  * Handles
  * - [[APIMessage.ChannelMessage]]
  * - [[APIMessage.GuildMessage]]
  * - [[APIMessage.MessageMessage]]
  * - [[APIMessage.VoiceStateUpdate]]
  * - [[GatewayEvent.GuildEvent]]
  * - [[GatewayEvent.OptGuildEvent]]
  *
  * This actor has a small cache for figuring out what actor to send messages
  * to for the gateway channel events.
  *
  * Global events like [[APIMessage.Ready]], [[APIMessage.Resumed]] and
  * [[APIMessage.UserUpdate]] are sent to matter what.
  *
  * It also respects [[DiscordClient.ShutdownClient]].
  * It sends the shutdown to the handler, and the hander has stopped, it stops
  * itself. The handler will not receive any further
  * events once a shutdown has been started.
  *
  * @param guildId The only guildID to allow through.
  * @param handlerProps A props for the handler.
  */
class GuildFilter(guildId: GuildId, handlerProps: Props) extends Actor with ActorLogging {
  private var handler        = context.actorOf(handlerProps, "FilterHandler")
  var channelToGuild = mutable.HashMap.empty[ChannelId, GuildId]
  var isShuttingDown = false
  context.watch(handler)

  override def receive: Receive = {
    case msg @ (_: APIMessage.Ready | _: APIMessage.Resumed | _: APIMessage.UserUpdate) => handler ! msg
    case msg: APIMessage.GuildMessage                                                   => sendToGuild(msg.guild.id, msg)
    case msg: APIMessage.ChannelMessage =>
      msg.channel match {
        case ch: GuildChannel => sendToGuild(ch.guildId, msg)
        case _                =>
      }
    case msg: APIMessage.MessageMessage =>
      msg.message.channel(msg.snapshot) match {
        case Some(gchannel: GuildChannel) => sendToGuild(gchannel.guildId, msg)
        case _                            =>
      }
    case msg @ APIMessage.VoiceStateUpdate(state, _, _) =>
      state.guildId match {
        case Some(id) => sendToGuild(id, msg)
        case None     =>
      }
    case msg: GatewayEvent.GuildCreate =>
      sendToGuild(msg.guildId, msg)
      msg.data.channels.foreach(channelToGuild ++= _.map(_.id -> msg.guildId))
    case msg: GatewayEvent.ChannelCreate =>
      msg.guildId.foreach { guildId =>
        sendToGuild(guildId, msg)
        channelToGuild.put(msg.data.id, guildId)
      }
    case msg: GatewayEvent.ChannelDelete =>
      msg.guildId.foreach(sendToGuild(_, msg))
      channelToGuild.remove(msg.data.id)
    case msg: GatewayEvent.GuildEvent[_] => sendToGuild(msg.guildId, msg)
    case msg: GatewayEvent.OptGuildEvent[_] =>
      msg.guildId.foreach(sendToGuild(_, msg))
    case msg: GatewayEvent.ChannelEvent[_] =>
      channelToGuild.get(msg.channelId).foreach(sendToGuild(_, msg))
    case DiscordClient.ShutdownClient =>
      isShuttingDown = true
      handler ! ShutdownClient
    case Terminated(_) =>
      val level = if (isShuttingDown) Logging.DebugLevel else Logging.WarningLevel
      log.log(level, "Actor for guild {} shut down", guildId)

      if (isShuttingDown) {
        context.stop(self)
      } else {
        handler = context.actorOf(handlerProps)
      }
  }

  def sendToGuild(guildId: GuildId, msg: Any): Unit = if (!isShuttingDown && guildId == this.guildId) handler ! msg
}
object GuildFilter {
  def props(guildId: GuildId, props: Props): Props =
    Props(new GuildFilter(guildId, props))
}
