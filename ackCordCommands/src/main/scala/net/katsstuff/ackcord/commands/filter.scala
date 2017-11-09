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

import akka.actor.{Actor, ActorRef, Props}
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.commands.CommandParser.ParsedCommand
import net.katsstuff.ackcord.commands.CommandRouter.Command
import net.katsstuff.ackcord.data.{CacheSnapshot, DMChannel, GroupDMChannel, GuildChannel, Message, Permission, UserId}
import net.katsstuff.ackcord.syntax._

/**
  * A command filter is something used to limit the scope in which a command
  * can be used. A few filters are defined here, but creating a custom one
  * is easy.
  */
trait CommandFilter {

  /**
    * Check if the message can be executed.
    */
  def isAllowed(msg: Message)(implicit c: CacheSnapshot): Boolean

  /**
    * If the message could not be executed, get an error message to
    * give the user.
    */
  def errorMessage(msg: Message)(implicit c: CacheSnapshot): String
}
object CommandFilter {

  /**
    * Only allow this command to be used in a specific context
    */
  case class InContext(context: Context) extends CommandFilter {
    override def isAllowed(msg: Message)(implicit c: CacheSnapshot): Boolean = msg.channel.exists {
      case _: GuildChannel   => context == Context.Guild
      case _: DMChannel      => context == Context.DM
      case _: GroupDMChannel => context == Context.DM //We consider group DMs to be DMs
    }
    override def errorMessage(msg: Message)(implicit c: CacheSnapshot): String =
      s"This command can only be used in a $context"
  }

  /**
    * This command can only be used in a guild
    */
  object InGuild extends InContext(Context.Guild)

  /**
    * This command can only be used in a dm
    */
  object InDM extends InContext(Context.DM)

  /**
    * This command can only be used if the user has specific permissions.
    * If this command is not used in a guild, it will always pass this filter.
    */
  case class NeedPermission(neededPermission: Permission) extends CommandFilter {
    override def isAllowed(msg: Message)(implicit c: CacheSnapshot): Boolean = {
      val res = for {
        channel      <- msg.channel
        guildChannel <- channel.asGuildChannel
        guild        <- guildChannel.guild
        member       <- guild.members.get(UserId(msg.author.id))
        if member.channelPermissions(msg.channelId).hasPermissions(neededPermission)
      } yield true

      res.exists(identity)
    }
    override def errorMessage(msg: Message)(implicit c: CacheSnapshot): String =
      "You don't have permission to use this command"
  }

  /**
    * Create an actor that will stop commands according to a set of filters.
    */
  def createActorFilter(filters: Seq[CommandFilter], forwardTo: Props, client: ClientActor): Props =
    Props(new CommandFilterActor(filters, forwardTo, client))
}

/**
  * Represents a place a command can be used.
  */
sealed trait Context
object Context {
  case object Guild extends Context
  case object DM    extends Context
}

class CommandFilterActor(filters: Seq[CommandFilter], forwardToProps: Props, client: ClientActor) extends Actor {
  val forwardTo: ActorRef = context.actorOf(forwardToProps, s"${self.path.name}AfterFilter")

  override def receive: Receive = {
    case cmd @ Command(msg, _, c)          => handleMsg(cmd, msg)(c)
    case cmd @ ParsedCommand(msg, _, _, c) => handleMsg(cmd, msg)(c)
    case other                             => forwardTo.forward(other)
  }

  def handleMsg(cmd: Any, msg: Message)(implicit c: CacheSnapshot): Unit = {
    val notPassed = filters.filterNot(_.isAllowed(msg))
    if (notPassed.isEmpty) forwardTo.forward(cmd)
    else
      for {
        tChannel <- msg.tChannel.toSeq
        filter   <- notPassed
      } client ! tChannel.sendMessage(filter.errorMessage(msg))
  }
}
