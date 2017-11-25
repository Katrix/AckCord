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
import java.time.temporal.ChronoUnit

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import net.katsstuff.ackcord.DiscordClient.ShutdownClient
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.requests.RESTRequests._
import net.katsstuff.ackcord.http.requests._
import net.katsstuff.ackcord.syntax._

object PingCmdFactory
    extends ParsedCmdFactory[NotUsed, NotUsed](
      category = ExampleCmdCategories.!,
      aliases = Seq("ping"),
      sink = (token, system, mat) =>
        //Completely manual
        Flow[ParsedCmd[NotUsed]]
          .map {
            case ParsedCmd(msg, _, _, c) =>
              implicit val cache: CacheSnapshot = c
              CreateMessage(msg.channelId, CreateMessageData("Pong"))
          }
          .via(RequestStreams.simpleRequestFlow(token)(system, mat))
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
          .via(RequestStreams.simpleRequestFlow(token)(system, mat))
          .to(Sink.ignore)
      },
      description = Some(
        CmdDescription(name = "Send file", description = "Make the bot send an embed with a file. Used for testing")
      )
    )

case class GetChannelInfo(guildId: GuildId, senderChannelId: ChannelId, c: CacheSnapshot)
object InfoChannelCmdFactory
    extends ParsedCmdFactory[GuildChannel, NotUsed](
      category = ExampleCmdCategories.!,
      aliases = Seq("infoChannel"),
      sink = (token, system, mat) => {
        //Using the context
        Flow[ParsedCmd[GuildChannel]]
          .map {
            case ParsedCmd(msg, channel, _, c) =>
              implicit val cache: CacheSnapshot = c
              GetChannel(channel.id, context = GetChannelInfo(channel.guildId, msg.channelId, c))
          }
          .via(RequestStreams.simpleRequestFlow(token)(system, mat))
          .mapConcat {
            case RequestResponse(res, GetChannelInfo(guildId, senderChannelId, c), _, _, _, _) =>
              implicit val cache: CacheSnapshot = c
              senderChannelId.tResolve(guildId).map(_.sendMessage(s"Info for ${res.name}:\n$res")).toList
            case error: FailedRequest[_] =>
              val GetChannelInfo(guildId, senderChannelId, c) = error.context
              implicit val cache: CacheSnapshot = c
              senderChannelId.tResolve(guildId).map(_.sendMessage("Error encountered")).toList
          }
          .via(RequestStreams.simpleRequestFlow(token)(system, mat))
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

object TimeDiffCmdFactory
    extends ParsedCmdFactory[NotUsed, NotUsed](
      category = ExampleCmdCategories.!,
      aliases = Seq("timeDiff"),
      sink = (token, system, mat) =>
        Flow[ParsedCmd[NotUsed]]
          .flatMapConcat {
            case ParsedCmd(msg, _, _, c) =>
              implicit val cache: CacheSnapshot = c

              //Using request dsl
              import net.katsstuff.ackcord.RequestDSL._
              val dsl = for {
                channel <- maybePure(msg.tChannel)
                sentMsg <- channel.sendMessage("Msg")
                between = ChronoUnit.MILLIS.between(msg.timestamp, sentMsg.timestamp)
                _ <- channel.sendMessage(s"$between msg between command and response")
              } yield ()

              dsl.toSource(RequestStreams.simpleRequestFlow(token)(system, mat))
          }
          .to(Sink.ignore),
      description = Some(
        CmdDescription(
          name = "Time diff",
          description = "Check the about of time between a command being used. And a response being sent."
        )
      )
    )

object RatelimitTestCmdFactory
    extends ParsedCmdFactory[Int, NotUsed](
      category = ExampleCmdCategories.!,
      aliases = Seq("ratelimitTest"),
      sink = (token, system, mat) =>
        Flow[ParsedCmd[Int]]
          .mapConcat {
            case ParsedCmd(msg, num, _, c) =>
              implicit val cache: CacheSnapshot = c
              msg.tChannel.toList.flatMap { ch =>
                (1 to num).map(num => ch.sendMessage(s"Msg$num"))
              }
          }
          .via(RequestStreams.simpleRequestFlow(token)(system, mat))
          .to(Sink.ignore),
      description = Some(
        CmdDescription(
          name = "Ratelimit test",
          description = "Send a bunch of messages at the same time to test rate limits.",
          usage = "<messages to send>"
        )
      )
    )

object ExampleErrorHandlers {
  import RESTRequests._
  def complain(
      token: String,
      allCmds: Map[CmdCategory, Set[String]]
  )(implicit system: ActorSystem, mat: Materializer): Sink[AllCmdMessages, NotUsed] =
    Flow[AllCmdMessages]
      .collect {
        case noCmd: NoCmd =>
          CreateMessage(noCmd.msg.channelId, CreateMessageData("No command specified"))
        case noCmdCat: NoCmdCategory =>
          CreateMessage(noCmdCat.msg.channelId, CreateMessageData("Unknown category"))
        case unknown: RawCmd if allCmds.get(unknown.category).forall(!_.contains(unknown.cmd)) =>
          CreateMessage(unknown.msg.channelId, CreateMessageData(s"No command named ${unknown.cmd} known"))
      }
      .via(RequestStreams.simpleRequestFlow(token)(system, mat))
      .to(Sink.ignore)
}
