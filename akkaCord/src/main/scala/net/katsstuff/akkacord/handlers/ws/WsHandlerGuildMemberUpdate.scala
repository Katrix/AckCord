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
import net.katsstuff.akkacord.data.CacheSnapshot
import net.katsstuff.akkacord.http.websocket.WsEvent.GuildMemberUpdateData

object WsHandlerGuildMemberUpdate extends Handler[GuildMemberUpdateData, APIMessage.GuildMemberUpdate] {
  override def handle(snapshot: CacheSnapshot, data: GuildMemberUpdateData)(
      implicit log:             LoggingAdapter
  ): AbstractHandlerResult[APIMessage.GuildMemberUpdate] = {
    val GuildMemberUpdateData(guildId, roles, user, nick) = data

    val res = snapshot.getGuild(guildId).map { guild =>
      val newGuild = guild.members.get(user.id) match {
        case Some(guildMember) =>
          guild.copy(members = guild.members + ((user.id, guildMember.copy(nick = nick, roles = roles))))
        case None =>
          log.warning("Received guildMember update for unknown user {}", user)
          guild
      }

      val newSnapshot = snapshot.copy(users = snapshot.users + ((user.id, user)), guilds = snapshot.guilds + ((guildId, newGuild)))
      HandlerResult(newSnapshot, APIMessage.GuildMemberUpdate(guild, roles.flatMap(newSnapshot.getRole), user, nick, _, _))
    }

    res.getOrElse(NoHandlerResult)
  }
}
