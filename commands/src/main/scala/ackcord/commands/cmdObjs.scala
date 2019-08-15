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

import ackcord.CacheSnapshot
import ackcord.data.Message

/**
  * Top trait for all command messages.
  */
sealed trait AllCmdMessages

/**
  * Trait for all command errors.
  */
sealed trait CmdError extends AllCmdMessages

/**
  * Trait for commands that have not been parsed into a specific command.
  */
sealed trait RawCmdMessage extends AllCmdMessages

/**
  * Trait for all unparsed command messages.
  */
sealed trait CmdMessage extends AllCmdMessages

/**
  * Trait for all parsed command messages.
  */
sealed trait ParsedCmdMessage[+A] extends AllCmdMessages

/**
  * A raw unparsed command.
  * @param msg The message of this command.
  * @param prefix The prefix for this command.
  * @param cmd The name of this command.
  * @param args The arguments of this command.
  * @param c The cache for this command.
  */
case class RawCmd(msg: Message, prefix: String, cmd: String, args: List[String], c: CacheSnapshot) extends RawCmdMessage

/**
  * Bot was mentioned, but no command was used.
  */
case class NoCmd(msg: Message, c: CacheSnapshot) extends RawCmdMessage with CmdError

/**
  * An unknown prefix was used.
  */
case class NoCmdPrefix(msg: Message, command: String, args: List[String], c: CacheSnapshot)
    extends RawCmdMessage
    with CmdError

/**
  * An unparsed specific command.
  * @param msg The message of this command.
  * @param args The args for this command.
  * @param cache The cache for this command.
  */
case class Cmd(msg: Message, args: List[String], cache: CacheSnapshot) extends CmdMessage

/**
  * A parsed specific command.
  * @param msg The message of this command.
  * @param args The args for this command.
  * @param remaining The remaining arguments after the parser did it's thing.
  * @param cache The cache for this command.
  */
case class ParsedCmd[A](msg: Message, args: A, remaining: List[String], cache: CacheSnapshot)
    extends ParsedCmdMessage[A]

/**
  * A parse error for a parsed command.
  * @param msg The message of this command.
  * @param error The error message.
  * @param cache The cache for this command.
  */
case class CmdParseError(msg: Message, error: String, cache: CacheSnapshot)
    extends ParsedCmdMessage[Nothing]
    with CmdError

/**
  * A command that did not make it through some filters.
  * @param failedFilters The filters the command failed.
  * @param cmd The raw command object.
  */
case class FilteredCmd(failedFilters: Seq[CmdFilter], cmd: RawCmd)
    extends CmdMessage
    with ParsedCmdMessage[Nothing]
    with CmdError

/**
  * A generic command error.
  * @param error The error message.
  * @param cmd The raw command object.
  */
case class GenericCmdError(error: String, cmd: RawCmd) extends CmdMessage with ParsedCmdMessage[Nothing] with CmdError
