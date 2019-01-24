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
package net.katsstuff.ackcord.cachehandlers

import net.katsstuff.ackcord.CacheSnapshot.BotUser
import net.katsstuff.ackcord.data._
import shapeless.tag

trait Handlers {
  import CacheUpdateHandler._

  //Update

  implicit val userUpdateHandler: CacheUpdateHandler[User] = updateHandler { (builder, obj, _) =>
    builder.userMap.put(obj.id, obj)
  }

  val botUserUpdateHandler: CacheUpdateHandler[User] = updateHandler { (builder, obj, _) =>
    builder.botUser = tag[BotUser](obj)
  }

  implicit val voiceStateUpdateHandler: CacheUpdateHandler[VoiceState] = updateHandler { (builder, obj, log) =>
    val optGuild = obj.guildId
      .toRight("Can't handle VoiceState update with missing guild")
      .right
      .flatMap(builder.getGuild(_).value.toRight(s"No guild found for voice state $obj"))

    optGuild match {
      case Right(guild) =>
        val newVoiceStates =
          obj.channelId.fold(guild.voiceStates - obj.userId)(_ => guild.voiceStates.updated(obj.userId, obj))
        builder.guildMap.put(guild.id, guild.copy(voiceStates = newVoiceStates))
      case Left(e) => log.warning(e)
    }
  }
}
object Handlers extends Handlers
