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
    seq: Long,
    var botUser: User @@ BotUser,
    private[this] var lastDmChannelMap: SnowflakeMap[DMChannel, DMChannel],
    private[this] var lastGroupDmChannelMap: SnowflakeMap[GroupDMChannel, GroupDMChannel],
    private[this] var lastUnavailableGuildMap: SnowflakeMap[Guild, UnavailableGuild],
    private[this] var lastGuildMap: SnowflakeMap[Guild, Guild],
    private[this] var lastMessageMap: SnowflakeMap[TextChannel, SnowflakeMap[Message, Message]],
    private[this] var lastLastTypedMap: SnowflakeMap[TextChannel, SnowflakeMap[User, Instant]],
    private[this] var lastUserMap: SnowflakeMap[User, User],
    private[this] var lastBanMap: SnowflakeMap[Guild, SnowflakeMap[User, Ban]],
    var processor: MemoryCacheSnapshot.CacheProcessor
) extends CacheSnapshotWithMaps {

  override type MapType[K, V] = mutable.Map[SnowflakeType[K], V]

  private def toMutableMap[K, V](map: SnowflakeMap[K, V]): mutable.Map[SnowflakeType[K], V] = {
    val builder = mutable.Map.newBuilder[SnowflakeType[K], V]
    builder.sizeHint(map)
    builder ++= map
    builder.result()
  }

  private def toMutableMapNested[K1, K2, V](map: SnowflakeMap[K1, SnowflakeMap[K2, V]]) =
    toMutableMap(map.map { case (k, v) => k -> toMutableMap(v) })

  private[this] var dmChannelMapWrite                                               = false
  private[this] var _dmChannelMap: mutable.Map[SnowflakeType[DMChannel], DMChannel] = toMutableMap(lastDmChannelMap)
  def dmChannelMap: mutable.Map[SnowflakeType[DMChannel], DMChannel] = {
    dmChannelMapWrite = true
    _dmChannelMap
  }
  def dmChannelMap_=(map: mutable.Map[SnowflakeType[DMChannel], DMChannel]): Unit = {
    dmChannelMapWrite = true
    _dmChannelMap = map
  }

  private[this] var groupDmChannelMapWrite = false
  private[this] var _groupDmChannelMap: mutable.Map[SnowflakeType[GroupDMChannel], GroupDMChannel] = toMutableMap(
    lastGroupDmChannelMap
  )
  def groupDmChannelMap: mutable.Map[SnowflakeType[GroupDMChannel], GroupDMChannel] = {
    groupDmChannelMapWrite = true
    _groupDmChannelMap
  }
  def groupDmChannelMap_=(map: mutable.Map[SnowflakeType[GroupDMChannel], GroupDMChannel]): Unit = {
    groupDmChannelMapWrite = true
    _groupDmChannelMap = map
  }

  private[this] var unavailableGuildMapWrite                                     = false
  private[this] var _unavailableGuildMap: mutable.Map[GuildId, UnavailableGuild] = toMutableMap(lastUnavailableGuildMap)
  def unavailableGuildMap: mutable.Map[GuildId, UnavailableGuild] = {
    unavailableGuildMapWrite = true
    _unavailableGuildMap
  }
  def unavailableGuildMap_=(map: mutable.Map[GuildId, UnavailableGuild]): Unit = {
    unavailableGuildMapWrite = true
    _unavailableGuildMap = map
  }

  private[this] var guildMapWrite                          = false
  private[this] var _guildMap: mutable.Map[GuildId, Guild] = toMutableMap(lastGuildMap)
  def guildMap: mutable.Map[GuildId, Guild] = {
    guildMapWrite = true
    _guildMap
  }
  def guildMap_=(map: mutable.Map[GuildId, Guild]): Unit = {
    guildMapWrite = true
    _guildMap = map
  }

  private[this] var messageMapWrite = false
  private[this] var _messageMap: mutable.Map[TextChannelId, mutable.Map[MessageId, Message]] = toMutableMapNested(
    lastMessageMap
  )
  def messageMap: mutable.Map[TextChannelId, mutable.Map[MessageId, Message]] = {
    messageMapWrite = true
    _messageMap
  }
  def messageMap_=(map: mutable.Map[TextChannelId, mutable.Map[MessageId, Message]]): Unit = {
    messageMapWrite = true
    _messageMap = map
  }

  private[this] var lastTypedMapWrite = false
  private[this] var _lastTypedMap: mutable.Map[TextChannelId, mutable.Map[UserId, Instant]] = toMutableMapNested(
    lastLastTypedMap
  )
  def lastTypedMap: mutable.Map[TextChannelId, mutable.Map[UserId, Instant]] = {
    lastTypedMapWrite = true
    _lastTypedMap

  }
  def lastTypedMap_=(map: mutable.Map[TextChannelId, mutable.Map[UserId, Instant]]): Unit = {
    lastTypedMapWrite = true
    _lastTypedMap = map
  }

  private[this] var userMapWrite                        = false
  private[this] var _userMap: mutable.Map[UserId, User] = toMutableMap(lastUserMap)
  def userMap: mutable.Map[UserId, User] = {
    userMapWrite = true
    _userMap
  }

  def userMap_=(map: mutable.Map[UserId, User]): Unit = {
    userMapWrite = true
    _userMap = map
  }

  private[this] var banMapWrite                                             = false
  private[this] var _banMap: mutable.Map[GuildId, mutable.Map[UserId, Ban]] = toMutableMapNested(lastBanMap)
  def banMap: mutable.Map[GuildId, mutable.Map[UserId, Ban]] = {
    banMapWrite = true
    _banMap
  }
  def banMap_=(map: mutable.Map[GuildId, mutable.Map[UserId, Ban]]): Unit = {
    banMapWrite = true
    _banMap = map
  }

  def executeProcessor(): Unit =
    this.processor = processor(processor, this)

  def toImmutable: MemoryCacheSnapshot = {
    def convertNested[K1, K2, V](
        map: mutable.Map[SnowflakeType[K1], mutable.Map[SnowflakeType[K2], V]]
    ): SnowflakeMap[K1, SnowflakeMap[K2, V]] = SnowflakeMap.from(map.map { case (k, v) => k -> SnowflakeMap.from(v) })

    if (dmChannelMapWrite) {
      lastDmChannelMap = SnowflakeMap.from(_dmChannelMap)
      dmChannelMapWrite = false
    }

    if (groupDmChannelMapWrite) {
      lastGroupDmChannelMap = SnowflakeMap.from(_groupDmChannelMap)
      groupDmChannelMapWrite = false
    }

    if (unavailableGuildMapWrite) {
      lastUnavailableGuildMap = SnowflakeMap.from(_unavailableGuildMap)
      unavailableGuildMapWrite = false
    }

    if (guildMapWrite) {
      lastGuildMap = SnowflakeMap.from(_guildMap)
      guildMapWrite = false
    }

    if (messageMapWrite) {
      lastMessageMap = convertNested(_messageMap)
      messageMapWrite = false
    }

    if (lastTypedMapWrite) {
      lastLastTypedMap = convertNested(_lastTypedMap)
      lastTypedMapWrite = false
    }

    if (userMapWrite) {
      lastUserMap = SnowflakeMap.from(_userMap)
      userMapWrite = false
    }

    if (banMapWrite) {
      lastBanMap = convertNested(_banMap)
      banMapWrite = false
    }

    MemoryCacheSnapshot(
      seq = seq + 1,
      botUser = botUser,
      dmChannelMap = lastDmChannelMap,
      groupDmChannelMap = lastGroupDmChannelMap,
      unavailableGuildMap = lastUnavailableGuildMap,
      guildMap = lastGuildMap,
      messageMap = lastMessageMap,
      lastTypedMap = lastLastTypedMap,
      userMap = lastUserMap,
      banMap = lastBanMap,
      processor = processor
    )
  }
  override def getChannelMessages(channelId: TextChannelId): mutable.Map[SnowflakeType[Message], Message] =
    messageMap.getOrElseUpdate(channelId, mutable.Map.empty)

  override def getChannelLastTyped(channelId: TextChannelId): mutable.Map[SnowflakeType[User], Instant] =
    lastTypedMap.getOrElseUpdate(channelId, mutable.Map.empty)

  override def getGuildBans(id: GuildId): mutable.Map[SnowflakeType[User], Ban] =
    banMap.getOrElseUpdate(id, mutable.Map.empty)

  def copy: CacheSnapshotBuilder = CacheSnapshotBuilder(toImmutable)

}
object CacheSnapshotBuilder {
  def apply(snapshot: MemoryCacheSnapshot): CacheSnapshotBuilder = {

    new CacheSnapshotBuilder(
      seq = snapshot.seq,
      botUser = snapshot.botUser,
      lastDmChannelMap = snapshot.dmChannelMap,
      lastGroupDmChannelMap = snapshot.groupDmChannelMap,
      lastUnavailableGuildMap = snapshot.unavailableGuildMap,
      lastGuildMap = snapshot.guildMap,
      lastMessageMap = snapshot.messageMap,
      lastLastTypedMap = snapshot.lastTypedMap,
      lastUserMap = snapshot.userMap,
      lastBanMap = snapshot.banMap,
      processor = snapshot.processor
    )
  }
}
