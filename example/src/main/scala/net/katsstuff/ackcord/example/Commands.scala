/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
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
package net.katsstuff.ackcord.example

import java.nio.file.Paths

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import net.katsstuff.ackcord.DiscordClient.ShutdownClient
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.requests._
import net.katsstuff.ackcord.http.{RawChannel, RawMessage}
import net.katsstuff.ackcord.syntax._

object PingCmdFactory
    extends ParsedCmdFactory[NotUsed, NotUsed](
      category = ExampleCmdCategories.!,
      aliases = Seq("ping"),
      sink = (token, system, mat) =>
        Flow[ParsedCmd[NotUsed]]
          .map {
            case ParsedCmd(msg, _, _, c) =>
              implicit val cache: CacheSnapshot = c
              RequestWrapper(RESTRequests.CreateMessage(msg.channelId, RESTRequests.CreateMessageData("Pong")))
          }
          .via(RequestStreams.simpleRequestFlow[RawMessage, NotUsed](token)(system, mat))
          .to(Sink.ignore),
      description =
        Some(CmdDescription(name = "Ping", description = "Ping this bot and get a response. Used for testing"))
    )

object SendFileCmdFactory
    extends ParsedCmdFactory[NotUsed, NotUsed](
      category = ExampleCmdCategories.!,
      aliases = Seq("sendFile"),
      sink = (token, system, mat) => {
        val embed = OutgoingEmbed(
          title = Some("This is an embed"),
          description = Some("This embed is sent together with a file"),
          fields = Seq(EmbedField("FileName", "theFile.txt"))
        )

        Flow[ParsedCmd[NotUsed]]
          .mapConcat {
            case ParsedCmd(msg, _, _, c) =>
              implicit val cache: CacheSnapshot = c
              msg.tChannel.map { tChannel =>
                //Use channel to construct request
                tChannel.sendMessage("Here is the file", files = Seq(Paths.get("theFile.txt")), embed = Some(embed))
              }.toList
          }
          .via(RequestStreams.simpleRequestFlow[RawMessage, NotUsed](token)(system, mat))
          .to(Sink.ignore)
      },
      description = Some(
        CmdDescription(name = "Send file", description = "Make the bot send an embed with a file. Used for testing")
      )
    )

case class GetChannelInfo(guildId: GuildId, requestedChannelId: ChannelId, senderChannelId: ChannelId, c: CacheSnapshot)
object InfoChannelCmdFactory
    extends ParsedCmdFactory[GuildChannel, NotUsed](
      category = ExampleCmdCategories.!,
      aliases = Seq("infoChannel"),
      sink = (token, system, mat) => {
        Flow[ParsedCmd[GuildChannel]]
          .map {
            case ParsedCmd(msg, channel, _, c) =>
              implicit val cache: CacheSnapshot = c
              RequestWrapper(
                RESTRequests.GetChannel(channel.id),
                GetChannelInfo(channel.guildId, channel.id, msg.channelId, c)
              )
          }
          .via(RequestStreams.simpleRequestFlow[RawChannel, GetChannelInfo](token)(system, mat))
          .mapConcat {
            case RequestResponse(res, GetChannelInfo(guildId, requestedChannelId, senderChannelId, c), _, _, _) =>
              implicit val cache: CacheSnapshot = c
              requestedChannelId
                .guildResolve(guildId)
                .map(_.name)
                .flatMap { name =>
                  senderChannelId.guildResolve(guildId).collect {
                    case channel: TGuildChannel => channel.sendMessage(s"Info for $name:\n$res")
                  }
                }
                .toList
            case error: RequestFailed[_, _] =>
              val GetChannelInfo(guildId, _, senderChannelId, c) = error.context
              implicit val cache: CacheSnapshot = c
              senderChannelId.tResolve(guildId).map(_.sendMessage("Error encountered")).toList
          }
          .via(RequestStreams.simpleRequestFlow[RawMessage, NotUsed](token)(system, mat))
          .to(Sink.ignore)
      },
      description = Some(
        CmdDescription(
          name = "Channel info",
          description = "Make the bot fetch information about a text channel from Discord. Used for testing"
        )
      )
    )

class KillCmd(main: ActorRef) extends Actor with ActorLogging {

  override def receive: Receive = {
    case ParsedCmd(_, _, _, _) =>
      log.info("Received shutdown command")
      main ! ShutdownClient
      context.watch(main)
    case Terminated(_) =>
      log.info("Everything shut down")
      context.system.terminate()
  }
}
object KillCmd {
  def props(main: ActorRef): Props = Props(new KillCmd(main))
}

class KillCmdFactory(actorCmd: ActorRef)
    extends ParsedCmdFactory[NotUsed, NotUsed](
      category = ExampleCmdCategories.!,
      aliases = Seq("kill", "die"),
      sink = (_, _, _) => Sink.actorRef(actorCmd, PoisonPill),
      description = Some(CmdDescription(name = "Kill bot", description = "Shut down this bot"))
    )
object KillCmdFactory {
  def apply(actorCmd: ActorRef): KillCmdFactory = new KillCmdFactory(actorCmd)
}

object ExampleErrorHandlers {
  import RESTRequests._
  def complain(
      token: String,
      allCmds: Map[CmdCategory, Set[String]]
  )(implicit system: ActorSystem, mat: Materializer): Sink[AllCmdMessages, NotUsed] =
    Flow[AllCmdMessages]
      .collect {
        case noCmd: NoCmd =>
          RequestWrapper(CreateMessage(noCmd.msg.channelId, CreateMessageData("No command specified")))
        case noCmdCat: NoCmdCategory =>
          RequestWrapper(CreateMessage(noCmdCat.msg.channelId, CreateMessageData("Unknown category")))
        case unknown: RawCmd if allCmds.get(unknown.category).forall(!_.contains(unknown.cmd)) =>
          RequestWrapper(
            CreateMessage(unknown.msg.channelId, CreateMessageData(s"No command named ${unknown.cmd} known"))
          )
      }
      .via(RequestStreams.simpleRequestFlow[RawMessage, NotUsed](token)(system, mat))
      .to(Sink.ignore)
}
