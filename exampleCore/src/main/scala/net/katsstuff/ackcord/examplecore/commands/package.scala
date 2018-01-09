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
package net.katsstuff.ackcord.examplecore

import java.nio.file.Paths
import java.time.temporal.ChronoUnit

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.requests.RESTRequests._
import net.katsstuff.ackcord.http.requests.{FailedRequest, RequestHelper, RequestResponse}
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.{DiscordShard, RequestDSL}

package object commands {

  val PingCmdFactory: ParsedCmdFactory[NotUsed, NotUsed] = ParsedCmdFactory[NotUsed, NotUsed](
    category = ExampleCmdCategories.!,
    aliases = Seq("ping"),
    sink = requests =>
      //Completely manual
      ParsedCmdFlow[NotUsed]
        .map(_ => cmd => CreateMessage.mkContent(cmd.msg.channelId, "Pong"))
        .to(requests.sinkIgnore),
    description =
      Some(CmdDescription(name = "Ping", description = "Ping this bot and get a response. Used for testing"))
  )

  val SendFileCmdFactory: ParsedCmdFactory[NotUsed, NotUsed] = ParsedCmdFactory[NotUsed, NotUsed](
    category = ExampleCmdCategories.!,
    aliases = Seq("sendFile"),
    sink = requests => {
      val embed = OutgoingEmbed(
        title = Some("This is an embed"),
        description = Some("This embed is sent together with a file"),
        fields = Seq(EmbedField("FileName", "theFile.txt"))
      )

      //Use channel to construct request
      ParsedCmdFlow[NotUsed]
        .mapConcat { implicit c => cmd =>
          cmd.msg.channelId.tResolve.map { tChannel =>
            tChannel.sendMessage("Here is the file", files = Seq(Paths.get("theFile.txt")), embed = Some(embed))
          }.toList
        }
        .to(requests.sinkIgnore)
    },
    description =
      Some(CmdDescription(name = "Send file", description = "Make the bot send an embed with a file. Used for testing"))
  )

  case class GetChannelInfo(guildId: GuildId, senderChannelId: ChannelId, c: CacheSnapshot)
  val InfoChannelCmdFactory: ParsedCmdFactory[GuildChannel, NotUsed] = ParsedCmdFactory[GuildChannel, NotUsed](
    category = ExampleCmdCategories.!,
    aliases = Seq("infoChannel"),
    sink = requests => {
      //Using the context
      ParsedCmdFlow[GuildChannel]
        .map { implicit c => cmd =>
          implicit val cache: CacheSnapshot = c
          GetChannel(cmd.args.id, context = GetChannelInfo(cmd.args.guildId, cmd.msg.channelId, c))
        }
        .via(requests.flow)
        .mapConcat {
          case RequestResponse(res, GetChannelInfo(guildId, senderChannelId, c), _, _, _, _) =>
            implicit val cache: CacheSnapshot = c
            senderChannelId.tResolve(guildId).map(_.sendMessage(s"Info for ${res.name}:\n$res")).toList
          case error: FailedRequest[_] =>
            val GetChannelInfo(guildId, senderChannelId, c) = error.context
            implicit val cache: CacheSnapshot = c
            senderChannelId.tResolve(guildId).map(_.sendMessage("Error encountered")).toList
        }
        .to(requests.sinkIgnore)
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
        main ! DiscordShard.StopShard
        context.watch(main)
      case Terminated(_) =>
        log.info("Everything shut down")
        context.system.terminate()
    }
  }
  object KillCmd {
    def props(main: ActorRef): Props = Props(new KillCmd(main))
  }

  def KillCmdFactory(mainActor: ActorRef): ParsedCmdFactory[NotUsed, NotUsed] = ParsedCmdFactory[NotUsed, NotUsed](
    category = ExampleCmdCategories.!,
    aliases = Seq("kill", "die"),
    //We use system.actorOf to keep the actor alive when this actor shuts down
    sink = requests => Sink.actorRef(requests.system.actorOf(KillCmd.props(mainActor), "KillCmd"), PoisonPill),
    description = Some(CmdDescription(name = "Kill bot", description = "Shut down this bot"))
  )

  val TimeDiffCmdFactory: ParsedCmdFactory[NotUsed, NotUsed] = ParsedCmdFactory.requestDSL[NotUsed](
    category = ExampleCmdCategories.!,
    aliases = Seq("timeDiff"),
    flow = ParsedCmdFlow[NotUsed].map { implicit c => cmd =>
      //Using request dsl
      import RequestDSL._
      for {
        channel <- maybePure(cmd.msg.channelId.tResolve)
        sentMsg <- channel.sendMessage("Msg")
        between = ChronoUnit.MILLIS.between(cmd.msg.timestamp, sentMsg.timestamp)
        _ <- channel.sendMessage(s"$between ms between command and response")
      } yield ()
    },
    description = Some(
      CmdDescription(
        name = "Time diff",
        description = "Check the about of time between a command being used. And a response being sent."
      )
    )
  )

  val RatelimitTestCmdFactory: ParsedCmdFactory[Int, NotUsed] = ParsedCmdFactory[Int, NotUsed](
    category = ExampleCmdCategories.!,
    aliases = Seq("ratelimitTest"),
    sink = requests =>
      ParsedCmdFlow[Int]
        .mapConcat { implicit c => cmd =>
          cmd.msg.channelId.tResolve.toList.flatMap { ch =>
            (1 to cmd.args).map(num => ch.sendMessage(s"Msg$num"))
          }
        }
        .to(requests.sinkIgnore),
    description = Some(
      CmdDescription(
        name = "Ratelimit test",
        description = "Send a bunch of messages at the same time to test rate limits.",
        usage = "<messages to send>"
      )
    )
  )

  def ComplainErrorHandler(
      requests: RequestHelper,
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
      .to(requests.sinkIgnore)
}
