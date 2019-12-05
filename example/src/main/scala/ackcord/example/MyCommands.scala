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
import ackcord.syntax._
import ackcord.commands._
import akka.NotUsed
import com.sedmelluq.discord.lavaplayer.player.{AudioPlayerManager, DefaultAudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.{AudioPlaylist, AudioTrack}

class MyCommands(client: DiscordClient, requests: RequestHelper) extends CommandController(requests) {

  val GeneralCommands = "!"
  val MusicCommands   = "&"

  val echo: NamedCommand[MessageParser.RemainingAsString] =
    Command
      .named(GeneralCommands, Seq("echo"), mustMention = true)
      .parsing[MessageParser.RemainingAsString]
      .withRequest { r =>
        r.tChannel.sendMessage(s"ECHO: ${r.parsed.remaining}")
      }

  val guildInfo: NamedCommand[NotUsed] =
    GuildCommand.named(GeneralCommands, Seq("guildInfo"), mustMention = true).withRequest { r =>
      val guildName   = r.guild.name
      val channelName = r.tChannel.name
      val userNick    = r.guildMember.nick.getOrElse(r.user.username)

      r.tChannel
        .sendMessage(s"This guild is named $guildName, the channel is named $channelName and you are called $userNick")
    }

  val ping: NamedCommand[NotUsed] =
    Command.named(GeneralCommands, Seq("ping"), mustMention = true).withSideEffects { _ =>
      println(s"Received ping command")
    }

  val kill: NamedCommand[NotUsed] =
    Command.named(GeneralCommands, Seq("kill", "die"), mustMention = true).withSideEffects { _ =>
      client.shutdownJVM()
    }

  val playerManager: AudioPlayerManager = new DefaultAudioPlayerManager
  AudioSourceManagers.registerRemoteSources(playerManager)

  val queue: NamedCommand[String] =
    GuildVoiceCommand.named(MusicCommands, Seq("queue"), mustMention = true).parsing[String].async { r =>
      val guildId     = r.guild.id
      val url         = r.parsed
      val loadItem    = client.loadTrack(playerManager, url)
      val joinChannel = client.joinChannel(guildId, r.vChannel.id, playerManager.createPlayer())

      loadItem.zip(joinChannel).map {
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
