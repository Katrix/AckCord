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
package net.katsstuff.ackcord.example.music

import scala.concurrent.Future

import akka.actor.ActorRef
import akka.stream.scaladsl.{Keep, Sink}
import akka.{Done, NotUsed}
import net.katsstuff.ackcord.commands.{CmdDescription, CmdFilter, ParsedCmdFactory, ParsedCmdFlow}
import net.katsstuff.ackcord.data.{GuildId, UserId, VoiceState}
import net.katsstuff.ackcord.example.ExampleCmdCategories
import net.katsstuff.ackcord.example.music.MusicHandler.{NextTrack, QueueUrl, StopMusic, TogglePause}
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.RequestDSL

class commands(guildId: GuildId, musicHandler: ActorRef) {

  val QueueCmdFactory: ParsedCmdFactory[String, NotUsed] =
    ParsedCmdFactory
      .requestDSL[String](
        category = ExampleCmdCategories.&,
        aliases = Seq("q", "queue"),
        flow = {
          ParsedCmdFlow[String]
            .map {
              implicit c => cmd =>
                import RequestDSL._
                for {
                  guild   <- maybePure(guildId.resolve)
                  channel <- maybePure(guild.tChannelById(cmd.msg.channelId))
                  _ <- maybeRequest {
                    guild.voiceStateFor(UserId(cmd.msg.authorId)) match {
                      case Some(VoiceState(_, Some(vChannelId), _, _, _, _, _, _, _)) =>
                        musicHandler ! QueueUrl(cmd.args, channel, vChannelId)
                        None
                      case _ => Some(channel.sendMessage("Not in a voice channel"))
                    }
                  }
                } yield ()
            }
        },
        filters = Seq(CmdFilter.InOneGuild(guildId)),
        description = Some(CmdDescription(name = "Queue music", description = "Set an url as the url to play"))
      )

  val StopCmdFactory: ParsedCmdFactory[NotUsed, Future[Done]] = ParsedCmdFactory[NotUsed, Future[Done]](
    category = ExampleCmdCategories.&,
    aliases = Seq("s", "stop"),
    sink = _ =>
      ParsedCmdFlow[NotUsed]
        .map(implicit c => cmd => cmd.msg.tChannel.foreach(musicHandler ! StopMusic(_)))
        .toMat(Sink.ignore)(Keep.right),
    filters = Seq(CmdFilter.InOneGuild(guildId)),
    description =
      Some(CmdDescription(name = "Stop music", description = "Stop music from playing, and leave the channel")),
  )

  val NextCmdFactory: ParsedCmdFactory[NotUsed, Future[Done]] = ParsedCmdFactory[NotUsed, Future[Done]](
    category = ExampleCmdCategories.&,
    aliases = Seq("n", "next"),
    sink = _ =>
      ParsedCmdFlow[NotUsed]
        .map(implicit c => cmd => cmd.msg.tChannel.foreach(musicHandler ! NextTrack(_)))
        .toMat(Sink.ignore)(Keep.right),
    filters = Seq(CmdFilter.InOneGuild(guildId)),
    description = Some(CmdDescription(name = "Next track", description = "Skip to the next track")),
  )

  val PauseCmdFactory: ParsedCmdFactory[NotUsed, Future[Done]] = ParsedCmdFactory[NotUsed, Future[Done]](
    category = ExampleCmdCategories.&,
    aliases = Seq("p", "pause"),
    sink = _ =>
      ParsedCmdFlow[NotUsed]
        .map(implicit c => cmd => cmd.msg.tChannel.foreach(musicHandler ! TogglePause(_)))
        .toMat(Sink.ignore)(Keep.right),
    filters = Seq(CmdFilter.InOneGuild(guildId)),
    description = Some(CmdDescription(name = "Pause/Play", description = "Toggle pause on the current player")),
  )
}
