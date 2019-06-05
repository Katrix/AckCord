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
package ackcord.examplecore

import scala.util.{Failure, Success}

import ackcord._
import ackcord.commands._
import ackcord.examplecore.ExampleMain.NewCommandsEntry
import ackcord.examplecore.music.MusicHandler
import ackcord.gateway.GatewaySettings
import ackcord.requests.{BotAuthentication, RequestHelper}
import ackcord.util.GuildRouter
import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.event.slf4j.Logger
import akka.stream.scaladsl.Keep
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer, Supervision}
import cats.arrow.FunctionK

object Example {

  val streamLogger = Logger("StreamLogger")

  val loggingDecider: Supervision.Decider = { e =>
    streamLogger.error("Unhandled exception in stream", e)
    Supervision.Resume
  }

  implicit val system: ActorSystem = ActorSystem("AckCord")
  implicit val mat: Materializer = ActorMaterializer(
    ActorMaterializerSettings(system).withSupervisionStrategy(loggingDecider)
  )
  import system.dispatcher

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("Please specify a token")
      sys.exit()
    }

    val cache = Cache.create
    val token = args.head

    val settings = GatewaySettings(token = token)
    DiscordShard.fetchWsGateway.map(DiscordShard.connect(_, settings, cache, "DiscordShard")).onComplete {
      case Success(shardActor) =>
        system.actorOf(ExampleMain.props(settings, cache, shardActor), "Main")
      case Failure(e) =>
        println("Could not connect to Discord")
        throw e
    }
  }
}

class ExampleMain(settings: GatewaySettings, cache: Cache, shard: ActorRef) extends Actor with ActorLogging {
  import cache.mat
  implicit val system: ActorSystem = context.system

  val requests: RequestHelper = RequestHelper.create(BotAuthentication(settings.token))

  val genericCmds: Seq[ParsedCmdFactory[Id, _, NotUsed]] = {
    import GenericCommands._
    Seq(
      PingCmdFactory,
      SendFileCmdFactory,
      InfoChannelCmdFactory,
      TimeDiffCmdFactory,
      RatelimitTestCmdFactory(
        "Ratelimit test",
        Seq("ratelimitTest"),
        requests.sinkIgnore
      ),
      RatelimitTestCmdFactory(
        "Ordered Ratelimit test",
        Seq("ratelimitTestOrdered"),
        requests.sinkIgnore(RequestHelper.RequestProperties.ordered)
      ),
      KillCmdFactory(self)
    )
  }

  val controllerCommands: Seq[NewCommandsEntry[NotUsed]] = {
    val controller = new NewCommandsController(requests)
    Seq(
      NewCommandsEntry("%", Seq("hello"), controller.hello, newcommands.CommandDescription("Hello", "Say hello")),
      NewCommandsEntry(
        "%",
        Seq("copy"),
        controller.copy,
        newcommands.CommandDescription("Copy", "Make the bot say what you said")
      ),
      NewCommandsEntry(
        "%",
        Seq("parseNum"),
        controller.parsingNumbers,
        newcommands.CommandDescription("Parse numbers", "Have the bot parse two numbers")
      ),
      NewCommandsEntry(
        "%",
        Seq("adminOnly"),
        controller.adminsOnly,
        newcommands.CommandDescription("Elevanted command", "Command only admins can use")
      ),
      NewCommandsEntry(
        "%",
        Seq("timeDiff"),
        controller.timeDiff,
        newcommands.CommandDescription("Time diff", "Checks the time between sending and seeing a message")
      ),
      NewCommandsEntry(
        "%",
        Seq("ping"),
        controller.ping,
        newcommands.CommandDescription("Ping", "Checks if the bot is alive")
      )
    )
  }

  val helpCmdActor: ActorRef = context.actorOf(ExampleHelpCmd.props(requests), "HelpCmd")
  val helpCmd                = ExampleHelpCmdFactory(helpCmdActor)

  //We set up a commands object, which parses potential commands
  val cmdObj: Commands[Id] =
    CoreCommands.create(
      CommandSettings(needsMention = true, prefixes = Set("!", "&")),
      cache,
      requests
    )

  val commandConnector = new newcommands.CommandConnector[Id](
    cache.subscribeAPI.collectType[APIMessage.MessageCreate].map(m => m.message -> m.cache.current),
    requests
  )

  def registerCmd[Mat](parsedCmdFactory: ParsedCmdFactory[Id, _, Mat]): Mat =
    ExampleMain.registerCmd(cmdObj, helpCmdActor)(parsedCmdFactory)

  def registerNewCommand[Mat](entry: NewCommandsEntry[Mat]): Mat =
    ExampleMain.registerNewCommand(commandConnector, helpCmdActor)(entry)

  genericCmds.foreach(registerCmd)
  controllerCommands.foreach(registerNewCommand)
  registerCmd(helpCmd)

  //Here is an example for a raw simple command
  cmdObj.subscribeRaw
    .collect {
      case RawCmd(_, "!", "restart", _, _) => println("Restart Starting")
    }
    .runForeach(_ => self ! DiscordShard.RestartShard)

  val guildRouterMusic: ActorRef = {
    val registerCmdObj = new FunctionK[MusicHandler.MatCmdFactory, Id] {
      override def apply[A](fa: MusicHandler.MatCmdFactory[A]): Id[A] = registerCmd(fa)
    }

    context.actorOf(
      GuildRouter.props(
        MusicHandler.props(requests, registerCmdObj, cache),
        None
      ),
      "MusicHandler"
    )
  }

  cache.subscribeAPIActor(guildRouterMusic, DiscordShard.StopShard)(classOf[APIMessage.Ready])
  shard ! DiscordShard.StartShard

  private var shutdownCount  = 0
  private var isShuttingDown = false

  override def receive: Receive = {
    case DiscordShard.RestartShard =>
      shard.forward(DiscordShard.RestartShard)

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
  def props(settings: GatewaySettings, cache: Cache, shard: ActorRef): Props =
    Props(new ExampleMain(settings, cache, shard))

  def registerCmd[Mat](commands: Commands[Id], helpCmdActor: ActorRef)(
      parsedCmdFactory: ParsedCmdFactory[Id, _, Mat]
  ): Mat = {
    val (complete, materialized) = commands.subscribe(parsedCmdFactory)(Keep.both)
    (parsedCmdFactory.refiner, parsedCmdFactory.description) match {
      case (info: AbstractCmdInfo[Id], Some(description)) => helpCmdActor ! HelpCmd.AddCmd(info, description, complete)
      case _                                              =>
    }
    import scala.concurrent.ExecutionContext.Implicits.global
    complete.foreach { _ =>
      println(s"Command completed: ${parsedCmdFactory.description.get.name}")
    }
    materialized
  }

  //Ass of now, you are still responsible for binding the command logic to names and descriptions yourself
  case class NewCommandsEntry[Mat](
      symbol: String,
      aliases: Seq[String],
      command: newcommands.Command[Id, _, Mat],
      description: newcommands.CommandDescription
  )

  def registerNewCommand[Mat](connector: newcommands.CommandConnector[Id], helpCmdActor: ActorRef)(
      entry: NewCommandsEntry[Mat]
  ): Mat = {
    val (materialized, complete) =
      connector.runNewCommand(connector.prefix(entry.symbol, entry.aliases, mustMention = true), entry.command)

    //Due to the new commands being a complete break from the old ones, being
    // completely incompatible with some other stuff, we need to do a bit of
    // translation and hackery here
    helpCmdActor ! HelpCmd.AddCmd(
      commands.CmdInfo(entry.symbol, entry.aliases),
      commands.CmdDescription(
        entry.description.name,
        entry.description.description,
        entry.description.usage,
        entry.description.extra
      ),
      complete
    )
    import scala.concurrent.ExecutionContext.Implicits.global
    complete.foreach { _ =>
      println(s"Command completed: ${entry.description.name}")
    }
    materialized
  }
}
