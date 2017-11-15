/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord.commands

import java.util.Locale

import akka.actor.Props
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.util.MessageParser

/**
  * Represents some group of commands.
  * The description has not effect on equality between to categories.
  * @param prefix The prefix for this category. This must be lowercase.
  * @param description The description for this category.
  */
case class CmdCategory(prefix: String, description: String) {
  require(prefix.toLowerCase(Locale.ROOT) == prefix, "The prefix of a command category must be lowercase")
  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case null             => false
      case cat: CmdCategory => prefix == cat.prefix
      case _                => false
    }
  }
}

/**
  * Represents non essential information about a command intended to be
  * displayed to an end user.
  * @param name The display name of a command.
  * @param description The description of what a command does.
  * @param usage How to use the command. Does not include the name or prefix.
  */
case class CmdDescription(name: String, description: String, usage: String = "")

trait CmdFactory {
  def category:                   CmdCategory
  def aliases:                    Seq[String]
  def props(client: ClientActor): Props
  def description:                Option[CmdDescription]

  def lowercaseAliases: Seq[String] = aliases.map(_.toLowerCase(Locale.ROOT))
}

case class UnparsedCmdFactory(
    category: CmdCategory,
    aliases: Seq[String],
    props: Props,
    filters: Seq[CmdFilter] = Seq.empty,
    description: Option[CmdDescription] = None,
) extends CmdFactory {
  override def props(client: ClientActor): Props =
    if (filters.nonEmpty) CmdFilter.createActorFilter(filters, props, client) else props
}

case class ParsedCmdFactory[A](
    category: CmdCategory,
    aliases: Seq[String],
    props: Props,
    filters: Seq[CmdFilter] = Seq.empty,
    description: Option[CmdDescription] = None,
)(implicit val parser: MessageParser[A])
    extends CmdFactory {

  override def props(client: ClientActor): Props = {
    val parsed = CmdParser.props(parser, props)
    if (filters.nonEmpty) CmdFilter.createActorFilter(filters, parsed, client) else parsed
  }
}
