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

import java.util.Locale

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import cats.Id
import net.katsstuff.ackcord.{APIMessage, MemoryCacheSnapshot}

object CmdStreams {

  /**
    * Parse messages into potential commands.
    * @param needMention If this handler should require mentions before
    *                    the commands.
    * @param categories The categories this handler should know about.
    * @param apiMessages A source of [[APIMessage]]s.
    */
  def cmdStreams[A](
      needMention: Boolean,
      categories: Set[CmdCategory],
      apiMessages: Source[APIMessage, A]
  )(implicit mat: Materializer): (A, Source[RawCmdMessage[Id], NotUsed]) = {
    apiMessages
      .collect {
        case APIMessage.MessageCreate(msg, c) =>
          implicit val cache: MemoryCacheSnapshot = c.current

          CmdHelper.isValidCommand(needMention, msg).value.map { args =>
            if (args == Nil) NoCmd(msg, c.current)
            else {
              val lowercaseCommand = args.head.toLowerCase(Locale.ROOT)

              categories
                .find(cat => lowercaseCommand.startsWith(cat.prefix))
                .fold[RawCmdMessage[Id]](NoCmdCategory(msg, lowercaseCommand, args.tail, cache)) { cat =>
                val withoutPrefix = lowercaseCommand.substring(cat.prefix.length)
                RawCmd(msg, cat, withoutPrefix, args.tail, c.current)
              }
            }
          }
      }
      .mapConcat(_.toList)
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
      .run()
  }

}
