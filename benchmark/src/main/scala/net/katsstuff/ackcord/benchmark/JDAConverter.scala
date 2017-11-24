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
package net.katsstuff.ackcord.benchmark

import java.awt.Color

import scala.collection.JavaConverters._

import net.dv8tion.jda.core.entities.impl._
import net.dv8tion.jda.core.{OnlineStatus, Region, entities => jda}
import net.katsstuff.ackcord.SnowflakeMap
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.syntax._

object JDAConverter {

  def convert(g: Guild, users: SnowflakeMap[User, User], api: JDAImpl): jda.Guild = {
    val gObj = new GuildImpl(api, g.id)

    val roles = g.roles.values.map(r => r.id -> convert(r, gObj)).toMap
    val members = g.members.values.map(m => m.userId -> convert(m, g, users, gObj, api, roles)).toMap

    gObj.setAvailable(true)
    gObj.setOwner(members(g.ownerId))
    gObj.setName(g.name)
    gObj.setIconId(g.icon.orNull)
    gObj.setSplashId(g.splash.orNull)
    gObj.setRegion(Region.fromKey(g.region))
    gObj.setAfkChannel(
      g.afkChannelId
        .flatMap(g.channels.get)
        .flatMap(_.asVGuildChannel)
        .map(convert(_, gObj, members, roles))
        .orNull
    )
    gObj.setSystemChannel(
      g.systemChannelId
        .flatMap(g.channels.get)
        .flatMap(_.asTGuildChannel)
        .map(convert(_, gObj, members, roles))
        .orNull
    )
    gObj.setVerificationLevel(jda.Guild.VerificationLevel.fromKey(VerificationLevel.idFor(g.verificationLevel)))
    gObj.setDefaultNotificationLevel(
      jda.Guild.NotificationLevel.fromKey(NotificationLevel.idFor(g.defaultMessageNotifications))
    )
    gObj.setRequiredMFALevel(jda.Guild.MFALevel.fromKey(MFALevel.idFor(g.mfaLevel)))
    gObj.setExplicitContentLevel(jda.Guild.ExplicitContentLevel.fromKey(FilterLevel.idFor(g.explicitContentFilter)))
    gObj.setAfkTimeout(jda.Guild.Timeout.SECONDS_60)

    g.channels.foreachValue {
      case ch: TGuildChannel => gObj.getTextChannelsMap.put(ch.id, convert(ch, gObj, members, roles))
      case ch: VGuildChannel => gObj.getVoiceChannelMap.put(ch.id, convert(ch, gObj, members, roles))
      case ch: GuildCategory => //ignore
    }

    members.foreach { case (id, mem) =>
      gObj.getMembersMap.put(id, mem)
    }

    roles.foreach { case (id, role) =>
      gObj.getRolesMap.put(id, role)
    }

    g.emojis.values.foreach { e =>
      gObj.getEmoteMap.put(e.id, convert(e, gObj, roles))
    }

    api.getGuildMap.put(g.id, gObj)

    gObj
  }

  def convert(r: Role, guildImpl: GuildImpl): jda.Role = {
    val rObj = new RoleImpl(r.id, guildImpl)

    rObj.setName(r.name)
    rObj.setColor(new Color(r.color))
    rObj.setManaged(r.managed)
    rObj.setHoisted(r.hoist)
    rObj.setMentionable(r.mentionable)
    rObj.setRawPermissions(r.permissions)
    rObj.setRawPosition(r.position)

    rObj
  }

  def convert(
      mem: GuildMember,
      guild: Guild,
      users: SnowflakeMap[User, User],
      guildImpl: GuildImpl,
      api: JDAImpl,
      roles: Map[RoleId, jda.Role]
  ): jda.Member = {
    var userObj = api.getUserById(mem.userId)
    if(userObj == null) {
      userObj = convert(users(mem.userId), api)
      api.getUserMap.put(mem.userId, userObj)
    }

    val mObj = new MemberImpl(guildImpl, userObj)

    val presence = guild.presences(mem.userId)

    mObj.setNickname(mem.nick.orNull)
    mObj.setJoinDate(mem.joinedAt)
    mObj.setGame(presence.content.map {
      case PresenceGame(name)           => jda.Game.of(name)
      case PresenceStreaming(name, uri) => jda.Game.of(name, uri)
    }.orNull)
    mObj.setOnlineStatus(OnlineStatus.fromKey(PresenceStatus.nameOf(presence.status)))
    mObj.getRoleSet.addAll(roles.values.filter(r => mem.roleIds.contains(r.getIdLong)).toSeq.asJava)

    mObj
  }

  def convert(u: User, api: JDAImpl): jda.User = {
    val uObj = new UserImpl(u.id, api)

    uObj.setName(u.username)
    uObj.setDiscriminator(u.discriminator)
    uObj.setAvatarId(u.avatar.orNull)
    uObj.setBot(u.bot.getOrElse(false))
    uObj.setFake(true)

    uObj
  }

  def convert(
      ch: VGuildChannel,
      guildImpl: GuildImpl,
      members: Map[UserId, jda.Member],
      roles: Map[RoleId, jda.Role]
  ): jda.VoiceChannel = {
    val chObj = new VoiceChannelImpl(ch.id, guildImpl)

    ch.permissionOverwrites.values.foreach { ow =>
      val holder =
        if (ow.`type` == PermissionOverwriteType.Member) members(UserId(ow.id))
        else roles(RoleId(ow.id))

      val owObj = new PermissionOverrideImpl(chObj, ow.id, holder)
      chObj.getOverrideMap.put(ow.id, owObj)
    }

    chObj.setName(ch.name)
    chObj.setRawPosition(ch.position)

    chObj.setUserLimit(ch.userLimit)
    chObj.setBitrate(ch.bitrate)

    chObj
  }

  def convert(
      ch: TGuildChannel,
      guildImpl: GuildImpl,
      members: Map[UserId, jda.Member],
      roles: Map[RoleId, jda.Role]
  ): jda.TextChannel = {
    val chObj = new TextChannelImpl(ch.id, guildImpl)

    ch.permissionOverwrites.values.foreach { ow =>
      val holder =
        if (ow.`type` == PermissionOverwriteType.Member) members(UserId(ow.id))
        else roles(RoleId(ow.id))

      val owObj = new PermissionOverrideImpl(chObj, ow.id, holder)
      chObj.getOverrideMap.put(ow.id, owObj)
    }

    chObj.setName(ch.name)
    chObj.setRawPosition(ch.position)

    chObj.setTopic(ch.topic.orNull)
    chObj.setLastMessageId(ch.lastMessageId.getOrElse(0))
    chObj.setNSFW(ch.nsfw)

    chObj
  }

  def convert(e: Emoji, guildImpl: GuildImpl, roles: Map[RoleId, jda.Role]): jda.Emote = {
    val eObj = new EmoteImpl(e.id, guildImpl)

    eObj.setName(e.name)
    eObj.setManaged(e.managed)
    eObj.getRoleSet.addAll(e.roles.flatMap(roles.get).asJava)

    eObj
  }
}
