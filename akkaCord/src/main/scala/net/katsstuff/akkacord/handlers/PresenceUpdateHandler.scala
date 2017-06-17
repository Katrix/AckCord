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

import scala.collection.mutable

import akka.event.LoggingAdapter
import net.katsstuff.akkacord.data.{Presence, PresenceGame, PresenceStatus, PresenceStreaming, User}
import net.katsstuff.akkacord.http.RawPresenceGame
import net.katsstuff.akkacord.http.websocket.WsEvent.PresenceUpdateData

object PresenceUpdateHandler extends CacheUpdateHandler[PresenceUpdateData] {
  override def handle(builder: CacheSnapshotBuilder, obj: PresenceUpdateData)(implicit log: LoggingAdapter): Unit = {
    val PresenceUpdateData(partialUser, roles, game, optGuildId, status) = obj

    optGuildId match {
      case Some(guildId) if builder.guilds.contains(guildId) =>

        //Add the user
        builder.getUser(partialUser.id) match {
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
            builder.users.put(existingUser.id, newUser)

          case None =>
            //Let's try to create a user
            for {
              username      <- partialUser.username
              discriminator <- partialUser.discriminator
            } {
              val newUser = User(
                partialUser.id,
                username,
                discriminator,
                partialUser.avatar,
                partialUser.bot,
                partialUser.mfaEnabled,
                partialUser.verified,
                partialUser.email
              )

              builder.users.put(partialUser.id, newUser)
            }
        }

        //Add the presence
        builder.getPresence(guildId, partialUser.id) match {
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

                  val newStatus = status.getOrElse(presence.status)
                  Presence(partialUser.id, content, newStatus)
              }
              .getOrElse(presence)

            builder.presences.getOrElseUpdate(guildId, mutable.Map.empty).put(partialUser.id, newPresence)
          case None =>
            game.foreach {
              case RawPresenceGame(name, gameType, url) =>
                val content = name.flatMap { name =>
                  gameType.flatMap {
                    case 0 => Some(PresenceGame(name))
                    case 1 => url.map(PresenceStreaming(name, _))
                  }
                }

                status.foreach { status =>
                  builder.presences.getOrElseUpdate(guildId, mutable.Map.empty).put(partialUser.id, Presence(partialUser.id, content, status))
                }
            }
        }

        val guild = builder.guilds(guildId)

        //Update roles
        guild.members
          .get(partialUser.id)
          .map(m => guild.members + ((partialUser.id, m.copy(roles = roles))))
          .foreach { newMembers =>
            val newGuild = guild.copy(members = newMembers)
            builder.guilds.put(guildId, newGuild)
          }

      case _ =>
      //TODO: What to do if no guild id?
    }
  }
}
