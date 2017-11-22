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

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.stream.scaladsl.Keep
import akka.stream.{ActorMaterializer, Materializer}
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.commands.{Commands, HelpCmd, ParsedCmdFactory}
import net.katsstuff.ackcord.example.music._
import net.katsstuff.ackcord.util.GuildRouter
import net.katsstuff.ackcord.{APIMessage, Cache, ClientSettings, DiscordClient}

object Example {

  implicit val system: ActorSystem  = ActorSystem("AckCord")
  implicit val mat:    Materializer = ActorMaterializer()
  import system.dispatcher

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("Please specify a token")
      sys.exit()
    }

    val cache = Cache.create
    val token = args.head

    val settings = ClientSettings(token = token)
    DiscordClient.fetchWsGateway.map(settings.connect(_, cache)).onComplete {
      case Success(clientActor) =>
        system.actorOf(ExampleMain.props(settings, cache, clientActor), "Main")
      case Failure(e) =>
        println("Could not connect to Discord")
        throw e
    }
  }
}

class ExampleMain(settings: ClientSettings, cache: Cache, var client: ClientActor)(implicit materializer: Materializer)
    extends Actor
    with ActorLogging {
  implicit val system: ActorSystem = context.system

  val genericCmds: Seq[ParsedCmdFactory[_, NotUsed]] = Seq(
    PingCmdFactory,
    SendFileCmdFactory,
    InfoChannelCmdFactory,
    KillCmdFactory(system.actorOf(KillCmd.props(self), "KillCmd")) //We use system.actorOf to keep the actor alive when this actor shuts down
  )
  val helpCmdActor: ActorRef = context.actorOf(ExampleHelpCmd.props(settings.token), "HelpCmd")
  val helpCmd = ExampleHelpCmdFactory(helpCmdActor)

  //We set up a commands object, which parses potential commands
  val commands: Commands =
    Commands.create(
      needMention = true,
      categories = Set(ExampleCmdCategories.!, ExampleCmdCategories.&),
      cache,
      settings.token
    )

  def registerCmd[Mat](parsedCmdFactory: ParsedCmdFactory[_, Mat]): Mat =
    ExampleMain.registerCmd(commands, helpCmdActor)(parsedCmdFactory)

  genericCmds.foreach(registerCmd)
  registerCmd(helpCmd)

  var guildRouterMusic: ActorRef =
    context.actorOf(
      GuildRouter.props(MusicHandler.props(client, settings.token, commands, helpCmdActor) _, None),
      "MusicHandler"
    )

  cache.subscribeAPIActor(guildRouterMusic, "Completed", classOf[APIMessage.Ready])
  cache.subscribeAPIActor(guildRouterMusic, "Completed", classOf[APIMessage.VoiceServerUpdate])
  cache.subscribeAPIActor(guildRouterMusic, "Completed", classOf[APIMessage.VoiceStateUpdate])
  client ! DiscordClient.StartClient

  private var shutdownCount  = 0
  private var isShuttingDown = false

  override def receive: Receive = {
    case DiscordClient.ShutdownClient =>
      isShuttingDown = true

      context.watch(client)
      context.watch(guildRouterMusic)

      client ! DiscordClient.ShutdownClient
      guildRouterMusic ! DiscordClient.ShutdownClient
    case Terminated(act) if isShuttingDown =>
      shutdownCount += 1
      log.info("Actor shut down: {} Shutdown count: {}", act.path, shutdownCount)
      if (shutdownCount == 2) {
        context.stop(self)
      }
  }
}
object ExampleMain {
  def props(settings: ClientSettings, cache: Cache, client: ClientActor)(implicit materializer: Materializer): Props =
    Props(new ExampleMain(settings, cache, client))

  def registerCmd[Mat](commands: Commands, helpCmdActor: ActorRef)(
      parsedCmdFactory: ParsedCmdFactory[_, Mat]
  )(implicit system: ActorSystem, mat: Materializer): Mat = {
    val (complete, materialized) = commands.subscribe(parsedCmdFactory)(Keep.both)
    helpCmdActor ! HelpCmd.AddCmd(parsedCmdFactory, complete)
    materialized
  }
}
