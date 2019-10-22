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

import ackcord.CacheSnapshot
import ackcord.data.raw.RawMessage
import ackcord.data.{Message, User}
import ackcord.requests.{CreateMessage, Request, RequestHelper}
import ackcord.syntax._
import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL}

object CmdHelper {

  /**
    * Handle command errors.
    */
  def addErrorHandlingGraph[A <: AllCmdMessages](
      requests: RequestHelper
  ): Flow[A, A, NotUsed] = {
    val graph = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val in          = builder.add(Flow[A])
      val broadcast   = builder.add(Broadcast[A](2))
      val mkWrapper   = builder.add(sendCmdErrorMsg[A])
      val requestSink = builder.add(requests.sinkIgnore[RawMessage, NotUsed])

      // format: OFF

      in ~> broadcast
            broadcast.out(0) ~> mkWrapper ~> requestSink

      // format: ON

      FlowShape(in.in, broadcast.out(1))
    }

    Flow.fromGraph(graph)
  }

  /**
    * Handle all the errors for a parsed command.
    */
  def addErrorHandlingParsed[A](
      requests: RequestHelper
  ): Flow[ParsedCmdMessage[A], ParsedCmd[A], NotUsed] =
    addErrorHandlingGraph[ParsedCmdMessage[A]](requests).collect {
      case msg: ParsedCmd[A] => msg
    }

  /**
    * Handle all the errors for a unparsed command.
    */
  def addErrorHandlingUnparsed(requests: RequestHelper): Flow[CmdMessage, Cmd, NotUsed] =
    addErrorHandlingGraph[CmdMessage](requests).collect {
      case msg: Cmd => msg
    }

  /**
    * A flow which will send error messages as messages.
    */
  def sendCmdErrorMsg[A <: AllCmdMessages]: Flow[A, Request[RawMessage, NotUsed], NotUsed] =
    Flow[A]
      .collect {
        case filtered: FilteredCmd =>
          implicit val c: CacheSnapshot = filtered.cmd.c

          val errors = filtered.failedFilters.toList.flatMap(_.errorMessage(filtered.cmd.msg))

          if (errors.nonEmpty) {
            filtered.cmd.msg.channelId.tResolve(filtered.cmd.c).map(_.sendMessage(errors.mkString("\n")))
          } else None: Option[CreateMessage[NotUsed]]
        case parseError: CmdParseError =>
          parseError.msg.channelId.tResolve(parseError.cache).map(_.sendMessage(parseError.error))
        case error: GenericCmdError =>
          error.cmd.msg.channelId.tResolve(error.cmd.c).map(_.sendMessage(error.error))
      }
      .mapConcat(_.toList)

  /**
    * Check if a message is a valid command, and if it is, returns the arguments of the command.
    */
  def isValidCommand[F[_]](needMention: Boolean, msg: Message)(
      implicit c: CacheSnapshot
  ): Option[List[String]] = {
    if (needMention) {
      val botUser = c.botUser
      //We do a quick check first before parsing the message
      val quickCheck = if (msg.mentions.contains(botUser.id)) Some(msg.content.split(" ").toList) else None

      quickCheck.flatMap { args =>
        MessageParser
          .parseEither(args, MessageParser[User])
          .toOption
          .flatMap {
            case (remaining, user) if user.id == botUser.id => Some(remaining)
            case (_, _)                                     => None
          }
      }
    } else Some(msg.content.split(" ").toList)
  }
}
