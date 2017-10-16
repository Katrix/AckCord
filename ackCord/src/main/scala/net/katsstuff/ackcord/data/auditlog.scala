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

import io.circe.Decoder

case class AuditLog(webhooks: Seq[Webhook], users: Seq[User], auditLogEntries: Seq[AuditLogEntry])

case class AuditLogEntry(
    targetId: Snowflake,
    changes: Seq[AuditLogChange[_]],
    userId: UserId,
    id: Snowflake,
    actionType: AuditLogEvent,
    options: Seq[OptionalAuditLogInfo],
    reason: String
)

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

case class OptionalAuditLogInfo(
    deleteMemberDays: Option[String], //Present for MemberPrune
    membersRemoved: Option[String], //Present for MemberPrune
    channelId: Option[ChannelId], //Present for MessageDelete
    count: Option[String], //Present for MessageDelete
    id: Option[UserOrRoleId], //Present for overwrite events
    `type`: Option[PermissionOverwriteType], //Present for overwrite events
    roleName: Option[String] //Present for overwrite events if type == "role"
)

sealed trait AuditLogChange[A] {
  def newValue: A
  def oldValue: A
}
object AuditLogChange {
  import net.katsstuff.ackcord.data
  case class Name(oldValue: String, newValue: String)                   extends AuditLogChange[String]
  case class IconHash(oldValue: String, newValue: String)               extends AuditLogChange[String]
  case class SplashHash(oldValue: String, newValue: String)             extends AuditLogChange[String]
  case class OwnerId(oldValue: UserId, newValue: UserId)                extends AuditLogChange[UserId]
  case class Region(oldValue: String, newValue: String)                 extends AuditLogChange[String]
  case class AfkChannelId(oldValue: ChannelId, newValue: ChannelId)     extends AuditLogChange[ChannelId]
  case class AfkTimeout(oldValue: Int, newValue: Int)                   extends AuditLogChange[Int]
  case class MfaLevel(oldValue: data.MFALevel, newValue: data.MFALevel) extends AuditLogChange[data.MFALevel]
  case class VerificationLevel(oldValue: data.VerificationLevel, newValue: data.VerificationLevel)
      extends AuditLogChange[data.VerificationLevel]
  case class ExplicitContentFilter(oldValue: data.FilterLevel, newValue: data.FilterLevel)
      extends AuditLogChange[data.FilterLevel]
  case class DefaultMessageNotification(oldValue: data.NotificationLevel, newValue: data.NotificationLevel)
      extends AuditLogChange[data.NotificationLevel]
  case class VanityUrlCode(oldValue: String, newValue: String)         extends AuditLogChange[String]
  case class $Add(oldValue: Seq[Role], newValue: Seq[Role])            extends AuditLogChange[Seq[Role]]
  case class $Remove(oldValue: Seq[Role], newValue: Seq[Role])         extends AuditLogChange[Seq[Role]]
  case class PruneDeleteDays(oldValue: Int, newValue: Int)             extends AuditLogChange[Int]
  case class WidgetEnabled(oldValue: Int, newValue: Int)               extends AuditLogChange[Int]
  case class WidgetChannelId(oldValue: ChannelId, newValue: ChannelId) extends AuditLogChange[ChannelId]
  case class Position(oldValue: Int, newValue: Int)                    extends AuditLogChange[Int]
  case class Topic(oldValue: String, newValue: String)                 extends AuditLogChange[String]
  case class Bitrate(oldValue: Int, newValue: Int)                     extends AuditLogChange[Int]
  case class PermissionOverwrites(oldValue: Seq[PermissionOverwrite], newValue: Seq[PermissionOverwrite])
      extends AuditLogChange[Seq[PermissionOverwrite]]
  case class NSFW(oldValue: Boolean, newValue: Boolean)              extends AuditLogChange[Boolean]
  case class ApplicationId(oldValue: Snowflake, newValue: Snowflake) extends AuditLogChange[Snowflake]
  case class Permissions(oldValue: Permission, newValue: Permission) extends AuditLogChange[Permission]
  case class Color(oldValue: Int, newValue: Int)                     extends AuditLogChange[Int]
  case class Hoist(oldValue: Boolean, newValue: Boolean)             extends AuditLogChange[Boolean]
  case class Mentionable(oldValue: Boolean, newValue: Boolean)       extends AuditLogChange[Boolean]
  case class Allow(oldValue: Permission, newValue: Permission)       extends AuditLogChange[Permission]
  case class Deny(oldValue: Permission, newValue: Permission)        extends AuditLogChange[Permission]
  case class Code(oldValue: Permission, newValue: Permission)        extends AuditLogChange[Permission]

  case class InviteChannelId(oldValue: ChannelId, newValue: ChannelId) extends AuditLogChange[ChannelId]
  case class InviterId(oldValue: UserId, newValue: UserId)             extends AuditLogChange[UserId]
  case class MaxUses(oldValue: Int, newValue: Int)                     extends AuditLogChange[Int]
  case class Uses(oldValue: Int, newValue: Int)                        extends AuditLogChange[Int]
  case class MaxAge(oldValue: Int, newValue: Int)                      extends AuditLogChange[Int]
  case class Temporary(oldValue: Boolean, newValue: Boolean)           extends AuditLogChange[Boolean]
  case class Deaf(oldValue: Boolean, newValue: Boolean)                extends AuditLogChange[Boolean]
  case class Mute(oldValue: Boolean, newValue: Boolean)                extends AuditLogChange[Boolean]
  case class Nick(oldValue: String, newValue: String)                  extends AuditLogChange[String]
  case class AvatarHash(oldValue: String, newValue: String)            extends AuditLogChange[String]
  case class Id(oldValue: Snowflake, newValue: Snowflake)              extends AuditLogChange[Snowflake]
  case class TypeInt(oldValue: Int, newValue: Int)                     extends AuditLogChange[Int]
  case class TypeString(oldValue: String, newValue: String)            extends AuditLogChange[String]
}
