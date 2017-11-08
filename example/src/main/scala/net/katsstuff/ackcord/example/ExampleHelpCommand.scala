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
package net.katsstuff.ackcord.example

import akka.actor.Props
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.commands.HelpCommand.HelpCommandArgs
import net.katsstuff.ackcord.commands.{CmdCategory, CommandDescription, CommandMeta, HelpCommand}
import net.katsstuff.ackcord.data.CacheSnapshot
import net.katsstuff.ackcord.http.requests.Requests.CreateMessageData
import net.katsstuff.ackcord.util.MessageParser

class ExampleHelpCommand(initialCommands: Map[CmdCategory, Map[String, CommandDescription]], client: ClientActor)
    extends HelpCommand(initialCommands, client) {

  override def createSingleReply(category: CmdCategory, name: String, desc: CommandDescription)(
      implicit c: CacheSnapshot
  ): CreateMessageData = CreateMessageData(createContent(category, printCategory = true, Seq(name), desc))

  override def createReplyAll(page: Int)(implicit c: CacheSnapshot): CreateMessageData = {
    val groupedCommands = commands.grouped(10).toSeq
    if (page > groupedCommands.length) {
      CreateMessageData(s"Max pages: ${groupedCommands.length}")
    }
    else {
      val strings = groupedCommands(page).map {
        case (cat, innerMap) =>
          val res = innerMap.groupBy(_._2.name).map { case (_, map) =>
            createContent(cat, printCategory = false, map.keys.toSeq, map.head._2)
          }

          s"Category: ${cat.prefix}   ${cat.description}\n" + res.mkString("\n")
      }
      CreateMessageData(s"Page: ${page + 1} of ${groupedCommands.length}\n" + strings.mkString("\n"))
    }
  }

  def createContent(cat: CmdCategory, printCategory: Boolean, names: Seq[String], desc: CommandDescription): String = {
    val builder = StringBuilder.newBuilder
    builder.append(s"Name: ${desc.name}\n")
    if(printCategory) builder.append(s"Category: ${cat.prefix}   ${cat.description}\n")
    builder.append(s"Description: ${desc.description}\n")
    builder.append(s"Usage: ${cat.prefix}${names.mkString("|")} ${desc.usage}\n")

    builder.mkString
  }
}
object ExampleHelpCommand {
  def props(initialCommands: Map[CmdCategory, Map[String, CommandDescription]], client: ClientActor): Props = {
    val helpMap = initialCommands.getOrElse(category, Map.empty) ++ aliases
      .map(_ -> description)
      .toMap
    val withHelp = initialCommands + (category -> helpMap)

    Props(new ExampleHelpCommand(withHelp, client))
  }

  def aliases:     Seq[String]        = Seq("help")
  def description: CommandDescription = CommandDescription("Help", "This command right here", "<page|command>")
  def category:    CmdCategory        = ExampleCmdCategories.!

  implicit val parser = MessageParser[Option[HelpCommandArgs]]

  def cmdMeta(
      initialCommands: Map[CmdCategory, Map[String, CommandDescription]],
      client: ClientActor
  ): CommandMeta[Option[HelpCommandArgs]] = CommandMeta[Option[HelpCommandArgs]](
    category = category,
    alias = aliases,
    handler = props(initialCommands, client),
    description = Some(description)
  )
}
