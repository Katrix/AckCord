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

import ackcord.CacheSnapshot.BotUser
import ackcord.data._
import ackcord.{CacheSnapshotWithMaps, MemoryCacheSnapshot, SnowflakeMap}
import shapeless.tag._

/** A mutable builder for creating a new snapshot */
class CacheSnapshotBuilder(
    seq: Long,
    var botUser: User @@ BotUser,
    var dmChannelMap: SnowflakeMap[DMChannel, DMChannel],
    var groupDmChannelMap: SnowflakeMap[GroupDMChannel, GroupDMChannel],
    var unavailableGuildMap: SnowflakeMap[Guild, UnavailableGuild],
    var guildMap: SnowflakeMap[Guild, GatewayGuild],
    var messageMap: SnowflakeMap[TextChannel, SnowflakeMap[Message, Message]],
    var lastTypedMap: SnowflakeMap[TextChannel, SnowflakeMap[User, Instant]],
    var userMap: SnowflakeMap[User, User],
    var banMap: SnowflakeMap[Guild, SnowflakeMap[User, Ban]],
    var processor: MemoryCacheSnapshot.CacheProcessor
) extends CacheSnapshotWithMaps {

  override type MapType[K, V] = SnowflakeMap[K, V]

  def executeProcessor(): Unit =
    this.processor = processor(processor, this)

  def toImmutable: MemoryCacheSnapshot =
    MemoryCacheSnapshot(
      seq = seq + 1,
      botUser = botUser,
      dmChannelMap = dmChannelMap,
      groupDmChannelMap = groupDmChannelMap,
      unavailableGuildMap = unavailableGuildMap,
      guildMap = guildMap,
      messageMap = messageMap,
      lastTypedMap = lastTypedMap,
      userMap = userMap,
      banMap = banMap,
      processor = processor
    )

  override def getChannelMessages(
      channelId: TextChannelId
  ): SnowflakeMap[Message, Message] =
    messageMap.getOrElse(channelId, SnowflakeMap.empty)

  override def getChannelLastTyped(
      channelId: TextChannelId
  ): SnowflakeMap[User, Instant] =
    lastTypedMap.getOrElse(channelId, SnowflakeMap.empty)

  override def getGuildBans(id: GuildId): SnowflakeMap[User, Ban] =
    banMap.getOrElse(id, SnowflakeMap.empty)

  def copy: CacheSnapshotBuilder = CacheSnapshotBuilder(toImmutable)

}
object CacheSnapshotBuilder {
  def apply(snapshot: MemoryCacheSnapshot): CacheSnapshotBuilder = {

    new CacheSnapshotBuilder(
      seq = snapshot.seq,
      botUser = snapshot.botUser,
      dmChannelMap = snapshot.dmChannelMap,
      groupDmChannelMap = snapshot.groupDmChannelMap,
      unavailableGuildMap = snapshot.unavailableGuildMap,
      guildMap = snapshot.guildMap,
      messageMap = snapshot.messageMap,
      lastTypedMap = snapshot.lastTypedMap,
      userMap = snapshot.userMap,
      banMap = snapshot.banMap,
      processor = snapshot.processor
    )
  }
}
