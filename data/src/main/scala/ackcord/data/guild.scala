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

import java.time.{Instant, OffsetDateTime}

import ackcord.{CacheSnapshot, SnowflakeMap}
import cats.data.OptionT
import cats.{Applicative, Functor, Monad, Traverse}

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
trait VerificationLevel
object VerificationLevel {

  /**
    * Unrestricted access
    */
  case object NoVerification extends VerificationLevel

  /**
    * Must have a verified email address
    */
  case object Low extends VerificationLevel

  /**
    * Must be a registered user for more than 5 minutes
    */
  case object Medium extends VerificationLevel

  /**
    * Must be a member of the guild for more than 10 minutes
    */
  case object High extends VerificationLevel

  /**
    * Must have a verified phone number
    */
  case object VeryHigh extends VerificationLevel

  def forId(id: Int): Option[VerificationLevel] = id match {
    case 0 => Some(NoVerification)
    case 1 => Some(Low)
    case 2 => Some(Medium)
    case 3 => Some(High)
    case 4 => Some(VeryHigh)
    case _ => None
  }

  def idFor(lvl: VerificationLevel): Int = lvl match {
    case NoVerification => 0
    case Low            => 1
    case Medium         => 2
    case High           => 3
    case VeryHigh       => 4
  }
}

/**
  * The different notification levels that can be used for a guild
  */
trait NotificationLevel
object NotificationLevel {

  /**
    * All messages trigger a notification
    */
  case object AllMessages extends NotificationLevel

  /**
    * Only mentions trigger a notification
    */
  case object OnlyMentions extends NotificationLevel

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

/**
  * The different explicit content filter levels to use for a guild.
  */
trait FilterLevel
object FilterLevel {

  /**
    * No filtering is done.
    */
  case object Disabled extends FilterLevel

  /**
    * Messages from members without roles are filtered
    */
  case object MembersWithoutRoles extends FilterLevel

  /**
    * All messages are filtered
    */
  case object AllMembers extends FilterLevel

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
  case object NoneMFA  extends MFALevel
  case object Elevated extends MFALevel

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
    features: Seq[String], //TODO: What is a feature?
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
    banner: Option[String]
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
  def owner[F[_]](implicit c: CacheSnapshot[F]): OptionT[F, User] = c.getUser(ownerId)

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

/**
  * Represents a user in a guild.
  * @param userId The user of this member.
  * @param guildId The guild this member belongs to.
  * @param nick The nickname of this user in this guild.
  * @param roleIds The roles of this user.
  * @param joinedAt When this user joined the guild.
  * @param deaf If this user is deaf.
  * @param mute IF this user is mute.
  */
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

  /**
    * Calculate the permissions of this user
    */
  def permissions[F[_]](implicit c: CacheSnapshot[F], F: Functor[F]): F[Permission] =
    guildId.resolve.map(permissions).getOrElse(Permission.None)

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
  def permissionsWithOverrides[F[_]](guildPermissions: Permission, channelId: ChannelId)(
      implicit c: CacheSnapshot[F],
      F: Monad[F]
  ): F[Permission] = {
    if (guildPermissions.hasPermissions(Permission.Administrator)) Applicative[F].pure(Permission.All)
    else {

      val res = guildId.resolve.subflatMap { guild =>
        guild.channels.get(channelId).map { channel =>
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
      }

      res.getOrElse(guildPermissions)
    }
  }

  /**
    * Calculate the permissions of this user in a channel.
    */
  def channelPermissions[F[_]](channelId: ChannelId)(implicit c: CacheSnapshot[F], F: Monad[F]): F[Permission] =
    Monad[F].flatMap(permissions)(perms => permissionsWithOverrides(perms, channelId))

  /**
    * Check if this user has any roles above the passed in roles.
    */
  def hasRoleAbove[F[_]](others: Seq[RoleId])(implicit c: CacheSnapshot[F], F: Monad[F]): F[Boolean] = {
    guild
      .semiflatMap { guild =>
        val ownerId = guild.ownerId
        if (this.userId == ownerId) Monad[F].pure(true)
        else {
          def maxRolesPosition(roles: Seq[RoleId]): F[Int] = {
            val seq = {
              import cats.instances.list._
              Traverse[List].traverse(roles.toList)(_.resolve(guildId).map(_.position).value)
            }
            Monad[F].map(seq) { optList =>
              val positions = optList.flatten
              if (positions.isEmpty) 0 else positions.max
            }
          }

          Monad[F].map2(maxRolesPosition(this.roleIds), maxRolesPosition(others))(_ > _)
        }
      }
      .exists(identity)
  }

  /**
    * Check if this user has any roles above the passed in roles.
    */
  def hasRoleAbove[F[_]](other: GuildMember)(implicit c: CacheSnapshot[F], F: Monad[F]): F[Boolean] =
    guild
      .semiflatMap { guild =>
        if (other.userId == guild.ownerId) Monad[F].pure(false) else hasRoleAbove(other.roleIds)
      }
      .exists(identity)
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
  def creator[F[_]](implicit c: CacheSnapshot[F], F: Applicative[F]): OptionT[F, User] =
    userId.fold(OptionT.none[F, User])(c.getUser)
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
  * The different statuses a user can have
  */
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

/**
  * The presence for a user
  * @param userId The user id
  * @param activity The activity of the presence
  * @param status The status of the user
  */
case class Presence(userId: UserId, activity: Option[Activity], status: PresenceStatus) extends GetUser

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
  def user[F[_]](implicit c: CacheSnapshot[F]): OptionT[F, User] = c.getUser(userId)
}
