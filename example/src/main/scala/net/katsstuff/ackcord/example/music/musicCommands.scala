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
import net.katsstuff.ackcord.commands.{CommandDescription, CommandFilter, CommandMeta, ParsedCommandActor}
import net.katsstuff.ackcord.data.{CacheSnapshot, Message, UserId, VoiceState}
import net.katsstuff.ackcord.example.ExampleCmdCategories
import net.katsstuff.ackcord.example.music.MusicHandler.{NextTrack, QueueUrl, StopMusic, TogglePause}
import net.katsstuff.ackcord.syntax._

class QueueCommand(musicHandler: ActorRef)(implicit val client: ClientActor)
    extends ParsedCommandActor[String]
    with ActorLogging {
  override def handleCommand(msg: Message, url: String, remaining: List[String])(implicit c: CacheSnapshot): Unit = {
    val gOpt = for {
      guildChannel <- msg.tGuildChannel
      guild        <- guildChannel.guild
    } yield (guildChannel, guild)
    log.info("Dummy")

    gOpt match {
      case Some((guildChannel, guild)) =>
        guild.voiceStateFor(UserId(msg.author.id)) match {
          case Some(VoiceState(_, Some(channelId), _, _, _, _, _, _, _)) =>
            musicHandler ! QueueUrl(url, guildChannel, channelId)
            log.info("Queued")
          case _ => client ! guildChannel.sendMessage("Not in a voice channel", sendResponseTo = Some(errorResponder))
        }

      case None => throw new IllegalStateException("No guild for guild command")
    }
  }
}
object QueueCommand {
  def props(musicHandler: ActorRef)(implicit client: ClientActor): Props = Props(new QueueCommand(musicHandler))
  def cmdMeta(musicHandler: ActorRef)(implicit client: ClientActor): CommandMeta[String] =
    CommandMeta[String](
      category = ExampleCmdCategories.&,
      alias = Seq("q", "queue"),
      handler = props(musicHandler),
      filters = Seq(CommandFilter.InGuild),
      description = Some(CommandDescription(name = "Queue music", description = "Set an url as the url to play")),
    )
}

class StopCommand(musicHandler: ActorRef)(implicit val client: ClientActor) extends ParsedCommandActor[NotUsed] {
  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit =
    msg.tChannel.foreach(musicHandler ! StopMusic(_))
}
object StopCommand {
  def props(musicHandler: ActorRef)(implicit client: ClientActor): Props = Props(new StopCommand(musicHandler))
  def cmdMeta(musicHandler: ActorRef)(implicit client: ClientActor): CommandMeta[NotUsed] =
    CommandMeta[NotUsed](
      category = ExampleCmdCategories.&,
      alias = Seq("s", "stop"),
      handler = props(musicHandler),
      filters = Seq(CommandFilter.InGuild),
      description =
        Some(CommandDescription(name = "Stop music", description = "Stop music from playing, and leave the channel")),
    )
}
class NextCommand(musicHandler: ActorRef)(implicit val client: ClientActor) extends ParsedCommandActor[NotUsed] {
  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit = {
    msg.tChannel.foreach(musicHandler ! NextTrack(_))
  }
}
object NextCommand {
  def props(musicHandler: ActorRef)(implicit client: ClientActor): Props = Props(new NextCommand(musicHandler))
  def cmdMeta(musicHandler: ActorRef)(implicit client: ClientActor): CommandMeta[NotUsed] =
    CommandMeta[NotUsed](
      category = ExampleCmdCategories.&,
      alias = Seq("n", "next"),
      handler = props(musicHandler),
      filters = Seq(CommandFilter.InGuild),
      description = Some(CommandDescription(name = "Next track", description = "Skip to the next track")),
    )
}
class PauseCommand(musicHandler: ActorRef)(implicit val client: ClientActor) extends ParsedCommandActor[NotUsed] {
  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit =
    msg.tChannel.foreach(musicHandler ! TogglePause(_))
}
object PauseCommand {
  def props(musicHandler: ActorRef)(implicit client: ClientActor): Props = Props(new PauseCommand(musicHandler))
  def cmdMeta(musicHandler: ActorRef)(implicit client: ClientActor): CommandMeta[NotUsed] =
    CommandMeta[NotUsed](
      category = ExampleCmdCategories.&,
      alias = Seq("p", "pause"),
      handler = props(musicHandler),
      filters = Seq(CommandFilter.InGuild),
      description = Some(CommandDescription(name = "Pause/Play", description = "Toggle pause on the current player")),
    )
}
