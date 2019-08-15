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
package ackcord.examplecore

import java.nio.file.Paths
import java.time.temporal.ChronoUnit

import scala.concurrent.Future

import ackcord._
import ackcord.commands._
import ackcord.data._
import ackcord.data.raw.{RawChannel, RawMessage}
import ackcord.requests._
import ackcord.syntax._
import akka.actor.{ActorRef, CoordinatedShutdown}
import akka.stream.scaladsl.Sink
import akka.{Done, NotUsed}

object GenericCommands {

  val PingCmdFactory: ParsedCmdFactory[NotUsed, NotUsed] = ParsedCmdFactory[NotUsed, NotUsed](
    refiner = CmdInfo(prefix = "!", aliases = Seq("ping")),
    sink = requests =>
      //Completely manual
      ParsedCmdFlow[NotUsed]
        .map(_ => cmd => CreateMessage.mkContent(cmd.msg.channelId, "Pong"))
        .to(requests.sinkIgnore),
    description =
      Some(CmdDescription(name = "Ping", description = "Ping this bot and get a response. Used for testing"))
  )

  val SendFileCmdFactory: ParsedCmdFactory[NotUsed, NotUsed] = ParsedCmdFactory[NotUsed, NotUsed](
    refiner = CmdInfo(prefix = "!", aliases = Seq("sendFile")),
    sink = requests => {
      val embed = OutgoingEmbed(
        title = Some("This is an embed"),
        description = Some("This embed is sent together with a file"),
        fields = Seq(EmbedField("FileName", "theFile.txt"))
      )

      //Using mapConcat for optional values
      ParsedCmdFlow[NotUsed]
        .mapConcat(implicit c => cmd => cmd.msg.channelId.tResolve.toList)
        .map(_.sendMessage("Here is the file", files = Seq(Paths.get("theFile.txt")), embed = Some(embed)))
        .to(requests.sinkIgnore)
    },
    description =
      Some(CmdDescription(name = "Send file", description = "Make the bot send an embed with a file. Used for testing"))
  )

  val InfoChannelCmdFactory: ParsedCmdFactory[GuildChannel, NotUsed] = ParsedCmdFactory[GuildChannel, NotUsed](
    refiner = CmdInfo(prefix = "!", aliases = Seq("infoChannel")),
    sink = requests => {
      //Using the context
      ParsedCmdFlow[GuildChannel]
        .map { implicit c => cmd =>
          GetChannel(cmd.args.id, context = GetChannelInfo(cmd.args.guildId, cmd.msg.channelId, c))
        }
        .via(requests.flow)
        .mapConcat { answer =>
          val ctx                           = answer.context
          implicit val cache: CacheSnapshot = ctx.c
          val content = answer match {
            case response: RequestResponse[RawChannel, GetChannelInfo] =>
              val data = response.data
              s"Info for ${data.name}:\n$data"
            case _: FailedRequest[_] => "Error encountered"
          }

          ctx.senderChannelId.tResolve(ctx.guildId).map(_.sendMessage(content)).toList
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

  def KillCmdFactory(mainActor: ActorRef): ParsedCmdFactory[NotUsed, NotUsed] =
    ParsedCmdFactory[NotUsed, NotUsed](
      refiner = CmdInfo(prefix = "!", aliases = Seq("kill", "die")),
      //We use system.actorOf to keep the actor alive when this actor shuts down
      sink = requests =>
        Sink
          .foreach[ParsedCmd[NotUsed]] { _ =>
            CoordinatedShutdown(requests.system).run(CoordinatedShutdown.JvmExitReason)
          }
          .mapMaterializedValue(_ => NotUsed),
      description = Some(CmdDescription(name = "Kill bot", description = "Shut down this bot"))
    )

  val TimeDiffCmdFactory: ParsedCmdFactory[NotUsed, NotUsed] = ParsedCmdFactory.requestRunner[NotUsed](
    refiner = CmdInfo(prefix = "!", aliases = Seq("timeDiff")),
    run = implicit c =>
      (runner, cmd) => {
        import runner._
        for {
          channel <- optionPure(cmd.msg.channelId.tResolve)
          sentMsg <- run(channel.sendMessage("Msg"))
          time = ChronoUnit.MILLIS.between(cmd.msg.timestamp, sentMsg.timestamp)
          _ <- run(channel.sendMessage(s"$time ms between command and response"))
        } yield ()
      },
    description = Some(
      CmdDescription(
        name = "Time diff",
        description = "Check the about of time between a command being used, and a response being sent."
      )
    )
  )

  def RatelimitTestCmdFactory(
      name: String,
      aliases: Seq[String],
      sink: Sink[Request[RawMessage, NotUsed], Future[Done]]
  ): ParsedCmdFactory[Int, NotUsed] =
    ParsedCmdFactory[Int, NotUsed](
      refiner = CmdInfo(prefix = "!", aliases = aliases),
      sink = _ =>
        ParsedCmdFlow[Int]
          .mapConcat { implicit c => cmd =>
            cmd.msg.channelId.tResolve.map(_ -> cmd.args).toList
          }
          .mapConcat { case (channel, args) => List.tabulate(args)(i => channel.sendMessage(s"Msg$i")) }
          .to(sink),
      description = Some(
        CmdDescription(
          name = name,
          description = "Send a bunch of messages at the same time to test rate limits.",
          usage = "<messages to send>"
        )
      )
    )
}
