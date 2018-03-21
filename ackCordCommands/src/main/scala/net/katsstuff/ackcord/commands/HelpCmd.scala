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
package net.katsstuff.ackcord.commands

import java.util.Locale

import scala.collection.mutable
import scala.concurrent.Future

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef}
import net.katsstuff.ackcord.CacheSnapshot
import net.katsstuff.ackcord.commands.HelpCmd.Args.{CommandArgs, PageArgs}
import net.katsstuff.ackcord.commands.HelpCmd.{AddCmd, TerminatedCmd}
import net.katsstuff.ackcord.data.raw.RawMessage
import net.katsstuff.ackcord.network.requests.RESTRequests.{CreateMessage, CreateMessageData}
import net.katsstuff.ackcord.network.requests._
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.util.MessageParser

/**
  * A base for help commands. Commands need to be registered manually
  * using [[HelpCmd.AddCmd]].
  */
abstract class HelpCmd extends Actor {
  import context.dispatcher

  val commands = mutable.HashMap.empty[CmdCategory, mutable.HashMap[String, CmdDescription]]

  override def receive: Receive = {
    case ParsedCmd(msg, Some(CommandArgs(cmd)), _, c) =>
      implicit val cache: CacheSnapshot = c
      val lowercaseCommand = cmd.toLowerCase(Locale.ROOT)

      val response = for {
        cat     <- commands.keys.find(cat => lowercaseCommand.startsWith(cat.prefix))
        descMap <- commands.get(cat)
        command = lowercaseCommand.substring(cat.prefix.length)
        req <- descMap.get(command) match {
          case Some(desc) => Some(CreateMessage(msg.channelId, createSingleReply(cat, command, desc)))
          case None       => unknownCmd(cat, command).map(CreateMessage(msg.channelId, _))
        }
      } yield req

      val withUnknownCategory = response.orElse(unknownCategory(lowercaseCommand).map(CreateMessage(msg.channelId, _)))

      withUnknownCategory match {
        case Some(req) => sendMessageAndAck(sender(), req)
        case None      => sendAck(sender())
      }

    case ParsedCmd(msg, Some(PageArgs(page)), _, c) =>
      implicit val cache: CacheSnapshot = c

      if (page > 0) {
        sendMessageAndAck(sender(), CreateMessage(msg.channelId, createReplyAll(page - 1)))
      } else {
        msg.channelId.tResolve match {
          case Some(channel) => sendMessageAndAck(sender(), channel.sendMessage(s"Invalid page $page"))
          case None          => sendAck(sender())
        }
      }

    case ParsedCmd(msg, None, _, c) =>
      implicit val cache: CacheSnapshot = c
      sendMessageAndAck(sender(), CreateMessage(msg.channelId, createReplyAll(0)))

    case AddCmd(factory, commandEnd) =>
      factory.description.foreach { desc =>
        commands.getOrElseUpdate(factory.category, mutable.HashMap.empty) ++= factory.lowercaseAliases.map(_ -> desc)

        commandEnd.onComplete { _ =>
          self ! TerminatedCmd(factory)
        }
      }

    case TerminatedCmd(factory) =>
      commands.get(factory.category).foreach(_ --= factory.lowercaseAliases)
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
    * Create a reply for a single command
    * @param category The category of the command
    * @param name The command name
    * @param desc The description for the command
    * @return Data to create a message describing the command
    */
  def createSingleReply(category: CmdCategory, name: String, desc: CmdDescription)(
      implicit c: CacheSnapshot
  ): CreateMessageData

  /**
    * Create a reply for all the commands tracked by this help command.
    * @param page The page to use. Starts at 0.
    * @return Data to create a message describing the commands tracked
    *         by this help command.
    */
  def createReplyAll(page: Int)(implicit c: CacheSnapshot): CreateMessageData

  def unknownCategory(command: String): Option[CreateMessageData] =
    Some(CreateMessageData("Unknown category"))

  def unknownCmd(category: CmdCategory, command: String): Option[CreateMessageData] =
    Some(CreateMessageData("Unknown command"))
}
object HelpCmd {
  sealed trait Args
  object Args {
    case class CommandArgs(command: String) extends Args
    case class PageArgs(page: Int)          extends Args

    //We write out the parser ourself as string parses any string
    implicit val parser: MessageParser[Args] = new MessageParser[Args] {
      override def parse(strings: List[String])(implicit c: CacheSnapshot): Either[String, (List[String], Args)] = {
        if (strings.nonEmpty) {
          val head :: tail = strings
          MessageParser.intParser
            .parse(strings)
            .map(t => t._1 -> PageArgs(t._2))
            .left
            .flatMap(_ => Right((tail, CommandArgs(head))))
        } else Left("Not enough arguments")
      }
    }
  }

  /**
    * Register a new help entry for a command.
    * @param factory The factory for the command.
    * @param commandEnd A future that is completed when the command is removed.
    */
  case class AddCmd(factory: CmdFactory[_, _], commandEnd: Future[Done])

  private case class TerminatedCmd(factory: CmdFactory[_, _])
}
