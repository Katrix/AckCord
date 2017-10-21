/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
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
package net.katsstuff.ackcord.example

import akka.actor.ActorSystem
import akka.event.EventStream
import akka.stream.{ActorMaterializer, Materializer}
import net.katsstuff.ackcord.commands.{CommandDispatcher, CommandParser}
import net.katsstuff.ackcord.data.GuildChannel
import net.katsstuff.ackcord.util.{GuildDispatcher, MessageParser}
import net.katsstuff.ackcord.{APIMessage, DiscordClient, DiscordClientSettings}

object Example {

  implicit val system: ActorSystem  = ActorSystem("AckCord")
  implicit val mat:    Materializer = ActorMaterializer()
  import system.dispatcher

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("Please specify a token")
      sys.exit()
    }

    val eventStream = new EventStream(system)
    val token       = args.head

    val settings = DiscordClientSettings(token = token, system = system, eventStream = eventStream)
    DiscordClient.fetchWsGateway.map(settings.connect).foreach { client =>

      //We set up a command dispatcher, that sends the correct command to the corresponding actor
      val commandDispatcher = CommandDispatcher.props(
        needMention = true,
        Map(
          "!" -> Map(
            "ping"        -> PingCommand.props(client),
            "sendFile"    -> SendFileCommand.props(client),
            "infoChannel" -> CommandParser.props(MessageParser[GuildChannel], InfoChannelCommand.props(client)),
            "kill"        -> KillCommand.props(client)
          )
        ),
        CommandErrorHandler.props(client)
      )

      //We place the command dispatcher behind a guild dispatcher, this way each guild gets it's own command dispatcher
      val guildDispatcherCommands = system.actorOf(GuildDispatcher.props(commandDispatcher, None), "BaseCommands")

      val guildDispatcherMusic = system.actorOf(GuildDispatcher.props(MusicHandler.props(client) _, None), "MusicHandler")

      eventStream.subscribe(guildDispatcherCommands, classOf[APIMessage.MessageCreate])
      eventStream.subscribe(guildDispatcherMusic, classOf[APIMessage.MessageCreate])
      eventStream.subscribe(guildDispatcherMusic, classOf[APIMessage.VoiceServerUpdate])
      eventStream.subscribe(guildDispatcherMusic, classOf[APIMessage.VoiceStateUpdate])
      client ! DiscordClient.StartClient
    }
  }
}
