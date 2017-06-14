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
package net.katsstuff.akkacord.handlers

import java.time.Instant

import scala.collection.mutable

import net.katsstuff.akkacord.CacheSnapshotLike
import net.katsstuff.akkacord.data.{Guild, CacheSnapshot, DMChannel, Message, Presence, Snowflake, UnavailableGuild, User}

class CacheSnapshotBuilder(
    var botUser:           User,
    var dmChannels:        mutable.Map[Snowflake, DMChannel],
    var unavailableGuilds: mutable.Map[Snowflake, UnavailableGuild],
    var guilds:            mutable.Map[Snowflake, Guild],
    var messages:          mutable.Map[Snowflake, mutable.Map[Snowflake, Message]],
    var lastTyped:         mutable.Map[Snowflake, mutable.Map[Snowflake, Instant]],
    var users:             mutable.Map[Snowflake, User],
    var presences:         mutable.Map[Snowflake, mutable.Map[Snowflake, Presence]]
) extends CacheSnapshotLike {

  override type MapType[A, B] = mutable.Map[A, B]

  def toImmutable: CacheSnapshot = CacheSnapshot(
    botUser = botUser,
    dmChannels = dmChannels.toMap,
    unavailableGuilds = unavailableGuilds.toMap,
    guilds = guilds.toMap,
    messages = messages.map { case (k, v)   => k -> v.toMap }.toMap,
    lastTyped = lastTyped.map { case (k, v) => k -> v.toMap }.toMap,
    users = users.toMap,
    presences = presences.map { case (k, v) => k -> v.toMap }.toMap
  )
  override def getChannelMessages(channelId:  Snowflake): mutable.Map[Snowflake, Message] = messages.getOrElse(channelId, mutable.Map.empty)
  override def getChannelLastTyped(channelId: Snowflake): mutable.Map[Snowflake, Instant] = lastTyped.getOrElse(channelId, mutable.Map.empty)
}
object CacheSnapshotBuilder {
  import scala.collection.breakOut
  def apply(snapshot: CacheSnapshot): CacheSnapshotBuilder = new CacheSnapshotBuilder(
    botUser = snapshot.botUser,
    dmChannels = toMutableMap(snapshot.dmChannels),
    unavailableGuilds = toMutableMap(snapshot.unavailableGuilds),
    guilds = toMutableMap(snapshot.guilds),
    messages = toMutableMap(snapshot.messages.map { case (k, v) => k -> toMutableMap(v) }),
    lastTyped = snapshot.lastTyped.map { case (k, v)            => k -> toMutableMap(v) }(breakOut),
    users = toMutableMap(snapshot.users),
    presences = snapshot.presences.map { case (k, v) => k -> toMutableMap(v) }(breakOut)
  )

  private def toMutableMap[A, B](map: Map[A, B]): mutable.Map[A, B] = {
    val builder = mutable.Map.newBuilder[A, B]
    builder.sizeHint(map)
    builder ++= map
    builder.result()
  }
}
