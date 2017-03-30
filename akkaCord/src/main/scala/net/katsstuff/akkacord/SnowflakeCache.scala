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

import java.time.Instant

import scala.collection.mutable

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.EventStream
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.http.{RawChannel, RawDMChannel, RawGuild, RawGuildChannel, RawGuildMember, RawUnavailableGuild}
import net.katsstuff.akkacord.http.websocket.WsEvent
import net.katsstuff.akkacord.http.websocket.WsEvent.GuildDeleteData
import net.katsstuff.akkacord.util.CachedImmutable

class SnowflakeCache(eventStream: EventStream) extends Actor with ActorLogging {

  private val _dmChannels =
    CachedImmutable[mutable.HashMap[Snowflake, DMChannel], Map[Snowflake, DMChannel]](new mutable.HashMap[Snowflake, DMChannel])
  private var _botUser: User = _

  private val _unavailableGuilds =
    CachedImmutable[mutable.HashMap[Snowflake, UnavailableGuild], Map[Snowflake, UnavailableGuild]](new mutable.HashMap[Snowflake, UnavailableGuild])
  private val _guilds =
    CachedImmutable[mutable.HashMap[Snowflake, AvailableGuild], Map[Snowflake, AvailableGuild]](new mutable.HashMap[Snowflake, AvailableGuild])
  private val _messages =
    CachedImmutable[mutable.Map[Snowflake, CachedImmutable[mutable.HashMap[Snowflake, Message], Map[Snowflake, Message]]], Map[
      Snowflake,
      CachedImmutable[mutable.HashMap[Snowflake, Message], Map[Snowflake, Message]]
    ]](
      new mutable.HashMap[Snowflake, CachedImmutable[mutable.HashMap[Snowflake, Message], Map[Snowflake, Message]]]
        .withDefault(_ => CachedImmutable(new mutable.HashMap[Snowflake, Message]))
    )
  private val _lastTyped =
    CachedImmutable[mutable.Map[Snowflake, CachedImmutable[mutable.HashMap[Snowflake, Instant], Map[Snowflake, Instant]]], Map[
      Snowflake,
      CachedImmutable[mutable.HashMap[Snowflake, Instant], Map[Snowflake, Instant]]
    ]](
      new mutable.HashMap[Snowflake, CachedImmutable[mutable.HashMap[Snowflake, Instant], Map[Snowflake, Instant]]]
        .withDefault(_ => CachedImmutable(new mutable.HashMap[Snowflake, Instant]))
    )
  private val _users = CachedImmutable[mutable.HashMap[Snowflake, User], Map[Snowflake, User]](new mutable.HashMap[Snowflake, User])

  private var snapshot: CacheSnapshot = _

  private def updateSnapshot(): Unit =
    snapshot = CacheSnapshot(
      _botUser,
      _dmChannels.value,
      _unavailableGuilds.value,
      _guilds.value,
      _messages.value.mapValues(_.value),
      _lastTyped.value.mapValues(_.value),
      _users.value
    )

  private def createUpdateChannel(channel: Channel): Unit =
    channel match {
      case dMChannel:    DMChannel    => _dmChannels.modify(_.put(dMChannel.id, dMChannel))
      case guildChannel: GuildChannel => createUpdateGuildChannel(guildChannel)
    }

  private def createUpdateGuildChannel(guildChannel: GuildChannel): Unit =
    guildUpdate(guildChannel.guildId, "channel") { guild =>
      _guilds.modify(_.put(guildChannel.guildId, guild.copy(channels = guild.channels + (guildChannel.id -> guildChannel))))
    }

  private def guildUpdate(id: Snowflake, updateType: String)(f: AvailableGuild => Unit): Unit =
    _guilds.modify(_.get(id) match {
      case Some(guild) => f(guild)
      case None        => log.warning("Received {} for unknown guild {}", updateType, id)
    })

  override def receive: Receive = {
    case RawWsEvent(WsEvent.Ready, WsEvent.ReadyData(_, user, rawPrivateChannels, rawGuilds, _, _)) =>
      val (dmChannels, users) = rawPrivateChannels.map {
        case RawDMChannel(id, _, recipient, lastMessageId) =>
          (id -> DMChannel(id, Some(lastMessageId), recipient.id), recipient.id -> recipient)
      }.unzip

      val guilds = rawGuilds.map {
        case RawUnavailableGuild(id, _) =>
          id -> UnavailableGuild(id)
      }

      _botUser = user
      _dmChannels.modify(_ ++= dmChannels)
      _users.modify(_ ++= users)
      _unavailableGuilds.modify(_ ++= guilds)
      updateSnapshot()

      eventStream.publish(APIMessage.Ready(snapshot))
    case RawWsEvent(WsEvent.Resumed, _) =>
      eventStream.publish(APIMessage.Resumed(snapshot))
    case RawWsEvent(WsEvent.ChannelCreate, rawChannel: RawChannel) => ???
    case RawWsEvent(WsEvent.ChannelUpdate, rawChannel: RawGuildChannel) => ???
    case RawWsEvent(WsEvent.ChannelDelete, rawChannel: RawChannel) => ???
    case RawWsEvent(WsEvent.GuildCreate, rawGuild: RawGuild) =>

      val (users, members) = rawGuild.members.get.map { case RawGuildMember(user, nick, roles, joinedAt, deaf, mute) =>

        (user.id -> user) -> (user.id -> GuildMember(user.id, nick, roles, joinedAt, deaf, mute))
      }.unzip

      val channels = rawGuild.channels.get.map { case RawGuildChannel(id, guildId, name, channelType, position, _,
      permissionOverwrites, topic, lastMessageId, bitrate, userLimit) =>
        channelType match {
          case "text" => id -> TGuildChannel(id, guildId.getOrElse(rawGuild.id), name, position, permissionOverwrites, topic, lastMessageId.map(Snowflake.apply))
          case "voice" => id -> VGuildChannel(id, guildId.getOrElse(rawGuild.id), name, position, permissionOverwrites, bitrate.get, userLimit.get)
        }
      }

      //Gets here are because everything should be sent here
      val guild = AvailableGuild(
        id = rawGuild.id,
        name = rawGuild.name,
        icon = rawGuild.icon,
        splash = rawGuild.splash,
        ownerId = rawGuild.ownerId,
        afkChannelId = rawGuild.afkChannelId,
        afkTimeout = rawGuild.afkTimeout,
        embedEnabled = rawGuild.embedEnabled,
        embedChannelId = rawGuild.embedChannelId,
        verificationLevel = rawGuild.verificationLevel,
        defaultMessageNotifications = rawGuild.defaultMessageNotifications,
        roles = rawGuild.roles.map(r => r.id -> r).toMap,
        emojis = rawGuild.emojis.map(e => e.id -> e).toMap,
        mfaLevel = rawGuild.mfaLevel,
        joinedAt = rawGuild.joinedAt.get,
        large = rawGuild.large.get,
        memberCount = rawGuild.memberCount.get,
        voiceStates = rawGuild.voiceStates.get,
        members = members.toMap,
        channels = channels.toMap
      )

      _guilds.modify(_.put(rawGuild.id, guild))
      _users.modify(_ ++= users)

    case RawWsEvent(WsEvent.GuildUpdate, rawGuild: RawGuild) => ???
    case RawWsEvent(WsEvent.GuildDelete, GuildDeleteData(id, unavailable)) => ???
  }
}
object SnowflakeCache {
  def props(eventStream: EventStream): Props = Props(classOf[SnowflakeCache], eventStream)
}

sealed trait RawEvent
case class RawWsEvent(event: WsEvent, data: Any)
