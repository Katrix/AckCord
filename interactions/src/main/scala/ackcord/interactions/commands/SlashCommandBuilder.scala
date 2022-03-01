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
package ackcord.interactions.commands

import ackcord.interactions.{CommandInteraction, DataInteractionTransformer, InteractionResponse}
import akka.NotUsed

/** A slash command builder. */
class SlashCommandBuilder[Interaction[_], A](
    val defaultPermission: Boolean,
    val transformer: DataInteractionTransformer[CommandInteraction, Interaction],
    implParamList: Either[NotUsed =:= A, ParamList[A]],
    val extra: Map[String, String]
) extends CommandBuilder[Interaction, A] {

  override def withTransformer[NewTo[_]](
      transformer: DataInteractionTransformer[CommandInteraction, NewTo]
  ): SlashCommandBuilder[NewTo, A] =
    new SlashCommandBuilder(defaultPermission, transformer, implParamList, extra)

  override def andThen[To2[_]](
      nextTransformer: DataInteractionTransformer[Interaction, To2]
  ): SlashCommandBuilder[To2, A] =
    withTransformer(this.transformer.andThen(nextTransformer))

  /** The parameter list of this command. */
  def paramList: Option[ParamList[A]] = implParamList.toOption

  /** Sets the parameters to use for this command. */
  def withParams[NewA](paramList: ParamList[NewA]): SlashCommandBuilder[Interaction, NewA] = {
    require(paramList.foldRight(0)((_, acc) => acc + 1) <= 25, "Too many parameters. The maximum is 25")
    new SlashCommandBuilder(defaultPermission, transformer, Right(paramList), extra)
  }

  /** Removes the parameters of the command builder. */
  def withNoParams: SlashCommandBuilder[Interaction, NotUsed] =
    new SlashCommandBuilder(defaultPermission, transformer, Left(implicitly), extra)

  override def withExtra(extra: Map[String, String]): SlashCommandBuilder[Interaction, A] =
    new SlashCommandBuilder(defaultPermission, transformer, implParamList, extra)

  //Only effective top level
  override def defaultPermission(permission: Boolean): SlashCommandBuilder[Interaction, A] =
    new SlashCommandBuilder(permission, transformer, implParamList, extra)

  /**
    * Create a slash command group.
    * @param name
    *   Name of the group
    * @param description
    *   Description of the group.
    * @param subcommands
    *   Subcommands of the group.
    */
  def group(name: String, description: String)(subcommands: SlashCommandOrGroup*): SlashCommandGroup = {
    require(name.matches("""^[\w-]{1,32}$"""), "Invalid command name")
    SlashCommandGroup(name, Some(description), defaultPermission, extra, subcommands)
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
  def command(name: String, description: String)(
      handle: Interaction[A] => InteractionResponse
  ): SlashCommand[Interaction, A] = {
    require(name.matches("""^[\w-]{1,32}$"""), "Invalid command name")
    SlashCommand(name, Some(description), defaultPermission, extra, implParamList, transformer, handle)
  }

  /** Sets the name and description of the created slash command. */
  def named(name: String, description: String): NamedSlashCommandBuilder[Interaction, A] = {
    require(name.matches("""^[\w-]{1,32}$"""), "Invalid command name")
    new NamedSlashCommandBuilder(name, description, defaultPermission, transformer, implParamList, extra)
  }
}

class NamedSlashCommandBuilder[Interaction[_], A](
    val name: String,
    val description: String,
    override val defaultPermission: Boolean,
    transformer: DataInteractionTransformer[CommandInteraction, Interaction],
    implParamList: Either[NotUsed =:= A, ParamList[A]],
    extra: Map[String, String]
) extends SlashCommandBuilder(defaultPermission, transformer, implParamList, extra) {

  override def withTransformer[NewTo[_]](
      transformer: DataInteractionTransformer[CommandInteraction, NewTo]
  ): NamedSlashCommandBuilder[NewTo, A] =
    new NamedSlashCommandBuilder(name, description, defaultPermission, transformer, implParamList, extra)

  override def andThen[To2[_]](
      nextTransformer: DataInteractionTransformer[Interaction, To2]
  ): NamedSlashCommandBuilder[To2, A] =
    withTransformer(this.transformer.andThen(nextTransformer))

  override def withParams[NewA](paramList: ParamList[NewA]): NamedSlashCommandBuilder[Interaction, NewA] = {
    require(paramList.foldRight(0)((_, acc) => acc + 1) <= 25, "Too many parameters. The maximum is 25")
    new NamedSlashCommandBuilder(name, description, defaultPermission, transformer, Right(paramList), extra)
  }

  override def withNoParams: NamedSlashCommandBuilder[Interaction, NotUsed] =
    new NamedSlashCommandBuilder(name, description, defaultPermission, transformer, Left(implicitly), extra)

  override def withExtra(extra: Map[String, String]): NamedSlashCommandBuilder[Interaction, A] =
    new NamedSlashCommandBuilder(name, description, defaultPermission, transformer, implParamList, extra)

  //Only effective top level
  override def defaultPermission(permission: Boolean): NamedSlashCommandBuilder[Interaction, A] =
    new NamedSlashCommandBuilder(name, description, permission, transformer, implParamList, extra)

  /**
    * Create a slash command.
    * @param handler
    *   A handler for the slash command.
    */
  def handle(handler: Interaction[A] => InteractionResponse): SlashCommand[Interaction, A] =
    SlashCommand(name, Some(description), defaultPermission, extra, implParamList, transformer, handler)
}
