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

import akka.actor.Props
import net.katsstuff.ackcord.util.MessageParser

/**
  * Represents non essential information about a command intended to be
  * displayed to an end user.
  * @param name The display name of a command.
  * @param description The description of what a command does.
  * @param usage How to use the command.
  */
case class CommandDescription(name: String, description: String, usage: String)

/**
  * Represents a parsed command, and information about it. Useful for grouping
  * commands together and registering them at the same time.
  * @param prefix The prefix for this command.
  * @param alias All the aliases of this command.
  * @param description Optional information to shot to users about the command.
  * @param handler The handler for the command. Should accept [[net.katsstuff.ackcord.commands.CommandParser.ParsedCommand]].
  * @param parser The parser to use for the command.
  * @tparam A The type of the parsed args.
  */
case class CommandMeta[A](prefix: String, alias: Seq[String], description: Option[CommandDescription], handler: Props)(
    implicit val parser: MessageParser[A]
)
object CommandMeta {

  /**
    * Create a map that can be passed to a [[CommandDispatcher]] as the initial commands.
    * @param commands The commands to use
    */
  def createDispatcherInitialCommands(commands: Seq[CommandMeta[_]]): Map[String, Map[String, Props]] = {
    commands.groupBy(_.prefix).mapValues { seq =>
      val res = for {
        meta  <- seq
        alias <- meta.alias
      } yield alias -> CommandParser.props(meta.parser, meta.handler)

      res.toMap
    }
  }

  /**
    * Create a map that can be passed to a [[HelpCommand]] as the initial commands.
    * @param commands The commands to use
    */
  def createHelpInitialCommands(commands: Seq[CommandMeta[_]]): Map[String, Map[String, CommandDescription]] = {
    commands.groupBy(_.prefix).mapValues { seq =>
      val res = for {
        meta  <- seq
        alias <- meta.alias
        desc <- meta.description
      } yield alias -> desc

      res.toMap
    }
  }
}
