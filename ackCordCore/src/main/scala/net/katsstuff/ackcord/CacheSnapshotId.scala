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
package net.katsstuff.ackcord

import java.time.Instant

import scala.language.higherKinds

import cats.Id
import cats.catsInstancesForId
import cats.data.OptionT
import net.katsstuff.ackcord.data._

trait CacheSnapshotId extends CacheSnapshot[Id] {

  /**
    * The map type to use. Mutable for builder, immutable otherwise.
    */
  type MapType[K, V] <: collection.Map[SnowflakeType[K], V]

  /**
    * The current dm channels.
    */
  def dmChannelMap: MapType[Channel, DMChannel]

  /**
    * The current group dm channels.
    */
  def groupDmChannelMap: MapType[Channel, GroupDMChannel]

  /**
    * The guilds currently not available.
    */
  def unavailableGuildMap: MapType[Guild, UnavailableGuild]

  /**
    * The currently joined guilds.
    */
  def guildMap: MapType[Guild, Guild]

  /**
    * All messages, organized by channelId, and then messageId.
    */
  def messageMap: MapType[Channel, MapType[Message, Message]]

  /**
    * The point each user typed for each channel.
    */
  def lastTypedMap: MapType[Channel, MapType[User, Instant]]

  /**
    * All the users currently tracked.
    */
  def userMap: MapType[User, User]

  /**
    * The bans received this session. NOTE: This is not all the bans that exists,
    * only the ones received during this session. If you want all the bans,
    * use [[net.katsstuff.ackcord.http.rest.GetGuildBans]].
    */
  def banMap: MapType[Guild, MapType[User, Ban]]

  override def getDmChannel(id: ChannelId): OptionT[Id, DMChannel] = OptionT.fromOption[Id](dmChannelMap.get(id))

  override def getUserDmChannel(id: UserId): OptionT[Id, DMChannel] =
    OptionT.fromOption[Id](dmChannelMap.find(_._2.userId == id).map(_._2))

  override def getGroupDmChannel(id: ChannelId): OptionT[Id, GroupDMChannel] =
    OptionT.fromOption[Id](groupDmChannelMap.get(id))

  override def getGuild(id: GuildId): OptionT[Id, Guild] = OptionT.fromOption[Id](guildMap.get(id))

  override def getGuildWithUnavailable(id: GuildId): OptionT[Id, UnknownStatusGuild] =
    OptionT.fromOption[Id](getGuild(id).value.orElse(unavailableGuildMap.get(id)))

  /**
    * Gets all the messages for a specific channel.
    */
  def getChannelMessages(channelId: ChannelId): MapType[Message, Message]

  override def getMessage(channelId: ChannelId, messageId: MessageId): OptionT[Id, Message] =
    OptionT.fromOption[Id](messageMap.get(channelId).flatMap(_.get(messageId)))

  override def getMessage(messageId: MessageId): OptionT[Id, Message] =
    OptionT.fromOption[Id](messageMap.collectFirst { case (_, chMap) if chMap.contains(messageId) => chMap(messageId) })

  override def getGuildChannel(guildId: GuildId, id: ChannelId): OptionT[Id, GuildChannel] =
    OptionT.fromOption[Id](guildMap.get(guildId).flatMap(_.channels.get(id)))

  override def getGuildChannel(id: ChannelId): OptionT[Id, GuildChannel] =
    OptionT.fromOption[Id](guildMap.collectFirst { case (_, gMap) if gMap.channels.contains(id) => gMap.channels(id) })

  override def getChannel(id: ChannelId): OptionT[Id, Channel] =
    OptionT.fromOption[Id](getDmChannel(id).value.orElse(getGroupDmChannel(id).value).orElse(getGuildChannel(id).value))

  override def getTChannel(id: ChannelId): OptionT[Id, TChannel] =
    getChannel(id).collect { case tCh: TChannel => tCh }

  override def getRole(id: RoleId): OptionT[Id, Role] =
    OptionT.fromOption[Id](guildMap.collectFirst { case (_, gMap) if gMap.roles.contains(id) => gMap.roles(id) })

  override def getRole(guildId: GuildId, roleId: RoleId): OptionT[Id, Role] =
    getGuild(guildId).subflatMap(_.roles.get(roleId))

  override def getEmoji(id: EmojiId): OptionT[Id, Emoji] =
    OptionT.fromOption[Id](guildMap.collectFirst { case (_, gMap) if gMap.emojis.contains(id) => gMap.emojis(id) })

  /**
    * Get a map of when users last typed in a channel.
    */
  def getChannelLastTyped(channelId: ChannelId): MapType[User, Instant]

  override def getLastTyped(channelId: ChannelId, userId: UserId): OptionT[Id, Instant] =
    OptionT.fromOption[Id](lastTypedMap.get(channelId).flatMap(_.get(userId)))

  override def getUser(id: UserId): OptionT[Id, User] =
    if (id == botUser.id) OptionT.pure[Id](botUser)
    else OptionT.fromOption[Id](userMap.get(id))

  /**
    * Gets all the bans for a specific guild.
    */
  def getGuildBans(id: GuildId): MapType[User, Ban]

  override def getBan(guildId: GuildId, userId: UserId): OptionT[Id, Ban] =
    OptionT.fromOption[Id](getGuildBans(guildId).get(userId))

  override def getPresence(guildId: GuildId, userId: UserId): OptionT[Id, Presence] =
    getGuild(guildId).subflatMap(_.presences.get(userId))

}
