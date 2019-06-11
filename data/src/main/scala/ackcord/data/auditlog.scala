/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
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

package ackcord.data

import scala.language.higherKinds

import scala.collection.immutable

import ackcord.CacheSnapshot
import ackcord.data.raw.RawRole
import cats.Monad
import cats.data.OptionT
import enumeratum.values.{IntCirceEnum, IntEnum, IntEnumEntry}

/**
  * Root audit log object. Received from [[ackcord.requests.GetGuildAuditLog]]
  *
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
) extends GetUser

/**
  * A type of change that an entry can represent
  */
sealed abstract class AuditLogEvent(val value: Int) extends IntEnumEntry
object AuditLogEvent extends IntEnum[AuditLogEvent] with IntCirceEnum[AuditLogEvent] {
  case object GuildUpdate            extends AuditLogEvent(1)
  case object ChannelCreate          extends AuditLogEvent(10)
  case object ChannelUpdate          extends AuditLogEvent(11)
  case object ChannelDelete          extends AuditLogEvent(12)
  case object ChannelOverwriteCreate extends AuditLogEvent(13)
  case object ChannelOverwriteUpdate extends AuditLogEvent(14)
  case object ChannelOverwriteDelete extends AuditLogEvent(15)
  case object MemberKick             extends AuditLogEvent(20)
  case object MemberPrune            extends AuditLogEvent(21)
  case object MemberBanAdd           extends AuditLogEvent(22)
  case object MemberBanRemove        extends AuditLogEvent(23)
  case object MemberUpdate           extends AuditLogEvent(24)
  case object MemberRoleUpdate       extends AuditLogEvent(25)
  case object RoleCreate             extends AuditLogEvent(30)
  case object RoleUpdate             extends AuditLogEvent(31)
  case object RoleDelete             extends AuditLogEvent(32)
  case object InviteCreate           extends AuditLogEvent(40)
  case object InviteUpdate           extends AuditLogEvent(41)
  case object InviteDelete           extends AuditLogEvent(42)
  case object WebhookCreate          extends AuditLogEvent(50)
  case object WebhookUpdate          extends AuditLogEvent(51)
  case object WebhookDelete          extends AuditLogEvent(52)
  case object EmojiCreate            extends AuditLogEvent(60)
  case object EmojiUpdate            extends AuditLogEvent(61)
  case object EmojiDelete            extends AuditLogEvent(62)
  case object MessageDelete          extends AuditLogEvent(72)

  override def values: immutable.IndexedSeq[AuditLogEvent] = findValues

  @deprecated("Prefer AuditLogEvent#value", since = "0.14.0")
  def idOf(event: AuditLogEvent): Int = event.value

  @deprecated("Prefer AuditLogEvent.withValueOpt", since = "0.14.0")
  def fromId(id: Int): Option[AuditLogEvent] = withValueOpt(id)
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
  import ackcord.data

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
  case class OwnerId(oldValue: Option[UserId], newValue: Option[UserId]) extends AuditLogChange[UserId] {

    def oldOwner[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, User] =
      OptionT.fromOption[F](oldValue).flatMap(c.getUser)

    def newOwner[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, User] =
      OptionT.fromOption[F](newValue).flatMap(c.getUser)
  }

  /**
    * Region changed
    */
  case class Region(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /**
    * AFK channelId changed
    */
  case class AfkChannelId(oldValue: Option[ChannelId], newValue: Option[ChannelId]) extends AuditLogChange[ChannelId] {

    def oldChannel[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, VGuildChannel] =
      OptionT.fromOption[F](oldValue).flatMap(c.getGuildChannel).collect {
        case ch: VGuildChannel => ch
      }

    def newChannel[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, VGuildChannel] =
      OptionT.fromOption[F](newValue).flatMap(c.getGuildChannel).collect {
        case ch: VGuildChannel => ch
      }
  }

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
  case class $Add(oldValue: Option[Seq[RawRole]], newValue: Option[Seq[RawRole]]) extends AuditLogChange[Seq[RawRole]]

  /**
    * Role removed
    */
  case class $Remove(oldValue: Option[Seq[RawRole]], newValue: Option[Seq[RawRole]])
      extends AuditLogChange[Seq[RawRole]]

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
  case class WidgetChannelId(oldValue: Option[ChannelId], newValue: Option[ChannelId])
      extends AuditLogChange[ChannelId] {

    def oldChannel[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, GuildChannel] =
      OptionT.fromOption[F](oldValue).flatMap(c.getGuildChannel)

    def newChannel[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, GuildChannel] =
      OptionT.fromOption[F](newValue).flatMap(c.getGuildChannel)
  }

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
  case class InviteChannelId(oldValue: Option[ChannelId], newValue: Option[ChannelId])
      extends AuditLogChange[ChannelId] {

    def oldChannel[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, TGuildChannel] =
      OptionT.fromOption[F](oldValue).flatMap(c.getGuildChannel).collect {
        case ch: TGuildChannel => ch
      }

    def newChannel[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, TGuildChannel] =
      OptionT.fromOption[F](newValue).flatMap(c.getGuildChannel).collect {
        case ch: TGuildChannel => ch
      }
  }

  /**
    * Inviter userId changed
    */
  case class InviterId(oldValue: Option[UserId], newValue: Option[UserId]) extends AuditLogChange[UserId] {

    def oldInvited[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, User] =
      OptionT.fromOption[F](oldValue).flatMap(c.getUser)

    def newInvited[F[_]](implicit c: CacheSnapshot[F], F: Monad[F]): OptionT[F, User] =
      OptionT.fromOption[F](newValue).flatMap(c.getUser)
  }

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
