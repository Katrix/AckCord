package ackcord.interactions.commands

import ackcord.interactions.{CommandInteraction, DataInteractionTransformer}

abstract class CommandBuilder[Interaction[_], Args] {

  def defaultPermission: Boolean
  def transformer: DataInteractionTransformer[CommandInteraction, Interaction]
  def extra: Map[String, String]

  def withTransformer[NewTo[_]](transformer: DataInteractionTransformer[CommandInteraction, NewTo]): CommandBuilder[NewTo, Args]

  def andThen[To2[_]](nextTransformer: DataInteractionTransformer[Interaction, To2]): CommandBuilder[To2, Args]

  def withExtra(extra: Map[String, String]): CommandBuilder[Interaction, Args]

  //Only effective top level
  def defaultPermission(permission: Boolean): CommandBuilder[Interaction, Args]
}
