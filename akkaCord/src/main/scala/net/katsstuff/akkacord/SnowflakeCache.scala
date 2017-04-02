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
import net.katsstuff.akkacord.http.websocket.WsEvent.{GuildDeleteData, GuildEmojisUpdateData, GuildIntegrationsUpdateData, GuildMemberChunkData, GuildMemberRemoveData, GuildMemberUpdateData, GuildRoleDeleteData, GuildRoleModifyData, MessageDeleteBulkData, MessageDeleteData, PresenceUpdateData, RawGuildMemberWithGuild, RawPartialMessage}
import net.katsstuff.akkacord.http.{RawChannel, RawDMChannel, RawGuild, RawGuildChannel, RawGuildMember, RawMessage, RawPresenceGame, RawUnavailableGuild}

class SnowflakeCache(eventStream: EventStream) extends Actor with ActorLogging {

  private var prevSnapshot: CacheSnapshot = _
  private var snapshot:     CacheSnapshot = _

  private def updateSnapshot(newSnapshot: CacheSnapshot): Unit = {
    prevSnapshot = snapshot
    snapshot = newSnapshot
  }

  private def isReady: Boolean = prevSnapshot != null && snapshot != null

  private def guildUpdate(id: Snowflake, updateType: String)(f: AvailableGuild => AvailableGuild): Unit =
    snapshot.getGuild(id) match {
      case Some(guild) =>
        updateSnapshot(snapshot.copy(guilds = snapshot.guilds + ((id, f(guild)))))
      case None => log.warning("Received {} for unknown guild {}", updateType, id)
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
        users = users.toMap,
        presences = Map().withDefaultValue(Map())
      )

      prevSnapshot = firstSnapshot
      snapshot = firstSnapshot

      eventStream.publish(APIMessage.Ready(snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.Resumed, _) if isReady =>
      eventStream.publish(APIMessage.Resumed(snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.ChannelCreate, rawChannel: RawChannel) if isReady =>
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
    case RawWsEvent(WsEvent.ChannelUpdate, rawChannel: RawGuildChannel) if isReady =>
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
    case RawWsEvent(WsEvent.ChannelDelete, rawChannel: RawChannel) if isReady =>
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

    case RawWsEvent(WsEvent.GuildCreate, rawGuild: RawGuild) if isReady =>
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

      val presences = rawGuild.presences.getOrElse(Seq.empty).flatMap { pres =>
        import shapeless.record._

        val status = pres.status.map {
          case "idle" => PresenceStatus.Idle
          case "online" => PresenceStatus.Online
          case "offline" => PresenceStatus.Offline
        }

        val content = pres.game.flatMap {
          case RawPresenceGame(Some(name), Some(0), url) => Some(PresenceGame(name))
          case RawPresenceGame(Some(name), Some(1), Some(url)) => Some(PresenceStreaming(name, url))
          case _ => None
        }

        status.map(s => Presence(pres.user.get('id), content, s))
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
        channels = channels.toMap,
        presences = presences
      )

      updateSnapshot(snapshot.copy(guilds = snapshot.guilds + ((guild.id, guild)), users = snapshot.users ++ users))
      eventStream.publish(APIMessage.GuildCreate(guild, snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.GuildUpdate, rawGuild: RawGuild) if isReady =>
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
          roles = rawGuild.roles.map(r => r.id   -> r).toMap,
          emojis = rawGuild.emojis.map(e => e.id -> e).toMap,
          mfaLevel = rawGuild.mfaLevel
        )
      }

      snapshot.getGuild(rawGuild.id).foreach(g => eventStream.publish(APIMessage.GuildUpdate(g, snapshot, prevSnapshot)))
    case RawWsEvent(WsEvent.GuildDelete, GuildDeleteData(id, unavailable)) if isReady =>
      if (unavailable) {
        updateSnapshot(snapshot.copy(guilds = snapshot.guilds - id, unavailableGuilds = snapshot.unavailableGuilds + ((id, UnavailableGuild(id)))))
      } else {
        updateSnapshot(snapshot.copy(guilds = snapshot.guilds - id))
      }

      prevSnapshot.getGuild(id).foreach(g => eventStream.publish(APIMessage.GuildDelete(g, unavailable, snapshot, prevSnapshot)))
    case RawWsEvent(WsEvent.GuildBanAdd, user) if isReady    => ???
    case RawWsEvent(WsEvent.GuildBanRemove, user) if isReady => ???
    case RawWsEvent(WsEvent.GuildEmojisUpdate, GuildEmojisUpdateData(guildId, emojis)) if isReady =>
      guildUpdate(guildId, "emojis") { guild =>
        guild.copy(emojis = emojis.map(e => e.id -> e).toMap)
      }

      snapshot.getGuild(guildId).foreach(g => eventStream.publish(APIMessage.GuildEmojiUpdate(g, emojis, snapshot, prevSnapshot)))
    case RawWsEvent(WsEvent.GuildIntegrationsUpdate, GuildIntegrationsUpdateData(guildId)) if isReady => ???
    case RawWsEvent(WsEvent.GuildMemberAdd, withGuild: RawGuildMemberWithGuild @unchecked) if isReady =>
      val RawGuildMember(user, nick, roles, joinedAt, deaf, mute) = WsEvent.guildMemberGen.from(withGuild.tail)
      import shapeless.record._
      val guildId = withGuild.get('guildId)
      val member = GuildMember(user.id, nick, roles, joinedAt, deaf, mute)

      snapshot.getGuild(guildId).foreach { guild =>
        val newGuild = guild.copy(members = guild.members + ((user.id, member)))
        updateSnapshot(snapshot.copy(users = snapshot.users + ((user.id, user)), guilds = snapshot.guilds + ((guildId, newGuild))))
        eventStream.publish(APIMessage.GuildMemberAdd(member, guild, snapshot, prevSnapshot))
      }
    case RawWsEvent(WsEvent.GuildMemberRemove, GuildMemberRemoveData(guildId, user)) if isReady =>
      guildUpdate(guildId, "user") { guild =>
        guild.copy(members = guild.members - user.id)
      }

      snapshot.getGuild(guildId).foreach(g => eventStream.publish(APIMessage.GuildMemberRemove(user, g, snapshot, prevSnapshot)))
    case RawWsEvent(WsEvent.GuildMemberUpdate, GuildMemberUpdateData(guildId, roles, user, nick)) if isReady =>
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
    case RawWsEvent(WsEvent.GuildMemberChunk, GuildMemberChunkData(guildId, newRawMembers)) if isReady =>
      val (newUsers, newMembers) = newRawMembers.map {
        case RawGuildMember(user, nick, roles, joinedAt, deaf, mute) => (user.id -> user) -> GuildMember(user.id, nick, roles, joinedAt, deaf, mute)
      }.unzip

      snapshot.getGuild(guildId).foreach { guild =>
        val newGuild = guild.copy(members = guild.members ++ newMembers.map(m => m.userId -> m))
        updateSnapshot(snapshot.copy(users = snapshot.users ++ newUsers, guilds = snapshot.guilds + ((guildId, newGuild))))
        eventStream.publish(APIMessage.GuildMembersChunk(guild, newMembers, snapshot, prevSnapshot))
      }
    case RawWsEvent(WsEvent.GuildRoleCreate, GuildRoleModifyData(guildId, role)) if isReady =>
      guildUpdate(guildId, "role") { guild =>
        eventStream.publish(APIMessage.GuildRoleCreate(guild, role, snapshot, prevSnapshot))
        guild.copy(roles = guild.roles + ((role.id, role)))
      }

    case RawWsEvent(WsEvent.GuildRoleUpdate, GuildRoleModifyData(guildId, role)) if isReady =>
      guildUpdate(guildId, "role") { guild =>
        eventStream.publish(APIMessage.GuildRoleUpdate(guild, role, snapshot, prevSnapshot))
        guild.copy(roles = guild.roles + ((role.id, role)))
      }

    case RawWsEvent(WsEvent.GuildRoleDelete, GuildRoleDeleteData(guildId, roleId)) if isReady =>
      guildUpdate(guildId, "role") { guild =>
        eventStream.publish(APIMessage.GuildRoleDelete(guild, roleId, snapshot, prevSnapshot))
        guild.copy(roles = guild.roles - roleId)
      }

    case RawWsEvent(WsEvent.MessageCreate, rawMessage: RawMessage) if isReady =>
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
      val newMessages        = snapshot.messages + ((rawMessage.channelId, newChannelMessages))
      updateSnapshot(snapshot.copy(users = snapshot.users ++ users, messages = newMessages))
      eventStream.publish(APIMessage.MessageCreate(message, snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.MessageUpdate, rawMessage: RawPartialMessage) if isReady =>
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
        val newMessages        = snapshot.messages + ((rawMessage.channelId, newChannelMessages))
        updateSnapshot(snapshot.copy(users = snapshot.users ++ newUsers, messages = newMessages))
        eventStream.publish(APIMessage.MessageUpdate(newMessage, snapshot, prevSnapshot))
      }
    case RawWsEvent(WsEvent.MessageDelete, MessageDeleteData(id, channelId)) if isReady =>
      updateSnapshot(snapshot.copy(messages = snapshot.messages + ((channelId, snapshot.messages(channelId) - id))))

      for {
        channel <- snapshot.getChannel(channelId)
        message <- snapshot.getMessage(channelId, id)
      } eventStream.publish(APIMessage.MessageDelete(message, channel, snapshot, prevSnapshot))
    case RawWsEvent(WsEvent.MessageDeleteBulk, MessageDeleteBulkData(ids, channelId)) if isReady =>
      updateSnapshot(snapshot.copy(messages = snapshot.messages + ((channelId, snapshot.messages(channelId) -- ids))))

      snapshot
        .getChannel(channelId)
        .foreach(ch => eventStream.publish(APIMessage.MessageDeleteBulk(ids.flatMap(snapshot.getMessage(channelId, _)), ch, snapshot, prevSnapshot)))
    case RawWsEvent(WsEvent.PresenceUpdate, PresenceUpdateData(partialUser, roles, game, optGuildId, status)) if isReady =>
      optGuildId match {
        case Some(guildId) if snapshot.guilds.contains(guildId) =>
          import shapeless.record._
          val rPartialUser = partialUser.record
          val newUser = snapshot.getUser(rPartialUser.id) match {
            case Some(existingUser) =>
              Some(
                existingUser.copy(
                  username = rPartialUser.username.getOrElse(existingUser.username),
                  discriminator = rPartialUser.discriminator.getOrElse(existingUser.discriminator),
                  avatar = rPartialUser.avatar.getOrElse(existingUser.avatar),
                  bot = rPartialUser.bot.orElse(existingUser.bot),
                  mfaEnabled = rPartialUser.mfaEnabled.orElse(existingUser.mfaEnabled),
                  verified = rPartialUser.verified.orElse(existingUser.verified),
                  email = rPartialUser.email.orElse(existingUser.email)
                )
              )

            case None =>
              //Let's try to create a user
              for {
                username      <- rPartialUser.username
                discriminator <- rPartialUser.discriminator
                avatar        <- rPartialUser.avatar
              } yield
                User(
                  rPartialUser.id,
                  username,
                  discriminator,
                  avatar,
                  rPartialUser.bot,
                  rPartialUser.mfaEnabled,
                  rPartialUser.verified,
                  rPartialUser.email
                )
          }

          val optNewPresence = snapshot.getPresence(guildId, rPartialUser.id) match {
            case Some(presence) =>
              val newPresence = game
                .map {
                  case RawPresenceGame(name, gameType, url) =>
                    val newName = name.orElse(presence.game.map(_.name))
                    val content = newName.flatMap { name =>
                      gameType.flatMap {
                        case 0 => Some(PresenceGame(name))
                        case 1 => url.map(PresenceStreaming(name, _))
                      }
                    }

                    val newStatus = status
                      .map {
                        case "online"  => PresenceStatus.Online
                        case "idle"    => PresenceStatus.Idle
                        case "offline" => PresenceStatus.Offline
                      }
                      .getOrElse(presence.status)

                    Presence(rPartialUser.id, content, newStatus)
                }
                .getOrElse(presence)

              Some(newPresence)
            case None =>
              game
                .flatMap {
                  case RawPresenceGame(name, gameType, url) =>
                    val content = name.flatMap { name =>
                      gameType.flatMap {
                        case 0 => Some(PresenceGame(name))
                        case 1 => url.map(PresenceStreaming(name, _))
                      }
                    }

                    val newStatus = status
                      .map {
                        case "online"  => PresenceStatus.Online
                        case "idle"    => PresenceStatus.Idle
                        case "offline" => PresenceStatus.Offline
                      }

                    newStatus.map(Presence(rPartialUser.id, content, _))
                }
          }

          val guild = snapshot.guilds(guildId)
          val withNewUser = newUser.map(u => snapshot.copy(users = snapshot.users + ((u.id, u)))).getOrElse(snapshot)

          val withUpdatedRoles = {
            val newMembers = guild.members.get(rPartialUser.id).map(m => guild.members + ((rPartialUser.id, m.copy(roles = roles))))
              .getOrElse(guild.members)
            val newGuild = guild.copy(members = newMembers)
            withNewUser.copy(guilds = withNewUser.guilds + ((guildId, newGuild)))
          }

          val withNewPresence = optNewPresence.map { newPresence =>
            val newGuildPresences = withUpdatedRoles.presences(guildId) + ((rPartialUser.id, newPresence))
            withUpdatedRoles.copy(presences = withUpdatedRoles.presences + ((guildId, newGuildPresences)))
          }.getOrElse(withUpdatedRoles)

          //Did I miss anything here?
          updateSnapshot(withNewPresence)

        case _ => //TODO: What to do if no guild id?
      }
    case RawWsEvent(_, _) if !isReady => log.error("Received event before ready")
  }
}
object SnowflakeCache {
  def props(eventStream: EventStream): Props = Props(classOf[SnowflakeCache], eventStream)
}

sealed trait RawEvent
case class RawWsEvent[Data](event: WsEvent[Data], data: Data)
