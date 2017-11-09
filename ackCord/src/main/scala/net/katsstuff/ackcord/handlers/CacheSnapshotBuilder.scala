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
    var presences: mutable.Map[GuildId, mutable.Map[UserId, Presence]],
    var bans: mutable.Map[GuildId, mutable.Map[UserId, Ban]]
) extends CacheSnapshotLike {

  override type MapType[A, B] = mutable.Map[SnowflakeType[A], B]

  def toImmutable: CacheSnapshot = CacheSnapshot(
    botUser = botUser,
    dmChannels = SnowflakeMap(dmChannels.toSeq: _*),
    groupDmChannels = SnowflakeMap(groupDmChannels.toSeq: _*),
    unavailableGuilds = SnowflakeMap(unavailableGuilds.toSeq: _*),
    guilds = SnowflakeMap(guilds.toSeq: _*),
    messages = SnowflakeMap(messages.map { case (k, v)   => k -> SnowflakeMap(v.toSeq: _*) }.toSeq: _*),
    lastTyped = SnowflakeMap(lastTyped.map { case (k, v) => k -> SnowflakeMap(v.toSeq: _*) }.toSeq: _*),
    users = SnowflakeMap(users.toSeq: _*),
    presences = SnowflakeMap(presences.map { case (k, v) => k -> SnowflakeMap(v.toSeq: _*) }.toSeq: _*),
    bans = SnowflakeMap(bans.map { case (k, v)           => k -> SnowflakeMap(v.toSeq: _*) }.toSeq: _*)
  )
  override def getChannelMessages(channelId: ChannelId): mutable.Map[MessageId, Message] =
    messages.getOrElse(channelId, mutable.Map.empty)
  override def getChannelLastTyped(channelId: ChannelId): mutable.Map[UserId, Instant] =
    lastTyped.getOrElse(channelId, mutable.Map.empty)
}
object CacheSnapshotBuilder {
  import scala.collection.breakOut
  def apply(snapshot: CacheSnapshot): CacheSnapshotBuilder = new CacheSnapshotBuilder(
    botUser = snapshot.botUser,
    dmChannels = toMutableMap(snapshot.dmChannels),
    groupDmChannels = toMutableMap(snapshot.groupDmChannels),
    unavailableGuilds = toMutableMap(snapshot.unavailableGuilds),
    guilds = toMutableMap(snapshot.guilds),
    messages = toMutableMap(snapshot.messages.map { case (k, v) => k -> toMutableMap(v) }),
    lastTyped = snapshot.lastTyped.map { case (k, v)            => k -> toMutableMap(v) }(breakOut),
    users = toMutableMap(snapshot.users),
    presences = snapshot.presences.map { case (k, v) => k -> toMutableMap(v) }(breakOut),
    bans = snapshot.bans.map { case (k, v)           => k -> toMutableMap(v) }(breakOut)
  )

  private def toMutableMap[A, B](map: Map[A, B]): mutable.Map[A, B] = {
    val builder = mutable.Map.newBuilder[A, B]
    builder.sizeHint(map)
    builder ++= map
    builder.result()
  }
}
