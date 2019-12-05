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

import ackcord._
import akka.stream.scaladsl.Source

object CoreCommands {

  /**
    * Create a new command handler using a cache.
    * @param settings The settings this handler should use.
    * @param cache The cache to use for subscribing to created messages.
    * @param requests A request helper object which will be passed to handlers.
    */
  def create(
      settings: AbstractCommandSettings,
      cache: Cache,
      requests: RequestHelper
  ): Commands = {
    import requests.system
    Commands(CmdStreams.cmdStreams(settings, cache.subscribeAPI)._2, requests)
  }

  /**
    * Create a new command handler using an [[APIMessage]] source.
    * @param settings The settings this handler should use.
    * @param apiMessages The source of [[APIMessage]]s.
    * @param requests A request helper object which will be passed to handlers.
    */
  def create[A](
      settings: AbstractCommandSettings,
      apiMessages: Source[APIMessage, A],
      requests: RequestHelper
  ): (A, Commands) = {
    import requests.system
    val (materialized, streams) = CmdStreams.cmdStreams(settings, apiMessages)
    materialized -> Commands(streams, requests)
  }
}
