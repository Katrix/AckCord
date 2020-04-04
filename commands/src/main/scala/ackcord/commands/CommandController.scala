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
package ackcord.commands

import scala.concurrent.ExecutionContext

import ackcord.requests.{Requests, RequestsHelper}
import ackcord.util.StreamInstances.SourceRequest
import ackcord.{CacheSnapshot, RequestRunner}
import akka.NotUsed
import cats.~>

/**
  * The base command controller that you will place your commands in.
  * Contains partially applied types, and the Command builder object.
  */
abstract class CommandController(val requests: Requests) {

  @deprecated("Prefer using the RequestHelper instead", since = "0.16")
  implicit val requestRunner: RequestRunner[SourceRequest] = {
    implicit val impRequest: Requests = requests
    ackcord.RequestRunner.sourceRequestRunner
  }

  val requestHelper: RequestsHelper = new RequestsHelper(requests)

  implicit val ec: ExecutionContext = requests.system.executionContext

  implicit def findCache[A](implicit message: CommandMessage[A]): CacheSnapshot = message.cache

  /**
    * Determines the default value for if a mention should required.
    */
  def defaultMustMention: Boolean = true

  /**
    * The base command builder that you can build off if you don't like the
    * default provided builder.
    */
  val baseCommandBuilder: CommandBuilder[CommandMessage, NotUsed] =
    CommandBuilder.rawBuilder(requests, defaultMustMention)

  /**
    * The default command builder you will use to create most of your commands.
    * By default blocks bots from using the commands.
    */
  val Command: CommandBuilder[UserCommandMessage, NotUsed] =
    baseCommandBuilder.andThen(CommandBuilder.nonBot { user =>
      λ[CommandMessage ~> UserCommandMessage](m => UserCommandMessage.Default(user, m))
    })

  /**
    * Another default command builder for you to use. Can only be used in
    * guilds, and includes the guild, guild channel and user of the command.
    */
  val GuildCommand: CommandBuilder[GuildMemberCommandMessage, NotUsed] =
    Command
      .andThen(CommandBuilder.onlyInGuild { (chG, g) =>
        λ[UserCommandMessage ~> GuildUserCommandMessage](m => GuildCommandMessage.WithUser(chG, g, m.user, m))
      })
      .andThen(CommandBuilder.withGuildMember { member =>
        λ[GuildUserCommandMessage ~> GuildMemberCommandMessage](m =>
          GuildMemberCommandMessage.Default(m.tChannel, m.guild, m.user, member, m)
        )
      })

  /**
    * A command builder that only accepts users that are in a voice channel.
    */
  val GuildVoiceCommand: CommandBuilder[VoiceGuildMemberCommandMessage, NotUsed] =
    GuildCommand.andThen(CommandBuilder.inVoiceChannel { vCh =>
      λ[GuildMemberCommandMessage ~> VoiceGuildMemberCommandMessage](m =>
        VoiceGuildCommandMessage.WithGuildMember(m.tChannel, m.guild, m.user, m.guildMember, vCh, m)
      )
    })
}
