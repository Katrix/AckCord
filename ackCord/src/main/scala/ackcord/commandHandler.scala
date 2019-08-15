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
package ackcord

import scala.language.higherKinds

import ackcord.commands.{CmdDescription, CmdRefiner, RawCmd}
import ackcord.data.Message

/**
  * A handler for all raw commands.
  */
abstract class RawCommandHandler[G[_]] {

  /**
    * Called whenever a raw command is encountered.
    * @param rawCmd The command object
    * @param c The cache snapshot for the command.
    */
  def handle(rawCmd: RawCmd)(implicit c: CacheSnapshot): G[Unit]
}

/**
  * A handler for a specific command.
  *
  * @tparam A The parameter type.
  */
abstract class CommandHandler[G[_], A](
    val refiner: CmdRefiner,
    val description: Option[CmdDescription] = None
) {

  /**
    * Called whenever the command for this handler is received.
    * @param c A cache snapshot associated with the command.
    */
  def handle(msg: Message, args: A, remaining: List[String])(implicit c: CacheSnapshot): G[Unit]
}
