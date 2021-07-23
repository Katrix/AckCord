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
package ackcord.interactions.commands

import java.util.Locale

import ackcord.data._
import ackcord.interactions._
import ackcord.{CacheSnapshot, OptFuture}
import akka.NotUsed
import cats.syntax.either._

sealed trait CommandOrGroup {
  def name: String
  def description: String
  def defaultPermission: Boolean

  def extra: Map[String, String]

  def toCommandOption: ApplicationCommandOption
  def makeCommandOptions: Seq[ApplicationCommandOption]

  def handleRaw(
      clientId: String,
      rawInteraction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): InteractionResponse
}
case class Command[InteractionObj[_], A] private (
    name: String,
    description: String,
    defaultPermission: Boolean,
    extra: Map[String, String],
    paramList: Either[NotUsed =:= A, ParamList[A]],
    filter: DataInteractionTransformer[CommandInteraction, InteractionObj],
    handle: InteractionObj[A] => InteractionResponse
) extends CommandOrGroup {

  override def makeCommandOptions: Seq[ApplicationCommandOption] = {
    val normalList                          = paramList.map(_.map(identity)).getOrElse(Nil)
    val (requiredParams, notRequiredParams) = normalList.partition(_.isRequired)
    (requiredParams ++ notRequiredParams).map(_.toCommandOption)
  }

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption(
    ApplicationCommandOptionType.SubCommand,
    name,
    description,
    required = Some(false),
    Some(Nil),
    Some(makeCommandOptions)
  )

  override def handleRaw(
      clientId: String,
      interaction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): InteractionResponse = {
    val data = interaction.data.get.asInstanceOf[ApplicationCommandInteractionData]

    val optionsMap = data.options
      .getOrElse(Nil)
      .collect {
        case dataOption @ ApplicationCommandInteractionDataOption(name, _, _) =>
          name.toLowerCase(Locale.ROOT) -> (dataOption.asInstanceOf[ApplicationCommandInteractionDataOption[Any]])
      }
      .toMap

    val optArgs = paramList match {
      case Right(value) => value.constructValues(optionsMap, data.resolved.getOrElse(ApplicationCommandInteractionDataResolved.empty))
      case Left(ev)     => Right(ev(NotUsed))
    }

    optArgs
      .leftMap(Some(_))
      .flatMap { args =>
        val invocationInfo = CommandInvocationInfo(
          InteractionInvocationInfo(
            interaction.id,
            interaction.guildId,
            interaction.channelId,
            interaction.member.map(_.user).orElse(interaction.user).get,
            interaction.member,
            interaction.memberPermission,
            interaction.token,
            clientId
          ),
          data.id,
          args
        )

        val base = cacheSnapshot match {
          case Some(value) => BaseCacheCommandInteraction(invocationInfo, value)
          case None        => StatelessCommandInteraction(invocationInfo)
        }

        filter.filter(base)
      }
      .map(handle)
      .leftMap {
        case Some(error) =>
          InteractionResponse.ChannelMessage(
            RawInteractionApplicationCommandCallbackData(content = Some(s"An error occurred: $error")),
            () => OptFuture.unit
          )
        case None =>
          InteractionResponse.ChannelMessage(
            RawInteractionApplicationCommandCallbackData(content = Some("An error occurred")),
            () => OptFuture.unit
          )
      }
      .merge
  }
}
case class CommandGroup private (
    name: String,
    description: String,
    defaultPermission: Boolean,
    extra: Map[String, String],
    commands: Seq[CommandOrGroup]
) extends CommandOrGroup {
  override def makeCommandOptions: Seq[ApplicationCommandOption] = commands.map(_.toCommandOption)

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption(
    ApplicationCommandOptionType.SubCommandGroup,
    name,
    description,
    required = Some(false),
    Some(Nil),
    Some(makeCommandOptions)
  )

  private lazy val subCommandsByName: Map[String, CommandOrGroup] = commands.map(c => c.name -> c).toMap

  override def handleRaw(
      clientId: String,
      rawInteraction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): InteractionResponse = {
    val data = rawInteraction.data.get.asInstanceOf[ApplicationCommandInteractionData]

    val subcommandExecution = data.options.getOrElse(Nil).collectFirst {
      case ApplicationCommandInteractionDataOption(
            name,
            ApplicationCommandOptionType.SubCommand | ApplicationCommandOptionType.SubCommandGroup,
            options
          ) =>
        subCommandsByName(name) -> options.asInstanceOf[Seq[ApplicationCommandInteractionDataOption[_]]]
    }

    subcommandExecution match {
      case Some((subcommand, options)) =>
        subcommand.handleRaw(
          clientId,
          rawInteraction.copy(data = Some(data.copy(options = Some(options)))),
          cacheSnapshot
        )
      case None =>
        InteractionResponse.ChannelMessage(
          RawInteractionApplicationCommandCallbackData(content = Some("Encountered dead end for subcommands")),
          () => OptFuture.unit
        )
    }

  }
}
