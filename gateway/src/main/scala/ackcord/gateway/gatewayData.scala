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
package ackcord.gateway

import java.time.{Instant, OffsetDateTime}

import scala.collection.immutable

import ackcord.data._
import ackcord.data.raw._
import ackcord.util.{IntCirceEnumWithUnknown, JsonOption, JsonSome, JsonUndefined}
import akka.NotUsed
import cats.{Eval, Later, Now}
import enumeratum.values.{IntEnum, IntEnumEntry}
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Json}

/**
  * Base trait for all gateway messages.
  */
sealed trait GatewayMessage[+D] {

  /**
    * The op code for the message.
    */
  def op: GatewayOpCode

  /**
    * The data for the message.
    */
  def d: Eval[Either[DecodingFailure, D]]

  /**
    * A sequence number for the message if there is one.
    */
  def s: JsonOption[Int] = JsonUndefined

  def t: JsonOption[GatewayEvent[D]] = JsonUndefined
}

sealed trait EagerGatewayMessage[D] extends GatewayMessage[D] {
  override def d: Now[Decoder.Result[D]] = Now(Right(nowD))
  def nowD: D
}

/**
  * Sent with each new event.
  * @param sequence The seq number.
  * @param event The sent event.
  */
case class Dispatch[+D](sequence: Int, event: GatewayEvent[D]) extends GatewayMessage[D] {
  override val s: JsonSome[Int]                    = JsonSome(sequence)
  override val t: JsonSome[GatewayEvent[D]]        = JsonSome(event)
  override def op: GatewayOpCode                   = GatewayOpCode.Dispatch
  override def d: Eval[Either[DecodingFailure, D]] = event.data
}

/**
  * Sent and received to confirm the connection is still going.
  * @param nowD The previous sequence.
  */
case class Heartbeat(nowD: Option[Int]) extends EagerGatewayMessage[Option[Int]] {
  override def op: GatewayOpCode = GatewayOpCode.Heartbeat
}

/**
  * @param token The bot token.
  * @param properties A map of properties to send.
  * @param compress If compressed messages should be used.
  * @param largeThreshold The threshold where the gateway stops sending
  *                       offline users.
  * @param shard The shard info, the first index is the shard id, while the
  *              second is the total amount of shards.
  * @param presence The presence data to start with.
  * @param guildSubscriptions If member presence events and similar should be
  *                           received. AckCord has not been tested with this
  *                           flag. Continue with caution.
  */
case class IdentifyData(
    token: String,
    properties: Map[String, String],
    compress: Boolean,
    largeThreshold: Int,
    shard: Seq[Int],
    presence: StatusData,
    guildSubscriptions: Boolean,
    intents: GatewayIntents
)
object IdentifyData {

  /**
    * Create a map of the default properties to send with the identify message.
    */
  def createProperties: Map[String, String] =
    Map("$os" -> System.getProperty("os.name"), "$browser" -> "AckCord", "$device" -> "AckCord")
}

/**
  * Sent by the shard to log in.
  */
case class Identify(nowD: IdentifyData) extends EagerGatewayMessage[IdentifyData] {
  override def op: GatewayOpCode = GatewayOpCode.Identify
}

/**
  * @param since If present, instant when the user went idle.
  * @param activities The presence activities.
  * @param status The status of the user.
  * @param afk If the user is AFK.
  */
case class StatusData(
    since: Option[Instant],
    activities: Option[Seq[RawActivity]],
    status: PresenceStatus,
    afk: Boolean
)

/**
  * Sent when a presence or status changes.
  */
case class StatusUpdate(nowD: StatusData) extends EagerGatewayMessage[StatusData] {
  override def op: GatewayOpCode = GatewayOpCode.StatusUpdate
}

/**
  * @param guildId The channel the voice channel is in.
  * @param channelId The voice channel to join.
  * @param selfMute If the bot should mute itself.
  * @param selfDeaf If the bot should deafen itself.
  */
case class VoiceStateUpdateData(
    guildId: GuildId,
    channelId: Option[VoiceGuildChannelId],
    selfMute: Boolean,
    selfDeaf: Boolean
)

/**
  * Sent by the bot to connect to a voice channel.
  */
case class VoiceStateUpdate(nowD: VoiceStateUpdateData) extends EagerGatewayMessage[VoiceStateUpdateData] {
  override def op: GatewayOpCode = GatewayOpCode.VoiceStateUpdate
}

/**
  * @param token The voice connection token.
  * @param guildId The guild of the update.
  * @param endpoint The voice server.
  */
case class VoiceServerUpdateData(token: String, guildId: GuildId, endpoint: String)
case class VoiceServerUpdate(nowD: VoiceServerUpdateData) extends EagerGatewayMessage[VoiceServerUpdateData] {
  override def op: GatewayOpCode = GatewayOpCode.VoiceServerPing
}

/**
  * @param token The bot token.
  * @param sessionId The sessionId received earlier.
  * @param seq The last seq received.
  */
case class ResumeData(token: String, sessionId: String, seq: Int)

/**
  * Sent by the shard instead of [[Identify]] when resuming a connection.
  */
case class Resume(nowD: ResumeData) extends EagerGatewayMessage[ResumeData] {
  override def op: GatewayOpCode = GatewayOpCode.Resume
}

/**
  * Sent by the gateway to indicate that the shard should reconnect.
  */
case object Reconnect extends EagerGatewayMessage[NotUsed] {
  override def op: GatewayOpCode = GatewayOpCode.Reconnect
  override def nowD: NotUsed     = NotUsed
}

/**
  * @param guildId The guildId(s) to request for.
  * @param query Return all the users where their username start with this.
  *              or an empty string for all users.
  * @param limit The amount of users to send, or 0 for all users.
  * @param presences If the presences of the users should be sent too.
  * @param userIds Users to fetch.
  */
case class RequestGuildMembersData(
    guildId: Either[Seq[GuildId], GuildId],
    query: Option[String] = None,
    limit: Int = 0,
    presences: Boolean = false,
    userIds: Option[Seq[UserId]],
    nonce: Option[String]
) {
  require(query.isDefined || userIds.isDefined, "Neither query not userIds is specified")
}

/**
  * Sent by the shard to receive all the members of a guild, even logged out ones.
  */
case class RequestGuildMembers(nowD: RequestGuildMembersData) extends EagerGatewayMessage[RequestGuildMembersData] {
  override def op: GatewayOpCode = GatewayOpCode.RequestGuildMembers
}

/**
  * Sent by the gateway if the session is invalid when resuming a connection.
  * @param resumable If the connection is resumable.
  */
case class InvalidSession(resumable: Boolean) extends EagerGatewayMessage[Boolean] {
  override def op: GatewayOpCode = GatewayOpCode.InvalidSession
  override def nowD: Boolean     = resumable
}

/**
  * @param heartbeatInterval The amount of milliseconds inbetween the time
  *                          to send a heartbeat.
  */
case class HelloData(heartbeatInterval: Int)

/**
  * Sent by the gateway as a response to [[Identify]]
  */
case class Hello(nowD: HelloData) extends EagerGatewayMessage[HelloData] {
  override def op: GatewayOpCode = GatewayOpCode.Hello
}

/**
  * Sent by the gateway as a response to [[Heartbeat]].
  */
case object HeartbeatACK extends EagerGatewayMessage[NotUsed] {
  override def op: GatewayOpCode = GatewayOpCode.HeartbeatACK
  override def nowD: NotUsed     = NotUsed
}

/**
  * All unknown gateway messages.
  */
case class UnknownGatewayMessage(op: GatewayOpCode) extends EagerGatewayMessage[NotUsed] {
  override def nowD: NotUsed = NotUsed
}

/**
  * All the different opcodes used by the gateway.
  * @param value The number of the opcode.
  */
sealed abstract class GatewayOpCode(val value: Int) extends IntEnumEntry
object GatewayOpCode extends IntEnum[GatewayOpCode] with IntCirceEnumWithUnknown[GatewayOpCode] {
  object Dispatch            extends GatewayOpCode(0)
  object Heartbeat           extends GatewayOpCode(1)
  object Identify            extends GatewayOpCode(2)
  object StatusUpdate        extends GatewayOpCode(3)
  object VoiceStateUpdate    extends GatewayOpCode(4)
  object VoiceServerPing     extends GatewayOpCode(5)
  object Resume              extends GatewayOpCode(6)
  object Reconnect           extends GatewayOpCode(7)
  object RequestGuildMembers extends GatewayOpCode(8)
  object InvalidSession      extends GatewayOpCode(9)
  object Hello               extends GatewayOpCode(10)
  object HeartbeatACK        extends GatewayOpCode(11)

  override def values: immutable.IndexedSeq[GatewayOpCode] = findValues

  case class Unknown(i: Int) extends GatewayOpCode(i)

  override def createUnknown(value: Int): GatewayOpCode = Unknown(value)
}

/**
  * Base trait for all gateway events.
  * @tparam D The data this event carries.
  */
sealed trait GatewayEvent[+D] {

  /**
    * The name of this event.
    */
  def name: String

  /**
    * The raw data this event was created from. Used for debugging and error reporting.
    */
  def rawData: Json

  /**
    * The data carried by this event.
    */
  def data: Eval[Either[DecodingFailure, D]]

  /**
    * Maps the data in this event without evaluating it.
    */
  def mapData[A](f: D => A): Eval[Decoder.Result[A]] = data.map(_.map(f))
}

object GatewayEvent {

  case class ReadyApplication(id: RawSnowflake, flags: Int)

  /**
    * @param v The API version used.
    * @param user The client user.
    * @param guilds The guilds for this shard. Not available at first.
    * @param sessionId The session id.
    * @param shard The shard info, the first index is the shard id, while the
    *              second is the total amount of shards.
    */
  case class ReadyData(
      v: Int,
      user: User,
      guilds: Seq[UnavailableGuild],
      sessionId: String,
      shard: Seq[Int],
      application: ReadyApplication
  )

  /**
    * Sent to the shard when Discord is ready to serve requests. No requests
    * should be sent before this has been received.
    */
  case class Ready(rawData: Json, data: Later[Decoder.Result[ReadyData]]) extends GatewayEvent[ReadyData] {
    override def name: String = "READY"
  }

  /**
    * Sent to the shard when a previously interrupted connection is resumed.
    */
  case class Resumed(rawData: Json) extends GatewayEvent[NotUsed] {
    override def name: String = "RESUMED"

    override def data: Later[Result[NotUsed]] = Later(Right(NotUsed))
  }

  /**
    * Base trait for all events that include an optional guild.
    */
  sealed trait OptGuildEvent[D] extends GatewayEvent[D] {

    /**
      * The guild id for this event.
      */
    def guildId: Eval[Decoder.Result[Option[GuildId]]]
  }

  /**
    * Sent to the shard when a new channel is created.
    * @param data The channel that was created.
    */
  case class ChannelCreate(rawData: Json, data: Later[Decoder.Result[RawChannel]])
      extends OptGuildEvent[RawChannel]
      with ChannelEvent[RawChannel] {
    override def name: String                                   = "CHANNEL_CREATE"
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
    override def channelId: Eval[Decoder.Result[ChannelId]]     = mapData(_.id)
  }

  /**
    * Sent to the shard when a channel is edited or updated.
    * @param data The channel that was edited. This will always be a guild channel.
    */
  case class ChannelUpdate(rawData: Json, data: Later[Decoder.Result[RawChannel]])
      extends OptGuildEvent[RawChannel]
      with ChannelEvent[RawChannel] {
    override def name: String                                   = "CHANNEL_UPDATE"
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
    override def channelId: Eval[Decoder.Result[ChannelId]]     = mapData(_.id)
  }

  /**
    * Sent to the shard when a channel is deleted. The current snapshot will
    * not contain the channel.
    * @param data The channel that was deleted.
    */
  case class ChannelDelete(rawData: Json, data: Later[Decoder.Result[RawChannel]])
      extends OptGuildEvent[RawChannel]
      with ChannelEvent[RawChannel] {
    override def name: String                                   = "CHANNEL_DELETE"
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
    override def channelId: Eval[Decoder.Result[ChannelId]]     = mapData(_.id)
  }

  /**
    * Base trait for an event that includes a channel.
    */
  sealed trait ChannelEvent[D] extends GatewayEvent[D] {

    /**
      * The channel associated with this event.
      */
    def channelId: Eval[Decoder.Result[ChannelId]]
  }

  /**
    * @param guildId The id of the guild where this change happened.
    * @param channelId The channel where the change happened.
    * @param timestamp The time the most recent pinned message was pinned.
    */
  case class ChannelPinsUpdateData(
      guildId: Option[GuildId],
      channelId: TextChannelId,
      timestamp: JsonOption[OffsetDateTime]
  )

  /**
    * Sent to the shard when a message is pinned or unpinned in a text
    * channel. This is not sent when a pinned message is deleted.
    */
  case class ChannelPinsUpdate(rawData: Json, data: Later[Decoder.Result[ChannelPinsUpdateData]])
      extends ChannelEvent[ChannelPinsUpdateData] {
    override def name: String                               = "CHANNEL_PINS_UPDATE"
    override def channelId: Eval[Decoder.Result[ChannelId]] = mapData(_.channelId)
  }

  /**
    * Base trait for all simple events that include an optional guild.
    */
  sealed trait GuildEvent[D] extends GatewayEvent[D] {

    /**
      * The guild id for this event.
      */
    def guildId: Eval[Decoder.Result[GuildId]]
  }

  /**
    * Sent to the shard after the shard connects to the gateway, when a
    * previously unavailable guild becomes available, and when the client
    * joins a new guild.
    * @param data The created guild object.
    */
  case class GuildCreate(rawData: Json, data: Later[Decoder.Result[RawGuild]]) extends GuildEvent[RawGuild] {
    override def name: String                           = "GUILD_CREATE"
    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.id)
  }

  /**
    * Sent to the shard when a guild object is updated.
    * @param data The updated guild.
    */
  case class GuildUpdate(rawData: Json, data: Later[Decoder.Result[RawGuild]]) extends GuildEvent[RawGuild] {
    override def name: String                           = "GUILD_UPDATE"
    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.id)
  }

  /**
    * Sent to the shard either if a guild becomes unavailable due to and
    * outage, or if the client leaves or is kicked from a guild.
    * @param data The deleted or unavailable guild.
    */
  case class GuildDelete(rawData: Json, data: Later[Decoder.Result[UnavailableGuild]])
      extends GuildEvent[UnavailableGuild] {
    override def name: String                           = "GUILD_DELETE"
    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.id)
  }

  case class UserWithGuildId(guildId: GuildId, user: User)

  /**
    * Sent to the shard when an user is banned from a guild.
    * @param data The banned user with a guildId of what guild the user was banned from.
    */
  case class GuildBanAdd(rawData: Json, data: Later[Decoder.Result[UserWithGuildId]])
      extends GuildEvent[UserWithGuildId] {
    override def name: String                           = "GUILD_BAN_ADD"
    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * Sent to the shard when an user is unbanned from a guild.
    * @param data The unbanned user with a guildId of what guild the user was unbanned from.
    */
  case class GuildBanRemove(rawData: Json, data: Later[Decoder.Result[UserWithGuildId]])
      extends GuildEvent[UserWithGuildId] {
    override def name: String                           = "GUILD_BAN_REMOVE"
    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * @param guildId The guild where the update occoured.
    * @param emojis The new emojis.
    */
  case class GuildEmojisUpdateData(guildId: GuildId, emojis: Seq[RawEmoji])

  /**
    * Sent to the shard when the emojis of a guild have been updated.
    */
  case class GuildEmojisUpdate(rawData: Json, data: Later[Decoder.Result[GuildEmojisUpdateData]])
      extends GuildEvent[GuildEmojisUpdateData] {
    override def name: String                           = "GUILD_EMOJIS_UPDATE"
    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * @param guildId The guild where the update occurred.
    */
  case class GuildIntegrationsUpdateData(guildId: GuildId)

  /**
    * Sent to the shard when the integrations of a guild were updated. You
    * have to fetch the integrations yourself.
    */
  case class GuildIntegrationsUpdate(rawData: Json, data: Later[Decoder.Result[GuildIntegrationsUpdateData]])
      extends GuildEvent[GuildIntegrationsUpdateData] {
    override def name: String                           = "GUILD_INTEGRATIONS_UPDATE"
    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  //Remember to edit RawGuildMember when editing this
  case class RawGuildMemberWithGuild(
      guildId: GuildId,
      user: User,
      nick: Option[String],
      roles: Seq[RoleId],
      joinedAt: OffsetDateTime,
      premiumSince: Option[OffsetDateTime],
      deaf: Boolean,
      mute: Boolean
  ) {
    def toRawGuildMember: RawGuildMember = RawGuildMember(user, nick, roles, joinedAt, premiumSince, deaf, mute)
  }

  object RawGuildMemberWithGuild {
    def apply(guildId: GuildId, m: RawGuildMember): RawGuildMemberWithGuild =
      new RawGuildMemberWithGuild(guildId, m.user, m.nick, m.roles, m.joinedAt, m.premiumSince, m.deaf, m.mute)
  }

  /**
    * Sent to the shard when a user joins the guild.
    * @param data The new guild member, includes a guild id.
    */
  case class GuildMemberAdd(rawData: Json, data: Later[Decoder.Result[RawGuildMemberWithGuild]])
      extends GuildEvent[RawGuildMemberWithGuild] {
    override def name: String = "GUILD_MEMBER_ADD"

    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * @param user The user that left.
    * @param guildId The guild the user left from.
    */
  case class GuildMemberRemoveData(guildId: GuildId, user: User)

  /**
    * Sent to the shard when a user leaves the guild (or is kicked or banned).
    */
  case class GuildMemberRemove(rawData: Json, data: Later[Decoder.Result[GuildMemberRemoveData]])
      extends GuildEvent[GuildMemberRemoveData] {
    override def name: String                           = "GUILD_MEMBER_REMOVE"
    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * The fields seen here are all the fields that can change. Looking at the
    * users [[ackcord.data.raw.RawGuildMember]] for changes is pointless.
    * @param guildId The guild of the guild member.
    * @param roles Thew new roles for the guild member.
    * @param user The user of the updated guild member.
    * @param nick Nick of the user if one was set.
    * @param premiumSince When the user bosted the guild.
    */
  case class GuildMemberUpdateData(
      guildId: GuildId,
      roles: Seq[RoleId],
      user: User,
      nick: Option[String],
      joinedAt: OffsetDateTime,
      premiumSince: Option[OffsetDateTime]
  )

  /**
    * Sent to the shard when a guild member is updated.
    */
  case class GuildMemberUpdate(rawData: Json, data: Later[Decoder.Result[GuildMemberUpdateData]])
      extends GuildEvent[GuildMemberUpdateData] {
    override def name: String = "GUILD_MEMBER_UPDATE"

    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * @param guildId The guild requested for.
    * @param members The guild members in this chunk.
    * @param notFound If some users that was requested wasn't found, their ID is returned here.
    * @param presences The presences of the users if those were requested as well.
    */
  case class GuildMemberChunkData(
      guildId: GuildId,
      members: Seq[RawGuildMember],
      chunkIndex: Int,
      chunkCount: Int,
      notFound: Option[Seq[UserId]],
      presences: Option[Seq[RawPresence]],
      nonce: Option[String]
  )

  /**
    * Sent to the shard if the shard requests to get all members
    * (even offline ones) for large guilds using [[RequestGuildMembers]].
    */
  case class GuildMemberChunk(rawData: Json, data: Later[Decoder.Result[GuildMemberChunkData]])
      extends GuildEvent[GuildMemberChunkData] {
    override def name: String = "GUILD_MEMBERS_CHUNK"

    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * @param guildId The guild of the modified role.
    * @param role The modified role.
    */
  case class GuildRoleModifyData(guildId: GuildId, role: RawRole)

  /**
    * Sent to the shard when a new role is created.
    */
  case class GuildRoleCreate(rawData: Json, data: Later[Decoder.Result[GuildRoleModifyData]])
      extends GuildEvent[GuildRoleModifyData] {
    override def name: String = "GUILD_ROLE_CREATE"

    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * Sent to the shard when a role is updated.
    */
  case class GuildRoleUpdate(rawData: Json, data: Later[Decoder.Result[GuildRoleModifyData]])
      extends GuildEvent[GuildRoleModifyData] {
    override def name: String                           = "GUILD_ROLE_UPDATE"
    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * @param guildId The guild of the deleted role.
    * @param roleId The deleted role.
    */
  case class GuildRoleDeleteData(guildId: GuildId, roleId: RoleId)

  /**
    * Sent to the shard when a role is deleted.
    */
  case class GuildRoleDelete(rawData: Json, data: Later[Decoder.Result[GuildRoleDeleteData]])
      extends GuildEvent[GuildRoleDeleteData] {
    override def name: String = "GUILD_ROLE_DELETE"

    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  case class InviteCreateData(
      channelId: GuildChannelId,
      code: String,
      createdAt: OffsetDateTime,
      guildId: Option[GuildId],
      inviter: Option[User],
      maxAge: Int,
      maxUses: Int,
      temporary: Boolean,
      uses: Int,
      targetUser: Option[InviteTargetUser],
      targetUserType: Option[Int]
  )

  /**
    * Sent when an invite is created.
    */
  case class InviteCreate(rawData: Json, data: Later[Decoder.Result[InviteCreateData]])
      extends OptGuildEvent[InviteCreateData]
      with ChannelEvent[InviteCreateData] {
    override def name: String                           = "INVITE_CREATE"
    override def guildId: Eval[Result[Option[GuildId]]] = mapData(_.guildId)
    override def channelId: Eval[Result[ChannelId]]     = mapData(_.channelId)
  }

  case class InviteDeleteData(
      channelId: GuildChannelId,
      guildId: Option[GuildId],
      code: String
  )

  /**
    * Sent when an invite is deleted.
    */
  case class InviteDelete(rawData: Json, data: Later[Decoder.Result[InviteDeleteData]])
      extends OptGuildEvent[InviteDeleteData]
      with ChannelEvent[InviteDeleteData] {
    override def name: String                           = "INVITE_DELETE"
    override def guildId: Eval[Result[Option[GuildId]]] = mapData(_.guildId)
    override def channelId: Eval[Result[ChannelId]]     = mapData(_.channelId)
  }

  /**
    * Sent to the shard when a message is created (posted).
    * @param data The sent message.
    */
  case class MessageCreate(rawData: Json, data: Later[Decoder.Result[RawMessage]]) extends ChannelEvent[RawMessage] {
    override def name: String                               = "MESSAGE_CREATE"
    override def channelId: Eval[Decoder.Result[ChannelId]] = mapData(_.channelId)
  }

  //RawPartialMessage is defined explicitly because we need to handle the author
  case class RawPartialMessage(
      id: MessageId,
      channelId: TextChannelId,
      author: JsonOption[Author[_]],
      content: JsonOption[String],
      timestamp: JsonOption[OffsetDateTime],
      editedTimestamp: JsonOption[OffsetDateTime],
      tts: JsonOption[Boolean],
      mentionEveryone: JsonOption[Boolean],
      mentions: JsonOption[Seq[User]],
      mentionRoles: JsonOption[Seq[RoleId]],
      attachment: JsonOption[Seq[Attachment]],
      embeds: JsonOption[Seq[ReceivedEmbed]],
      reactions: JsonOption[Seq[Reaction]],
      nonce: JsonOption[Either[Int, String]],
      pinned: JsonOption[Boolean],
      webhookId: JsonOption[String],
      messageType: JsonOption[MessageType],
      activity: JsonOption[RawMessageActivity],
      application: JsonOption[MessageApplication],
      messageReference: JsonOption[MessageReference],
      flags: JsonOption[MessageFlags],
      stickers: JsonOption[Seq[Sticker]],
      referencedMessage: JsonOption[RawPartialMessage]
  )

  /**
    * Sent to the shard when a message is updated.
    * @param data The new message.
    */
  case class MessageUpdate(rawData: Json, data: Later[Decoder.Result[RawPartialMessage]])
      extends ChannelEvent[RawPartialMessage] {
    override def name: String                               = "MESSAGE_UPDATE"
    override def channelId: Eval[Decoder.Result[ChannelId]] = mapData(_.channelId)
  }

  /**
    * @param id The deleted message.
    * @param channelId The channel of the message.
    * @param guildId The guild this was done in. Can be missing.
    */
  case class MessageDeleteData(id: MessageId, channelId: TextChannelId, guildId: Option[GuildId])

  /**
    * Sent to the shard when a message is deleted.
    */
  case class MessageDelete(rawData: Json, data: Later[Decoder.Result[MessageDeleteData]])
      extends ChannelEvent[MessageDeleteData]
      with OptGuildEvent[MessageDeleteData] {
    override def name: String = "MESSAGE_DELETE"

    override def channelId: Eval[Decoder.Result[ChannelId]]     = mapData(_.channelId)
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
  }

  /**
    * @param ids The deleted messages.
    * @param channelId The channel of the deleted messages.
    * @param guildId The guild this was done in. Can be missing.
    */
  case class MessageDeleteBulkData(ids: Seq[MessageId], channelId: TextChannelId, guildId: Option[GuildId])

  /**
    * Sent to the shard when multiple messages are deleted at the same time.
    * Often this is performed by a bot.
    */
  case class MessageDeleteBulk(rawData: Json, data: Later[Decoder.Result[MessageDeleteBulkData]])
      extends ChannelEvent[MessageDeleteBulkData]
      with OptGuildEvent[MessageDeleteBulkData] {
    override def name: String = "MESSAGE_DELETE_BULK"

    override def channelId: Eval[Decoder.Result[ChannelId]]     = mapData(_.channelId)
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
  }

  /**
    * @param userId The user that caused the reaction change.
    * @param channelId The channel of the message.
    * @param messageId The message the reaction belonged to.
    * @param guildId The guild this was done in. Can be missing.
    * @param emoji The emoji the user reacted with.
    */
  case class MessageReactionData(
      userId: UserId,
      channelId: TextChannelId,
      messageId: MessageId,
      guildId: Option[GuildId],
      member: Option[RawGuildMember],
      emoji: PartialEmoji
  )

  /**
    * Sent to the shard when a user adds a reaction to a message.
    */
  case class MessageReactionAdd(rawData: Json, data: Later[Decoder.Result[MessageReactionData]])
      extends ChannelEvent[MessageReactionData]
      with OptGuildEvent[MessageReactionData] {
    override def name: String = "MESSAGE_REACTION_ADD"

    override def channelId: Eval[Decoder.Result[ChannelId]]     = mapData(_.channelId)
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
  }

  /**
    * Sent to the shard when a user removes a reaction from a message.
    */
  case class MessageReactionRemove(rawData: Json, data: Later[Decoder.Result[MessageReactionData]])
      extends ChannelEvent[MessageReactionData]
      with OptGuildEvent[MessageReactionData] {
    override def name: String = "MESSAGE_REACTION_REMOVE"

    override def channelId: Eval[Decoder.Result[ChannelId]]     = mapData(_.channelId)
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
  }

  /**
    * @param channelId The channel of the message.
    * @param messageId The message the user removed the reactions from.
    * @param guildId The guild this was done in. Can be missing.
    */
  case class MessageReactionRemoveAllData(channelId: TextChannelId, messageId: MessageId, guildId: Option[GuildId])

  /**
    * Sent to the shard when a user removes all reactions from a message.
    */
  case class MessageReactionRemoveAll(rawData: Json, data: Later[Decoder.Result[MessageReactionRemoveAllData]])
      extends ChannelEvent[MessageReactionRemoveAllData]
      with OptGuildEvent[MessageReactionRemoveAllData] {
    override def name: String = "MESSAGE_REACTION_REMOVE_ALL"

    override def channelId: Eval[Decoder.Result[ChannelId]]     = mapData(_.channelId)
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
  }

  /**
    * @param channelId The channel of the message.
    * @param messageId The message the user removed the reactions from.
    * @param guildId The guild this was done in. Can be missing.
    * @param emoji The emoji that was deleted.
    */
  case class MessageReactionRemoveEmojiData(
      channelId: TextChannelId,
      messageId: MessageId,
      guildId: Option[GuildId],
      emoji: PartialEmoji
  )

  /**
    * Sent to the shard when a user removes all reactions of a specific emoji
    * from a message.
    */
  case class MessageReactionRemoveEmoji(rawData: Json, data: Later[Decoder.Result[MessageReactionRemoveEmojiData]])
      extends ChannelEvent[MessageReactionRemoveEmojiData]
      with OptGuildEvent[MessageReactionRemoveEmojiData] {
    override def name: String = "MESSAGE_REACTION_REMOVE_EMOJI"

    override def channelId: Eval[Decoder.Result[ChannelId]]     = mapData(_.channelId)
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
  }

  /**
    * @param user The user of the presence.
    * @param guildId The guild where the update took place.
    * @param status The new status.
    * @param activities The current activites of the user.
    */
  case class PresenceUpdateData(
      user: PartialUser,
      guildId: GuildId,
      status: PresenceStatus,
      activities: Seq[RawActivity],
      clientStatus: ClientStatus
  )

  /**
    * Sent to the shard when the presence of a user updates.
    */
  case class PresenceUpdate(rawData: Json, data: Later[Decoder.Result[PresenceUpdateData]])
      extends GuildEvent[PresenceUpdateData] {
    override def name: String = "PRESENCE_UPDATE"

    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * @param channelId The channel where the typing happened.
    * @param guildId The guild id of where the typing happened.
    * @param userId The user that began typing.
    * @param timestamp When user started typing.
    */
  case class TypingStartData(
      channelId: TextChannelId,
      guildId: Option[GuildId],
      userId: UserId,
      timestamp: Instant,
      member: Option[RawGuildMember]
  )

  /**
    * Sent to the shard when a user starts typing in a channel.
    */
  case class TypingStart(rawData: Json, data: Later[Decoder.Result[TypingStartData]])
      extends ChannelEvent[TypingStartData] {
    override def name: String = "TYPING_START"

    override def channelId: Eval[Decoder.Result[ChannelId]] = mapData(_.channelId)
  }

  /**
    * Sent to the shard when a user object is updated.
    * @param data The new user.
    */
  case class UserUpdate(rawData: Json, data: Later[Decoder.Result[User]]) extends GatewayEvent[User] {
    override def name: String = "USER_UPDATE"
  }

  /**
    * Sent to the shard when a user joins/leaves/moves voice channels.
    * @param data New voice states.
    */
  case class VoiceStateUpdate(rawData: Json, data: Later[Decoder.Result[VoiceState]])
      extends OptGuildEvent[VoiceState] {
    override def name: String                                   = "VOICE_STATUS_UPDATE"
    override def guildId: Eval[Decoder.Result[Option[GuildId]]] = mapData(_.guildId)
  }

  /**
    * Sent a guilds voice server is updated. Also used when connecting to a voice channel.
    */
  case class VoiceServerUpdate(rawData: Json, data: Later[Decoder.Result[VoiceServerUpdateData]])
      extends GuildEvent[VoiceServerUpdateData] {
    override def name: String = "VOICE_SERVER_UPDATE"

    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  /**
    * @param guildId The guild of the updated webhook.
    * @param channelId The channel for the webhook.
    */
  case class WebhookUpdateData(guildId: GuildId, channelId: TextChannelId)

  /**
    * Sent to the shard when guilds webhooks are updated.
    */
  case class WebhookUpdate(rawData: Json, data: Later[Decoder.Result[WebhookUpdateData]])
      extends GuildEvent[WebhookUpdateData] {
    override def name: String = "WEBHOOK_UPDATE"

    override def guildId: Eval[Decoder.Result[GuildId]] = mapData(_.guildId)
  }

  case class SimpleRawInteraction(
      id: RawSnowflake,
      `type`: Int,
      data: Option[Json],
      guildId: GuildId,
      channelId: TextChannelId,
      member: RawGuildMember,
      token: String
  ) {

    def reparse[A: Decoder]: Result[A] = {
      import io.circe.syntax._
      import GatewayProtocol._
      val self: SimpleRawInteraction = this
      self.asJson.as[A]
    }
  }

  case class InteractionCreate(rawData: Json, data: Later[Decoder.Result[SimpleRawInteraction]])
      extends GuildEvent[SimpleRawInteraction] {
    override def guildId: Eval[Result[GuildId]] = mapData(_.guildId)

    override def name: String = "INTERACTION_CREATE"
  }

  /**
    * An unknown event type which AckCord does not know about.
    *
    * Extend this if you want to implement custom event types based on stuff Discord sends.
    */
  trait UnknownEvent[A] extends GatewayEvent[A]
  object UnknownEvent {
    trait UnknownChannelEvent[A]  extends UnknownEvent[A] with ChannelEvent[A]
    trait UnknownGuildEvent[A]    extends UnknownEvent[A] with GuildEvent[A]
    trait UnknownOptGuildEvent[A] extends UnknownEvent[A] with OptGuildEvent[A]
  }

  case class IgnoredEvent(name: String, rawData: Json, data: Later[Decoder.Result[Unit]]) extends GatewayEvent[Unit]
}
