/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
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
package net.katsstuff.akkacord.data

sealed trait ChannelType
object ChannelType {
  case object Text  extends ChannelType
  case object Voice extends ChannelType

  def forName(name: String): Option[ChannelType] = name match {
    case "text"  => Some(Text)
    case "voice" => Some(Voice)
    case _       => None
  }

  def nameFor(channelType: ChannelType): String = channelType match {
    case Text  => "text"
    case Voice => "voice"
  }
}

sealed trait PermissionValueType
object PermissionValueType {
  case object Role   extends PermissionValueType
  case object Member extends PermissionValueType

  def forName(name: String): Option[PermissionValueType] = name match {
    case "role"   => Some(Role)
    case "member" => Some(Member)
    case _        => None
  }

  def nameOf(tpe: PermissionValueType): String = tpe match {
    case Role   => "role"
    case Member => "member"
  }
}

case class PermissionValue(id: UserOrRoleId, `type`: PermissionValueType, allow: Permission, deny: Permission)

sealed trait Channel {
  def id:        ChannelId
  def isPrivate: Boolean
}

sealed trait TChannel extends Channel {

  /**
    * Points to the last message id in the channel.
    * The id might not point to a valid or existing message.
    */
  def lastMessageId: Option[MessageId]
}

sealed trait GuildChannel extends Channel with GetGuild {
  def isPrivate: Boolean = false

  def guildId:              GuildId
  def name:                 String
  def channelType:          ChannelType
  def position:             Int
  def permissionOverwrites: Seq[PermissionValue]
}

case class TGuildChannel(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: Seq[PermissionValue],
    topic: Option[String],
    lastMessageId: Option[MessageId]
) extends GuildChannel
    with TChannel {
  override def channelType: ChannelType = ChannelType.Text
}

case class VGuildChannel(
    id: ChannelId,
    guildId: GuildId,
    name: String,
    position: Int,
    permissionOverwrites: Seq[PermissionValue],
    bitrate: Int,
    userLimit: Int
) extends GuildChannel {
  override def channelType: ChannelType = ChannelType.Voice
}

case class DMChannel(id: ChannelId, lastMessageId: Option[MessageId], userId: UserId) extends Channel with TChannel with GetUser {
  override def isPrivate: Boolean = true
}
