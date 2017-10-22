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
import akka.actor.Actor
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.Request
import net.katsstuff.ackcord.commands.CommandParser.{ParseError, ParsedCommand}
import net.katsstuff.ackcord.commands.HelpCommand.{RegisterCommand, UnregisterCommand}
import net.katsstuff.ackcord.data.CacheSnapshot
import net.katsstuff.ackcord.http.rest.Requests.{CreateMessage, CreateMessageData}
import net.katsstuff.ackcord.syntax._

/**
  * A base for help commands. Takes [[ParsedCommand]] where the argument is
  * either Option[String], representing a specific command, or Option[Int],
  * representing a page.
  * @param client The client
  * @param initialCommands The initial commands to start with.
  *                        The first map is a map for the prefix. The second
  *                        map is for the command name itself, without the prefix.
  */
abstract class HelpCommand(initialCommands: Map[CmdCategory, Map[String, CommandDescription]])(implicit client: ClientActor) extends Actor {

  val commands = mutable.HashMap.empty[CmdCategory, mutable.HashMap[String, CommandDescription]]
  initialCommands.foreach {
    case (cat, innerMap) =>
      commands.getOrElseUpdate(cat, mutable.HashMap.empty) ++= innerMap.map {
        case (name, desc) =>
          val lowercaseName = name.toLowerCase(Locale.ROOT)
          lowercaseName -> desc
      }
  }

  override def receive: Receive = {
    case ParsedCommand(msg, Some(cmd: String), _, c) =>
      implicit val cache: CacheSnapshot = c
      val lowercaseCommand = cmd.toLowerCase(Locale.ROOT)
      for {
        channel <- msg.tChannel
        cat  <- commands.keys.find(cat => lowercaseCommand.startsWith(cat.prefix))
        descMap <- commands.get(cat)
      } {
        val command = lowercaseCommand.substring(cat.prefix.length)
        descMap.get(command) match {
          case Some(desc) =>
            client ! Request(
              CreateMessage(msg.channelId, createSingleReply(cat, command, desc)),
              NotUsed,
              None
            )
          case None => client ! channel.sendMessage("Unknown command")
        }
      }

    case ParsedCommand(msg, None, _, _) =>
      client ! Request(CreateMessage(msg.channelId, createReplyAll(0)), NotUsed, None)
    case ParsedCommand(msg, Some(page: Int), _, c) =>
      implicit val cache: CacheSnapshot = c
      if(page > 0) {
        client ! Request(CreateMessage(msg.channelId, createReplyAll(page - 1)), NotUsed, None)
      }
      else {
        msg.tChannel.foreach { channel =>
          client ! channel.sendMessage(s"Invalid page $page")
        }
      }
    case ParseError(msg, e, c) =>
      implicit val cache: CacheSnapshot = c
      msg.tChannel.foreach { channel =>
        client ! channel.sendMessage(e)
      }
    case RegisterCommand(cat, name, desc) =>
      commands
        .getOrElseUpdate(cat, mutable.HashMap.empty)
        .put(name.toLowerCase(Locale.ROOT), desc)
    case UnregisterCommand(cat, name) =>
      commands.get(cat).foreach(_.remove(name.toLowerCase(Locale.ROOT)))
  }

  /**
    * Create a reply for a single command
    * @param category The category of the command
    * @param name The command name
    * @param desc The description for the command
    * @return Data to create a message describing the command
    */
  def createSingleReply(category: CmdCategory, name: String, desc: CommandDescription): CreateMessageData

  /**
    * Create a reply for all the commands tracked by this help command.
    * @param page The page to use. Starts at 0.
    * @return Data to create a message describing the commands tracked
    *         by this help command.
    */
  def createReplyAll(page: Int): CreateMessageData
}
object HelpCommand {

  /**
    * Send to the help command to register a new command
    * @param category The category for this command, for example `!`
    * @param name The name of this command, for example `ping`
    * @param description The description for this command
    */
  case class RegisterCommand(category: CmdCategory, name: String, description: CommandDescription)

  /**
    * Send to the help command to unregister a command
    * @param category The category for this command, for example `!`
    * @param name The name of this command, for example `ping`
    */
  case class UnregisterCommand(category: CmdCategory, name: String)
}