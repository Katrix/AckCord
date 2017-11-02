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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import net.katsstuff.ackcord.{APIMessage, DiscordClient}
import net.katsstuff.ackcord.data.{CacheSnapshot, Message, User}
import net.katsstuff.ackcord.util.MessageParser

/**
  * Used to parse valid commands and send them to some handler.
  * It also respects [[DiscordClient.ShutdownClient]].
  * It sends the shutdown to all it's children, and when all the children have
  * stopped, it stops itself. The child actors will not receive any further
  * events once a shutdown has been started.
  * @param needMention If all commands handled by this dispatcher need a
  *                    mention before the command
  * @param initialCommands The initial commands this dispatcher should start with.
  *                        The first map is a map for the category. The second
  *                        map is for the command name itself.
  * @param errorHandlerProps Props for the actor to send all invalid commands to.
  *                          Here you can roll your own, or you can base it of
  *                          [[CommandErrorHandler]].
  */
class CommandRouter(
    needMention: Boolean,
    initialCommands: Map[CmdCategory, Map[String, Props]],
    errorHandlerProps: Props
) extends Actor
    with ActorLogging {
  import net.katsstuff.ackcord.commands.CommandRouter._

  val errorHandler: ActorRef = context.actorOf(errorHandlerProps, "ErrorHandler")

  val commands = mutable.HashMap.empty[CmdCategory, mutable.HashMap[String, ActorRef]]
  initialCommands.foreach {
    case (cat, innerMap) =>
      commands.getOrElseUpdate(cat, mutable.HashMap.empty) ++= innerMap.map {
        case (name, props) =>
          val lowercaseName = name.toLowerCase(Locale.ROOT)
          val actor         = context.actorOf(props, lowercaseName)
          context.watchWith(actor, TerminatedCommand(cat, lowercaseName, props))
          lowercaseName -> actor
      }
  }

  var isShuttingDown = false

  override def receive: Receive = {
    case APIMessage.MessageCreate(msg, c, _) =>
      implicit val cache: CacheSnapshot = c
      if (!isShuttingDown) {
        isValidCommand(msg).foreach { args =>
          if (args == Nil) errorHandler ! NoCommand(msg, c)
          else {
            val lowercaseCommand = args.head.toLowerCase(Locale.ROOT)
            for {
              cat        <- commands.keys.find(cat => lowercaseCommand.startsWith(cat.prefix))
              handlerMap <- commands.get(cat)
            } {
              val newArgs = lowercaseCommand.substring(cat.prefix.length) :: args.tail
              handlerMap.get(newArgs.head) match {
                case Some(handler) => handler ! Command(msg, newArgs.tail, c)
                case None          => errorHandler ! UnknownCommand(msg, cat, newArgs.head, newArgs.tail, c)
              }
            }
          }
        }
      }
    case RegisterCommand(cat, name, handler) =>
      if (!isShuttingDown) {
        commands
          .getOrElseUpdate(cat, mutable.HashMap.empty)
          .put(name.toLowerCase(Locale.ROOT), handler)
      }
    case UnregisterCommand(cat, name) =>
      commands.get(cat).foreach(_.remove(name.toLowerCase(Locale.ROOT)))
    case DiscordClient.ShutdownClient =>
      isShuttingDown = true
      commands.foreach(_._2.foreach(_._2 ! DiscordClient.ShutdownClient))
    case TerminatedCommand(category, name, props) =>
      if (isShuttingDown) {
        log.debug("Command {} in category {} shut down")
        commands.get(category).foreach { map =>
          map.remove(name)
          if(map.isEmpty) commands.remove(category)
        }

        if(commands.isEmpty) {
          context.stop(self)
        }
      } else {
        log.warning("Command {} in category {} shut down. Restarting")
        val newActor = context.actorOf(props)
        context.watchWith(newActor, TerminatedCommand(category, name, props))
        commands.getOrElseUpdate(category, mutable.HashMap.empty).put(name, newActor)
      }
  }

  def isValidCommand(msg: Message)(implicit c: CacheSnapshot): Option[List[String]] = {
    if (needMention) {
      //We do a quick check first before parsing the message
      val quickCheck = if (msg.mentions.contains(c.botUser.id)) Some(msg.content.split(" ").toList) else None

      quickCheck.flatMap { args =>
        MessageParser[User]
          .parse(args)
          .toOption
          .flatMap {
            case (remaining, user) =>
              if (user.id == c.botUser.id) Some(remaining)
              else None
          }
      }
    } else Some(msg.content.split(" ").toList)
  }
}
object CommandRouter {
  def props(needMention: Boolean, initialCommands: Map[CmdCategory, Map[String, Props]], errorHandler: Props): Props =
    Props(new CommandRouter(needMention, initialCommands, errorHandler))

  /**
    * Sent to the error handler if no command is specified when mentioning
    * the client. Only sent if mentioning is required.
    * @param msg The message that triggered this.
    * @param c The cache snapshot.
    */
  case class NoCommand(msg: Message, c: CacheSnapshot)

  /**
    * Sent to the error handler if a correct category is supplied,
    * but no handler for the command is found.
    * @param msg The message that triggered this.
    * @param category The category that was used.
    * @param command The unknown command.
    * @param args The already parsed args. These will not include stuff like
    *             the category and mention.
    * @param c The cache snapshot.
    */
  case class UnknownCommand(msg: Message, category: CmdCategory, command: String, args: List[String], c: CacheSnapshot)

  /**
    * Sent to a handler when a valid command was used.
    * @param msg The message that triggered this
    * @param args The already parsed args. These will not include stuff like
    *             the category, mention and command name.
    * @param c The cache snapshot
    */
  case class Command(msg: Message, args: List[String], c: CacheSnapshot)

  /**
    * Send to the command handler to register a new command
    * @param category The category for this command, for example `!`
    * @param name The name of this command, for example `ping`
    * @param handler The actor that will handle this command
    */
  case class RegisterCommand(category: CmdCategory, name: String, handler: ActorRef)

  /**
    * Send to the command handler to unregister a command
    * @param category The category for this command, for example `!`
    * @param name The name of this command, for example `ping`
    */
  case class UnregisterCommand(category: CmdCategory, name: String)

  private case class TerminatedCommand(category: CmdCategory, name: String, props: Props)
}
