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
package net.katsstuff.ackcord.benchmark

import java.lang.reflect.Field

import net.katsstuff.ackcord.SnowflakeMap
import net.katsstuff.ackcord.data.{ChannelType, Guild, MFALevel, NotificationLevel, PermissionOverwriteType, PresenceGame, PresenceListening, PresenceStatus, PresenceStreaming, PresenceWatching, User, UserId, VerificationLevel}
import net.katsstuff.ackcord.syntax._
import sx.blah.discord.api.internal.{DiscordUtils, ShardImpl}
import sx.blah.discord.api.internal.json.objects._
import sx.blah.discord.handle.obj.IGuild
import sx.blah.discord.util.cache.Cache

object D4JConverter {

  val guildCacheField: Field = classOf[ShardImpl].getDeclaredField("guildCache")
  guildCacheField.setAccessible(true)

  def convert(g: Guild, users: SnowflakeMap[User, User], shard: ShardImpl): IGuild = {
    val gObj = new GuildObject
    gObj.id = g.id.asString
    gObj.name = g.name
    gObj.icon = g.icon.orNull
    gObj.owner_id = g.ownerId.asString
    gObj.region = g.region
    gObj.afk_channel_id = g.afkChannelId.map(_.asString).orNull
    gObj.afk_timeout = g.afkTimeout
    gObj.embed_enabled = g.embedEnabled.getOrElse(false)
    gObj.embed_channel_id = g.embedChannelId.map(_.asString).orNull
    gObj.verification_level = VerificationLevel.idFor(g.verificationLevel)
    gObj.default_messages_notifications = NotificationLevel.idFor(g.defaultMessageNotifications)
    gObj.roles = g.roles.values.map { role =>
      val rObj = new RoleObject
      rObj.id = role.id.asString
      rObj.name = role.name
      rObj.color = role.color
      rObj.hoist = role.hoist
      rObj.position = role.position
      rObj.permissions = role.permissions.toInt
      rObj.managed = role.managed
      rObj.mentionable = role.mentionable
      rObj
    }.toArray
    gObj.emojis = g.emojis.values.map { emoji =>
      val eObj = new EmojiObject
      eObj.id = emoji.id.asString
      eObj.name = emoji.name
      eObj.roles = emoji.roles.map(_.asString).toArray
      eObj.require_colons = emoji.requireColons
      eObj.managed = emoji.managed
      eObj
    }.toArray
    gObj.features = g.features.toArray
    gObj.mfa_level = MFALevel.idFor(g.mfaLevel)
    gObj.joined_at = g.joinedAt.toLocalDateTime.toString
    gObj.large = g.large
    gObj.unavailable = false
    gObj.member_count = g.memberCount
    gObj.voice_states = g.voiceStates.values.map { vState =>
      val vObj = new VoiceStateObject
      vObj.guild_id = vState.guildId.map(_.asString).orNull
      vObj.channel_id = vState.channelId.map(_.asString).orNull
      vObj.user_id = vState.userId.asString
      vObj.session_id = vState.sessionId
      vObj.deaf = vState.deaf
      vObj.mute = vState.mute
      vObj.self_deaf = vState.selfDeaf
      vObj.self_mute = vState.selfMute
      vObj.suppress = vState.suppress
      vObj
    }.toArray
    gObj.members = g.members.values.flatMap { mem =>
      userToUserObj(mem.userId, users).map { userObj =>
        val mObj = new MemberObject
        mObj.user = userObj
        mObj.nick = mem.nick.orNull
        mObj.roles = mem.roleIds.map(_.asString).toArray
        mObj.joined_at = mem.joinedAt.toLocalDateTime.toString
        mObj.deaf = mem.deaf
        mObj.mute = mem.mute
        mObj
      }
    }.toArray
    gObj.channels = g.channels.values.map { ch =>
      val chObj = new ChannelObject
      chObj.id = ch.id.asString
      chObj.`type` = ChannelType.idFor(ch.channelType)
      chObj.guild_id = ch.guildId.asString
      chObj.position = ch.position
      chObj.permission_overwrites = ch.permissionOverwrites.values.map { permOver =>
        val permObj = new OverwriteObject
        permObj.id = permOver.id.asString
        permObj.`type` = PermissionOverwriteType.nameOf(permOver.`type`)
        permObj.allow = permOver.allow.toInt
        permObj.deny = permOver.deny.toInt
        permObj
      }.toArray
      chObj.name = ch.name
      chObj.topic = ch.asTGuildChannel.flatMap(_.topic).orNull
      chObj.last_message_id = ch.asTGuildChannel.flatMap(_.lastMessageId).map(_.asString).orNull
      chObj.bitrate = ch.asVGuildChannel.map(c => Int.box(c.bitrate)).orNull
      chObj.user_limit = ch.asVGuildChannel.map(c => Int.box(c.userLimit)).orNull
      chObj.recipients = null
      chObj.icon = null
      chObj.owner_id = null
      chObj.application_id = null
      chObj.nsfw = ch.nsfw
      chObj.parent_id = ch.parentId.map(_.asString).orNull
      chObj
    }.toArray
    gObj.presences = g.presences.values.flatMap { pres =>
      userToUserObj(pres.userId, users).map { userObj =>
        val pObj = new PresenceObject
        pObj.user = userObj
        pObj.status = PresenceStatus.nameOf(pres.status)
        pObj.game = pres.content.map {
          case PresenceGame(name) => new GameObject(name, null)
          case PresenceStreaming(name, uri) => new GameObject(name, uri)
          case PresenceListening(name) => new GameObject(name, 2)
          case PresenceWatching(name) => new GameObject(name, 3)
        }.orNull
        pObj
      }
    }.toArray

    val res = DiscordUtils.getGuildFromJSON(shard, gObj)
    guildCacheField.get(shard).asInstanceOf[Cache[IGuild]].put(res)
    res
  }

  def userToUserObj(userId: UserId, users: SnowflakeMap[User, User]): Option[UserObject] = {
    users.get(userId).map { u =>
      val uObj = new UserObject
      uObj.username = u.username
      uObj.discriminator = u.discriminator
      uObj.id = u.id.asString
      uObj.avatar = u.avatar.orNull
      uObj.bot = u.bot.getOrElse(false)
      uObj
    }
  }
}
