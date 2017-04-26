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
package net.katsstuff.akkacord.handlers2

import akka.event.LoggingAdapter
import net.katsstuff.akkacord.data.{DMChannel, UnavailableGuild}
import net.katsstuff.akkacord.http.websocket.WsEvent.ReadyData
import net.katsstuff.akkacord.http.{RawDMChannel, RawUnavailableGuild}

//We handle this one seperately is it's kind of special
object ReadyHandler extends CacheHandler[ReadyData] {
  override def handle(builder: CacheSnapshotBuilder, obj: ReadyData)(implicit log: LoggingAdapter): Unit = {
    val ReadyData(_, user, rawPrivateChannels, rawGuilds, _, _) = obj

    val (dmChannels, users) = rawPrivateChannels.map {
      case RawDMChannel(id, _, recipient, lastMessageId) => (id -> DMChannel(id, lastMessageId, recipient.id), recipient.id -> recipient)
    }.unzip

    val guilds = rawGuilds.map {
      case RawUnavailableGuild(id, _) => id -> UnavailableGuild(id)
    }

    builder.botUser = user
    builder.dmChannels ++= dmChannels.toMap
    builder.unavailableGuilds ++= guilds.toMap
    builder.users ++= users.toMap
  }
}
