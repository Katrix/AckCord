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
import net.katsstuff.ackcord.commands.CmdParser.{ParseError, ParsedCommand}
import net.katsstuff.ackcord.commands.CmdRouter.Command
import net.katsstuff.ackcord.data.{CacheSnapshot, Message}
import net.katsstuff.ackcord.util.MessageParser

/**
  * A command handler that will parse a command, and send the parsed command
  * to some other actor
  * @param parser The parser to use
  * @param handlerProps The props of the actor that will handle the command
  * @tparam A The parser type
  */
class CmdParser[A](parser: MessageParser[A], handlerProps: Props) extends Actor {
  val handler: ActorRef = context.actorOf(handlerProps, "Handler")

  override def receive: Receive = {
    case Command(msg, args, c) =>
      implicit val cache: CacheSnapshot = c
      handler ! parser.parse(args).fold(s => ParseError(msg, s, c), t => ParsedCommand(msg, t._2, t._1, c))
    case other => handler ! other
  }
}
object CmdParser {
  def props[A](parser: MessageParser[A], handler: Props): Props = Props(new CmdParser(parser, handler))

  /**
    * Props for a parser that will spit out the command the list of arguments
    * untouched.
    * @param handler The command handler
    */
  def allStrings(handler: Props): Props = props(MessageParser.allStringsParser, handler)

  /**
    * Props for a parser that will only succeed if no arguments are passed to
    * it. Good for commands that don't take arguments.
    * @param handler The command handler
    */
  def noArgs(handler: Props): Props = props(MessageParser.notUsedParser, handler)

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
