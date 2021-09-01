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

import java.time.{Instant, OffsetDateTime}

import scala.collection.immutable

import ackcord.data.raw.{RawChannel, RawEmoji, RawGuildMember, RawPresence}
import ackcord.util.{IntCirceEnumWithUnknown, StringCirceEnumWithUnknown}
import ackcord.{CacheSnapshot, SnowflakeMap}
import enumeratum.values._

/** A guild which that status of is unknown. */
sealed trait UnknownStatusGuild {

  /** The id of the guild. */
  def id: GuildId
  def unavailable: Option[Boolean]
}

/** The different verification levels that can be used for a guild. */
sealed abstract class VerificationLevel(val value: Int) extends IntEnumEntry
object VerificationLevel extends IntEnum[VerificationLevel] with IntCirceEnumWithUnknown[VerificationLevel] {

  /** Unrestricted access */
  case object NoVerification extends VerificationLevel(0)

  /** Must have a verified email address */
  case object Low extends VerificationLevel(1)

  /** Must be a registered user for more than 5 minutes */
  case object Medium extends VerificationLevel(2)

  /** Must be a member of the guild for more than 10 minutes */
  case object High extends VerificationLevel(3)

  /** Must have a verified phone number */
  case object VeryHigh extends VerificationLevel(4)

  override def values: immutable.IndexedSeq[VerificationLevel] = findValues

  case class Unknown(i: Int) extends VerificationLevel(i)

  override def createUnknown(value: Int): VerificationLevel = Unknown(value)
}

/** The different notification levels that can be used for a guild */
sealed abstract class NotificationLevel(val value: Int) extends IntEnumEntry
object NotificationLevel extends IntEnum[NotificationLevel] with IntCirceEnumWithUnknown[NotificationLevel] {

  /** All messages trigger a notification */
  case object AllMessages extends NotificationLevel(0)

  /** Only mentions trigger a notification */
  case object OnlyMentions extends NotificationLevel(1)

  override def values: immutable.IndexedSeq[NotificationLevel] = findValues

  case class Unknown(i: Int) extends NotificationLevel(i)

  override def createUnknown(value: Int): NotificationLevel = Unknown(value)
}

/** The different explicit content filter levels to use for a guild. */
sealed abstract class FilterLevel(val value: Int) extends IntEnumEntry
object FilterLevel extends IntEnum[FilterLevel] with IntCirceEnumWithUnknown[FilterLevel] {

  /** No filtering is done. */
  case object Disabled extends FilterLevel(0)

  /** Messages from members without roles are filtered */
  case object MembersWithoutRoles extends FilterLevel(1)

  /** All messages are filtered */
  case object AllMembers extends FilterLevel(2)

  override def values: immutable.IndexedSeq[FilterLevel] = findValues

  case class Unknown(i: Int) extends FilterLevel(i)

  override def createUnknown(value: Int): FilterLevel = Unknown(value)
}

sealed abstract class MFALevel(val value: Int) extends IntEnumEntry
object MFALevel extends IntEnum[MFALevel] with IntCirceEnumWithUnknown[MFALevel] {
  override def values: immutable.IndexedSeq[MFALevel] = findValues

  case object NoneMFA        extends MFALevel(0)
  case object Elevated       extends MFALevel(1)
  case class Unknown(i: Int) extends MFALevel(i)

  override def createUnknown(value: Int): MFALevel = Unknown(value)
}

sealed abstract class PremiumTier(val value: Int) extends IntEnumEntry
object PremiumTier extends IntEnum[PremiumTier] with IntCirceEnumWithUnknown[PremiumTier] {
  override def values: immutable.IndexedSeq[PremiumTier] = findValues

  case object None           extends PremiumTier(0)
  case object Tier1          extends PremiumTier(1)
  case object Tier2          extends PremiumTier(2)
  case object Tier3          extends PremiumTier(3)
  case class Unknown(i: Int) extends PremiumTier(i)

  override def createUnknown(value: Int): PremiumTier = Unknown(value)
}

/**
  * A preview of a public guild
  * @param id
  *   The id of the guild.
  * @param name
  *   The name of the guild.
  * @param icon
  *   The icon hash.
  * @param splash
  *   The splash hash.
  * @param discoverySplash
  *   The discovery splash hash.
  * @param emojis
  *   The emojis of the guild.
  * @param features
  *   The enabled guild features.
  * @param approximateMemberCount
  *   An approximate count of the members in the guild.
  * @param approximatePresenceCount
  *   An approximate count of the presences in the guild.
  * @param description
  *   A description for the guild
  */
case class GuildPreview(
    id: GuildId,
    name: String,
    icon: Option[String],
    splash: Option[String],
    discoverySplash: Option[String],
    emojis: Seq[RawEmoji],
    features: Seq[GuildFeature],
    approximateMemberCount: Int,
    approximatePresenceCount: Int,
    description: Option[String]
)

sealed trait Guild extends UnknownStatusGuild {
  override def unavailable: Option[Boolean] = Some(false)

  /** The name of the guild. */
  def name: String

  /** The icon hash. */
  def icon: Option[String]

  /** Used for template objects. */
  def iconHash: Option[String]

  /** The splash hash. */
  def splash: Option[String]

  /** The discovery splash hash. */
  def discoverySplash: Option[String]

  /** The userId of the owner. */
  def ownerId: UserId

  /** The channelId of the AFK channel. */
  def afkChannelId: Option[NormalVoiceGuildChannelId]

  /**
    * The amount of seconds you need to be AFK before being moved to the AFK
    * channel.
    */
  def afkTimeout: Int

  /** If the widget is enabled. */
  def widgetEnabled: Option[Boolean]

  /** The channel id for the widget. */
  def widgetChannelId: Option[GuildChannelId]

  /** The verification level for the guild. */
  def verificationLevel: VerificationLevel

  /** The notification level for the guild. */
  def defaultMessageNotifications: NotificationLevel

  /** The explicit content filter level for the guild. */
  def explicitContentFilter: FilterLevel

  /** The roles of the guild. */
  def roles: SnowflakeMap[Role, Role]

  /** The emojis of the guild. */
  def emojis: SnowflakeMap[Emoji, Emoji]

  /** The enabled guild features. */
  def features: Seq[GuildFeature]

  /** The MFA level. */
  def mfaLevel: MFALevel

  /** The application id if this guild is bot created. */
  def applicationId: Option[ApplicationId]

  /** The channel which notices like welcome and boost messages are sent to. */
  def systemChannelId: Option[TextGuildChannelId]

  /** The flags for the system channel. */
  def systemChannelFlags: SystemChannelFlags

  /** The id for the channel where the rules of a guild are stored. */
  def rulesChannelId: Option[TextGuildChannelId]

  /** The maximum amount of presences in the guild. */
  def maxPresences: Option[Int]

  /** The maximum amount of members in the guild. */
  def maxMembers: Option[Int]

  /** The vanity url code for the guild. */
  def vanityUrlCode: Option[String]

  /** A description for a community guild. */
  def description: Option[String]

  /** A banner hash for the guild. */
  def banner: Option[String]

  /** The premium tier of the guild. */
  def premiumTier: PremiumTier

  /** How many users that are boosting the server. */
  def premiumSubscriptionCount: Option[Int]

  /** The preferred locale of a community guild. */
  def preferredLocale: Option[String]

  /**
    * The channel where admin and mods can see public updates are sent to public
    * guilds.
    */
  def publicUpdatesChannelId: Option[TextGuildChannelId]

  /** The max amount of users in a video call. */
  def maxVideoChannelUsers: Option[Int]

  /** The guild NSFW level. */
  def nsfwLevel: NSFWLevel

  /** Get the everyone role in this guild. */
  def everyoneRole: Role = roles(RoleId(id)) //The everyone role should always be present

  /** Get the everyone mention for this guild. */
  def mentionEveryone: String = "@everyone"

  /** Get the owner this this guild. */
  def owner(implicit c: CacheSnapshot): Option[User] = c.getUser(ownerId)
}

/**
  * A guild or server in Discord. This object is usually gotten from a request.
  *
  * @param isOwner
  *   If the current user is the owner of the guild.
  * @param permissions
  *   The permissions of the current user without overwrites.
  * @param approximateMemberCount
  *   Roughly how many members there is in the guild. Present when gotten from
  *   the [[ackcord.requests.GetGuild]] endpoint with `withCounts = true`
  * @param approximatePresenceCount
  *   Roughly how many presences there is in the guild. Present when gotten from
  *   the [[ackcord.requests.GetGuild]] endpoint with `withCounts = true`
  */
case class RequestsGuild(
    id: GuildId,
    name: String,
    icon: Option[String],
    iconHash: Option[String],
    splash: Option[String],
    discoverySplash: Option[String],
    isOwner: Option[Boolean],
    ownerId: UserId,
    permissions: Option[Permission],
    afkChannelId: Option[NormalVoiceGuildChannelId],
    afkTimeout: Int,
    widgetEnabled: Option[Boolean],
    widgetChannelId: Option[GuildChannelId],
    verificationLevel: VerificationLevel,
    defaultMessageNotifications: NotificationLevel,
    explicitContentFilter: FilterLevel,
    roles: SnowflakeMap[Role, Role],
    emojis: SnowflakeMap[Emoji, Emoji],
    features: Seq[GuildFeature],
    mfaLevel: MFALevel,
    applicationId: Option[ApplicationId],
    systemChannelId: Option[TextGuildChannelId],
    systemChannelFlags: SystemChannelFlags,
    rulesChannelId: Option[TextGuildChannelId],
    maxPresences: Option[Int],
    maxMembers: Option[Int],
    vanityUrlCode: Option[String],
    description: Option[String],
    banner: Option[String],
    premiumTier: PremiumTier,
    premiumSubscriptionCount: Option[Int],
    preferredLocale: Option[String],
    publicUpdatesChannelId: Option[TextGuildChannelId],
    maxVideoChannelUsers: Option[Int],
    approximateMemberCount: Option[Int],
    approximatePresenceCount: Option[Int],
    nsfwLevel: NSFWLevel
) extends Guild {

  def toGatewayGuild(
      joinedAt: OffsetDateTime,
      large: Boolean,
      memberCount: Int,
      voiceStates: Seq[VoiceState],
      members: Seq[RawGuildMember],
      rawChannels: Seq[RawChannel],
      rawThreads: Seq[RawChannel],
      presences: Seq[RawPresence],
      stageInstances: Option[Seq[StageInstance]],
      botUserId: Option[UserId]
  ): GatewayGuild = {
    val channels = rawChannels.flatMap(_.toGuildChannel(id, botUserId))
    val threads =
      rawThreads.flatMap(_.toGuildChannel(id, botUserId)).collect { case thread: ThreadGuildChannel => thread }

    GatewayGuild(
      id,
      name,
      icon,
      iconHash,
      splash,
      discoverySplash,
      ownerId,
      afkChannelId,
      afkTimeout,
      widgetEnabled,
      widgetChannelId,
      verificationLevel,
      defaultMessageNotifications,
      explicitContentFilter,
      roles,
      emojis,
      features,
      mfaLevel,
      applicationId,
      systemChannelId,
      systemChannelFlags,
      rulesChannelId,
      joinedAt,
      large,
      memberCount,
      SnowflakeMap.withKey(voiceStates)(_.userId),
      SnowflakeMap.from(members.map(mem => mem.user.id -> mem.toGuildMember(id))),
      SnowflakeMap.withKey(channels)(_.id),
      SnowflakeMap.withKey(threads)(_.id),
      SnowflakeMap.from(presences.map(p => p.user.id -> p.toPresence)),
      maxPresences,
      maxMembers,
      vanityUrlCode,
      description,
      banner,
      premiumTier,
      premiumSubscriptionCount,
      preferredLocale,
      publicUpdatesChannelId,
      maxVideoChannelUsers,
      nsfwLevel,
      SnowflakeMap.withKey(stageInstances.toSeq.flatten)(_.id)
    )
  }
}

/**
  * A guild or server in Discord received over the Gateway.
  *
  * @param joinedAt
  *   When the client joined the guild.
  * @param large
  *   If this guild is above the large threshold.
  * @param memberCount
  *   The amount of members in the guild.
  * @param voiceStates
  *   The voice states of the guild.
  * @param members
  *   The guild members in the guild.
  * @param channels
  *   The channels in the guild.
  * @param presences
  *   The presences in the guild.
  * @param publicUpdatesChannelId
  *   The channel where admin and mods can see public updates are sent to public
  *   guilds.
  * @param maxVideoChannelUsers
  *   The max amount of users in a video call.
  */
case class GatewayGuild(
    id: GuildId,
    name: String,
    icon: Option[String],
    iconHash: Option[String],
    splash: Option[String],
    discoverySplash: Option[String],
    ownerId: UserId,
    afkChannelId: Option[NormalVoiceGuildChannelId],
    afkTimeout: Int,
    widgetEnabled: Option[Boolean],
    widgetChannelId: Option[GuildChannelId],
    verificationLevel: VerificationLevel,
    defaultMessageNotifications: NotificationLevel,
    explicitContentFilter: FilterLevel,
    roles: SnowflakeMap[Role, Role],
    emojis: SnowflakeMap[Emoji, Emoji],
    features: Seq[GuildFeature],
    mfaLevel: MFALevel,
    applicationId: Option[ApplicationId],
    systemChannelId: Option[TextGuildChannelId],
    systemChannelFlags: SystemChannelFlags,
    rulesChannelId: Option[TextGuildChannelId],
    joinedAt: OffsetDateTime,
    large: Boolean,
    memberCount: Int,
    voiceStates: SnowflakeMap[User, VoiceState], //guildId is absent in those received in GuildCreate
    members: SnowflakeMap[User, GuildMember],
    channels: SnowflakeMap[GuildChannel, GuildChannel],
    threads: SnowflakeMap[ThreadGuildChannel, ThreadGuildChannel],
    presences: SnowflakeMap[User, Presence],
    maxPresences: Option[Int],
    maxMembers: Option[Int],
    vanityUrlCode: Option[String],
    description: Option[String],
    banner: Option[String],
    premiumTier: PremiumTier,
    premiumSubscriptionCount: Option[Int],
    preferredLocale: Option[String],
    publicUpdatesChannelId: Option[TextGuildChannelId],
    maxVideoChannelUsers: Option[Int],
    nsfwLevel: NSFWLevel,
    stageInstances: SnowflakeMap[StageInstance, StageInstance]
) extends Guild {

  /** Get the AFK channel of this guild. */
  def afkChannel: Option[NormalVoiceGuildChannel] =
    afkChannelId.flatMap(channels.get).collect { case ch: NormalVoiceGuildChannel => ch }

  /** Get the widget channel of this guild. */
  def widgetChannel: Option[GuildChannel] = widgetChannelId.flatMap(channels.get)

  /**
    * Get the system channel of this guild. This is the first channel new users
    * see when they join the guild.
    */
  def systemChannel: Option[TextGuildChannel] =
    systemChannelId.flatMap(channels.get).collect { case ch: TextGuildChannel => ch }
}

/**
  * @param description
  *   A description of the server
  * @param welcomeChannels
  *   Channels shown on the welcome screen
  */
case class WelcomeScreen(
    description: Option[String],
    welcomeChannels: Seq[WelcomeScreenChannel]
)

/**
  * @param channelId
  *   The id the channel is referencing.
  * @param description
  *   The description for the channel
  * @param emojiId
  *   The emoji id if it is a custom one
  * @param emojiName
  *   The emoji name if it is not a custom one
  */
case class WelcomeScreenChannel(
    channelId: GuildChannelId,
    description: String,
    emojiId: Option[EmojiId],
    emojiName: Option[String]
)

sealed abstract class NSFWLevel(val value: Int) extends IntEnumEntry
object NSFWLevel extends IntEnum[NSFWLevel] with IntCirceEnumWithUnknown[NSFWLevel] {
  override def values: immutable.IndexedSeq[NSFWLevel] = findValues

  case object Default       extends NSFWLevel(0)
  case object Explicit      extends NSFWLevel(1)
  case object Safe          extends NSFWLevel(2)
  case object AgeRestricted extends NSFWLevel(3)

  case class Unknown(override val value: Int) extends NSFWLevel(value)

  override def createUnknown(value: Int): NSFWLevel = Unknown(value)
}

/**
  * A guild which is not available.
  * @param id
  *   The id of the guild.
  * @param unavailable
  *   If the guild is unavailable because of an outage.
  */
case class UnavailableGuild(id: GuildId, unavailable: Option[Boolean]) extends UnknownStatusGuild

sealed abstract class GuildFeature(val value: String) extends StringEnumEntry
object GuildFeature extends StringEnum[GuildFeature] with StringCirceEnumWithUnknown[GuildFeature] {
  override def values: immutable.IndexedSeq[GuildFeature] = findValues

  case object AnimatedIcon                  extends GuildFeature("ANIMATED_ICON")
  case object Banner                        extends GuildFeature("BANNER")
  case object Commerce                      extends GuildFeature("COMMERCE")
  case object Community                     extends GuildFeature("COMMUNITY")
  case object Discoverable                  extends GuildFeature("DISCOVERABLE")
  case object Featureable                   extends GuildFeature("FEATURABLE")
  case object InviteSplash                  extends GuildFeature("INVITE_SPLASH")
  case object MemberVerificationGateEnabled extends GuildFeature("MEMBER_VERIFICATION_GATE_ENABLED")
  case object News                          extends GuildFeature("NEWS")
  case object Partnered                     extends GuildFeature("PARTNERED")
  case object PreviewEnabled                extends GuildFeature("PREVIEW_ENABLED")
  case object VanityUrl                     extends GuildFeature("VANITY_URL")
  case object Verified                      extends GuildFeature("VERIFIED")
  case object VipRegions                    extends GuildFeature("VIP_REGIONS")
  case object WelcomeScreenEnabled          extends GuildFeature("TICKETED_EVENTS_ENABLED")
  case object TicketedEventsEnabled         extends GuildFeature("WELCOME_SCREEN_ENABLED")
  case object MonetizationEnabled           extends GuildFeature("MONETIZATION_ENABLED")
  case object MoreStickers                  extends GuildFeature("MORE_STICKERS")
  case object ThreeDayThreadArchive         extends GuildFeature("THREE_DAY_THREAD_ARCHIVE")
  case object SevenDayThreadArchive         extends GuildFeature("SEVEN_DAY_THREAD_ARCHIVE")
  case object PrivateThreads                extends GuildFeature("PRIVATE_THREADS")

  case class Unknown(str: String) extends GuildFeature(str)

  override def createUnknown(value: String): GuildFeature = Unknown(value)
}

/**
  * Represents a user in a guild.
  * @param userId
  *   The user of this member.
  * @param guildId
  *   The guild this member belongs to.
  * @param nick
  *   The nickname of this user in this guild.
  * @param roleIds
  *   The roles of this user.
  * @param joinedAt
  *   When this user joined the guild.
  * @param premiumSince
  *   When this user boosted the server.
  * @param deaf
  *   If this user is deaf.
  * @param mute
  *   IF this user is mute.
  * @param pending
  *   True if the member hasn't gotten past the guild screening yet
  */
case class GuildMember(
    userId: UserId,
    guildId: GuildId,
    nick: Option[String],
    roleIds: Seq[RoleId],
    joinedAt: Option[OffsetDateTime],
    premiumSince: Option[OffsetDateTime],
    deaf: Boolean,
    mute: Boolean,
    pending: Option[Boolean]
) extends GetUser
    with GetGuild {

  /** Calculate the permissions of this user */
  def permissions(guild: Guild): Permission = {
    if (guild.ownerId == userId) Permission.All
    else {
      val userPermissions = roleIds.flatMap(guild.roles.get).map(_.permissions)
      val everyonePerms   = guild.everyoneRole.permissions

      val guildPermissions = everyonePerms.addPermissions(Permission(userPermissions: _*))

      if (guildPermissions.hasPermissions(Permission.Administrator)) Permission.All else guildPermissions
    }
  }

  /** Calculate the permissions of this user in a channel. */
  def permissionsWithOverridesId(
      guild: GatewayGuild,
      guildPermissions: Permission,
      channelId: GuildChannelId
  ): Permission = {
    if (guildPermissions.hasPermissions(Permission.Administrator)) Permission.All
    else {
      val res = guild.channels.get(channelId).map { channel =>
        if (guild.ownerId == userId) Permission.All
        else {
          val everyoneOverwrite = channel.permissionOverwrites.get(guild.everyoneRole.id)
          val everyoneAllow     = everyoneOverwrite.map(_.allow)
          val everyoneDeny      = everyoneOverwrite.map(_.deny)

          val rolesForUser   = roleIds.flatMap(guild.roles.get)
          val roleOverwrites = rolesForUser.flatMap(r => channel.permissionOverwrites.get(r.id))
          val roleAllow      = Permission(roleOverwrites.map(_.allow): _*)
          val roleDeny       = Permission(roleOverwrites.map(_.deny): _*)

          val userOverwrite = channel.permissionOverwrites.get(userId)
          val userAllow     = userOverwrite.map(_.allow)
          val userDeny      = userOverwrite.map(_.deny)

          def mapOrElse(
              permission: Permission,
              opt: Option[Permission],
              f: (Permission, Permission) => Permission
          ): Permission =
            opt.map(f(permission, _)).getOrElse(permission)

          def addOrElse(opt: Option[Permission])(permission: Permission): Permission =
            mapOrElse(permission, opt, _.addPermissions(_))
          def removeOrElse(opt: Option[Permission])(permission: Permission): Permission =
            mapOrElse(permission, opt, _.removePermissions(_))

          val withEveryone = (addOrElse(everyoneAllow) _).andThen(removeOrElse(everyoneDeny)).apply(guildPermissions)
          val withRole     = withEveryone.addPermissions(roleAllow).removePermissions(roleDeny)
          val withUser     = (addOrElse(userAllow) _).andThen(removeOrElse(userDeny)).apply(withRole)

          withUser
        }
      }

      res.getOrElse(guildPermissions)
    }
  }

  /** Calculate the permissions of this user in a channel given a guild. */
  def channelPermissionsId(guild: GatewayGuild, channelId: GuildChannelId): Permission =
    permissionsWithOverridesId(guild, permissions(guild), channelId)

  /** Check if this user has any roles above the passed in roles. */
  def hasRoleAboveId(guild: Guild, others: Seq[RoleId]): Boolean = {
    val ownerId = guild.ownerId
    if (this.userId == ownerId) true
    else {
      def maxRolesPosition(roles: Seq[RoleId]): Int = {
        val optList   = roles.toList.map(guild.roles.get(_).map(_.position))
        val positions = optList.flatten
        if (positions.isEmpty) 0 else positions.max
      }

      maxRolesPosition(this.roleIds) > maxRolesPosition(others)
    }
  }

  /** Check if this user has any roles above the passed in roles. */
  def hasRoleAboveId(guild: Guild, other: GuildMember): Boolean =
    if (other.userId == guild.ownerId) false else hasRoleAboveId(guild, other.roleIds)
}

/**
  * An emoji in a guild.
  * @param id
  *   The id of the emoji.
  * @param name
  *   The emoji name.
  * @param roles
  *   The roles that can use this emoji.
  * @param userId
  *   The id of the user that created this emoji.
  * @param requireColons
  *   If the emoji requires colons.
  * @param managed
  *   If the emoji is managed.
  * @param animated
  *   If the emoji is animated.
  * @param available
  *   If the emoji can be used.
  */
case class Emoji(
    id: EmojiId,
    name: String,
    roles: Seq[RoleId],
    userId: Option[UserId],
    requireColons: Option[Boolean],
    managed: Option[Boolean],
    animated: Option[Boolean],
    available: Option[Boolean]
) {

  /** Mention this emoji so it can be formatted correctly in messages. */
  def mention: String =
    if (requireColons.getOrElse(false)) s"<:$name:$id>"
    else if (animated.getOrElse(false)) s"<a:$name:$id>"
    else s"$name"

  /** Returns a string representation of this emoji for use in requests. */
  def asString: String = if (!managed.getOrElse(false)) s"$name:$id" else s"$name"

  /** Get the creator of this emoji if it has one. */
  def creator(implicit c: CacheSnapshot): Option[User] =
    userId.fold(None: Option[User])(c.getUser)
}

/**
  * @param start
  *   When the activity started.
  * @param end
  *   When the activity will end.
  */
case class ActivityTimestamps(start: Option[Instant], end: Option[Instant])

/**
  * @param largeImage
  *   Id for the large asset. Usually a snowflake.
  * @param largeText
  *   Text displayed when hovering over the large image.
  * @param smallImage
  *   Id for the small asset. Usually a snowflake.
  * @param smallText
  *   Text displayed when hovering over the small image.
  */
case class ActivityAsset(
    largeImage: Option[String],
    largeText: Option[String],
    smallImage: Option[String],
    smallText: Option[String]
)

/**
  * @param join
  *   Secret for joining a party.
  * @param spectate
  *   Secret for spectating a game.
  * @param `match`
  *   Secret for a specific instanced match.
  */
case class ActivitySecrets(
    join: Option[String],
    spectate: Option[String],
    `match`: Option[String]
)

/**
  * @param id
  *   The id of the party
  * @param currentSize
  *   The current size of the party.
  * @param maxSize
  *   The max size of the party.
  */
case class ActivityParty(id: Option[String], currentSize: Option[Int], maxSize: Option[Int])

/** The text in a presence */
sealed trait Activity {

  def tpe: ActivityType

  /** When this activity was created. */
  def createdAt: Instant

  /** The text shown */
  def name: String

  /** Timestamps for start and end of activity. */
  def timestamps: Option[ActivityTimestamps]

  /** What the player is doing. */
  def details: Option[String]

  /** Images for the presence and hover texts. */
  def assets: Option[ActivityAsset]
}

sealed abstract class ActivityType(val value: Int) extends IntEnumEntry
object ActivityType extends IntEnum[ActivityType] with IntCirceEnumWithUnknown[ActivityType] {
  override def values: immutable.IndexedSeq[ActivityType] = findValues

  case object Game      extends ActivityType(0)
  case object Streaming extends ActivityType(1)
  case object Listening extends ActivityType(2)
  case object Watching  extends ActivityType(3)
  case object Custom    extends ActivityType(4)
  case object Competing extends ActivityType(5)

  case class Unknown(override val value: Int) extends ActivityType(value)

  override def createUnknown(value: Int): ActivityType = Unknown(value)
}

/**
  * The presence of someone playing a game
  * @param applicationId
  *   Application id of the game.
  * @param state
  *   The user's party status.
  * @param party
  *   Info about the user's party.
  */
case class PresenceGame(
    name: String,
    createdAt: Instant,
    timestamps: Option[ActivityTimestamps],
    applicationId: Option[ApplicationId],
    details: Option[String],
    state: Option[String],
    party: Option[ActivityParty],
    assets: Option[ActivityAsset],
    secrets: Option[ActivitySecrets],
    instance: Option[Boolean],
    flags: Option[ActivityFlags],
    buttons: Option[Seq[String]]
) extends Activity {
  override def tpe: ActivityType = ActivityType.Game
}

/**
  * The presence of someone streaming
  * @param uri
  *   The uri of the stream
  * @param applicationId
  *   Application id of the game.
  * @param state
  *   The user's party status.
  * @param party
  *   Info about the user's party.
  */
case class PresenceStreaming(
    name: String,
    uri: Option[String],
    createdAt: Instant,
    timestamps: Option[ActivityTimestamps],
    applicationId: Option[ApplicationId],
    details: Option[String],
    state: Option[String],
    party: Option[ActivityParty],
    assets: Option[ActivityAsset]
) extends Activity {
  override def tpe: ActivityType = ActivityType.Streaming
}

/** The presence of someone listening to music */
case class PresenceOther(
    tpe: ActivityType,
    name: String,
    createdAt: Instant,
    timestamps: Option[ActivityTimestamps],
    applicationId: Option[ApplicationId],
    details: Option[String],
    assets: Option[ActivityAsset],
    secrets: Option[ActivitySecrets],
    instance: Option[Boolean],
    flags: Option[ActivityFlags],
    buttons: Option[Seq[String]]
) extends Activity

case class PresenceCustom(
    name: String,
    createdAt: Instant,
    state: Option[String],
    emoji: Option[ActivityEmoji]
) extends Activity {

  override def timestamps: Option[ActivityTimestamps] = None

  override def details: Option[String] = None

  override def assets: Option[ActivityAsset] = None

  override def tpe: ActivityType = ActivityType.Custom
}

/** The emoji of a custom status. */
case class ActivityEmoji(
    name: String,
    id: Option[EmojiId],
    animated: Option[Boolean]
)

/** The different statuses a user can have */
sealed abstract class PresenceStatus(val value: String) extends StringEnumEntry
object PresenceStatus extends StringEnum[PresenceStatus] with StringCirceEnumWithUnknown[PresenceStatus] {
  override def values: immutable.IndexedSeq[PresenceStatus] = findValues

  case object Online              extends PresenceStatus("online")
  case object DoNotDisturb        extends PresenceStatus("dnd")
  case object Idle                extends PresenceStatus("idle")
  case object Invisible           extends PresenceStatus("invisible")
  case object Offline             extends PresenceStatus("offline")
  case class Unknown(str: String) extends PresenceStatus(str)

  override def createUnknown(value: String): PresenceStatus = Unknown(value)
}

/**
  * The status of a user per platform. Not present if the user is offline, or
  * invisible.
  */
case class ClientStatus(
    desktop: Option[PresenceStatus],
    mobile: Option[PresenceStatus],
    web: Option[PresenceStatus]
)

/**
  * The presence for a user
  * @param userId
  *   The user id
  * @param status
  *   The status of the user
  * @param clientStatus
  *   The status of the user over several platforms
  */
case class Presence(userId: UserId, status: PresenceStatus, activities: Seq[Activity], clientStatus: ClientStatus)
    extends GetUser

sealed trait Integration {

  /** The id of the integration. */
  def id: IntegrationId

  /** The integration name. */
  def name: String

  /** The type of the integration. */
  def `type`: IntegrationType

  /** If the integration is enabled. */
  def enabled: Boolean

  /** Account information. */
  def account: IntegrationAccount
}

/**
  * A discord bot/OAuth2 integration.
  * @param id
  *   The id of the integration
  * @param name
  *   The integration name
  * @param enabled
  *   If the integration is enabled
  * @param account
  *   Account information
  * @param application
  *   The bot/OAuth2 application
  */
case class DiscordIntegration(
    id: IntegrationId,
    name: String,
    enabled: Boolean,
    account: IntegrationAccount,
    application: IntegrationApplication
) extends Integration {

  override def `type`: IntegrationType = IntegrationType.Discord
}

/**
  * A server integration
  * @param id
  *   The id of the integration
  * @param name
  *   The integration name
  * @param `type`
  *   The type of the integration
  * @param enabled
  *   If the integration is enabled
  * @param syncing
  *   If the integration is synced
  * @param roleId
  *   Role that this integration uses for subscribers, or guild id for Discord
  *   integrations
  * @param expireBehavior
  *   The behavior of expiring subscribers.
  * @param expireGracePeriod
  *   The grace period before expiring subscribers.
  * @param user
  *   The user for this integration
  * @param account
  *   Account information
  * @param syncedAt
  *   When the integration last synced'
  * @param subscriberCount
  *   How many subscribers this integration has. 0 for Discord
  * @param revoked
  *   If this integration has been revoked
  */
case class ExternalIntegration(
    id: IntegrationId,
    name: String,
    `type`: IntegrationType,
    enabled: Boolean,
    syncing: Boolean,
    roleId: RoleId,
    enableEmoticons: Option[Boolean],
    expireBehavior: IntegrationExpireBehavior,
    expireGracePeriod: Int,
    user: Option[User],
    account: IntegrationAccount,
    syncedAt: OffsetDateTime,
    subscriberCount: Int,
    revoked: Boolean,
    application: Option[IntegrationApplication]
) extends Integration

sealed abstract class IntegrationType(val value: String) extends StringEnumEntry
object IntegrationType extends StringEnum[IntegrationType] with StringCirceEnumWithUnknown[IntegrationType] {
  override def values: immutable.IndexedSeq[IntegrationType] = findValues

  case object Twitch            extends IntegrationType("twitch")
  case object Youtube           extends IntegrationType("youtube")
  case object Discord           extends IntegrationType("discord")
  case class Unknown(s: String) extends IntegrationType(s)

  override def createUnknown(value: String): IntegrationType = Unknown(value)
}

sealed abstract class IntegrationExpireBehavior(val value: Int) extends IntEnumEntry
object IntegrationExpireBehavior
    extends IntEnum[IntegrationExpireBehavior]
    with IntCirceEnumWithUnknown[IntegrationExpireBehavior] {
  override def values: immutable.IndexedSeq[IntegrationExpireBehavior] = findValues

  case object RemoveRole     extends IntegrationExpireBehavior(0)
  case object Kick           extends IntegrationExpireBehavior(1)
  case class Unknown(i: Int) extends IntegrationExpireBehavior(i)

  override def createUnknown(value: Int): IntegrationExpireBehavior = Unknown(value)
}

/**
  * @param id
  *   The id of the application
  * @param name
  *   The name of the application
  * @param icon
  *   The icon hash of the application
  * @param description
  *   The description of the application
  * @param summary
  *   The summary of the application
  * @param bot
  *   The bot user of the application
  */
case class IntegrationApplication(
    id: ApplicationId,
    name: String,
    icon: Option[String],
    description: String,
    summary: String,
    bot: Option[User]
)

/**
  * @param id
  *   The id of the account
  * @param name
  *   The name of the account
  */
case class IntegrationAccount(id: String, name: String)

case class GuildWidgetSettings(enabled: Boolean, channelId: Option[GuildChannelId])

/** The object returned when getting the widget for a guild */
case class GuildWidget(
    id: GuildId,
    name: String,
    instantInvite: String,
    channels: Seq[GuildWidgetChannel],
    members: Seq[GuildWidgetMember],
    presenceCount: Int
)

case class GuildWidgetChannel(
    id: GuildChannelId,
    name: String,
    position: Int
)

case class GuildWidgetMember(
    id: UserId,
    username: String,
    discriminator: String,
    avatar: Option[String],
    status: PresenceStatus,
    avatarUrl: String
)

/**
  * Represents a banned user.
  * @param reason
  *   Why the user was banned.
  * @param userId
  *   The user that was baned.
  */
case class Ban(reason: Option[String], userId: UserId) {

  /** Get the user this ban applies to. */
  def user(implicit c: CacheSnapshot): Option[User] = c.getUser(userId)
}
