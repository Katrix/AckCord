/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
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
package net.katsstuff.ackcord.highlvl

import akka.actor.{Actor, Props, Terminated}
import net.katsstuff.ackcord.DiscordShard.ShardActor
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler.Logout

class ShardShutdownManager(shards: Seq[ShardActor]) extends Actor {
  var shardNum: Int = shards.size

  override def receive: Receive = {
    case Logout =>
      shards.foreach { shard =>
        context.watch(shard)
        shard ! Logout
      }
    case Terminated(_) =>
      shardNum -= 1
      if(shardNum == 0) {
        context.stop(self)
      }
  }
}
object ShardShutdownManager {
  def props(shards: Seq[ShardActor]): Props = Props(new ShardShutdownManager(shards))
}
