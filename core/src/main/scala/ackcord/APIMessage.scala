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
package ackcord

import java.time.{Instant, OffsetDateTime}

import ackcord.data._

/**
  * Base trait normal messages.
  */
sealed trait APIMessage {

  /**
    * The current state of the cache. Contains both a snapshot of how the
    * cache looks like after this message (the current one), and a snapshot for
    * how the cache looked like before this message.
    */
  def cache: CacheState
}
object APIMessage {

  /**
    * Sent to the client when Discord is ready to serve requests. No requests
    * should be sent before this has been received.
    */
  case class Ready(cache: CacheState) extends APIMessage

  /**
    * Sent to the client when a previously interrupted connection is resumed.
    */
  case class Resumed(cache: CacheState) extends APIMessage

  /**
    * Trait that covers all channel messages
    */
  sealed trait ChannelMessage extends APIMessage {

    /**
      * The channel that was acted upon.
      */
    def channel: Channel
  }

  /**
    * Sent to the client when a new channel is created.
    * @param channel The channel that was created.
    */
  case class ChannelCreate(channel: GuildChannel, cache: CacheState) extends ChannelMessage

  /**
    * Sent to the client when a channel is edited or updated.
    * @param channel The channel that was edited. This will always be a
    *                guild channel
    */
  case class ChannelUpdate(channel: GuildChannel, cache: CacheState) extends ChannelMessage

  /**
    * Sent to the client when a channel is deleted. The current snapshot will
    * not contain the channel.
    * @param channel The channel that was deleted.
    */
  case class ChannelDelete(channel: GuildChannel, cache: CacheState) extends ChannelMessage

  /**
    * Sent to the client when a message is pinned or unpinned in a text
    * channel. This is not sent when a pinned message is deleted.
    * @param channel The channel where the change happened
    * @param mostRecent The time the most recent pinned message was pinned
    */
  case class ChannelPinsUpdate(channel: TextChannel, mostRecent: Option[OffsetDateTime], cache: CacheState)
      extends ChannelMessage

  /**
    * Trait that covers all guild messages.
    */
  sealed trait GuildMessage extends APIMessage {

    /**
      * The guild that was acted upon.
      */
    def guild: Guild
  }

  /**
    * Sent to the client after the client connects to the gateway, when a
    * previously unavailable guild becomes available, and when the client
    * joins a new guild.
    * @param guild The created guild object
    */
  case class GuildCreate(guild: Guild, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when the guild object is updated.
    * @param guild The updated guild.
    */
  case class GuildUpdate(guild: Guild, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client either if a guild becomes unavailable due to and
    * outage, or if the client leaves or is kicked from a guild.
    * @param guild The deleted guild
    * @param unavailable If an outage caused this event
    */
  case class GuildDelete(guild: Guild, unavailable: Boolean, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when an user is banned from a guild. If you need the
    * [[ackcord.data.GuildMember]] object of the user, you can find it in [[cache.previous]].
    * @param guild The guild the user was banned from.
    * @param user The banned user.
    */
  case class GuildBanAdd(guild: Guild, user: User, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when an user is unbanned from a guild.
    * @param guild The guild where the user was previously banned.
    * @param user The previously banned user.
    */
  case class GuildBanRemove(guild: Guild, user: User, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when the emojis of a guild have been updated. If you
    * need the old emojis, you can find them in [[cache.previous]].
    * @param guild The guild where the update occurred.
    * @param emojis The new emojis.
    */
  case class GuildEmojiUpdate(guild: Guild, emojis: Seq[Emoji], cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when the integrations of a guild were updated. You
    * have to fetch the integrations yourself.
    * @param guild The guild where the update occurred.
    */
  case class GuildIntegrationsUpdate(guild: Guild, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when a user joins the guild.
    * @param member The new member
    * @param guild The joined guild
    */
  case class GuildMemberAdd(member: GuildMember, guild: Guild, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when a user leaves the guild (or is kicked or banned).
    * If you need the [[ackcord.data.GuildMember]], you can find it in [[cache.previous]].
    * @param user The user that left
    * @param guild The guild the user left
    */
  case class GuildMemberRemove(user: User, guild: Guild, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when a guild member is updated. The fields seen here
    * are all the fields that can change. Looking at the users [[ackcord.data.GuildMember]]
    * for changes is pointless.
    * @param guild The guild of the guild member
    * @param roles Thew new roles for the guild member
    * @param user The user of the updated guild member
    * @param nick Nick of the user if one was set
    */
  case class GuildMemberUpdate(
      guild: Guild,
      roles: Seq[Role],
      user: User,
      nick: Option[String],
      premiumSince: Option[OffsetDateTime],
      cache: CacheState
  ) extends GuildMessage

  /**
    * Sent to the client if the client requests to get all members
    * (even offline ones) for large guilds using [[ackcord.gateway.RequestGuildMembers]].
    * @param guild The guild requested for.
    * @param members The guild members in this chunk.
    */
  case class GuildMembersChunk(guild: Guild, members: Seq[GuildMember], cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when a new role is created.
    * @param guild The guild of the new role
    * @param role The new role
    */
  case class GuildRoleCreate(guild: Guild, role: Role, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when a role is updated.
    * @param guild The guild of the updated role
    * @param role The updated role
    */
  case class GuildRoleUpdate(guild: Guild, role: Role, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when a role is deleted
    * @param guild The guild of the deleted role
    * @param roleId The deleted role.
    */
  case class GuildRoleDelete(guild: Guild, roleId: Role, cache: CacheState) extends GuildMessage

  /**
    * Sent when an invite is created.
    * @param guild The guild the invite is for.
    * @param channel The channel the invite directs to.
    */
  case class InviteCreate(guild: Option[Guild], channel: TextChannel, invite: CreatedInvite, cache: CacheState)
      extends ChannelMessage

  /**
    * Sent when an invite is deleted.
    * @param guild The guild the invite was for.
    * @param channel The channel the invite directed to.
    */
  case class InviteDelete(guild: Option[Guild], channel: TextChannel, code: String, cache: CacheState)
      extends ChannelMessage

  /**
    * Trait that covers all message messages.
    */
  sealed trait MessageMessage extends APIMessage {

    /**
      * The message that was acted upon.
      */
    def message: Message
  }

  /**
    * Sent to the client when a message is created (posted).
    * @param message The sent message
    */
  case class MessageCreate(message: Message, cache: CacheState) extends MessageMessage

  /**
    * Sent to the client when a message is updated.
    * @param message The new message. The check changes, the old message can
    *                be found in [[cache.previous]].
    */
  case class MessageUpdate(message: Message, cache: CacheState) extends MessageMessage

  /**
    * Sent to the client when a message is deleted.
    * @param message The deleted message.
    * @param channel The channel of the message.
    */
  case class MessageDelete(message: Message, channel: TextChannel, cache: CacheState)
      extends MessageMessage
      with ChannelMessage

  /**
    * Sent to the client when multiple messages are deleted at the same time.
    * Often this is performed by a bot.
    * @param messages The deleted messages
    * @param channel The channel of the deleted messages
    */
  case class MessageDeleteBulk(messages: Seq[Message], channel: TextChannel, cache: CacheState) extends ChannelMessage

  /**
    * Sent to the client when a user adds a reaction to a message.
    * @param user The user that added the reaction.
    * @param channel The channel of the message.
    * @param message The message the user added an reaction to.
    * @param emoji The emoji the user reacted with
    */
  case class MessageReactionAdd(
      user: User,
      channel: TextChannel,
      message: Message,
      emoji: PartialEmoji,
      cache: CacheState
  ) extends MessageMessage
      with ChannelMessage

  /**
    * Sent to the client when a user removes a reaction from a message.
    * @param user The user that removed the reaction.
    * @param channel The channel of the message.
    * @param message The message the user removed an reaction from.
    * @param emoji The emoji the user reacted with
    */
  case class MessageReactionRemove(
      user: User,
      channel: TextChannel,
      message: Message,
      emoji: PartialEmoji,
      cache: CacheState
  ) extends MessageMessage
      with ChannelMessage

  /**
    * Sent to the client when a user removes all reactions from a message.
    * The emojis of the message can be found in [[cache.previous]].
    * @param guild The guild of the message.
    * @param channel The channel of the message.
    * @param message The message the user removed the reactions from.
    */
  case class MessageReactionRemoveAll(guild: Option[Guild], channel: TextChannel, message: Message, cache: CacheState)
      extends MessageMessage
      with ChannelMessage

  /**
    * Sent to the client when a user removes all reactions of a specific emoji
    * from a message.
    * @param guild The guild of the message.
    * @param channel The channel of the message.
    * @param message The message the user removed the reactions from.
    * @param emoji The removed emoji.
    */
  case class MessageReactionRemoveEmoji(
      guild: Option[Guild],
      channel: TextChannel,
      message: Message,
      emoji: PartialEmoji,
      cache: CacheState
  ) extends MessageMessage
      with ChannelMessage

  /**
    * Sent to the client when the presence of a user updates.
    * @param guild The guild where the update took place
    * @param user The user of the presence
    * @param roleIds The roles of the user
    * @param presence The new presence
    * @param nick The nickname of the user if they have one.
    * @param premiumSince When the user boosted the guild.
    */
  case class PresenceUpdate(
      guild: Guild,
      user: User,
      roleIds: Seq[RoleId],
      presence: Presence,
      nick: Option[String],
      premiumSince: Option[OffsetDateTime],
      cache: CacheState
  ) extends GuildMessage

  /**
    * Sent to the client when a user starts typing in a channel
    * @param channel The channel where the typing happened
    * @param user The user that began typing
    * @param timestamp When user started typing
    */
  case class TypingStart(channel: TextChannel, user: User, timestamp: Instant, cache: CacheState) extends ChannelMessage

  /**
    * Sent to the client when a user object is updated.
    * @param user The new user.
    */
  case class UserUpdate(user: User, cache: CacheState) extends APIMessage

  /**
    * Sent to the client when a user joins/leaves/moves voice channels
    * @param voiceState New voice states
    */
  case class VoiceStateUpdate(voiceState: VoiceState, cache: CacheState) extends APIMessage

  /**
    * Sent a guilds voice server is updated. Also used when connecting to a voice channel.
    * @param token The voice connection token
    * @param guild The guild of the update
    * @param endpoint The voice server
    */
  case class VoiceServerUpdate(token: String, guild: Guild, endpoint: String, cache: CacheState) extends GuildMessage

  /**
    * Sent to the client when guilds webhooks are updated.
    * @param guild The guild of the updated webhook
    * @param channel The channel for the webhook
    */
  case class WebhookUpdate(guild: Guild, channel: GuildChannel, cache: CacheState)
      extends GuildMessage
      with ChannelMessage

}
