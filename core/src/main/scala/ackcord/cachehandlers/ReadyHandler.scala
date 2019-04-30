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

import ackcord.CacheSnapshot.BotUser
import ackcord.gateway.GatewayEvent.ReadyData
import akka.event.LoggingAdapter
import shapeless.tag

//We handle this one separately as is it's kind of special
object ReadyHandler extends CacheHandler[ReadyData] {
  override def handle(builder: CacheSnapshotBuilder, obj: ReadyData)(implicit log: LoggingAdapter): Unit = {
    val ReadyData(_, botUser, unavailableGuilds, _, _, _) = obj

    val guilds = unavailableGuilds.map(g => g.id -> g)

    builder.botUser = tag[BotUser](botUser)
    builder.unavailableGuildMap ++= guilds
  }
}
