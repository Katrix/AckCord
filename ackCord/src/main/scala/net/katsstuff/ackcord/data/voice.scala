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
package net.katsstuff.ackcord.data

/**
  * Represents a user voice connection status
  * @param guildId The guild this state belongs to
  * @param channelId The channel the user is in, or None if the user isn't in a channel
  * @param userId The user of this state.
  * @param sessionId The sessionId
  * @param deaf If the user is deafened by the guild
  * @param mute If the user is muted by the guild
  * @param selfDeaf If the user is deafened locally
  * @param selfMute If the user is muted locally
  * @param suppress If the client has muted the user
  */
case class VoiceState(
    guildId: Option[GuildId],
    channelId: Option[ChannelId], //channelId can be null
    userId: UserId,
    sessionId: String,
    deaf: Boolean,
    mute: Boolean,
    selfDeaf: Boolean,
    selfMute: Boolean,
    suppress: Boolean
) extends GetGuildOpt
    with GetVChannelOpt
    with GetUser

/**
  * A voice region
  * @param id The id of the region
  * @param name The name of the voice region
  * @param sampleHostname An example host name
  * @param samplePort An example host port
  * @param vip If this is a VIP only server
  * @param optimal If this is the server closest to the client
  * @param deprecated If this is a deprecated region
  * @param custom If this is a custom region
  */
case class VoiceRegion(
    id: String,
    name: String,
    sampleHostname: String,
    samplePort: Int,
    vip: Boolean,
    optimal: Boolean,
    deprecated: Boolean,
    custom: Boolean
)
