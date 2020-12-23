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

import akka.NotUsed

class CommandBuilder[Interaction[_], A](
    val transformer: CommandTransformer[CommandInteraction, Interaction],
    implParamList: Either[NotUsed =:= A, ParamList[A]],
    extra: Map[String, String]
) {

  def withTransformer[NewTo[_]](transformer: CommandTransformer[CommandInteraction, NewTo]): CommandBuilder[NewTo, A] =
    new CommandBuilder(transformer, implParamList, extra)

  def andThen[To2[_]](nextTransformer: CommandTransformer[Interaction, To2]): CommandBuilder[To2, A] =
    withTransformer(this.transformer.andThen(nextTransformer))

  def paramList: Option[ParamList[A]] = implParamList.toOption

  def withParams[NewA](paramList: ParamList[NewA]): CommandBuilder[Interaction, NewA] = {
    require(paramList.foldRight(0)((_, acc) => acc + 1) <= 10, "Too many parameters. The maximum is 10")
    new CommandBuilder(transformer, Right(paramList), extra)
  }

  def withNoParams: CommandBuilder[Interaction, NotUsed] =
    new CommandBuilder(transformer, Left(implicitly), extra)

  def withExtra(extra: Map[String, String]): CommandBuilder[Interaction, A] =
    new CommandBuilder(transformer, implParamList, extra)

  def group(name: String, description: String)(subcommands: CommandOrGroup*): CommandGroup = {
    require(subcommands.length <= 10, "Too many subcommands or groups. The maximum is 10")
    CommandGroup(name, description, extra, subcommands)
  }

  def command(name: String, description: String)(handle: Interaction[A] => CommandResponse): Command[Interaction, A] =
    Command(name, description, extra, implParamList, transformer, handle)

  def named(name: String, description: String): NamedCommandBuilder[Interaction, A] =
    new NamedCommandBuilder(name, description, transformer, implParamList, extra)
}

class NamedCommandBuilder[Interaction[_], A](
    val name: String,
    val description: String,
    transformer: CommandTransformer[CommandInteraction, Interaction],
    implParamList: Either[NotUsed =:= A, ParamList[A]],
    extra: Map[String, String]
) extends CommandBuilder(transformer, implParamList, extra) {

  override def withTransformer[NewTo[_]](
      transformer: CommandTransformer[CommandInteraction, NewTo]
  ): NamedCommandBuilder[NewTo, A] =
    new NamedCommandBuilder(name, description, transformer, implParamList, extra)

  override def andThen[To2[_]](nextTransformer: CommandTransformer[Interaction, To2]): NamedCommandBuilder[To2, A] =
    withTransformer(this.transformer.andThen(nextTransformer))

  override def withParams[NewA](paramList: ParamList[NewA]): NamedCommandBuilder[Interaction, NewA] = {
    require(paramList.foldRight(0)((_, acc) => acc + 1) <= 10, "Too many parameters. The maximum is 10")
    new NamedCommandBuilder(name, description, transformer, Right(paramList), extra)
  }

  override def withNoParams: NamedCommandBuilder[Interaction, NotUsed] =
    new NamedCommandBuilder(name, description, transformer, Left(implicitly), extra)

  override def withExtra(extra: Map[String, String]): NamedCommandBuilder[Interaction, A] =
    new NamedCommandBuilder(name, description, transformer, implParamList, extra)

  def handle(handler: Interaction[A] => CommandResponse): Command[Interaction, A] =
    Command(name, description, extra, implParamList, transformer, handler)
}
