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

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, Props}
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.commands.{CmdDescription, CmdFilter, ParsedCmdActor, ParsedCmdFactory}
import net.katsstuff.ackcord.data.{CacheSnapshot, Message, UserId, VoiceState}
import net.katsstuff.ackcord.example.ExampleCmdCategories
import net.katsstuff.ackcord.example.music.MusicHandler.{NextTrack, QueueUrl, StopMusic, TogglePause}
import net.katsstuff.ackcord.syntax._

class QueueCmd(musicHandler: ActorRef, val client: ClientActor) extends ParsedCmdActor[String] with ActorLogging {
  override def handleCommand(msg: Message, url: String, remaining: List[String])(implicit c: CacheSnapshot): Unit = {
    val gOpt = for {
      channel      <- msg.channel
      guildChannel <- channel.asTGuildChannel
      guild        <- guildChannel.guild
    } yield (guildChannel, guild)
    log.info("Dummy")

    gOpt match {
      case Some((guildChannel, guild)) =>
        guild.voiceStateFor(UserId(msg.author.id)) match {
          case Some(VoiceState(_, Some(channelId), _, _, _, _, _, _, _)) =>
            musicHandler ! QueueUrl(url, guildChannel, channelId)
            log.info("Queued")
          case _ => client ! guildChannel.sendMessage("Not in a voice channel")
        }

      case None => throw new IllegalStateException("No guild for guild command")
    }
  }
}
class QueueCmdFactory(musicHandler: ActorRef)
    extends ParsedCmdFactory[String](
      category = ExampleCmdCategories.&,
      aliases = Seq("q", "queue"),
      cmdProps = client => Props(new QueueCmd(musicHandler, client)),
      filters = Seq(CmdFilter.InGuild),
      description = Some(CmdDescription(name = "Queue music", description = "Set an url as the url to play")),
    )
object QueueCmdFactory {
  def apply(musicHandler: ActorRef): QueueCmdFactory = new QueueCmdFactory(musicHandler)
}

class StopCmd(musicHandler: ActorRef, val client: ClientActor) extends ParsedCmdActor[NotUsed] {
  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit =
    msg.tChannel.foreach(musicHandler ! StopMusic(_))
}
class StopCmdFactory(musicHandler: ActorRef)
    extends ParsedCmdFactory[NotUsed](
      category = ExampleCmdCategories.&,
      aliases = Seq("s", "stop"),
      cmdProps = client => Props(new StopCmd(musicHandler, client)),
      filters = Seq(CmdFilter.InGuild),
      description =
        Some(CmdDescription(name = "Stop music", description = "Stop music from playing, and leave the channel")),
    )
object StopCmdFactory {
  def apply(musicHandler: ActorRef): StopCmdFactory = new StopCmdFactory(musicHandler)
}

class NextCmd(musicHandler: ActorRef, val client: ClientActor) extends ParsedCmdActor[NotUsed] {
  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit =
    msg.tChannel.foreach(musicHandler ! NextTrack(_))
}
class NextCmdFactory(musicHandler: ActorRef)
    extends ParsedCmdFactory[NotUsed](
      category = ExampleCmdCategories.&,
      aliases = Seq("n", "next"),
      cmdProps = client => Props(new NextCmd(musicHandler, client)),
      filters = Seq(CmdFilter.InGuild),
      description = Some(CmdDescription(name = "Next track", description = "Skip to the next track")),
    )
object NextCmdFactory {
  def apply(musicHandler: ActorRef): NextCmdFactory = new NextCmdFactory(musicHandler)
}

class PauseCmd(musicHandler: ActorRef, val client: ClientActor) extends ParsedCmdActor[NotUsed] {
  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit =
    msg.tChannel.foreach(musicHandler ! TogglePause(_))
}
class PauseCmdFactory(musicHandler: ActorRef)
    extends ParsedCmdFactory[NotUsed](
      category = ExampleCmdCategories.&,
      aliases = Seq("p", "pause"),
      cmdProps = client => Props(new PauseCmd(musicHandler, client)),
      filters = Seq(CmdFilter.InGuild),
      description = Some(CmdDescription(name = "Pause/Play", description = "Toggle pause on the current player")),
    )
object PauseCmdFactory {
  def apply(musicHandler: ActorRef): PauseCmdFactory = new PauseCmdFactory(musicHandler)
}
