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

class Permission private (val int: Int) extends AnyVal {

  def addPermissions(other: Permission):       Permission = Permission(this.int | other.int)
  def removePermissions(other: Permission):    Permission = Permission(this.int & ~other.int)
  def togglePermissions(other: Permission):    Permission = Permission(this.int ^ other.int)
  def hasPermissions(permissions: Permission): Boolean    = (this.int & permissions.int) != 0
}
object Permission {

  private def apply(int: Int):         Permission = new Permission(int)
  def apply(permissions: Permission*): Permission = permissions.fold(NoPermissions)((a1, a2) => a1.addPermissions(a2))

  def fromInt(int: Int): Permission = apply(int)

  val NoPermissions = Permission(0x00000000)

  val CreateInstantInvite = Permission(0x00000001)
  val KickMembers         = Permission(0x00000002)
  val BanMembers          = Permission(0x00000004)
  val Administrator       = Permission(0x00000008)
  val ManageChannels      = Permission(0x00000010)
  val ManageGuild         = Permission(0x00000020)
  val AddReactions        = Permission(0x00000040)
  val ViewAuditLog        = Permission(0x00000080)
  val ReadMessages        = Permission(0x00000400)
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
  val ChangeNickname      = Permission(0x04000000)
  val ManageNicknames     = Permission(0x08000000)
  val ManageRoles         = Permission(0x10000000)
  val ManageWebhooks      = Permission(0x20000000)
  val ManageEmojis        = Permission(0x40000000)
}

case class Role(
    id: RoleId,
    guildId: GuildId,
    name: String,
    color: Int,
    hoist: Boolean,
    position: Int,
    permissions: Permission,
    managed: Boolean,
    mentionable: Boolean
) extends GetGuild
