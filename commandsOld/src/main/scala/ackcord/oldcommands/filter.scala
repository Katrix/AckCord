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
package ackcord.oldcommands

import ackcord.CacheSnapshot
import ackcord.data._
import ackcord.syntax._

/**
  * A command filter is something used to limit the scope in which a command
  * can be used. A few filters are defined here, but creating a custom one
  * is easy.
  */
trait CmdFilter {

  /**
    * Check if a command can be used by a user. While this is not used
    * directly in AckCord, it can be used when implementing help commands
    * and similar. If it's not possible to determine if the command can be
    * used, then this method should be optimistic.
    */
  def isAllowed(userId: UserId, guildId: GuildId)(implicit c: CacheSnapshot): Boolean

  /**
    * Check if the message can be executed.
    */
  def isAllowed(msg: Message)(implicit c: CacheSnapshot): Boolean

  /**
    * If the message could not be executed, get an error message to
    * give the user.
    */
  def errorMessage(msg: Message)(implicit c: CacheSnapshot): Option[String]
}
object CmdFilter {

  /**
    * Only allow this command to be used in a specific context
    */
  case class InContext(context: Context) extends CmdFilter {
    override def isAllowed(userId: UserId, guildId: GuildId)(
        implicit c: CacheSnapshot
    ): Boolean = context == Context.Guild //We must be in a guild as we were passed a guild id

    override def isAllowed(msg: Message)(implicit c: CacheSnapshot): Boolean =
      msg.channelId.resolve.exists {
        case _: GuildChannel       => context == Context.Guild
        case _: DMChannel          => context == Context.DM
        case _: GroupDMChannel     => context == Context.DM //We consider group DMs to be DMs
        case _: UnsupportedChannel => false
      }

    override def errorMessage(msg: Message)(implicit c: CacheSnapshot): Option[String] =
      Some(s"This command can only be used in a $context")
  }

  /**
    * This command can only be used in a guild
    */
  object InGuild extends InContext(Context.Guild)

  /**
    * This command can only be used in a dm
    */
  object InDM extends InContext(Context.DM)

  /**
    * A command that can only be used in a single guild.
    */
  case class InOneGuild(guildId: GuildId) extends CmdFilter {
    override def isAllowed(userId: UserId, guildId: GuildId)(
        implicit c: CacheSnapshot
    ): Boolean = guildId.resolve.map(_.members).exists(_.contains(userId))

    override def isAllowed(msg: Message)(implicit c: CacheSnapshot): Boolean =
      msg.tGuildChannel(guildId).isDefined

    override def errorMessage(msg: Message)(implicit c: CacheSnapshot): Option[String] =
      None
  }

  /**
    * This command can only be used if the user has specific permissions.
    * If this command is not used in a guild, it will always pass this filter.
    */
  case class NeedPermission(neededPermission: Permission) extends CmdFilter {
    override def isAllowed(userId: UserId, guildId: GuildId)(
        implicit c: CacheSnapshot
    ): Boolean = guildId.resolve.exists { guild =>
      guild.members.get(userId).exists(_.permissions(guild).hasPermissions(neededPermission))
    }

    override def isAllowed(msg: Message)(implicit c: CacheSnapshot): Boolean = {
      val allowed = for {
        channel      <- msg.channelId.tResolve
        guildChannel <- channel.asGuildChannel
        guild        <- guildChannel.guild
        member       <- guild.members.get(UserId(msg.authorId))
      } yield member.channelPermissionsId(guild, msg.channelId).hasPermissions(neededPermission)

      allowed.getOrElse(false)
    }

    override def errorMessage(msg: Message)(implicit c: CacheSnapshot): Option[String] =
      Some("You don't have permission to use this command")
  }

  /**
    * A filter that only allows non bot users.
    */
  case object NonBot extends CmdFilter {
    override def isAllowed(userId: UserId, guildId: GuildId)(
        implicit c: CacheSnapshot
    ): Boolean =
      c.getUser(userId).exists(_.bot.getOrElse(false))

    override def isAllowed(msg: Message)(implicit c: CacheSnapshot): Boolean =
      UserId(msg.authorId).resolve.exists(u => !u.bot.getOrElse(false) && msg.isAuthorUser)

    override def errorMessage(msg: Message)(implicit c: CacheSnapshot): Option[String] =
      None
  }
}

/**
  * Represents a place a command can be used.
  */
sealed trait Context
object Context {
  case object Guild extends Context
  case object DM    extends Context
}
