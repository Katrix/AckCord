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
package net.katsstuff.ackcord.example

import scala.language.higherKinds

import com.sedmelluq.discord.lavaplayer.player.{AudioPlayerManager, DefaultAudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.{AudioPlaylist, AudioTrack}

import cats.{Alternative, Id, Monad}
import cats.syntax.all._
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.data.Message
import net.katsstuff.ackcord.syntax._

object MyBot extends App {

  val GeneralCommands = CmdCategory("!", "General commands")
  val MusicCommands   = CmdCategory("&", "Music commands")

  require(args.nonEmpty, "Please provide a token")
  val token = args.head
  val settings =
    ClientSettings(token, commandSettings = CommandSettings(categories = Set(GeneralCommands, MusicCommands)))
  import settings.executionContext

  settings
    .build()
    .foreach { client =>
      client.registerHandler(new EventHandler[Id, APIMessage.Ready] {
        override def handle(message: APIMessage.Ready)(implicit c: CacheSnapshot[Id]): Unit =
          println("Now ready")
      })

      client.registerHandler(new EventHandlerDSL[Id, APIMessage.ChannelCreate] {
        override def handle[G[_]](
            message: APIMessage.ChannelCreate
        )(implicit c: CacheSnapshot[Id], DSL: RequestDSL[G], G: Alternative[G] with Monad[G]): G[Unit] = {
          import DSL._

          for {
            tChannel <- optionPure(message.channel.asTChannel)
            _        <- wrapRequest(tChannel.sendMessage("First"))
          } yield ()
        }
      })



      client.registerHandler(new EventHandlerDSL[Id, APIMessage.ChannelDelete] {
        override def handle[G[_]](
            message: APIMessage.ChannelDelete
        )(implicit c: CacheSnapshot[Id], DSL: RequestDSL[G], G: Alternative[G] with Monad[G]): G[Unit] = {
          import DSL._

          for {
            guildChannel <- optionPure(message.channel.asGuildChannel)
            optGuild     <- liftFoldable(guildChannel.guild.value)
            guild        <- optionPure(optGuild)
            _            <- optionRequest(guild.tChannels.headOption.map(_.sendMessage(s"${guildChannel.name} was deleted")))
          } yield ()
        }
      })

      client.registerHandler(new RawCommandHandlerDSL[Id] {
        override def handle[G[_]](implicit c: CacheSnapshot[Id], DSL: RequestDSL[G], G: Alternative[G] with Monad[G])
          : PartialFunction[RawCmd[Id], Unit] = {
          case RawCmd(message, GeneralCommands, "echo", args, _) =>
            import DSL._
            for {
              channel <- optionPure(message.tGuildChannel.value)
              _       <- wrapRequest(channel.sendMessage(s"ECHO: ${args.mkString(" ")}"))
            } yield ()
        }
      })

      client.registerHandler(
        new CommandHandler[Id, Int](
          category = GeneralCommands,
          aliases = Seq("ping"),
          filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild),
          description = Some(CmdDescription("Ping", "Check if the bot is alive"))
        ) {
          override def handle(msg: Message, args: Int, remaining: List[String])(implicit c: CacheSnapshot[Id]): Unit =
            println(s"Received ping command with arg $args")
        }
      )

      val playerManager: AudioPlayerManager = new DefaultAudioPlayerManager
      AudioSourceManagers.registerRemoteSources(playerManager)

      client.registerHandler(
        new CommandHandler[Id, String](
          category = MusicCommands,
          aliases = Seq("queue"),
          filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild),
          description = Some(CmdDescription("Queue", "Queue a track"))
        ) {
          override def handle(msg: Message, args: String, remaining: List[String])(
              implicit c: CacheSnapshot[Id]
          ): Unit = {
            for {
              channel    <- msg.tGuildChannel[Id].value
              authorId   <- msg.authorUserId
              guild      <- channel.guild[Id].value
              vChannelId <- guild.voiceStateFor(authorId).flatMap(_.channelId)
            } {
              val guildId     = guild.id
              val url         = args
              val loadItem    = client.loadTrack(playerManager, url)
              val joinChannel = client.joinChannel(guildId, vChannelId, playerManager.createPlayer())

              loadItem.zip(joinChannel).foreach {
                case (track: AudioTrack, player) =>
                  player.startTrack(track, true)
                  client.setPlaying(guildId, playing = true)
                case (playlist: AudioPlaylist, player) =>
                  if (playlist.getSelectedTrack != null) {
                    player.startTrack(playlist.getSelectedTrack, false)
                  } else {
                    player.startTrack(playlist.getTracks.get(0), false)
                  }
                  client.setPlaying(guildId, playing = true)
              }
            }
          }
        }
      )

      client.login()
    }
}
