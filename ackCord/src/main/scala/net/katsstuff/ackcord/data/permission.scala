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

/**
  * A permission to do some action. In AckCord this is represented as a
  * value class around int.
  */
class Permission private (val long: Long) extends AnyVal {

  /**
    * Add a permission to this permission.
    * @param other The other permission.
    */
  def addPermissions(other: Permission): Permission = Permission(this.long | other.long)

  /**
    * Add a permission to this permission.
    * @param other The other permission.
    */
  def |(other: Permission): Permission = addPermissions(other)

  /**
    * Add a permission to this permission.
    * @param other The other permission.
    */
  def +(other: Permission): Permission = addPermissions(other)

  /**
    * Remove a permission from this permission.
    * @param other The permission to remove.
    */
  def removePermissions(other: Permission): Permission = Permission(this.long & ~other.long)

  /**
    * Remove a permission from this permission.
    * @param other The permission to remove.
    */
  def -(other: Permission): Permission = Permission(this.long & ~other.long)

  /**
    * Toggle a permission in this permission.
    * @param other The permission to toggle.
    */
  def togglePermissions(other: Permission): Permission = Permission(this.long ^ other.long)

  /**
    * Check if this permission has a permission.
    * @param permissions The permission to check against.
    */
  def hasPermissions(permissions: Permission): Boolean = (this.long & permissions.long) != 0

  override def toString: String = long.toString
}
object Permission {

  private def apply(long: Long): Permission = new Permission(long)

  /**
    * Create a permission that has all the permissions passed in.
    */
  def apply(permissions: Permission*): Permission = permissions.fold(None)(_ | _)

  /**
    * Create a permission from an int.
    */
  def fromLong(long: Long): Permission = apply(long)

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
    ReadMessages,
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
    ManageEmojis,
  )
}

/**
  * A role in a guild.
  * @param id The id of this role.
  * @param guildId The guildId this role belongs to.
  * @param name The name of this role.
  * @param color The color of this role.
  * @param hoist If this role is listed in the sidebar.
  * @param position The position of this role.
  * @param permissions The permissions this role grant.
  * @param managed If this is a bot role.
  * @param mentionable If you can mention this role.
  */
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
) extends GetGuild {

  /**
    * Mention this role.
    */
  def mention: String = s"<@&$id>"
}
