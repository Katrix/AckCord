/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord.cachehandlers

import scala.collection.mutable

import net.katsstuff.ackcord.SnowflakeMap
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.network.websocket.gateway.GatewayEvent._
import net.katsstuff.ackcord.network.{RawBan, RawChannel, RawEmoji, RawGuild, RawMessage}
import net.katsstuff.ackcord.syntax._

object RawHandlers extends Handlers {

  import CacheDeleteHandler._
  import CacheUpdateHandler._

  implicit val rawChannelUpdateHandler: CacheUpdateHandler[RawChannel] = updateHandler {
    case (builder, rawChannel: RawChannel, log) =>
      rawChannel.toChannel.foreach {
        case guildChannel: GuildChannel =>
          builder.guilds.get(guildChannel.guildId) match {
            case Some(guild) =>
              val newChannels = guild.channels.updated(guildChannel.id, guildChannel)
              builder.guilds.put(guild.id, guild.copy(channels = newChannels))
            case None => log.warning(s"No guild for channel update $guildChannel")
          }
        case dmChannel: DMChannel           => builder.dmChannels.put(dmChannel.id, dmChannel)
        case groupDmChannel: GroupDMChannel => builder.groupDmChannels.put(groupDmChannel.id, groupDmChannel)
      }
  }

  implicit val rawGuildUpdateHandler: CacheUpdateHandler[RawGuild] = updateHandler { (builder, obj, log) =>
    val rawMembers   = obj.members.getOrElse(Seq.empty)
    val rawChannels  = obj.channels.getOrElse(Seq.empty)
    val rawPresences = obj.presences.getOrElse(Seq.empty)

    //We use unzip here to get away with a single traversal instead of 2
    val (users, members) = rawMembers.map { rawMember =>
      rawMember.user -> rawMember.toGuildMember(obj.id)
    }.unzip

    val presences = rawPresences.map(_.toPresence)
    val channels  = rawChannels.flatMap(_.toGuildChannel(obj.id))

    val oldGuild = builder.getGuild(obj.id)

    //Get on Option here are because everything should be sent here
    val guild = Guild(
      id = obj.id,
      name = obj.name,
      icon = obj.icon,
      splash = obj.splash,
      owner = obj.owner,
      ownerId = obj.ownerId,
      permissions = obj.permissions,
      region = obj.region,
      afkChannelId = obj.afkChannelId,
      afkTimeout = obj.afkTimeout,
      embedEnabled = obj.embedEnabled,
      embedChannelId = obj.embedChannelId,
      verificationLevel = obj.verificationLevel,
      defaultMessageNotifications = obj.defaultMessageNotifications,
      explicitContentFilter = obj.explicitContentFilter,
      roles = SnowflakeMap(obj.roles.map(r => r.id   -> r.toRole(obj.id))),
      emojis = SnowflakeMap(obj.emojis.map(e => e.id -> e.toEmoji)),
      features = obj.features,
      mfaLevel = obj.mfaLevel,
      applicationId = obj.applicationId,
      widgetEnabled = obj.widgetEnabled,
      widgetChannelId = obj.widgetChannelId,
      systemChannelId = obj.systemChannelId,
      joinedAt = obj.joinedAt.orElse(oldGuild.map(_.joinedAt)).get,
      large = obj.large.orElse(oldGuild.map(_.large)).get,
      memberCount = obj.memberCount.orElse(oldGuild.map(_.memberCount)).get,
      voiceStates = obj.voiceStates
        .map(seq => SnowflakeMap.withKey(seq)(_.userId))
        .orElse(oldGuild.map(_.voiceStates))
        .get,
      members = SnowflakeMap.withKey(members)(_.userId),
      channels = SnowflakeMap.withKey(channels)(_.id),
      presences = SnowflakeMap.withKey(presences)(_.userId)
    )

    builder.guilds.put(guild.id, guild)
    handleUpdateLog(builder, users, log)
  }

  implicit val guildEmojisUpdateDataHandler: CacheUpdateHandler[GuildEmojisUpdateData] = updateHandler {
    case (builder, obj @ GuildEmojisUpdateData(guildId, emojis), log) =>
      builder.getGuild(guildId) match {
        case Some(guild) =>
          val newGuild = guild.copy(emojis = SnowflakeMap(emojis.map(e => e.id -> e.toEmoji)))
          builder.guilds.put(guildId, newGuild)
        case None => log.warning(s"Can't find guild for emojis update $obj")
      }
  }

  implicit val rawGuildMemberWithGuildUpdateHandler: CacheUpdateHandler[RawGuildMemberWithGuild] = updateHandler {
    case (builder, obj, log) =>
      val member = obj.toRawGuildMember.toGuildMember(obj.guildId)

      builder.getGuild(obj.guildId) match {
        case Some(guild) =>
          val newGuild = guild.copy(members = guild.members.updated(obj.user.id, member))
          builder.guilds.put(obj.guildId, newGuild)
        case None => log.warning(s"Can't find guild for guildMember update $obj")
      }

      handleUpdateLog(builder, obj.user, log)
  }

  implicit val rawGuildMemberChunkHandler: CacheUpdateHandler[GuildMemberChunkData] = updateHandler {
    case (builder, obj @ GuildMemberChunkData(guildId, newRawMembers), log) =>
      val (newUsers, newMembers) = newRawMembers.map(member => member.user -> member.toGuildMember(guildId)).unzip

      builder.getGuild(guildId) match {
        case Some(guild) =>
          val newGuild = guild.copy(members = guild.members ++ SnowflakeMap.withKey(newMembers)(_.userId))
          builder.guilds.put(guildId, newGuild)
        case None => log.warning(s"Can't find guild for guildMember update $obj")
      }

      handleUpdateLog(builder, newUsers, log)
  }

  implicit val rawGuildMemberUpdateHandler: CacheUpdateHandler[GuildMemberUpdateData] = updateHandler {
    case (builder, obj @ GuildMemberUpdateData(guildId, roles, user, nick), log) =>
      val newGuildEither = for {
        guild       <- builder.getGuild(guildId).toRight(s"Can't find guild for user update $obj")
        guildMember <- guild.members.get(user.id).toRight(s"Can't find member for member update $obj")
      } yield {
        val newGuildMember = guildMember.copy(nick = nick, roleIds = roles)
        guild.copy(members = guild.members.updated(user.id, newGuildMember))
      }

      newGuildEither match {
        case Right(newGuild) => builder.guilds.put(guildId, newGuild)
        case Left(e)         => log.warning(e)
      }

      handleUpdateLog(builder, user, log)
  }

  implicit val roleUpdateHandler: CacheUpdateHandler[GuildRoleModifyData] = updateHandler {
    case (builder, obj @ GuildRoleModifyData(guildId, role), log) =>
      builder.getGuild(guildId) match {
        case Some(guild) =>
          val newGuild = guild.copy(roles = guild.roles.updated(role.id, role.toRole(guildId)))
          builder.guilds.put(guildId, newGuild)
        case None => log.warning(s"No guild found for role update $obj")
      }
  }

  implicit val rawMessageUpdateHandler: CacheUpdateHandler[RawMessage] = updateHandler { (builder, obj, log) =>
    val users   = obj.mentions
    val message = obj.toMessage

    builder.messages.getOrElseUpdate(obj.channelId, mutable.Map.empty).put(message.id, message)
    handleUpdateLog(builder, users, log)
    obj.author match {
      case user: User => handleUpdateLog(builder, user, log)
      case _          => //Ignore
    }
  }

  implicit val rawPartialMessageUpdateHandler: CacheUpdateHandler[RawPartialMessage] = updateHandler {
    (builder, obj, log) =>
      val newUsers = obj.mentions.getOrElse(Seq.empty)

      builder.getMessage(obj.channelId, obj.id).map { message =>
        val newMessage = message.copy(
          authorId = obj.author.map(a => RawSnowflake(a.id)).getOrElse(message.authorId),
          isAuthorUser = obj.author.map(_.isUser).getOrElse(message.isAuthorUser),
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
        )

        builder.messages.getOrElseUpdate(obj.channelId, mutable.Map.empty).put(message.id, newMessage)
      }

      handleUpdateLog(builder, newUsers, log)
  }

  implicit val lastTypedHandler: CacheUpdateHandler[TypingStartData] = updateHandler { (builder, obj, _) =>
    builder.getChannelLastTyped(obj.channelId).put(obj.userId, obj.timestamp)
  }

  implicit val rawMessageReactionUpdateHandler: CacheUpdateHandler[MessageReactionData] = updateHandler {
    (builder, obj, _) =>
      builder.getMessage(obj.channelId, obj.messageId).foreach { message =>
        val (toChange, toNotChange) = message.reactions.partition(_.emoji == obj.emoji)
        val changed = toChange.map { emoji =>
          val isMe = if (builder.botUser.id == obj.userId) true else emoji.me
          emoji.copy(count = emoji.count + 1, me = isMe)
        }

        val newMessage = message.copy(reactions = toNotChange ++ changed)
        builder.getChannelMessages(obj.channelId).put(obj.messageId, newMessage)
      }
  }

  implicit val rawBanUpdateHandler: CacheUpdateHandler[(GuildId, RawBan)] = updateHandler {
    case (builder, (guildId, obj), log) =>
      builder.bans.getOrElseUpdate(guildId, mutable.Map.empty).put(obj.user.id, obj.toBan)
      handleUpdateLog(builder, obj.user, log)
  }

  implicit val rawEmojiUpdateHandler: GuildId => CacheUpdateHandler[RawEmoji] = guildId =>
    updateHandler { (builder, obj, log) =>
      builder.guilds.get(guildId) match {
        case Some(guild) =>
          val newGuild = guild.copy(emojis = guild.emojis.updated(obj.id, obj.toEmoji))
          builder.guilds.put(guildId, newGuild)
        case None => log.warning(s"No guild for emoji $obj")
      }
  }

  //Delete
  implicit val rawChannelDeleteHandler: CacheDeleteHandler[RawChannel] = deleteHandler { (builder, rawChannel, _) =>
    rawChannel.`type` match {
      case ChannelType.GuildText | ChannelType.GuildVoice | ChannelType.GuildCategory =>
        rawChannel.guildId.flatMap(builder.getGuild).foreach { guild =>
          builder.guilds.put(guild.id, guild.copy(channels = guild.channels - rawChannel.id))
        }
      case ChannelType.DM      => builder.dmChannels.remove(rawChannel.id)
      case ChannelType.GroupDm => builder.groupDmChannels.remove(rawChannel.id)
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

  implicit val rawMessageReactionRemoveHandler: CacheDeleteHandler[MessageReactionData] = deleteHandler {
    (builder, obj, _) =>
      builder.getMessage(obj.channelId, obj.messageId).foreach { message =>
        val (toChange, toNotChange) = message.reactions.partition(_.emoji == obj.emoji)
        val changed = toChange.map { emoji =>
          val isMe = if (builder.botUser.id == obj.userId) false else emoji.me
          emoji.copy(count = emoji.count - 1, me = isMe)
        }

        val newMessage = message.copy(reactions = toNotChange ++ changed)
        builder.getChannelMessages(obj.channelId).put(obj.messageId, newMessage)
      }
  }

  implicit val rawMessageReactionRemoveAllHandler: CacheDeleteHandler[MessageReactionRemoveAllData] = deleteHandler {
    (builder, obj, _) =>
      builder.getMessage(obj.channelId, obj.messageId).foreach { message =>
        builder.messages(obj.channelId).put(obj.messageId, message.copy(reactions = Nil))
      }
  }

  implicit val rawBanDeleteHandler: CacheDeleteHandler[(GuildId, User)] = deleteHandler {
    case (builder, (guildId, user), log) =>
      builder.bans.get(guildId).foreach(_.remove(user.id))
      handleUpdateLog(builder, user, log)
  }
}
