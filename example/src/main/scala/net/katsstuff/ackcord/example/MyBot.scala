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

import com.sedmelluq.discord.lavaplayer.player.{AudioPlayerManager, DefaultAudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.{AudioPlaylist, AudioTrack}

import akka.NotUsed
import cats.Id
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.syntax._

object MyBot extends App {

  val GeneralCommands = "!"
  val MusicCommands   = "&"

  require(args.nonEmpty, "Please provide a token")
  val token = args.head
  val settings =
    ClientSettings(
      token,
      commandSettings = CommandSettings(needsMention = true, prefixes = Set(GeneralCommands, MusicCommands))
    )
  import settings.executionContext

  settings
    .build()
    .foreach { client =>
      client.onEvent[Id] {
        case APIMessage.Ready(_) => println("Now ready")
        case _                   => client.sourceRequesterRunner.unit
      }

      import client.sourceRequesterRunner._
      client.onEvent[SourceRequest] {
        client.withCache[SourceRequest, APIMessage] { implicit c =>
          {
            case APIMessage.ChannelCreate(channel, _) =>
              for {
                tChannel <- optionPure(channel.asTChannel)
                _        <- run(tChannel.sendMessage("First"))
              } yield ()
            case APIMessage.ChannelDelete(channel, _) =>
              for {
                guildChannel <- optionPure(channel.asGuildChannel)
                guild        <- optionPure(guildChannel.guild.value)
                _            <- runOption(guild.tChannels.headOption.map(_.sendMessage(s"${guildChannel.name} was deleted")))
              } yield ()
            case _ => client.sourceRequesterRunner.unit
          }
        }
      }

      client.onRawCmd[SourceRequest] {
        client.withCache[SourceRequest, RawCmd[Id]] { implicit c =>
          {
            case RawCmd(message, GeneralCommands, "echo", args, _) =>
              for {
                channel <- optionPure(message.tGuildChannel[Id].value)
                _       <- run(channel.sendMessage(s"ECHO: ${args.mkString(" ")}"))
              } yield ()
            case _ => client.sourceRequesterRunner.unit
          }
        }
      }

      client.registerCmd[NotUsed, Id](
        prefix = GeneralCommands,
        aliases = Seq("ping"),
        filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild),
        description = Some(CmdDescription("Ping", "Check if the bot is alive"))
      ) { _ =>
        println(s"Received ping command}")
      }

      val playerManager: AudioPlayerManager = new DefaultAudioPlayerManager
      AudioSourceManagers.registerRemoteSources(playerManager)

      client.registerCmd[String, Id](
        refiner =
          CmdInfo[Id](prefix = MusicCommands, aliases = Seq("queue"), filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild)),
        description = Some(CmdDescription("Queue", "Queue a track"))
      ) {
        client.withCache[Id, ParsedCmd[Id, String]] { implicit c => cmd =>
          for {
            channel    <- cmd.msg.tGuildChannel.value
            authorId   <- cmd.msg.authorUserId
            guild      <- channel.guild.value
            vChannelId <- guild.voiceStateFor(authorId).flatMap(_.channelId)
          } {
            val guildId     = guild.id
            val url         = cmd.args
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
              case _ => sys.error("Unknown audio item")
            }
          }
        }
      }

      client.login()
    }
}
