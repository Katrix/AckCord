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
package net.katsstuff.akkacord.http

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri
import net.katsstuff.akkacord.data.Snowflake
import net.katsstuff.akkacord.http.rest.RestRoute

object Routes {

  val base: Uri = "https://discordapp.com/api"

  //WS
  val gateway: Uri = s"$base/gateway"

  //REST

  //Some type aliases for better documentation by the types
  type GuildId   = Snowflake
  type ChannelId = Snowflake
  type MessageId = Snowflake
  type UserId    = Snowflake

  /**
    * Emoji is a bit more complicated than the others.
    * If it's a custom emoji, the format is `id:name` for example `rust:232722868583006209`.
    * If it's a normal emoji, it's encoded using percent encoding, for example `%F0%9F%92%A9`.
    */
  type Emoji = String

  /**
    * The id of the thing the overwrite applies to. Can come either from a user or a role.
    */
  type OverwriteId = Snowflake

  implicit class Func2Syntax[T1, T2, R](val f: (T1, T2) => R) extends AnyVal {
    def andThen[A](g: R => A): (T1, T2) => A = (t1, t2) => g(f(t1, t2))
  }

  implicit class Func3Syntax[T1, T2, T3, R](val f: (T1, T2, T3) => R) extends AnyVal {
    def andThen[A](g: R => A): (T1, T2, T3) => A = (t1, t2, t3) => g(f(t1, t2, t3))
  }

  //Channel routes

  val channel: ChannelId => Uri = channelId => s"$base/channels/$channelId"

  val getChannel:         ChannelId => RestRoute = channel.andThen(RestRoute(_, GET))
  val modifyChannelPut:   ChannelId => RestRoute = channel.andThen(RestRoute(_, PUT))
  val modifyChannelPatch: ChannelId => RestRoute = channel.andThen(RestRoute(_, PATCH))
  val deleteCloseChannel: ChannelId => RestRoute = channel.andThen(RestRoute(_, DELETE))

  val channelMessages: ChannelId => Uri              = channel.andThen(uri => s"$uri/messages")
  val channelMessage:  (MessageId, ChannelId) => Uri = Function.uncurried(messageId => channelMessages.andThen(uri => s"$uri/$messageId"))

  val getChannelMessages: ChannelId => RestRoute              = channelMessages.andThen(RestRoute(_, GET))
  val getChannelMessage:  (MessageId, ChannelId) => RestRoute = channelMessage.andThen(RestRoute(_, GET))
  val createMessage:      ChannelId => RestRoute              = channelMessages.andThen(RestRoute(_, POST))
  val editMessage:        (MessageId, ChannelId) => RestRoute = channelMessage.andThen(RestRoute(_, PATCH))
  val bulkDeleteMessages: (ChannelId) => RestRoute            = channelMessages.andThen(uri => RestRoute(s"$uri/bulk-delete", POST))

  val reactions:        (MessageId, ChannelId) => Uri        = channelMessage.andThen(uri => s"$uri/reactions")
  val emojiReactions:   (Emoji, MessageId, ChannelId) => Uri = Function.uncurried(emoji => reactions.andThen(uri => s"$uri/$emoji": Uri).curried)
  val modifyMeReaction: (Emoji, MessageId, ChannelId) => Uri = emojiReactions.andThen(uri => s"$uri/@me")

  val createReaction:    (Emoji, MessageId, ChannelId) => RestRoute = modifyMeReaction.andThen(RestRoute(_, PUT))
  val deleteOwnReaction: (Emoji, MessageId, ChannelId) => RestRoute = modifyMeReaction.andThen(RestRoute(_, DELETE))
  val deleteUserReaction: (UserId, Emoji, MessageId, ChannelId) => Uri =
    Function.uncurried((userId: UserId) => emojiReactions.andThen(uri => s"$uri/$userId": Uri).curried)

  val getReactions:       (Emoji, MessageId, ChannelId) => RestRoute = emojiReactions.andThen(RestRoute(_, GET))
  val deleteAllReactions: (MessageId, ChannelId) => RestRoute        = reactions.andThen(RestRoute(_, DELETE))

  val channelPermissions: (OverwriteId, ChannelId) => Uri = Function.uncurried(overwrite => channel.andThen(uri => s"$uri/permissions/$overwrite"))

  val editChannelPermissions:   (OverwriteId, ChannelId) => RestRoute = channelPermissions.andThen(RestRoute(_, PUT))
  val deleteChannelPermissions: (OverwriteId, ChannelId) => RestRoute = channelPermissions.andThen(RestRoute(_, DELETE))

  val channelInvites: ChannelId => Uri = channel.andThen(uri => s"$uri/invites")

  val getChannelInvites:    ChannelId => RestRoute = channelInvites.andThen(RestRoute(_, GET))
  val createChannelInvites: ChannelId => RestRoute = channelInvites.andThen(RestRoute(_, POST))

  val triggerTyping: ChannelId => RestRoute = channel.andThen(uri => RestRoute(s"$uri/typing", POST))

  val channelPinnedMessage:       (MessageId, ChannelId) => Uri       = Function.uncurried(messageId => channel.andThen(uri => s"$uri/pins/$messageId"))
  val addPinnedChannelMessage:    (MessageId, ChannelId) => RestRoute = channelPinnedMessage.andThen(RestRoute(_, PUT))
  val deletePinnedChannelMessage: (MessageId, ChannelId) => RestRoute = channelPinnedMessage.andThen(RestRoute(_, DELETE))

  val groupDmRecipient:       (UserId, ChannelId) => Uri       = Function.uncurried(userId => channel.andThen(uri => s"$uri/$userId"))
  val groupDmAddRecipient:    (UserId, ChannelId) => RestRoute = groupDmRecipient.andThen(RestRoute(_, PUT))
  val groupDmRemoveRecipient: (UserId, ChannelId) => RestRoute = groupDmRecipient.andThen(RestRoute(_, DELETE))

}
