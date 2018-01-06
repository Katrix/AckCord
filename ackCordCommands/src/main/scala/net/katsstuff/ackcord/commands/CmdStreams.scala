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

import java.util.Locale

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, BroadcastHub, Flow, GraphDSL, Sink, Source}
import akka.stream.{FlowShape, Materializer}
import net.katsstuff.ackcord.APIMessage
import net.katsstuff.ackcord.data.{CacheSnapshot, Message, User}
import net.katsstuff.ackcord.http.RawMessage
import net.katsstuff.ackcord.http.requests.{Request, RequestHelper}
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.util.MessageParser

object CmdStreams {

  /**
    * Parse messages into potential commands.
    * @param needMention If this handler should require mentions before
    *                    the commands.
    * @param categories The categories this handler should know about.
    * @param apiMessages A source of [[APIMessage]]s.
    */
  def cmdStreams(needMention: Boolean, categories: Set[CmdCategory], apiMessages: Source[APIMessage, NotUsed])(
      implicit system: ActorSystem,
      mat: Materializer
  ): Source[RawCmdMessage, NotUsed] = {
    apiMessages
      .collect { case msg: APIMessage.MessageCreate => msg }
      .mapConcat {
        case APIMessage.MessageCreate(msg, c) =>
          implicit val cache: CacheSnapshot = c.current
          val res = isValidCommand(needMention, msg).map { args =>
            if (args == Nil) NoCmd(msg, c.current)
            else {
              val lowercaseCommand = args.head.toLowerCase(Locale.ROOT)
              categories
                .find(cat => lowercaseCommand.startsWith(cat.prefix))
                .fold[RawCmdMessage](NoCmdCategory(msg, lowercaseCommand, args.tail, cache)) { cat =>
                  val withoutPrefix = lowercaseCommand.substring(cat.prefix.length)
                  RawCmd(msg, cat, withoutPrefix, args.tail, c.current)
                }
            }
          }

          res.toList
      }
      .runWith(BroadcastHub.sink(bufferSize = 256))
  }

  /**
    * Handle command errors.
    */
  def handleErrors[A <: AllCmdMessages](requests: RequestHelper): Flow[A, A, NotUsed] = {

    val graph = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val in          = builder.add(Flow[A])
      val broadcast   = builder.add(Broadcast[A](2))
      val mkWrapper   = builder.add(sendCmdErrorMsg[A])
      val requestFlow = builder.add(requests.flow[RawMessage, NotUsed])

      // format: OFF
      
      in ~> broadcast
            broadcast.out(0) ~> mkWrapper ~> requestFlow ~> Sink.ignore
            
      // format: ON

      FlowShape(in.in, broadcast.out(1))
    }

    Flow.fromGraph(graph)
  }

  /**
    * Handle all the errors for a parsed command.
    */
  def handleErrorsParsed[A](requests: RequestHelper): Flow[ParsedCmdMessage[A], ParsedCmd[A], NotUsed] =
    handleErrors[ParsedCmdMessage[A]](requests).collect {
      case msg: ParsedCmd[A] => msg
    }

  /**
    * Handle all the errors for a unparsed command.
    */
  def handleErrorsUnparsed(requests: RequestHelper): Flow[CmdMessage, Cmd, NotUsed] =
    handleErrors[CmdMessage](requests).collect {
      case msg: Cmd => msg
    }

  /**
    * A flow which will send error messages as messages.
    */
  def sendCmdErrorMsg[A <: AllCmdMessages]: Flow[A, Request[RawMessage, NotUsed], NotUsed] =
    Flow[A]
      .collect {
        case filtered: FilteredCmd =>
          val errors = filtered.failedFilters.flatMap(_.errorMessage(filtered.cmd.msg)(filtered.cmd.c))
          if (errors.nonEmpty) {
            filtered.cmd.msg.channelId.tResolve(filtered.cmd.c).map(_.sendMessage(errors.mkString("\n")))
          } else None
        case parseError: CmdParseError =>
          parseError.msg.channelId.tResolve(parseError.cache).map(_.sendMessage(parseError.error))
      }
      .mapConcat(_.toList)

  /**
    * Check if a message is a valid command.
    */
  def isValidCommand(needMention: Boolean, msg: Message)(implicit c: CacheSnapshot): Option[List[String]] = {
    if (needMention) {
      //We do a quick check first before parsing the message
      val quickCheck = if (msg.mentions.contains(c.botUser.id)) Some(msg.content.split(" ").toList) else None

      quickCheck.flatMap { args =>
        MessageParser[User]
          .parse(args)
          .toOption
          .flatMap {
            case (remaining, user) =>
              if (user.id == c.botUser.id) Some(remaining)
              else None
          }
      }
    } else Some(msg.content.split(" ").toList)
  }
}
