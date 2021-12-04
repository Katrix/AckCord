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

sealed trait CreatedApplicationCommand {
  def name: String
  def description: Option[String]
  def defaultPermission: Boolean

  def extra: Map[String, String]

  def makeCommandOptions: Seq[ApplicationCommandOption]

  def handleRaw(
      clientId: String,
      rawInteraction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): InteractionResponse

  def commandType: ApplicationCommandType
}
object CreatedApplicationCommand {
  private[commands] def handleCommon[A, InteractionObj[_]](
      clientId: String,
      interaction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot],
      optArgs: Either[String, A],
      filter: DataInteractionTransformer[CommandInteraction, InteractionObj],
      handle: InteractionObj[A] => InteractionResponse
  ) = {
    val data = interaction.data.get.asInstanceOf[ApplicationCommandInteractionData]
    optArgs
      .leftMap(Some(_))
      .flatMap { args =>
        val invocationInfo = CommandInvocationInfo(
          InteractionInvocationInfo(
            interaction.id,
            interaction.guildId,
            interaction.channelId.getOrElse(
              throw new IllegalArgumentException("Got an interaction without a channel for a command")
            ),
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

sealed trait SlashCommandOrGroup extends CreatedApplicationCommand {
  def toCommandOption: ApplicationCommandOption
  override def commandType: ApplicationCommandType = ApplicationCommandType.ChatInput
}

case class SlashCommand[InteractionObj[_], A] private (
    name: String,
    description: Option[String],
    defaultPermission: Boolean,
    extra: Map[String, String],
    paramList: Either[NotUsed =:= A, ParamList[A]],
    filter: DataInteractionTransformer[CommandInteraction, InteractionObj],
    handle: InteractionObj[A] => InteractionResponse
) extends SlashCommandOrGroup {

  override def makeCommandOptions: Seq[ApplicationCommandOption] = {
    val normalList                          = paramList.map(_.map(identity)).getOrElse(Nil)
    val (requiredParams, notRequiredParams) = normalList.partition(_.isRequired)
    (requiredParams ++ notRequiredParams).map(_.toCommandOption)
  }

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption(
    ApplicationCommandOptionType.SubCommand,
    name,
    description.get,
    required = Some(false),
    Some(Nil),
    Some(makeCommandOptions),
    None,
    None,
    None
  )

  override def handleRaw(
      clientId: String,
      interaction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): InteractionResponse = {
    val data = interaction.data.get.asInstanceOf[ApplicationCommandInteractionData]
    if (data.`type` != ApplicationCommandType.ChatInput) {
      InteractionResponse.ChannelMessage(
        RawInteractionApplicationCommandCallbackData(content =
          Some(s"Encountered unexpected interaction type ${data.`type`} for slash command")
        ),
        () => OptFuture.unit
      )
    } else {
      val optionsMap = data.options
        .getOrElse(Nil)
        .collect { case dataOption @ ApplicationCommandInteractionDataOption(name, _, _) =>
          name.toLowerCase(Locale.ROOT) -> (dataOption.asInstanceOf[ApplicationCommandInteractionDataOption[Any]])
        }
        .toMap

      val optArgs = paramList match {
        case Right(value) =>
          value.constructValues(optionsMap, data.resolved.getOrElse(ApplicationCommandInteractionDataResolved.empty))
        case Left(ev) => Right(ev(NotUsed))
      }

      CreatedApplicationCommand.handleCommon(clientId, interaction, cacheSnapshot, optArgs, filter, handle)
    }
  }
}
case class SlashCommandGroup private (
    name: String,
    description: Option[String],
    defaultPermission: Boolean,
    extra: Map[String, String],
    commands: Seq[SlashCommandOrGroup]
) extends SlashCommandOrGroup {
  override def makeCommandOptions: Seq[ApplicationCommandOption] = commands.map(_.toCommandOption)

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption(
    ApplicationCommandOptionType.SubCommandGroup,
    name,
    description.get,
    required = Some(false),
    Some(Nil),
    Some(makeCommandOptions),
    None,
    None,
    None
  )

  private lazy val subCommandsByName: Map[String, CreatedApplicationCommand] = commands.map(c => c.name -> c).toMap

  override def handleRaw(
      clientId: String,
      rawInteraction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): InteractionResponse = {
    val data = rawInteraction.data.get.asInstanceOf[ApplicationCommandInteractionData]
    if (data.`type` != ApplicationCommandType.ChatInput) {
      InteractionResponse.ChannelMessage(
        RawInteractionApplicationCommandCallbackData(content =
          Some(s"Encountered unexpected interaction type ${data.`type`} for slash command")
        ),
        () => OptFuture.unit
      )
    } else {
      val subcommandExecution = data.options.getOrElse(Nil).collectFirst {
        case ApplicationCommandInteractionDataOption(
              name,
              ApplicationCommandOptionType.SubCommand | ApplicationCommandOptionType.SubCommandGroup,
              option
            ) =>
          subCommandsByName(name) -> option.asInstanceOf[Option[Seq[ApplicationCommandInteractionDataOption[_]]]]
      }

      subcommandExecution match {
        case Some((subcommand, option)) =>
          subcommand.handleRaw(
            clientId,
            rawInteraction.copy(data = Some(data.copy(options = option))),
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
}

case class UserCommand[InteractionObj[_]] private (
    name: String,
    defaultPermission: Boolean,
    extra: Map[String, String],
    filter: DataInteractionTransformer[CommandInteraction, InteractionObj],
    handle: InteractionObj[(User, Option[InteractionRawGuildMember])] => InteractionResponse
) extends CreatedApplicationCommand {
  override def description: Option[String] = None

  override def makeCommandOptions: Seq[ApplicationCommandOption] = Nil

  override def commandType: ApplicationCommandType = ApplicationCommandType.User

  override def handleRaw(
      clientId: String,
      rawInteraction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): InteractionResponse = {
    val data = rawInteraction.data.get.asInstanceOf[ApplicationCommandInteractionData]
    if (data.`type` != ApplicationCommandType.User) {
      InteractionResponse.ChannelMessage(
        RawInteractionApplicationCommandCallbackData(content =
          Some(s"Encountered unexpected interaction type ${data.`type`} for slash command")
        ),
        () => OptFuture.unit
      )
    } else {
      val resolved = data.resolved.getOrElse(ApplicationCommandInteractionDataResolved.empty)
      val optArgs =
        data.targetId
          .map(UserId(_))
          .toRight("Did not received target_id for user command")
          .flatMap(userId => resolved.users.get(userId).toRight("Did not received resolved target user"))
          .map(user => user -> resolved.members.get(user.id))
      CreatedApplicationCommand.handleCommon(clientId, rawInteraction, cacheSnapshot, optArgs, filter, handle)
    }
  }
}

case class MessageCommand[InteractionObj[_]] private (
    name: String,
    defaultPermission: Boolean,
    extra: Map[String, String],
    filter: DataInteractionTransformer[CommandInteraction, InteractionObj],
    handle: InteractionObj[InteractionPartialMessage] => InteractionResponse
) extends CreatedApplicationCommand {
  override def description: Option[String] = None

  override def makeCommandOptions: Seq[ApplicationCommandOption] = Nil

  override def commandType: ApplicationCommandType = ApplicationCommandType.Message

  override def handleRaw(
      clientId: String,
      rawInteraction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): InteractionResponse = {
    val data = rawInteraction.data.get.asInstanceOf[ApplicationCommandInteractionData]
    if (data.`type` != ApplicationCommandType.Message) {
      InteractionResponse.ChannelMessage(
        RawInteractionApplicationCommandCallbackData(content =
          Some(s"Encountered unexpected interaction type ${data.`type`} for slash command")
        ),
        () => OptFuture.unit
      )
    } else {
      val resolved = data.resolved.getOrElse(ApplicationCommandInteractionDataResolved.empty)
      val optArgs =
        data.targetId
          .map(MessageId(_))
          .toRight("Did not received target_id for user command")
          .flatMap(messageId => resolved.messages.get(messageId).toRight("Did not received resolved target message"))
      CreatedApplicationCommand.handleCommon(clientId, rawInteraction, cacheSnapshot, optArgs, filter, handle)
    }
  }
}
