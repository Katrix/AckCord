package ackcord.interactions.commands

import ackcord.data.{InteractionRawGuildMember, User}
import ackcord.interactions.{CommandInteraction, DataInteractionTransformer, InteractionResponse}

class UserCommandBuilder[Interaction[_]](
    val defaultPermission: Boolean,
    val transformer: DataInteractionTransformer[CommandInteraction, Interaction],
    val extra: Map[String, String]
) extends CommandBuilder[Interaction, (User, Option[InteractionRawGuildMember])] {
  override def withTransformer[NewTo[_]](
      transformer: DataInteractionTransformer[CommandInteraction, NewTo]
  ): UserCommandBuilder[NewTo] = new UserCommandBuilder(defaultPermission, transformer, extra)

  override def andThen[To2[_]](
      nextTransformer: DataInteractionTransformer[Interaction, To2]
  ): UserCommandBuilder[To2] = withTransformer(this.transformer.andThen(nextTransformer))

  override def withExtra(extra: Map[String, String]): UserCommandBuilder[Interaction] = new UserCommandBuilder(defaultPermission, transformer, extra)

  override def defaultPermission(permission: Boolean): UserCommandBuilder[Interaction] = new UserCommandBuilder(permission, transformer, extra)

  def handle(name: String)(handler: Interaction[(User, Option[InteractionRawGuildMember])] => InteractionResponse): UserCommand[Interaction] =
    UserCommand(name, defaultPermission, extra, transformer, handler)
}
