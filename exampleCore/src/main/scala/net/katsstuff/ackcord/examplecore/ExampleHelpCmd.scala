/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord.examplecore

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.stream.scaladsl.Sink
import net.katsstuff.ackcord.commands.{CmdCategory, CmdDescription, HelpCmd, ParsedCmdFactory}
import net.katsstuff.ackcord.data.CacheSnapshot
import net.katsstuff.ackcord.network.requests.RESTRequests.CreateMessageData
import net.katsstuff.ackcord.network.requests.{Request, RequestHelper}

class ExampleHelpCmd(requests: RequestHelper) extends HelpCmd {

  implicit val system: ActorSystem = context.system

  override def createSingleReply(category: CmdCategory, name: String, desc: CmdDescription)(
      implicit c: CacheSnapshot
  ): CreateMessageData = CreateMessageData(createContent(category, printCategory = true, Seq(name), desc))

  override def createReplyAll(page: Int)(implicit c: CacheSnapshot): CreateMessageData = {
    val groupedCommands = commands.grouped(10).toSeq
    if (page > groupedCommands.length) {
      CreateMessageData(s"Max pages: ${groupedCommands.length}")
    } else {
      val strings = groupedCommands(page).map {
        case (cat, innerMap) =>
          val res = innerMap.groupBy(_._2.name).map {
            case (_, map) =>
              createContent(cat, printCategory = false, map.keys.toSeq, map.head._2)
          }

          s"Category: ${cat.prefix}   ${cat.description}\n" + res.mkString("\n")
      }
      CreateMessageData(s"Page: ${page + 1} of ${groupedCommands.length}\n" + strings.mkString("\n"))
    }
  }

  def createContent(cat: CmdCategory, printCategory: Boolean, names: Seq[String], desc: CmdDescription): String = {
    val builder = StringBuilder.newBuilder
    builder.append(s"Name: ${desc.name}\n")
    if (printCategory) builder.append(s"Category: ${cat.prefix}   ${cat.description}\n")
    builder.append(s"Description: ${desc.description}\n")
    builder.append(s"Usage: ${cat.prefix}${names.mkString("|")} ${desc.usage}\n")

    builder.mkString
  }

  override def sendMsg[Data, Ctx](request: Request[Data, Ctx]): Unit = requests.singleIgnore(request)
}
object ExampleHelpCmd {
  def props(requests: RequestHelper): Props = Props(new ExampleHelpCmd(requests))
}

class ExampleHelpCmdFactory(helpCmdActor: ActorRef)
    extends ParsedCmdFactory[HelpCmd.Args, NotUsed](
      category = ExampleCmdCategories.!,
      aliases = Seq("help"),
      sink = _ => Sink.actorRef(helpCmdActor, PoisonPill),
      description =
        Some(CmdDescription(name = "Help", description = "This command right here", usage = "<page|command>"))
    )
object ExampleHelpCmdFactory {
  def apply(helpCmdActor: ActorRef): ExampleHelpCmdFactory = new ExampleHelpCmdFactory(helpCmdActor)
}
