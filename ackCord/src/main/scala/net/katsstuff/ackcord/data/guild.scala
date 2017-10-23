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
package net.katsstuff.ackcord.data

import java.time.OffsetDateTime

sealed trait UnknownStatusGuild {
  def id:          GuildId
  def unavailable: Boolean
}

trait VerificationLevel
object VerificationLevel {
  object NoneVerification extends VerificationLevel
  object Low              extends VerificationLevel
  object Medium           extends VerificationLevel
  object High             extends VerificationLevel
  object VeryHigh         extends VerificationLevel

  def forId(id: Int): Option[VerificationLevel] = id match {
    case 0 => Some(NoneVerification)
    case 1 => Some(Low)
    case 2 => Some(Medium)
    case 3 => Some(High)
    case 4 => Some(VeryHigh)
    case _ => None
  }

  def idFor(lvl: VerificationLevel): Int = lvl match {
    case NoneVerification => 0
    case Low              => 1
    case Medium           => 2
    case High             => 3
    case VeryHigh         => 4
  }
}

trait NotificationLevel
object NotificationLevel {
  object AllMessages  extends NotificationLevel
  object OnlyMentions extends NotificationLevel

  def forId(id: Int): Option[NotificationLevel] = id match {
    case 0 => Some(AllMessages)
    case 1 => Some(OnlyMentions)
    case _ => None
  }

  def idFor(lvl: NotificationLevel): Int = lvl match {
    case AllMessages  => 0
    case OnlyMentions => 1
  }
}

trait FilterLevel
object FilterLevel {
  object Disabled            extends FilterLevel
  object MembersWithoutRoles extends FilterLevel
  object AllMembers          extends FilterLevel

  def forId(id: Int): Option[FilterLevel] = id match {
    case 0 => Some(Disabled)
    case 1 => Some(MembersWithoutRoles)
    case 2 => Some(AllMembers)
    case _ => None
  }

  def idFor(lvl: FilterLevel): Int = lvl match {
    case Disabled            => 0
    case MembersWithoutRoles => 1
    case AllMembers          => 2
  }
}

trait MFALevel
object MFALevel {
  object NoneMFA  extends MFALevel
  object Elevated extends MFALevel

  def forId(id: Int): Option[MFALevel] = id match {
    case 0 => Some(NoneMFA)
    case 1 => Some(Elevated)
    case _ => None
  }

  def idFor(lvl: MFALevel): Int = lvl match {
    case NoneMFA  => 0
    case Elevated => 1
  }
}

case class Guild(
    id: GuildId,
    name: String,
    icon: Option[String], ////Icon can be null
    splash: Option[String], //Splash can be null
    ownerId: UserId,
    region: String,
    afkChannelId: Option[ChannelId], //AfkChannelId can be null
    afkTimeout: Int,
    embedEnabled: Option[Boolean], //embedEnabled can be missing
    embedChannelId: Option[ChannelId], //embedChannelId can be missing
    verificationLevel: VerificationLevel,
    defaultMessageNotifications: NotificationLevel,
    explicitContentFilter: FilterLevel,
    roles: Map[RoleId, Role],
    emojis: Map[EmojiId, GuildEmoji],
    features: Seq[String], //TODO: What is a feature?
    mfaLevel: MFALevel,
    applicationId: Option[Snowflake],
    widgetEnabled: Option[Boolean], //Can me missing
    widgetChannelId: Option[ChannelId], //Can be missing
    joinedAt: OffsetDateTime,
    large: Boolean,
    memberCount: Int,
    voiceStates: Map[UserId, VoiceState], //guildId is absent in those received in GuildCreate
    members: Map[UserId, GuildMember],
    channels: Map[ChannelId, GuildChannel],
    presences: Map[UserId, Presence]
) extends UnknownStatusGuild {
  override def unavailable: Boolean = false
}

case class UnavailableGuild(id: GuildId, unavailable: Boolean) extends UnknownStatusGuild

case class GuildMember(
    userId: UserId,
    guildId: GuildId,
    nick: Option[String],
    roleIds: Seq[RoleId],
    joinedAt: OffsetDateTime,
    deaf: Boolean,
    mute: Boolean
) extends GetUser
    with GetGuild {
  def permissions(implicit c: CacheSnapshot): Permission = {
    import net.katsstuff.ackcord.syntax._

    val res = for {
      guild <- guildId.resolve
    } yield {
      if(guild.ownerId == userId) Permission.All
      else {
        val userPermissions = this.rolesForUser.map(_.permissions)
        val everyonePerms = guild.everyoneRole.permissions

        val guildPermissions = everyonePerms.addPermissions(Permission(userPermissions: _*))

        if(guildPermissions.hasPermissions(Permission.Administrator)) Permission.All
        else guildPermissions
      }
    }

    res.getOrElse(Permission.None)
  }

  def permissionsWithOverrides(guildPermissions: Permission, channelId: ChannelId)(implicit c: CacheSnapshot): Permission = {
    import net.katsstuff.ackcord.syntax._

    if(guildPermissions.hasPermissions(Permission.Administrator)) Permission.All
    else {
      val res = for {
        guild <- guildId.resolve
        channel <- guild.channelById(channelId)
      } yield {
        if(guild.ownerId == userId) Permission.All
        else {
          val everyoneOverwrite = channel.permissionOverwrites.get(guild.everyoneRole.id)
          val everyoneAllow = everyoneOverwrite.map(_.allow)
          val everyoneDeny = everyoneOverwrite.map(_.deny)

          val roleOverwrites = this.rolesForUser.flatMap(r => channel.permissionOverwrites.get(r.id))
          val roleAllow = Permission(roleOverwrites.map(_.allow): _*)
          val roleDeny = Permission(roleOverwrites.map(_.deny): _*)

          val userOverwrite = channel.permissionOverwrites.get(userId)
          val userAllow = userOverwrite.map(_.allow)
          val userDeny = userOverwrite.map(_.deny)

          def mapOrElse(permission: Permission, opt: Option[Permission], f: (Permission, Permission) => Permission): Permission = {
            opt.map(f(permission, _)).getOrElse(permission)
          }

          def addOrElse(opt: Option[Permission])(permission: Permission): Permission =
            mapOrElse(permission, opt, _.addPermissions(_))
          def removeOrElse(opt: Option[Permission])(permission: Permission): Permission =
            mapOrElse(permission, opt, _.removePermissions(_))

          val withEveryone = (addOrElse(everyoneAllow) _).andThen(removeOrElse(everyoneDeny)).apply(guildPermissions)
          val withRole = withEveryone.addPermissions(roleAllow).removePermissions(roleDeny)
          val withUser = (addOrElse(userAllow) _).andThen(removeOrElse(userDeny)).apply(withRole)

          withUser
        }
      }

      res.getOrElse(guildPermissions)
    }
  }

  def channelPermissions(channelId: ChannelId)(implicit c: CacheSnapshot): Permission =
    permissionsWithOverrides(permissions, channelId)
}
case class GuildEmoji(id: EmojiId, name: String, roles: Seq[RoleId], requireColons: Boolean, managed: Boolean)

sealed trait PresenceContent {
  def name: String
}
case class PresenceGame(name: String)                   extends PresenceContent
case class PresenceStreaming(name: String, uri: String) extends PresenceContent
sealed trait PresenceStatus
object PresenceStatus {
  case object Online       extends PresenceStatus
  case object DoNotDisturb extends PresenceStatus
  case object Idle         extends PresenceStatus
  case object Invisible    extends PresenceStatus
  case object Offline      extends PresenceStatus

  def nameOf(status: PresenceStatus): String = status match {
    case Online       => "online"
    case DoNotDisturb => "dnd"
    case Idle         => "idle"
    case Invisible    => "invisible"
    case Offline      => "offline"
  }

  def forName(name: String): Option[PresenceStatus] = name match {
    case "online"    => Some(Online)
    case "dnd"       => Some(DoNotDisturb)
    case "idle"      => Some(Idle)
    case "invisible" => Some(Invisible)
    case "offline"   => Some(Offline)
    case _           => None
  }
}
case class Presence(userId: UserId, game: Option[PresenceContent], status: PresenceStatus) extends GetUser

case class Integration(
    id: IntegrationId,
    name: String,
    `type`: String, //TODO: Use enum here
    enabled: Boolean,
    syncing: Boolean,
    roleId: RoleId,
    expireBehavior: Int, //TODO: Better than Int here
    expireGracePeriod: Int,
    user: User,
    account: IntegrationAccount,
    syncedAt: OffsetDateTime
)

case class IntegrationAccount(id: String, name: String)

case class GuildEmbed(enabled: Boolean, channelId: ChannelId)
