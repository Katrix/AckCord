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

import akka.event.LoggingAdapter
import net.katsstuff.ackcord.CacheSnapshotLike.BotUser
import net.katsstuff.ackcord.data.{ChannelType, DMChannel, GroupDMChannel}
import net.katsstuff.ackcord.http.websocket.gateway.GatewayEvent.ReadyData
import shapeless._

//We handle this one separately as is it's kind of special
object ReadyHandler extends CacheHandler[ReadyData] {
  override def handle(builder: CacheSnapshotBuilder, obj: ReadyData)(implicit log: LoggingAdapter): Unit = {
    val ReadyData(_, botUser, rawChannels, unavailableGuilds, _, _) = obj

    val (dmChannels, users1) = rawChannels
      .collect {
        case rawChannel if rawChannel.`type` == ChannelType.DM =>
          val optUser = rawChannel.recipients.flatMap(_.headOption)
          optUser.map { user =>
            (rawChannel.id -> DMChannel(rawChannel.id, rawChannel.lastMessageId, user.id), user.id -> user)
          }
      }
      .flatten
      .unzip

    val (groupDmChannels, users2) = rawChannels
      .collect {
        case rawChannel if rawChannel.`type` == ChannelType.GroupDm =>
          val users   = rawChannel.recipients.toSeq.flatMap(_.map(user => user.id -> user))
          val userIds = users.map(_._1)

          for {
            name    <- rawChannel.name
            ownerId <- rawChannel.ownerId
            if userIds.nonEmpty
          } yield
            GroupDMChannel(
              rawChannel.id,
              name,
              userIds,
              rawChannel.lastMessageId,
              ownerId,
              rawChannel.applicationId,
              rawChannel.icon
            ) -> users
      }
      .flatten
      .unzip

    val guilds = unavailableGuilds.map(g => g.id -> g)

    builder.botUser = tag[BotUser](botUser)
    builder.dmChannels ++= dmChannels.toMap
    builder.unavailableGuilds ++= guilds
    builder.users ++= users1
    builder.users ++= users2.flatten
  }
}
