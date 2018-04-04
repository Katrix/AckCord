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
package net.katsstuff.ackcord.cachehandlers

import akka.event.LoggingAdapter
import net.katsstuff.ackcord.CacheSnapshotLike.BotUser
import net.katsstuff.ackcord.websocket.gateway.GatewayEvent.ReadyData
import net.katsstuff.ackcord.syntax._
import shapeless._

//We handle this one separately as is it's kind of special
object ReadyHandler extends CacheHandler[ReadyData] {
  override def handle(builder: CacheSnapshotBuilder, obj: ReadyData)(implicit log: LoggingAdapter): Unit = {
    val ReadyData(_, botUser, rawChannels, unavailableGuilds, _, _) = obj

    //This should only contain DM and group DM channels
    val channels   = rawChannels.flatMap(_.toChannel)
    val recipients = rawChannels.flatMap(_.recipients.toSeq.flatten).map(u => u.id -> u)

    val dmChannels      = channels.flatMap(_.asDMChannel).map(ch => ch.id      -> ch)
    val groupDmChannels = channels.flatMap(_.asGroupDMChannel).map(ch => ch.id -> ch)

    val guilds = unavailableGuilds.map(g => g.id -> g)

    builder.botUser = tag[BotUser](botUser)
    builder.dmChannels ++= dmChannels
    builder.groupDmChannels ++= groupDmChannels
    builder.unavailableGuilds ++= guilds
    builder.users ++= recipients
  }
}
