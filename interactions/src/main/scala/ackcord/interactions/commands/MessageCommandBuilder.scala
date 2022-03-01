package ackcord.interactions.commands

import ackcord.data.InteractionPartialMessage
import ackcord.interactions.{CommandInteraction, DataInteractionTransformer, InteractionResponse}

/**
  * A builder for message commands.
  */
class MessageCommandBuilder[Interaction[_]](
    val defaultPermission: Boolean,
    val transformer: DataInteractionTransformer[CommandInteraction, Interaction],
    val extra: Map[String, String]
) extends CommandBuilder[Interaction, InteractionPartialMessage] {
  override def withTransformer[NewTo[_]](
      transformer: DataInteractionTransformer[CommandInteraction, NewTo]
  ): MessageCommandBuilder[NewTo] = new MessageCommandBuilder(defaultPermission, transformer, extra)

  override def andThen[To2[_]](
      nextTransformer: DataInteractionTransformer[Interaction, To2]
  ): MessageCommandBuilder[To2] = withTransformer(this.transformer.andThen(nextTransformer))

  override def withExtra(extra: Map[String, String]): MessageCommandBuilder[Interaction] =
    new MessageCommandBuilder(defaultPermission, transformer, extra)

  override def defaultPermission(permission: Boolean): MessageCommandBuilder[Interaction] =
    new MessageCommandBuilder(permission, transformer, extra)

  /**
    * Create a new message command.
    * @param name
    *   The name of the command.
    * @param handler
    *   The handler for the command.
    */
  def handle(name: String)(
      handler: Interaction[InteractionPartialMessage] => InteractionResponse
  ): MessageCommand[Interaction] =
    MessageCommand(name, defaultPermission, extra, transformer, handler)
}
