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
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import akka.stream.{ActorAttributes, Supervision}

object CmdStreams {

  /**
    * Parse messages into potential commands.
    * @param settings The settings which defines how messages should be parsed
    *                 into commands.
    * @param apiMessages A source of [[APIMessage]]s.
    */
  def cmdStreams[A](
      settings: AbstractCommandSettings,
      apiMessages: Source[APIMessage, A]
  )(implicit system: ActorSystem[Nothing]): (A, Source[RawCmdMessage, NotUsed]) = {
    apiMessages
      .collect {
        case APIMessage.MessageCreate(msg, c) =>
          implicit val cache: MemoryCacheSnapshot = c.current

          CmdHelper.isValidCommand(settings.needMention(msg), msg).map { args =>
            if (args == Nil) NoCmd(msg, c.current)
            else {
              settings
                .getPrefix(args, msg)
                .fold[RawCmdMessage](NoCmdPrefix(msg, args.head, args.tail, cache)) {
                  case (prefix, remaining) => RawCmd(msg, prefix, remaining.head, remaining.tail.toList, c.current)
                }
            }
          }
      }
      .mapConcat(_.toList)
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
      .addAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .run()
  }

}
