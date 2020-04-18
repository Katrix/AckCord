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
package ackcord.data

import ackcord.CacheSnapshot

trait GetGuild {
  def guildId: GuildId

  /**
    * The guild for this object
    */
  def guild(implicit snapshot: CacheSnapshot): Option[Guild] = snapshot.getGuild(guildId)
}

trait GetGuildOpt {
  def guildId: Option[GuildId]

  /**
    * The guild for this object
    */
  def guild(implicit snapshot: CacheSnapshot): Option[Guild] =
    guildId.flatMap(snapshot.getGuild)
}

trait GetUser {
  def userId: UserId

  /**
    * The user for this object
    */
  def user(implicit snapshot: CacheSnapshot): Option[User] = snapshot.getUser(userId)
}

trait GetTextChannel {
  def channelId: ChannelId

  /**
    * Resolve the channelId of this object as a dm channel
    */
  def dmChannel(implicit snapshot: CacheSnapshot): Option[DMChannel] = snapshot.getDmChannel(channelId)

  /**
    * Resolve the channelId of this object as a TextGuildChannel
    */
  def textGuildChannel(implicit snapshot: CacheSnapshot): Option[TextGuildChannel] =
    snapshot.getGuildChannel(channelId).collect {
      case guildChannel: TextGuildChannel => guildChannel
    }

  /**
    * Resolve the channelId of this object as a TextGuildChannel using an provided guildId
    */
  def textGuildChannel(
      guildId: GuildId
  )(implicit snapshot: CacheSnapshot): Option[TextGuildChannel] =
    snapshot.getGuildChannel(guildId, channelId).collect {
      case guildChannel: TextGuildChannel => guildChannel
    }
}

trait GetVoiceChannelOpt {
  def channelId: Option[ChannelId]

  /**
    * Resolve the channelId of this object as a voice channel.
    */
  def voiceChannel(implicit snapshot: CacheSnapshot): Option[Channel] =
    channelId.flatMap(snapshot.getChannel).collect {
      case voiceChannel: VoiceGuildChannel => voiceChannel
    }
}
