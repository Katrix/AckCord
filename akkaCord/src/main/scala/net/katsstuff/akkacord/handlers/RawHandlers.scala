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
package net.katsstuff.akkacord.handlers

import scala.collection.mutable

import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.http.websocket.WsEvent._
import net.katsstuff.akkacord.http.{RawChannel, RawGuild, RawGuildMember, RawMessage, RawPresenceGame}

object RawHandlers extends Handlers {

  import CacheDeleteHandler._
  import CacheUpdateHandler._

  implicit val rawChannelUpdateHandler: CacheUpdateHandler[RawChannel] = updateHandler {
    case (builder, rawChannel: RawChannel, log) =>
      rawChannel.`type` match {
        case ChannelType.GuildText =>
          for {
            guildId              <- rawChannel.guildId
            name                 <- rawChannel.name
            position             <- rawChannel.position
            permissionOverwrites <- rawChannel.permissionOverwrites
          } {
            val c: GuildChannel = TGuildChannel(rawChannel.id, guildId, name, position, permissionOverwrites, rawChannel.topic, rawChannel.lastMessageId, rawChannel.nsfw.getOrElse(false), rawChannel.parentId)
            handleUpdateLog(builder, c, log)
          }
        case ChannelType.GuildVoice =>
          for {
            guildId              <- rawChannel.guildId
            name                 <- rawChannel.name
            position             <- rawChannel.position
            permissionOverwrites <- rawChannel.permissionOverwrites
            bitRate              <- rawChannel.bitrate
            userLimit            <- rawChannel.userLimit
          } {
            val c: GuildChannel = VGuildChannel(rawChannel.id, guildId, name, position, permissionOverwrites, bitRate, userLimit, rawChannel.nsfw.getOrElse(false), rawChannel.parentId)
            handleUpdateLog(builder, c, log)
          }
        case ChannelType.GuildCategory =>
          for {
            guildId              <- rawChannel.guildId
            name                 <- rawChannel.name
            position             <- rawChannel.position
            permissionOverwrites <- rawChannel.permissionOverwrites
          } {
            val c: GuildChannel = GuildCategory(rawChannel.id, guildId, name, position, permissionOverwrites, rawChannel.nsfw.getOrElse(false), rawChannel.parentId)
            handleUpdateLog(builder, c, log)
          }
        case ChannelType.DM =>
          rawChannel.recipients
            .flatMap(_.headOption)
            .foreach(user => handleUpdateLog(builder, DMChannel(rawChannel.id, rawChannel.lastMessageId, user.id), log))
        case ChannelType.GroupDm =>
          for {
            name    <- rawChannel.name
            ownerId <- rawChannel.ownerId
            users   <- rawChannel.recipients
          } {
            val c = GroupDMChannel(rawChannel.id, name, users.map(_.id), rawChannel.lastMessageId, ownerId, rawChannel.applicationId, rawChannel.icon)
            handleUpdateLog(builder, c, log)
          }
      }
  }

  implicit val rawGuildUpdateHandler: CacheUpdateHandler[RawGuild] = updateHandler { (builder, obj, log) =>
    val rawMembers  = obj.members.getOrElse(Seq.empty)
    val rawChannels = obj.channels.getOrElse(Seq.empty)

    val (users, members) = rawMembers.map {
      case RawGuildMember(user, nick, roles, joinedAt, deaf, mute) =>
        user -> (user.id -> GuildMember(user.id, obj.id, nick, roles, joinedAt, deaf, mute))
    }.unzip

    val presences = obj.presences.getOrElse(Seq.empty).flatMap { pres =>
      val content = pres.game.flatMap {
        case RawPresenceGame(name, 0, _)         => Some(PresenceGame(name))
        case RawPresenceGame(name, 1, Some(url)) => Some(PresenceStreaming(name, url))
        case _                                   => None
      }

      pres.status.map(s => Presence(pres.user.id, content, s))
    }

    val oldGuild = builder.getGuild(obj.id)

    //Gets here are because everything should be sent here
    val guild = Guild(
      id = obj.id,
      name = obj.name,
      icon = obj.icon,
      splash = obj.splash,
      ownerId = obj.ownerId,
      region = obj.region,
      afkChannelId = obj.afkChannelId,
      afkTimeout = obj.afkTimeout,
      embedEnabled = obj.embedEnabled,
      embedChannelId = obj.embedChannelId,
      verificationLevel = obj.verificationLevel,
      defaultMessageNotifications = obj.defaultMessageNotifications,
      explicitContentFilter = obj.explicitContentFilter,
      roles = obj.roles.map(r => r.id   -> r).toMap,
      emojis = obj.emojis.map(e => e.id -> e).toMap,
      features = obj.features,
      mfaLevel = obj.mfaLevel,
      applicationId = obj.applicationId,
      widgetEnabled = obj.widgetEnabled,
      widgetChannelId = obj.widgetChannelId,
      joinedAt = obj.joinedAt.orElse(oldGuild.map(_.joinedAt)).get,
      large = obj.large.orElse(oldGuild.map(_.large)).get,
      memberCount = obj.memberCount.orElse(oldGuild.map(_.memberCount)).get,
      voiceStates = obj.voiceStates.orElse(oldGuild.map(_.voiceStates)).get,
      members = members.toMap,
      channels = Map.empty,
      presences = presences.map(p => p.userId -> p).toMap
    )

    handleUpdateLog(builder, guild, log)
    handleUpdateLog(builder, rawChannels.map(_.copy(guildId = Some(guild.id))), log)
    handleUpdateLog(builder, users, log)
  }

  implicit val guildEmojisUpdateDataHandler: CacheUpdateHandler[GuildEmojisUpdateData] = updateHandler {
    case (builder, obj @ GuildEmojisUpdateData(guildId, emojis), log) =>
      builder.getGuild(guildId) match {
        case Some(guild) =>
          builder.guilds.put(guildId, guild.copy(emojis = emojis.map(e => e.id -> e).toMap))
        case None => log.warning(s"Can't find guild for emojis update $obj")
      }
  }

  implicit val rawGuildMemberWithGuildUpdateHandler: CacheUpdateHandler[RawGuildMemberWithGuild] = updateHandler { (builder, obj, log) =>
    val RawGuildMemberWithGuild(guildId, user, nick, roles, joinedAt, deaf, mute) = obj
    val member                                                                    = GuildMember(user.id, obj.guildId, nick, roles, joinedAt, deaf, mute)

    builder.getGuild(guildId) match {
      case Some(guild) => builder.guilds.put(guildId, guild.copy(members = guild.members + ((user.id, member))))
      case None        => log.warning(s"Can't find guild for guildMember update $obj")
    }

    handleUpdateLog(builder, user, log)
  }

  implicit val rawGuildMemberChunkHandler: CacheUpdateHandler[GuildMemberChunkData] = updateHandler {
    case (builder, obj @ GuildMemberChunkData(guildId, newRawMembers), log) =>
      val (newUsers, newMembers) = newRawMembers.map {
        case RawGuildMember(user, nick, roles, joinedAt, deaf, mute) => user -> GuildMember(user.id, guildId, nick, roles, joinedAt, deaf, mute)
      }.unzip

      builder.getGuild(guildId) match {
        case Some(guild) =>
          val newGuild = guild.copy(members = guild.members ++ newMembers.map(m => m.userId -> m))
          builder.guilds.put(guildId, newGuild)
        case None => log.warning(s"Can't find guild for guildMember update $obj")
      }

      handleUpdateLog(builder, newUsers, log)
  }

  implicit val rawGuildMemberUpdateHandler: CacheUpdateHandler[GuildMemberUpdateData] = updateHandler {
    case (builder, obj @ GuildMemberUpdateData(guildId, roles, user, nick), log) =>
      builder.getGuild(guildId) match {
        case Some(guild) =>
          guild.members.get(user.id) match {
            case Some(guildMember) =>
              builder.guilds.put(guildId, guild.copy(members = guild.members + ((user.id, guildMember.copy(nick = nick, roles = roles)))))
            case None =>
              log.warning(s"Can't find user for user update $obj")
          }
        case None => log.warning(s"Can't find guild for user update $obj")
      }

      handleUpdateLog(builder, user, log)
  }

  implicit val roleUpdateHandler: CacheUpdateHandler[GuildRoleModifyData] = updateHandler {
    case (builder, obj @ GuildRoleModifyData(guildId, role), log) =>
      builder.getGuild(guildId) match {
        case Some(guild) => builder.guilds.put(guildId, guild.copy(roles = guild.roles + ((role.id, role))))
        case None        => log.warning(s"No guild found for role update $obj")
      }
  }

  implicit val rawMessageUpdateHandler: CacheUpdateHandler[RawMessage] = updateHandler { (builder, obj, log) =>
    val users = obj.mentions
    val message = Message(
      id = obj.id,
      channelId = obj.channelId,
      author = obj.author,
      content = obj.content,
      timestamp = obj.timestamp,
      editedTimestamp = obj.editedTimestamp,
      tts = obj.tts,
      mentionEveryone = obj.mentionEveryone,
      mentions = obj.mentions.map(_.id),
      mentionRoles = obj.mentionRoles,
      attachment = obj.attachment,
      embeds = obj.embeds,
      reactions = obj.reactions.getOrElse(Seq.empty),
      nonce = obj.nonce,
      pinned = obj.pinned,
      webhookId = obj.webhookId,
      messageType = obj.`type`
    )

    builder.messages.getOrElseUpdate(obj.channelId, mutable.Map.empty).put(message.id, message)
    handleUpdateLog(builder, users, log)
  }

  implicit val rawPartialMessageUpdateHandler: CacheUpdateHandler[RawPartialMessage] = updateHandler { (builder, obj, log) =>
    val newUsers = obj.mentions.getOrElse(Seq.empty)

    builder.getMessage(obj.channelId, obj.id).map { message =>
      val newMessage = message.copy(
        author = obj.author.getOrElse(message.author),
        content = obj.content.getOrElse(message.content),
        timestamp = obj.timestamp.getOrElse(message.timestamp),
        editedTimestamp = obj.editedTimestamp.orElse(message.editedTimestamp),
        tts = obj.tts.getOrElse(message.tts),
        mentionEveryone = obj.mentionEveryone.getOrElse(message.mentionEveryone),
        mentions = obj.mentions.map(_.map(_.id)).getOrElse(message.mentions),
        mentionRoles = obj.mentionRoles.getOrElse(message.mentionRoles),
        attachment = obj.attachment.getOrElse(message.attachment),
        embeds = obj.embeds.getOrElse(message.embeds),
        reactions = obj.reactions.getOrElse(message.reactions),
        nonce = obj.nonce.orElse(message.nonce),
        pinned = obj.pinned.getOrElse(message.pinned),
        webhookId = obj.webhookId.orElse(message.webhookId)
      )

      builder.messages.getOrElseUpdate(obj.channelId, mutable.Map.empty).put(message.id, newMessage)
    }

    handleUpdateLog(builder, newUsers, log)
  }

  implicit val lastTypedHandler: CacheUpdateHandler[TypingStartData] = updateHandler { (builder, obj, log) =>
    builder.getChannelLastTyped(obj.channelId).put(obj.userId, obj.timestamp)
  }

  implicit val rawMessageReactionUpdateHandler: CacheUpdateHandler[MessageReactionData] = updateHandler { (builder, obj, _) =>
    builder.getMessage(obj.channelId, obj.messageId).foreach { message =>
      val (toChange, toNotChange) = message.reactions.partition(_.emoji == obj.emoji)
      val changed = toChange.map { e =>
        e.copy(count = e.count + 1, me = if (builder.botUser.id == obj.userId) true else e.me)
      }

      val newMessage = message.copy(reactions = toNotChange ++ changed)

      builder.getChannelMessages(obj.channelId).put(obj.messageId, newMessage)
    }
  }

  //Delete
  implicit val rawChannelDeleteHandler: CacheDeleteHandler[RawChannel] = deleteHandler { (builder, rawChannel, log) =>
    rawChannel.`type` match {
      case ChannelType.GuildText =>
        for {
          guildId              <- rawChannel.guildId
          name                 <- rawChannel.name
          position             <- rawChannel.position
          permissionOverwrites <- rawChannel.permissionOverwrites
        } {
          val c: GuildChannel = TGuildChannel(rawChannel.id, guildId, name, position, permissionOverwrites, rawChannel.topic, rawChannel.lastMessageId, rawChannel.nsfw.getOrElse(false), rawChannel.parentId)
          handleDeleteLog(builder, c, log)
        }
      case ChannelType.GuildVoice =>
        for {
          guildId              <- rawChannel.guildId
          name                 <- rawChannel.name
          position             <- rawChannel.position
          permissionOverwrites <- rawChannel.permissionOverwrites
          bitRate              <- rawChannel.bitrate
          userLimit            <- rawChannel.userLimit
        } {
          val c: GuildChannel = VGuildChannel(rawChannel.id, guildId, name, position, permissionOverwrites, bitRate, userLimit, rawChannel.nsfw.getOrElse(false), rawChannel.parentId)
          handleDeleteLog(builder, c, log)
        }
      case ChannelType.GuildCategory =>
        for {
          guildId              <- rawChannel.guildId
          name                 <- rawChannel.name
          position             <- rawChannel.position
          permissionOverwrites <- rawChannel.permissionOverwrites
        } {
          val c: GuildChannel = GuildCategory(rawChannel.id, guildId, name, position, permissionOverwrites, rawChannel.nsfw.getOrElse(false), rawChannel.parentId)
          handleDeleteLog(builder, c, log)
        }
      case ChannelType.DM =>
        rawChannel.recipients
          .flatMap(_.headOption)
          .foreach(user => handleDeleteLog(builder, DMChannel(rawChannel.id, rawChannel.lastMessageId, user.id), log))
      case ChannelType.GroupDm =>
        for {
          name    <- rawChannel.name
          ownerId <- rawChannel.ownerId
          users   <- rawChannel.recipients
        } {
          val c = GroupDMChannel(rawChannel.id, name, users.map(_.id), rawChannel.lastMessageId, ownerId, rawChannel.applicationId, rawChannel.icon)
          handleDeleteLog(builder, c, log)
        }
    }
  }

  implicit val deleteGuildDataHandler: CacheDeleteHandler[UnavailableGuild] = deleteHandler {
    case (builder, g @ UnavailableGuild(id, unavailable), _) =>
      builder.guilds.remove(id)
      if (unavailable) {
        builder.unavailableGuilds.put(id, g)
      }
  }

  implicit val rawGuildMemberDeleteHandler: CacheDeleteHandler[GuildMemberRemoveData] = deleteHandler {
    case (builder, obj @ GuildMemberRemoveData(guildId, user), log) =>
      builder.getGuild(guildId) match {
        case Some(guild) =>
          builder.guilds.put(guildId, guild.copy(members = guild.members - user.id))
        case None => log.warning(s"Couldn't get guild for member delete $obj")
      }
  }

  implicit val roleDeleteHandler: CacheDeleteHandler[GuildRoleDeleteData] = deleteHandler {
    case (builder, obj @ GuildRoleDeleteData(guildId, roleId), log) =>
      builder.getGuild(guildId) match {
        case Some(guild) => builder.guilds.put(guildId, guild.copy(roles = guild.roles - roleId))
        case None        => log.warning(s"Couldn't get guild for member delete $obj")
      }
  }

  implicit val rawMessageDeleteHandler: CacheDeleteHandler[MessageDeleteData] = deleteHandler {
    case (builder, MessageDeleteData(id, channelId), _) =>
      builder.messages.get(channelId).foreach(_.remove(id))
  }

  implicit val rawMessageDeleteBulkHandler: CacheDeleteHandler[MessageDeleteBulkData] = deleteHandler {
    case (builder, MessageDeleteBulkData(ids, channelId), _) =>
      builder.messages.get(channelId).foreach(_ --= ids)
  }

  implicit val rawMessageReactionRemoveHandler: CacheDeleteHandler[MessageReactionData] = deleteHandler { (builder, obj, _) =>
    builder.getMessage(obj.channelId, obj.messageId).foreach { message =>
      val (toChange, toNotChange) = message.reactions.partition(_.emoji == obj.emoji)
      val changed = toChange.map { e =>
        e.copy(count = e.count - 1, me = if (builder.botUser.id == obj.userId) false else e.me)
      }

      val newMessage = message.copy(reactions = toNotChange ++ changed)

      builder.getChannelMessages(obj.channelId).put(obj.messageId, newMessage)
    }
  }

  implicit val rawMessageReactionRemoveAllHandler: CacheDeleteHandler[MessageReactionRemoveAllData] = deleteHandler { (builder, obj, _) =>
    builder.getMessage(obj.channelId, obj.messageId).foreach { message =>
      builder.messages(obj.channelId).put(obj.messageId, message.copy(reactions = Nil))
    }
  }
}
