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

import scala.collection.mutable

import akka.NotUsed
import akka.actor.ActorRef
import net.katsstuff.ackcord.commands.CmdParser.ParsedCommand
import net.katsstuff.ackcord.commands.HelpCmd.{AddCmd, TerminatedCmd}
import net.katsstuff.ackcord.commands.HelpCmd.Args.{CommandArgs, PageArgs}
import net.katsstuff.ackcord.data.{CacheSnapshot, Message}
import net.katsstuff.ackcord.http.requests.RESTRequests.{CreateMessage, CreateMessageData}
import net.katsstuff.ackcord.http.requests._
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.util.MessageParser

/**
  * A base for help commands. Takes [[ParsedCommand]] where the argument is
  * either Option[String], representing a specific command, or Option[Int],
  * representing a page.
  */
abstract class HelpCmd extends ParsedCmdActor[Option[HelpCmd.Args]] {

  val commands = mutable.HashMap.empty[CmdCategory, mutable.HashMap[String, CmdDescription]]

  override def handleCommand(msg: Message, args: Option[HelpCmd.Args], remaining: List[String])(
      implicit c: CacheSnapshot
  ): Unit = {
    args match {
      case Some(CommandArgs(cmd)) =>
        val lowercaseCommand = cmd.toLowerCase(Locale.ROOT)
        msg.tChannel.foreach { channel =>
          val res = for {
            cat     <- commands.keys.find(cat => lowercaseCommand.startsWith(cat.prefix))
            descMap <- commands.get(cat)
          } yield {
            val command = lowercaseCommand.substring(cat.prefix.length)
            descMap.get(command) match {
              case Some(desc) =>
                RequestWrapper(CreateMessage(msg.channelId, createSingleReply(cat, command, desc)), NotUsed, self)
              case None =>
                unknownCommand(cat, command).foreach(
                  data => client ! RequestWrapper(CreateMessage(msg.channelId, data), NotUsed, self)
                )
            }
          }

          res match {
            case Some(req) => client ! req
            case None =>
              unknownCategory(lowercaseCommand).foreach(
                data => client ! RequestWrapper(CreateMessage(msg.channelId, data), NotUsed, self)
              )
          }
        }

      case Some(PageArgs(page)) =>
        if (page > 0) {
          client ! RequestWrapper(CreateMessage(msg.channelId, createReplyAll(page - 1)), NotUsed, self)
        } else {
          msg.tChannel.foreach { channel =>
            client ! channel.sendMessage(s"Invalid page $page")
          }
        }
      case None =>
        client ! RequestWrapper(CreateMessage(msg.channelId, createReplyAll(0)), NotUsed, self)
    }
  }

  override def extraReceive: Receive = {
    case AddCmd(factory, handler) =>
      if(handler != self) {
        factory.description.foreach { desc =>
          commands.getOrElseUpdate(factory.category, mutable.HashMap.empty) ++= factory.lowercaseAliases.map(_ -> desc)
          context.watchWith(handler, TerminatedCmd(factory))
        }
      }
    case TerminatedCmd(factory) =>
      commands.get(factory.category).foreach(_ --= factory.lowercaseAliases)
  }

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

  def unknownCategory(command: String): Option[CreateMessageData] = Some(CreateMessageData("Unknown category"))
  def unknownCommand(category: CmdCategory, command: String): Option[CreateMessageData] =
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
    * Sent to a help command to register a new help entry.
    * @param factory The factory for the new command, where help info is located.
    * @param handler The command actor itself. When this stops, the help entry
    *                will be unregistered.
    */
  case class AddCmd(factory: CmdFactory, handler: ActorRef)

  private case class TerminatedCmd(factory: CmdFactory)
}
