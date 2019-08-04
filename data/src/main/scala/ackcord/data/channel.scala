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

import java.time.OffsetDateTime

import scala.collection.immutable

import ackcord.{CacheSnapshot, SnowflakeMap}
import enumeratum.values._

/**
  * Different type of channels
  */
sealed abstract class ChannelType(val value: Int) extends IntEnumEntry
object ChannelType extends IntEnum[ChannelType] with IntCirceEnum[ChannelType] {
  case object GuildText     extends ChannelType(0)
  case object DM            extends ChannelType(1)
  case object GuildVoice    extends ChannelType(2)
  case object GroupDm       extends ChannelType(3)
  case object GuildCategory extends ChannelType(4)
  case object GuildNews     extends ChannelType(5)
  case object GuildStore    extends ChannelType(6)

  //Not API documented
  case object LFG extends ChannelType(8)

  override def values: immutable.IndexedSeq[ChannelType] = findValues

  /**
    * Get a channel type from an id
    */
  @deprecated("Prefer ChannelType.withValueOpt", since = "0.14.0")
  def forId(id: Int): Option[ChannelType] = withValueOpt(id)

  /**
    * Get id for a channel type
    */
  @deprecated("Prefer ChannelType#value", since = "0.14.0")
  def idFor(channelType: ChannelType): Int = channelType.value
}

/**
  * Permission overwrites can apply to both users and role. This tells you what's
  * being overwritten for a specific overwrite.
  */
sealed abstract class PermissionOverwriteType(val value: String) extends StringEnumEntry
object PermissionOverwriteType
    extends StringEnum[PermissionOverwriteType]
    with StringCirceEnum[PermissionOverwriteType] {
  case object Role   extends PermissionOverwriteType("role")
  case object Member extends PermissionOverwriteType("member")

  override def values: immutable.IndexedSeq[PermissionOverwriteType] = findValues

  /**
    * Get a overwrite type from a name.
    */
  @deprecated("Prefer PermissionOverwriteType.withValueOpt", since = "0.14.0")
  def forName(name: String): Option[PermissionOverwriteType] = withValueOpt(name)

  /**
    * Get the name of an overwrite type
    */
  @deprecated("Prefer PermissionOverwriteType#value", since = "0.14.0")
  def nameOf(tpe: PermissionOverwriteType): String = tpe.value
}

/**
  * Represents a permission overwrite in a channel for a user or a guild.
  * @param id The id that this overwrite applies to. Can be both a user or a
  *           role. Check [[`type`]] to see what is valid for this overwrite.
  * @param `type` The type of object this applies to.
  * @param allow The permissions granted by this overwrite.
  * @param deny The permissions denied by this overwrite.
  */
case class PermissionOverwrite(id: UserOrRoleId, `type`: PermissionOverwriteType, allow: Permission, deny: Permission) {

  /**
    * If this overwrite applies to a user, get's that user, otherwise returns None.
    */
  def user(implicit c: CacheSnapshot): Option[User] =
    if (`type` == PermissionOverwriteType.Member) c.getUser(UserId(id)) else None

  /**
    * If this overwrite applies to a user, get that user's member, otherwise returns None.
    * @param guild The guild this overwrite belongs to.
    */
  def member(guild: Guild): Option[GuildMember] =
    if (`type` == PermissionOverwriteType.Member) guild.members.get(UserId(id)) else None

  /**
    * If this overwrite applies to a role, get that role, otherwise returns None.
    * @param guild The guild this overwrite belongs to.
    */
  def role(guild: Guild): Option[Role] =
    if (`type` == PermissionOverwriteType.Role) guild.roles.get(RoleId(id)) else None
}

/**
  * Base channel type
  */
sealed trait Channel {

  /**
    * The id of the channel
    */
  def id: ChannelId

  /**
    * The channel type of this channel
    */
  def channelType: ChannelType

  /**
    * Get a representation of this channel that can refer to it in messages.
    */
  def mention: String = s"<#$id>"
}

/**
  * A channel that is of a type that AckCord knows about, but doesn't implement.
  * Normally because it's not yet part of the public API.
  */
case class UnsupportedChannel(id: ChannelId, channelType: ChannelType) extends Channel

/**
  * A text channel that has text messages
  */
sealed trait TChannel extends Channel {

  /**
    * Points to the last message id in the channel.
    * The id might not point to a valid or existing message.
    */
  def lastMessageId: Option[MessageId]

  /**
    * Gets the last message for this channel if it exists.
    */
  def lastMessage(implicit c: CacheSnapshot): Option[Message] =
    lastMessageId.fold(None: Option[Message])(c.getMessage(id, _))
}

/**
  * A channel within a guild
  */
sealed trait GuildChannel extends Channel with GetGuild {

  /**
    * The id of the containing guild
    */
  def guildId: GuildId

  /**
    * The position of this channel
    */
  def position: Int

  /**
    * The name of this channel
    */
  def name: String

  /**
    * The permission overwrites for this channel
    */
  def permissionOverwrites: SnowflakeMap[UserOrRoleTag, PermissionOverwrite]

  /**
    * If this channel is marked as NSFW.
    */
  def nsfw: Boolean

  /**
    * The id of the category this channel is in.
    */
  def parentId: Option[ChannelId]

  /**
    * Gets the category for this channel if it has one.
    */
  def category(implicit c: CacheSnapshot): Option[GuildCategory] =
    parentId.flatMap(c.getGuildChannel(guildId, _)).collect {
      case cat: GuildCategory => cat
    }
}

/**
  * A texual channel in a guild
  */
sealed trait TGuildChannel extends GuildChannel with TChannel {

  /**
    * The topic for this channel.
    */
  def topic: Option[String]

  /**
    * The amount of time a user has to wait before sending messages after each
    * other. Bots are not affected.
    */
  def rateLimitPerUser: Option[Int]

  /**
    * When the last pinned message was pinned.
    */
  def lastPinTimestamp: Option[OffsetDateTime]
}

/**
  * A news channel in a guild. For the most part you can treat this like any
  * other text channel.
  */
case class NewsTGuildChannel(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: SnowflakeMap[UserOrRoleTag, PermissionOverwrite],
    topic: Option[String],
    lastMessageId: Option[MessageId],
    nsfw: Boolean,
    parentId: Option[ChannelId],
    lastPinTimestamp: Option[OffsetDateTime]
) extends TGuildChannel {
  override def channelType: ChannelType = ChannelType.GuildText

  def rateLimitPerUser: Option[Int] = None
}

/**
  * A normal text channel in a guild
  */
case class NormalTGuildChannel(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: SnowflakeMap[UserOrRoleTag, PermissionOverwrite],
    topic: Option[String],
    lastMessageId: Option[MessageId],
    rateLimitPerUser: Option[Int],
    nsfw: Boolean,
    parentId: Option[ChannelId],
    lastPinTimestamp: Option[OffsetDateTime]
) extends TGuildChannel {
  override def channelType: ChannelType = ChannelType.GuildText
}

/**
  * A voice channel in a guild
  * @param bitrate The bitrate of this channel in bits
  * @param userLimit The max amount of users that can join this channel
  */
case class VGuildChannel(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: SnowflakeMap[UserOrRoleTag, PermissionOverwrite],
    bitrate: Int,
    userLimit: Int,
    nsfw: Boolean,
    parentId: Option[ChannelId]
) extends GuildChannel {
  override def channelType: ChannelType = ChannelType.GuildVoice
}

/**
  * A category in a guild
  */
case class GuildCategory(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: SnowflakeMap[UserOrRoleTag, PermissionOverwrite],
    nsfw: Boolean,
    parentId: Option[ChannelId]
) extends GuildChannel {
  override def channelType: ChannelType = ChannelType.GuildCategory
}

/**
  * A store channel in a guild
  */
case class GuildStoreChannel(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: SnowflakeMap[UserOrRoleTag, PermissionOverwrite],
    nsfw: Boolean,
    parentId: Option[ChannelId]
) extends GuildChannel {
  override def channelType: ChannelType = ChannelType.GuildStore
}

/**
  * A DM text channel
  */
case class DMChannel(id: ChannelId, lastMessageId: Option[MessageId], userId: UserId)
    extends Channel
    with TChannel
    with GetUser {
  override def channelType: ChannelType = ChannelType.DM
}

/**
  * A group DM text channel
  * @param users The users in this group DM
  * @param ownerId The creator of this channel
  * @param applicationId The applicationId of the application that created
  *                      this channel, if the channel wasn't created by a user
  * @param icon The icon hash for this group dm
  */
case class GroupDMChannel(
    id: ChannelId,
    name: String,
    users: Seq[UserId],
    lastMessageId: Option[MessageId],
    ownerId: UserId,
    applicationId: Option[RawSnowflake],
    icon: Option[String]
) extends Channel
    with TChannel {
  override def channelType: ChannelType = ChannelType.GroupDm

  /**
    * Gets the owner for this group DM.
    */
  def owner(implicit c: CacheSnapshot): Option[User] = c.getUser(ownerId)
}
