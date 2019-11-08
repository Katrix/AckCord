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

package ackcord.newcommands

import akka.stream.scaladsl.Flow

/**
  * A constructed command execution.
  *
  * @tparam A The argument type of the command
  * @tparam Mat The materialized result of creating this command
  */
trait ComplexCommand[A, Mat] {

  def parser: MessageParser[A]

  def flow: Flow[CommandMessage[A], CommandError, Mat]
}

/**
  * A constructed command execution with a name.
  *
  * @tparam A The argument type of the command
  * @tparam Mat The materialized result of creating this command
  */
trait NamedComplexCommand[A, Mat] extends ComplexCommand[A, Mat] {

  /**
    * The prefix symbol to use for this command.
    */
  def symbol: String

  /**
    * The valid aliases of this command.
    */
  def aliases: Seq[String]

  /**
    * If this command requires a mention when invoking it.
    */
  def requiresMention: Boolean

  /**
    * If the aliases of this command should be matched with case sensitivity.
    */
  def caseSensitive: Boolean
}

/**
  * Represents non essential information about a command intended to be
  * displayed to an end user.
  *
  * @param name The display name of a command.
  * @param description The description of what a command does.
  * @param usage How to use the command. Does not include the name or prefix.
  * @param extra Extra stuff about the command that you yourself decide on.
  */
case class CommandDescription(
    name: String,
    description: String,
    usage: String = "",
    extra: Map[String, String] = Map.empty
)
