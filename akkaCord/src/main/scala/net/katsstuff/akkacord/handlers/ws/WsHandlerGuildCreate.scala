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
package net.katsstuff.akkacord.handlers
package ws

import akka.event.LoggingAdapter
import net.katsstuff.akkacord.APIMessage
import net.katsstuff.akkacord.data.{AvailableGuild, CacheSnapshot, GuildMember, Presence, PresenceGame, PresenceStatus, PresenceStreaming, TGuildChannel, VGuildChannel}
import net.katsstuff.akkacord.http.{RawGuild, RawGuildChannel, RawGuildMember, RawPresenceGame}

object WsHandlerGuildCreate extends Handler[RawGuild, APIMessage.GuildCreate] {
  override def handle(snapshot: CacheSnapshot, rawGuild: RawGuild)(implicit log: LoggingAdapter): AbstractHandlerResult[APIMessage.GuildCreate] = {
    val (users, members) = rawGuild.members.get.map {
      case RawGuildMember(user, nick, roles, joinedAt, deaf, mute) =>
        (user.id -> user) -> (user.id -> GuildMember(user.id, nick, roles, joinedAt, deaf, mute))
    }.unzip

    val channels = rawGuild.channels.get.map {
      case RawGuildChannel(id, guildId, name, channelType, position, _, permissionOverwrites, topic, lastMessageId, bitrate, userLimit) =>
        channelType match {
          case "text"  => id -> TGuildChannel(id, guildId.getOrElse(rawGuild.id), name, position, permissionOverwrites, topic, lastMessageId)
          case "voice" => id -> VGuildChannel(id, guildId.getOrElse(rawGuild.id), name, position, permissionOverwrites, bitrate.get, userLimit.get)
        }
    }

    val presences = rawGuild.presences.getOrElse(Seq.empty).flatMap { pres =>
      import shapeless.record._

      val status = pres.status.map {
        case "idle"    => PresenceStatus.Idle
        case "online"  => PresenceStatus.Online
        case "offline" => PresenceStatus.Offline
      }

      val content = pres.game.flatMap {
        case RawPresenceGame(Some(name), Some(0), url)       => Some(PresenceGame(name))
        case RawPresenceGame(Some(name), Some(1), Some(url)) => Some(PresenceStreaming(name, url))
        case _                                               => None
      }

      status.map(s => Presence(pres.user.get('id), content, s))
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
      roles = rawGuild.roles.map(r => r.id   -> r).toMap,
      emojis = rawGuild.emojis.map(e => e.id -> e).toMap,
      mfaLevel = rawGuild.mfaLevel,
      joinedAt = rawGuild.joinedAt.get,
      large = rawGuild.large.get,
      memberCount = rawGuild.memberCount.get,
      voiceStates = rawGuild.voiceStates.get,
      members = members.toMap,
      channels = channels.toMap,
      presences = presences
    )

    val newSnapshot = snapshot.copy(guilds = snapshot.guilds + ((guild.id, guild)), users = snapshot.users ++ users)
    HandlerResult(newSnapshot, APIMessage.GuildCreate(guild, _, _))
  }
}
