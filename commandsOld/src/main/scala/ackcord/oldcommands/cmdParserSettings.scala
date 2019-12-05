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
package ackcord.oldcommands

import ackcord.CacheSnapshot
import ackcord.data.Message
import cats.instances.option._
import cats.syntax.all._

/**
  * An object to control how messages are parsed into command objects.
  */
trait AbstractCommandSettings {

  /**
    * Checks if a given message needs a mention at the start.
    */
  def needMention(message: Message)(implicit c: CacheSnapshot): Boolean

  /**
    * Extracts the prefix for a command, and the remaining arguments given a message.
    * @param args The arguments for the command. The first argument is the prefix.
    * @param message The message object that sent the command.
    * @return Some if a given prefix is valid, otherwise None. The first
    *         string in the tuple is the prefix, while the `Seq[String]` is
    *         the remaining arguments.
    */
  def getPrefix(args: Seq[String], message: Message)(implicit c: CacheSnapshot): Option[(String, Seq[String])]
}

/**
  * A simple [[AbstractCommandSettings]] that can be used when you know in
  * advance if commands need a mention at the start, and what prefixes are
  * valid.
  * @param needsMention If a mention should always be required.
  * @param prefixes All the valid prefixes for commands.
  */
case class CommandSettings(
    needsMention: Boolean,
    prefixes: Set[String]
) extends AbstractCommandSettings {

  override def needMention(message: Message)(implicit c: CacheSnapshot): Boolean = needsMention

  override def getPrefix(args: Seq[String], message: Message)(
      implicit c: CacheSnapshot
  ): Option[(String, Seq[String])] =
    prefixes
      .find(prefix => args.headOption.exists(_.contains(prefix)))
      .fproduct(prefix => args.head.drop(prefix.length) +: args.tail)
}
