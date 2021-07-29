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
import ackcord.commands.PrefixParser
import ackcord.gateway.{Dispatch, GatewayIntents}
import ackcord.interactions.InteractionsRegistrar
import ackcord.syntax._

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
        case APIMessage.Ready(_, _, _) => println("Now ready")
      }

      client.onEventSideEffectsIgnore {
        case msg => println(msg.getClass.getName)
      }

      {
        import client.system
        client.events.receiveGatewaySubscribe.runForeach {
          case Dispatch(_, msg, _) => println(msg.getClass.getName)
          case _                   =>
        }
      }

      import client.requestsHelper._
      client.onEventAsync { implicit c =>
        {
          case APIMessage.ChannelCreate(_, channel, _, _) =>
            for {
              tChannel <- optionPure(channel.asTextChannel)
              _        <- run(tChannel.sendMessage("First"))
            } yield ()
          case APIMessage.ChannelDelete(optGuild, channel, _, _) =>
            for {
              guild <- optionPure(optGuild)
              _     <- runOption(guild.textChannels.headOption.map(_.sendMessage(s"${channel.name} was deleted")))
            } yield ()
        }
      }

      client.onEventAsync { implicit c =>
        {
          case APIMessage.ThreadCreate(_, thread, _, _) =>
            run(thread.sendMessage("First")).map(_ => ())
        }
      }

      /*
      client.onEventAsync { implicit c =>
        {
          case APIMessage.ThreadCreate(_, thread, _, _) =>
            println(thread)
            //run(thread.sendMessage("First")).map(_ => ())
            run(CreateMessage.mkContent(thread.parentChannelId, s"First in ${thread.name}")).map(_ => ())
          case APIMessage.ThreadUpdate(_, thread, _, _) => run(thread.sendMessage(s"Edited")).map(_ => ())
          case msg @ APIMessage.ThreadDelete(_, threadId, parentId, _, _, _) =>
            run(
              CreateMessage
                .mkContent(parentId, s"Deleted thread ${msg.thread.fold(threadId.asString)(_.name)}")
            ).map(_ => ())
        }
      }
      */

      val myEvents      = new MyEvents(client.requests)
      val myListeners   = new Listeners(client)
      val myCommands    = new MyCommands(client, client.requests)
      val myHelpCommand = new MyHelpCommand(client.requests)

      client.bulkRegisterListeners(
        myEvents.printReady,
        myEvents.welcomeNew
      )

      client.registerListener(myListeners.createListeners)

      client.commands.runNewCommand(
        PrefixParser.structured(needsMention = true, Seq("!"), Seq("help")),
        myHelpCommand.command
      )

      client.commands.bulkRunNamedWithHelp(
        myHelpCommand,
        myCommands.hello,
        myCommands.copy,
        myCommands.setShouldMention,
        myCommands.modifyPrefixSymbols,
        myCommands.guildInfo,
        myCommands.sendFile,
        myCommands.adminsOnly,
        myCommands.timeDiff,
        myCommands.ping,
        myCommands.maybeFail,
        myCommands.ratelimitTest("ratelimitTest", client.requests.sinkIgnore[Any]),
        myCommands
          .ratelimitTest("ratelimitTestOrdered", client.requests.sinkIgnore[Any](Requests.RequestProperties.ordered)),
        myCommands.kill
      )

      val buttonCommands = new MyComponentCommands(client.requests)

      client.commands.bulkRunNamedWithHelp(
        myHelpCommand,
        buttonCommands.commands: _*
      )

      import client.system

      client.onEventSideEffectsIgnore {
        case APIMessage.Ready(applicationId, _, _) =>
          client.events.interactions
            .to(InteractionsRegistrar.gatewayInteractions()(applicationId.asString, client.requests))
            .run()
      }

      client.login()
    }
}
