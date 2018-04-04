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
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Sink, Source}
import net.katsstuff.ackcord.CacheSnapshot
import net.katsstuff.ackcord.commands.{CmdCategory, CmdDescription, HelpCmd, ParsedCmdFactory}
import net.katsstuff.ackcord.data.raw.RawMessage
import net.katsstuff.ackcord.http.requests.{Request, RequestHelper}
import net.katsstuff.ackcord.http.rest.CreateMessageData

class ExampleHelpCmd(requests: RequestHelper) extends HelpCmd {

  implicit val system: ActorSystem = context.system
  import requests.mat
  import context.dispatcher

  private val msgQueue =
    Source.queue(32, OverflowStrategy.backpressure).to(requests.sinkIgnore[RawMessage, NotUsed]).run()

  override def receive: Receive = {
    val withInit: Receive = {
      case ExampleHelpCmd.InitAck => sender() ! ExampleHelpCmd.Ack
    }

    withInit.orElse(super.receive)
  }

  override def createSingleReply(category: CmdCategory, name: String, desc: CmdDescription)(
      implicit c: CacheSnapshot
  ): CreateMessageData = CreateMessageData(createContent(category, printCategory = true, Seq(name), desc))

  override def createReplyAll(page: Int)(implicit c: CacheSnapshot): CreateMessageData = {
    val groupedCommands = commands.grouped(10).toSeq
    if (!groupedCommands.isDefinedAt(page)) {
      CreateMessageData(s"Max pages: ${groupedCommands.length}")
    } else {
      val lines = groupedCommands(page).map {
        case (cat, pageCommands) =>
          val categoryLines = pageCommands.groupBy(_._2.name).map {
            case (_, command) =>
              createContent(cat, printCategory = false, command.keys.toSeq, command.head._2)
          }

          s"Category: ${cat.prefix}   ${cat.description}\n${categoryLines.mkString("\n")}"
      }

      CreateMessageData(s"Page: ${page + 1} of ${groupedCommands.length}\n${lines.mkString("\n")}")
    }
  }

  def createContent(
      cat: CmdCategory,
      printCategory: Boolean,
      names: Seq[String],
      description: CmdDescription
  ): String = {
    val builder = StringBuilder.newBuilder
    builder.append(s"Name: ${description.name}\n")
    if (printCategory) builder.append(s"Category: ${cat.prefix}   ${cat.description}\n")
    builder.append(s"Description: ${description.description}\n")
    builder.append(s"Usage: ${cat.prefix}${names.mkString("|")} ${description.usage}\n")

    builder.mkString
  }

  override def sendMessageAndAck(sender: ActorRef, request: Request[RawMessage, NotUsed]): Unit =
    msgQueue.offer(request).onComplete(_ => sendAck(sender))

  override def sendAck(sender: ActorRef): Unit = sender ! ExampleHelpCmd.Ack
}
object ExampleHelpCmd {
  case object InitAck
  case object Ack

  def props(requests: RequestHelper): Props = Props(new ExampleHelpCmd(requests))
}

object ExampleHelpCmdFactory {
  def apply(helpCmdActor: ActorRef): ParsedCmdFactory[HelpCmd.Args, NotUsed] = ParsedCmdFactory(
    category = ExampleCmdCategories.!,
    aliases = Seq("help"),
    sink = _ => Sink.actorRefWithAck(helpCmdActor, ExampleHelpCmd.InitAck, ExampleHelpCmd.Ack, PoisonPill),
    description = Some(CmdDescription(name = "Help", description = "This command right here", usage = "<page|command>"))
  )
}
