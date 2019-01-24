/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
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

import scala.language.higherKinds

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Sink, Source}
import cats.{Id, Monad}
import net.katsstuff.ackcord.MemoryCacheSnapshot
import net.katsstuff.ackcord.commands.{CmdDescription, CmdInfo, HelpCmd, ParsedCmdFactory}
import net.katsstuff.ackcord.data.{EmbedField, Message, OutgoingEmbed, OutgoingEmbedFooter}
import net.katsstuff.ackcord.data.raw.RawMessage
import net.katsstuff.ackcord.requests.{CreateMessageData, Request, RequestHelper}

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

  override def createSearchReply(message: Message, query: String, matches: Seq[HelpCmd.CommandRegistration])(
      implicit c: MemoryCacheSnapshot
  ): CreateMessageData =
    CreateMessageData(
      embed = Some(
        OutgoingEmbed(
          title = Some(s"Commands matching: $query"),
          fields = matches
            .filter(_.info.filters(message).forall(_.isAllowed[Id](message)))
            .map(createContent(message, _))
        )
      )
    )

  override def createReplyAll(message: Message, page: Int)(implicit c: MemoryCacheSnapshot): CreateMessageData = {
    val commandsSlice = commands.toSeq
      .sortBy(reg => (reg.info.prefix(message), reg.info.aliases(message).head))
      .slice(page * 10, (page + 1) * 10)
    val maxPages = Math.max(commands.size / 10, 1)
    if (commandsSlice.isEmpty) {
      CreateMessageData(s"Max pages: $maxPages")
    } else {

      CreateMessageData(
        embed = Some(
          OutgoingEmbed(
            fields = commandsSlice.map(createContent(message, _)),
            footer = Some(OutgoingEmbedFooter(s"Page: ${page + 1} of $maxPages"))
          )
        )
      )
    }
  }

  def createContent(
      message: Message,
      reg: HelpCmd.CommandRegistration
  )(implicit c: MemoryCacheSnapshot): EmbedField = {
    val builder = StringBuilder.newBuilder
    builder.append(s"Name: ${reg.description.name}\n")
    builder.append(s"Description: ${reg.description.description}\n")
    builder.append(
      s"Usage: ${reg.info.prefix(message)}${reg.info.aliases(message).mkString("|")} ${reg.description.usage}\n"
    )

    EmbedField(reg.description.name, builder.mkString)
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
  def apply[F[_]: Monad](helpCmdActor: ActorRef): ParsedCmdFactory[F, Option[HelpCmd.Args], NotUsed] = ParsedCmdFactory(
    refiner = CmdInfo[F](prefix = "!", aliases = Seq("help")),
    sink = _ => Sink.actorRefWithAck(helpCmdActor, ExampleHelpCmd.InitAck, ExampleHelpCmd.Ack, PoisonPill),
    description = Some(CmdDescription(name = "Help", description = "This command right here", usage = "<page|command>"))
  )
}
