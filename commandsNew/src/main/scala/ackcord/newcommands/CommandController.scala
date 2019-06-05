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

import ackcord.data.{GuildId, Permission}
import ackcord.requests.RequestHelper
import ackcord.util.StreamInstances.SourceRequest
import ackcord.util.Streamable
import ackcord.{CacheSnapshot, RequestRunner}
import akka.NotUsed
import cats.Monad

import scala.language.higherKinds

/**
  * The base command controller that you will place your commands in.
  * Contains partially applied types, and the Command builder object.
  * @tparam F The type of effect for the cache
  */
abstract class CommandController[F[_]: Streamable: Monad](val requests: RequestHelper) {

  type CommandMessage[+A]            = ackcord.newcommands.CommandMessage[F, A]
  type ComplexCommand[A, Mat]        = ackcord.newcommands.Command[F, A, Mat]
  type Command[A]                    = ackcord.newcommands.Command[F, A, NotUsed]
  type CommandBuilder[+M[_], A]      = ackcord.newcommands.CommandBuilder[F, M, A]
  type CommandFunction[-I[_], +O[_]] = ackcord.newcommands.CommandFunction[F, I, O]

  implicit val requestRunner: RequestRunner[SourceRequest, F] = {
    implicit val impRequest: RequestHelper = requests
    ackcord.RequestRunner.sourceRequestRunner[F]
  }

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
  val Command: CommandBuilder[CommandMessage, List[String]] = baseCommandBuilder.andThen(CommandFunction.nonBot)

  /**
    * Various command functions to filter or transform command messages.
    */
  object CommandFunction {

    /**
      * Only allows commands in a specific context.
      */
    def onlyIn[M[A] <: CommandMessage[A]](context: Context): CommandFunction[M, M] =
      CommandBuilder.onlyIn[F, M](context)

    /**
      * Only allow commands sent from a guild.
      */
    def onlyInGuild[M[A] <: CommandMessage[A]]: CommandFunction[M, M] =
      onlyIn(Context.Guild)

    /**
      * Only allow commands sent from a DM.
      */
    def onlyInDm[M[A] <: CommandMessage[A]]: CommandFunction[M, M] =
      onlyIn(Context.DM)

    /**
      * Only allow commands sent from one specific guild.
      */
    def inOneGuild[M[A] <: CommandMessage[A]](guildId: GuildId): CommandFunction[M, M] =
      CommandBuilder.inOneGuild[F, M](guildId)

    /**
      * Those who use this command need some set of permissions.
      */
    def needPermission[M[A] <: CommandMessage[A]](
        neededPermission: Permission
    ): CommandFunction[M, M] = CommandBuilder.needPermission[F, M](neededPermission)

    /**
      * Only non bots can use this command.
      */
    def nonBot[M[A] <: CommandMessage[A]]: CommandFunction[M, M] =
      CommandBuilder.nonBot[F, M]
  }
}
