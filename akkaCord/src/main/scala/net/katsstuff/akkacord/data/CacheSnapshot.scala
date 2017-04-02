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
package net.katsstuff.akkacord.data

import java.time.Instant

//All nested maps should use default maps
case class CacheSnapshot(botUser:           User,
                         dmChannels:        Map[Snowflake, DMChannel],
                         unavailableGuilds: Map[Snowflake, UnavailableGuild],
                         guilds:            Map[Snowflake, AvailableGuild],
                         messages:          Map[Snowflake, Map[Snowflake, Message]],
                         lastTyped:         Map[Snowflake, Map[Snowflake, Instant]],
                         users:             Map[Snowflake, User],
                         presences:         Map[Snowflake, Map[Snowflake, Presence]]) {

  def getDmChannel(id: Snowflake): Option[DMChannel] = dmChannels.get(id)

  def getGuild(id:             Snowflake): Option[AvailableGuild] = guilds.get(id)
  def getGuildWithFallback(id: Snowflake): Option[Guild]          = guilds.get(id).orElse(unavailableGuilds.get(id))

  def getGuildChannel(id: Snowflake): Option[GuildChannel] = guilds.values.collectFirst {
    case guild if guild.channels.contains(id) => guild.channels(id)
  }

  def getChannel(id: Snowflake): Option[Channel] = getDmChannel(id).orElse(getGuildChannel(id))

  def getRole(id:   Snowflake): Option[Role] = guilds.values.collectFirst {
    case guild if guild.roles.contains(id) => guild.roles(id)
  }
  def getEmoji(id:  Snowflake): Option[GuildEmoji] = guilds.values.collectFirst {
    case guild if guild.emojis.contains(id) => guild.emojis(id)
  }
  def getMember(id: Snowflake): Option[GuildMember] = guilds.values.collectFirst {
    case guild if guild.members.contains(id) => guild.members(id)
  }

  def getChannelMessages(channelId: Snowflake): Map[Snowflake, Message] = messages.getOrElse(channelId, Map.empty)
  def getMessage(channelId:         Snowflake, messageId: Snowflake): Option[Message] = messages.get(channelId).flatMap(_.get(messageId))
  def getMessage(messageId:         Snowflake): Option[Message] = messages.values.collectFirst {
    case channelMap if channelMap.contains(messageId) => channelMap(messageId)
  }

  def getChannelLastTyped(channelId: Snowflake): Map[Snowflake, Instant] = lastTyped.getOrElse(channelId, Map.empty)
  def getLastTyped(channelId:        Snowflake, userId: Snowflake): Option[Instant] = lastTyped.get(channelId).flatMap(_.get(userId))

  def getUser(id: Snowflake): Option[User] = users.get(id)

  def getPresence(guildId: Snowflake, userId: Snowflake): Option[Presence] = presences(guildId).get(userId)

}
