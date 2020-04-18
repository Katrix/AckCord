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
package ackcord.examplecore.music

import scala.concurrent.{ExecutionContext, Future}

import ackcord._
import ackcord.oldcommands._
import ackcord.data.{GuildId, TextChannel, UserId}
import ackcord.examplecore.music.MusicHandler.{NextTrack, QueueUrl, StopMusic, TogglePause}
import ackcord.requests.CreateMessage
import ackcord.syntax._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.Timeout
import akka.{Done, NotUsed}
import cats.data.OptionT

class MusicCommands(guildId: GuildId, musicHandler: ActorRef[MusicHandler.Command])(
    implicit timeout: Timeout,
    ec: ExecutionContext,
    system: ActorSystem[Nothing]
) {

  val QueueCmdFactory: ParsedCmdFactory[String, NotUsed] = ParsedCmdFactory.requestRunner(
    refiner = CmdInfo(prefix = "&", aliases = Seq("q", "queue"), filters = Seq(CmdFilter.InOneGuild(guildId))),
    run = implicit c =>
      (runner, cmd) => {
        import runner._
        for {
          guild   <- optionPure(guildId.resolve)
          channel <- optionPure(guild.textChannelById(cmd.msg.channelId))
          _ <- liftOptionT[Future, CreateMessage] {
            OptionT(guild.voiceStateFor(UserId(cmd.msg.authorId)) match {
              case Some(vs) if vs.channelId.isDefined =>
                musicHandler
                  .ask[MusicHandler.CommandAck.type](replyTo => QueueUrl(cmd.args, channel, vs.channelId.get, replyTo))
                  .map(_ => None)
              case _ => Future.successful(Some(channel.sendMessage("Not in a voice channel")))
            })
          }
        } yield ()
      },
    description = Some(CmdDescription(name = "Queue music", description = "Set an url as the url to play"))
  )

  private def simpleCommand(
      aliases: Seq[String],
      description: CmdDescription,
      mapper: (TextChannel, ActorRef[MusicHandler.CommandAck.type]) => MusicHandler.MusicHandlerEvents
  ): ParsedCmdFactory[NotUsed, Future[Done]] =
    ParsedCmdFactory[NotUsed, Future[Done]](
      refiner = CmdInfo(prefix = "&", aliases = aliases, filters = Seq(CmdFilter.InOneGuild(guildId))),
      sink = requests =>
        ParsedCmdFlow[NotUsed]
          .mapConcat(implicit c => cmd => cmd.msg.textGuildChannel(guildId).toList)
          .via(ActorFlow.ask(requests.parallelism)(musicHandler)(mapper))
          .toMat(Sink.ignore)(Keep.right),
      description = Some(description)
    )

  val StopCmdFactory: ParsedCmdFactory[NotUsed, Future[Done]] = simpleCommand(
    aliases = Seq("s", "stop"),
    mapper = StopMusic.apply,
    description = CmdDescription(name = "Stop music", description = "Stop music from playing, and leave the channel")
  )

  val NextCmdFactory: ParsedCmdFactory[NotUsed, Future[Done]] = simpleCommand(
    aliases = Seq("n", "next"),
    mapper = NextTrack.apply,
    description = CmdDescription(name = "Next track", description = "Skip to the next track")
  )

  val PauseCmdFactory: ParsedCmdFactory[NotUsed, Future[Done]] = simpleCommand(
    aliases = Seq("p", "pause"),
    mapper = TogglePause.apply,
    description = CmdDescription(name = "Pause/Play", description = "Toggle pause on the current player")
  )
}
