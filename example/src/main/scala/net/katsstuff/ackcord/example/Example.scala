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

import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.event.EventStream
import akka.stream.{ActorMaterializer, Materializer}
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.commands.{CommandMeta, CommandRouter}
import net.katsstuff.ackcord.example.music.{MusicHandler, NextCommand, PauseCommand, QueueCommand, StopCommand}
import net.katsstuff.ackcord.util.GuildRouter
import net.katsstuff.ackcord.{APIMessage, ClientSettings, DiscordClient}

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

    val settings = ClientSettings(token = token)
    DiscordClient.fetchWsGateway.map(settings.connect(eventStream, _)).onComplete {
      case Success(clientActor) =>
        system.actorOf(ExampleMain.props(settings, eventStream, clientActor), "Main")
      case Failure(e) =>
        println("Could not connect to Discord")
        throw e
    }
  }
}

class ExampleMain(settings: ClientSettings, eventStream: EventStream, var client: ClientActor)(
    implicit materializer: Materializer
) extends Actor
    with ActorLogging {
  import context.dispatcher
  implicit val system: ActorSystem = context.system

  val commands =
    Seq(PingCommand.cmdMeta(client), SendFileCommand.cmdMeta(client), InfoChannelCommand.cmdMeta(client))
  private val allCommands = commands ++ Seq(
    KillCommand.cmdMeta(self, client),
    QueueCommand.cmdMeta(null, client),
    StopCommand.cmdMeta(null, client),
    NextCommand.cmdMeta(null, client),
    PauseCommand.cmdMeta(null, client)
  )
  private val allCommandNames = {
    val base     = CommandMeta.routerMap(allCommands, client).mapValues(_.keySet)
    val withHelp = base(ExampleCmdCategories.!) + "help"
    base + (ExampleCmdCategories.! -> withHelp)
  }

  //We set up a command dispatcher, that sends the correct command to the corresponding actor
  val commandDispatcher: Props = CommandRouter.props(
    needMention = true,
    CommandMeta
      .routerMap(commands :+ ExampleHelpCommand.cmdMeta(CommandMeta.helpCmdMap(allCommands), client), client), //This method on CommandMeta wires up our command handling for us
    ExampleErrorHandler.props(client, allCommandNames)
  )

  //This command need to be handled for itself to avoid a deadlock
  val killCommandDispatcher: ActorRef = system.actorOf(
    CommandRouter
      .props(
        needMention = true,
        CommandMeta.routerMap(Seq(KillCommand.cmdMeta(self, client)), client),
        IgnoreUnknownErrorHandler.props(client)
      ),
    "KillCommand"
  )

  //We place the command dispatcher behind a guild dispatcher, this way each guild gets it's own command dispatcher
  var guildDispatcherCommands: ActorRef =
    context.actorOf(GuildRouter.props(commandDispatcher, None), "BaseCommands")

  var guildDispatcherMusic: ActorRef =
    context.actorOf(GuildRouter.props(MusicHandler.props(client) _, None), "MusicHandler")

  eventStream.subscribe(guildDispatcherCommands, classOf[APIMessage.MessageCreate])
  eventStream.subscribe(killCommandDispatcher, classOf[APIMessage.MessageCreate])
  eventStream.subscribe(guildDispatcherMusic, classOf[APIMessage.MessageCreate])
  eventStream.subscribe(guildDispatcherMusic, classOf[APIMessage.VoiceServerUpdate])
  eventStream.subscribe(guildDispatcherMusic, classOf[APIMessage.VoiceStateUpdate])
  client ! DiscordClient.StartClient

  private var shutdownCount = 0
  private var shutdownInitiator: ActorRef = _

  override def preStart(): Unit = {
    context.watch(client)
    context.watch(guildDispatcherMusic)
    context.watch(guildDispatcherCommands)
  }

  override def receive: Receive = {
    case DiscordClient.ShutdownClient =>
      shutdownInitiator = sender()
      client ! DiscordClient.ShutdownClient
      context.stop(guildDispatcherCommands)
      guildDispatcherMusic ! DiscordClient.ShutdownClient
    case Terminated(act) if shutdownInitiator != null =>
      shutdownCount += 1
      log.info("Actor shut down: {} Shutdown count: {}", act.path, shutdownCount)
      if (shutdownCount == 3) {
        context.stop(self)
      }
    case Terminated(ref) if ref == guildDispatcherCommands =>
      log.warning("Guild command dispatcher went down. Restarting")
      guildDispatcherCommands = context.actorOf(GuildRouter.props(commandDispatcher, None), "BaseCommands")
    case Terminated(ref) if ref == guildDispatcherMusic =>
      log.warning("Guild music dispatcher went down. Restarting")
      guildDispatcherMusic = context.actorOf(GuildRouter.props(MusicHandler.props(client) _, None), "MusicHandler")
    case Terminated(ref) if ref == client =>
      log.warning("Discord client actor went down. Trying to restart")
      DiscordClient.fetchWsGateway.map(settings.connect(eventStream, _)).onComplete {
        case Success(clientActor) => client = clientActor
        case Failure(e) =>
          println("Could not connect to Discord")
          throw e
      }
  }
}
object ExampleMain {
  def props(settings: ClientSettings, eventStream: EventStream, client: ClientActor)(
      implicit materializer: Materializer
  ): Props =
    Props(new ExampleMain(settings, eventStream, client))
}
