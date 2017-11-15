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
import akka.routing.Broadcast
import net.katsstuff.ackcord.DiscordClient.{ClientActor, ShutdownClient}
import net.katsstuff.ackcord.data.{CacheSnapshot, Message, User}
import net.katsstuff.ackcord.util.MessageParser
import net.katsstuff.ackcord.{APIMessage, DiscordClient}

/**
  * Used to parse valid commands and send them to some handler.
  * It also respects [[DiscordClient.ShutdownClient]].
  * It sends the shutdown to all it's children, and when all the children have
  * stopped, it stops itself. The child actors will not receive any further
  * events once a shutdown has been started.
  * @param needMention If all commands handled by this dispatcher need a
  *                    mention before the command
  * @param errorHandlerProps Props for the actor to send all invalid commands to.
  *                          Here you can roll your own, or you can base it of
  *                          [[CmdErrorHandler]].
  */
class CmdRouter(client: ClientActor, var needMention: Boolean, errorHandlerProps: Props, helpFactory: Option[CmdFactory])
    extends Actor
    with ActorLogging {
  import net.katsstuff.ackcord.commands.CmdRouter._

  val commands       = mutable.HashMap.empty[CmdCategory, mutable.HashMap[String, ActorRef]]
  var isShuttingDown = false

  val errorHandler: ActorRef         = context.actorOf(errorHandlerProps, "ErrorHandler")
  val helpCmd:      Option[ActorRef] = helpFactory.map { factory =>
    val helpActor = context.actorOf(factory.props(client), "HelpCmd")
    commands.getOrElseUpdate(factory.category, mutable.HashMap.empty) ++= factory.lowercaseAliases.map(_ -> helpActor)
    helpActor
  }

  override def receive: Receive = {
    case APIMessage.MessageCreate(msg, c) =>
      implicit val cache: CacheSnapshot = c.current
      if (!isShuttingDown) {
        isValidCommand(msg).foreach { args =>
          if (args == Nil) errorHandler ! NoCommand(msg, c.current)
          else {
            val lowercaseCommand = args.head.toLowerCase(Locale.ROOT)
            for {
              cat        <- commands.keys.find(cat => lowercaseCommand.startsWith(cat.prefix))
              handlerMap <- commands.get(cat)
            } {
              val withoutPrefix = lowercaseCommand.substring(cat.prefix.length)
              handlerMap.get(withoutPrefix) match {
                case Some(handler) => handler ! Command(msg, args.tail, c.current)
                case None          => errorHandler ! UnknownCommand(msg, cat, withoutPrefix, args.tail, c.current)
              }
            }
          }
        }
      }
    case RegisterCmd(factory) =>
      if (!isShuttingDown) {
        val handler = context.actorOf(factory.props(client), factory.aliases.head)
        context.watchWith(handler, TerminatedCmd(factory))
        commands.getOrElseUpdate(factory.category, mutable.HashMap.empty) ++= factory.lowercaseAliases.map(_ -> handler)
      }
    case SetNeedMention(newNeedMention) => needMention = newNeedMention
    case ClearCmds =>
      commands.foreach(_._2.foreach(t => context.stop(t._2)))
    case SendToCmd(factory, msg) =>
      commands
        .get(factory.category)
        .flatMap(_.find(t => factory.lowercaseAliases.contains(t._1)))
        .foreach(_._2 ! msg)
    case Broadcast(msg) =>
      commands.foreach(_._2.foreach(_._2 ! msg))
    case SendToErrorHandler(msg) =>
      errorHandler ! msg
    case SendToHelpCmd(msg) =>
      helpCmd.foreach(_ ! msg)
    case DiscordClient.ShutdownClient =>
      isShuttingDown = true
      errorHandler ! ShutdownClient
      commands.foreach(_._2.foreach(_._2 ! DiscordClient.ShutdownClient))
    case TerminatedCmd(factory) =>
      log.debug("Command {} in category {} shut down")

      commands.get(factory.category).foreach { map =>
        map --= factory.lowercaseAliases
        if (map.isEmpty) commands.remove(factory.category)
      }

      if (commands.isEmpty) {
        context.stop(self)
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
object CmdRouter {
  def props(client: ClientActor, needMention: Boolean, errorHandler: Props, helpCmd: Option[CmdFactory]): Props =
    Props(new CmdRouter(client, needMention, errorHandler, helpCmd))

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
    * Send this to the command router to register a new command.
    * @param factory The factory for the command.
    */
  case class RegisterCmd(factory: CmdFactory)

  /**
    * Set if this command router should need a mention after it's been started.
    */
  case class SetNeedMention(needMention: Boolean)

  /**
    * Removes all the commands from this command router. This will stop the 
    * actor once all the commands have stopped.
    */
  case object ClearCmds

  /**
    * Send a message to a specific command.
    * @param factory The factory for the command.
    * @param msg The message to send.
    */
  case class SendToCmd(factory: CmdFactory, msg: Any)

  /**
    * Send a message to the error handler.
    * @param msg The message to send.
    */
  case class SendToErrorHandler(msg: Any)

  /**
    * Send a command to the help command of this command router.
    * @param msg The message to send.
    */
  case class SendToHelpCmd(msg: Any)

  private case class TerminatedCmd(factory: CmdFactory)
}
