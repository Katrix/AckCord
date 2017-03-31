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

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.EventStream
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.http.websocket.WsEvent
import net.katsstuff.akkacord.http.websocket.WsEvent.{GuildDeleteData, GuildEmojisUpdateData, GuildIntegrationsUpdateData, GuildMemberChunkData, GuildMemberRemoveData, GuildMemberUpdateData, GuildRoleDeleteData, GuildRoleModifyData, MessageDeleteBulkData, MessageDeleteData, RawGuildMemberWithGuild, RawOptionalMessage}
import net.katsstuff.akkacord.http.{RawChannel, RawDMChannel, RawGuild, RawGuildChannel, RawGuildMember, RawMessage, RawUnavailableGuild}

class SnowflakeCache(eventStream: EventStream) extends Actor with ActorLogging {

  private var prevSnapshot: CacheSnapshot = _
  private var snapshot:     CacheSnapshot = _

  private def updateSnapshot(newSnapshot: CacheSnapshot): Unit = {
    prevSnapshot = snapshot
    snapshot = newSnapshot
  }

  private def guildUpdate(id: Snowflake, updateType: String)(f: AvailableGuild => AvailableGuild): Unit = {
    snapshot.getGuild(id) match {
      case Some(guild) =>
        updateSnapshot(snapshot.copy(guilds = snapshot.guilds + ((id, f(guild)))))
      case None        => log.warning("Received {} for unknown guild {}", updateType, id)
    }
  }

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

      val firstSnapshot = CacheSnapshot(
        botUser = user,
        dmChannels = dmChannels.toMap,
        unavailableGuilds = guilds.toMap,
        guilds = Map(),
        messages = Map().withDefaultValue(Map()),
        lastTyped = Map().withDefaultValue(Map()),
        users = users.toMap
      )

      prevSnapshot = firstSnapshot
      snapshot = firstSnapshot

      eventStream.publish(APIMessage.Ready(snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.Resumed, _) =>
      eventStream.publish(APIMessage.Resumed(snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.ChannelCreate, rawChannel: RawChannel) =>
      //The guild id should always be present
      (rawChannel: @unchecked) match {
        case RawGuildChannel(id, Some(guildId), name, channelType, position, _, permissionOverwrites, topic, lastMessageId, bitrate, userLimit) =>
          val channel = channelType match {
            case "text"  => TGuildChannel(id, guildId, name, position, permissionOverwrites, topic, lastMessageId)
            case "voice" => VGuildChannel(id, guildId, name, position, permissionOverwrites, bitrate.get, userLimit.get)
          }

          guildUpdate(guildId, "channel") { guild =>
            guild.copy(channels = guild.channels + ((id, channel)))
          }

          eventStream.publish(APIMessage.ChannelCreate(channel, snapshot, prevSnapshot))
        case RawDMChannel(id, _, recipient, lastMessageId) =>
          val channel = DMChannel(id, lastMessageId, recipient.id)

          updateSnapshot(snapshot.copy(dmChannels = snapshot.dmChannels + ((id, channel)), users = snapshot.users + ((recipient.id, recipient))))
          eventStream.publish(APIMessage.ChannelCreate(channel, snapshot, prevSnapshot))
      }
    case RawWsEvent(WsEvent.ChannelUpdate, rawChannel: RawGuildChannel) =>
      val RawGuildChannel(id, Some(guildId), name, channelType, position, _, permissionOverwrites, topic, lastMessageId, bitrate, userLimit) =
        rawChannel

      val channel = channelType match {
        case "text"  => TGuildChannel(id, guildId, name, position, permissionOverwrites, topic, lastMessageId)
        case "voice" => VGuildChannel(id, guildId, name, position, permissionOverwrites, bitrate.get, userLimit.get)
      }

      guildUpdate(guildId, "channel") { guild =>
        guild.copy(channels = guild.channels + ((id, channel)))
      }

      eventStream.publish(APIMessage.ChannelUpdate(channel, snapshot, prevSnapshot))
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

          eventStream.publish(APIMessage.ChannelDelete(channel, snapshot, prevSnapshot))
        case RawDMChannel(id, _, recipient, lastMessageId) =>
          val channel = DMChannel(id, lastMessageId, recipient.id)

          //Should I still update the user here?
          updateSnapshot(snapshot.copy(dmChannels = snapshot.dmChannels - id, users = snapshot.users + ((recipient.id, recipient))))
          eventStream.publish(APIMessage.ChannelDelete(channel, snapshot, prevSnapshot))
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

      updateSnapshot(snapshot.copy(guilds = snapshot.guilds + ((guild.id, guild)), users = snapshot.users ++ users))
      eventStream.publish(APIMessage.GuildCreate(guild, snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.GuildUpdate, rawGuild: RawGuild) =>
      guildUpdate(rawGuild.id, "guild") { guild =>
        guild.copy(
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
          roles = rawGuild.roles.map(r => r.id -> r).toMap,
          emojis = rawGuild.emojis.map(e => e.id -> e).toMap,
          mfaLevel = rawGuild.mfaLevel
        )
      }

      snapshot.getGuild(rawGuild.id).foreach(g => eventStream.publish(APIMessage.GuildUpdate(g, snapshot, prevSnapshot)))
    case RawWsEvent(WsEvent.GuildDelete, GuildDeleteData(id, unavailable)) =>
      if(unavailable) {
        updateSnapshot(snapshot.copy(guilds = snapshot.guilds - id, unavailableGuilds = snapshot.unavailableGuilds + ((id, UnavailableGuild(id)))))
      }
      else {
        updateSnapshot(snapshot.copy(guilds = snapshot.guilds - id))
      }

      prevSnapshot.getGuild(id).foreach(g => eventStream.publish(APIMessage.GuildDelete(g, unavailable, snapshot, prevSnapshot)))
    case RawWsEvent(WsEvent.GuildBanAdd, user)    => ???
    case RawWsEvent(WsEvent.GuildBanRemove, user) => ???
    case RawWsEvent(WsEvent.GuildEmojisUpdate, GuildEmojisUpdateData(guildId, emojis)) =>
      guildUpdate(guildId, "emojis") { guild =>
        guild.copy(emojis = emojis.map(e => e.id -> e).toMap)
      }

      snapshot.getGuild(guildId).foreach(g => eventStream.publish(APIMessage.GuildEmojiUpdate(g, emojis, snapshot, prevSnapshot)))
    case RawWsEvent(WsEvent.GuildIntegrationsUpdate, GuildIntegrationsUpdateData(guildId)) => ???
    case RawWsEvent(WsEvent.GuildMemberAdd, RawGuildMemberWithGuild(user, guildId, nick, roles, joinedAt, deaf, mute)) =>
      val member = GuildMember(user.id, nick, roles, joinedAt, deaf, mute)

      snapshot.getGuild(guildId).foreach { guild =>
        val newGuild = guild.copy(members = guild.members + ((user.id, member)))
        updateSnapshot(snapshot.copy(users = snapshot.users + ((user.id, user)), guilds = snapshot.guilds + ((guildId, newGuild))))
        eventStream.publish(APIMessage.GuildMemberAdd(member, guild, snapshot, prevSnapshot))
      }
    case RawWsEvent(WsEvent.GuildMemberRemove, GuildMemberRemoveData(guildId, user)) =>
      guildUpdate(guildId, "user") { guild =>
        guild.copy(members = guild.members - user.id)
      }

      snapshot.getGuild(guildId).foreach(g => eventStream.publish(APIMessage.GuildMemberRemove(user, g, snapshot, prevSnapshot)))
    case RawWsEvent(WsEvent.GuildMemberUpdate, GuildMemberUpdateData(guildId, roles, user, nick)) =>
      snapshot.getGuild(guildId).foreach { guild =>
        val newGuild = guild.members.get(user.id) match {
          case Some(guildMember) =>
            guild.copy(members = guild.members + ((user.id, guildMember.copy(nick = nick, roles = roles))))
          case None =>
            log.warning("Received guildMember update for unknown user {}", user)
            guild
        }

        eventStream.publish(APIMessage.GuildMemberUpdate(guild, roles.flatMap(snapshot.getRole), user, nick, snapshot, prevSnapshot))
        updateSnapshot(snapshot.copy(users = snapshot.users + ((user.id, user)), guilds = snapshot.guilds + ((guildId, newGuild))))
      }
    case RawWsEvent(WsEvent.GuildMemberChunk, GuildMemberChunkData(guildId, newRawMembers)) =>
      val (newUsers, newMembers) = newRawMembers.map {
        case RawGuildMember(user, nick, roles, joinedAt, deaf, mute) => (user.id -> user) -> GuildMember(user.id, nick, roles, joinedAt, deaf, mute)
      }.unzip

      snapshot.getGuild(guildId).foreach { guild =>
        val newGuild = guild.copy(members = guild.members ++ newMembers.map(m => m.userId -> m))
        updateSnapshot(snapshot.copy(users = snapshot.users ++ newUsers, guilds = snapshot.guilds + ((guildId, newGuild))))
        eventStream.publish(APIMessage.GuildMembersChunk(guild, newMembers, snapshot, prevSnapshot))
      }
    case RawWsEvent(WsEvent.GuildRoleCreate, GuildRoleModifyData(guildId, role)) =>
      guildUpdate(guildId, "role") { guild =>
        eventStream.publish(APIMessage.GuildRoleCreate(guild, role, snapshot, prevSnapshot))
        guild.copy(roles = guild.roles + ((role.id, role)))
      }

    case RawWsEvent(WsEvent.GuildRoleUpdate, GuildRoleModifyData(guildId, role)) =>
      guildUpdate(guildId, "role") { guild =>
        eventStream.publish(APIMessage.GuildRoleUpdate(guild, role, snapshot, prevSnapshot))
        guild.copy(roles = guild.roles + ((role.id, role)))
      }

    case RawWsEvent(WsEvent.GuildRoleDelete, GuildRoleDeleteData(guildId, roleId)) =>
      guildUpdate(guildId, "role") { guild =>
        eventStream.publish(APIMessage.GuildRoleDelete(guild, roleId, snapshot, prevSnapshot))
        guild.copy(roles = guild.roles - roleId)
      }

    case RawWsEvent(WsEvent.MessageCreate, rawMessage: RawMessage) =>
      val users = rawMessage.mentions.map(u => u.id -> u)
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

      val newChannelMessages = snapshot.messages(rawMessage.channelId) + ((message.id, message))
      val newMessages = snapshot.messages + ((rawMessage.channelId, newChannelMessages))
      updateSnapshot(snapshot.copy(users = snapshot.users ++ users, messages = newMessages))
      eventStream.publish(APIMessage.MessageCreate(message, snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.MessageUpdate, rawMessage: RawOptionalMessage) =>
      val newUsers = rawMessage.mentions.getOrElse(Seq.empty).map(u => u.id -> u)
      snapshot.getMessage(rawMessage.channelId, rawMessage.id).foreach { message =>
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

        val newChannelMessages = snapshot.messages(rawMessage.channelId) + ((rawMessage.id, newMessage))
        val newMessages = snapshot.messages + ((rawMessage.channelId, newChannelMessages))
        updateSnapshot(snapshot.copy(users = snapshot.users ++ newUsers, messages = newMessages))
        eventStream.publish(APIMessage.MessageUpdate(newMessage, snapshot, prevSnapshot))
      }
    case RawWsEvent(WsEvent.MessageDelete, MessageDeleteData(id, channelId)) =>
      updateSnapshot(snapshot.copy(messages = snapshot.messages + ((channelId, snapshot.messages(channelId) - id))))

      for {
        channel <- snapshot.getChannel(channelId)
        message <- snapshot.getMessage(channelId, id)
      } eventStream.publish(APIMessage.MessageDelete(message, channel, snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.MessageDeleteBulk, MessageDeleteBulkData(ids, channelId)) =>
      updateSnapshot(snapshot.copy(messages = snapshot.messages + ((channelId, snapshot.messages(channelId) -- ids))))

      snapshot
        .getChannel(channelId)
        .foreach(ch => eventStream.publish(APIMessage.MessageDeleteBulk(ids.flatMap(snapshot.getMessage(channelId, _)), ch, snapshot, prevSnapshot)))
  }
}
object SnowflakeCache {
  def props(eventStream: EventStream): Props = Props(classOf[SnowflakeCache], eventStream)
}

sealed trait RawEvent
case class RawWsEvent(event: WsEvent, data: Any)
