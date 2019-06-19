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
package ackcord.cachehandlers

import java.time.Instant

import scala.collection.mutable

import ackcord.CacheSnapshot.BotUser
import ackcord.data._
import ackcord.{CacheSnapshotWithMaps, MemoryCacheSnapshot, SnowflakeMap}
import shapeless.tag._

/**
  * A mutable builder for creating a new snapshot
  */
class CacheSnapshotBuilder(
    var botUser: User @@ BotUser,
    var dmChannelMap: mutable.Map[ChannelId, DMChannel],
    var groupDmChannelMap: mutable.Map[ChannelId, GroupDMChannel],
    var unavailableGuildMap: mutable.Map[GuildId, UnavailableGuild],
    var guildMap: mutable.Map[GuildId, Guild],
    var messageMap: mutable.Map[ChannelId, mutable.Map[MessageId, Message]],
    var lastTypedMap: mutable.Map[ChannelId, mutable.Map[UserId, Instant]],
    var userMap: mutable.Map[UserId, User],
    var banMap: mutable.Map[GuildId, mutable.Map[UserId, Ban]]
) extends CacheSnapshotWithMaps {

  override type MapType[K, V] = mutable.Map[SnowflakeType[K], V]

  def toImmutable: MemoryCacheSnapshot = {
    def convertNested[K1, K2, V](
        map: mutable.Map[SnowflakeType[K1], mutable.Map[SnowflakeType[K2], V]]
    ): SnowflakeMap[K1, SnowflakeMap[K2, V]] = SnowflakeMap(map.map { case (k, v) => k -> SnowflakeMap(v) })

    MemoryCacheSnapshot(
      botUser = botUser,
      dmChannelMap = SnowflakeMap(dmChannelMap),
      groupDmChannelMap = SnowflakeMap(groupDmChannelMap),
      unavailableGuildMap = SnowflakeMap(unavailableGuildMap),
      guildMap = SnowflakeMap(guildMap),
      messageMap = convertNested(messageMap),
      lastTypedMap = convertNested(lastTypedMap),
      userMap = SnowflakeMap(userMap),
      banMap = convertNested(banMap)
    )
  }
  override def getChannelMessages(channelId: ChannelId): mutable.Map[SnowflakeType[Message], Message] =
    messageMap.getOrElse(channelId, mutable.Map.empty)

  override def getChannelLastTyped(channelId: ChannelId): mutable.Map[SnowflakeType[User], Instant] =
    lastTypedMap.getOrElse(channelId, mutable.Map.empty)

  override def getGuildBans(id: GuildId): mutable.Map[SnowflakeType[User], Ban] =
    banMap.getOrElse(id, mutable.Map.empty)
}
object CacheSnapshotBuilder {
  def apply(snapshot: MemoryCacheSnapshot): CacheSnapshotBuilder = {
    def toMutableMap[K, V](map: SnowflakeMap[K, V]): mutable.Map[SnowflakeType[K], V] = {
      val builder = mutable.Map.newBuilder[SnowflakeType[K], V]
      builder.sizeHint(map)
      builder ++= map
      builder.result()
    }

    def toMutableMapNested[K1, K2, V](map: SnowflakeMap[K1, SnowflakeMap[K2, V]]) =
      toMutableMap(map.map { case (k, v) => k -> toMutableMap(v) })

    new CacheSnapshotBuilder(
      botUser = snapshot.botUser,
      dmChannelMap = toMutableMap(snapshot.dmChannelMap),
      groupDmChannelMap = toMutableMap(snapshot.groupDmChannelMap),
      unavailableGuildMap = toMutableMap(snapshot.unavailableGuildMap),
      guildMap = toMutableMap(snapshot.guildMap),
      messageMap = toMutableMapNested(snapshot.messageMap),
      lastTypedMap = toMutableMapNested(snapshot.lastTypedMap),
      userMap = toMutableMap(snapshot.userMap),
      banMap = toMutableMapNested(snapshot.banMap)
    )
  }
}
