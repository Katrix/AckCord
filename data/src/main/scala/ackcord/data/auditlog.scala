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

import scala.collection.immutable

import ackcord.CacheSnapshot
import ackcord.data.raw.RawChannel
import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.{IntEnum, IntEnumEntry}

/**
  * Root audit log object. Received from [[ackcord.requests.GetGuildAuditLog]]
  *
  * @param auditLogEntries
  *   The entries of the log
  * @param users
  *   The users found in the log
  * @param webhooks
  *   The webhooks found in the log
  */
case class AuditLog(
    auditLogEntries: Seq[AuditLogEntry],
    integrations: Seq[PartialIntegration],
    threads: Seq[RawChannel],
    users: Seq[User],
    webhooks: Seq[Webhook]
)

/**
  * An individual audit log event
  * @param targetId
  *   The id of the affected object
  * @param changes
  *   The changes made to the object
  * @param userId
  *   The user responsible for the changes
  * @param id
  *   The id of this entry
  * @param actionType
  *   Type of change that happened
  * @param options
  *   Optional extra data for some changes, see comments on
  *   [[OptionalAuditLogInfo]] for more info
  * @param reason
  *   The reason for the change
  */
case class AuditLogEntry(
    targetId: Option[RawSnowflake],
    changes: Option[Seq[AuditLogChange[_]]],
    userId: Option[UserId],
    id: RawSnowflake,
    actionType: AuditLogEvent,
    options: Option[OptionalAuditLogInfo],
    reason: Option[String]
)

case class PartialIntegration(
    id: IntegrationId,
    name: String,
    `type`: String,
    account: IntegrationAccount
)

/** A type of change that an entry can represent */
sealed abstract class AuditLogEvent(val value: Int) extends IntEnumEntry
object AuditLogEvent extends IntEnum[AuditLogEvent] with IntCirceEnumWithUnknown[AuditLogEvent] {
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
  case object MemberMove             extends AuditLogEvent(26)
  case object MemberDisconnect       extends AuditLogEvent(27)
  case object BotAdd                 extends AuditLogEvent(28)
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
  case object MessageBulkDelete      extends AuditLogEvent(73)
  case object MessagePin             extends AuditLogEvent(74)
  case object MessageUnpin           extends AuditLogEvent(75)
  case object IntegrationCreate      extends AuditLogEvent(80)
  case object IntegrationUpdate      extends AuditLogEvent(81)
  case object IntegrationDelete      extends AuditLogEvent(82)
  case object StageInstanceCreate    extends AuditLogEvent(83)
  case object StageInstanceUpdate    extends AuditLogEvent(84)
  case object StageInstanceDelete    extends AuditLogEvent(85)
  case object StickerCreate          extends AuditLogEvent(90)
  case object StickerUpdate          extends AuditLogEvent(91)
  case object StickerDelete          extends AuditLogEvent(92)
  case object ThreadCreate           extends AuditLogEvent(110)
  case object ThreadUpdate           extends AuditLogEvent(111)
  case object ThreadDelete           extends AuditLogEvent(112)

  override def values: immutable.IndexedSeq[AuditLogEvent] = findValues

  case class Unknown(i: Int) extends AuditLogEvent(i)

  override def createUnknown(value: Int): AuditLogEvent = Unknown(value)
}

/**
  * Extra data for an entry
  * @param channelId
  *   The channelId of the deleted message. Present for MemberMove, MessagePin,
  *   MessageUnpin, MessageDelete, StageInstanceCreate, StageInstanceUpdate and
  *   StageInstanceDelete.
  * @param count
  *   The amount of deleted messages. Present for MessageDelete,
  *   MessageBulkDelete, MemberDisconnect and MemberMove.
  * @param deleteMemberDays
  *   The amount of days before a user was considered inactive and kicked.
  *   Present for MemberPrune.
  * @param id
  *   The id of the overwritten object. Present for overwrite events.
  * @param membersRemoved
  *   The amount of members removed. Present for MemberPrune.
  * @param messageId
  *   The message that was targeted. Present for MessagePin and MessageUnpin.
  * @param roleName
  *   The name of the role. Present for overwrite events if type == Role.
  * @param `type`
  *   The type of the overwritten object. Present for overwrite events.
  */
case class OptionalAuditLogInfo(
    channelId: Option[TextChannelId],
    count: Option[String],
    deleteMemberDays: Option[String],
    id: Option[UserOrRoleId],
    membersRemoved: Option[String],
    messageId: Option[MessageId],
    roleName: Option[String],
    `type`: Option[PermissionOverwriteType]
)

/**
  * Some sort of change
  * @tparam A
  *   The data type that changed
  */
sealed trait AuditLogChange[A] {

  /** The new value */
  def newValue: Option[A]

  /** The old value */
  def oldValue: Option[A]
}
object AuditLogChange {
  import ackcord.data

  /** AFK channelId changed */
  case class AfkChannelId(oldValue: Option[NormalVoiceGuildChannelId], newValue: Option[NormalVoiceGuildChannelId])
      extends AuditLogChange[NormalVoiceGuildChannelId] {

    def oldChannel(implicit c: CacheSnapshot): Option[NormalVoiceGuildChannel] =
      oldValue.flatMap(c.getGuildChannel).collect { case ch: NormalVoiceGuildChannel => ch }

    def newChannel(implicit c: CacheSnapshot): Option[NormalVoiceGuildChannel] =
      newValue.flatMap(c.getGuildChannel).collect { case ch: NormalVoiceGuildChannel => ch }
  }

  /** AFK timeout changed */
  case class AfkTimeout(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Permission was allowed for a role on a channel */
  case class Allow(oldValue: Option[Permission], newValue: Option[Permission]) extends AuditLogChange[Permission]

  /** ApplicationId of webhook or bot */
  case class ApplicationId(oldValue: Option[data.ApplicationId], newValue: Option[data.ApplicationId])
      extends AuditLogChange[data.ApplicationId]

  /** Thread is archived/unarchived */
  case class Archived(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** Empty string */
  case class Asset(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Auto archive duration changed */
  case class AutoArchiveDuration(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Availability of a sticker changed */
  case class Available(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** Avatar hash changed */
  case class AvatarHash(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Banner hash changed */
  case class BannerHash(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Voice channel bitrate changed */
  case class Bitrate(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Invite channelId changed */
  case class InviteChannelId(oldValue: Option[GuildChannelId], newValue: Option[GuildChannelId])
    extends AuditLogChange[GuildChannelId] {

    def oldChannel(implicit c: CacheSnapshot): Option[TextGuildChannel] =
      oldValue.flatMap(c.getGuildChannel).collect { case ch: TextGuildChannel => ch }

    def newChannel[F[_]](implicit c: CacheSnapshot): Option[TextGuildChannel] =
      newValue.flatMap(c.getGuildChannel).collect { case ch: TextGuildChannel => ch }
  }

  /** Invite code changed */
  case class Code(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Color of role changed */
  case class Color(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Deaf for user changed */
  case class Deaf(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** Default auto archive duration for new threads changed */
  case class DefaultAutoArchiveDuration(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Default message notification level changed */
  case class DefaultMessageNotification(
      oldValue: Option[data.NotificationLevel],
      newValue: Option[data.NotificationLevel]
  ) extends AuditLogChange[data.NotificationLevel]

  /** Permission was denied for role on a channel */
  case class Deny(oldValue: Option[Permission], newValue: Option[Permission]) extends AuditLogChange[Permission]

  /** Description changed */
  case class Description(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Discovery splash hash changed */
  case class DiscoverySplashHash(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Integration emoticons enabled/disabled changed */
  case class EnableEmoticons(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** Integration expire behavior changed */
  case class ExpireBehavior(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Integration grace period changed */
  case class ExpireGracePeriod(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Explicit content filter changed */
  case class ExplicitContentFilter(oldValue: Option[data.FilterLevel], newValue: Option[data.FilterLevel])
      extends AuditLogChange[data.FilterLevel]

  /** Format type of a sticker changed */
  case class FormatType(oldValue: Option[data.FormatType], newValue: Option[data.FormatType])
      extends AuditLogChange[data.FormatType]

  /** Guild sticker changed */
  case class GuildIdChange(oldValue: Option[data.GuildId], newValue: Option[data.GuildId])
      extends AuditLogChange[data.GuildId]

  /** Hoist of role changed */
  case class Hoist(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** Icon hash changed */
  case class IconHash(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Id of changed object */
  case class Id(oldValue: Option[RawSnowflake], newValue: Option[RawSnowflake]) extends AuditLogChange[RawSnowflake]

  /** Inviter userId changed */
  case class InviterId(oldValue: Option[UserId], newValue: Option[UserId]) extends AuditLogChange[UserId] {

    def oldInvited(implicit c: CacheSnapshot): Option[User] =
      oldValue.flatMap(c.getUser)

    def newInvited(implicit c: CacheSnapshot): Option[User] =
      newValue.flatMap(c.getUser)
  }

  /** Thread is locked/unlocked */
  case class Locked(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** Max age of invite changed */
  case class MaxAge(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Max uses of an invite changed */
  case class MaxUses(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Mentionable of role changed */
  case class Mentionable(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** MFA level changed */
  case class MfaLevel(oldValue: Option[data.MFALevel], newValue: Option[data.MFALevel])
      extends AuditLogChange[data.MFALevel]

  /** Mute for user changed */
  case class Mute(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** Name changed */
  case class Name(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Nick for user changed */
  case class Nick(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** NSFW for channel changed */
  case class NSFW(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** Owner id changed */
  case class OwnerId(oldValue: Option[UserId], newValue: Option[UserId]) extends AuditLogChange[UserId] {

    def oldOwner(implicit c: CacheSnapshot): Option[User] =
      oldValue.flatMap(c.getUser)

    def newOwner(implicit c: CacheSnapshot): Option[User] =
      newValue.flatMap(c.getUser)
  }

  /** Permission overwrites for channel changed */
  case class PermissionOverwrites(
      oldValue: Option[Seq[PermissionOverwrite]],
      newValue: Option[Seq[PermissionOverwrite]]
  ) extends AuditLogChange[Seq[PermissionOverwrite]]

  /** Permissions of role changed */
  case class Permissions(oldValue: Option[Permission], newValue: Option[Permission]) extends AuditLogChange[Permission]

  /** Channel position changed */
  case class Position(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Region changed */
  case class PreferredLocale(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Privacy level of a stage instance */
  case class PrivacyLevel(oldValue: Option[StageInstancePrivacyLevel], newValue: Option[StageInstancePrivacyLevel])
      extends AuditLogChange[StageInstancePrivacyLevel]

  /** Prune delete duration changed */
  case class PruneDeleteDays(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Public updates channel changed */
  case class PublicUpdatesChannelId(oldValue: Option[TextGuildChannelId], newValue: Option[TextGuildChannelId])
      extends AuditLogChange[TextGuildChannelId]

  /** Ratelimit changed */
  case class RateLimitPerUser(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Region changed */
  case class Region(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Rules channel changed */
  case class RulesChannelId(oldValue: Option[TextGuildChannelId], newValue: Option[TextGuildChannelId])
      extends AuditLogChange[TextGuildChannelId]

  /** Splash hash changed */
  case class SplashHash(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** System channelId changed */
  case class SystemChannelId(oldValue: Option[TextGuildChannelId], newValue: Option[TextGuildChannelId])
      extends AuditLogChange[TextGuildChannelId] {

    def oldChannel(implicit c: CacheSnapshot): Option[GuildChannel] =
      oldValue.flatMap(c.getGuildChannel)

    def newChannel(implicit c: CacheSnapshot): Option[GuildChannel] =
      newValue.flatMap(c.getGuildChannel)
  }

  /** Related emoji of a sticker changed */
  case class Tags(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** If invite is temporary changed */
  case class Temporary(oldValue: Option[Boolean], newValue: Option[Boolean]) extends AuditLogChange[Boolean]

  /** Channel or stage instance topic changed */
  case class Topic(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Type of object changed */
  case class TypeInt(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Type of created object */
  case class TypeString(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Role unicode emoji changed */
  case class UnicodeEmoji(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** New user limit in a voice channel */
  case class UserLimit(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Amount of times an invite has been used changed */
  case class Uses(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  /** Guild invite vanity url changed */
  case class VanityUrlCode(oldValue: Option[String], newValue: Option[String]) extends AuditLogChange[String]

  /** Required verification level changed */
  case class VerificationLevel(oldValue: Option[data.VerificationLevel], newValue: Option[data.VerificationLevel])
      extends AuditLogChange[data.VerificationLevel]

  /** Widget channelId changed */
  case class WidgetChannelId(oldValue: Option[GuildChannelId], newValue: Option[GuildChannelId])
    extends AuditLogChange[GuildChannelId] {

    def oldChannel(implicit c: CacheSnapshot): Option[GuildChannel] =
      oldValue.flatMap(c.getGuildChannel)

    def newChannel(implicit c: CacheSnapshot): Option[GuildChannel] =
      newValue.flatMap(c.getGuildChannel)
  }

  /** Widget enabled changed */
  case class WidgetEnabled(oldValue: Option[Int], newValue: Option[Int]) extends AuditLogChange[Int]

  case class PartialRole(
      name: String,
      id: RoleId
  )

  /** Role added */
  case class $Add(oldValue: Option[Seq[PartialRole]], newValue: Option[Seq[PartialRole]])
      extends AuditLogChange[Seq[PartialRole]]

  /** Role removed */
  case class $Remove(oldValue: Option[Seq[PartialRole]], newValue: Option[Seq[PartialRole]])
      extends AuditLogChange[Seq[PartialRole]]
}
