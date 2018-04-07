/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord.commands

import scala.language.higherKinds

import cats.Monad
import cats.data.OptionT
import net.katsstuff.ackcord.CacheSnapshotLike
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.syntax._

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
  def isAllowed[F[_]: Monad](userId: UserId, guildId: GuildId)(implicit c: CacheSnapshotLike[F]): F[Boolean]

  /**
    * Check if the message can be executed.
    */
  def isAllowed[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): F[Boolean]

  /**
    * If the message could not be executed, get an error message to
    * give the user.
    */
  def errorMessage[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): OptionT[F, String]
}
object CmdFilter {

  /**
    * Only allow this command to be used in a specific context
    */
  case class InContext(context: Context) extends CmdFilter {
    override def isAllowed[F[_]: Monad](userId: UserId, guildId: GuildId)(
        implicit c: CacheSnapshotLike[F]
    ): F[Boolean] = Monad[F].pure(true)

    override def isAllowed[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): F[Boolean] =
      msg.channelId.resolve.exists {
        case _: GuildChannel   => context == Context.Guild
        case _: DMChannel      => context == Context.DM
        case _: GroupDMChannel => context == Context.DM //We consider group DMs to be DMs
      }

    override def errorMessage[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): OptionT[F, String] =
      OptionT.pure[F](s"This command can only be used in a $context")
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
    override def isAllowed[F[_]: Monad](userId: UserId, guildId: GuildId)(
        implicit c: CacheSnapshotLike[F]
    ): F[Boolean] = guildId.resolve.map(_.members).exists(_.contains(userId))

    override def isAllowed[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): F[Boolean] =
      msg.tGuildChannel(guildId).isDefined

    override def errorMessage[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): OptionT[F, String] =
      OptionT.none
  }

  /**
    * This command can only be used if the user has specific permissions.
    * If this command is not used in a guild, it will always pass this filter.
    */
  case class NeedPermission(neededPermission: Permission) extends CmdFilter {
    override def isAllowed[F[_]: Monad](userId: UserId, guildId: GuildId)(
        implicit c: CacheSnapshotLike[F]
    ): F[Boolean] = guildId.resolve.exists { guild =>
      guild.members.get(userId).exists(_.permissions(guild).hasPermissions(neededPermission))
    }

    override def isAllowed[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): F[Boolean] = {
      val allowed = for {
        channel      <- msg.channelId.tResolve
        guildChannel <- OptionT.fromOption(channel.asGuildChannel)
        guild        <- guildChannel.guild
        member       <- OptionT.fromOption(guild.members.get(UserId(msg.authorId)))
        hasPerms <- OptionT.liftF(
          Monad[F].map(member.channelPermissions(msg.channelId))(_.hasPermissions(neededPermission))
        )
      } yield hasPerms

      allowed.getOrElse(false)
    }

    override def errorMessage[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): OptionT[F, String] =
      OptionT.pure("You don't have permission to use this command")
  }

  /**
    * A filter that only allows non bot users.
    */
  case object NonBot extends CmdFilter {
    override def isAllowed[F[_]: Monad](userId: UserId, guildId: GuildId)(
        implicit c: CacheSnapshotLike[F]
    ): F[Boolean] =
      c.getUser(userId).exists(_.bot.getOrElse(false))

    override def isAllowed[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): F[Boolean] =
      UserId(msg.authorId).resolve.exists(u => !u.bot.getOrElse(false) && msg.isAuthorUser)

    override def errorMessage[F[_]: Monad](msg: Message)(implicit c: CacheSnapshotLike[F]): OptionT[F, String] =
      OptionT.none
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
