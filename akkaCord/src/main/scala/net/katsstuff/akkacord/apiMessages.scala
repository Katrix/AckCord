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
package net.katsstuff.akkacord

import java.time.{Instant, LocalDateTime}

import net.katsstuff.akkacord.data._

sealed trait APIMessage {
  def cache: CacheSnapshot
}
object APIMessage {
  implicit def getCache(message: APIMessage): CacheSnapshot = message.cache

  case class Ready(cache: CacheSnapshot) extends APIMessage
  case class Resumed(cache: CacheSnapshot) extends APIMessage

  case class ChannelCreate(channel: Channel, cache:      CacheSnapshot) extends APIMessage
  case class ChannelUpdate(channel: GuildChannel, cache: CacheSnapshot) extends APIMessage
  case class ChannelDelete(channel: Channel, cache:      CacheSnapshot) extends APIMessage

  case class GuildCreate(guild: AvailableGuild, cache:                                CacheSnapshot) extends APIMessage
  case class GuildUpdate(guild: AvailableGuild, cache:                                CacheSnapshot) extends APIMessage
  case class GuildDelete(guild: AvailableGuild, unavailable: Boolean, cache: CacheSnapshot) extends APIMessage

  case class GuildBanAdd(guild:    AvailableGuild, user: User, cache: CacheSnapshot) extends APIMessage
  case class GuildBanRemove(guild: AvailableGuild, user: User, cache: CacheSnapshot) extends APIMessage

  case class GuildEmojiUpdate(guild:        AvailableGuild, emojis: Seq[GuildEmoji], cache: CacheSnapshot) extends APIMessage
  case class GuildIntegrationsUpdate(guild: AvailableGuild, cache:  CacheSnapshot) extends APIMessage

  case class GuildMemberAdd(member:   GuildMember, guild: AvailableGuild, cache:            CacheSnapshot) extends APIMessage
  case class GuildMemberRemove(user:  User, guild:        AvailableGuild, cache:            CacheSnapshot) extends APIMessage
  case class GuildMemberUpdate(guild: AvailableGuild, roles:       Seq[Role], user:         User, nick: String, cache: CacheSnapshot) extends APIMessage
  case class GuildMembersChunk(guild: AvailableGuild, members:     Seq[GuildMember], cache: CacheSnapshot) extends APIMessage

  case class GuildRoleCreate(guild: AvailableGuild, role:   Role, cache:      CacheSnapshot) extends APIMessage
  case class GuildRoleUpdate(guild: AvailableGuild, role:   Role, cache:      CacheSnapshot) extends APIMessage
  case class GuildRoleDelete(guild: AvailableGuild, roleId: Snowflake, cache: CacheSnapshot) extends APIMessage

  case class MessageCreate(message:      Message, cache:                         CacheSnapshot) extends APIMessage
  case class MessageUpdate(message:      Message, cache:                         CacheSnapshot) extends APIMessage
  case class MessageDelete(id:           Message, channel:      Channel, cache: CacheSnapshot) extends APIMessage
  case class MessageDeleteBulk(messages: Seq[Message], channel: Channel, cache: CacheSnapshot) extends APIMessage

  //case class PresenceUpdate(presence: Presence, cache: CacheSnapshot) //TODO

  case class TypingStart(channel: Channel, user: User, timestamp: Instant, cache: CacheSnapshot) extends APIMessage

  //case class UserSettingsUpdate(user: UserSettings)
  case class UserUpdate(user: User, cache: CacheSnapshot) extends APIMessage

  case class VoiceStateUpdate(voiceState: VoiceState, cache: CacheSnapshot) extends APIMessage
  case class VoiceServerUpdate(token:     String, guild:           AvailableGuild, endpoint: String, cache: CacheSnapshot) extends APIMessage

}