package ackcord.interactions

import ackcord.data.{JsonOption, JsonUndefined, Message, Permissions}
import ackcord.interactions.data.Interaction

/** A builder for message commands. */
class MessageCommandBuilder[F[_]](
    val extra: Map[String, String],
    val defaultMemberPermissions: Option[Permissions],
    val nsfw: Boolean
) extends CommandBuilder[F, Message] {

  override def withExtra(extra: Map[String, String]): MessageCommandBuilder[F] =
    new MessageCommandBuilder(extra, defaultMemberPermissions, nsfw)

  override def withDefaultMemberPermissions(defaultMemberPermissions: Option[Permissions]): MessageCommandBuilder[F] =
    new MessageCommandBuilder(extra, defaultMemberPermissions, nsfw)

  override def withNsfw(nsfw: Boolean): MessageCommandBuilder[F] =
    new MessageCommandBuilder(extra, defaultMemberPermissions, nsfw)

  /**
    * Create a new message command.
    * @param name
    *   The name of the command.
    * @param handler
    *   The handler for the command.
    */
  def handle(name: String, nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined())(
      handler: (Interaction, Message) => HighInteractionResponse[F]
  ): MessageCommand[F] =
    MessageCommand(name, nameLocalizations, defaultMemberPermissions, nsfw, extra, handler)
}
