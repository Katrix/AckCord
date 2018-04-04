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
package net.katsstuff.ackcord.data

import scala.language.higherKinds

import cats.data.OptionT
import cats.{Functor, Monad}
import net.katsstuff.ackcord.CacheSnapshotLike

trait GetGuild {
  def guildId: GuildId

  /**
    * The guild for this object
    */
  def guild[F[_]](implicit snapshot: CacheSnapshotLike[F]): OptionT[F, Guild] = snapshot.getGuild(guildId)
}

trait GetGuildOpt {
  def guildId: Option[GuildId]

  /**
    * The guild for this object
    */
  def guild[F[_]: Monad](implicit snapshot: CacheSnapshotLike[F]): OptionT[F, Guild] =
    OptionT.fromOption[F](guildId).flatMap(snapshot.getGuild)
}

trait GetUser {
  def userId: UserId

  /**
    * The user for this object
    */
  def user[F[_]](implicit snapshot: CacheSnapshotLike[F]): OptionT[F, User] = snapshot.getUser(userId)
}

trait GetTChannel {
  def channelId: ChannelId

  /**
    * Resolve the channelId of this object as a dm channel
    */
  def dmChannel[F[_]](implicit snapshot: CacheSnapshotLike[F]): OptionT[F, DMChannel] = snapshot.getDmChannel(channelId)

  /**
    * Resolve the channelId of this object as a TGuildChannel
    */
  def tGuildChannel[F[_]: Functor](implicit snapshot: CacheSnapshotLike[F]): OptionT[F, TGuildChannel] =
    snapshot.getGuildChannel(channelId).collect {
      case guildChannel: TGuildChannel => guildChannel
    }

  /**
    * Resolve the channelId of this object as a TGuildChannel using an provided guildId
    */
  def tGuildChannel[F[_]: Functor](
      guildId: GuildId
  )(implicit snapshot: CacheSnapshotLike[F]): OptionT[F, TGuildChannel] =
    snapshot.getGuildChannel(guildId, channelId).collect {
      case guildChannel: TGuildChannel => guildChannel
    }
}

trait GetVChannelOpt {
  def channelId: Option[ChannelId]

  /**
    * Resolve the channelId of this object as a voice channel.
    */
  def vChannel[F[_]: Monad](implicit snapshot: CacheSnapshotLike[F]): OptionT[F, Channel] =
    OptionT.fromOption[F](channelId).flatMap(snapshot.getChannel).collect {
      case vChannel: VGuildChannel => vChannel
    }
}
