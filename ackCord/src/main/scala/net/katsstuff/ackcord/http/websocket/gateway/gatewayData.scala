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

sealed trait GatewayMessage[D] extends WsMessage[D, GatewayOpCode] {
  def t: Option[ComplexGatewayEvent[D, _]] = None
}

case class Dispatch[Data](sequence: Int, event: ComplexGatewayEvent[Data, _]) extends GatewayMessage[Data] {
  override val s:  Some[Int]                          = Some(sequence)
  override val t:  Some[ComplexGatewayEvent[Data, _]] = Some(event)
  override def op: GatewayOpCode                      = GatewayOpCode.Dispatch
  override def d:  Data                               = event.data
}

case class Heartbeat(d: Option[Int]) extends GatewayMessage[Option[Int]] {
  override def op: GatewayOpCode = GatewayOpCode.Heartbeat
}

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
case class Identify(d: IdentifyData) extends GatewayMessage[IdentifyData] {
  override def op: GatewayOpCode = GatewayOpCode.Identify
}

case class StatusData(since: Option[Instant], game: Option[RawPresenceGame], status: PresenceStatus, afk: Boolean)
case class StatusUpdate(d: StatusData) extends GatewayMessage[StatusData] {
  override def op: GatewayOpCode = GatewayOpCode.StatusUpdate
}

case class VoiceStateUpdateData(guildId: GuildId, channelId: Option[ChannelId], selfMute: Boolean, selfDeaf: Boolean)
case class VoiceStateUpdate(d: VoiceStateUpdateData) extends GatewayMessage[VoiceStateUpdateData] {
  override def op: GatewayOpCode = GatewayOpCode.VoiceStateUpdate
}

case class VoiceServerUpdateData(token: String, guildId: GuildId, endpoint: String)
case class VoiceServerUpdate(d: VoiceServerUpdateData) extends GatewayMessage[VoiceServerUpdateData] {
  override def op: GatewayOpCode = GatewayOpCode.VoiceServerPing
}

case class ResumeData(token: String, sessionId: String, seq: Int)
case class Resume(d: ResumeData) extends GatewayMessage[ResumeData] {
  override def op: GatewayOpCode = GatewayOpCode.Resume
}

case object Reconnect extends GatewayMessage[NotUsed] {
  override def op: GatewayOpCode = GatewayOpCode.Reconnect
  override def d:  NotUsed       = NotUsed
}

case class RequestGuildMembersData(guildId: GuildId, query: String, limit: Int)
case class RequestGuildMembers(d: RequestGuildMembersData) extends GatewayMessage[RequestGuildMembersData] {
  override def op: GatewayOpCode = GatewayOpCode.RequestGuildMembers
}

case class InvalidSession(resumable: Boolean) extends GatewayMessage[Boolean] {
  override def op: GatewayOpCode = GatewayOpCode.InvalidSession
  override def d:  Boolean       = resumable
}

case class HelloData(heartbeatInterval: Int, _trace: Seq[String])
case class Hello(d: HelloData) extends GatewayMessage[HelloData] {
  override def op: GatewayOpCode = GatewayOpCode.Hello
}

case object HeartbeatACK extends GatewayMessage[NotUsed] {
  override def op: GatewayOpCode = GatewayOpCode.HeartbeatACK
  override def d:  NotUsed       = NotUsed
}

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

trait ComplexGatewayEvent[Data, HandlerType] {
  def name: String

  def data:         Data
  def cacheHandler: CacheHandler[HandlerType]
  def handlerData:  HandlerType

  def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage]
}
trait SimpleGatewayEvent[Data] extends ComplexGatewayEvent[Data, Data] {
  override def handlerData: Data = data
}

object GatewayEvent {
  private def notImplementedHandler[A] = new CacheHandler[A] {
    override def handle(builder: CacheSnapshotBuilder, obj: A)(implicit log: LoggingAdapter): Unit =
      log.warning(s"Not implemented handler for $obj")
  }

  case class ReadyData(
      v: Int,
      user: User,
      privateChannels: Seq[RawChannel],
      guilds: Seq[UnavailableGuild],
      sessionId: String,
      _trace: Seq[String]
  )
  case class Ready(data: ReadyData) extends SimpleGatewayEvent[ReadyData] {
    override def name:         String                  = "READY"
    override def cacheHandler: CacheHandler[ReadyData] = ReadyHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      Some(APIMessage.Ready(current, prev))
  }

  case class ResumedData(_trace: Seq[String])
  case class Resumed(data: ResumedData) extends SimpleGatewayEvent[ResumedData] {
    override def name:         String                  = "RESUMED"
    override def cacheHandler: CacheHandler[ResumedData] = new NOOPHandler[ResumedData]
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      Some(APIMessage.Resumed(current, prev))
  }

  case class ChannelCreate(data: RawChannel) extends SimpleGatewayEvent[RawChannel] {
    override def name:         String                   = "CHANNEL_CREATE"
    override def cacheHandler: CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getChannel(data.id).map(c => APIMessage.ChannelCreate(c, current, prev))
  }

  case class ChannelUpdate(data: RawChannel) extends SimpleGatewayEvent[RawChannel] {
    override def name:         String                   = "CHANNEL_UPDATE"
    override def cacheHandler: CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuildChannel(data.id).map(c => APIMessage.ChannelUpdate(c, current, prev))
  }

  case class ChannelDelete(data: RawChannel) extends SimpleGatewayEvent[RawChannel] {
    override def name:         String                   = "CHANNEL_DELETE"
    override def cacheHandler: CacheHandler[RawChannel] = RawHandlers.rawChannelDeleteHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      prev.getChannel(data.id).map(c => APIMessage.ChannelDelete(c, current, prev))
  }

  case class ChannelPinsUpdateData(channelId: ChannelId, timestamp: Option[OffsetDateTime])
  case class ChannelPinsUpdate(data: ChannelPinsUpdateData) extends SimpleGatewayEvent[ChannelPinsUpdateData] {
    override def name:         String                              = "CHANNEL_PINS_UPDATE"
    override def cacheHandler: CacheHandler[ChannelPinsUpdateData] = notImplementedHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getTChannel(data.channelId).map(c => APIMessage.ChannelPinsUpdate(c, data.timestamp, current, prev))
  }

  case class GuildCreate(data: RawGuild) extends SimpleGatewayEvent[RawGuild] {
    override def name:         String                 = "GUILD_CREATE"
    override def cacheHandler: CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.id).map(g => APIMessage.GuildCreate(g, current, prev))
  }

  case class GuildUpdate(data: RawGuild) extends SimpleGatewayEvent[RawGuild] {
    override def name:         String                 = "GUILD_UPDATE"
    override def cacheHandler: CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.id).map(g => APIMessage.GuildUpdate(g, current, prev))
  }

  case class GuildDelete(data: UnavailableGuild) extends SimpleGatewayEvent[UnavailableGuild] {
    override def name:         String                         = "GUILD_DELETE"
    override def cacheHandler: CacheHandler[UnavailableGuild] = RawHandlers.deleteGuildDataHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      prev.getGuild(data.id).map(g => APIMessage.GuildDelete(g, data.unavailable, current, prev))
  }

  val userGen = LabelledGeneric[User]
  type GuildUser = FieldType[Witness.`'guildId`.T, GuildId] :: userGen.Repr

  case class GuildBanAdd(data: GuildUser) extends ComplexGatewayEvent[GuildUser, (GuildId, RawBan)] {
    override def name:         String                          = "GUILD_BAN_ADD"
    override def cacheHandler: CacheHandler[(GuildId, RawBan)] = RawHandlers.rawBanUpdateHandler
    override def handlerData:  (GuildId, RawBan)               = (data.head, RawBan(None, userGen.from(data.tail)))
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.head).map(g => APIMessage.GuildBanAdd(g, userGen.from(data.tail), current, prev))
  }

  case class GuildBanRemove(data: GuildUser) extends ComplexGatewayEvent[GuildUser, (GuildId, User)] {
    override def name:         String                        = "GUILD_BAN_REMOVE"
    override def cacheHandler: CacheHandler[(GuildId, User)] = RawHandlers.rawBanDeleteHandler
    override def handlerData:  (GuildId, User)               = (data.head, userGen.from(data.tail))
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.head).map(g => APIMessage.GuildBanRemove(g, userGen.from(data.tail), current, prev))
  }

  case class GuildEmojisUpdateData(guildId: GuildId, emojis: Seq[Emoji])
  case class GuildEmojisUpdate(data: GuildEmojisUpdateData) extends SimpleGatewayEvent[GuildEmojisUpdateData] {
    override def name:         String                              = "GUILD_EMOJIS_UPDATE"
    override def cacheHandler: CacheHandler[GuildEmojisUpdateData] = RawHandlers.guildEmojisUpdateDataHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.guildId).map(g => APIMessage.GuildEmojiUpdate(g, data.emojis, current, prev))
  }

  case class GuildIntegrationsUpdateData(guildId: GuildId)
  case class GuildIntegrationsUpdate(data: GuildIntegrationsUpdateData)
      extends SimpleGatewayEvent[GuildIntegrationsUpdateData] {
    override def name:         String                                    = "GUILD_INTEGRATIONS_UPDATE"
    override def cacheHandler: CacheHandler[GuildIntegrationsUpdateData] = new NOOPHandler[GuildIntegrationsUpdateData]
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.guildId).map(g => APIMessage.GuildIntegrationsUpdate(g, current, prev))
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

  case class GuildMemberAdd(data: RawGuildMemberWithGuild) extends SimpleGatewayEvent[RawGuildMemberWithGuild] {
    override def name:         String                                = "GUILD_MEMBER_ADD"
    override def cacheHandler: CacheHandler[RawGuildMemberWithGuild] = RawHandlers.rawGuildMemberWithGuildUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        g   <- current.getGuild(data.guildId)
        mem <- g.members.get(data.user.id)
      } yield APIMessage.GuildMemberAdd(mem, g, current, prev)
  }

  case class GuildMemberRemoveData(guildId: GuildId, user: User)
  case class GuildMemberRemove(data: GuildMemberRemoveData) extends SimpleGatewayEvent[GuildMemberRemoveData] {
    override def name:         String                              = "GUILD_MEMBER_REMOVE"
    override def cacheHandler: CacheHandler[GuildMemberRemoveData] = RawHandlers.rawGuildMemberDeleteHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getGuild(data.guildId).map(g => APIMessage.GuildMemberRemove(data.user, g, current, prev))
  }

  case class GuildMemberUpdateData(guildId: GuildId, roles: Seq[RoleId], user: User, nick: Option[String]) //TODO: Nick can probably be null here
  case class GuildMemberUpdate(data: GuildMemberUpdateData) extends SimpleGatewayEvent[GuildMemberUpdateData] {
    override def name:         String                              = "GUILD_MEMBER_UPDATE"
    override def cacheHandler: CacheHandler[GuildMemberUpdateData] = RawHandlers.rawGuildMemberUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map { g =>
          APIMessage.GuildMemberUpdate(g, data.roles.flatMap(current.getRole), data.user, data.nick, current, prev)
        }
  }

  case class GuildMemberChunkData(guildId: GuildId, members: Seq[RawGuildMember])
  case class GuildMemberChunk(data: GuildMemberChunkData) extends SimpleGatewayEvent[GuildMemberChunkData] {
    override def name:         String                             = "GUILD_MEMBER_CHUNK"
    override def cacheHandler: CacheHandler[GuildMemberChunkData] = RawHandlers.rawGuildMemberChunkHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map(g => APIMessage.GuildMembersChunk(g, data.members.flatMap(m => g.members.get(m.user.id)), current, prev))
  }

  case class GuildRoleModifyData(guildId: GuildId, role: RawRole)
  case class GuildRoleCreate(data: GuildRoleModifyData) extends SimpleGatewayEvent[GuildRoleModifyData] {
    override def name:         String                            = "GUILD_ROLE_CREATE"
    override def cacheHandler: CacheHandler[GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map(g => APIMessage.GuildRoleCreate(g, data.role.makeRole(data.guildId), current, prev))
  }

  case class GuildRoleUpdate(data: GuildRoleModifyData) extends SimpleGatewayEvent[GuildRoleModifyData] {
    override def name:         String                            = "GUILD_ROLE_UPDATE"
    override def cacheHandler: CacheHandler[GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map(g => APIMessage.GuildRoleUpdate(g, data.role.makeRole(data.guildId), current, prev))
  }

  case class GuildRoleDeleteData(guildId: GuildId, roleId: RoleId)
  case class GuildRoleDelete(data: GuildRoleDeleteData) extends SimpleGatewayEvent[GuildRoleDeleteData] {
    override def name:         String                            = "GUILD_ROLE_DELETE"
    override def cacheHandler: CacheHandler[GuildRoleDeleteData] = RawHandlers.roleDeleteHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      prev.getGuild(data.guildId).flatMap(g => g.roles.get(data.roleId).map(g -> _)).map {
        case (g, r) =>
          APIMessage.GuildRoleDelete(g, r, current, prev)
      }
  }

  case class MessageCreate(data: RawMessage) extends SimpleGatewayEvent[RawMessage] {
    override def name:         String                   = "MESSAGE_CREATE"
    override def cacheHandler: CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getMessage(data.id).map(message => APIMessage.MessageCreate(message, current, prev))
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
  case class MessageUpdate(data: RawPartialMessage) extends SimpleGatewayEvent[RawPartialMessage] {
    override def name:         String                          = "MESSAGE_UPDATE"
    override def cacheHandler: CacheHandler[RawPartialMessage] = RawHandlers.rawPartialMessageUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getMessage(data.id).map(message => APIMessage.MessageCreate(message, current, prev))
  }

  case class MessageDeleteData(id: MessageId, channelId: ChannelId)
  case class MessageDelete(data: MessageDeleteData) extends SimpleGatewayEvent[MessageDeleteData] {
    override def name:         String                          = "MESSAGE_DELETE"
    override def cacheHandler: CacheHandler[MessageDeleteData] = RawHandlers.rawMessageDeleteHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      prev.getMessage(data.id).flatMap { message =>
        current.getChannel(data.channelId).collect {
          case channel: TChannel => APIMessage.MessageDelete(message, channel, current, prev)
        }
      }
  }

  case class MessageDeleteBulkData(ids: Seq[MessageId], channelId: ChannelId)
  case class MessageDeleteBulk(data: MessageDeleteBulkData) extends SimpleGatewayEvent[MessageDeleteBulkData] {
    override def name:         String                              = "MESSAGE_DELETE_BULK"
    override def cacheHandler: CacheHandler[MessageDeleteBulkData] = RawHandlers.rawMessageDeleteBulkHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getChannel(data.channelId).collect {
        case channel: TChannel =>
          APIMessage.MessageDeleteBulk(data.ids.flatMap(prev.getMessage(_).toSeq), channel, current, prev)
      }
  }

  case class MessageReactionData(userId: UserId, channelId: ChannelId, messageId: MessageId, emoji: PartialEmoji)
  case class MessageReactionAdd(data: MessageReactionData) extends SimpleGatewayEvent[MessageReactionData] {
    override def name:         String                            = "MESSAGE_REACTION_ADD"
    override def cacheHandler: CacheHandler[MessageReactionData] = RawHandlers.rawMessageReactionUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        user     <- current.getUser(data.userId)
        channel  <- current.getChannel(data.channelId)
        tChannel <- Typeable[TChannel].cast(channel)
        message  <- current.getMessage(data.channelId, data.messageId)
      } yield APIMessage.MessageReactionAdd(user, tChannel, message, data.emoji, current, prev)
  }

  case class MessageReactionRemove(data: MessageReactionData) extends SimpleGatewayEvent[MessageReactionData] {
    override def name:         String                            = "MESSAGE_REACTION_REMOVE"
    override def cacheHandler: CacheHandler[MessageReactionData] = RawHandlers.rawMessageReactionRemoveHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        user     <- current.getUser(data.userId)
        channel  <- current.getChannel(data.channelId)
        tChannel <- Typeable[TChannel].cast(channel)
        message  <- current.getMessage(data.channelId, data.messageId)
      } yield APIMessage.MessageReactionRemove(user, tChannel, message, data.emoji, current, prev)
  }

  case class MessageReactionRemoveAllData(channelId: ChannelId, messageId: MessageId)
  case class MessageReactionRemoveAll(data: MessageReactionRemoveAllData)
      extends SimpleGatewayEvent[MessageReactionRemoveAllData] {
    override def name: String = "MESSAGE_REACTION_REMOVE_ALL"
    override def cacheHandler: CacheHandler[MessageReactionRemoveAllData] =
      RawHandlers.rawMessageReactionRemoveAllHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        channel  <- current.getChannel(data.channelId)
        tChannel <- Typeable[TChannel].cast(channel)
        message  <- current.getMessage(data.channelId, data.messageId)
      } yield APIMessage.MessageReactionRemoveAll(tChannel, message, current, prev)
  }

  case class PresenceUpdateData(
      user: PartialUser,
      roles: Seq[RoleId],
      game: Option[RawPresenceGame],
      guildId: GuildId,
      status: PresenceStatus
  )
  case class PresenceUpdate(data: PresenceUpdateData) extends SimpleGatewayEvent[PresenceUpdateData] {
    override def name:         String                           = "PRESENCE_UPDATE"
    override def cacheHandler: CacheHandler[PresenceUpdateData] = PresenceUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        guild    <- current.getGuild(data.guildId)
        user     <- current.getUser(data.user.id)
        presence <- guild.presences.get(user.id)
      } yield APIMessage.PresenceUpdate(guild, user, data.roles, presence, current, prev)
  }

  case class TypingStartData(channelId: ChannelId, userId: UserId, timestamp: Instant)
  case class TypingStart(data: TypingStartData) extends SimpleGatewayEvent[TypingStartData] {
    override def name:         String                        = "TYPING_START"
    override def cacheHandler: CacheHandler[TypingStartData] = RawHandlers.lastTypedHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current.getUser(data.userId).flatMap { user =>
        current.getChannel(data.channelId).collect {
          case channel: TChannel => APIMessage.TypingStart(channel, user, data.timestamp, current, prev)
        }
      }
  }

  case class UserUpdate(data: User) extends SimpleGatewayEvent[User] {
    override def name:         String             = "USER_UPDATE"
    override def cacheHandler: CacheHandler[User] = RawHandlers.userUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      Some(APIMessage.UserUpdate(data, current, prev))
  }

  case class VoiceStateUpdate(data: VoiceState) extends SimpleGatewayEvent[VoiceState] {
    override def name:         String                   = "VOICE_STATUS_UPDATE"
    override def cacheHandler: CacheHandler[VoiceState] = Handlers.voiceStateUpdateHandler
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      Some(APIMessage.VoiceStateUpdate(data, current, prev))
  }

  case class VoiceServerUpdate(data: VoiceServerUpdateData) extends SimpleGatewayEvent[VoiceServerUpdateData] {
    override def name:         String                              = "VOICE_SERVER_UPDATE"
    override def cacheHandler: CacheHandler[VoiceServerUpdateData] = new NOOPHandler[VoiceServerUpdateData]
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      current
        .getGuild(data.guildId)
        .map(g => APIMessage.VoiceServerUpdate(data.token, g, data.endpoint, current, prev))
  }

  case class WebhookUpdateData(guildId: GuildId, channelId: ChannelId)
  case class WebhookUpdate(data: WebhookUpdateData) extends SimpleGatewayEvent[WebhookUpdateData] {
    override def name:         String                          = "WEBHOOK_UPDATE"
    override def cacheHandler: CacheHandler[WebhookUpdateData] = new NOOPHandler[WebhookUpdateData]
    override def createEvent(current: CacheSnapshot, prev: CacheSnapshot): Option[APIMessage] =
      for {
        guild   <- current.getGuild(data.guildId)
        channel <- guild.channels.get(data.channelId)
      } yield APIMessage.WebhookUpdate(guild, channel, current, prev)
  }
}
