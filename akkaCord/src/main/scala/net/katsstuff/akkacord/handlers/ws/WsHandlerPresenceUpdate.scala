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
package net.katsstuff.akkacord.handlers
package ws

import akka.event.LoggingAdapter
import net.katsstuff.akkacord.APIMessage
import net.katsstuff.akkacord.data.{CacheSnapshot, Presence, PresenceGame, PresenceStatus, PresenceStreaming, User}
import net.katsstuff.akkacord.http.RawPresenceGame
import net.katsstuff.akkacord.http.websocket.WsEvent.PresenceUpdateData

object WsHandlerPresenceUpdate extends Handler[PresenceUpdateData, APIMessage.PresenceUpdate] {
  override def handle(snapshot: CacheSnapshot, data: PresenceUpdateData)(
      implicit log:             LoggingAdapter
  ): AbstractHandlerResult[APIMessage.PresenceUpdate] = {
    val PresenceUpdateData(partialUser, roles, game, optGuildId, status) = data

    optGuildId match {
      case Some(guildId) if snapshot.guilds.contains(guildId) =>
        import shapeless.record._
        val rPartialUser = partialUser.record
        val newUser = snapshot.getUser(rPartialUser.id) match {
          case Some(existingUser) =>
            Some(
              existingUser.copy(
                username = rPartialUser.username.getOrElse(existingUser.username),
                discriminator = rPartialUser.discriminator.getOrElse(existingUser.discriminator),
                avatar = rPartialUser.avatar.getOrElse(existingUser.avatar),
                bot = rPartialUser.bot.orElse(existingUser.bot),
                mfaEnabled = rPartialUser.mfaEnabled.orElse(existingUser.mfaEnabled),
                verified = rPartialUser.verified.orElse(existingUser.verified),
                email = rPartialUser.email.orElse(existingUser.email)
              )
            )

          case None =>
            //Let's try to create a user
            for {
              username      <- rPartialUser.username
              discriminator <- rPartialUser.discriminator
              avatar        <- rPartialUser.avatar
            } yield
              User(
                rPartialUser.id,
                username,
                discriminator,
                avatar,
                rPartialUser.bot,
                rPartialUser.mfaEnabled,
                rPartialUser.verified,
                rPartialUser.email
              )
        }

        val optNewPresence = snapshot.getPresence(guildId, rPartialUser.id) match {
          case Some(presence) =>
            val newPresence = game
              .map {
                case RawPresenceGame(name, gameType, url) =>
                  val newName = name.orElse(presence.game.map(_.name))
                  val content = newName.flatMap { name =>
                    gameType.flatMap {
                      case 0 => Some(PresenceGame(name))
                      case 1 => url.map(PresenceStreaming(name, _))
                    }
                  }

                  val newStatus = status
                    .map {
                      case "online"  => PresenceStatus.Online
                      case "idle"    => PresenceStatus.Idle
                      case "offline" => PresenceStatus.Offline
                    }
                    .getOrElse(presence.status)

                  Presence(rPartialUser.id, content, newStatus)
              }
              .getOrElse(presence)

            Some(newPresence)
          case None =>
            game
              .flatMap {
                case RawPresenceGame(name, gameType, url) =>
                  val content = name.flatMap { name =>
                    gameType.flatMap {
                      case 0 => Some(PresenceGame(name))
                      case 1 => url.map(PresenceStreaming(name, _))
                    }
                  }

                  val newStatus = status
                    .map {
                      case "online"  => PresenceStatus.Online
                      case "idle"    => PresenceStatus.Idle
                      case "offline" => PresenceStatus.Offline
                    }

                  newStatus.map(Presence(rPartialUser.id, content, _))
              }
        }

        val guild       = snapshot.guilds(guildId)
        val withNewUser = newUser.map(u => snapshot.copy(users = snapshot.users + ((u.id, u)))).getOrElse(snapshot)

        val withUpdatedRoles = {
          val newMembers = guild.members
            .get(rPartialUser.id)
            .map(m => guild.members + ((rPartialUser.id, m.copy(roles = roles))))
            .getOrElse(guild.members)
          val newGuild = guild.copy(members = newMembers)
          withNewUser.copy(guilds = withNewUser.guilds + ((guildId, newGuild)))
        }

        val withNewPresence = optNewPresence
          .map { newPresence =>
            val newGuildPresences = withUpdatedRoles.presences(guildId) + ((rPartialUser.id, newPresence))
            withUpdatedRoles.copy(presences = withUpdatedRoles.presences + ((guildId, newGuildPresences)))
          }
          .getOrElse(withUpdatedRoles)

        //Did I miss anything here?
        //TODO: This is techically wrong. The event function should be created when the presence is in scope
        val createMessage = (snapshot: CacheSnapshot, prevSnapshot: CacheSnapshot) =>
          prevSnapshot.presences(guildId).get(rPartialUser.id).map(p => APIMessage.PresenceUpdate(p, snapshot, prevSnapshot))
        OptionalMessageHandlerResult(withNewPresence, createMessage)
      case _ =>
        //TODO: What to do if no guild id?
        NoHandlerResult
    }
  }
}
