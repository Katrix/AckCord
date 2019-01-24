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

import scala.language.higherKinds

import ackcord.data.{ChannelId, GuildId, Permission}
import cats.Monad
import cats.data.OptionT

package object requests {

  /**
    * Check if a client has the needed permissions in a guild
    * @param guildId The guild to check for
    * @param permissions The needed permissions
    * @param c The cache
    */
  def hasPermissionsGuild[F[_]](guildId: GuildId, permissions: Permission)(
      implicit c: CacheSnapshot[F],
      F: Monad[F]
  ): F[Boolean] = {
    val res = for {
      guild         <- c.getGuild(guildId)
      botUser       <- OptionT.liftF(c.botUser)
      botUserMember <- OptionT.fromOption[F](guild.members.get(botUser.id))
    } yield botUserMember.permissions(guild).hasPermissions(permissions)

    res.getOrElse(false)
  }

  /**
    * Check if a client has the needed permissions in a channel
    * @param channelId The channel to check for
    * @param permissions The needed permissions
    * @param c The cache
    */
  def hasPermissionsChannel[F[_]](channelId: ChannelId, permissions: Permission)(
      implicit c: CacheSnapshot[F],
      F: Monad[F]
  ): F[Boolean] = {
    val opt = for {
      gChannel      <- c.getGuildChannel(channelId)
      guild         <- gChannel.guild
      botUser       <- OptionT.liftF(c.botUser)
      botUserMember <- OptionT.fromOption[F](guild.members.get(botUser.id))
      channelPerms  <- OptionT.liftF(botUserMember.channelPermissions(channelId))
    } yield channelPerms.hasPermissions(permissions)

    opt.getOrElse(false)
  }
}
