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

import java.time.Instant

import scala.collection.mutable

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.EventStream
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.http.{RawChannel, RawDMChannel, RawGuild, RawGuildChannel, RawGuildMember, RawMessage, RawUnavailableGuild}
import net.katsstuff.akkacord.http.websocket.WsEvent
import net.katsstuff.akkacord.http.websocket.WsEvent.{GuildDeleteData, GuildEmojisUpdateData, GuildIntegrationsUpdateData, GuildMemberChunkData, GuildMemberRemoveData, GuildMemberUpdateData, GuildRoleDeleteData, GuildRoleModifyData, MessageDeleteBulkData, MessageDeleteData, RawGuildMemberWithGuild, RawOptionalMessage}
import net.katsstuff.akkacord.util.CachedImmutable

class SnowflakeCache(eventStream: EventStream) extends Actor with ActorLogging {

  private val _dmChannels =
    CachedImmutable[mutable.HashMap[Snowflake, DMChannel], Map[Snowflake, DMChannel]](new mutable.HashMap[Snowflake, DMChannel])
  private var _botUser: User = _

  private val _unavailableGuilds =
    CachedImmutable[mutable.HashMap[Snowflake, UnavailableGuild], Map[Snowflake, UnavailableGuild]](new mutable.HashMap[Snowflake, UnavailableGuild])
  private val _guilds =
    CachedImmutable[mutable.HashMap[Snowflake, AvailableGuild], Map[Snowflake, AvailableGuild]](new mutable.HashMap[Snowflake, AvailableGuild])
  private val _messages =
    CachedImmutable[mutable.Map[Snowflake, CachedImmutable[mutable.HashMap[Snowflake, Message], Map[Snowflake, Message]]], Map[
      Snowflake,
      CachedImmutable[mutable.HashMap[Snowflake, Message], Map[Snowflake, Message]]
    ]](
      new mutable.HashMap[Snowflake, CachedImmutable[mutable.HashMap[Snowflake, Message], Map[Snowflake, Message]]]
        .withDefault(_ => CachedImmutable(new mutable.HashMap[Snowflake, Message]))
    )
  private val _lastTyped =
    CachedImmutable[mutable.Map[Snowflake, CachedImmutable[mutable.HashMap[Snowflake, Instant], Map[Snowflake, Instant]]], Map[
      Snowflake,
      CachedImmutable[mutable.HashMap[Snowflake, Instant], Map[Snowflake, Instant]]
    ]](
      new mutable.HashMap[Snowflake, CachedImmutable[mutable.HashMap[Snowflake, Instant], Map[Snowflake, Instant]]]
        .withDefault(_ => CachedImmutable(new mutable.HashMap[Snowflake, Instant]))
    )
  private val _users = CachedImmutable[mutable.HashMap[Snowflake, User], Map[Snowflake, User]](new mutable.HashMap[Snowflake, User])

  private var snapshot: CacheSnapshot = _

  private def updateSnapshot(): Unit =
    snapshot = CacheSnapshot(
      _botUser,
      _dmChannels.value,
      _unavailableGuilds.value,
      _guilds.value,
      _messages.value.mapValues(_.value),
      _lastTyped.value.mapValues(_.value),
      _users.value
    )

  private def createUpdateGuildChannel(guildChannel: GuildChannel): Unit =
    guildUpdate(guildChannel.guildId, "channel") { guild =>
      guild.copy(channels = guild.channels + (guildChannel.id -> guildChannel))
    }

  private def guildUpdate(id: Snowflake, updateType: String)(f: AvailableGuild => AvailableGuild): Unit =
    _guilds.modify(
      map =>
        map.get(id) match {
          case Some(guild) => map.put(id, f(guild))
          case None        => log.warning("Received {} for unknown guild {}", updateType, id)
      }
    )

  override def receive: Receive = {
    case RawWsEvent(WsEvent.Ready, WsEvent.ReadyData(_, user, rawPrivateChannels, rawGuilds, _, _)) =>
      val (dmChannels, users) = rawPrivateChannels.map {
        case RawDMChannel(id, _, recipient, lastMessageId) =>
          (id -> DMChannel(id, lastMessageId, recipient.id), recipient.id -> recipient)
      }.unzip

      val guilds = rawGuilds.map {
        case RawUnavailableGuild(id, _) =>
          id -> UnavailableGuild(id)
      }

      _botUser = user
      _dmChannels.modify(_ ++= dmChannels)
      _users.modify(_ ++= users)
      _unavailableGuilds.modify(_ ++= guilds)

      updateSnapshot()
      eventStream.publish(APIMessage.Ready(snapshot))
    case RawWsEvent(WsEvent.Resumed, _) =>
      eventStream.publish(APIMessage.Resumed(snapshot))
    case RawWsEvent(WsEvent.ChannelCreate, rawChannel: RawChannel) =>
      //The guild id should always be present
      (rawChannel: @unchecked) match {
        case RawGuildChannel(id, Some(guildId), name, channelType, position, _, permissionOverwrites, topic, lastMessageId, bitrate, userLimit) =>
          val channel = channelType match {
            case "text"  => TGuildChannel(id, guildId, name, position, permissionOverwrites, topic, lastMessageId)
            case "voice" => VGuildChannel(id, guildId, name, position, permissionOverwrites, bitrate.get, userLimit.get)
          }

          createUpdateGuildChannel(channel)

          updateSnapshot()
          eventStream.publish(APIMessage.ChannelCreate(channel, snapshot))

        case RawDMChannel(id, _, recipient, lastMessageId) =>
          val channel = DMChannel(id, lastMessageId, recipient.id)
          _dmChannels.modify(_.put(id, channel))
          _users.modify(_.put(recipient.id, recipient))

          updateSnapshot()
          eventStream.publish(APIMessage.ChannelCreate(channel, snapshot))
      }
    case RawWsEvent(WsEvent.ChannelUpdate, rawChannel: RawGuildChannel) =>
      val RawGuildChannel(id, Some(guildId), name, channelType, position, _, permissionOverwrites, topic, lastMessageId, bitrate, userLimit) =
        rawChannel

      val channel = channelType match {
        case "text"  => TGuildChannel(id, guildId, name, position, permissionOverwrites, topic, lastMessageId)
        case "voice" => VGuildChannel(id, guildId, name, position, permissionOverwrites, bitrate.get, userLimit.get)
      }
      createUpdateGuildChannel(channel)

      eventStream.publish(APIMessage.ChannelUpdate(channel, snapshot))
      updateSnapshot()
    case RawWsEvent(WsEvent.ChannelDelete, rawChannel: RawChannel) =>
      //The guild id should always be present
      (rawChannel: @unchecked) match {
        case RawGuildChannel(id, Some(guildId), name, channelType, position, _, permissionOverwrites, topic, lastMessageId, bitrate, userLimit) =>
          val channel = channelType match {
            case "text"  => TGuildChannel(id, guildId, name, position, permissionOverwrites, topic, lastMessageId)
            case "voice" => VGuildChannel(id, guildId, name, position, permissionOverwrites, bitrate.get, userLimit.get)
          }

          guildUpdate(guildId, "channel") { guild =>
            guild.copy(channels = guild.channels - id)
          }

          updateSnapshot()
          eventStream.publish(APIMessage.ChannelDelete(channel, snapshot))
        case RawDMChannel(id, _, recipient, lastMessageId) =>
          val channel = DMChannel(id, lastMessageId, recipient.id)
          _dmChannels.modify(_.remove(id))
          _users.modify(_.put(recipient.id, recipient)) //Should I still update the user here?

          updateSnapshot()
          eventStream.publish(APIMessage.ChannelDelete(channel, snapshot))
      }

    case RawWsEvent(WsEvent.GuildCreate, rawGuild: RawGuild) =>
      val (users, members) = rawGuild.members.get.map {
        case RawGuildMember(user, nick, roles, joinedAt, deaf, mute) =>
          (user.id -> user) -> (user.id -> GuildMember(user.id, nick, roles, joinedAt, deaf, mute))
      }.unzip

      val channels = rawGuild.channels.get.map {
        case RawGuildChannel(id, guildId, name, channelType, position, _, permissionOverwrites, topic, lastMessageId, bitrate, userLimit) =>
          channelType match {
            case "text"  => id -> TGuildChannel(id, guildId.getOrElse(rawGuild.id), name, position, permissionOverwrites, topic, lastMessageId)
            case "voice" => id -> VGuildChannel(id, guildId.getOrElse(rawGuild.id), name, position, permissionOverwrites, bitrate.get, userLimit.get)
          }
      }

      //Gets here are because everything should be sent here
      val guild = AvailableGuild(
        id = rawGuild.id,
        name = rawGuild.name,
        icon = rawGuild.icon,
        splash = rawGuild.splash,
        ownerId = rawGuild.ownerId,
        afkChannelId = rawGuild.afkChannelId,
        afkTimeout = rawGuild.afkTimeout,
        embedEnabled = rawGuild.embedEnabled,
        embedChannelId = rawGuild.embedChannelId,
        verificationLevel = rawGuild.verificationLevel,
        defaultMessageNotifications = rawGuild.defaultMessageNotifications,
        roles = rawGuild.roles.map(r => r.id   -> r).toMap,
        emojis = rawGuild.emojis.map(e => e.id -> e).toMap,
        mfaLevel = rawGuild.mfaLevel,
        joinedAt = rawGuild.joinedAt.get,
        large = rawGuild.large.get,
        memberCount = rawGuild.memberCount.get,
        voiceStates = rawGuild.voiceStates.get,
        members = members.toMap,
        channels = channels.toMap
      )

      _guilds.modify(_.put(rawGuild.id, guild))
      _users.modify(_ ++= users)

      updateSnapshot()
      eventStream.publish(APIMessage.GuildCreate(guild, snapshot))
    case RawWsEvent(WsEvent.GuildUpdate, rawGuild: RawGuild) =>
      guildUpdate(rawGuild.id, "guild") { guild =>
        val newGuild = guild.copy(
          id = rawGuild.id,
          name = rawGuild.name,
          icon = rawGuild.icon,
          splash = rawGuild.splash,
          ownerId = rawGuild.ownerId,
          afkChannelId = rawGuild.afkChannelId,
          afkTimeout = rawGuild.afkTimeout,
          embedEnabled = rawGuild.embedEnabled,
          embedChannelId = rawGuild.embedChannelId,
          verificationLevel = rawGuild.verificationLevel,
          defaultMessageNotifications = rawGuild.defaultMessageNotifications,
          roles = rawGuild.roles.map(r => r.id   -> r).toMap,
          emojis = rawGuild.emojis.map(e => e.id -> e).toMap,
          mfaLevel = rawGuild.mfaLevel
        )

        eventStream.publish(APIMessage.GuildUpdate(newGuild, snapshot))

        newGuild
      }
      updateSnapshot()

    case RawWsEvent(WsEvent.GuildDelete, GuildDeleteData(id, unavailable)) =>
      _guilds.modify { guilds =>
        guilds.get(id) match {
          case Some(guild) =>
            if (unavailable) {
              _unavailableGuilds.modify(_.put(id, UnavailableGuild(id)))
            }

            guilds.remove(id)

            eventStream.publish(APIMessage.GuildDelete(guild, unavailable, snapshot))
            updateSnapshot()
          case None => log.warning("Tried to delete unknown guild {}", id)
        }
      }
    case RawWsEvent(WsEvent.GuildBanAdd, user)    => ???
    case RawWsEvent(WsEvent.GuildBanRemove, user) => ???
    case RawWsEvent(WsEvent.GuildEmojisUpdate, GuildEmojisUpdateData(guildId, emojis)) =>
      guildUpdate(guildId, "emojis") { guild =>
        eventStream.publish(APIMessage.GuildEmojiUpdate(guild, emojis, snapshot))

        guild.copy(emojis = emojis.map(e => e.id -> e).toMap)
      }
      updateSnapshot()

    case RawWsEvent(WsEvent.GuildIntegrationsUpdate, GuildIntegrationsUpdateData(guildId)) => ???
    case RawWsEvent(WsEvent.GuildMemberAdd, RawGuildMemberWithGuild(user, guildId, nick, roles, joinedAt, deaf, mute)) =>
      val member = GuildMember(user.id, nick, roles, joinedAt, deaf, mute)

      _users.modify(_.put(user.id, user))
      guildUpdate(guildId, "guildMember") { guild =>
        guild.copy(members = guild.members + ((user.id, member)))
      }

      updateSnapshot()
      snapshot.getGuild(guildId).foreach(g => eventStream.publish(APIMessage.GuildMemberAdd(member, g, snapshot)))
    case RawWsEvent(WsEvent.GuildMemberRemove, GuildMemberRemoveData(guildId, user)) =>
      guildUpdate(guildId, "user") { guild =>
        eventStream.publish(APIMessage.GuildMemberRemove(user, guild, snapshot))
        guild.copy(members = guild.members - user.id)
      }
      updateSnapshot()
    case RawWsEvent(WsEvent.GuildMemberUpdate, GuildMemberUpdateData(guildId, roles, user, nick)) =>
      _users.modify(_.put(user.id, user))

      guildUpdate(guildId, "guildMember") { guild =>
        eventStream.publish(APIMessage.GuildMemberUpdate(guild, roles.flatMap(snapshot.getRole), user, nick, snapshot))

        guild.members.get(user.id) match {
          case Some(guildMember) =>
            guild.copy(members = guild.members + ((user.id, guildMember.copy(nick = nick, roles = roles))))
          case None =>
            log.warning("Received guildMember update for unknown user {}", user)
            guild
        }
      }

      updateSnapshot()
    case RawWsEvent(WsEvent.GuildMemberChunk, GuildMemberChunkData(guildId, newRawMembers)) =>
      val (newUsers, newMembers) = newRawMembers.map {
        case RawGuildMember(user, nick, roles, joinedAt, deaf, mute) => (user.id -> user) -> GuildMember(user.id, nick, roles, joinedAt, deaf, mute)
      }.unzip

      _users.modify(_ ++= newUsers)
      guildUpdate(guildId, "member chunk") { guild =>
        guild.copy(members = guild.members ++ newMembers.map(m => m.userId -> m))
      }

      updateSnapshot()
      snapshot.getGuild(guildId).foreach(g => eventStream.publish(APIMessage.GuildMembersChunk(g, newMembers, snapshot)))
    case RawWsEvent(WsEvent.GuildRoleCreate, GuildRoleModifyData(guildId, role)) =>
      guildUpdate(guildId, "role") { guild =>
        eventStream.publish(APIMessage.GuildRoleCreate(guild, role, snapshot))
        guild.copy(roles = guild.roles + ((role.id, role)))
      }

      updateSnapshot()
    case RawWsEvent(WsEvent.GuildRoleUpdate, GuildRoleModifyData(guildId, role)) =>
      guildUpdate(guildId, "role") { guild =>
        eventStream.publish(APIMessage.GuildRoleUpdate(guild, role, snapshot))
        guild.copy(roles = guild.roles + ((role.id, role)))
      }

      updateSnapshot()
    case RawWsEvent(WsEvent.GuildRoleDelete, GuildRoleDeleteData(guildId, roleId)) =>
      guildUpdate(guildId, "role") { guild =>
        eventStream.publish(APIMessage.GuildRoleDelete(guild, roleId, snapshot))
        guild.copy(roles = guild.roles - roleId)
      }

      updateSnapshot()
    case RawWsEvent(WsEvent.MessageCreate, rawMessage: RawMessage) =>
      val users = rawMessage.mentions.map(u => u.id -> u)
      _users.modify(_ ++= users)

      _messages.modify { allMessages =>
        allMessages(rawMessage.channelId).modify { channelMessages =>
          val message = Message(
            id = rawMessage.id,
            channelId = rawMessage.channelId,
            author = rawMessage.author,
            content = rawMessage.content,
            timestamp = rawMessage.timestamp,
            editedTimestamp = rawMessage.editedTimestamp,
            tts = rawMessage.tts,
            mentionEveryone = rawMessage.mentionEveryone,
            mentions = rawMessage.mentions.map(_.id),
            mentionRoles = rawMessage.mentionRoles,
            attachment = rawMessage.attachment,
            embeds = rawMessage.embeds,
            reactions = rawMessage.reactions.getOrElse(Seq.empty),
            nonce = rawMessage.nonce,
            pinned = rawMessage.pinned,
            webhookId = rawMessage.webhookId
          )

          channelMessages.put(message.id, message)

          eventStream.publish(APIMessage.MessageCreate(message, snapshot))
          updateSnapshot()
        }
      }
    case RawWsEvent(WsEvent.MessageUpdate, rawMessage: RawOptionalMessage) =>
      rawMessage.mentions.map(_.map(u => u.id -> u)).foreach(seq => _users.modify(_ ++= seq))

      _messages.modify { allMessages =>
        allMessages(rawMessage.channelId).modify { channelMessages =>
          channelMessages.get(rawMessage.id) match {
            case Some(message) =>
              val newMessage = message.copy(
                author = rawMessage.author.getOrElse(message.author),
                content = rawMessage.content.getOrElse(message.content),
                timestamp = rawMessage.timestamp.getOrElse(message.timestamp),
                editedTimestamp = rawMessage.editedTimestamp.orElse(message.editedTimestamp),
                tts = rawMessage.tts.getOrElse(message.tts),
                mentionEveryone = rawMessage.mentionEveryone.getOrElse(message.mentionEveryone),
                mentions = rawMessage.mentions.map(_.map(_.id)).getOrElse(message.mentions),
                mentionRoles = rawMessage.mentionRoles.getOrElse(message.mentionRoles),
                attachment = rawMessage.attachment.getOrElse(message.attachment),
                embeds = rawMessage.embeds.getOrElse(message.embeds),
                reactions = rawMessage.reactions.getOrElse(message.reactions),
                nonce = rawMessage.nonce.orElse(message.nonce),
                pinned = rawMessage.pinned.getOrElse(message.pinned),
                webhookId = rawMessage.webhookId.orElse(message.webhookId)
              )

              channelMessages.put(newMessage.id, newMessage)

              eventStream.publish(APIMessage.MessageUpdate(newMessage, snapshot))
              updateSnapshot()
            case None => log.warning("No message found for edit event {}", rawMessage.id)
          }
        }
      }
    case RawWsEvent(WsEvent.MessageDelete, MessageDeleteData(id, channelId)) =>
      _messages.modify { allMessages =>
        allMessages(channelId).modify { channelMessages =>
          channelMessages - id
        }
      }
      for {
        channel <- snapshot.getChannel(channelId)
        message <- snapshot.getMessage(channelId, id)
      } eventStream.publish(APIMessage.MessageDelete(message, channel, snapshot))
      updateSnapshot()
    case RawWsEvent(WsEvent.MessageDeleteBulk, MessageDeleteBulkData(ids, channelId)) =>
      _messages.modify { allMessages =>
        allMessages(channelId).modify { channelMessages =>
          channelMessages -- ids
        }
      }

      snapshot.getChannel(channelId)
        .foreach(channel => eventStream.publish(APIMessage.MessageDeleteBulk(ids.flatMap(snapshot.getMessage(channelId, _)), channel, snapshot)))
      updateSnapshot()
  }
}
object SnowflakeCache {
  def props(eventStream: EventStream): Props = Props(classOf[SnowflakeCache], eventStream)
}

sealed trait RawEvent
case class RawWsEvent(event: WsEvent, data: Any)
