package ackcord.interactions

import java.util.Locale

import ackcord.data._
import ackcord.gateway.Context
import ackcord.interactions.data.ApplicationCommand
import ackcord.interactions.data.ApplicationCommand.ApplicationCommandOption.ApplicationCommandOptionType
import ackcord.interactions.data.ApplicationCommand.{ApplicationCommandOption, ApplicationCommandType}
import ackcord.interactions.data.Interaction.{
  ApplicationCommandData,
  ApplicationCommandInteractionDataOption,
  InteractionType,
  ResolvedData
}
import ackcord.interactions.data.InteractionResponse.MessageData
import cats.syntax.all._
import cats.{ApplicativeError, MonadError}
import io.circe.Json

/** An application command created in AckCord. */
sealed trait CreatedApplicationCommand[F[_]] {

  /** Name of the application command. */
  def name: String

  /** Localized names of the command for different locales. */
  def nameLocalizations: JsonOption[Map[String, String]]

  /** Description of the application command. */
  def description: Option[String]

  /** Localized description of the command for different locales. */
  def descriptionLocalizations: JsonOption[Map[String, String]]

  /** Extra info associated with the command. */
  def extra: Map[String, String]

  /**
    * Convert the parameters of this command into [[ApplicationCommandOption]]
    * s.
    */
  def makeCommandOptions: Seq[ApplicationCommandOption]

  /** The permissions a user must have to use the command by default. */
  def defaultMemberPermissions: Option[Permissions]

  /** If this command can be used in DMs. */
  def dmPermission: Boolean

  /** If this command is NSFW */
  def nsfw: Boolean

  /** Handle an interaction with the application command. */
  def handleRaw(
      rawInteraction: Interaction,
      context: Context
  )(implicit F: MonadError[F, Throwable]): F[HighInteractionResponse[F]]

  /** The application command type. */
  def commandType: ApplicationCommandType

  def toApplicationCommand(applicationId: ApplicationId, guildId: UndefOr[GuildId]): ApplicationCommand =
    ApplicationCommand
      .make20(
        id = Snowflake("0"),
        tpe = UndefOrSome(commandType),
        applicationId = applicationId,
        guildId = guildId,
        name = name,
        nameLocalized = UndefOrUndefined(),
        nameLocalizations = nameLocalizations,
        description = description.get,
        descriptionLocalized = UndefOrUndefined(),
        descriptionLocalizations = descriptionLocalizations,
        options = UndefOrSome(makeCommandOptions),
        defaultMemberPermissions = defaultMemberPermissions,
        dmPermission = UndefOrSome(dmPermission),
        nsfw = UndefOrSome(nsfw),
        version = RawSnowflake("0")
      )
      .objWithout(ApplicationCommand, "id")
      .objWithout(ApplicationCommand, "version")
}
object CreatedApplicationCommand {
  def verifyDataAndType[F[_]](interaction: Interaction, tpe: ApplicationCommandType, commandTypeStr: String)(
      implicit F: ApplicativeError[F, Throwable]
  ): F[ApplicationCommandData] =
    interaction.data match {
      case UndefOrSome(data: ApplicationCommandData) =>
        if (data.tpe != tpe) {
          F.raiseError(
            MissingFieldException.messageAndData(
              s"Encountered unexpected interaction type ${data.tpe} for $commandTypeStr",
              data
            )
          )
        } else data.pure
      case _ =>
        F.raiseError(MissingFieldException.messageAndData("Missing or wrong data type", interaction))
    }

  def handleCommon[F[_], A](
      interaction: Interaction,
      data: ApplicationCommandData,
      context: Context,
      optArgs: Either[String, A],
      handle: CommandInvocation[A] => HighInteractionResponse[F]
  )(
      implicit F: ApplicativeError[F, Throwable]
  ): HighInteractionResponse[F] =
    optArgs
      .map(args =>
        handle(
          CommandInvocation(interaction, data, args, context)
        )
      )
      .leftMap { error =>
        HighInteractionResponse.ChannelMessage(
          MessageData.make20(content = UndefOrSome(s"An error occurred: $error")),
          F.unit
        )
      }
      .merge

  def handleCommonFull[F[_], A](
      interaction: Interaction,
      context: Context,
      tpe: ApplicationCommandType,
      commandTypeStr: String,
      makeArgs: (ApplicationCommandData, ResolvedData) => Either[String, A],
      handle: CommandInvocation[A] => HighInteractionResponse[F]
  )(
      implicit F: ApplicativeError[F, Throwable]
  ): F[HighInteractionResponse[F]] = {
    verifyDataAndType(interaction, tpe, commandTypeStr).map { data =>
      val resolved = data.resolved.getOrElse(ResolvedData.makeRaw(Json.obj(), Map.empty))
      val optArgs  = makeArgs(data, resolved)

      handleCommon(interaction, data, context, optArgs, handle)
    }
  }
}

sealed trait SlashCommandOrGroup[F[_]] extends CreatedApplicationCommand[F] {

  /** Convert this command to an [[ApplicationCommandOption]]. */
  def toCommandOption: ApplicationCommandOption

  override def commandType: ApplicationCommandType = ApplicationCommandType.ChatInput
}

case class SlashCommand[F[_], A] private[interactions] (
    name: String,
    nameLocalizations: JsonOption[Map[String, String]],
    descriptionSome: String,
    descriptionLocalizations: JsonOption[Map[String, String]],
    defaultMemberPermissions: Option[Permissions],
    dmPermission: Boolean,
    nsfw: Boolean,
    extra: Map[String, String],
    paramList: Either[Unit =:= A, ParamList[A]],
    handle: CommandInvocation[A] => HighInteractionResponse[F]
) extends SlashCommandOrGroup[F] {

  override def description: Option[String] = Some(descriptionSome)

  override def makeCommandOptions: Seq[ApplicationCommandOption] = {
    val normalList                          = paramList.map(_.map(identity)).getOrElse(Nil)
    val (requiredParams, notRequiredParams) = normalList.partition(_.isRequired)
    (requiredParams ++ notRequiredParams).map(_.toCommandOption)
  }

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption.make20(
    tpe = ApplicationCommandOptionType.SUB_COMMAND,
    name = name,
    nameLocalizations = nameLocalizations,
    description = descriptionSome,
    descriptionLocalizations = descriptionLocalizations,
    required = UndefOrSome(false),
    options = UndefOrSome(makeCommandOptions)
  )

  override def handleRaw(
      interaction: Interaction,
      context: Context
  )(implicit F: MonadError[F, Throwable]): F[HighInteractionResponse[F]] =
    CreatedApplicationCommand
      .verifyDataAndType(
        interaction,
        ApplicationCommandType.ChatInput,
        "slash command"
      )
      .map { data =>
        val optionsMap = data.options
          .getOrElse(Nil)
          .map { dataOption =>
            dataOption.name.toLowerCase(Locale.ROOT) -> dataOption
          }
          .toMap

        interaction.tpe match {
          case InteractionType.APPLICATION_COMMAND =>
            val optArgs = paramList match {
              case Right(value) =>
                value.constructValues(
                  optionsMap,
                  data.resolved.getOrElse(ResolvedData.makeRaw(Json.obj(), Map.empty))
                )
              case Left(ev) => Right(ev(()))
            }

            CreatedApplicationCommand.handleCommon(interaction, data, context, optArgs, handle)

          case InteractionType.APPLICATION_COMMAND_AUTOCOMPLETE =>
            paramList match {
              case Right(paramList) =>
                HighInteractionResponse.Autocomplete(paramList.runAutocomplete(optionsMap))
              case Left(_) =>
                HighInteractionResponse.ChannelMessage(
                  MessageData
                    .make20(content = UndefOrSome(s"Got an autocomplete request for command with no parameters")),
                  F.unit
                )
            }
          case _ =>
            HighInteractionResponse.ChannelMessage(
              MessageData.make20(content =
                UndefOrSome(s"Encountered unexpected interaction type ${data.tpe} for slash command")
              ),
              F.unit
            )
        }
      }

}
case class SlashCommandGroup[F[_]] private[interactions] (
    name: String,
    nameLocalizations: JsonOption[Map[String, String]],
    descriptionSome: String,
    descriptionLocalizations: JsonOption[Map[String, String]],
    defaultMemberPermissions: Option[Permissions],
    dmPermission: Boolean,
    nsfw: Boolean,
    extra: Map[String, String],
    commands: Seq[SlashCommandOrGroup[F]]
) extends SlashCommandOrGroup[F] {
  override def description: Option[String] = Some(descriptionSome)

  override def makeCommandOptions: Seq[ApplicationCommandOption] = commands.map(_.toCommandOption)

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption.make20(
    tpe = ApplicationCommandOptionType.SUB_COMMAND_GROUP,
    name,
    nameLocalizations = nameLocalizations,
    description = descriptionSome,
    descriptionLocalizations = descriptionLocalizations,
    required = UndefOrSome(false),
    options = UndefOrSome(makeCommandOptions)
  )

  private lazy val subCommandsByName: Map[String, CreatedApplicationCommand[F]] = commands.map(c => c.name -> c).toMap

  override def handleRaw(
      interaction: Interaction,
      context: Context
  )(implicit F: MonadError[F, Throwable]): F[HighInteractionResponse[F]] = {
    CreatedApplicationCommand
      .verifyDataAndType(
        interaction,
        ApplicationCommandType.ChatInput,
        "slash command"
      )
      .flatMap { data =>
        val subcommandExecution = data.options.getOrElse(Nil).collectFirst {
          case option: ApplicationCommandInteractionDataOption
              if option.tpe == ApplicationCommandOptionType.SUB_COMMAND || option.tpe == ApplicationCommandOptionType.SUB_COMMAND_GROUP =>
            subCommandsByName(option.name) -> option.options
        }

        subcommandExecution match {
          case Some((subcommand, options)) =>
            subcommand.handleRaw(interaction.withData(UndefOrSome(data.withOptions(options))), context)
          case None =>
            F.pure(
              HighInteractionResponse.ChannelMessage(
                MessageData.make20(content = UndefOrSome("Encountered dead end for subcommands")),
                F.unit
              )
            )
        }
      }
  }
}

case class UserCommand[F[_]] private[interactions] (
    name: String,
    nameLocalizations: JsonOption[Map[String, String]],
    defaultMemberPermissions: Option[Permissions],
    nsfw: Boolean,
    extra: Map[String, String],
    handle: CommandInvocation[(User, Option[GuildMember.Partial])] => HighInteractionResponse[F]
) extends CreatedApplicationCommand[F] {
  override def description: Option[String] = None

  override def descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()

  override def dmPermission: Boolean = false

  override def makeCommandOptions: Seq[ApplicationCommandOption] = Nil

  override def commandType: ApplicationCommandType = ApplicationCommandType.User

  override def handleRaw(
      interaction: Interaction,
      context: Context
  )(implicit F: MonadError[F, Throwable]): F[HighInteractionResponse[F]] =
    CreatedApplicationCommand.handleCommonFull(
      interaction,
      context,
      ApplicationCommandType.User,
      "user command",
      (data, resolved) =>
        data.targetId
          .map(UserId(_))
          .toOption
          .toRight("Did not received target_id for user command")
          .flatMap(userId =>
            resolved.users.toEither
              .leftMap(_.message)
              .flatMap(_.get(userId).toRight("Did not received resolved target user"))
          )
          .map(user => user -> resolved.members.toOption.flatMap(_.get(user.id))),
      handle
    )
}

case class MessageCommand[F[_]] private[interactions] (
    name: String,
    nameLocalizations: JsonOption[Map[String, String]],
    defaultMemberPermissions: Option[Permissions],
    nsfw: Boolean,
    extra: Map[String, String],
    handle: CommandInvocation[Message] => HighInteractionResponse[F]
) extends CreatedApplicationCommand[F] {
  override def description: Option[String] = None

  override def descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()

  override def dmPermission: Boolean = false

  override def makeCommandOptions: Seq[ApplicationCommandOption] = Nil

  override def commandType: ApplicationCommandType = ApplicationCommandType.Message

  override def handleRaw(
      interaction: Interaction,
      context: Context
  )(implicit F: MonadError[F, Throwable]): F[HighInteractionResponse[F]] =
    CreatedApplicationCommand.handleCommonFull(
      interaction,
      context,
      ApplicationCommandType.Message,
      "message command",
      (data, resolved) =>
        data.targetId
          .map(MessageId(_))
          .toOption
          .toRight("Did not received target_id for user command")
          .flatMap(messageId =>
            resolved.messages.toEither
              .leftMap(_.message)
              .flatMap(_.get(messageId).toRight("Did not received resolved target message"))
          ),
      handle
    )
}
