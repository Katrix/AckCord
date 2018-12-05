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
package net.katsstuff.ackcord.examplecore.music

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.Timeout
import akka.{Done, NotUsed}
import cats.Monad
import cats.data.OptionT
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.commands.{CmdDescription, CmdFilter, CmdInfo, ParsedCmdFactory, ParsedCmdFlow}
import net.katsstuff.ackcord.data.{GuildId, TChannel, UserId, VoiceState}
import net.katsstuff.ackcord.examplecore.music.MusicHandler.{NextTrack, QueueUrl, StopMusic, TogglePause}
import net.katsstuff.ackcord.http.rest.CreateMessage
import net.katsstuff.ackcord.syntax._

class MusicCommands[F[_]: Streamable: Monad](guildId: GuildId, musicHandler: ActorRef)(
    implicit timeout: Timeout,
    ec: ExecutionContext
) {

  val QueueCmdFactory: ParsedCmdFactory[F, String, NotUsed] = ParsedCmdFactory.requestRunner(
    refiner = CmdInfo[F](prefix = "&", aliases = Seq("q", "queue"), filters = Seq(CmdFilter.InOneGuild(guildId))),
    run = implicit c =>
      (runner, cmd) => {
        import runner._
        for {
          guild   <- liftOptionT(guildId.resolve)
          channel <- optionPure(guild.tChannelById(cmd.msg.channelId))
          _ <- liftOptionT[Future, CreateMessage[NotUsed]] {
            OptionT(guild.voiceStateFor(UserId(cmd.msg.authorId)) match {
              case Some(VoiceState(_, Some(vChannelId), _, _, _, _, _, _, _, _)) =>
                (musicHandler ? QueueUrl(cmd.args, channel, vChannelId)).map(_ => None)
              case _ => Future.successful(Some(channel.sendMessage("Not in a voice channel")))
            })
          }
        } yield ()
    },
    description = Some(CmdDescription(name = "Queue music", description = "Set an url as the url to play"))
  )

  private def simpleCommand[A](aliases: Seq[String], description: CmdDescription, mapper: TChannel => A) =
    ParsedCmdFactory(
      refiner = CmdInfo[F](prefix = "&", aliases = aliases, filters = Seq(CmdFilter.InOneGuild(guildId))),
      sink = requests =>
        ParsedCmdFlow[F, NotUsed]
          .flatMapConcat { implicit c => cmd =>
            Streamable[F].optionToSource(cmd.msg.tGuildChannel[F](guildId)).map(mapper)
          }
          .ask[MusicHandler.CommandAck.type](requests.parallelism)(musicHandler)
          .toMat(Sink.ignore)(Keep.right),
      description = Some(description)
    )

  val StopCmdFactory: ParsedCmdFactory[F, NotUsed, Future[Done]] = simpleCommand(
    aliases = Seq("s", "stop"),
    mapper = StopMusic.apply,
    description = CmdDescription(name = "Stop music", description = "Stop music from playing, and leave the channel"),
  )

  val NextCmdFactory: ParsedCmdFactory[F, NotUsed, Future[Done]] = simpleCommand(
    aliases = Seq("n", "next"),
    mapper = NextTrack.apply,
    description = CmdDescription(name = "Next track", description = "Skip to the next track"),
  )

  val PauseCmdFactory: ParsedCmdFactory[F, NotUsed, Future[Done]] = simpleCommand(
    aliases = Seq("p", "pause"),
    mapper = TogglePause.apply,
    description = CmdDescription(name = "Pause/Play", description = "Toggle pause on the current player"),
  )
}
