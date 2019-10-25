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
package ackcord.examplecore

import ackcord.CacheSnapshot
import ackcord.commands.{CmdDescription, CmdInfo, HelpCmd, ParsedCmd, ParsedCmdFactory}
import ackcord.data.raw.RawMessage
import ackcord.data.{EmbedField, Message, OutgoingEmbed, OutgoingEmbedFooter}
import ackcord.requests.{CreateMessageData, Request, RequestHelper}
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSink

class ExampleHelpCmd(
    ctx: ActorContext[ExampleHelpCmd.Command],
    requests: RequestHelper,
    someHandler: ActorRef[HelpCmd.HandlerReply]
) extends HelpCmd[ExampleHelpCmd.Command](ctx) {

  implicit val system: ActorSystem[Nothing] = context.system
  import context.executionContext

  private val msgQueue =
    Source.queue(32, OverflowStrategy.backpressure).to(requests.sinkIgnore[RawMessage, NotUsed]).run()

  override protected def handler: Option[ActorRef[HelpCmd.HandlerReply]] = Some(someHandler)

  override protected def sendEmptyEvent: Boolean = true

  override def onMessage(msg: ExampleHelpCmd.Command): Behavior[ExampleHelpCmd.Command] = msg match {
    case ExampleHelpCmd.InitAck(replyTo) =>
      replyTo ! HelpCmd.Ack
      Behaviors.same
    case ExampleHelpCmd.SinkComplete             => Behaviors.same
    case ExampleHelpCmd.SendException(e)         => throw e
    case ExampleHelpCmd.BaseCommandWrapper(base) => onBaseMessage(base)
  }

  override def createSearchReply(message: Message, query: String, matches: Seq[HelpCmd.CommandRegistration])(
      implicit c: CacheSnapshot
  ): CreateMessageData =
    CreateMessageData(
      embed = Some(
        OutgoingEmbed(
          title = Some(s"Commands matching: $query"),
          fields = matches
            .filter(_.info.filters(message).forall(_.isAllowed(message)))
            .map(createContent(message, _))
        )
      )
    )

  override def createReplyAll(message: Message, page: Int)(implicit c: CacheSnapshot): CreateMessageData = {
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
  )(implicit c: CacheSnapshot): EmbedField = {
    val builder = new StringBuilder
    builder.append(s"Name: ${reg.description.name}\n")
    builder.append(s"Description: ${reg.description.description}\n")
    builder.append(
      s"Usage: ${reg.info.prefix(message)}${reg.info.aliases(message).mkString("|")} ${reg.description.usage}\n"
    )

    EmbedField(reg.description.name, builder.mkString)
  }

  override def sendMessageAndAck(
      sender: ActorRef[HelpCmd.Ack.type],
      request: Request[RawMessage, NotUsed]
  ): Unit =
    msgQueue.offer(request).onComplete(_ => sendAck(sender))

  override def terminateCommand(registration: HelpCmd.CommandRegistration): ExampleHelpCmd.Command =
    ExampleHelpCmd.BaseCommandWrapper(HelpCmd.TerminateCommand(registration))
}
object ExampleHelpCmd {
  def apply(requests: RequestHelper, handler: ActorRef[HelpCmd.HandlerReply]): Behavior[Command] =
    Behaviors.setup(ctx => new ExampleHelpCmd(ctx, requests, handler))

  sealed trait Command
  case class BaseCommandWrapper(wrapper: HelpCmd.BaseCommand) extends Command

  case class InitAck(replyTo: ActorRef[HelpCmd.Ack.type]) extends Command
  case object SinkComplete                                extends Command
  case class SendException(e: Throwable)                  extends Command
}

object ExampleHelpCmdFactory {
  def apply(helpCmdActor: ActorRef[ExampleHelpCmd.Command]): ParsedCmdFactory[Option[HelpCmd.Args], NotUsed] =
    ParsedCmdFactory(
      refiner = CmdInfo(prefix = "!", aliases = Seq("help")),
      sink = _ =>
        ActorSink
          .actorRefWithBackpressure[ParsedCmd[Option[HelpCmd.Args]], ExampleHelpCmd.Command, HelpCmd.Ack.type](
            helpCmdActor,
            (r, c) => ExampleHelpCmd.BaseCommandWrapper(HelpCmd.CmdMessage(r, c)),
            ExampleHelpCmd.InitAck,
            HelpCmd.Ack,
            ExampleHelpCmd.SinkComplete,
            ExampleHelpCmd.SendException
          ),
      description = Some(
        CmdDescription(
          name = "Help",
          description = "This command right here",
          usage = "<page|command>",
          extra = Map("ignore-help-last" -> "") //Ignores the help command when returning if all commands have been unregistered
        )
      )
    )
}
