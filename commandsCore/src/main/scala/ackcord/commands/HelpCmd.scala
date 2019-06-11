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
package ackcord.commands

import java.util.Locale

import scala.collection.mutable
import scala.concurrent.Future
import ackcord._
import ackcord.commands.HelpCmd.Args.{CommandArgs, PageArgs}
import ackcord.commands.HelpCmd.{AddCmd, CommandRegistration, TerminateCommand}
import ackcord.data.Message
import ackcord.data.raw.RawMessage
import ackcord.requests._
import ackcord.syntax._
import akka.actor.{Actor, ActorRef}
import akka.{Done, NotUsed}
import cats.syntax.all._

/**
  * A base for help commands. Commands need to be registered manually
  * using [[HelpCmd.AddCmd]].
  *
  * Can only be used with the cache in Core.
  */
abstract class HelpCmd extends Actor {
  import context.dispatcher

  val commands = mutable.HashSet.empty[CommandRegistration]

  protected def handler: Option[ActorRef] = None

  /**
    * If this help command should send an event to the handler when a command
    * is stopped.
    */
  protected def sendEndedEvent: Boolean = false

  /**
    * If this help command should send an event to the handler when all it's
    * commands have ended.
    */
  protected def sendEmptyEvent: Boolean = false

  override def receive: Receive = {
    case ParsedCmd(msg, Some(CommandArgs(cmd)), _, c) =>
      implicit val cache: MemoryCacheSnapshot = c.asInstanceOf[MemoryCacheSnapshot]
      val lowercaseCommand                    = cmd.toLowerCase(Locale.ROOT)

      val matches = for {
        reg <- commands
        prefix = reg.info.prefix(msg)
        if lowercaseCommand.startsWith(prefix)
        command = lowercaseCommand.substring(prefix.length)
        aliases = reg.info.aliases(msg): Seq[String]
        if aliases.contains(command)
      } yield reg

      val response = if (matches.nonEmpty) Some(createSearchReply(msg, cmd, matches.toSeq)) else unknownCmd(cmd)

      response.map(CreateMessage(msg.channelId, _)) match {
        case Some(req) => sendMessageAndAck(sender(), req)
        case None      => sendAck(sender())
      }

    case ParsedCmd(msg, Some(PageArgs(page)), _, c) =>
      implicit val cache: MemoryCacheSnapshot = c.asInstanceOf[MemoryCacheSnapshot]

      if (page > 0) {
        sendMessageAndAck(sender(), CreateMessage(msg.channelId, createReplyAll(msg, page - 1)))
      } else {
        msg.channelId.tResolve.value match {
          case Some(channel) => sendMessageAndAck(sender(), channel.sendMessage(s"Invalid page $page"))
          case None          => sendAck(sender())
        }
      }

    case ParsedCmd(msg, None, _, c) =>
      implicit val cache: MemoryCacheSnapshot = c.asInstanceOf[MemoryCacheSnapshot]
      sendMessageAndAck(sender(), CreateMessage(msg.channelId, createReplyAll(msg, 0)))

    case AddCmd(info, description, commandEnd) =>
      val registration = CommandRegistration(info, description)
      commands += registration

      commandEnd.onComplete { _ =>
        self ! TerminateCommand(registration)
      }

    case TerminateCommand(registration) =>
      commands -= registration

      if (sendEndedEvent) {
        handler.foreach(_ ! HelpCmd.CommandTerminated(registration))
      }

      if (commands.forall(_.description.extra.contains("ignore-help-last")) && sendEmptyEvent) {
        handler.foreach(_ ! HelpCmd.NoCommandsRemaining)
      }
  }

  /**
    * Sends an ack once the processing of a command is done.
    * @param sender The actor to send the ack to.
    */
  def sendAck(sender: ActorRef): Unit

  /**
    * Send a request, and acks the sender.
    */
  def sendMessageAndAck(sender: ActorRef, request: Request[RawMessage, NotUsed]): Unit

  /**
    * Create a reply for a search result
    * @param matches All the commands that matched the arguments
    * @return Data to create a message describing the search
    */
  def createSearchReply(message: Message, query: String, matches: Seq[CommandRegistration])(
      implicit c: MemoryCacheSnapshot
  ): CreateMessageData

  /**
    * Create a reply for all the commands tracked by this help command.
    * @param page The page to use. Starts at 0.
    * @return Data to create a message describing the commands tracked
    *         by this help command.
    */
  def createReplyAll(message: Message, page: Int)(implicit c: MemoryCacheSnapshot): CreateMessageData

  def unknownCmd(command: String): Option[CreateMessageData] =
    Some(CreateMessageData(s"Unknown command $command"))
}
object HelpCmd {
  case class CommandRegistration(info: AbstractCmdInfo[Id], description: CmdDescription)
  private case class TerminateCommand(registration: CommandRegistration)

  sealed trait Args
  object Args {
    case class CommandArgs(command: String) extends Args
    case class PageArgs(page: Int)          extends Args

    //We write out the parser ourself as string parses any string
    implicit val parser: MessageParser[Args] =
      MessageParser.intParser.map[Args](PageArgs).orElse(MessageParser.stringParser.map(CommandArgs))
  }

  /**
    * Register a new help entry for a command.
    * @param info The command info for the command.
    * @param description The command description for the command
    * @param commandEnd A future that is completed when the command is removed.
    */
  case class AddCmd(info: AbstractCmdInfo[Id], description: CmdDescription, commandEnd: Future[Done])

  /**
    * Sent to a handler from the help command when a command is unregistered.
    * @param registration The registration info for the command
    */
  case class CommandTerminated(registration: CommandRegistration)

  /**
    * Sent from the help command when all the commands it's been managing have
    * been unregistered. Commands that have the extra property named
    * `ignore-help-last` will be ignored from the consideration of all commands.
    */
  case object NoCommandsRemaining
}
