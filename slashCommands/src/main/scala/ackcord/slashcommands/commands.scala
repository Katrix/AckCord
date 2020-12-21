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

import java.util.Locale

import ackcord.data._
import ackcord.slashcommands.raw._
import ackcord.{CacheSnapshot, OptFuture, slashcommands}
import akka.NotUsed
import cats.arrow.FunctionK
import cats.syntax.either._
import cats.~>

sealed trait CommandOrGroup {
  def name: String
  def description: String

  def toCommandOption: ApplicationCommandOption
  def makeCommandOptions: Seq[ApplicationCommandOption]

  def handleRaw(clientId: String, rawInteraction: RawInteraction, cacheSnapshot: Option[CacheSnapshot]): CommandResponse
}
case class Command[Interaction[_], A] private (
    name: String,
    description: String,
    paramList: Either[NotUsed =:= A, ParamList[A]],
    filter: CommandTransformer[CommandInteraction, Interaction],
    handle: Interaction[A] => CommandResponse
) extends CommandOrGroup {

  override def makeCommandOptions: Seq[ApplicationCommandOption] = {
    val normalList                        = paramList.map(_.map(identity)).getOrElse(Nil)
    val (defaultParams, nonDefaultParams) = normalList.partition(_.default)
    if (defaultParams.length > 1) {
      throw new IllegalArgumentException("Can only have one default parameter")
    }

    val (requiredParams, notRequiredParams) = nonDefaultParams.partition(_.isRequired)

    (defaultParams ++ requiredParams ++ notRequiredParams).map(_.toCommandOption)
  }

  override def toCommandOption: ApplicationCommandOption = slashcommands.raw.ApplicationCommandOption(
    ApplicationCommandOptionType.SubCommand,
    name,
    description,
    default = Some(false),
    required = Some(false),
    Some(Nil),
    Some(makeCommandOptions)
  )

  override def handleRaw(
      clientId: String,
      rawInteraction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): CommandResponse = {
    val data = rawInteraction.data.get

    val optionsMap = data.options
      .getOrElse(Nil)
      .collect {
        case ApplicationCommandInteractionDataOption.ApplicationCommandInteractionDataOptionWithValue(
              name,
              value
            ) =>
          name.toLowerCase(Locale.ROOT) -> value
      }
      .toMap

    val optArgs = paramList match {
      case Right(value) => value.constructValues(optionsMap)
      case Left(ev)     => Right(ev(NotUsed))
    }

    optArgs
      .leftMap(Some(_))
      .flatMap { args =>
        val invocationInfo = CommandInvocationInfo(
          data.id,
          rawInteraction.id,
          rawInteraction.guildId,
          rawInteraction.channelId,
          rawInteraction.member.user,
          rawInteraction.member.toGuildMember(rawInteraction.guildId),
          rawInteraction.token,
          args,
          clientId
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
          CommandResponse.ChannelMessage(
            InteractionApplicationCommandCallbackData(content = s"An error occurred: $error"),
            withSource = false,
            () => OptFuture.unit
          )
        case None =>
          CommandResponse.ChannelMessage(
            InteractionApplicationCommandCallbackData(content = "An error occurred"),
            withSource = false,
            () => OptFuture.unit
          )
      }
      .merge
  }
}
case class CommandGroup private (
    name: String,
    description: String,
    commands: Seq[CommandOrGroup]
) extends CommandOrGroup {
  override def makeCommandOptions: Seq[ApplicationCommandOption] = commands.map(_.toCommandOption)

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption(
    ApplicationCommandOptionType.SubCommandGroup,
    name,
    description,
    default = Some(false),
    required = Some(false),
    Some(Nil),
    Some(makeCommandOptions)
  )

  private lazy val subCommandsByName: Map[String, CommandOrGroup] = commands.map(c => c.name -> c).toMap

  override def handleRaw(
      clientId: String,
      rawInteraction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): CommandResponse = {
    val data = rawInteraction.data.get

    val subcommandExecution = data.options.getOrElse(Nil).collectFirst {
      case ApplicationCommandInteractionDataOption.ApplicationCommandInteractionDataOptionWithOptions(name, options)
          if subCommandsByName.contains(name) =>
        subCommandsByName(name) -> options
    }

    subcommandExecution match {
      case Some((subcommand, options)) =>
        subcommand.handleRaw(
          clientId,
          rawInteraction.copy(data = Some(data.copy(options = Some(options)))),
          cacheSnapshot
        )
      case None =>
        CommandResponse.ChannelMessage(
          InteractionApplicationCommandCallbackData(content = "Encountered dead end for subcommands"),
          withSource = false,
          () => OptFuture.unit
        )
    }

  }
}

case class CommandInvocationInfo[A](
    commandId: CommandId,
    id: InteractionId,
    guildId: GuildId,
    channelId: TextChannelId,
    user: User,
    member: GuildMember,
    token: String,
    args: A,
    clientId: String
) {
  def webhookId: SnowflakeType[Webhook] = SnowflakeType(clientId)
}

trait CommandInteraction[A] {
  def commandInvocationInfo: CommandInvocationInfo[A]

  def commandId: CommandId              = commandInvocationInfo.commandId
  def id: InteractionId                 = commandInvocationInfo.id
  def guildId: GuildId                  = commandInvocationInfo.guildId
  def channelId: TextChannelId          = commandInvocationInfo.channelId
  def user: User                        = commandInvocationInfo.user
  def member: GuildMember               = commandInvocationInfo.member
  def token: String                     = commandInvocationInfo.token
  def args: A                           = commandInvocationInfo.args
  def webhookId: SnowflakeType[Webhook] = commandInvocationInfo.webhookId

  def optCache: Option[CacheSnapshot]
}

trait CacheCommandInteraction[A] extends CommandInteraction[A] {
  def cache: CacheSnapshot
  override def optCache: Option[CacheSnapshot] = Some(cache)
}
case class BaseCacheCommandInteraction[A](commandInvocationInfo: CommandInvocationInfo[A], cache: CacheSnapshot)
    extends CacheCommandInteraction[A]

trait ResolvedCommandInteraction[A] extends CacheCommandInteraction[A] {
  def textChannel: TextChannel
  def guild: Guild
}
case class BaseResolvedCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A],
    textChannel: TextChannel,
    guild: Guild,
    cache: CacheSnapshot
) extends ResolvedCommandInteraction[A]

trait GuildCommandInteraction[A] extends ResolvedCommandInteraction[A] {
  def textChannel: TextGuildChannel
}
case class BaseGuildCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A],
    textChannel: TextGuildChannel,
    guild: Guild,
    cache: CacheSnapshot
) extends GuildCommandInteraction[A]

trait VoiceChannelCommandInteraction[A] extends GuildCommandInteraction[A] {
  def voiceChannel: VoiceGuildChannel
}
case class BaseVoiceChannelCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A],
    textChannel: TextGuildChannel,
    guild: Guild,
    voiceChannel: VoiceGuildChannel,
    cache: CacheSnapshot
) extends VoiceChannelCommandInteraction[A]

case class StatelessCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A]
) extends CommandInteraction[A] {
  override def optCache: Option[CacheSnapshot] = None
}

trait CommandTransformer[From[_], To[_]] { outer =>
  def filter[A](from: From[A]): Either[Option[String], To[A]]

  def andThen[To2[_]](transformer: CommandTransformer[To, To2]): CommandTransformer[From, To2] =
    new CommandTransformer[From, To2] {
      override def filter[A](from: From[A]): Either[Option[String], To2[A]] =
        outer.filter(from).flatMap(transformer.filter)
    }

  @inline final def compose[From2[_]](transformer: CommandTransformer[From2, From]): CommandTransformer[From2, To] =
    transformer.andThen(this)
}
object CommandTransformer {

  /**
    * A command transformer which resolves most ids from the cache.
    */
  def resolved[I[A] <: CacheCommandInteraction[A], O[_]](
      create: (TextChannel, Guild) => I ~> O
  ): CommandTransformer[I, O] = new CommandTransformer[I, O] {
    override def filter[A](from: I[A]): Either[Option[String], O[A]] = {
      implicit val c: CacheSnapshot = from.cache
      val res = for {
        channel <- from.channelId.asChannelId[TextGuildChannel].resolve(from.guildId).orElse(from.channelId.resolve)
        guild   <- from.guildId.resolve
      } yield create(channel, guild)(from)

      res.toRight(Some("Missing cached values while executing function"))
    }
  }

  /**
    * A command function that only allows commands sent from a guild.
    */
  def onlyInGuild[I[A] <: ResolvedCommandInteraction[A], O[_]](
      create: TextGuildChannel => I ~> O
  ): CommandTransformer[I, O] =
    new CommandTransformer[I, O] {

      override def filter[A](from: I[A]): Either[Option[String], O[A]] =
        from.textChannel match {
          case t: TextGuildChannel => Right(create(t)(from))
          case _                   => Left(Some(s"This command can only be used in a guild"))
        }
    }

  def inVoiceChannel[I[A] <: GuildCommandInteraction[A], O[_]](
      create: VoiceGuildChannel => I ~> O
  ): CommandTransformer[I, O] = new CommandTransformer[I, O] {
    type Result[A] = Either[Option[String], O[A]]

    override def filter[A](from: I[A]): Either[Option[String], O[A]] = {
      implicit val c: CacheSnapshot = from.cache
      from.guild.voiceStates
        .get(from.user.id)
        .flatMap(_.channelId)
        .flatMap(_.resolve(from.guildId))
        .toRight(Some(s"This command can only be used while in a voice channel"))
        .map(vCh => create(vCh)(from))
    }
  }

  /**
    * A command function that requires that those who use this command need
    * some set of permissions.
    */
  def needPermission[M[A] <: GuildCommandInteraction[A]](
      neededPermission: Permission
  ): CommandTransformer[M, M] = new CommandTransformer[M, M] {

    override def filter[A](from: M[A]): Either[Option[String], M[A]] =
      Either.cond(
        from.member.channelPermissionsId(from.guild, from.textChannel.id).hasPermissions(neededPermission),
        from,
        Some("You don't have permission to use this command")
      )
  }
}

class CommandFunction[From[_], To[_]](f: FunctionK[From, To]) extends CommandTransformer[From, To] {
  override def filter[A](from: From[A]): Either[Option[String], To[A]] = Right(f(from))
}

sealed trait CommandResponse {
  def toInteractionResponse: InteractionResponse = this match {
    case CommandResponse.Pong                  => InteractionResponse(InteractionResponseType.Pong, None)
    case CommandResponse.Acknowledge(false, _) => InteractionResponse(InteractionResponseType.Acknowledge, None)
    case CommandResponse.Acknowledge(true, _)  => InteractionResponse(InteractionResponseType.ACKWithSource, None)
    case CommandResponse.ChannelMessage(message, false, _) =>
      slashcommands.raw.InteractionResponse(InteractionResponseType.ChannelMessage, Some(message))
    case CommandResponse.ChannelMessage(message, true, _) =>
      slashcommands.raw.InteractionResponse(InteractionResponseType.ChannelMessageWithSource, Some(message))
  }
}
object CommandResponse {
  sealed trait AsyncMessageable extends CommandResponse {
    def doAsync(action: AsyncMessageToken => OptFuture[_])(implicit interaction: CommandInteraction[_]): CommandResponse
  }

  case object Pong                                                           extends CommandResponse
  case class Acknowledge(withSource: Boolean, andThenDo: () => OptFuture[_]) extends CommandResponse
  case class ChannelMessage(
      message: InteractionApplicationCommandCallbackData,
      withSource: Boolean,
      andThenDo: () => OptFuture[_]
  ) extends CommandResponse
      with AsyncMessageable {

    def doAsync(action: AsyncMessageToken => OptFuture[_])(
        implicit interaction: CommandInteraction[_]
    ): ChannelMessage = copy(andThenDo = () => action(AsyncToken.fromInteractionWithMessage(interaction)))
  }
}
