package ackcord.interactions

import ackcord.data.{GuildMember, JsonOption, JsonUndefined, Permissions, User}

/** A builder for user commands. */
class UserCommandBuilder[F[_]](
    val extra: Map[String, String],
    val defaultMemberPermissions: Option[Permissions],
    val nsfw: Boolean
) extends CommandBuilder[F, (User, Option[GuildMember.Partial])] {
  override def withExtra(extra: Map[String, String]): UserCommandBuilder[F] =
    new UserCommandBuilder(extra, defaultMemberPermissions, nsfw)

  override def withDefaultMemberPermissions(defaultMemberPermissions: Option[Permissions]): UserCommandBuilder[F] =
    new UserCommandBuilder(extra, defaultMemberPermissions, nsfw)

  override def withNsfw(nsfw: Boolean): UserCommandBuilder[F] =
    new UserCommandBuilder(extra, defaultMemberPermissions, nsfw)

  /**
    * Create a new user command.
    * @param name
    *   The name of the command.
    * @param handler
    *   The handler for the command.
    */
  def handle(name: String, nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined())(
      handler: (Interaction, (User, Option[GuildMember.Partial])) => HighInteractionResponse[F]
  ): UserCommand[F] =
    UserCommand(name, nameLocalizations, defaultMemberPermissions, nsfw, extra, handler)
}
