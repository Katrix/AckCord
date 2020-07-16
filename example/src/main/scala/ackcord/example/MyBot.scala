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
import ackcord.gateway.GatewayIntents
import ackcord.syntax._
import cats.instances.future._

object MyBot extends App {

  val GeneralCommands = "!"
  val MusicCommands   = "&"

  require(args.nonEmpty, "Please provide a token")
  val token    = args.head
  val settings = ClientSettings(token, intents = GatewayIntents.AllNonPrivileged)
  import settings.executionContext

  settings
    .createClient()
    .foreach { client =>
      client.onEventSideEffectsIgnore {
        case APIMessage.Ready(_) => println("Now ready")
      }

      import client.requestsHelper._
      client.onEventAsync { implicit c =>
        {
          case APIMessage.ChannelCreate(_, channel, _) =>
            for {
              tChannel <- optionPure(channel.asTextChannel)
              _        <- run(tChannel.sendMessage("First"))
            } yield ()
          case APIMessage.ChannelDelete(optGuild, channel, _) =>
            for {
              guild <- optionPure(optGuild)
              _     <- runOption(guild.textChannels.headOption.map(_.sendMessage(s"${channel.name} was deleted")))
            } yield ()
        }
      }

      val myEvents    = new MyEvents(client.requests)
      val myListeners = new Listeners(client)
      val myCommands  = new MyCommands(client, client.requests)

      client.bulkRegisterListeners(
        myEvents.printReady,
        myEvents.welcomeNew
      )

      client.registerListener(myListeners.createListeners)

      client.commands.bulkRunNamed(
        myCommands.echo,
        myCommands.guildInfo,
        myCommands.ping,
        myCommands.kill,
        myCommands.queue
      )

      client.login()
    }
}
