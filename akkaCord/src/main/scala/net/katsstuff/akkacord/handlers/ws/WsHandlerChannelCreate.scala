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
package ws

import akka.event.LoggingAdapter
import net.katsstuff.akkacord.APIMessage
import net.katsstuff.akkacord.data.{CacheSnapshot, DMChannel, TGuildChannel, VGuildChannel}
import net.katsstuff.akkacord.http.{RawChannel, RawDMChannel, RawGuildChannel}

object WsHandlerChannelCreate extends Handler[RawChannel, APIMessage.ChannelCreate] {
  override def handle(snapshot: CacheSnapshot, rawChannel: RawChannel)(implicit log: LoggingAdapter): AbstractHandlerResult[APIMessage.ChannelCreate] = {
    //The guild id should always be present
    (rawChannel: @unchecked) match {
      case RawGuildChannel(id, Some(guildId), name, channelType, position, _, permissionOverwrites, topic, lastMessageId, bitrate, userLimit) =>
        val channel = channelType match {
          case "text"  => TGuildChannel(id, guildId, name, position, permissionOverwrites, topic, lastMessageId)
          case "voice" => VGuildChannel(id, guildId, name, position, permissionOverwrites, bitrate.get, userLimit.get)
        }

        guildUpdate(guildId, "channel", snapshot) { guild =>
          val newSnapshot = newGuild(snapshot, guildId, guild.copy(channels = guild.channels + ((id, channel))))
          HandlerResult(newSnapshot, APIMessage.ChannelCreate(channel, _, _))
        }
      case RawDMChannel(id, _, recipient, lastMessageId) =>
        val channel = DMChannel(id, lastMessageId, recipient.id)

        val newSnapshot = snapshot.copy(dmChannels = snapshot.dmChannels + ((id, channel)), users = snapshot.users + ((recipient.id, recipient)))
        HandlerResult(newSnapshot, APIMessage.ChannelCreate(channel, _, _))
    }
  }
}
