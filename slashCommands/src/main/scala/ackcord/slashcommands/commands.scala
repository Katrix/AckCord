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

import ackcord.data._
import ackcord.data.raw.RawGuildMember
import ackcord.slashcommands.raw._
import ackcord.{CacheSnapshot, OptFuture, slashcommands}
import akka.NotUsed
import cats.arrow.FunctionK
import cats.syntax.either._
import cats.~>

import java.util.Locale

sealed trait CommandOrGroup {
  def name: String
  def description: String
  def extra: Map[String, String]

  def toCommandOption: ApplicationCommandOption
  def makeCommandOptions: Seq[ApplicationCommandOption]

  def handleRaw(clientId: String, rawInteraction: Interaction, cacheSnapshot: Option[CacheSnapshot]): CommandResponse
}
case class Command[InteractionObj[_], A] private (
    name: String,
    description: String,
    extra: Map[String, String],
    paramList: Either[NotUsed =:= A, ParamList[A]],
    filter: CommandTransformer[CommandInteraction, InteractionObj],
    handle: InteractionObj[A] => CommandResponse
) extends CommandOrGroup {

  override def makeCommandOptions: Seq[ApplicationCommandOption] = {
    val normalList                          = paramList.map(_.map(identity)).getOrElse(Nil)
    val (requiredParams, notRequiredParams) = normalList.partition(_.isRequired)
    (requiredParams ++ notRequiredParams).map(_.toCommandOption)
  }

  override def toCommandOption: ApplicationCommandOption = slashcommands.raw.ApplicationCommandOption(
    ApplicationCommandOptionType.SubCommand,
    name,
    description,
    required = Some(false),
    Some(Nil),
    Some(makeCommandOptions)
  )

  override def handleRaw(
      clientId: String,
      interaction: Interaction,
      cacheSnapshot: Option[CacheSnapshot]
  ): CommandResponse = {
    val data = interaction.data.get

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
          interaction.id,
          interaction.guildId,
          interaction.channelId,
          interaction.member.map(_.user).orElse(interaction.user).get,
          interaction.member,
          interaction.memberPermission,
          interaction.token,
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
            InteractionApplicationCommandCallbackData(content = Some(s"An error occurred: $error")),
            () => OptFuture.unit
          )
        case None =>
          CommandResponse.ChannelMessage(
            InteractionApplicationCommandCallbackData(content = Some("An error occurred")),
            () => OptFuture.unit
          )
      }
      .merge
  }
}
case class CommandGroup private (
    name: String,
    description: String,
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
      rawInteraction: Interaction,
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
          InteractionApplicationCommandCallbackData(content = Some("Encountered dead end for subcommands")),
          () => OptFuture.unit
        )
    }

  }
}

case class CommandInvocationInfo[A](
    commandId: CommandId,
    id: InteractionId,
    guildId: Option[GuildId],
    channelId: TextChannelId,
    user: User,
    member: Option[RawGuildMember],
    memberPermissions: Option[Permission],
    token: String,
    args: A,
    clientId: String
) {
  def webhookId: SnowflakeType[Webhook] = SnowflakeType(clientId)
}

trait CommandInteraction[A] {
  def commandInvocationInfo: CommandInvocationInfo[A]

  def commandId: CommandId                     = commandInvocationInfo.commandId
  def id: InteractionId                        = commandInvocationInfo.id
  def optGuildId: Option[GuildId]              = commandInvocationInfo.guildId
  def channelId: TextChannelId                 = commandInvocationInfo.channelId
  def user: User                               = commandInvocationInfo.user
  def optMember: Option[GuildMember]           = commandInvocationInfo.member.map(_.toGuildMember(optGuildId.get))
  def optMemberPermissions: Option[Permission] = commandInvocationInfo.memberPermissions
  def token: String                            = commandInvocationInfo.token
  def args: A                                  = commandInvocationInfo.args
  def webhookId: SnowflakeType[Webhook]        = commandInvocationInfo.webhookId

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
  def optGuild: Option[Guild]
}
case class BaseResolvedCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A],
    textChannel: TextChannel,
    optGuild: Option[Guild],
    cache: CacheSnapshot
) extends ResolvedCommandInteraction[A]

trait GuildCommandInteraction[A] extends ResolvedCommandInteraction[A] {
  def guild: Guild
  def member: GuildMember
  def memberPermissions: Permission

  def textChannel: TextGuildChannel

  override def optGuild: Option[Guild] = Some(guild)
}
case class BaseGuildCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A],
    textChannel: TextGuildChannel,
    guild: Guild,
    member: GuildMember,
    memberPermissions: Permission,
    cache: CacheSnapshot
) extends GuildCommandInteraction[A]

trait VoiceChannelCommandInteraction[A] extends GuildCommandInteraction[A] {
  def voiceChannel: VoiceGuildChannel
}
case class BaseVoiceChannelCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A],
    textChannel: TextGuildChannel,
    guild: Guild,
    member: GuildMember,
    memberPermissions: Permission,
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
      create: (TextChannel, Option[Guild]) => I ~> O
  ): CommandTransformer[I, O] = new CommandTransformer[I, O] {
    override def filter[A](from: I[A]): Either[Option[String], O[A]] = {
      implicit val c: CacheSnapshot = from.cache

      val res = from.optGuildId match {
        case Some(guildId) =>
          for {
            channel <- from.channelId.asChannelId[TextGuildChannel].resolve(guildId)
            guild   <- guildId.resolve
          } yield create(channel, Some(guild))(from)

        case None =>
          from.channelId.resolve.map(create(_, None)(from))
      }

      res.toRight(Some("Missing cached values while executing function"))
    }
  }

  /**
    * A command function that only allows commands sent from a guild.
    */
  def onlyInGuild[I[A] <: ResolvedCommandInteraction[A], O[_]](
      create: (Guild, GuildMember, Permission, TextGuildChannel) => I ~> O
  ): CommandTransformer[I, O] =
    new CommandTransformer[I, O] {

      override def filter[A](from: I[A]): Either[Option[String], O[A]] = {
        from.optGuild match {
          case Some(guild) =>
            val member            = from.optMember.get
            val memberPermissions = from.optMemberPermissions.get
            val textGuildChannel  = from.asInstanceOf[TextGuildChannel]

            Right(create(guild, member, memberPermissions, textGuildChannel)(from))
          case None =>
            Left(Some(s"This command can only be used in a guild"))
        }
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
        .flatMap(_.resolve(from.guild.id))
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
        from.memberPermissions.hasPermissions(neededPermission),
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
    case CommandResponse.Pong => InteractionResponse(InteractionResponseType.Pong, None)
    case CommandResponse.Acknowledge(_) =>
      InteractionResponse(InteractionResponseType.DeferredChannelMessageWithSource, None)
    case CommandResponse.ChannelMessage(message, _) =>
      slashcommands.raw.InteractionResponse(InteractionResponseType.ChannelMessageWithSource, Some(message))
  }
}
object CommandResponse {
  sealed trait AsyncMessageable extends CommandResponse {
    def doAsync(action: AsyncMessageToken => OptFuture[_])(implicit interaction: CommandInteraction[_]): CommandResponse
  }

  case object Pong                                      extends CommandResponse
  case class Acknowledge(andThenDo: () => OptFuture[_]) extends CommandResponse
  case class ChannelMessage(
      message: InteractionApplicationCommandCallbackData,
      andThenDo: () => OptFuture[_]
  ) extends CommandResponse
      with AsyncMessageable {

    def doAsync(action: AsyncMessageToken => OptFuture[_])(
        implicit interaction: CommandInteraction[_]
    ): ChannelMessage = copy(andThenDo = () => action(AsyncToken.fromInteractionWithMessage(interaction)))
  }
}
