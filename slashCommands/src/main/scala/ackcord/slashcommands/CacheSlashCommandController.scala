/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 Katrix
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
package ackcord.slashcommands

import ackcord.CacheSnapshot
import ackcord.requests.Requests
import akka.NotUsed
import cats.~>

class CacheSlashCommandController(val requests: Requests)
    extends SlashCommandControllerBase[ResolvedCommandInteraction] {

  implicit def findCache[A](implicit message: CacheCommandInteraction[A]): CacheSnapshot = message.cache

  override val Command: CommandBuilder[ResolvedCommandInteraction, NotUsed] = new CommandBuilder(
    new CommandTransformer[CommandInteraction, CacheCommandInteraction] {
      override def filter[A](from: CommandInteraction[A]): Either[Option[String], CacheCommandInteraction[A]] =
        from.optCache match {
          case Some(value) => Right(BaseCacheCommandInteraction(from.commandInvocationInfo, value))
          case None        => Left(Some("This command can only be used when the bot has access to a cache"))
        }
    }.andThen(
      CommandTransformer.resolved((channel, optGuild) =>
        Lambda[CacheCommandInteraction ~> ResolvedCommandInteraction] { baseInteraction =>
          BaseResolvedCommandInteraction(
            baseInteraction.commandInvocationInfo,
            channel,
            optGuild,
            baseInteraction.cache
          )
        }
      )
    ),
    Left(implicitly),
    Map.empty
  )

  val GuildCommand: CommandBuilder[GuildCommandInteraction, NotUsed] = Command.andThen(
    CommandTransformer.onlyInGuild((guild, guildMember, memberPermissions, channel) =>
      Lambda[ResolvedCommandInteraction ~> GuildCommandInteraction](i =>
        BaseGuildCommandInteraction(i.commandInvocationInfo, channel, guild, guildMember, memberPermissions, i.cache)
      )
    )
  )

  val GuildVoiceCommand: CommandBuilder[VoiceChannelCommandInteraction, NotUsed] = GuildCommand.andThen(
    CommandTransformer.inVoiceChannel(voiceChannel =>
      Lambda[GuildCommandInteraction ~> VoiceChannelCommandInteraction](i =>
        BaseVoiceChannelCommandInteraction(
          i.commandInvocationInfo,
          i.textChannel,
          i.guild,
          i.member,
          i.memberPermissions,
          voiceChannel,
          i.cache
        )
      )
    )
  )
}
