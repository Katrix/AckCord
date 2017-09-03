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

import scala.language.higherKinds

import net.katsstuff.akkacord.CacheSnapshotLike.BotUser
import net.katsstuff.akkacord.data._

import shapeless.tag._

trait CacheSnapshotLike {
  type MapType[A, B] <: collection.Map[A, B]

  def botUser:           User @@ BotUser
  def dmChannels:        MapType[ChannelId, DMChannel]
  def groupDmChannels:   MapType[ChannelId, GroupDMChannel]
  def unavailableGuilds: MapType[GuildId, UnavailableGuild]
  def guilds:            MapType[GuildId, Guild]
  def messages:          MapType[ChannelId, MapType[MessageId, Message]]
  def lastTyped:         MapType[ChannelId, MapType[UserId, Instant]]
  def users:             MapType[UserId, User]
  def presences:         MapType[GuildId, MapType[UserId, Presence]]

  def getDmChannel(id: ChannelId): Option[DMChannel] = dmChannels.get(id)
  def getGroupDmChannel(id: ChannelId): Option[GroupDMChannel] = groupDmChannels.get(id)

  def getGuild(id: GuildId):                Option[Guild]              = guilds.get(id)
  def getGuildWithUnavailable(id: GuildId): Option[UnknownStatusGuild] = guilds.get(id).orElse(unavailableGuilds.get(id))

  def getGuildChannel(guildId: GuildId, id: ChannelId): Option[GuildChannel] = guilds.get(guildId).flatMap(_.channels.get(id))
  def getGuildChannel(id: ChannelId): Option[GuildChannel] = guilds.values.collectFirst {
    case guild if guild.channels.contains(id) => guild.channels(id)
  }

  def getChannel(id: ChannelId): Option[Channel] = getDmChannel(id).orElse(getGuildChannel(id))

  def getRole(id: RoleId): Option[Role] = guilds.values.collectFirst {
    case guild if guild.roles.contains(id) => guild.roles(id)
  }

  def getEmoji(id: EmojiId): Option[GuildEmoji] = guilds.values.collectFirst {
    case guild if guild.emojis.contains(id) => guild.emojis(id)
  }

  def getMember(id: UserId): Option[GuildMember] = guilds.values.collectFirst {
    case guild if guild.members.contains(id) => guild.members(id)
  }

  def getChannelMessages(channelId: ChannelId): MapType[MessageId, Message]

  def getMessage(channelId: ChannelId, messageId: MessageId): Option[Message] = messages.get(channelId).flatMap(_.get(messageId))
  def getMessage(messageId: MessageId): Option[Message] = messages.values.collectFirst {
    case channelMap if channelMap.contains(messageId) => channelMap(messageId)
  }

  def getChannelLastTyped(channelId: ChannelId): MapType[UserId, Instant]

  def getLastTyped(channelId: ChannelId, userId: UserId): Option[Instant] = lastTyped.get(channelId).flatMap(_.get(userId))

  def getUser(id: UserId): Option[User] = users.get(id)

  def getPresence(guildId: GuildId, userId: UserId): Option[Presence] = presences.get(guildId).flatMap(_.get(userId))
}
object CacheSnapshotLike {
  sealed trait BotUser
}