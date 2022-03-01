package ackcord.interactions.commands

import ackcord.interactions.{CommandInteraction, DataInteractionTransformer}

/** A builder of some sort of command. */
abstract class CommandBuilder[Interaction[_], Args] {

  def defaultPermission: Boolean

  /**
    * A transformer to do base processing of the interaction before handling it.
    */
  def transformer: DataInteractionTransformer[CommandInteraction, Interaction]

  /** Extra info to associate with the command. */
  def extra: Map[String, String]

  /** Sets the transformer to process the interaction first. */
  def withTransformer[NewTo[_]](
      transformer: DataInteractionTransformer[CommandInteraction, NewTo]
  ): CommandBuilder[NewTo, Args]

  /** Use a new transformer on the current transformer. */
  def andThen[To2[_]](nextTransformer: DataInteractionTransformer[Interaction, To2]): CommandBuilder[To2, Args]

  /** Sets the extra info associated with the command. */
  def withExtra(extra: Map[String, String]): CommandBuilder[Interaction, Args]

  //Only effective top level
  def defaultPermission(permission: Boolean): CommandBuilder[Interaction, Args]
}
