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
  def t: Option[GatewayEvent[D]] = None
}

case class Dispatch[Data](sequence: Int, event: GatewayEvent[Data], d: Data) extends GatewayMessage[Data] {
  override val s:  Some[Int]                = Some(sequence)
  override val t:  Some[GatewayEvent[Data]] = Some(event)
  override def op: GatewayOpCode            = GatewayOpCode.Dispatch
}

case class Heartbeat(d: Option[Int]) extends GatewayMessage[Option[Int]] {
  override def op: GatewayOpCode = GatewayOpCode.Heartbeat
}

case class IdentifyObject(
    token: String,
    properties: Map[String, String],
    compress: Boolean,
    largeThreshold: Int,
    shard: Seq[Int],
    presence: StatusData
)
object IdentifyObject {
  def createProperties: Map[String, String] =
    Map("$os" -> System.getProperty("os.name"), "$browser" -> "AckCord", "$device" -> "AckCord")
}
case class Identify(d: IdentifyObject) extends GatewayMessage[IdentifyObject] {
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

sealed abstract case class GatewayEvent[Data](
    name: String,
    handler: CacheHandler[Data],
    createEvent: Data => (CacheSnapshot, CacheSnapshot) => Option[APIMessage]
)
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
  object Ready extends GatewayEvent[ReadyData]("READY", ReadyHandler, _ => (n, o) => Some(APIMessage.Ready(n, o)))

  case class ResumedData(_trace: Seq[String])
  object Resumed
      extends GatewayEvent[ResumedData](
        "RESUMED",
        new NOOPHandler,
        _ => (current, prev) => Some(APIMessage.Resumed(current, prev))
      )

  object ChannelCreate
      extends GatewayEvent[RawChannel](
        "CHANNEL_CREATE",
        RawHandlers.rawChannelUpdateHandler,
        data => (current, prev) => current.getChannel(data.id).map(c => APIMessage.ChannelCreate(c, current, prev))
      )

  object ChannelUpdate
      extends GatewayEvent[RawChannel](
        "CHANNEL_UPDATE",
        RawHandlers.rawChannelUpdateHandler,
        data => (current, prev) => current.getGuildChannel(data.id).map(c => APIMessage.ChannelUpdate(c, current, prev))
      )

  object ChannelDelete
      extends GatewayEvent[RawChannel](
        "CHANNEL_DELETE",
        RawHandlers.rawChannelDeleteHandler,
        data => (current, prev) => prev.getGuildChannel(data.id).map(c => APIMessage.ChannelDelete(c, current, prev))
      )

  case class ChannelPinsUpdateData(channelId: ChannelId, timestamp: Option[OffsetDateTime])
  object ChannelPinsUpdate
      extends GatewayEvent[ChannelPinsUpdateData](
        "CHANNEL_PINS_UPDATE",
        notImplementedHandler,
        data =>
          (current, prev) =>
            current.getTChannel(data.channelId).map(c => APIMessage.ChannelPinsUpdate(c, data.timestamp, current, prev))
      )

  object GuildCreate
      extends GatewayEvent[RawGuild](
        "GUILD_CREATE",
        RawHandlers.rawGuildUpdateHandler,
        data => (current, prev) => current.getGuild(data.id).map(g => APIMessage.GuildCreate(g, current, prev))
      )
  object GuildUpdate
      extends GatewayEvent[RawGuild](
        "GUILD_UPDATE",
        RawHandlers.rawGuildUpdateHandler,
        data => (current, prev) => current.getGuild(data.id).map(g => APIMessage.GuildUpdate(g, current, prev))
      )

  object GuildDelete
      extends GatewayEvent[UnavailableGuild](
        "GUILD_DELETE",
        RawHandlers.deleteGuildDataHandler,
        data =>
          (current, prev) => prev.getGuild(data.id).map(g => APIMessage.GuildDelete(g, data.unavailable, current, prev))
      )

  val userGen = LabelledGeneric[User]
  type GuildUser = FieldType[Witness.`'guildId`.T, GuildId] :: userGen.Repr

  object GuildBanAdd
      extends GatewayEvent[GuildUser](
        "GUILD_BAN_ADD",
        notImplementedHandler,
        data =>
          (current, prev) =>
            current.getGuild(data.head).map(g => APIMessage.GuildBanAdd(g, userGen.from(data.tail), current, prev))
      )

  object GuildBanRemove
      extends GatewayEvent[GuildUser](
        "GUILD_BAN_REMOVE",
        notImplementedHandler,
        data =>
          (current, prev) =>
            current.getGuild(data.head).map(g => APIMessage.GuildBanRemove(g, userGen.from(data.tail), current, prev))
      )

  case class GuildEmojisUpdateData(guildId: GuildId, emojis: Seq[GuildEmoji])
  object GuildEmojisUpdate
      extends GatewayEvent[GuildEmojisUpdateData](
        "GUILD_EMOJIS_UPDATE",
        RawHandlers.guildEmojisUpdateDataHandler,
        data =>
          (current, prev) =>
            current.getGuild(data.guildId).map(g => APIMessage.GuildEmojiUpdate(g, data.emojis, current, prev))
      )

  case class GuildIntegrationsUpdateData(guildId: GuildId)
  object GuildIntegrationsUpdate
      extends GatewayEvent[GuildIntegrationsUpdateData](
        "GUILD_INTEGRATIONS_UPDATE",
        new NOOPHandler[GuildIntegrationsUpdateData],
        data =>
          (current, prev) =>
            current.getGuild(data.guildId).map(g => APIMessage.GuildIntegrationsUpdate(g, current, prev))
      )

  /*
  TODO: Wait for https://github.com/milessabin/shapeless/issues/734 to be fixed for this to work
  val guildMemberGen = LabelledGeneric[RawGuildMember]
  type RawGuildMemberWithGuild = FieldType[Witness.`'guildId`.T, GuildId] :: guildMemberGen.Repr
  object GuildMemberAdd
      extends WsEvent[RawGuildMemberWithGuild](
        "GUILD_MEMBER_ADD",
        RawHandlers.rawGuildMemberWithGuildUpdateHandler,
        data =>
          (current, prev) =>
            for {
              g   <- current.getGuild(data.head)
              mem <- g.members.get(guildMemberGen.from(data.tail).user.id)
            } yield APIMessage.GuildMemberAdd(mem, g, current, prev)
      )
   */
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

  object GuildMemberAdd
      extends GatewayEvent[RawGuildMemberWithGuild](
        "GUILD_MEMBER_ADD",
        RawHandlers.rawGuildMemberWithGuildUpdateHandler,
        data =>
          (current, prev) =>
            for {
              g   <- current.getGuild(data.guildId)
              mem <- g.members.get(data.user.id)
            } yield APIMessage.GuildMemberAdd(mem, g, current, prev)
      )

  case class GuildMemberRemoveData(guildId: GuildId, user: User)
  object GuildMemberRemove
      extends GatewayEvent[GuildMemberRemoveData](
        "GUILD_MEMBER_REMOVE",
        RawHandlers.rawGuildMemberDeleteHandler,
        data =>
          (current, prev) =>
            current.getGuild(data.guildId).map(g => APIMessage.GuildMemberRemove(data.user, g, current, prev))
      )

  case class GuildMemberUpdateData(guildId: GuildId, roles: Seq[RoleId], user: User, nick: Option[String]) //TODO: Nick can probably be null here
  object GuildMemberUpdate
      extends GatewayEvent[GuildMemberUpdateData](
        "GUILD_MEMBER_UPDATE",
        RawHandlers.rawGuildMemberUpdateHandler,
        data =>
          (current, prev) =>
            current
              .getGuild(data.guildId)
              .map(
                g =>
                  APIMessage
                    .GuildMemberUpdate(g, data.roles.flatMap(current.getRole), data.user, data.nick, current, prev)
          )
      )

  case class GuildMemberChunkData(guildId: GuildId, members: Seq[RawGuildMember])
  object GuildMemberChunk
      extends GatewayEvent[GuildMemberChunkData](
        "GUILD_MEMBER_CHUNK",
        RawHandlers.rawGuildMemberChunkHandler,
        data =>
          (current, prev) =>
            current
              .getGuild(data.guildId)
              .map(
                g => APIMessage.GuildMembersChunk(g, data.members.flatMap(m => g.members.get(m.user.id)), current, prev)
          )
      )

  case class GuildRoleModifyData(guildId: GuildId, role: RawRole)
  object GuildRoleCreate
      extends GatewayEvent[GuildRoleModifyData](
        "GUILD_ROLE_CREATE",
        RawHandlers.roleUpdateHandler,
        data =>
          (current, prev) =>
            current
              .getGuild(data.guildId)
              .map(g => APIMessage.GuildRoleCreate(g, data.role.makeRole(data.guildId), current, prev))
      )
  object GuildRoleUpdate
      extends GatewayEvent[GuildRoleModifyData](
        "GUILD_ROLE_UPDATE",
        RawHandlers.roleUpdateHandler,
        data =>
          (current, prev) =>
            current
              .getGuild(data.guildId)
              .map(g => APIMessage.GuildRoleUpdate(g, data.role.makeRole(data.guildId), current, prev))
      )

  case class GuildRoleDeleteData(guildId: GuildId, roleId: RoleId)
  object GuildRoleDelete
      extends GatewayEvent[GuildRoleDeleteData](
        "GUILD_ROLE_DELETE",
        RawHandlers.roleDeleteHandler,
        data =>
          (current, prev) =>
            prev.getGuild(data.guildId).flatMap(g => g.roles.get(data.roleId).map(g -> _)).map {
              case (g, r) =>
                APIMessage.GuildRoleDelete(g, r, current, prev)
        }
      )

  object MessageCreate
      extends GatewayEvent[RawMessage](
        "MESSAGE_CREATE",
        RawHandlers.rawMessageUpdateHandler,
        data =>
          (current, prev) =>
            current.getMessage(data.id).map(message => APIMessage.MessageCreate(message, current, prev))
      )

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
  object MessageUpdate
      extends GatewayEvent[RawPartialMessage](
        "MESSAGE_UPDATE",
        RawHandlers.rawPartialMessageUpdateHandler,
        data =>
          (current, prev) =>
            current.getMessage(data.id).map(message => APIMessage.MessageCreate(message, current, prev))
      )

  case class MessageDeleteData(id: MessageId, channelId: ChannelId)
  object MessageDelete
      extends GatewayEvent[MessageDeleteData](
        "MESSAGE_DELETE",
        RawHandlers.rawMessageDeleteHandler,
        data =>
          (current, prev) =>
            prev
              .getMessage(data.id)
              .flatMap(
                message =>
                  current.getChannel(data.channelId).collect {
                    case channel: TChannel =>
                      APIMessage.MessageDelete(message, channel, current, prev)
                }
          )
      )

  case class MessageDeleteBulkData(ids: Seq[MessageId], channelId: ChannelId)
  object MessageDeleteBulk
      extends GatewayEvent[MessageDeleteBulkData](
        "MESSAGE_DELETE_BULK",
        RawHandlers.rawMessageDeleteBulkHandler,
        data =>
          (current, prev) =>
            current
              .getChannel(data.channelId)
              .collect {
                case channel: TChannel =>
                  APIMessage.MessageDeleteBulk(data.ids.flatMap(prev.getMessage(_).toSeq), channel, current, prev)
          }
      )

  case class MessageReactionData(userId: UserId, channelId: ChannelId, messageId: MessageId, emoji: MessageEmoji)
  object MessageReactionAdd
      extends GatewayEvent[MessageReactionData](
        "MESSAGE_REACTION_ADD",
        RawHandlers.rawMessageReactionUpdateHandler,
        data =>
          (current, prev) =>
            for {
              user     <- current.getUser(data.userId)
              channel  <- current.getChannel(data.channelId)
              tChannel <- Typeable[TChannel].cast(channel)
              message  <- current.getMessage(data.channelId, data.messageId)
            } yield APIMessage.MessageReactionAdd(user, tChannel, message, data.emoji, current, prev)
      )

  object MessageReactionRemove
      extends GatewayEvent[MessageReactionData](
        "MESSAGE_REACTION_REMOVE",
        RawHandlers.rawMessageReactionRemoveHandler,
        data =>
          (current, prev) =>
            for {
              user     <- current.getUser(data.userId)
              channel  <- current.getChannel(data.channelId)
              tChannel <- Typeable[TChannel].cast(channel)
              message  <- current.getMessage(data.channelId, data.messageId)
            } yield APIMessage.MessageReactionRemove(user, tChannel, message, data.emoji, current, prev)
      )

  case class MessageReactionRemoveAllData(channelId: ChannelId, messageId: MessageId)
  object MessageReactionRemoveAll
      extends GatewayEvent[MessageReactionRemoveAllData](
        "MESSAGE_REACTION_REMOVE_ALL",
        RawHandlers.rawMessageReactionRemoveAllHandler,
        data =>
          (current, prev) =>
            for {
              channel  <- current.getChannel(data.channelId)
              tChannel <- Typeable[TChannel].cast(channel)
              message  <- current.getMessage(data.channelId, data.messageId)
            } yield APIMessage.MessageReactionRemoveAll(tChannel, message, current, prev)
      )

  case class PresenceUpdateData(
      user: PartialUser,
      roles: Seq[RoleId],
      game: Option[RawPresenceGame],
      guildId: GuildId,
      status: PresenceStatus
  )
  object PresenceUpdate
      extends GatewayEvent[PresenceUpdateData](
        "PRESENCE_UPDATE",
        PresenceUpdateHandler,
        data =>
          (current, prev) =>
            for {
              guild    <- current.getGuild(data.guildId)
              user     <- current.getUser(data.user.id)
              presence <- guild.presences.get(user.id)
            } yield APIMessage.PresenceUpdate(guild, user, data.roles, presence, current, prev)
      )

  case class TypingStartData(channelId: ChannelId, userId: UserId, timestamp: Instant)
  object TypingStart
      extends GatewayEvent[TypingStartData](
        "TYPING_START",
        RawHandlers.lastTypedHandler,
        data =>
          (current, prev) =>
            current
              .getUser(data.userId)
              .flatMap(
                user =>
                  current.getChannel(data.channelId).collect {
                    case channel: TChannel => APIMessage.TypingStart(channel, user, data.timestamp, current, prev)
                }
          )
      )

  object UserUpdate
      extends GatewayEvent[User](
        "USER_UPDATE",
        RawHandlers.userUpdateHandler,
        (data) => (current, prev) => Some(APIMessage.UserUpdate(data, current, prev))
      )

  object VoiceStateUpdate
      extends GatewayEvent[VoiceState](
        "VOICE_STATUS_UPDATE",
        Handlers.voiceStateUpdateHandler,
        data => (current, prev) => Some(APIMessage.VoiceStateUpdate(data, current, prev))
      )

  object VoiceServerUpdate
      extends GatewayEvent[VoiceServerUpdateData](
        "VOICE_SERVER_UPDATE",
        notImplementedHandler,
        data =>
          (current, prev) =>
            current
              .getGuild(data.guildId)
              .map(g => APIMessage.VoiceServerUpdate(data.token, g, data.endpoint, current, prev))
      )

  case class WebhookUpdateData(guildId: GuildId, channelId: ChannelId)
  object WebhookUpdate
      extends GatewayEvent[WebhookUpdateData](
        "WEBHOOK_UPDATE",
        new NOOPHandler[WebhookUpdateData],
        data =>
          (current, prev) =>
            for {
              guild   <- current.getGuild(data.guildId)
              channel <- guild.channels.get(data.channelId)
            } yield APIMessage.WebhookUpdate(guild, channel, current, prev)
      )

  def forName(name: String): Option[GatewayEvent[_]] = name match {
    case "READY"                       => Some(Ready)
    case "RESUMED"                     => Some(Resumed)
    case "CHANNEL_CREATE"              => Some(ChannelCreate)
    case "CHANNEL_UPDATE"              => Some(ChannelUpdate)
    case "CHANNEL_DELETE"              => Some(ChannelDelete)
    case "CHANNEL_PINS_UPDATE"         => Some(ChannelPinsUpdate)
    case "GUILD_CREATE"                => Some(GuildCreate)
    case "GUILD_UPDATE"                => Some(GuildUpdate)
    case "GUILD_DELETE"                => Some(GuildDelete)
    case "GUILD_BAN_ADD"               => Some(GuildBanAdd)
    case "GUILD_BAN_REMOVE"            => Some(GuildBanRemove)
    case "GUILD_EMOJIS_UPDATE"         => Some(GuildEmojisUpdate)
    case "GUILD_INTEGRATIONS_UPDATE"   => Some(GuildIntegrationsUpdate)
    case "GUILD_MEMBER_ADD"            => Some(GuildMemberAdd)
    case "GUILD_MEMBER_REMOVE"         => Some(GuildMemberRemove)
    case "GUILD_MEMBER_UPDATE"         => Some(GuildMemberUpdate)
    case "GUILD_MEMBER_CHUNK"          => Some(GuildMemberChunk)
    case "GUILD_ROLE_CREATE"           => Some(GuildRoleCreate)
    case "GUILD_ROLE_UPDATE"           => Some(GuildRoleUpdate)
    case "GUILD_ROLE_DELETE"           => Some(GuildRoleDelete)
    case "MESSAGE_CREATE"              => Some(MessageCreate)
    case "MESSAGE_UPDATE"              => Some(MessageUpdate)
    case "MESSAGE_DELETE"              => Some(MessageDelete)
    case "MESSAGE_DELETE_BULK"         => Some(MessageDeleteBulk)
    case "MESSAGE_REACTION_ADD"        => Some(MessageReactionAdd)
    case "MESSAGE_REACTION_REMOVE"     => Some(MessageReactionRemove)
    case "MESSAGE_REACTION_REMOVE_ALL" => Some(MessageReactionRemoveAll)
    case "PRESENCE_UPDATE"             => Some(PresenceUpdate)
    case "TYPING_START"                => Some(TypingStart)
    case "USER_UPDATE"                 => Some(UserUpdate)
    case "VOICE_STATE_UPDATE"          => Some(VoiceStateUpdate)
    case "VOICE_SERVER_UPDATE"         => Some(VoiceServerUpdate)
    case "WEBHOOK_UPDATE"              => Some(WebhookUpdate)
    case _                             => None
  }
}
