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
package net.katsstuff.ackcord.handlers

import net.katsstuff.ackcord.CacheSnapshotLike.BotUser
import net.katsstuff.ackcord.data._
import shapeless.tag

trait Handlers {
  import CacheDeleteHandler._
  import CacheUpdateHandler._

  //Update
  implicit val dmChannelUpdateHandler: CacheUpdateHandler[DMChannel] = updateHandler(
    (builder, obj, _) => builder.dmChannels.put(obj.id, obj)
  )
  implicit val groupDmChannelUpdateHandler: CacheUpdateHandler[GroupDMChannel] = updateHandler(
    (builder, obj, _) => builder.groupDmChannels.put(obj.id, obj)
  )
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
      case dmChannel: DMChannel           => handleUpdateLog(builder, dmChannel, log)(dmChannelUpdateHandler)
      case groupDmChannel: GroupDMChannel => handleUpdateLog(builder, groupDmChannel, log)(groupDmChannelUpdateHandler)
      case guildChannel: GuildChannel     => handleUpdateLog(builder, guildChannel, log)(guildChannelUpdateHandler)
    }
  }

  implicit val guildUpdateHandler: CacheUpdateHandler[Guild] = updateHandler { (builder, obj, _) =>
    builder.guilds.put(obj.id, obj)
  }

  implicit val userUpdateHandler: CacheUpdateHandler[User] = updateHandler { (builder, obj, _) =>
    builder.users.put(obj.id, obj)
  }

  val botUserUpdateHandler: CacheUpdateHandler[User] = updateHandler { (builder, obj, _) =>
    builder.botUser = tag[BotUser](obj)
  }

  implicit val voiceStateUpdateHandler: CacheUpdateHandler[VoiceState] = updateHandler { (builder, obj, log) =>
    val optGuild = obj.guildId
      .toRight("Can't handle VoiceState update with missing guild")
      .flatMap(builder.getGuild(_).toRight(s"No guild found for voice state $obj"))

    optGuild match {
      case Right(guild) =>
        val newVoiceStates =
          obj.channelId.fold(guild.voiceStates - obj.userId)(_ => guild.voiceStates + ((obj.userId, obj)))
        builder.guilds.put(guild.id, guild.copy(voiceStates = newVoiceStates))
      case Left(e) => log.warning(e)
    }
  }

  //Delete
  implicit val dmChannelDeleteHandler: CacheDeleteHandler[DMChannel] = deleteHandler(
    (builder, obj, _) => builder.dmChannels.remove(obj.id)
  )
  implicit val groupDmChannelDeleteHandler: CacheDeleteHandler[GroupDMChannel] = deleteHandler(
    (builder, obj, _) => builder.groupDmChannels.remove(obj.id)
  )
  implicit val guildChannelDeleteHandler: CacheDeleteHandler[GuildChannel] = deleteHandler { (builder, obj, log) =>
    builder.getGuild(obj.guildId) match {
      case Some(guild) => builder.guilds.put(guild.id, guild.copy(channels = guild.channels - obj.id))
      case None        => log.warning(s"No guild for delete $obj")
    }
  }

  implicit val guildDeleteHandler: CacheDeleteHandler[UnknownStatusGuild] = deleteHandler(
    (builder, obj, _) => builder.guilds.remove(obj.id)
  )
  implicit val messageDeleteHandler: CacheDeleteHandler[Message] = deleteHandler(
    (builder, obj, _) => builder.getChannelMessages(obj.channelId).remove(obj.id)
  )
  implicit val userDeleteHandler: CacheDeleteHandler[User] = deleteHandler(
    (builder, obj, _) => builder.users.remove(obj.id)
  )
}
object Handlers extends Handlers
