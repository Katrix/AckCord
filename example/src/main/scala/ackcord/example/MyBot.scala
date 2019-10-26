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
package ackcord.example

import ackcord._
import ackcord.commands._
import ackcord.syntax._
import akka.NotUsed
import com.sedmelluq.discord.lavaplayer.player.{AudioPlayerManager, DefaultAudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.{AudioPlaylist, AudioTrack}

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
    .createClient()
    .foreach { client =>
      client.onEvent[cats.Id] {
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
                guild        <- optionPure(guildChannel.guild)
                _            <- runOption(guild.tChannels.headOption.map(_.sendMessage(s"${guildChannel.name} was deleted")))
              } yield ()
            case _ => client.sourceRequesterRunner.unit
          }
        }
      }

      client.onRawCmd[SourceRequest] {
        client.withCache[SourceRequest, RawCmd] { implicit c =>
          {
            case RawCmd(message, GeneralCommands, "echo", args, _) =>
              for {
                channel <- optionPure(message.tGuildChannel)
                _       <- run(channel.sendMessage(s"ECHO: ${args.mkString(" ")}"))
              } yield ()
            case _ => client.sourceRequesterRunner.unit
          }
        }
      }

      client.registerCmd[NotUsed, SourceRequest](
        prefix = GeneralCommands,
        aliases = Seq("guildInfo"),
        filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild)
      ) {
        client.withCache[SourceRequest, ParsedCmd[NotUsed]] { implicit c => cmd =>
          for {
            user        <- optionPure(cmd.msg.authorUser)
            channel     <- optionPure(cmd.msg.tGuildChannel)
            guild       <- optionPure(channel.guild)
            guildMember <- optionPure(guild.memberFromUser(user))
            guildName   = guild.name
            channelName = channel.name
            userNick    = guildMember.nick.getOrElse(user.username)
            _ <- run(
              channel.sendMessage(
                s"This guild is named $guildName, the channel is named $channelName and you are called $userNick"
              )
            )
          } yield ()
        }
      }

      client.registerCmd[NotUsed, cats.Id](
        prefix = GeneralCommands,
        aliases = Seq("ping"),
        filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild),
        description = Some(CmdDescription("Ping", "Check if the bot is alive"))
      ) { _ =>
        println(s"Received ping command")
      }

      client.registerCmd[NotUsed, cats.Id](
        prefix = GeneralCommands,
        aliases = Seq("kill", "die"),
        filters = Seq(CmdFilter.NonBot), //Ideally you're create a new filter type here to only allow the owner to shut down the bot
        description = Some(CmdDescription("Kill", "Stops the bot"))
      ) { _ =>
        client.shutdown()
      }

      val playerManager: AudioPlayerManager = new DefaultAudioPlayerManager
      AudioSourceManagers.registerRemoteSources(playerManager)

      client.registerCmd[String, cats.Id](
        refiner = CmdInfo(
          prefix = MusicCommands,
          aliases = Seq("queue"),
          filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild)
        ),
        description = Some(CmdDescription("Queue", "Queue a track"))
      ) {
        client.withCache[cats.Id, ParsedCmd[String]] { implicit c => cmd =>
          for {
            channel    <- cmd.msg.tGuildChannel
            authorId   <- cmd.msg.authorUserId
            guild      <- channel.guild
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
