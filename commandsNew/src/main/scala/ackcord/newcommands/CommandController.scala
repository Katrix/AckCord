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
package ackcord.newcommands

import ackcord.data.{Guild, GuildId, GuildMember, Permission, TGuildChannel, User, VGuildChannel}
import ackcord.requests.RequestHelper
import ackcord.util.StreamInstances.SourceRequest
import ackcord.util.Streamable
import ackcord.{CacheSnapshot, RequestRunner}
import akka.NotUsed
import cats.{Monad, ~>}
import scala.language.higherKinds

import scala.concurrent.ExecutionContext

/**
  * The base command controller that you will place your commands in.
  * Contains partially applied types, and the Command builder object.
  * @tparam F The type of effect for the cache
  */
abstract class CommandController[F[_]: Streamable: Monad](val requests: RequestHelper) {

  type CommandMessage[+A]            = ackcord.newcommands.CommandMessage[F, A]
  type GuildCommandMessage[+A]       = ackcord.newcommands.GuildCommandMessage[F, A]
  type UserCommandMessage[+A]        = ackcord.newcommands.UserCommandMessage[F, A]
  type GuildMemberCommandMessage[+A] = ackcord.newcommands.GuildMemberCommandMessage[F, A]
  type VoiceGuildCommandMessage[+A]  = ackcord.newcommands.VoiceGuildCommandMessage[F, A]

  type GuildUserCommandMessage[+A]        = GuildCommandMessage[A] with UserCommandMessage[A]
  type VoiceGuildMemberCommandMessage[+A] = GuildMemberCommandMessage[A] with VoiceGuildCommandMessage[A]

  type ComplexCommand[A, Mat] = ackcord.newcommands.Command[F, A, Mat]
  type Command[A]             = ackcord.newcommands.Command[F, A, NotUsed]

  type CommandBuilder[+M[_], A]         = ackcord.newcommands.CommandBuilder[F, M, A]
  type CommandFunction[-I[_], +O[_]]    = ackcord.newcommands.CommandFunction[F, I, O]
  type CommandTransformer[-I[_], +O[_]] = ackcord.newcommands.CommandTransformer[F, I, O]

  implicit val requestRunner: RequestRunner[SourceRequest, F] = {
    implicit val impRequest: RequestHelper = requests
    ackcord.RequestRunner.sourceRequestRunner[F]
  }
  implicit val ec: ExecutionContext = requests.mat.executionContext

  implicit def findCache[A](implicit message: CommandMessage[A]): CacheSnapshot[F] = message.cache

  /**
    * The base command builder that you can build off if you don't like the
    * default provided builder.
    */
  val baseCommandBuilder: CommandBuilder[CommandMessage, List[String]] = CommandBuilder.rawBuilder[F](requests)

  /**
    * The default command builder you will use to create most of your commands.
    * By default blocks bots from using the commands.
    */
  val Command: CommandBuilder[UserCommandMessage, List[String]] =
    baseCommandBuilder.andThen(CommandFunction.nonBot { user =>
      λ[CommandMessage ~> UserCommandMessage](m => UserCommandMessage.Default(user, m))
    })

  /**
    * Another default command builder for you to use. Can only be used in
    * guilds, and includes the guild, guild channel and user of the command.
    */
  val GuildCommand: CommandBuilder[GuildMemberCommandMessage, List[String]] =
    Command
      .andThen(CommandFunction.onlyInGuild { (chG, g) =>
        λ[UserCommandMessage ~> GuildUserCommandMessage](m => GuildCommandMessage.WithUser(chG, g, m.user, m))
      })
      .andThen(CommandFunction.withGuildMember { member =>
        λ[GuildUserCommandMessage ~> GuildMemberCommandMessage](
          m => GuildMemberCommandMessage.Default(m.tChannel, m.guild, m.user, member, m)
        )
      })

  /**
    * A command builder that only accepts users that are in a voice channel.
    */
  val GuildVoiceCommand: CommandBuilder[VoiceGuildMemberCommandMessage, List[String]] =
    GuildCommand.andThen(CommandFunction.inVoiceChannel { vCh =>
      λ[GuildMemberCommandMessage ~> VoiceGuildMemberCommandMessage](
        m => VoiceGuildCommandMessage.WithGuildMember(m.tChannel, m.guild, m.user, m.guildMember, vCh, m)
      )
    })

  /**
    * Various command functions to filter or transform command messages.
    */
  object CommandFunction {

    type Expander[M[_], H[_], A] = M[A]

    def onlyInGuild[I[A] <: CommandMessage[A], O[_]](
        create: (TGuildChannel, Guild) => I ~> O
    ): CommandFunction[I, O] =
      CommandBuilder.onlyInGuild[F, I, O](create)

    /**
      * A command function that lets you add the guild member to a command message.
      */
    def withGuildMember[I[A] <: GuildUserCommandMessage[A], O[_]](
        create: GuildMember => I ~> O
    ): CommandTransformer[I, O] =
      CommandBuilder.withGuildMember[F, I, O](create)

    def inVoiceChannel[I[A] <: GuildUserCommandMessage[A], O[_]](
        create: VGuildChannel => I ~> O
    ): CommandFunction[I, O] = CommandBuilder.inVoiceChannel[F, I, O](create)

    /**
      * Only allow commands sent from one specific guild.
      */
    def inOneGuild[M[A] <: GuildCommandMessage[A]](guildId: GuildId): CommandFunction[M, M] =
      CommandBuilder.inOneGuild[F, M](guildId)

    /**
      * Those who use this command need some set of permissions.
      */
    def needPermission[M[A] <: GuildCommandMessage[A]](
        neededPermission: Permission
    ): CommandFunction[M, M] =
      CommandBuilder.needPermission[F, M](neededPermission)

    /**
      * Only non bots can use this command.
      */
    def nonBot[I[A] <: CommandMessage[A], O[_]](create: User => I ~> O): CommandFunction[I, O] =
      CommandBuilder.nonBot[F, I, O](create)
  }
}
