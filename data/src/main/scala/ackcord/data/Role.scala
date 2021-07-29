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
    mentionable: Boolean,
    tags: Option[RoleTags]
) extends GetGuild
    with UserOrRole {

  /** Mention this role. */
  def mention: String = id.mention

  /** Check if this role is above another role. */
  def isAbove(other: Role): Boolean = this.position > other.position

  /** Check if this role is below another role. */
  def isBelow(other: Role): Boolean = this.position < other.position
}

/**
  * @param botId If this role is for a bot, the bot the role belongs to.
  * @param integrationId If this role is for an integration, the integration the
  *                      role belongs to.
  * @param premiumSubscriber If this role is the premium subscriber role.
  */
case class RoleTags(
    botId: Option[UserId],
    integrationId: Option[IntegrationId],
    premiumSubscriber: Boolean
)
