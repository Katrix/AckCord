package ackcord.interactions

import ackcord.data.{JsonOption, JsonUndefined, Permissions}
import ackcord.interactions.data.Interaction

/** A slash command builder. */
class SlashCommandBuilder[F[_], A](
    implParamList: Either[Unit =:= A, ParamList[A]],
    val extra: Map[String, String],
    val defaultMemberPermissions: Option[Permissions],
    val dmPermissions: Boolean,
    val nsfw: Boolean
) extends CommandBuilder[F, A] {

  /** The parameter list of this command. */
  def paramList: Option[ParamList[A]] = implParamList.toOption

  /** Sets the parameters to use for this command. */
  def withParams[NewA](paramList: ParamList[NewA]): SlashCommandBuilder[F, NewA] = {
    require(paramList.foldRight(0)((_, acc) => acc + 1) <= 25, "Too many parameters. The maximum is 25")
    new SlashCommandBuilder(Right(paramList), extra, defaultMemberPermissions, dmPermissions, nsfw)
  }

  /** Removes the parameters of the command builder. */
  def withNoParams: SlashCommandBuilder[F, Unit] =
    new SlashCommandBuilder(Left(implicitly), extra, defaultMemberPermissions, dmPermissions, nsfw)

  override def withExtra(extra: Map[String, String]): SlashCommandBuilder[F, A] =
    new SlashCommandBuilder(implParamList, extra, defaultMemberPermissions, dmPermissions, nsfw)

  override def withDefaultMemberPermissions(
      defaultMemberPermissions: Option[Permissions]
  ): SlashCommandBuilder[F, A] =
    new SlashCommandBuilder(implParamList, extra, defaultMemberPermissions, dmPermissions, nsfw)

  def withDmPermissions(dmPermissions: Boolean): SlashCommandBuilder[F, A] =
    new SlashCommandBuilder(implParamList, extra, defaultMemberPermissions, dmPermissions, nsfw)

  override def withNsfw(nsfw: Boolean): SlashCommandBuilder[F, A] =
    new SlashCommandBuilder(implParamList, extra, defaultMemberPermissions, dmPermissions, nsfw)

  /**
    * Create a slash command group.
    * @param name
    *   Name of the group
    * @param description
    *   Description of the group.
    * @param subcommands
    *   Subcommands of the group.
    */
  def group(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()
  )(subcommands: SlashCommandOrGroup[F]*): SlashCommandGroup[F] = {
    require(name.matches("""^[\w-]{1,32}$"""), "Invalid command name")
    SlashCommandGroup(
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      defaultMemberPermissions,
      dmPermissions,
      nsfw,
      extra,
      subcommands
    )
  }

  /**
    * Create a slash command.
    * @param name
    *   Name of the slash command.
    * @param description
    *   Description of the slash command.
    * @param handle
    *   A handler for the slash command.
    */
  def command(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()
  )(
      handle: (Interaction, A) => HighInteractionResponse[F]
  ): SlashCommand[F, A] = {
    require(name.matches("""^[\w-]{1,32}$"""), "Invalid command name")
    SlashCommand(
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      defaultMemberPermissions,
      dmPermissions,
      nsfw,
      extra,
      implParamList,
      handle
    )
  }

  /** Sets the name and description of the created slash command. */
  def named(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()
  ): NamedSlashCommandBuilder[F, A] = {
    require(name.matches("""^[\w-]{1,32}$"""), "Invalid command name")
    new NamedSlashCommandBuilder(
      name,
      description,
      nameLocalizations,
      descriptionLocalizations,
      implParamList,
      extra,
      defaultMemberPermissions,
      dmPermissions,
      nsfw
    )
  }
}

class NamedSlashCommandBuilder[F[_], A](
    val name: String,
    val description: String,
    nameLocalizations: JsonOption[Map[String, String]],
    descriptionLocalizations: JsonOption[Map[String, String]],
    implParamList: Either[Unit =:= A, ParamList[A]],
    extra: Map[String, String],
    defaultMemberPermissions: Option[Permissions],
    dmPermissions: Boolean,
    nsfw: Boolean
) extends SlashCommandBuilder(implParamList, extra, defaultMemberPermissions, dmPermissions, nsfw) {

  override def withParams[NewA](paramList: ParamList[NewA]): NamedSlashCommandBuilder[F, NewA] = {
    require(paramList.foldRight(0)((_, acc) => acc + 1) <= 25, "Too many parameters. The maximum is 25")
    new NamedSlashCommandBuilder(
      name,
      description,
      nameLocalizations,
      descriptionLocalizations,
      Right(paramList),
      extra,
      defaultMemberPermissions,
      dmPermissions,
      nsfw
    )
  }

  override def withNoParams: NamedSlashCommandBuilder[F, Unit] =
    new NamedSlashCommandBuilder(
      name,
      description,
      nameLocalizations,
      descriptionLocalizations,
      Left(implicitly),
      extra,
      defaultMemberPermissions,
      dmPermissions,
      nsfw
    )

  override def withExtra(extra: Map[String, String]): NamedSlashCommandBuilder[F, A] =
    new NamedSlashCommandBuilder(
      name,
      description,
      nameLocalizations,
      descriptionLocalizations,
      implParamList,
      extra,
      defaultMemberPermissions,
      dmPermissions,
      nsfw
    )

  override def withDefaultMemberPermissions(
      defaultMemberPermissions: Option[Permissions]
  ): NamedSlashCommandBuilder[F, A] =
    new NamedSlashCommandBuilder(
      name,
      description,
      nameLocalizations,
      descriptionLocalizations,
      implParamList,
      extra,
      defaultMemberPermissions,
      dmPermissions,
      nsfw
    )

  override def withDmPermissions(dmPermissions: Boolean): NamedSlashCommandBuilder[F, A] =
    new NamedSlashCommandBuilder(
      name,
      description,
      nameLocalizations,
      descriptionLocalizations,
      implParamList,
      extra,
      defaultMemberPermissions,
      dmPermissions,
      nsfw
    )

  override def withNsfw(nsfw: Boolean): NamedSlashCommandBuilder[F, A] =
    new NamedSlashCommandBuilder(
      name,
      description,
      nameLocalizations,
      descriptionLocalizations,
      implParamList,
      extra,
      defaultMemberPermissions,
      dmPermissions,
      nsfw
    )

  /**
    * Create a slash command.
    * @param handler
    *   A handler for the slash command.
    */
  def handle(handler: (Interaction, A) => HighInteractionResponse[F]): SlashCommand[F, A] =
    SlashCommand(
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      defaultMemberPermissions,
      dmPermissions,
      nsfw,
      extra,
      implParamList,
      handler
    )
}
