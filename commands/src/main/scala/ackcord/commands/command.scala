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

import akka.stream.scaladsl.Flow

/**
  * A constructed command execution.
  *
  * @tparam A
  *   The argument type of the command
  * @tparam Mat
  *   The materialized result of creating this command
  */
case class ComplexCommand[A, Mat](
    parser: MessageParser[A],
    flow: Flow[CommandMessage[A], CommandError, Mat]
) {

  /**
    * Converts this command into a named command.
    * @param Parser
    *   The prefix parser to use
    */
  def toNamed(Parser: StructuredPrefixParser): NamedComplexCommand[A, Mat] =
    NamedComplexCommand(this, Parser)
}

/**
  * A constructed command execution with a name.
  *
  * @tparam A
  *   The argument type of the command
  * @tparam Mat
  *   The materialized result of creating this command
  */
case class NamedComplexCommand[A, Mat](
    command: ComplexCommand[A, Mat],
    prefixParser: StructuredPrefixParser
) {

  def toDescribed(
      description: CommandDescription
  ): NamedDescribedComplexCommand[A, Mat] =
    NamedDescribedComplexCommand(command, prefixParser, description)
}

/**
  * A constructed command execution with a name and a description.
  *
  * @tparam A
  *   The argument type of the command
  * @tparam Mat
  *   The materialized result of creating this command
  */
case class NamedDescribedComplexCommand[A, Mat](
    command: ComplexCommand[A, Mat],
    prefixParser: StructuredPrefixParser,
    description: CommandDescription
)

/**
  * Represents non essential information about a command intended to be
  * displayed to an end user.
  *
  * @param name
  *   The display name of a command.
  * @param description
  *   The description of what a command does.
  * @param usage
  *   How to use the command. Does not include the name or prefix.
  * @param extra
  *   Extra stuff about the command that you yourself decide on.
  */
case class CommandDescription(
    name: String,
    description: String,
    usage: String = "",
    extra: Map[String, String] = Map.empty
)
