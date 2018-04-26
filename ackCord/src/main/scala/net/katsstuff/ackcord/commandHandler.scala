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
package net.katsstuff.ackcord

import scala.language.higherKinds

import cats.Monad
import net.katsstuff.ackcord.commands.{CmdCategory, CmdDescription, CmdFilter}
import net.katsstuff.ackcord.data.Message

/**
  * A handler for a specific command.
  *
  * @tparam A The parameter type.
  */
abstract class CommandHandler[A](
    val category: CmdCategory,
    val aliases: Seq[String],
    val filters: Seq[CmdFilter] = Nil,
    val description: Option[CmdDescription] = None
) {

  /**
    * Called whenever the command for this handler is received.
    * @param c A cache snapshot associated with the command.
    */
  def handle[F[_]: Monad: Streamable](msg: Message, args: A, remaining: List[String])(
      implicit c: CacheSnapshot[F]
  ): Unit
}

/**
  * A handler for a specific command that runs a [[RequestDSL]] when the command is received.
  *
  * @tparam A The parameter type.
  */
abstract class CommandHandlerDSL[A](
    val category: CmdCategory,
    val aliases: Seq[String],
    val filters: Seq[CmdFilter] = Nil,
    val description: Option[CmdDescription] = None
) {

  /**
    * Runs the [[RequestDSL]] whenever the command for this handler is received.
    * @param c A cache snapshot associated with the command.
    */
  def handle[F[_]: Monad: Streamable](msg: Message, args: A, remaining: List[String])(
      implicit c: CacheSnapshot[F]
  ): RequestDSL[Unit]
}
