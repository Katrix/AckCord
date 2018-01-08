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

import java.time.Instant

import scala.language.higherKinds

import net.katsstuff.ackcord.CacheSnapshotLike.BotUser
import net.katsstuff.ackcord.data._
import shapeless.tag._

/**
  * A representation of the cache
  */
trait CacheSnapshotLike {

  /**
    * The map type to use. Mutable for builder, immutable otherwise
    */
  type MapType[A, B] <: collection.Map[SnowflakeType[A], B]

  /**
    * Our bot user. Tagged to allow special syntax
    */
  def botUser: User @@ BotUser

  /**
    * The current dm channels
    */
  def dmChannels: MapType[Channel, DMChannel]

  /**
    * The current group dm channels
    */
  def groupDmChannels: MapType[Channel, GroupDMChannel]

  /**
    * The guilds currently not available
    */
  def unavailableGuilds: MapType[Guild, UnavailableGuild]

  /**
    * The currently joined guilds
    */
  def guilds: MapType[Guild, Guild]

  /**
    * All messages, organized by channelId, and then messageId
    */
  def messages: MapType[Channel, MapType[Message, Message]]

  /**
    * The point each user typed for each channel
    */
  def lastTyped: MapType[Channel, MapType[User, Instant]]

  /**
    * All the users currently tracked
    */
  def users: MapType[User, User]

  /**
    * The bans received this session. NOTE: This is not all the bans that exists,
    * only the ones received during this session. If you want all the bans,
    * use [[net.katsstuff.ackcord.http.requests.RESTRequests.GetGuildBans]].
    */
  def bans: MapType[Guild, MapType[User, Ban]]

  /**
    * Get a dm channel by id
    */
  def getDmChannel(id: ChannelId): Option[DMChannel] = dmChannels.get(id)

  /**
    * Get a group dm channel by id
    */
  def getGroupDmChannel(id: ChannelId): Option[GroupDMChannel] = groupDmChannels.get(id)

  /**
    * Get a guild by id
    */
  def getGuild(id: GuildId): Option[Guild] = guilds.get(id)

  /**
    * Get guild by id, also including unavailable guilds
    */
  def getGuildWithUnavailable(id: GuildId): Option[UnknownStatusGuild] =
    guilds.get(id).orElse(unavailableGuilds.get(id))

  /**
    * Get a guild channel
    * @param guildId The guild id
    * @param id The channel id
    */
  def getGuildChannel(guildId: GuildId, id: ChannelId): Option[GuildChannel] =
    guilds.get(guildId).flatMap(_.channels.get(id))

  /**
    * Get a guild channel by id without knowing the guild it belongs to
    */
  def getGuildChannel(id: ChannelId): Option[GuildChannel] = guilds.values.collectFirst {
    case guild if guild.channels.contains(id) => guild.channels(id)
  }

  /**
    * Get a channel by id, ignoring if it's a dm or guild channel
    */
  def getChannel(id: ChannelId): Option[Channel] = getDmChannel(id).orElse(getGuildChannel(id))

  /**
    * Get a text channel by id, ignoring if it's a dm or guild channel
    */
  def getTChannel(id: ChannelId): Option[TChannel] = getChannel(id).collect { case tc: TChannel => tc }

  /**
    * Get a role by id without knowing the guild it belongs to
    */
  def getRole(id: RoleId): Option[Role] = guilds.values.collectFirst {
    case guild if guild.roles.contains(id) => guild.roles(id)
  }

  /**
    * Get a role by a guildId and a roleID.
    */
  def getRole(guildId: GuildId, roleId: RoleId): Option[Role] = getGuild(guildId).flatMap(_.roles.get(roleId))

  /**
    * Get an emoji by id without knowing the guild it belongs to
    */
  def getEmoji(id: EmojiId): Option[Emoji] = guilds.values.collectFirst {
    case guild if guild.emojis.contains(id) => guild.emojis(id)
  }

  /**
    * Get all the messages for a certain channel
    */
  def getChannelMessages(channelId: ChannelId): MapType[Message, Message]

  /**
    * Get a message, specifying both the channel, and message id
    */
  def getMessage(channelId: ChannelId, messageId: MessageId): Option[Message] =
    messages.get(channelId).flatMap(_.get(messageId))

  /**
    * Get a message by id without knowing the channel it belongs to
    */
  def getMessage(messageId: MessageId): Option[Message] = messages.values.collectFirst {
    case channelMap if channelMap.contains(messageId) => channelMap(messageId)
  }

  /**
    * Get a map of when users last typed in a channel
    */
  def getChannelLastTyped(channelId: ChannelId): MapType[User, Instant]

  /**
    * Get the instant a user last typed in a channel
    */
  def getLastTyped(channelId: ChannelId, userId: UserId): Option[Instant] =
    lastTyped.get(channelId).flatMap(_.get(userId))

  /**
    * Get a user by id
    */
  def getUser(id: UserId): Option[User] =
    if (id == botUser.id) Some(botUser)
    else users.get(id)

  /**
    * Get the presence of a user for a specific guild
    */
  def getPresence(guildId: GuildId, userId: UserId): Option[Presence] = getGuild(guildId).flatMap(_.presences.get(userId))
}

object CacheSnapshotLike {

  /**
    * Phantom type for the bot (client) user. Used for syntax.
    */
  sealed trait BotUser
}
