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

import akka.actor.{Actor, ActorRef}
import net.katsstuff.ackcord.commands.CommandDispatcher.Command
import net.katsstuff.ackcord.commands.CommandParser.{ParseError, ParsedCommand}
import net.katsstuff.ackcord.data.{CacheSnapshot, Message}
import net.katsstuff.ackcord.util.MessageParser

class CommandParser[A](parser: MessageParser[A], handler: ActorRef) extends Actor {
  override def receive: Receive = {
    case Command(msg, args, c) =>
      implicit val cache: CacheSnapshot = c
      handler ! parser.parse(args).fold(s => ParseError(msg, s, c), t  => ParsedCommand(msg, t._2, t._1, c))
  }
}
object CommandParser {

  /**
    * Sent to the handler if the parser couldn't parse the message
    * @param msg The base message
    * @param error The error message
    * @param cache The current cache
    */
  case class ParseError(msg: Message, error: String, cache: CacheSnapshot)

  /**
    * Sent to the handler when the parser has parsed the message
    * @param msg The base message
    * @param args The parsed arguments
    * @param remaining The remaining arguments
    * @param cache The current cache
    * @tparam A The expected type for the parsed arguments
    */
  case class ParsedCommand[A](msg: Message, args: A, remaining: List[String], cache: CacheSnapshot)
}
