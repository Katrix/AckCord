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
package ackcord

import scala.language.implicitConversions

import java.lang.{Long => JLong}
import java.time.Instant

import shapeless.tag._

package object data {

  private object SharedTagger extends Tagger[Nothing]
  private def tagS[U]: Tagger[U] = SharedTagger.asInstanceOf[Tagger[U]]

  type SnowflakeType[A] = Long @@ SnowflakeTag[A]
  object SnowflakeType {
    def apply[A](long: Long): Long @@ SnowflakeTag[A] = tagS[SnowflakeTag[A]](long)
    def apply[A](content: String): SnowflakeType[A]   = apply[A](JLong.parseUnsignedLong(content))

    /**
      * Creates a snowflake tag for the earliest moment in time. Use this
      * for pagination.
      */
    def epoch[A]: SnowflakeType[A] = apply("0")

    /**
      * Creates a snowflake for a specific moment. Use this for pagination.
      */
    def fromInstant[A](instant: Instant): SnowflakeType[A] = apply(instant.toEpochMilli - DiscordEpoch << 22)
  }

  private val DiscordEpoch = 1420070400000L

  implicit class SnowflakeTypeSyntax[A](private val snowflake: SnowflakeType[A]) extends AnyVal {
    def creationDate: Instant = Instant.ofEpochMilli(DiscordEpoch + (snowflake >> 22))
    def asString: String      = JLong.toUnsignedString(snowflake)
  }

  type RawSnowflake = SnowflakeType[RawSnowflakeTag]
  object RawSnowflake {
    def apply(content: String): RawSnowflake = RawSnowflake(JLong.parseUnsignedLong(content))
    def apply(long: Long): RawSnowflake      = SnowflakeType[RawSnowflakeTag](long)
  }

  //Some type aliases for better documentation by the types
  type GuildId = SnowflakeType[Guild]
  object GuildId {
    def apply(s: Long): GuildId = SnowflakeType[Guild](s)
  }

  implicit class GuildIdSyntax(private val guildId: GuildId) extends AnyVal {

    /**
      * Resolve the guild represented by this id.
      */
    def resolve(implicit c: CacheSnapshot): Option[Guild] = c.getGuild(guildId)
  }

  type ChannelId = SnowflakeType[Channel]
  object ChannelId {
    def apply(s: Long): ChannelId = SnowflakeType[Channel](s)
  }

  implicit class ChannelIdSyntax(private val channelId: ChannelId) extends AnyVal {

    /**
      * Resolve the channel represented by this id. If a guild id is know,
      * prefer one of the guildResolve methods instead.
      */
    def resolve(implicit c: CacheSnapshot): Option[Channel] = c.getChannel(channelId)

    /**
      * Resolve the channel represented by this id as a guild channel. If a
      * guild id is know, prefer one of the other guildResolve methods instead.
      */
    def guildResolve(implicit c: CacheSnapshot): Option[GuildChannel] = c.getGuildChannel(channelId)

    /**
      * Resolve the channel represented by this id relative to a guild id.
      */
    def guildResolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[GuildChannel] =
      c.getGuildChannel(guildId, channelId)

    /**
      * Resolve the channel represented by this id as a text channel. If a
      * guild id is know, prefer the other tResolve method instead.
      */
    def tResolve(implicit c: CacheSnapshot): Option[TChannel] = c.getTChannel(channelId)

    /**
      * Resolve the channel represented by this id as a text channel relative
      * to a guild id.
      */
    def tResolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[TGuildChannel] =
      c.getGuildChannel(guildId, channelId).collect { case tc: TGuildChannel => tc }

    /**
      * Resolve the channel represented by this id as a voice channel relative
      * to a guild id.
      */
    def vResolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[VGuildChannel] =
      c.getGuildChannel(guildId, channelId).collect { case vc: VGuildChannel => vc }
  }

  type MessageId = SnowflakeType[Message]
  object MessageId {
    def apply(s: Long): MessageId = SnowflakeType[Message](s)
  }

  implicit class MessageIdSyntax(private val messageId: MessageId) extends AnyVal {

    /**
      * Resolve the message represented by this id. If a channel id is known,
      * prefer the method that takes a channel id.
      */
    def resolve(implicit c: CacheSnapshot): Option[Message] = c.getMessage(messageId)

    /**
      * Resolves the message represented by this id relative to a channel id.
      */
    def resolve(channelId: ChannelId)(implicit c: CacheSnapshot): Option[Message] =
      c.getMessage(channelId, messageId)
  }

  type UserId = SnowflakeType[User]
  object UserId {
    def apply(s: Long): UserId = SnowflakeType[User](s)
  }

  implicit class UserIdSyntax(private val userId: UserId) extends AnyVal {

    /**
      * Resolve the user represented by this id.
      */
    def resolve(implicit c: CacheSnapshot): Option[User] = c.getUser(userId)

    /**
      * Resolve the guild member represented by this id.
      * @param guildId The guild to find the guild member in
      */
    def resolveMember(guildId: GuildId)(implicit c: CacheSnapshot): Option[GuildMember] =
      c.getGuild(guildId).flatMap(_.members.get(userId))
  }

  type RoleId = SnowflakeType[Role]
  object RoleId {
    def apply(s: Long): RoleId = SnowflakeType[Role](s)
  }

  implicit class RoleIdSyntax(private val roleId: RoleId) extends AnyVal {

    /**
      * Resolve the role this id represents. If a guild id is known, prefer
      * the method that takes a guild id.
      */
    def resolve(implicit c: CacheSnapshot): Option[Role] = c.getRole(roleId)

    /**
      * Resolve the role this id represents relative to a guild id.
      */
    def resolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[Role] =
      c.getRole(guildId, roleId)
  }

  type UserOrRoleId = SnowflakeType[UserOrRoleTag]
  object UserOrRoleId {
    def apply(s: Long): UserOrRoleId = SnowflakeType[UserOrRoleTag](s)
  }

  implicit def liftUser(userId: UserId): UserOrRoleId = UserOrRoleId(userId)
  implicit def liftRole(userId: RoleId): UserOrRoleId = UserOrRoleId(userId)

  type EmojiId = SnowflakeType[Emoji]
  object EmojiId {
    def apply(s: Long): EmojiId = SnowflakeType[Emoji](s)
  }

  implicit class EmojiIdSyntax(private val emojiId: EmojiId) extends AnyVal {

    /**
      * Resolve the emoji this id represents. If a guild id is known, prefer
      * the method that takes a guild id.
      */
    def resolve(implicit c: CacheSnapshot): Option[Emoji] = c.getEmoji(emojiId)

    /**
      * Resolve the emoji this id represents relative to a guild id.
      */
    def resolve(guildId: GuildId)(implicit c: CacheSnapshot): Option[Emoji] =
      c.getGuild(guildId).flatMap(_.emojis.get(emojiId))
  }

  type IntegrationId = SnowflakeType[Integration]
  object IntegrationId {
    def apply(s: Long): IntegrationId = SnowflakeType[Integration](s)
  }

  /**
    * A permission to do some action. In AckCord this is represented as a
    * value class around int.
    */
  type Permission = Long @@ Permission.type
  object Permission {

    private[data] def apply(long: Long): Long @@ Permission.type = tagS[Permission.type](long)

    /**
      * Create a permission that has all the permissions passed in.
      */
    def apply(permissions: Permission*): Permission = permissions.fold(None)(_ ++ _)

    /**
      * Create a permission from an int.
      */
    def fromLong(long: Long): Long @@ Permission.type = apply(long)

    val CreateInstantInvite = Permission(0x00000001)
    val KickMembers         = Permission(0x00000002)
    val BanMembers          = Permission(0x00000004)
    val Administrator       = Permission(0x00000008)
    val ManageChannels      = Permission(0x00000010)
    val ManageGuild         = Permission(0x00000020)
    val AddReactions        = Permission(0x00000040)
    val ViewAuditLog        = Permission(0x00000080)
    val ViewChannel         = Permission(0x00000400)
    val SendMessages        = Permission(0x00000800)
    val SendTtsMessages     = Permission(0x00001000)
    val ManageMessages      = Permission(0x00002000)
    val EmbedLinks          = Permission(0x00004000)
    val AttachFiles         = Permission(0x00008000)
    val ReadMessageHistory  = Permission(0x00010000)
    val MentionEveryone     = Permission(0x00020000)
    val UseExternalEmojis   = Permission(0x00040000)
    val Connect             = Permission(0x00100000)
    val Speak               = Permission(0x00200000)
    val MuteMembers         = Permission(0x00400000)
    val DeafenMembers       = Permission(0x00800000)
    val MoveMembers         = Permission(0x01000000)
    val UseVad              = Permission(0x02000000)
    val PrioritySpeaker     = Permission(0x00000100)
    val Stream              = Permission(0x00000200)
    val ChangeNickname      = Permission(0x04000000)
    val ManageNicknames     = Permission(0x08000000)
    val ManageRoles         = Permission(0x10000000)
    val ManageWebhooks      = Permission(0x20000000)
    val ManageEmojis        = Permission(0x40000000)

    @deprecated("Prefer ViewChannel instead", since = "0.15")
    val ReadMessages: Permission = ViewChannel

    val None = Permission(0x00000000)
    val All = Permission(
      CreateInstantInvite,
      KickMembers,
      BanMembers,
      Administrator,
      ManageChannels,
      ManageGuild,
      AddReactions,
      ViewAuditLog,
      ViewChannel,
      SendMessages,
      SendTtsMessages,
      ManageMessages,
      EmbedLinks,
      AttachFiles,
      ReadMessageHistory,
      MentionEveryone,
      UseExternalEmojis,
      Connect,
      Speak,
      MuteMembers,
      DeafenMembers,
      MoveMembers,
      UseVad,
      ChangeNickname,
      ManageNicknames,
      ManageRoles,
      ManageWebhooks,
      ManageEmojis
    )
  }
  implicit class PermissionSyntax(private val permission: Permission) extends AnyVal {

    /**
      * Add a permission to this permission.
      * @param other The other permission.
      */
    def addPermissions(other: Permission): Permission = Permission(permission | other)

    /**
      * Add a permission to this permission.
      * @param other The other permission.
      */
    def ++(other: Permission): Permission = addPermissions(other)

    /**
      * Remove a permission from this permission.
      * @param other The permission to remove.
      */
    def removePermissions(other: Permission): Permission = Permission(permission & ~other)

    /**
      * Remove a permission from this permission.
      * @param other The permission to remove.
      */
    def --(other: Permission): Permission = removePermissions(other)

    /**
      * Toggle a permission in this permission.
      * @param other The permission to toggle.
      */
    def togglePermissions(other: Permission): Permission = Permission(permission ^ other)

    /**
      * Check if this permission has a permission.
      * @param other The permission to check against.
      */
    def hasPermissions(other: Permission): Boolean = (permission & other) == other

    /**
      * Check if this permission grants any permissions.
      */
    def isNone: Boolean = permission == 0
  }

  type UserFlags = Int @@ UserFlags.type
  object UserFlags {

    private[data] def apply(int: Int): Int @@ UserFlags.type = tagS[UserFlags.type](int)

    /**
      * Create a UserFlag that has all the flags passed in.
      */
    def apply(flags: UserFlags*): UserFlags = flags.fold(None)(_ ++ _)

    /**
      * Create a UserFlag from an int.
      */
    def fromInt(int: Int): Int @@ UserFlags.type = apply(int)

    val None            = UserFlags(0)
    val DiscordEmployee = UserFlags(1 << 0)
    val DiscordPartner  = UserFlags(1 << 1)
    val HypeSquadEvents = UserFlags(1 << 2)
    val BugHunter       = UserFlags(1 << 3)
    val HouseBravery    = UserFlags(1 << 6)
    val HouseBrilliance = UserFlags(1 << 7)
    val HouseBalance    = UserFlags(1 << 8)
    val EarlySupporter  = UserFlags(1 << 9)
    val TeamUser        = UserFlags(1 << 10)
  }
  implicit class UserFlagsSyntax(private val flags: UserFlags) extends AnyVal {

    /**
      * Add a flag to this flag.
      * @param other The other flag.
      */
    def ++(other: UserFlags): UserFlags = UserFlags(flags | other)

    /**
      * Remove a flag from this flag.
      * @param other The flag to remove.
      */
    def --(other: UserFlags): UserFlags = UserFlags(flags & ~other)

    /**
      * Check if these flags has a flag.
      * @param other The flag to check against.
      */
    def hasFlag(other: UserFlags): Boolean = (flags & other) == other

    /**
      * Check if these flags is not empty.
      */
    def isNone: Boolean = flags == 0
  }
}
