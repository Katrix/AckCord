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

import cats.Monad
import cats.data.OptionT
import net.katsstuff.ackcord.CacheSnapshotLike

/**
  * A webhook
  * @param id The webhook id
  * @param guildId The guild it belongs to
  * @param channelId The channel it belongs to
  * @param user The user that created the webhook. Not present when getting
  *             a webhook with a token.
  * @param name The name of the webhook
  * @param avatar The avatar of the webhook.
  * @param token The token of the webhook
  */
case class Webhook(
    id: SnowflakeType[Webhook],
    guildId: Option[GuildId],
    channelId: ChannelId,
    user: Option[User],
    name: Option[String],
    avatar: Option[String],
    token: String
) extends GetGuildOpt {

  /**
    * Resolve the channel of this webhook as a guild channel
    */
  def tGuildChannel[F[_]: Monad](implicit snapshot: CacheSnapshotLike[F]): OptionT[F, TChannel] =
    OptionT
      .fromOption[F](guildId)
      .flatMap(g => snapshot.getGuildChannel(g, channelId))
      .orElse(snapshot.getGuildChannel(channelId))
      .collect {
        case gChannel: TGuildChannel => gChannel
      }
}
