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

import scala.language.higherKinds

import net.katsstuff.ackcord.CacheSnapshot
import net.katsstuff.ackcord.data.Message

/**
  * Top trait for all command messages.
  */
sealed trait AllCmdMessages[F[_]]

/**
  * Trait for all command errors.
  */
sealed trait CmdError[F[_]] extends AllCmdMessages[F]

/**
  * Trait for commands that have not been parsed into a specific command.
  */
sealed trait RawCmdMessage[F[_]] extends AllCmdMessages[F]

/**
  * Trait for all unparsed command messages.
  */
sealed trait CmdMessage[F[_]] extends AllCmdMessages[F]

/**
  * Trait for all parsed command messages.
  */
sealed trait ParsedCmdMessage[F[_], +A] extends AllCmdMessages[F]

/**
  * A raw unparsed command.
  * @param msg The message of this command.
  * @param prefix The prefix for this command.
  * @param cmd The name of this command.
  * @param args The arguments of this command.
  * @param c The cache for this command.
  */
case class RawCmd[F[_]](msg: Message, prefix: String, cmd: String, args: List[String], c: CacheSnapshot[F])
    extends RawCmdMessage[F] {

  @deprecated("CmdCategory is deprecated. Use the prefix instead", since = "0.11")
  def category: CmdCategory = CmdCategory(prefix, "")
}

/**
  * Bot was mentioned, but no command was used.
  */
case class NoCmd[F[_]](msg: Message, c: CacheSnapshot[F]) extends RawCmdMessage[F] with CmdError[F]

/**
  * An unknown prefix was used.
  */
case class NoCmdPrefix[F[_]](msg: Message, command: String, args: List[String], c: CacheSnapshot[F])
    extends RawCmdMessage[F]
    with CmdError[F]

/**
  * An unparsed specific command.
  * @param msg The message of this command.
  * @param args The args for this command.
  * @param cache The cache for this command.
  */
case class Cmd[F[_]](msg: Message, args: List[String], cache: CacheSnapshot[F]) extends CmdMessage[F]

/**
  * A parsed specific command.
  * @param msg The message of this command.
  * @param args The args for this command.
  * @param remaining The remaining arguments after the parser did it's thing.
  * @param cache The cache for this command.
  */
case class ParsedCmd[F[_], A](msg: Message, args: A, remaining: List[String], cache: CacheSnapshot[F])
    extends ParsedCmdMessage[F, A]

/**
  * A parse error for a parsed command.
  * @param msg The message of this command.
  * @param error The error message.
  * @param cache The cache for this command.
  */
case class CmdParseError[F[_]](msg: Message, error: String, cache: CacheSnapshot[F])
    extends ParsedCmdMessage[F, Nothing]
    with CmdError[F]

/**
  * A command that did not make it through some filters.
  * @param failedFilters The filters the command failed.
  * @param cmd The raw command object.
  */
case class FilteredCmd[F[_]](failedFilters: Seq[CmdFilter], cmd: RawCmd[F])
    extends CmdMessage[F]
    with ParsedCmdMessage[F, Nothing]
    with CmdError[F]
