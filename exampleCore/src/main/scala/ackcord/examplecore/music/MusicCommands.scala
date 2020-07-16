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

import ackcord._
import ackcord.commands.{CommandBuilder, CommandController, NamedCommand, VoiceGuildMemberCommandMessage}
import ackcord.data.{GuildId, TextChannel}
import ackcord.examplecore.music.MusicHandler.{NextTrack, QueueUrl, StopMusic, TogglePause}
import akka.NotUsed
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.Timeout

class MusicCommands(requests: Requests, guildId: GuildId, musicHandler: ActorRef[MusicHandler.Command])(
    implicit timeout: Timeout,
    system: ActorSystem[Nothing]
) extends CommandController(requests) {

  val music = Seq("&")

  val VoiceCommand: CommandBuilder[VoiceGuildMemberCommandMessage, NotUsed] =
    GuildVoiceCommand.andThen(CommandBuilder.inOneGuild(guildId))

  val queue: NamedCommand[String] =
    VoiceCommand.named(music, Seq("q", "queue")).parsing[String].withSideEffects { m =>
      musicHandler.ask[MusicHandler.CommandAck.type](QueueUrl(m.parsed, m.textChannel, m.voiceChannel.id, _))
    }

  private def simpleCommand(
      aliases: Seq[String],
      mapper: (TextChannel, ActorRef[MusicHandler.CommandAck.type]) => MusicHandler.MusicHandlerEvents
  ): NamedCommand[NotUsed] = {
    VoiceCommand.andThen(CommandBuilder.inOneGuild(guildId)).named(music, aliases, mustMention = true).toSink {
      Flow[VoiceGuildMemberCommandMessage[NotUsed]]
        .map(_.textChannel)
        .via(ActorFlow.ask(requests.parallelism)(musicHandler)(mapper))
        .toMat(Sink.ignore)(Keep.none)
    }
  }

  val stop: NamedCommand[NotUsed] = simpleCommand(Seq("s", "stop"), StopMusic.apply)

  val next: NamedCommand[NotUsed] = simpleCommand(Seq("n", "next"), NextTrack.apply)

  val pause: NamedCommand[NotUsed] = simpleCommand(Seq("p", "pause"), TogglePause.apply)
}
