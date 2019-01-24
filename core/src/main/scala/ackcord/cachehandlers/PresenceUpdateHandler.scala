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
package ackcord.cachehandlers

import ackcord.data.{Presence, User}
import ackcord.gateway.GatewayEvent.PresenceUpdateData
import akka.event.LoggingAdapter

object PresenceUpdateHandler extends CacheUpdateHandler[PresenceUpdateData] {
  override def handle(builder: CacheSnapshotBuilder, obj: PresenceUpdateData)(implicit log: LoggingAdapter): Unit = {
    val PresenceUpdateData(partialUser, roles, rawActivity, guildId, status, _) = obj

    builder.guildMap.get(guildId).foreach { oldGuild =>
      //Add the user
      builder.getUser(partialUser.id).value match {
        case Some(existingUser) =>
          val newUser = existingUser.copy(
            username = partialUser.username.getOrElse(existingUser.username),
            discriminator = partialUser.discriminator.getOrElse(existingUser.discriminator),
            avatar = partialUser.avatar.orElse(existingUser.avatar),
            bot = partialUser.bot.orElse(existingUser.bot),
            mfaEnabled = partialUser.mfaEnabled.orElse(existingUser.mfaEnabled),
            verified = partialUser.verified.orElse(existingUser.verified),
            email = partialUser.email.orElse(existingUser.email)
          )
          builder.userMap.put(newUser.id, newUser)

        case None =>
          //Let's try to create a user
          for {
            username      <- partialUser.username
            discriminator <- partialUser.discriminator
          } {
            val newUser = User(
              id = partialUser.id,
              username = username,
              discriminator = discriminator,
              avatar = partialUser.avatar,
              bot = partialUser.bot,
              mfaEnabled = partialUser.mfaEnabled,
              verified = partialUser.verified,
              email = partialUser.email,
              flags = partialUser.flags,
              premiumType = partialUser.premiumType
            )

            builder.userMap.put(newUser.id, newUser)
          }
      }

      val newPresence = Presence(partialUser.id, rawActivity.map(_.toActivity), status)

      val oldMembers = oldGuild.members
      val newMembers = oldMembers
        .get(partialUser.id)
        .map(member => oldMembers.updated(partialUser.id, member.copy(roleIds = roles)))
        .getOrElse(oldMembers)

      val newGuild =
        oldGuild.copy(presences = oldGuild.presences.updated(partialUser.id, newPresence), members = newMembers)

      builder.guildMap.put(guildId, newGuild)
    }
  }
}
