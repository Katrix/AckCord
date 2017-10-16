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

sealed trait ChannelType
object ChannelType {
  case object GuildText     extends ChannelType
  case object DM            extends ChannelType
  case object GuildVoice    extends ChannelType
  case object GroupDm       extends ChannelType
  case object GuildCategory extends ChannelType

  def forId(id: Int): Option[ChannelType] = id match {
    case 0 => Some(GuildText)
    case 1 => Some(DM)
    case 2 => Some(GuildVoice)
    case 3 => Some(GroupDm)
    case 4 => Some(GuildCategory)
    case _ => None
  }

  def idFor(channelType: ChannelType): Int = channelType match {
    case GuildText     => 0
    case DM            => 1
    case GuildVoice    => 2
    case GroupDm       => 3
    case GuildCategory => 4
  }
}

sealed trait PermissionOverwriteType
object PermissionOverwriteType {
  case object Role   extends PermissionOverwriteType
  case object Member extends PermissionOverwriteType

  def forName(name: String): Option[PermissionOverwriteType] = name match {
    case "role"   => Some(Role)
    case "member" => Some(Member)
    case _        => None
  }

  def nameOf(tpe: PermissionOverwriteType): String = tpe match {
    case Role   => "role"
    case Member => "member"
  }
}

case class PermissionOverwrite(id: UserOrRoleId, `type`: PermissionOverwriteType, allow: Permission, deny: Permission)

sealed trait Channel {
  def id:          ChannelId
  def channelType: ChannelType
}

sealed trait TChannel extends Channel {

  /**
    * Points to the last message id in the channel.
    * The id might not point to a valid or existing message.
    */
  def lastMessageId: Option[MessageId]
}

sealed trait GuildChannel extends Channel with GetGuild {
  def guildId:              GuildId
  def position:             Int
  def name:                 String
  def permissionOverwrites: Map[UserOrRoleId, PermissionOverwrite]
  def nsfw:                 Boolean
  def parentId:             Option[ChannelId]
}

case class TGuildChannel(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: Map[UserOrRoleId, PermissionOverwrite],
    topic: Option[String],
    lastMessageId: Option[MessageId],
    nsfw: Boolean,
    parentId: Option[ChannelId]
) extends GuildChannel
    with TChannel {
  override def channelType: ChannelType = ChannelType.GuildText
}

case class VGuildChannel(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: Map[UserOrRoleId, PermissionOverwrite],
    bitrate: Int,
    userLimit: Int,
    nsfw: Boolean,
    parentId: Option[ChannelId]
) extends GuildChannel {
  override def channelType: ChannelType = ChannelType.GuildVoice
}

case class GuildCategory(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: Map[UserOrRoleId, PermissionOverwrite],
    nsfw: Boolean,
    parentId: Option[ChannelId]
) extends GuildChannel {
  override def channelType: ChannelType = ChannelType.GuildCategory
}

case class DMChannel(id: ChannelId, lastMessageId: Option[MessageId], userId: UserId)
    extends Channel
    with TChannel
    with GetUser {
  override def channelType: ChannelType = ChannelType.DM
}

case class GroupDMChannel(
    id: ChannelId,
    name: String,
    users: Seq[UserId],
    lastMessageId: Option[MessageId],
    ownerId: UserId,
    applicationId: Option[Snowflake],
    icon: Option[String]
) extends Channel
    with TChannel {
  override def channelType: ChannelType = ChannelType.GroupDm
}
