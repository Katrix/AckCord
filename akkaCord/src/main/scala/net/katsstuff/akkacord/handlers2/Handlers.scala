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
package net.katsstuff.akkacord.handlers2

import net.katsstuff.akkacord.data._

trait Handlers {
  import CacheUpdateHandler._
  import CacheDeleteHandler._

  //Update
  implicit val dmChannelUpdateHandler: CacheUpdateHandler[DMChannel] = updateHandler((builder, obj, _) => builder.dmChannels.put(obj.id, obj))
  implicit val guildChannelUpdateHandler: CacheUpdateHandler[GuildChannel] = updateHandler { (builder, obj, log) =>
    builder
      .getGuild(obj.guildId)
      .toRight(s"No guild for update $obj")
      .map(g => g.copy(channels = g.channels + ((obj.id, obj)))) match {
      case Right(guild) => builder.guilds.put(guild.id, guild)
      case Left(e)      => log.warning(e)
    }
  }
  implicit val channelUpdateHandler: CacheUpdateHandler[Channel] = updateHandler { (builder, obj, log) =>
    obj match {
      case dmChannel: DMChannel => handleUpdateLog(builder, dmChannel, log)
      case guildChannel: GuildChannel => handleUpdateLog(builder, guildChannel, log)
    }
  }

  implicit val guildUpdateHandler: CacheUpdateHandler[AvailableGuild] = updateHandler { (builder, obj, log) =>
    builder.guilds.put(obj.id, obj)
  }

  implicit val userUpdateHandler: CacheUpdateHandler[User] = updateHandler { (builder, obj, log) =>
    builder.users.put(obj.id, obj)
  }

  //Delete
  implicit val dmChannelDeleteHandler: CacheDeleteHandler[DMChannel] = deleteHandler((builder, obj, _) => builder.dmChannels.remove(obj.id))
  implicit val guildChannelDeleteHandler: CacheDeleteHandler[GuildChannel] = deleteHandler { (builder, obj, log) =>
    builder.getGuild(obj.guildId) match {
      case Some(guild) => builder.guilds.put(guild.id, guild.copy(channels = guild.channels - obj.id))
      case None        => log.warning(s"No guild for delete $obj")
    }
  }

  implicit val guildDeleteHandler: CacheDeleteHandler[Guild] = deleteHandler((builder, obj, _) => builder.guilds.remove(obj.id))
  implicit val messageDeleteHandler: CacheDeleteHandler[Message] = deleteHandler(
    (builder, obj, _) => builder.getChannelMessages(obj.channelId).remove(obj.id)
  )
  implicit val userDeleteHandler: CacheDeleteHandler[User] = deleteHandler((builder, obj, _) => builder.users.remove(obj.id))
}
