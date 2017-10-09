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
package net.katsstuff.ackcord

import java.time.Instant

import net.katsstuff.ackcord.data._

sealed trait APIMessage {
  def prevSnapshot: CacheSnapshot
  def snapshot:     CacheSnapshot
}
object APIMessage {
  case class Ready(snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)   extends APIMessage
  case class Resumed(snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) extends APIMessage

  case class ChannelCreate(channel: Channel, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) extends APIMessage
  case class ChannelUpdate(channel: GuildChannel, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage
  case class ChannelDelete(channel: Channel, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) extends APIMessage

  case class ChannelPinsUpdate(channel: Channel, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage

  case class GuildCreate(guild: Guild, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) extends APIMessage
  case class GuildUpdate(guild: Guild, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) extends APIMessage
  case class GuildDelete(guild: Guild, unavailable: Boolean, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage

  case class GuildBanAdd(guild: Guild, user: User, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage
  case class GuildBanRemove(guild: Guild, user: User, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage

  case class GuildEmojiUpdate(
      guild: Guild,
      emojis: Seq[GuildEmoji],
      snapshot: CacheSnapshot,
      prevSnapshot: CacheSnapshot
  ) extends APIMessage
  case class GuildIntegrationsUpdate(guild: Guild, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage

  case class GuildMemberAdd(member: GuildMember, guild: Guild, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage
  case class GuildMemberRemove(user: User, guild: Guild, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage
  case class GuildMemberUpdate(
      guild: Guild,
      roles: Seq[Role],
      user: User,
      nick: Option[String],
      snapshot: CacheSnapshot,
      prevSnapshot: CacheSnapshot
  ) extends APIMessage
  case class GuildMembersChunk(
      guild: Guild,
      members: Seq[GuildMember],
      snapshot: CacheSnapshot,
      prevSnapshot: CacheSnapshot
  ) extends APIMessage

  case class GuildRoleCreate(guild: Guild, role: Role, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage
  case class GuildRoleUpdate(guild: Guild, role: Role, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage
  case class GuildRoleDelete(guild: Guild, roleId: RoleId, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage

  case class MessageCreate(message: Message, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) extends APIMessage
  case class MessageUpdate(message: Message, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) extends APIMessage
  case class MessageDelete(message: Message, channel: TChannel, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage
  case class MessageDeleteBulk(
      messages: Seq[Message],
      channel: TChannel,
      snapshot: CacheSnapshot,
      prevSnapshot: CacheSnapshot
  ) extends APIMessage

  case class MessageReactionAdd(
      user: User,
      channel: TChannel,
      message: Message,
      emoji: MessageEmoji,
      snapshot: CacheSnapshot,
      prevSnapshot: CacheSnapshot
  ) extends APIMessage
  case class MessageReactionRemove(
      user: User,
      channel: TChannel,
      message: Message,
      emoji: MessageEmoji,
      snapshot: CacheSnapshot,
      prevSnapshot: CacheSnapshot
  ) extends APIMessage
  case class MessageReactionRemoveAll(
      channel: TChannel,
      message: Message,
      snapshot: CacheSnapshot,
      prevSnapshot: CacheSnapshot
  ) extends APIMessage

  case class PresenceUpdate(presence: Presence, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) extends APIMessage

  case class TypingStart(
      channel: TChannel,
      user: User,
      timestamp: Instant,
      snapshot: CacheSnapshot,
      prevSnapshot: CacheSnapshot
  ) extends APIMessage

  case class UserUpdate(user: User, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) extends APIMessage

  case class VoiceStateUpdate(voiceState: VoiceState, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage
  case class VoiceServerUpdate(
      token: String,
      guild: Guild,
      endpoint: String,
      snapshot: CacheSnapshot,
      prevSnapshot: CacheSnapshot
  ) extends APIMessage

  case class WebhookUpdate(guild: Guild, channel: GuildChannel, snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot)
      extends APIMessage

}
