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
import net.katsstuff.ackcord.DiscordShard.ShardActor
import net.katsstuff.ackcord.commands.{Commands, HelpCmd, ParsedCmdFactory}
import net.katsstuff.ackcord.example.music._
import net.katsstuff.ackcord.http.requests.{BotAuthentication, RequestHelper}
import net.katsstuff.ackcord.util.GuildRouter
import net.katsstuff.ackcord.{APIMessage, Cache, ClientSettings, DiscordShard}

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
    DiscordShard.fetchWsGateway.map(settings.connect(_, cache, "DiscordShard")).onComplete {
      case Success(shardActor) =>
        system.actorOf(ExampleMain.props(settings, cache, shardActor), "Main")
      case Failure(e) =>
        println("Could not connect to Discord")
        throw e
    }
  }
}

class ExampleMain(settings: ClientSettings, cache: Cache, shard: ShardActor) extends Actor with ActorLogging {
  import cache.mat
  implicit val system: ActorSystem = context.system

  val requests = RequestHelper(BotAuthentication(settings.token))

  val genericCmds: Seq[ParsedCmdFactory[_, NotUsed]] = {
    import commands._
    Seq(
      PingCmdFactory,
      SendFileCmdFactory,
      InfoChannelCmdFactory,
      TimeDiffCmdFactory,
      RatelimitTestCmdFactory,
      KillCmdFactory(self)
    )
  }
  val helpCmdActor: ActorRef = context.actorOf(ExampleHelpCmd.props(requests), "HelpCmd")
  val helpCmd = ExampleHelpCmdFactory(helpCmdActor)

  //We set up a commands object, which parses potential commands
  val cmdObj: Commands =
    Commands.create(
      needMention = true,
      categories = Set(ExampleCmdCategories.!, ExampleCmdCategories.&),
      cache,
      requests
    )

  def registerCmd[Mat](parsedCmdFactory: ParsedCmdFactory[_, Mat]): Mat =
    ExampleMain.registerCmd(cmdObj, helpCmdActor)(parsedCmdFactory)

  genericCmds.foreach(registerCmd)
  registerCmd(helpCmd)

  var guildRouterMusic: ActorRef =
    context.actorOf(
      GuildRouter.props(MusicHandler.props(shard, requests, cmdObj, helpCmdActor, cache), None),
      "MusicHandler"
    )

  cache.subscribeAPIActor(
    guildRouterMusic,
    DiscordShard.StopShard,
    classOf[APIMessage.Ready],
    classOf[APIMessage.VoiceServerUpdate],
    classOf[APIMessage.VoiceStateUpdate]
  )
  shard ! DiscordShard.StartShard

  private var shutdownCount  = 0
  private var isShuttingDown = false

  override def receive: Receive = {
    case DiscordShard.StopShard =>
      isShuttingDown = true

      context.watch(shard)
      context.watch(guildRouterMusic)

      shard ! DiscordShard.StopShard
      guildRouterMusic ! DiscordShard.StopShard
    case Terminated(act) if isShuttingDown =>
      shutdownCount += 1
      log.info("Actor shut down: {} Shutdown count: {}", act.path, shutdownCount)
      if (shutdownCount == 2) {
        context.stop(self)
      }
  }
}
object ExampleMain {
  def props(settings: ClientSettings, cache: Cache, shard: ShardActor): Props =
    Props(new ExampleMain(settings, cache, shard))

  def registerCmd[Mat](commands: Commands, helpCmdActor: ActorRef)(parsedCmdFactory: ParsedCmdFactory[_, Mat]): Mat = {
    val (complete, materialized) = commands.subscribe(parsedCmdFactory)(Keep.both)
    helpCmdActor ! HelpCmd.AddCmd(parsedCmdFactory, complete)
    materialized
  }
}
