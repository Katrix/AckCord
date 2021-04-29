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
import ackcord.gateway.GatewayInfo

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

  /**
    * Info about the state of the gateway that received this gateway.
    */
  def gatewayInfo: GatewayInfo
}
object APIMessage {

  /**
    * Sent to the client when Discord is ready to serve requests. No requests
    * should be sent before this has been received.
    */
  case class Ready(applicationId: RawSnowflake, cache: CacheState, gatewayInfo: GatewayInfo) extends APIMessage

  /**
    * Sent to the client when a previously interrupted connection is resumed.
    */
  case class Resumed(cache: CacheState, gatewayInfo: GatewayInfo) extends APIMessage

  /** A trait that covers all messages that might have an guild associated with them */
  sealed trait OptGuildMessage extends APIMessage {

    def guild: Option[Guild]
  }

  /** Trait that covers all channel messages */
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
  case class ChannelCreate(guild: Option[Guild], channel: GuildChannel, cache: CacheState, gatewayInfo: GatewayInfo)
      extends OptGuildMessage
      with ChannelMessage

  /**
    * Sent to the client when a channel is edited or updated.
    * @param channel The channel that was edited.
    */
  case class ChannelUpdate(guild: Option[Guild], channel: Channel, cache: CacheState, gatewayInfo: GatewayInfo)
      extends OptGuildMessage
      with ChannelMessage

  /**
    * Sent to the client when a channel is deleted. The current snapshot will
    * not contain the channel.
    * @param channel The channel that was deleted.
    */
  case class ChannelDelete(guild: Option[Guild], channel: GuildChannel, cache: CacheState, gatewayInfo: GatewayInfo)
      extends OptGuildMessage
      with ChannelMessage

  /** Trait that covers all channel id messages */
  sealed trait TextChannelIdMessage extends OptGuildMessage {

    def channelId: TextChannelId

    def channel: Option[TextChannel] =
      guild.fold(cache.current.getTextChannel(channelId))(
        _.channels.get(channelId.asChannelId[TextGuildChannel]).collect {
          case ch: TextGuildChannel => ch
        }
      )
  }

  /**
    * Sent to the client when a message is pinned or unpinned in a text
    * channel where guild information is not available. This is not sent when
    * a pinned message is deleted.
    * @param guild The guild where the change happened
    * @param channelId The channel where the change happened
    * @param mostRecent The time the most recent pinned message was pinned
    */
  case class ChannelPinsUpdate(
      guild: Option[Guild],
      channelId: TextChannelId,
      mostRecent: Option[OffsetDateTime],
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends TextChannelIdMessage

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
  case class GuildCreate(guild: Guild, cache: CacheState, gatewayInfo: GatewayInfo) extends GuildMessage

  /**
    * Sent to the client when the guild object is updated.
    * @param guild The updated guild.
    */
  case class GuildUpdate(guild: Guild, cache: CacheState, gatewayInfo: GatewayInfo) extends GuildMessage

  /**
    * Sent to the client either if a guild becomes unavailable due to and
    * outage, or if the client leaves or is kicked from a guild.
    * @param guild The deleted guild
    * @param unavailable If an outage caused this event
    */
  case class GuildDelete(guild: Guild, unavailable: Option[Boolean], cache: CacheState, gatewayInfo: GatewayInfo)
      extends GuildMessage

  /**
    * Sent to the client when an user is banned from a guild. If you need the
    * [[ackcord.data.GuildMember]] object of the user, you can find it in [[cache.previous]].
    * @param guild The guild the user was banned from.
    * @param user The banned user.
    */
  case class GuildBanAdd(guild: Guild, user: User, cache: CacheState, gatewayInfo: GatewayInfo) extends GuildMessage

  /**
    * Sent to the client when an user is unbanned from a guild.
    * @param guild The guild where the user was previously banned.
    * @param user The previously banned user.
    */
  case class GuildBanRemove(guild: Guild, user: User, cache: CacheState, gatewayInfo: GatewayInfo) extends GuildMessage

  /**
    * Sent to the client when the emojis of a guild have been updated. If you
    * need the old emojis, you can find them in [[cache.previous]].
    * @param guild The guild where the update occurred.
    * @param emojis The new emojis.
    */
  case class GuildEmojiUpdate(guild: Guild, emojis: Seq[Emoji], cache: CacheState, gatewayInfo: GatewayInfo)
      extends GuildMessage

  /**
    * Sent to the client when the integrations of a guild were updated. You
    * have to fetch the integrations yourself.
    * @param guild The guild where the update occurred.
    */
  case class GuildIntegrationsUpdate(guild: Guild, cache: CacheState, gatewayInfo: GatewayInfo) extends GuildMessage

  /**
    * Sent to the client when a user joins the guild.
    * @param member The new member
    * @param guild The joined guild
    */
  case class GuildMemberAdd(member: GuildMember, guild: Guild, cache: CacheState, gatewayInfo: GatewayInfo)
      extends GuildMessage

  /**
    * Sent to the client when a user leaves the guild (or is kicked or banned).
    * If you need the [[ackcord.data.GuildMember]], you can find it in [[cache.previous]].
    * @param user The user that left
    * @param guild The guild the user left
    */
  case class GuildMemberRemove(user: User, guild: Guild, cache: CacheState, gatewayInfo: GatewayInfo)
      extends GuildMessage

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
      joinedAt: OffsetDateTime,
      premiumSince: Option[OffsetDateTime],
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends GuildMessage

  /**
    * Sent to the client if the client requests to get all members
    * (even offline ones) for large guilds using [[ackcord.gateway.RequestGuildMembers]].
    * @param guild The guild requested for.
    * @param members The guild members in this chunk.
    */
  case class GuildMembersChunk(
      guild: Guild,
      members: Seq[GuildMember],
      chunkIndex: Int,
      chunkCount: Int,
      notFound: Option[Seq[UserId]],
      nonce: Option[String],
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends GuildMessage

  /**
    * Sent to the client when a new role is created.
    * @param guild The guild of the new role
    * @param role The new role
    */
  case class GuildRoleCreate(guild: Guild, role: Role, cache: CacheState, gatewayInfo: GatewayInfo) extends GuildMessage

  /**
    * Sent to the client when a role is updated.
    * @param guild The guild of the updated role
    * @param role The updated role
    */
  case class GuildRoleUpdate(guild: Guild, role: Role, cache: CacheState, gatewayInfo: GatewayInfo) extends GuildMessage

  /**
    * Sent to the client when a role is deleted
    * @param guild The guild of the deleted role
    * @param roleId The deleted role.
    */
  case class GuildRoleDelete(guild: Guild, roleId: Role, cache: CacheState, gatewayInfo: GatewayInfo)
      extends GuildMessage

  /**
    * Sent when an invite is created.
    * @param guild The guild the invite is for.
    * @param channel The channel the invite directs to.
    */
  case class InviteCreate(
      guild: Option[Guild],
      channel: TextChannel,
      invite: CreatedInvite,
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends OptGuildMessage
      with ChannelMessage

  /**
    * Sent when an invite is deleted.
    * @param guild The guild the invite was for.
    * @param channel The channel the invite directed to.
    */
  case class InviteDelete(
      guild: Option[Guild],
      channel: TextChannel,
      code: String,
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends OptGuildMessage
      with ChannelMessage

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
  case class MessageCreate(guild: Option[Guild], message: Message, cache: CacheState, gatewayInfo: GatewayInfo)
      extends OptGuildMessage
      with MessageMessage

  /**
    * Sent to the client when a message is updated.
    * @param messageId The id of the message that changed.
    */
  case class MessageUpdate(
      guild: Option[Guild],
      messageId: MessageId,
      channelId: TextChannelId,
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends OptGuildMessage
      with MessageIdMessage

  /** Trait that covers all message id messages */
  sealed trait MessageIdMessage extends TextChannelIdMessage {

    def messageId: MessageId

    def message: Option[Message] = cache.current.getMessage(channelId, messageId)
  }

  /**
    * Sent to the client when a message is deleted.
    * @param messageId The deleted message.
    * @param channelId The channel of the message.
    */
  case class MessageDelete(
      messageId: MessageId,
      guild: Option[Guild],
      channelId: TextChannelId,
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends MessageIdMessage
      with TextChannelIdMessage

  /**
    * Sent to the client when multiple messages are deleted at the same time.
    * Often this is performed by a bot.
    * @param messageIds The deleted messages
    * @param channelId The channel of the deleted messages
    */
  case class MessageDeleteBulk(
      messageIds: Seq[MessageId],
      guild: Option[Guild],
      channelId: TextChannelId,
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends TextChannelIdMessage

  /**
    * Sent to the client when a user adds a reaction to a message.
    * @param userId The id of the user that added the reaction.
    * @param guild The guild the message was sent in.
    * @param channelId The channel the message was sent in.
    * @param messageId The id of the message the user added an reaction to.
    * @param emoji The emoji the user reacted with.
    * @param user The user that added the reaction.
    * @param member The guild member that added the reaction.
    */
  case class MessageReactionAdd(
      userId: UserId,
      guild: Option[Guild],
      channelId: TextChannelId,
      messageId: MessageId,
      emoji: PartialEmoji,
      user: Option[User],
      member: Option[GuildMember],
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends MessageIdMessage
      with TextChannelIdMessage

  /**
    * Sent to the client when a user removes a reaction from a message.
    * @param userId The id of the user that added the reaction.
    * @param guild The guild the message was sent in.
    * @param channelId The channel the message was sent in.
    * @param messageId The id of the message the user added an reaction to.
    * @param emoji The emoji the user removed.
    * @param user The user that added the reaction.
    * @param member The guild member that added the reaction.
    */
  case class MessageReactionRemove(
      userId: UserId,
      guild: Option[Guild],
      channelId: TextChannelId,
      messageId: MessageId,
      emoji: PartialEmoji,
      user: Option[User],
      member: Option[GuildMember],
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends MessageIdMessage
      with TextChannelIdMessage

  /**
    * Sent to the client when a user removes all reactions from a message.
    * The emojis of the message can be found in [[cache.previous]].
    * @param guild The guild of the message.
    * @param channelId The id of the channel the message was sent it.
    * @param messageId The id of the message the user removed the reactions from.
    */
  case class MessageReactionRemoveAll(
      guild: Option[Guild],
      channelId: TextChannelId,
      messageId: MessageId,
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends MessageIdMessage
      with TextChannelIdMessage

  /**
    * Sent to the client when a user removes all reactions of a specific emoji
    * from a message.
    * @param guild The guild of the message.
    * @param channelId The id of the channel the message was sent it.
    * @param messageId The id of the message the user removed the reactions from.
    * @param emoji The removed emoji.
    */
  case class MessageReactionRemoveEmoji(
      guild: Option[Guild],
      channelId: TextChannelId,
      messageId: MessageId,
      emoji: PartialEmoji,
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends MessageIdMessage
      with TextChannelIdMessage

  /**
    * Sent to the client when the presence of a user updates.
    * @param guild The guild where the update took place
    * @param user The user of the presence
    * @param presence The new presence
    */
  case class PresenceUpdate(
      guild: Guild,
      user: User,
      presence: Presence,
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends GuildMessage

  /**
    * Sent to the client when a user starts typing in a channel
    * @param guild The guild where the typing happened
    * @param channelId The id of the channel where the typing happened
    * @param userId The id of the user that began typing
    * @param timestamp When user started typing
    * @param user The user that began typing
    * @param member The member that began typing
    */
  case class TypingStart(
      guild: Option[Guild],
      channelId: TextChannelId,
      userId: UserId,
      timestamp: Instant,
      user: Option[User],
      member: Option[GuildMember],
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends TextChannelIdMessage

  /**
    * Sent to the client when a user object is updated.
    * @param user The new user.
    */
  case class UserUpdate(user: User, cache: CacheState, gatewayInfo: GatewayInfo) extends APIMessage

  /**
    * Sent to the client when a user joins/leaves/moves voice channels
    * @param voiceState New voice states
    */
  case class VoiceStateUpdate(voiceState: VoiceState, cache: CacheState, gatewayInfo: GatewayInfo) extends APIMessage

  /**
    * Sent a guilds voice server is updated. Also used when connecting to a voice channel.
    * @param token The voice connection token
    * @param guild The guild of the update
    * @param endpoint The voice server
    */
  case class VoiceServerUpdate(
      token: String,
      guild: Guild,
      endpoint: String,
      cache: CacheState,
      gatewayInfo: GatewayInfo
  ) extends GuildMessage

  /**
    * Sent to the client when guilds webhooks are updated.
    * @param guild The guild of the updated webhook
    * @param channel The channel for the webhook
    */
  case class WebhookUpdate(guild: Guild, channel: GuildChannel, cache: CacheState, gatewayInfo: GatewayInfo)
      extends GuildMessage
      with ChannelMessage

  case class InteractionCreate(rawInteraction: RawInteraction, cache: CacheState, gatewayInfo: GatewayInfo)
      extends APIMessage
}
