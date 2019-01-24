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
package net.katsstuff.ackcord

import java.time.Instant

import scala.language.higherKinds

import cats.data.OptionT
import net.katsstuff.ackcord.CacheSnapshot.BotUser
import net.katsstuff.ackcord.data._
import shapeless.tag._

/**
  * A representation of the cache.
  * @define optionalMap This method returns a map that might be empty depending
  *                     on the implementation. Make sure you know if this will
  *                     be the case before you use this method.
  */
trait CacheSnapshot[F[_]] {

  /**
    * The map type to use. Mutable for builder, immutable otherwise.
    */
  type MapType[K, V] <: collection.Map[SnowflakeType[K], V]

  /**
    * The current dm channels.
    *
    * $optionalMap
    */
  def dmChannelMap: F[MapType[Channel, DMChannel]]

  /**
    * The current group dm channels.
    *
    * $optionalMap
    */
  def groupDmChannelMap: F[MapType[Channel, GroupDMChannel]]

  /**
    * The guilds currently not available.
    *
    * $optionalMap
    */
  def unavailableGuildMap: F[MapType[Guild, UnavailableGuild]]

  /**
    * The currently joined guilds.
    *
    * $optionalMap
    */
  def guildMap: F[MapType[Guild, Guild]]

  /**
    * All messages, organized by channelId, and then messageId.
    *
    * $optionalMap
    */
  def messageMap: F[MapType[Channel, MapType[Message, Message]]]

  /**
    * The point each user typed for each channel.
    *
    * $optionalMap
    */
  def lastTypedMap: F[MapType[Channel, MapType[User, Instant]]]

  /**
    * All the users currently tracked.
    *
    * $optionalMap
    */
  def userMap: F[MapType[User, User]]

  /**
    * The bans received this session. NOTE: This is not all the bans that exists,
    * only the ones received during this session. If you want all the bans,
    * use [[net.katsstuff.ackcord.http.rest.GetGuildBans]].
    *
    * $optionalMap
    */
  def banMap: F[MapType[Guild, MapType[User, Ban]]]

  /**
    * Our bot user. Tagged to allow special syntax.
    */
  def botUser: F[User @@ BotUser]

  /**
    * Get a dm channel by id.
    */
  def getDmChannel(id: ChannelId): OptionT[F, DMChannel]

  /**
    * Get the dm channel for a specific user.
    */
  def getUserDmChannel(id: UserId): OptionT[F, DMChannel]

  /**
    * Get a group dm channel by id.
    */
  def getGroupDmChannel(id: ChannelId): OptionT[F, GroupDMChannel]

  /**
    * Get a guild by id.
    */
  def getGuild(id: GuildId): OptionT[F, Guild]

  /**
    * Get guild by id, also including unavailable guilds.
    */
  def getGuildWithUnavailable(id: GuildId): OptionT[F, UnknownStatusGuild]

  /**
    * Gets all the messages for a specific channel.
    *
    * $optionalMap
    */
  def getChannelMessages(channelId: ChannelId): MapType[Message, Message]

  /**
    * Get a message, specifying both the channel, and message id.
    */
  def getMessage(channelId: ChannelId, messageId: MessageId): OptionT[F, Message]

  /**
    * Get a message by id without knowing the channel it belongs to.
    */
  def getMessage(messageId: MessageId): OptionT[F, Message]

  /**
    * Get a guild channel.
    * @param guildId The guild id
    * @param id The channel id
    */
  def getGuildChannel(guildId: GuildId, id: ChannelId): OptionT[F, GuildChannel]

  /**
    * Get a guild channel by id without knowing the guild it belongs to.
    */
  def getGuildChannel(id: ChannelId): OptionT[F, GuildChannel]

  /**
    * Get a channel by id, ignoring if it's a dm or guild channel.
    */
  def getChannel(id: ChannelId): OptionT[F, Channel]

  /**
    * Get a text channel by id, ignoring if it's a dm or guild channel.
    */
  def getTChannel(id: ChannelId): OptionT[F, TChannel]

  /**
    * Get a role by id without knowing the guild it belongs to.
    */
  def getRole(id: RoleId): OptionT[F, Role]

  /**
    * Get a role by a guildId and a roleID.
    */
  def getRole(guildId: GuildId, roleId: RoleId): OptionT[F, Role]

  /**
    * Get an emoji by id without knowing the guild it belongs to.
    */
  def getEmoji(id: EmojiId): OptionT[F, Emoji]

  /**
    * Get the instant a user last typed in a channel.
    */
  def getLastTyped(channelId: ChannelId, userId: UserId): OptionT[F, Instant]

  //For implementers, remember to check if the user to return is the bot user
  /**
    * Get a user by id.
    */
  def getUser(id: UserId): OptionT[F, User]

  /**
    * Gets the ban for a specific user.
    */
  def getBan(guildId: GuildId, userId: UserId): OptionT[F, Ban]

  /**
    * Get the presence of a user for a specific guild
    */
  def getPresence(guildId: GuildId, userId: UserId): OptionT[F, Presence]
}

object CacheSnapshot {

  /**
    * Phantom type for the bot (client) user. Used for syntax.
    */
  sealed trait BotUser
}
