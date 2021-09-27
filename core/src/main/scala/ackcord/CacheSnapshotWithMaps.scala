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
package ackcord

import java.time.Instant

import ackcord.data._

/** A cache snapshot where the getters can use the maps to get their data. */
trait CacheSnapshotWithMaps extends CacheSnapshot {

  override def getDmChannel(id: SnowflakeType[DMChannel]): Option[DMChannel] =
    dmChannelMap.get(id)

  override def getUserDmChannel(id: UserId): Option[DMChannel] =
    dmChannelMap.find(_._2.userId == id).map(_._2)

  override def getGroupDmChannel(
      id: SnowflakeType[GroupDMChannel]
  ): Option[GroupDMChannel] =
    groupDmChannelMap.get(id)

  override def getGuild(id: GuildId): Option[GatewayGuild] = guildMap.get(id)

  override def getGuildWithUnavailable(
      id: GuildId
  ): Option[UnknownStatusGuild] =
    getGuild(id).orElse(unavailableGuildMap.get(id))

  override def getMessage(
      channelId: TextChannelId,
      messageId: MessageId
  ): Option[Message] =
    messageMap.get(channelId).flatMap(_.get(messageId))

  override def getMessage(messageId: MessageId): Option[Message] =
    messageMap.collectFirst {
      case (_, chMap) if chMap.contains(messageId) => chMap(messageId)
    }

  override def getGuildChannel(
      guildId: GuildId,
      id: GuildChannelId
  ): Option[GuildChannel] =
    guildMap.get(guildId).flatMap(_.channels.get(id))

  override def getGuildChannel(id: GuildChannelId): Option[GuildChannel] =
    guildMap.collectFirst {
      case (_, gMap) if gMap.channels.contains(id) => gMap.channels(id)
    }

  override def getThread(
      guildId: GuildId,
      id: ThreadGuildChannelId
  ): Option[ThreadGuildChannel] =
    guildMap.get(guildId).flatMap(_.threads.get(id))

  override def getThread(id: ThreadGuildChannelId): Option[ThreadGuildChannel] =
    guildMap.collectFirst {
      case (_, gMap) if gMap.threads.contains(id) => gMap.threads(id)
    }

  override def getChannel(id: ChannelId): Option[Channel] =
    getDmChannel(id.asChannelId[DMChannel])
      .orElse(getGroupDmChannel(id.asChannelId[GroupDMChannel]))
      .orElse(getGuildChannel(id.asChannelId[GuildChannel]))

  override def getTextChannel(id: TextChannelId): Option[TextChannel] =
    getChannel(id).collect { case tCh: TextChannel => tCh }

  override def getRole(id: RoleId): Option[Role] =
    guildMap.collectFirst {
      case (_, gMap) if gMap.roles.contains(id) => gMap.roles(id)
    }

  override def getRole(guildId: GuildId, roleId: RoleId): Option[Role] =
    getGuild(guildId).flatMap(_.roles.get(roleId))

  override def getEmoji(id: EmojiId): Option[Emoji] =
    guildMap.collectFirst {
      case (_, gMap) if gMap.emojis.contains(id) => gMap.emojis(id)
    }

  override def getLastTyped(
      channelId: TextChannelId,
      userId: UserId
  ): Option[Instant] =
    lastTypedMap.get(channelId).flatMap(_.get(userId))

  override def getUser(id: UserId): Option[User] =
    if (id == botUser.id) Some(botUser)
    else userMap.get(id)

  override def getBan(guildId: GuildId, userId: UserId): Option[Ban] =
    getGuildBans(guildId).get(userId)

  override def getPresence(guildId: GuildId, userId: UserId): Option[Presence] =
    getGuild(guildId).flatMap(_.presences.get(userId))

}
