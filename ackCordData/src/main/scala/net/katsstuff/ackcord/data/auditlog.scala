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

package net.katsstuff.ackcord.data

/**
  * Root audit log object. Received from [[net.katsstuff.ackcord.http.requests.RESTRequests.GetGuildAuditLog]]
  * @param webhooks The webhooks found in the log
  * @param users The users found in the log
  * @param auditLogEntries The entries of the log
  */
case class AuditLog(webhooks: Seq[Webhook], users: Seq[User], auditLogEntries: Seq[AuditLogEntry])

/**
  * An individual audit log event
  * @param targetId The id of the affected object
  * @param changes The changes made to the object
  * @param userId The user responsible for the changes
  * @param id The id of this entry
  * @param actionType Type of change that happened
  * @param options Optional extra data for some changes,
  *                see comments on [[OptionalAuditLogInfo]] for more info
  * @param reason The reason for the change
  */
case class AuditLogEntry(
    targetId: Option[RawSnowflake],
    changes: Option[Seq[AuditLogChange[_]]],
    userId: UserId,
    id: RawSnowflake,
    actionType: AuditLogEvent,
    options: Option[OptionalAuditLogInfo],
    reason: Option[String]
)

/**
  * A type of change that an entry can represent
  */
sealed trait AuditLogEvent
object AuditLogEvent {
  case object GuildUpdate            extends AuditLogEvent
  case object ChannelCreate          extends AuditLogEvent
  case object ChannelUpdate          extends AuditLogEvent
  case object ChannelDelete          extends AuditLogEvent
  case object ChannelOverwriteCreate extends AuditLogEvent
  case object ChannelOverwriteUpdate extends AuditLogEvent
  case object ChannelOverwriteDelete extends AuditLogEvent
  case object MemberKick             extends AuditLogEvent
  case object MemberPrune            extends AuditLogEvent
  case object MemberBanAdd           extends AuditLogEvent
  case object MemberBanRemove        extends AuditLogEvent
  case object MemberUpdate           extends AuditLogEvent
  case object MemberRoleUpdate       extends AuditLogEvent
  case object RoleCreate             extends AuditLogEvent
  case object RoleUpdate             extends AuditLogEvent
  case object RoleDelete             extends AuditLogEvent
  case object InviteCreate           extends AuditLogEvent
  case object InviteUpdate           extends AuditLogEvent
  case object InviteDelete           extends AuditLogEvent
  case object WebhookCreate          extends AuditLogEvent
  case object WebhookUpdate          extends AuditLogEvent
  case object WebhookDelete          extends AuditLogEvent
  case object EmojiCreate            extends AuditLogEvent
  case object EmojiUpdate            extends AuditLogEvent
  case object EmojiDelete            extends AuditLogEvent
  case object MessageDelete          extends AuditLogEvent

  def idOf(event: AuditLogEvent): Int = event match {
    case GuildUpdate            => 1
    case ChannelCreate          => 10
    case ChannelUpdate          => 11
    case ChannelDelete          => 12
    case ChannelOverwriteCreate => 13
    case ChannelOverwriteUpdate => 14
    case ChannelOverwriteDelete => 15
    case MemberKick             => 20
    case MemberPrune            => 21
    case MemberBanAdd           => 22
    case MemberBanRemove        => 23
    case MemberUpdate           => 24
    case MemberRoleUpdate       => 25
    case RoleCreate             => 30
    case RoleUpdate             => 31
    case RoleDelete             => 32
    case InviteCreate           => 40
    case InviteUpdate           => 41
    case InviteDelete           => 42
    case WebhookCreate          => 50
    case WebhookUpdate          => 51
    case WebhookDelete          => 52
    case EmojiCreate            => 60
    case EmojiUpdate            => 61
    case EmojiDelete            => 62
    case MessageDelete          => 72
  }

  def fromId(id: Int): Option[AuditLogEvent] = id match {
    case 1  => Some(GuildUpdate)
    case 10 => Some(ChannelCreate)
    case 11 => Some(ChannelUpdate)
    case 12 => Some(ChannelDelete)
    case 13 => Some(ChannelOverwriteCreate)
    case 14 => Some(ChannelOverwriteUpdate)
    case 15 => Some(ChannelOverwriteDelete)
    case 20 => Some(MemberKick)
    case 21 => Some(MemberPrune)
    case 22 => Some(MemberBanAdd)
    case 23 => Some(MemberBanRemove)
    case 24 => Some(MemberUpdate)
    case 25 => Some(MemberRoleUpdate)
    case 30 => Some(RoleCreate)
    case 31 => Some(RoleUpdate)
    case 32 => Some(RoleDelete)
    case 40 => Some(InviteCreate)
    case 41 => Some(InviteUpdate)
    case 42 => Some(InviteDelete)
    case 50 => Some(WebhookCreate)
    case 51 => Some(WebhookUpdate)
    case 52 => Some(WebhookDelete)
    case 60 => Some(EmojiCreate)
    case 61 => Some(EmojiUpdate)
    case 62 => Some(EmojiDelete)
    case 72 => Some(MessageDelete)
    case _  => None
  }
}

/**
  * Extra data for an entry
  * @param deleteMemberDays The amount of days before a user was considered
  *                         inactive and kicked. Present for MemberPrune.
  * @param membersRemoved The amount of members removed.
  *                       Present for MemberPrune.
  * @param channelId The channelId of the deleted message.
  *                  Present for MessageDelete.
  * @param count The amount of deleted messages. Present for MessageDelete.
  * @param id The id of the overwritten object. Present for overwrite events.
  * @param `type` The type of the overwritten object.
  *               Present for overwrite events.
  * @param roleName The name of the role. Present for overwrite events if type == Role.
  */
case class OptionalAuditLogInfo(
    deleteMemberDays: Option[String],
    membersRemoved: Option[String],
    channelId: Option[ChannelId],
    count: Option[String],
    id: Option[UserOrRoleId],
    `type`: Option[PermissionOverwriteType],
    roleName: Option[String]
)

/**
  * Some sort of change
  * @tparam A The data type that changed
  */
sealed trait AuditLogChange[A] {

  /**
    * The new value
    */
  def newValue: Option[A]

  /**
    * The old value
    */
  def oldValue: Option[A]
}
object AuditLogChange {
  import net.katsstuff.ackcord.data

  /**
    * Name changed
    */
  case class Name(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * Icon hash changed
    */
  case class IconHash(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * Splash hash changed
    */
  case class SplashHash(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * Owner id changed
    */
  case class OwnerId(oldValue: Option[UserId], newValue: Option[UserId]) extends AuditLogChange[UserId]

  /**
    * Region changed
    */
  case class Region(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * AFK channelId changed
    */
  case class AfkChannelId(oldValue: Option[ChannelId], newValue: Option[ChannelId]) extends AuditLogChange[ChannelId]

  /**
    * AFK timeout changed
    */
  case class AfkTimeout(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * MFA level changed
    */
  case class MfaLevel(oldValue: Option[data.MFALevel], newValue: Option[data.MFALevel])
      extends AuditLogChange[data.MFALevel]

  /**
    * Required verification level changed
    */
  case class VerificationLevel(oldValue: Option[data.VerificationLevel], newValue: Option[data.VerificationLevel])
      extends AuditLogChange[data.VerificationLevel]

  /**
    * Explicit content filter changed
    */
  case class ExplicitContentFilter(oldValue: Option[data.FilterLevel], newValue: Option[data.FilterLevel])
      extends AuditLogChange[data.FilterLevel]

  /**
    * Default message notification level changed
    */
  case class DefaultMessageNotification(
      oldValue: Option[data.NotificationLevel],
      newValue: Option[data.NotificationLevel]
  ) extends AuditLogChange[data.NotificationLevel]

  /**
    * Guild invite vanity url changed
    */
  case class VanityUrlCode(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * Role added
    */
  case class $Add(oldValue: Option[Seq[Role]], newValue: Option[Seq[Role]]) extends AuditLogChange[Seq[Role]]

  /**
    * Role removed
    */
  case class $Remove(oldValue: Option[Seq[Role]], newValue: Option[Seq[Role]]) extends AuditLogChange[Seq[Role]]

  /**
    * Prune delete duration changed
    */
  case class PruneDeleteDays(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * Widget enabled changed
    */
  case class WidgetEnabled(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * Widget channelId changed
    */
  case class WidgetChannelId(oldValue: Option[ChannelId], newValue: Option[ChannelId]) extends AuditLogChange[ChannelId]

  /**
    * Channel position changed
    */
  case class Position(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * Channel topic changed
    */
  case class Topic(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * Voice channel bitrate changed
    */
  case class Bitrate(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * Permission overwrites for channel changed
    */
  case class PermissionOverwrites(
      oldValue: Option[Seq[PermissionOverwrite]],
      newValue: Option[Seq[PermissionOverwrite]]
  ) extends AuditLogChange[Seq[PermissionOverwrite]]

  /**
    * NSFW for channel changed
    */
  case class NSFW(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /**
    * ApplicationId of webhook or bot
    */
  case class ApplicationId(oldValue: Option[RawSnowflake], newValue: Option[RawSnowflake])
      extends AuditLogChange[RawSnowflake]

  /**
    * Permissions of role changed
    */
  case class Permissions(oldValue: Option[Permission], newValue: Option[Permission]) extends AuditLogChange[Permission]

  /**
    * Color of role changed
    */
  case class Color(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * Hoist of role changed
    */
  case class Hoist(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /**
    * Mentionable of role changed
    */
  case class Mentionable(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /**
    * Permission was allowed for a role on a channel
    */
  case class Allow(oldValue: Option[Permission], newValue: Option[Permission]) extends AuditLogChange[Permission]

  /**
    * Permission was denied for role on a channel
    */
  case class Deny(oldValue: Option[Permission], newValue: Option[Permission]) extends AuditLogChange[Permission]

  /**
    * Invite code changed
    */
  case class Code(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * Invite channelId changed
    */
  case class InviteChannelId(oldValue: Option[ChannelId], newValue: Option[ChannelId]) extends AuditLogChange[ChannelId]

  /**
    * Inviter userId changed
    */
  case class InviterId(oldValue: Option[UserId], newValue: Option[UserId]) extends AuditLogChange[UserId]

  /**
    * Max uses of an invite changed
    */
  case class MaxUses(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * Amount of times an invite has been used changed
    */
  case class Uses(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * Max age of invite changed
    */
  case class MaxAge(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * If invite is temporary changed
    */
  case class Temporary(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /**
    * Deaf for user changed
    */
  case class Deaf(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /**
    * Mute for user changed
    */
  case class Mute(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /**
    * Nick for user changed
    */
  case class Nick(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * Avatar hash changed
    */
  case class AvatarHash(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * Id of changed object
    */
  case class Id(oldValue: Option[RawSnowflake], newValue: Option[RawSnowflake]) extends AuditLogChange[RawSnowflake]

  /**
    * Type of object changed
    */
  case class TypeInt(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /**
    * Type of created object
    */
  case class TypeString(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]
}
