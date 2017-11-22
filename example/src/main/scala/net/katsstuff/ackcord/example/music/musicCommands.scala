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
import akka.stream.scaladsl.{Flow, Sink}
import akka.{Done, NotUsed}
import net.katsstuff.ackcord.commands.{CmdDescription, CmdFilter, ParsedCmd, ParsedCmdFactory}
import net.katsstuff.ackcord.data.{CacheSnapshot, GuildId, UserId, VoiceState}
import net.katsstuff.ackcord.example.ExampleCmdCategories
import net.katsstuff.ackcord.example.music.MusicHandler.{NextTrack, QueueUrl, StopMusic, TogglePause}
import net.katsstuff.ackcord.http.RawMessage
import net.katsstuff.ackcord.http.requests.RequestStreams
import net.katsstuff.ackcord.syntax._

class QueueCmdFactory(guildId: GuildId, musicHandler: ActorRef)
    extends ParsedCmdFactory[String, NotUsed](
      category = ExampleCmdCategories.&,
      aliases = Seq("q", "queue"),
      sink = (token, system, mat) => {
        Flow[ParsedCmd[String]]
          .mapConcat {
            case ParsedCmd(msg, url, _, c) =>
              implicit val cache: CacheSnapshot = c

              val errorMsg = for {
                channel      <- msg.channel
                guildChannel <- channel.asTGuildChannel
                guild        <- guildChannel.guild
                res <- guild.voiceStateFor(UserId(msg.authorId)) match {
                  case Some(VoiceState(_, Some(channelId), _, _, _, _, _, _, _)) =>
                    musicHandler ! QueueUrl(url, guildChannel, channelId)
                    None
                  case _ => Some(guildChannel.sendMessage("Not in a voice channel"))
                }
              } yield res

              errorMsg.toList
          }
          .via(RequestStreams.simpleRequestFlow[RawMessage, NotUsed](token)(system, mat))
          .to(Sink.ignore)
      },
      filters = Seq(CmdFilter.InOneGuild(guildId)),
      description = Some(CmdDescription(name = "Queue music", description = "Set an url as the url to play")),
    )
object QueueCmdFactory {
  def apply(guildId: GuildId, musicHandler: ActorRef): QueueCmdFactory = new QueueCmdFactory(guildId, musicHandler)
}

class StopCmdFactory(guildId: GuildId, musicHandler: ActorRef)
    extends ParsedCmdFactory[NotUsed, Future[Done]](
      category = ExampleCmdCategories.&,
      aliases = Seq("s", "stop"),
      sink = (_, _, _) =>
        Sink.foreach {
          case ParsedCmd(msg, _, _, c) =>
            implicit val cache: CacheSnapshot = c
            msg.tChannel.foreach(musicHandler ! StopMusic(_))
      },
      filters = Seq(CmdFilter.InOneGuild(guildId)),
      description =
        Some(CmdDescription(name = "Stop music", description = "Stop music from playing, and leave the channel")),
    )
object StopCmdFactory {
  def apply(guildId: GuildId, musicHandler: ActorRef): StopCmdFactory = new StopCmdFactory(guildId, musicHandler)
}

class NextCmdFactory(guildId: GuildId, musicHandler: ActorRef)
    extends ParsedCmdFactory[NotUsed, Future[Done]](
      category = ExampleCmdCategories.&,
      aliases = Seq("n", "next"),
      sink = (_, _, _) =>
        Sink.foreach {
          case ParsedCmd(msg, _, _, c) =>
            implicit val cache: CacheSnapshot = c
            msg.tChannel.foreach(musicHandler ! NextTrack(_))
      },
      filters = Seq(CmdFilter.InOneGuild(guildId)),
      description = Some(CmdDescription(name = "Next track", description = "Skip to the next track")),
    )
object NextCmdFactory {
  def apply(guildId: GuildId, musicHandler: ActorRef): NextCmdFactory = new NextCmdFactory(guildId, musicHandler)
}

class PauseCmdFactory(guildId: GuildId, musicHandler: ActorRef)
    extends ParsedCmdFactory[NotUsed, Future[Done]](
      category = ExampleCmdCategories.&,
      aliases = Seq("p", "pause"),
      sink = (_, _, _) =>
        Sink.foreach {
          case ParsedCmd(msg, _, _, c) =>
            implicit val cache: CacheSnapshot = c
            msg.tChannel.foreach(musicHandler ! TogglePause(_))
      },
      filters = Seq(CmdFilter.InOneGuild(guildId)),
      description = Some(CmdDescription(name = "Pause/Play", description = "Toggle pause on the current player")),
    )
object PauseCmdFactory {
  def apply(guildId: GuildId, musicHandler: ActorRef): PauseCmdFactory = new PauseCmdFactory(guildId, musicHandler)
}
