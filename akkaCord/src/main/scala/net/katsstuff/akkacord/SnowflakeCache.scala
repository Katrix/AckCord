/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
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
package net.katsstuff.akkacord

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.EventStream
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.handlers._
import net.katsstuff.akkacord.handlers.ws._
import net.katsstuff.akkacord.http.websocket.WsEvent
import net.katsstuff.akkacord.http.websocket.WsEvent.GuildIntegrationsUpdateData
import net.katsstuff.akkacord.http.{RawDMChannel, RawUnavailableGuild}

class SnowflakeCache(eventStream: EventStream) extends Actor with ActorLogging {

  private var prevSnapshot: CacheSnapshot = _
  private var snapshot:     CacheSnapshot = _

  private val handlers: Map[Any, Handler[_, _]] = Map(
    WsEvent.Resumed           -> WsHandlerResumed,
    WsEvent.ChannelCreate     -> WsHandlerChannelCreate,
    WsEvent.ChannelUpdate     -> WsHandlerChannelUpdate,
    WsEvent.ChannelDelete     -> WsHandlerChannelDelete,
    WsEvent.GuildCreate       -> WsHandlerGuildCreate,
    WsEvent.GuildUpdate       -> WsHandlerGuildUpdate,
    WsEvent.GuildDelete       -> WsHandlerGuildDelete,
    WsEvent.GuildEmojisUpdate -> WsHandlerGuildEmojiUpdate,
    WsEvent.GuildMemberAdd    -> WsHandlerGuildMemberAdd,
    WsEvent.GuildMemberRemove -> WsHandlerGuildMemberRemove,
    WsEvent.GuildMemberUpdate -> WsHandlerGuildMemberUpdate,
    WsEvent.GuildMemberChunk  -> WsHandlerGuildMemberChunk,
    WsEvent.GuildRoleCreate   -> WsHandlerGuildRoleCreate,
    WsEvent.GuildRoleUpdate   -> WsHandlerGuildRoleUpdate,
    WsEvent.GuildRoleDelete   -> WsHandlerGuildRoleDelete,
    WsEvent.MessageCreate     -> WsHandlerMessageCreate,
    WsEvent.MessageUpdate     -> WsHandlerMessageUpdate,
    WsEvent.MessageDelete     -> WsHandlerMessageDelete,
    WsEvent.MessageDeleteBulk -> WsHandlerMessageDeleteBulk,
    WsEvent.PresenceUpdate    -> WsHandlerPresenceUpdate
  )

  private def updateSnapshot(newSnapshot: CacheSnapshot): Unit = {
    if (newSnapshot ne snapshot) {
      prevSnapshot = snapshot
      snapshot = newSnapshot
    }
  }

  private def getHandler[Event[_], EventData](event: Event[EventData]): Handler[EventData, AnyRef] =
    handlers(event).asInstanceOf[Handler[EventData, AnyRef]]

  private def isReady: Boolean = prevSnapshot != null && snapshot != null

  private def guildUpdate(id: Snowflake, updateType: String)(f: AvailableGuild => AvailableGuild): Unit =
    snapshot.getGuild(id) match {
      case Some(guild) =>
        updateSnapshot(snapshot.copy(guilds = snapshot.guilds + ((id, f(guild)))))
      case None => log.warning("Received {} for unknown guild {}", updateType, id)
    }

  override def receive: Receive = {
    case RawWsEvent(WsEvent.Ready, WsEvent.ReadyData(_, user, rawPrivateChannels, rawGuilds, _, _)) =>
      val (dmChannels, users) = rawPrivateChannels.map {
        case RawDMChannel(id, _, recipient, lastMessageId) =>
          (id -> DMChannel(id, lastMessageId, recipient.id), recipient.id -> recipient)
      }.unzip

      val guilds = rawGuilds.map {
        case RawUnavailableGuild(id, _) =>
          id -> UnavailableGuild(id)
      }

      val firstSnapshot = CacheSnapshot(
        botUser = user,
        dmChannels = dmChannels.toMap,
        unavailableGuilds = guilds.toMap,
        guilds = Map(),
        messages = Map().withDefaultValue(Map()),
        lastTyped = Map().withDefaultValue(Map()),
        users = users.toMap,
        presences = Map().withDefaultValue(Map())
      )

      prevSnapshot = firstSnapshot
      snapshot = firstSnapshot

      eventStream.publish(APIMessage.Ready(snapshot, prevSnapshot))
    case raw: RawWsEvent[_] if isReady && handlers.contains(raw.event) =>
      val handlerResult = getHandler(raw.event).handle(snapshot, raw.data)(log)
      handlerResult match {
        case HandlerResult(newSnapshot, createMessage) =>
          updateSnapshot(newSnapshot)
          eventStream.publish(createMessage(snapshot, prevSnapshot))
        case OptionalMessageHandlerResult(newSnapshot, createMessage) =>
          updateSnapshot(newSnapshot)
          createMessage.apply(snapshot, prevSnapshot).foreach(eventStream.publish)
        case NoHandlerResult =>
      }
    case RawWsEvent(WsEvent.GuildBanAdd, user) if isReady                                             => ???
    case RawWsEvent(WsEvent.GuildBanRemove, user) if isReady                                          => ???
    case RawWsEvent(WsEvent.GuildIntegrationsUpdate, GuildIntegrationsUpdateData(guildId)) if isReady => ???
    case RawWsEvent(_, _) if !isReady => log.error("Received event before ready")
  }
}
object SnowflakeCache {
  def props(eventStream: EventStream): Props = Props(classOf[SnowflakeCache], eventStream)
}

sealed trait RawEvent
case class RawWsEvent[Data](event: WsEvent[Data], data: Data) extends RawEvent
