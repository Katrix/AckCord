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

import ackcord.{CacheSnapshot, SnowflakeMap}
import enumeratum.values._

/**
  * A guild which that status of is unknown.
  */
sealed trait UnknownStatusGuild {
  def id: GuildId
  def unavailable: Boolean
}

/**
  * The different verification levels that can be used for a guild.
  */
sealed abstract class VerificationLevel(val value: Int) extends IntEnumEntry
object VerificationLevel extends IntEnum[VerificationLevel] with IntCirceEnum[VerificationLevel] {

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
}

/**
  * The different notification levels that can be used for a guild
  */
sealed abstract class NotificationLevel(val value: Int) extends IntEnumEntry
object NotificationLevel extends IntEnum[NotificationLevel] with IntCirceEnum[NotificationLevel] {

  /** All messages trigger a notification */
  case object AllMessages extends NotificationLevel(0)

  /** Only mentions trigger a notification */
  case object OnlyMentions extends NotificationLevel(1)

  override def values: immutable.IndexedSeq[NotificationLevel] = findValues
}

/**
  * The different explicit content filter levels to use for a guild.
  */
sealed abstract class FilterLevel(val value: Int) extends IntEnumEntry
object FilterLevel extends IntEnum[FilterLevel] with IntCirceEnum[FilterLevel] {

  /** No filtering is done. */
  case object Disabled extends FilterLevel(0)

  /** Messages from members without roles are filtered */
  case object MembersWithoutRoles extends FilterLevel(1)

  /** All messages are filtered */
  case object AllMembers extends FilterLevel(2)

  override def values: immutable.IndexedSeq[FilterLevel] = findValues
}

sealed abstract class MFALevel(val value: Int) extends IntEnumEntry
object MFALevel extends IntEnum[MFALevel] with IntCirceEnum[MFALevel] {
  case object NoneMFA  extends MFALevel(0)
  case object Elevated extends MFALevel(1)

  override def values: immutable.IndexedSeq[MFALevel] = findValues
}

sealed abstract class PremiumTier(val value: Int) extends IntEnumEntry
object PremiumTier extends IntEnum[PremiumTier] with IntCirceEnum[PremiumTier] {
  case object None  extends PremiumTier(0)
  case object Tier1 extends PremiumTier(1)
  case object Tier2 extends PremiumTier(2)
  case object Tier3 extends PremiumTier(3)

  override def values: immutable.IndexedSeq[PremiumTier] = findValues
}

/**
  * A guild or server in Discord.
  * @param id The id of the guild.
  * @param name The name of the guild.
  * @param icon The icon hash.
  * @param splash The splash hash.
  * @param isOwner If the current user is the owner of the guild.
  * @param ownerId The userId of the owner.
  * @param permissions The permissions of the current user without overwrites.
  * @param region The voice region
  * @param afkChannelId The channelId of the AFK channel.
  * @param afkTimeout The amount of seconds you need to be AFK before being
  *                   moved to the AFK channel.
  * @param embedEnabled If the embed is enabled.
  * @param embedChannelId The channelId for the embed.
  * @param verificationLevel The verification level for the guild.
  * @param defaultMessageNotifications The notification level for the guild.
  * @param explicitContentFilter The explicit content filter level for the guild.
  * @param roles The roles of the guild.
  * @param emojis The emojis of the guild.
  * @param features The enabled guild features.
  * @param mfaLevel The MFA level.
  * @param applicationId The application id if this guild is bot created.
  * @param widgetEnabled If the widget is enabled.
  * @param widgetChannelId The channel id for the widget.
  * @param systemChannelId The channel which system messages are sent to.
  * @param joinedAt When the client joined the guild.
  * @param large If this guild is above the large threshold.
  * @param memberCount The amount of members in the guild.
  * @param voiceStates The voice states of the guild.
  * @param members The guild members in the guild.
  * @param channels The channels in the guild.
  * @param presences The presences in the guild.
  * @param maxPresences The maximum amount of presences in the guild.
  * @param maxMembers The maximum amount of members in the guild.
  * @param vanityUrlCode The vanity url code for the guild.
  * @param description A descriptiom fpr the guild.
  * @param banner A banner hash for the guild.
  * @param premiumTier The premium tier of the guild.
  * @param premiumSubscriptionCount How many users that are boosting the server.
  */
case class Guild(
    id: GuildId,
    name: String,
    icon: Option[String],
    splash: Option[String],
    isOwner: Option[Boolean],
    ownerId: UserId,
    permissions: Option[Permission],
    region: String,
    afkChannelId: Option[ChannelId],
    afkTimeout: Int,
    embedEnabled: Option[Boolean],
    embedChannelId: Option[ChannelId],
    verificationLevel: VerificationLevel,
    defaultMessageNotifications: NotificationLevel,
    explicitContentFilter: FilterLevel,
    roles: SnowflakeMap[Role, Role],
    emojis: SnowflakeMap[Emoji, Emoji],
    features: Seq[GuildFeature], //TODO: What is a feature?
    mfaLevel: MFALevel,
    applicationId: Option[RawSnowflake],
    widgetEnabled: Option[Boolean],
    widgetChannelId: Option[ChannelId],
    systemChannelId: Option[ChannelId],
    joinedAt: OffsetDateTime,
    large: Boolean,
    memberCount: Int,
    voiceStates: SnowflakeMap[User, VoiceState], //guildId is absent in those received in GuildCreate
    members: SnowflakeMap[User, GuildMember],
    channels: SnowflakeMap[Channel, GuildChannel],
    presences: SnowflakeMap[User, Presence],
    maxPresences: Int,
    maxMembers: Option[Int],
    vanityUrlCode: Option[String],
    description: Option[String],
    banner: Option[String],
    premiumTier: PremiumTier,
    premiumSubscriptionCount: Option[Int],
    preferredLocale: Option[String]
) extends UnknownStatusGuild {
  override def unavailable: Boolean = false

  /**
    * Get the everyone role in this guild.
    */
  def everyoneRole: Role = roles(RoleId(id)) //The everyone role should always be present

  /**
    * Get the everyone mention for this guild.
    */
  def mentionEveryone: String = "@everyone"

  /**
    * Get the owner this this guild.
    */
  def owner(implicit c: CacheSnapshot): Option[User] = c.getUser(ownerId)

  /**
    * Get the AFK channel of this guild.
    */
  def afkChannel: Option[VGuildChannel] = afkChannelId.flatMap(channels.get).collect {
    case ch: VGuildChannel => ch
  }

  /**
    * Get the AFK channel of this guild.
    */
  def embedChannel: Option[GuildChannel] = embedChannelId.flatMap(channels.get)

  /**
    * Get the widget channel of this guild.
    */
  def widgetChannel: Option[GuildChannel] = widgetChannelId.flatMap(channels.get)

  /**
    * Get the system channel of this guild. This is the first channel new users
    * see when they join the guild.
    */
  def systemChannel: Option[TGuildChannel] = systemChannelId.flatMap(channels.get).collect {
    case ch: TGuildChannel => ch
  }
}

/**
  * A guild which is not available.
  * @param id The id of the guild.
  * @param unavailable If the guild is unavailable.
  */
case class UnavailableGuild(id: GuildId, unavailable: Boolean) extends UnknownStatusGuild

sealed abstract class GuildFeature(val value: String) extends StringEnumEntry
object GuildFeature extends StringEnum[GuildFeature] with StringCirceEnum[GuildFeature] {
  override def values: immutable.IndexedSeq[GuildFeature] = findValues

  case object InviteSplash extends GuildFeature("INVITE_SPLASH")
  case object VipRegions   extends GuildFeature("VIP_REGIONS")
  case object VanityUrl    extends GuildFeature("VANITY_URL")
  case object Verified     extends GuildFeature("VERIFIED")
  case object Partnered    extends GuildFeature("PARTNERED")
  case object Public       extends GuildFeature("PUBLIC")
  case object Commerce     extends GuildFeature("COMMERCE")
  case object News         extends GuildFeature("NEWS")
  case object Discoverable extends GuildFeature("DISCOVERABLE")
  case object Featureable  extends GuildFeature("FEATURABLE")
  case object AnimatedIcon extends GuildFeature("ANIMATED_ICON")
  case object Banner       extends GuildFeature("BANNER")
}

/**
  * Represents a user in a guild.
  * @param userId The user of this member.
  * @param guildId The guild this member belongs to.
  * @param nick The nickname of this user in this guild.
  * @param roleIds The roles of this user.
  * @param joinedAt When this user joined the guild.
  * @param premiumSince When this user boosted the server.
  * @param deaf If this user is deaf.
  * @param mute IF this user is mute.
  */
case class GuildMember(
    userId: UserId,
    guildId: GuildId,
    nick: Option[String],
    roleIds: Seq[RoleId],
    joinedAt: OffsetDateTime,
    premiumSince: Option[OffsetDateTime],
    deaf: Boolean,
    mute: Boolean
) extends GetUser
    with GetGuild {

  /**
    * Calculate the permissions of this user
    */
  def permissions(guild: Guild): Permission = {
    if (guild.ownerId == userId) Permission.All
    else {
      val userPermissions = roleIds.flatMap(guild.roles.get).map(_.permissions)
      val everyonePerms   = guild.everyoneRole.permissions

      val guildPermissions = everyonePerms.addPermissions(Permission(userPermissions: _*))

      if (guildPermissions.hasPermissions(Permission.Administrator)) Permission.All else guildPermissions
    }
  }

  /**
    * Calculate the permissions of this user in a channel.
    */
  def permissionsWithOverridesId(guild: Guild, guildPermissions: Permission, channelId: ChannelId): Permission = {
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

  /**
    * Calculate the permissions of this user in a channel given a guild.
    */
  def channelPermissionsId(guild: Guild, channelId: ChannelId): Permission =
    permissionsWithOverridesId(guild, permissions(guild), channelId)

  /**
    * Check if this user has any roles above the passed in roles.
    */
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

  /**
    * Check if this user has any roles above the passed in roles.
    */
  def hasRoleAboveId(guild: Guild, other: GuildMember): Boolean =
    if (other.userId == guild.ownerId) false else hasRoleAboveId(guild, other.roleIds)
}

/**
  * An emoji in a guild.
  * @param id The id of the emoji.
  * @param name The emoji name.
  * @param roles The roles that can use this emoji.
  * @param userId The id of the user that created this emoji.
  * @param requireColons If the emoji requires colons.
  * @param managed If the emoji is managed.
  */
case class Emoji(
    id: EmojiId,
    name: String,
    roles: Seq[RoleId],
    userId: Option[UserId],
    requireColons: Boolean,
    managed: Boolean,
    animated: Boolean
) {

  /**
    * Mention this role so it can be formatted correctly in messages.
    */
  def mention: String = if (!managed) s"<:$asString:>" else asString

  /**
    * Returns a string representation of this emoji used in requests.
    */
  def asString: String = if (!managed) s"$name:$id" else s"$name"

  /**
    * Get the creator of this emoji if it has one.
    */
  def creator(implicit c: CacheSnapshot): Option[User] =
    userId.fold(None: Option[User])(c.getUser)
}

/**
  * @param start When the activity started.
  * @param end When the activity will end.
  */
case class ActivityTimestamps(start: Option[Instant], end: Option[Instant])

/**
  * @param largeImage Id for the large asset. Usually a snowflake.
  * @param largeText Text displayed when hovering over the large image.
  * @param smallImage Id for the small asset. Usually a snowflake.
  * @param smallText Text displayed when hovering over the small image.
  */
case class ActivityAsset(
    largeImage: Option[String],
    largeText: Option[String],
    smallImage: Option[String],
    smallText: Option[String]
)

/**
  * @param id The id of the party
  * @param currentSize The current size of the party.
  * @param maxSize The max size of the party.
  */
case class ActivityParty(id: Option[String], currentSize: Option[Int], maxSize: Option[Int])

/**
  * The text in a presence
  */
sealed trait Activity {

  /**
    * The text shown
    */
  def name: String

  /**
    * Timestamps for start and end of activity.
    */
  def timestamps: Option[ActivityTimestamps]

  /**
    * What the player is doing.
    */
  def details: Option[String]

  /**
    * Images for the presence and hover texts.
    */
  def assets: Option[ActivityAsset]
}

/**
  * The presence of someone playing a game
  * @param applicationId Application id of the game.
  * @param state The user's party status.
  * @param party Info about the user's party.
  */
case class PresenceGame(
    name: String,
    timestamps: Option[ActivityTimestamps],
    applicationId: Option[RawSnowflake],
    details: Option[String],
    state: Option[String],
    party: Option[ActivityParty],
    assets: Option[ActivityAsset]
) extends Activity

/**
  * The presence of someone streaming
  * @param uri The uri of the stream
  * @param applicationId Application id of the game.
  * @param state The user's party status.
  * @param party Info about the user's party.
  */
case class PresenceStreaming(
    name: String,
    uri: Option[String],
    timestamps: Option[ActivityTimestamps],
    applicationId: Option[RawSnowflake],
    details: Option[String],
    state: Option[String],
    party: Option[ActivityParty],
    assets: Option[ActivityAsset]
) extends Activity

/**
  * The presence of someone listening to music
  */
case class PresenceListening(
    name: String,
    timestamps: Option[ActivityTimestamps],
    details: Option[String],
    assets: Option[ActivityAsset]
) extends Activity

/**
  * The presence of someone watching something
  */
case class PresenceWatching(
    name: String,
    timestamps: Option[ActivityTimestamps],
    details: Option[String],
    assets: Option[ActivityAsset]
) extends Activity

//TODO: Figure out what the public API for this is, and what it sends back
case class PresenceCustom(
    name: String,
    state: Option[String] //TODO: Figure out if this is nullable
) extends Activity {

  override def timestamps: Option[ActivityTimestamps] = None

  override def details: Option[String] = None

  override def assets: Option[ActivityAsset] = None
}

/**
  * The different statuses a user can have
  */
sealed abstract class PresenceStatus(val value: String) extends StringEnumEntry
object PresenceStatus extends StringEnum[PresenceStatus] with StringCirceEnum[PresenceStatus] {
  case object Online       extends PresenceStatus("online")
  case object DoNotDisturb extends PresenceStatus("dnd")
  case object Idle         extends PresenceStatus("idle")
  case object Invisible    extends PresenceStatus("invisible")
  case object Offline      extends PresenceStatus("offline")

  override def values: immutable.IndexedSeq[PresenceStatus] = findValues
}

/**
  * The status of a user per platform. Not present if the user is offline,
  * or invisible.
  */
case class ClientStatus(
    desktop: Option[PresenceStatus],
    mobile: Option[PresenceStatus],
    web: Option[PresenceStatus]
)

/**
  * The presence for a user
  * @param userId The user id
  * @param activity The activity of the presence
  * @param status The status of the user
  * @param clientStatus The status of the user over several platforms
  */
case class Presence(userId: UserId, activity: Option[Activity], status: PresenceStatus, clientStatus: ClientStatus)
    extends GetUser

/**
  * A server integration
  * @param id The id of the integration
  * @param name The integration name
  * @param `type` The type of the integration
  * @param enabled If the integration is enabled
  * @param syncing If the integration is synced
  * @param roleId Role that this integration uses for subscribers
  * @param expireBehavior The behavior of expiring subscribers.
  * @param expireGracePeriod The grace period before expiring subscribers.
  * @param user The user for this integration
  * @param account Account information
  * @param syncedAt When the integration last synced
  */
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

/**
  * @param id The id of the account
  * @param name The name of the account
  */
case class IntegrationAccount(id: String, name: String)

case class GuildEmbed(enabled: Boolean, channelId: Option[ChannelId])

/**
  * Represents a banned user.
  * @param reason Why the user was banned.
  * @param userId The user that was baned.
  */
case class Ban(reason: Option[String], userId: UserId) {

  /**
    * Get the user this ban applies to.
    */
  def user(implicit c: CacheSnapshot): Option[User] = c.getUser(userId)
}
