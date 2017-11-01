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
package net.katsstuff.ackcord.http.websocket.gateway

import java.time.{Instant, OffsetDateTime}

import akka.NotUsed
import akka.event.LoggingAdapter
import net.katsstuff.ackcord.APIMessage
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.handlers._
import net.katsstuff.ackcord.http._
import net.katsstuff.ackcord.http.websocket.WsMessage
import shapeless._
import shapeless.labelled.FieldType

/**
  * Base trait for all gateway messages.
  */
sealed trait GatewayMessage[D] extends WsMessage[D, GatewayOpCode] {
  def t: Option[ComplexGatewayEvent[D, _]] = None
}

/**
  * Sends a new event.
  * @param sequence The seq number.
  * @param event The sent event.
  */
case class Dispatch[Data](sequence: Int, event: ComplexGatewayEvent[Data, _]) extends GatewayMessage[Data] {
  override val s:  Some[Int]                          = Some(sequence)
  override val t:  Some[ComplexGatewayEvent[Data, _]] = Some(event)
  override def op: GatewayOpCode                      = GatewayOpCode.Dispatch
  override def d:  Data                               = event.data
}

/**
  * Sent and received to confirm the connection is still going.
  * @param d The previous sequence.
  */
case class Heartbeat(d: Option[Int]) extends GatewayMessage[Option[Int]] {
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
  */
case class IdentifyData(
    token: String,
    properties: Map[String, String],
    compress: Boolean,
    largeThreshold: Int,
    shard: Seq[Int],
    presence: StatusData
)
object IdentifyData {
  def createProperties: Map[String, String] =
    Map("$os" -> System.getProperty("os.name"), "$browser" -> "AckCord", "$device" -> "AckCord")
}

/**
  * Sent by the client to log in.
  */
case class Identify(d: IdentifyData) extends GatewayMessage[IdentifyData] {
  override def op: GatewayOpCode = GatewayOpCode.Identify
}

/**
  * @param since If present, instant when the user went idle.
  * @param game The presence text.
  * @param status The status of the user.
  * @param afk If the user is AFK.
  */
case class StatusData(since: Option[Instant], game: Option[RawPresenceGame], status: PresenceStatus, afk: Boolean)

/**
  * Sent when a presence or status changes.
  */
case class StatusUpdate(d: StatusData) extends GatewayMessage[StatusData] {
  override def op: GatewayOpCode = GatewayOpCode.StatusUpdate
}

case class VoiceStateUpdateData(guildId: GuildId, channelId: Option[ChannelId], selfMute: Boolean, selfDeaf: Boolean)
case class VoiceStateUpdate(d: VoiceStateUpdateData) extends GatewayMessage[VoiceStateUpdateData] {
  override def op: GatewayOpCode = GatewayOpCode.VoiceStateUpdate
}

/**
  * @param token The voice connection token.
  * @param guildId The guild of the update.
  * @param endpoint The voice server.
  */
case class VoiceServerUpdateData(token: String, guildId: GuildId, endpoint: String)
case class VoiceServerUpdate(d: VoiceServerUpdateData) extends GatewayMessage[VoiceServerUpdateData] {
  override def op: GatewayOpCode = GatewayOpCode.VoiceServerPing
}

/**
  * @param token The bot token.
  * @param sessionId The sessionId received earlier.
  * @param seq The last seq received.
  */
case class ResumeData(token: String, sessionId: String, seq: Int)

/**
  * Sent by the client instead of [[Identify]] when resuming a connection.
  */
case class Resume(d: ResumeData) extends GatewayMessage[ResumeData] {
  override def op: GatewayOpCode = GatewayOpCode.Resume
}

/**
  * Sent by the gateway to indicate that the client should reconnect.
  */
case object Reconnect extends GatewayMessage[NotUsed] {
  override def op: GatewayOpCode = GatewayOpCode.Reconnect
  override def d:  NotUsed       = NotUsed
}

/**
  * @param guildId The guildId to request for.
  * @param query Return all the users where their username start with this.
  *              or an empty string for all users.
  * @param limit The amount of users to send, or 0 for all users.
  */
case class RequestGuildMembersData(guildId: GuildId, query: String = "", limit: Int = 0)

/**
  * Sent by the client to receive all the members of a guild, even logged out ones.
  */
case class RequestGuildMembers(d: RequestGuildMembersData) extends GatewayMessage[RequestGuildMembersData] {
  override def op: GatewayOpCode = GatewayOpCode.RequestGuildMembers
}

/**
  * Sent by the gateway if the session is invalid when resuming a connection.
  * @param resumable If the connection is resumable.
  */
case class InvalidSession(resumable: Boolean) extends GatewayMessage[Boolean] {
  override def op: GatewayOpCode = GatewayOpCode.InvalidSession
  override def d:  Boolean       = resumable
}

/**
  * @param heartbeatInterval The amount of milliseconds inbetween the time
  *                          to send a heartbeat.
  */
case class HelloData(heartbeatInterval: Int, _trace: Seq[String])

/**
  * Sent by the gateway as a response to [[Identify]]
  */
case class Hello(d: HelloData) extends GatewayMessage[HelloData] {
  override def op: GatewayOpCode = GatewayOpCode.Hello
}

/**
  * Sent by the gateway as a response to [[Heartbeat]].
  */
case object HeartbeatACK extends GatewayMessage[NotUsed] {
  override def op: GatewayOpCode = GatewayOpCode.HeartbeatACK
  override def d:  NotUsed       = NotUsed
}

/**
  * All the different opcodes used by the gateway.
  * @param code The number of the opcode.
  */
sealed abstract case class GatewayOpCode(code: Int)
object GatewayOpCode {
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

  /**
    * Get an opcode from a number if it exists.
    */
  def forCode(code: Int): Option[GatewayOpCode] = code match {
    case 0  => Some(Dispatch)
    case 1  => Some(Heartbeat)
    case 2  => Some(Identify)
    case 3  => Some(StatusUpdate)
    case 4  => Some(VoiceStateUpdate)
    case 5  => Some(VoiceServerPing)
    case 6  => Some(Resume)
    case 7  => Some(Reconnect)
    case 8  => Some(RequestGuildMembers)
    case 9  => Some(InvalidSession)
    case 10 => Some(Hello)
    case 11 => Some(HeartbeatACK)
    case _  => None
  }
}

/**
  * Base trait for all gateway events.
  * @tparam Data The data this event carries.
  * @tparam HandlerType The type the cache handler takes.
  */
sealed trait ComplexGatewayEvent[Data, HandlerType] {

  /**
    * The name of this event.
    */
  def name: String

  /**
    * The data carried by this event.
    */
  def data: Data

  /**
    * The cache handler used to handle this event.
    */
  def cacheHandler: CacheHandler[HandlerType]

  /**
    * Convert the data carried by this event into a format the cache handler can understand.
    */
  def handlerData: HandlerType

  /**
    * Creates an [[APIMessage]] as long as all the needed components are in place.
    */
  def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage]
}

/**
  * A simpler gateway event where the data type and the handler type are the same.
  */
sealed trait SimpleGatewayEvent[Data] extends ComplexGatewayEvent[Data, Data] {
  override def handlerData: Data = data
}

object GatewayEvent {

  /**
    * @param v The API version used.
    * @param user The client user.
    * @param privateChannels The DM channels for this client.
    * @param guilds The guilds for this client. Not available at first.
    * @param sessionId The session id.
    */
  case class ReadyData(
      v: Int,
      user: User,
      privateChannels: Seq[RawChannel],
      guilds: Seq[UnavailableGuild],
      sessionId: String,
      _trace: Seq[String]
  )

  /**
    * Sent to the client when Discord is ready to serve requests. No requests
    * should be sent before this has been received.
    */
  case class Ready(data: ReadyData) extends SimpleGatewayEvent[ReadyData] {
    override def name:         String                  = "READY"
    override def cacheHandler: CacheHandler[ReadyData] = ReadyHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      Some(APIMessage.Ready(current, prev))
  }

  case class ResumedData(_trace: Seq[String])

  /**
    * Sent to the client when a previously interrupted connection is resumed.
    */
  case class Resumed(data: ResumedData) extends SimpleGatewayEvent[ResumedData] {
    override def name:         String                    = "RESUMED"
    override def cacheHandler: CacheHandler[ResumedData] = new NOOPHandler[ResumedData]
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      Some(APIMessage.Resumed(current, prev))
  }

  sealed trait OptGuildEvent[Data] extends SimpleGatewayEvent[Data] {
    def guildId: Option[GuildId]
  }

  /**
    * Sent to the client when a new channel is created.
    * @param data The channel that was created.
    */
  case class ChannelCreate(data: RawChannel) extends OptGuildEvent[RawChannel] {
    override def name:         String                   = "CHANNEL_CREATE"
    override def cacheHandler: CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getChannel(data.id).map(c => APIMessage.ChannelCreate(c, current, prev))
    override def guildId: Option[GuildId] = data.guildId
  }

  /**
    * Sent to the client when a channel is edited or updated.
    * @param data The channel that was edited. This will always be a
    *                guild channel.
    */
  case class ChannelUpdate(data: RawChannel) extends OptGuildEvent[RawChannel] {
    override def name:         String                   = "CHANNEL_UPDATE"
    override def cacheHandler: CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuildChannel(data.id).map(c => APIMessage.ChannelUpdate(c, current, prev))
    override def guildId: Option[GuildId] = data.guildId
  }

  /**
    * Sent to the client when a channel is deleted. The current snapshot will
    * not contain the channel.
    * @param data The channel that was deleted.
    */
  case class ChannelDelete(data: RawChannel) extends OptGuildEvent[RawChannel] {
    override def name:         String                   = "CHANNEL_DELETE"
    override def cacheHandler: CacheHandler[RawChannel] = RawHandlers.rawChannelDeleteHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      prev.getChannel(data.id).map(c => APIMessage.ChannelDelete(c, current, prev))
    override def guildId: Option[GuildId] = data.guildId
  }

  /**
    * @param channelId The channel where the change happened.
    * @param timestamp The time the most recent pinned message was pinned.
    */
  case class ChannelPinsUpdateData(channelId: ChannelId, timestamp: Option[OffsetDateTime])

  sealed trait ChannelEvent[Data] extends SimpleGatewayEvent[Data] {
    def channelId: ChannelId
  }

  /**
    * Sent to the client when a message is pinned or unpinned in a text
    * channel. This is not sent when a pinned message is deleted.
    */
  case class ChannelPinsUpdate(data: ChannelPinsUpdateData) extends ChannelEvent[ChannelPinsUpdateData] {
    override def name: String = "CHANNEL_PINS_UPDATE"
    override def cacheHandler: CacheHandler[ChannelPinsUpdateData] =
      new NOOPHandler[ChannelPinsUpdateData] //No way for us to know what changed
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getTChannel(data.channelId).map(c => APIMessage.ChannelPinsUpdate(c, data.timestamp, current, prev))
    override def channelId: ChannelId = data.channelId
  }

  sealed trait GuildEvent[Data] extends SimpleGatewayEvent[Data] {
    def guildId: GuildId
  }

  /**
    * Sent to the client after the client connects to the gateway, when a
    * previously unavailable guild becomes available, and when the client
    * joins a new guild.
    * @param data The created guild object.
    */
  case class GuildCreate(data: RawGuild) extends GuildEvent[RawGuild] {
    override def name:         String                 = "GUILD_CREATE"
    override def cacheHandler: CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.id).map(g => APIMessage.GuildCreate(g, current, prev))
    override def guildId: GuildId = data.id
  }

  /**
    * Sent to the client when the guild object is updated.
    * @param data The updated guild.
    */
  case class GuildUpdate(data: RawGuild) extends GuildEvent[RawGuild] {
    override def name:         String                 = "GUILD_UPDATE"
    override def cacheHandler: CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.id).map(g => APIMessage.GuildUpdate(g, current, prev))
    override def guildId: GuildId = data.id
  }

  /**
    * Sent to the client either if a guild becomes unavailable due to and
    * outage, or if the client leaves or is kicked from a guild.
    * @param data The deleted or unavailable guild.
    */
  case class GuildDelete(data: UnavailableGuild) extends GuildEvent[UnavailableGuild] {
    override def name:         String                         = "GUILD_DELETE"
    override def cacheHandler: CacheHandler[UnavailableGuild] = RawHandlers.deleteGuildDataHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      prev.getGuild(data.id).map(g => APIMessage.GuildDelete(g, data.unavailable, current, prev))
    override def guildId: GuildId = data.id
  }

  val userGen = LabelledGeneric[User]
  type GuildUser = FieldType[Witness.`'guildId`.T, GuildId] :: userGen.Repr

  sealed trait ComplexGuildEvent[Data, HandlerType] extends ComplexGatewayEvent[Data, HandlerType] {
    def guildId: GuildId
  }

  /**
    * Sent to the client when an user is banned from a guild.
    * @param data The banned user with a guildId of what guild the user was banned from.
    */
  case class GuildBanAdd(data: GuildUser) extends ComplexGuildEvent[GuildUser, (GuildId, RawBan)] {
    override def name:         String                          = "GUILD_BAN_ADD"
    override def cacheHandler: CacheHandler[(GuildId, RawBan)] = RawHandlers.rawBanUpdateHandler
    override def handlerData:  (GuildId, RawBan)               = (data.head, RawBan(None, userGen.from(data.tail)))
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.head).map(g => APIMessage.GuildBanAdd(g, userGen.from(data.tail), current, prev))
    override def guildId: GuildId = data.head
  }

  /**
    * Sent to the client when an user is unbanned from a guild.
    * @param data The unbanned user with a guildId of what guild the user was unbanned from.
    */
  case class GuildBanRemove(data: GuildUser) extends ComplexGuildEvent[GuildUser, (GuildId, User)] {
    override def name:         String                        = "GUILD_BAN_REMOVE"
    override def cacheHandler: CacheHandler[(GuildId, User)] = RawHandlers.rawBanDeleteHandler
    override def handlerData:  (GuildId, User)               = (data.head, userGen.from(data.tail))
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.head).map(g => APIMessage.GuildBanRemove(g, userGen.from(data.tail), current, prev))
    override def guildId: GuildId = data.head
  }

  /**
    * @param guildId The guild where the update occoured.
    * @param emojis The new emojis.
    */
  case class GuildEmojisUpdateData(guildId: GuildId, emojis: Seq[Emoji])

  /**
    * Sent to the client when the emojis of a guild have been updated.
    */
  case class GuildEmojisUpdate(data: GuildEmojisUpdateData) extends GuildEvent[GuildEmojisUpdateData] {
    override def name:         String                              = "GUILD_EMOJIS_UPDATE"
    override def cacheHandler: CacheHandler[GuildEmojisUpdateData] = RawHandlers.guildEmojisUpdateDataHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.guildId).map(g => APIMessage.GuildEmojiUpdate(g, data.emojis, current, prev))
    override def guildId: GuildId = data.guildId
  }

  /**
    * @param guildId The guild where the update occurred.
    */
  case class GuildIntegrationsUpdateData(guildId: GuildId)

  /**
    * Sent to the client when the integrations of a guild were updated. You
    * have to fetch the integrations yourself.
    */
  case class GuildIntegrationsUpdate(data: GuildIntegrationsUpdateData)
      extends GuildEvent[GuildIntegrationsUpdateData] {
    override def name:         String                                    = "GUILD_INTEGRATIONS_UPDATE"
    override def cacheHandler: CacheHandler[GuildIntegrationsUpdateData] = new NOOPHandler[GuildIntegrationsUpdateData]
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.guildId).map(g => APIMessage.GuildIntegrationsUpdate(g, current, prev))
    override def guildId: GuildId = data.guildId
  }

  //Remember to edit RawGuildMember when editing this
  case class RawGuildMemberWithGuild(
      guildId: GuildId,
      user: User,
      nick: Option[String],
      roles: Seq[RoleId],
      joinedAt: OffsetDateTime,
      deaf: Boolean,
      mute: Boolean
  ) {
    def toRawGuildMember: RawGuildMember = RawGuildMember(user, nick, roles, joinedAt, deaf, mute)
  }
  object RawGuildMemberWithGuild {
    def apply(guildId: GuildId, m: RawGuildMember): RawGuildMemberWithGuild =
      new RawGuildMemberWithGuild(guildId, m.user, m.nick, m.roles, m.joinedAt, m.deaf, m.mute)
  }

  /**
    * Sent to the client when a user joins the guild.
    * @param data The new guild member, includes a guild id.
    */
  case class GuildMemberAdd(data: RawGuildMemberWithGuild) extends GuildEvent[RawGuildMemberWithGuild] {
    override def name:         String                                = "GUILD_MEMBER_ADD"
    override def cacheHandler: CacheHandler[RawGuildMemberWithGuild] = RawHandlers.rawGuildMemberWithGuildUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        g   <- current.getGuild(data.guildId)
        mem <- g.members.get(data.user.id)
      } yield APIMessage.GuildMemberAdd(mem, g, current, prev)
    override def guildId: GuildId = data.guildId
  }

  /**
    * @param user The user that left.
    * @param guildId The guild the user left from.
    */
  case class GuildMemberRemoveData(guildId: GuildId, user: User)

  /**
    * Sent to the client when a user leaves the guild (or is kicked or banned).
    */
  case class GuildMemberRemove(data: GuildMemberRemoveData) extends GuildEvent[GuildMemberRemoveData] {
    override def name:         String                              = "GUILD_MEMBER_REMOVE"
    override def cacheHandler: CacheHandler[GuildMemberRemoveData] = RawHandlers.rawGuildMemberDeleteHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.guildId).map(g => APIMessage.GuildMemberRemove(data.user, g, current, prev))
    override def guildId: GuildId = data.guildId
  }

  /**
    * The fields seen here are all the fields that can change. Looking at the
    * users [[RawGuildMember]] for changes is pointless.
    * @param guildId The guild of the guild member.
    * @param roles Thew new roles for the guild member.
    * @param user The user of the updated guild member.
    * @param nick Nick of the user if one was set.
    */
  case class GuildMemberUpdateData(guildId: GuildId, roles: Seq[RoleId], user: User, nick: Option[String]) //TODO: Nick can probably be null here

  /**
    * Sent to the client when a guild member is updated.
    */
  case class GuildMemberUpdate(data: GuildMemberUpdateData) extends GuildEvent[GuildMemberUpdateData] {
    override def name:         String                              = "GUILD_MEMBER_UPDATE"
    override def cacheHandler: CacheHandler[GuildMemberUpdateData] = RawHandlers.rawGuildMemberUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map { g =>
          APIMessage.GuildMemberUpdate(g, data.roles.flatMap(current.getRole), data.user, data.nick, current, prev)
        }
    override def guildId: GuildId = data.guildId
  }

  /**
    * @param guildId The guild requested for.
    * @param members The guild members in this chunk.
    */
  case class GuildMemberChunkData(guildId: GuildId, members: Seq[RawGuildMember])

  /**
    * Sent to the client if the client requests to get all members
    * (even offline ones) for large guilds using [[RequestGuildMembers]].
    */
  case class GuildMemberChunk(data: GuildMemberChunkData) extends GuildEvent[GuildMemberChunkData] {
    override def name:         String                             = "GUILD_MEMBER_CHUNK"
    override def cacheHandler: CacheHandler[GuildMemberChunkData] = RawHandlers.rawGuildMemberChunkHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map(g => APIMessage.GuildMembersChunk(g, data.members.flatMap(m => g.members.get(m.user.id)), current, prev))
    override def guildId: GuildId = data.guildId
  }

  /**
    * @param guildId The guild of the modified role.
    * @param role The modified role.
    */
  case class GuildRoleModifyData(guildId: GuildId, role: RawRole)

  /**
    * Sent to the client when a new role is created.
    */
  case class GuildRoleCreate(data: GuildRoleModifyData) extends GuildEvent[GuildRoleModifyData] {
    override def name:         String                            = "GUILD_ROLE_CREATE"
    override def cacheHandler: CacheHandler[GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map(g => APIMessage.GuildRoleCreate(g, data.role.makeRole(data.guildId), current, prev))
    override def guildId: GuildId = data.guildId
  }

  /**
    * Sent to the client when a role is updated.
    */
  case class GuildRoleUpdate(data: GuildRoleModifyData) extends GuildEvent[GuildRoleModifyData] {
    override def name:         String                            = "GUILD_ROLE_UPDATE"
    override def cacheHandler: CacheHandler[GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map(g => APIMessage.GuildRoleUpdate(g, data.role.makeRole(data.guildId), current, prev))
    override def guildId: GuildId = data.guildId
  }

  /**
    * @param guildId The guild of the deleted role.
    * @param roleId The deleted role.
    */
  case class GuildRoleDeleteData(guildId: GuildId, roleId: RoleId)

  /**
    * Sent to the client when a role is deleted.
    */
  case class GuildRoleDelete(data: GuildRoleDeleteData) extends GuildEvent[GuildRoleDeleteData] {
    override def name:         String                            = "GUILD_ROLE_DELETE"
    override def cacheHandler: CacheHandler[GuildRoleDeleteData] = RawHandlers.roleDeleteHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      prev.getGuild(data.guildId).flatMap(g => g.roles.get(data.roleId).map(g -> _)).map {
        case (g, r) =>
          APIMessage.GuildRoleDelete(g, r, current, prev)
      }
    override def guildId: GuildId = data.guildId
  }

  /**
    * Sent to the client when a message is created (posted).
    * @param data The sent message.
    */
  case class MessageCreate(data: RawMessage) extends ChannelEvent[RawMessage] {
    override def name:         String                   = "MESSAGE_CREATE"
    override def cacheHandler: CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getMessage(data.id).map(message => APIMessage.MessageCreate(message, current, prev))
    override def channelId: ChannelId = data.channelId
  }

  //RawPartialMessage is defined explicitly because we need to handle the author
  case class RawPartialMessage(
      id: MessageId,
      channelId: ChannelId,
      author: Option[Author],
      content: Option[String],
      timestamp: Option[OffsetDateTime],
      editedTimestamp: Option[OffsetDateTime],
      tts: Option[Boolean],
      mentionEveryone: Option[Boolean],
      mentions: Option[Seq[User]],
      mentionRoles: Option[Seq[RoleId]],
      attachment: Option[Seq[Attachment]],
      embeds: Option[Seq[ReceivedEmbed]],
      reactions: Option[Seq[Reaction]],
      nonce: Option[Snowflake],
      pinned: Option[Boolean],
      webhookId: Option[String]
  )

  /**
    * Sent to the client when a message is updated.
    * @param data The new message.
    */
  case class MessageUpdate(data: RawPartialMessage) extends ChannelEvent[RawPartialMessage] {
    override def name:         String                          = "MESSAGE_UPDATE"
    override def cacheHandler: CacheHandler[RawPartialMessage] = RawHandlers.rawPartialMessageUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getMessage(data.id).map(message => APIMessage.MessageCreate(message, current, prev))
    override def channelId: ChannelId = data.channelId
  }

  /**
    * @param id The deleted message.
    * @param channelId The channel of the message.
    */
  case class MessageDeleteData(id: MessageId, channelId: ChannelId)

  /**
    * Sent to the client when a message is deleted.
    */
  case class MessageDelete(data: MessageDeleteData) extends ChannelEvent[MessageDeleteData] {
    override def name:         String                          = "MESSAGE_DELETE"
    override def cacheHandler: CacheHandler[MessageDeleteData] = RawHandlers.rawMessageDeleteHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      prev.getMessage(data.id).flatMap { message =>
        current.getChannel(data.channelId).collect {
          case channel: TChannel => APIMessage.MessageDelete(message, channel, current, prev)
        }
      }
    override def channelId: ChannelId = data.channelId
  }

  /**
    * @param ids The deleted messages.
    * @param channelId The channel of the deleted messages.
    */
  case class MessageDeleteBulkData(ids: Seq[MessageId], channelId: ChannelId)

  /**
    * Sent to the client when multiple messages are deleted at the same time.
    * Often this is performed by a bot.
    */
  case class MessageDeleteBulk(data: MessageDeleteBulkData) extends ChannelEvent[MessageDeleteBulkData] {
    override def name:         String                              = "MESSAGE_DELETE_BULK"
    override def cacheHandler: CacheHandler[MessageDeleteBulkData] = RawHandlers.rawMessageDeleteBulkHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getChannel(data.channelId).collect {
        case channel: TChannel =>
          APIMessage.MessageDeleteBulk(data.ids.flatMap(prev.getMessage(_).toSeq), channel, current, prev)
      }
    override def channelId: ChannelId = data.channelId
  }

  /**
    * @param userId The user that caused the reaction change.
    * @param channelId The channel of the message.
    * @param messageId The message the reaction belonged to.
    * @param emoji The emoji the user reacted with.
    */
  case class MessageReactionData(userId: UserId, channelId: ChannelId, messageId: MessageId, emoji: PartialEmoji)

  /**
    * Sent to the client when a user adds a reaction to a message.
    */
  case class MessageReactionAdd(data: MessageReactionData) extends ChannelEvent[MessageReactionData] {
    override def name:         String                            = "MESSAGE_REACTION_ADD"
    override def cacheHandler: CacheHandler[MessageReactionData] = RawHandlers.rawMessageReactionUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        user     <- current.getUser(data.userId)
        channel  <- current.getChannel(data.channelId)
        tChannel <- Typeable[TChannel].cast(channel)
        message  <- current.getMessage(data.channelId, data.messageId)
      } yield APIMessage.MessageReactionAdd(user, tChannel, message, data.emoji, current, prev)
    override def channelId: ChannelId = data.channelId
  }

  /**
    * Sent to the client when a user removes a reaction from a message.
    */
  case class MessageReactionRemove(data: MessageReactionData) extends ChannelEvent[MessageReactionData] {
    override def name:         String                            = "MESSAGE_REACTION_REMOVE"
    override def cacheHandler: CacheHandler[MessageReactionData] = RawHandlers.rawMessageReactionRemoveHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        user     <- current.getUser(data.userId)
        channel  <- current.getChannel(data.channelId)
        tChannel <- Typeable[TChannel].cast(channel)
        message  <- current.getMessage(data.channelId, data.messageId)
      } yield APIMessage.MessageReactionRemove(user, tChannel, message, data.emoji, current, prev)
    override def channelId: ChannelId = data.channelId
  }

  /**
    * @param channelId The channel of the message.
    * @param messageId The message the user removed the reactions from.
    */
  case class MessageReactionRemoveAllData(channelId: ChannelId, messageId: MessageId)

  /**
    * Sent to the client when a user removes all reactions from a message.
    */
  case class MessageReactionRemoveAll(data: MessageReactionRemoveAllData)
      extends ChannelEvent[MessageReactionRemoveAllData] {
    override def name: String = "MESSAGE_REACTION_REMOVE_ALL"
    override def cacheHandler: CacheHandler[MessageReactionRemoveAllData] =
      RawHandlers.rawMessageReactionRemoveAllHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        channel  <- current.getChannel(data.channelId)
        tChannel <- Typeable[TChannel].cast(channel)
        message  <- current.getMessage(data.channelId, data.messageId)
      } yield APIMessage.MessageReactionRemoveAll(tChannel, message, current, prev)
    override def channelId: ChannelId = data.channelId
  }

  /**
    * @param user The user of the presence.
    * @param roles The roles of the user.
    * @param game The new presence message.
    * @param guildId The guild where the update took place.
    * @param status The new status.
    */
  case class PresenceUpdateData(
      user: PartialUser,
      roles: Seq[RoleId],
      game: Option[RawPresenceGame],
      guildId: GuildId,
      status: PresenceStatus
  )

  /**
    * Sent to the client when the presence of a user updates.
    */
  case class PresenceUpdate(data: PresenceUpdateData) extends GuildEvent[PresenceUpdateData] {
    override def name:         String                           = "PRESENCE_UPDATE"
    override def cacheHandler: CacheHandler[PresenceUpdateData] = PresenceUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        guild    <- current.getGuild(data.guildId)
        user     <- current.getUser(data.user.id)
        presence <- guild.presences.get(user.id)
      } yield APIMessage.PresenceUpdate(guild, user, data.roles, presence, current, prev)
    override def guildId: GuildId = data.guildId
  }

  /**
    * @param channelId The channel where the typing happened.
    * @param userId The user that began typing.
    * @param timestamp When user started typing.
    */
  case class TypingStartData(channelId: ChannelId, userId: UserId, timestamp: Instant)

  /**
    * Sent to the client when a user starts typing in a channel.
    */
  case class TypingStart(data: TypingStartData) extends ChannelEvent[TypingStartData] {
    override def name:         String                        = "TYPING_START"
    override def cacheHandler: CacheHandler[TypingStartData] = RawHandlers.lastTypedHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getUser(data.userId).flatMap { user =>
        current.getChannel(data.channelId).collect {
          case channel: TChannel => APIMessage.TypingStart(channel, user, data.timestamp, current, prev)
        }
      }
    override def channelId: ChannelId = data.channelId
  }

  /**
    * Sent to the client when a user object is updated.
    * @param data The new user.
    */
  case class UserUpdate(data: User) extends SimpleGatewayEvent[User] {
    override def name:         String             = "USER_UPDATE"
    override def cacheHandler: CacheHandler[User] = RawHandlers.userUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      Some(APIMessage.UserUpdate(data, current, prev))
  }

  /**
    * Sent to the client when a user joins/leaves/moves voice channels.
    * @param data New voice states.
    */
  case class VoiceStateUpdate(data: VoiceState) extends OptGuildEvent[VoiceState] {
    override def name:         String                   = "VOICE_STATUS_UPDATE"
    override def cacheHandler: CacheHandler[VoiceState] = Handlers.voiceStateUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      Some(APIMessage.VoiceStateUpdate(data, current, prev))
    override def guildId: Option[GuildId] = data.guildId
  }

  /**
    * Sent a guilds voice server is updated. Also used when connecting to a voice channel.
    */
  case class VoiceServerUpdate(data: VoiceServerUpdateData) extends GuildEvent[VoiceServerUpdateData] {
    override def name:         String                              = "VOICE_SERVER_UPDATE"
    override def cacheHandler: CacheHandler[VoiceServerUpdateData] = new NOOPHandler[VoiceServerUpdateData]
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map(g => APIMessage.VoiceServerUpdate(data.token, g, data.endpoint, current, prev))
    override def guildId: GuildId = data.guildId
  }

  /**
    * @param guildId The guild of the updated webhook.
    * @param channelId The channel for the webhook.
    */
  case class WebhookUpdateData(guildId: GuildId, channelId: ChannelId)

  /**
    * Sent to the client when guilds webhooks are updated.
    */
  case class WebhookUpdate(data: WebhookUpdateData) extends GuildEvent[WebhookUpdateData] {
    override def name:         String                          = "WEBHOOK_UPDATE"
    override def cacheHandler: CacheHandler[WebhookUpdateData] = new NOOPHandler[WebhookUpdateData]
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        guild   <- current.getGuild(data.guildId)
        channel <- guild.channels.get(data.channelId)
      } yield APIMessage.WebhookUpdate(guild, channel, current, prev)
    override def guildId: GuildId = data.guildId
  }
}
