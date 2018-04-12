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

import akka.stream.scaladsl.Source
import cats.Id
import net.katsstuff.ackcord._

object CoreCommands {

  /**
    * Create a new command handler using a cache.
    * @param needMention If this handler should require mentions before
    *                    the commands.
    * @param categories The categories this handler should know about.
    * @param cache The cache to use for subscribing to created messages.
    * @param requests A request helper object which will be passed to handlers.
    */
  def create(
      needMention: Boolean,
      categories: Set[CmdCategory],
      cache: Cache,
      requests: RequestHelper
  ): Commands[Id] = {
    import requests.mat
    Commands(CmdStreams.cmdStreams(needMention, categories, cache.subscribeAPI)._2, categories, requests)
  }

  /**
    * Create a new command handler using an [[APIMessage]] source.
    * @param needMention If this handler should require mentions before
    *                    the commands.
    * @param categories The categories this handler should know about.
    * @param apiMessages The source of [[APIMessage]]s.
    * @param requests A request helper object which will be passed to handlers.
    */
  def create[A](
      needMention: Boolean,
      categories: Set[CmdCategory],
      apiMessages: Source[APIMessage, A],
      requests: RequestHelper
  ): (A, Commands[Id]) = {
    import requests.mat
    val (materialized, streams) = CmdStreams.cmdStreams(needMention, categories, apiMessages)
    materialized -> Commands(streams, categories, requests)
  }
}
