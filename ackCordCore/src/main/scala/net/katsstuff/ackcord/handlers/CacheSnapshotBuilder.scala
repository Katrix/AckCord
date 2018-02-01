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
package net.katsstuff.ackcord.handlers

import java.time.Instant

import scala.collection.mutable

import net.katsstuff.ackcord.CacheSnapshotLike.BotUser
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.{CacheSnapshotLike, SnowflakeMap}
import shapeless.tag._

/**
  * A mutable builder for creating a new snapshot
  */
class CacheSnapshotBuilder(
    var botUser: User @@ BotUser,
    var dmChannels: mutable.Map[ChannelId, DMChannel],
    var groupDmChannels: mutable.Map[ChannelId, GroupDMChannel],
    var unavailableGuilds: mutable.Map[GuildId, UnavailableGuild],
    var guilds: mutable.Map[GuildId, Guild],
    var messages: mutable.Map[ChannelId, mutable.Map[MessageId, Message]],
    var lastTyped: mutable.Map[ChannelId, mutable.Map[UserId, Instant]],
    var users: mutable.Map[UserId, User],
    var bans: mutable.Map[GuildId, mutable.Map[UserId, Ban]]
) extends CacheSnapshotLike {

  override type MapType[K, V] = mutable.Map[SnowflakeType[K], V]

  def toImmutable: CacheSnapshot = {
    def convertNested[K1, K2, V](
        map: mutable.Map[SnowflakeType[K1], mutable.Map[SnowflakeType[K2], V]]
    ): SnowflakeMap[K1, SnowflakeMap[K2, V]] = SnowflakeMap(map.map { case (k, v) => k -> SnowflakeMap(v) })

    CacheSnapshot(
      botUser = botUser,
      dmChannels = SnowflakeMap(dmChannels),
      groupDmChannels = SnowflakeMap(groupDmChannels),
      unavailableGuilds = SnowflakeMap(unavailableGuilds),
      guilds = SnowflakeMap(guilds),
      messages = convertNested(messages),
      lastTyped = convertNested(lastTyped),
      users = SnowflakeMap(users),
      bans = convertNested(bans)
    )
  }

  override def getChannelMessages(channelId: ChannelId): mutable.Map[MessageId, Message] =
    messages.getOrElse(channelId, mutable.Map.empty)

  override def getChannelLastTyped(channelId: ChannelId): mutable.Map[UserId, Instant] =
    lastTyped.getOrElse(channelId, mutable.Map.empty)
}
object CacheSnapshotBuilder {
  def apply(snapshot: CacheSnapshot): CacheSnapshotBuilder = {
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
      dmChannels = toMutableMap(snapshot.dmChannels),
      groupDmChannels = toMutableMap(snapshot.groupDmChannels),
      unavailableGuilds = toMutableMap(snapshot.unavailableGuilds),
      guilds = toMutableMap(snapshot.guilds),
      messages = toMutableMapNested(snapshot.messages),
      lastTyped = toMutableMapNested(snapshot.lastTyped),
      users = toMutableMap(snapshot.users),
      bans = toMutableMapNested(snapshot.bans)
    )
  }
}
