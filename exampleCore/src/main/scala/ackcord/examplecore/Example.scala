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

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

import ackcord._
import ackcord.cachehandlers.CacheTypeRegistry
import ackcord.commands._
import ackcord.examplecore.ExampleMain.NewCommandsEntry
import ackcord.examplecore.music.MusicHandler
import ackcord.gateway.GatewayEvent
import ackcord.gateway.GatewaySettings
import ackcord.requests.{BotAuthentication, RequestHelper}
import ackcord.util.GuildRouter
import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, CoordinatedShutdown, Props, Status, Terminated}
import akka.stream.scaladsl.Keep
import akka.pattern.ask
import akka.pattern.gracefulStop
import akka.stream.{KillSwitches, SharedKillSwitch}
import akka.util.Timeout
import cats.arrow.FunctionK

object Example {

  implicit val system: ActorSystem = ActorSystem("AckCord")
  import system.dispatcher

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("Please specify a token")
      sys.exit()
    }

    val cache = Cache.create
    val token = args.head

    val settings = GatewaySettings(token = token)
    DiscordShard.fetchWsGateway
      .map(
        DiscordShard.connect(
          _,
          settings,
          cache,
          "DiscordShard",
          //We can set some gateway events here that we want AckCord to completely
          //ignore. For anything listed here, the JSON will never be deserialized,
          //and it will be like as if they weren't sent.
          ignoredEvents = Seq(
            classOf[GatewayEvent.PresenceUpdate],
            classOf[GatewayEvent.TypingStart]
          ),
          //In addition to setting events that will be ignored, we can also
          //set data types that we don't want the cache to deal with.
          //This will for the most part help us save RAM.
          //This will for example kick in the GuildCreate event, which includes
          //presences.
          cacheTypeRegistry = CacheTypeRegistry.noPresences
        )
      )
      .onComplete {
        case Success(shardActor) =>
          system.actorOf(ExampleMain.props(settings, cache, shardActor), "Main")
        case Failure(e) =>
          println("Could not connect to Discord")
          throw e
      }
  }
}

class ExampleMain(settings: GatewaySettings, cache: Cache, shard: ActorRef) extends Actor with ActorLogging {
  implicit val system: ActorSystem = context.system
  import context.dispatcher

  val requests: RequestHelper =
    RequestHelper.create(
      BotAuthentication(settings.token),
      millisecondPrecision = false, //My system is pretty bad at syncing stuff up, so I need to be very generous when it comes to ratelimits
      relativeTime = true
    )

  val genericCmds: Seq[ParsedCmdFactory[_, NotUsed]] = {
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
      NewCommandsEntry(controller.hello, newcommands.CommandDescription("Hello", "Say hello")),
      NewCommandsEntry(controller.copy, newcommands.CommandDescription("Copy", "Make the bot say what you said")),
      NewCommandsEntry(
        controller.guildInfo,
        newcommands.CommandDescription("Guild info", "Prints info about the current guild")
      ),
      NewCommandsEntry(
        controller.parsingNumbers,
        newcommands.CommandDescription("Parse numbers", "Have the bot parse two numbers")
      ),
      NewCommandsEntry(
        controller.adminsOnly,
        newcommands.CommandDescription("Elevanted command", "Command only admins can use")
      ),
      NewCommandsEntry(
        controller.timeDiff,
        newcommands.CommandDescription("Time diff", "Checks the time between sending and seeing a message")
      ),
      NewCommandsEntry(controller.ping, newcommands.CommandDescription("Ping", "Checks if the bot is alive")),
      NewCommandsEntry(
        controller.maybeFail,
        newcommands.CommandDescription("MaybeFail", "A command that sometimes fails and throws an exception")
      )
    )
  }

  val helpCmdActor: ActorRef = context.actorOf(ExampleHelpCmd.props(requests, self), "HelpCmd")
  val helpCmd                = ExampleHelpCmdFactory(helpCmdActor)

  val killSwitch: SharedKillSwitch = KillSwitches.shared("Commands")

  //We set up a commands object, which parses potential commands
  //If you wanted to be fancy, you could use a valve here to stop new commands when shutting down
  //Here we just shut everything down at once
  val cmdObj: Commands =
    CoreCommands
      .create(
        CommandSettings(needsMention = true, prefixes = Set("!", "&")),
        cache.subscribeAPI.via(killSwitch.flow),
        requests
      )
      ._2

  val commandConnector = new newcommands.CommandConnector(
    cache.subscribeAPI
      .collectType[APIMessage.MessageCreate]
      .map(m => m.message -> m.cache.current)
      .via(killSwitch.flow),
    requests
  )

  def registerCmd[Mat](parsedCmdFactory: ParsedCmdFactory[_, Mat]): Mat =
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
    val registerCmdObj = new FunctionK[MusicHandler.MatCmdFactory, cats.Id] {
      override def apply[A](fa: MusicHandler.MatCmdFactory[A]): A = registerCmd(fa)
    }

    context.actorOf(
      GuildRouter.props(
        MusicHandler.props(requests, registerCmdObj, cache),
        None
      ),
      "MusicHandler"
    )
  }

  //TODO: Complete this before shutting down guildRouterMusic
  cache.subscribeAPIActor(guildRouterMusic, DiscordShard.StopShard, Status.Failure)(classOf[APIMessage.Ready])
  shard ! DiscordShard.StartShard

  private var shutdownCount        = 0
  private var isShuttingDown       = false
  private var tempSender: ActorRef = _

  private val shutdown = CoordinatedShutdown(system)

  shutdown.addTask("before-service-unbind", "begin-deathwatch") { () =>
    implicit val timeout: Timeout = Timeout(shutdown.timeout("before-service-unbind"))
    (self ? ExampleMain.BeginDeathwatch).mapTo[Done]
  }

  shutdown.addTask("service-unbind", "unregister-commands") { () =>
    implicit val timeout: Timeout = Timeout(shutdown.timeout("service-unbind"))
    (self ? ExampleMain.UnregisterCommands).mapTo[Done]
  }

  shutdown.addTask("service-requests-done", "stop-help-command") { () =>
    val timeout = shutdown.timeout("service-requests-done")
    gracefulStop(helpCmdActor, timeout).map(_ => Done)
  }

  shutdown.addTask("service-requests-done", "stop-music") { () =>
    implicit val timeout: Timeout = Timeout(shutdown.timeout("service-requests-done"))
    (self ? ExampleMain.StopMusic).mapTo[Done]
  }

  shutdown.addTask("service-stop", "stop-discord") { () =>
    implicit val timeout: Timeout = Timeout(shutdown.timeout("service-stop"))
    (self ? DiscordShard.StopShard).mapTo[Done]
  }

  override def receive: Receive = {
    case DiscordShard.RestartShard =>
      shard.forward(DiscordShard.RestartShard)

    case HelpCmd.NoCommandsRemaining =>
      if (isShuttingDown) {
        tempSender ! Done
        tempSender = null
      }
    case HelpCmd.CommandTerminated(_) => //Ignore

    case ExampleMain.BeginDeathwatch =>
      isShuttingDown = true

      context.watch(shard)
      context.watch(guildRouterMusic)

      sender() ! Done

    case ExampleMain.UnregisterCommands =>
      tempSender = sender()
      killSwitch.shutdown()

    case ExampleMain.StopMusic =>
      tempSender = sender()
      guildRouterMusic ! DiscordShard.StopShard

    case DiscordShard.StopShard =>
      tempSender = sender()
      shard ! DiscordShard.StopShard

    case Terminated(act) if isShuttingDown =>
      shutdownCount += 1
      log.info("Actor shut down: {} Shutdown count: {}", act.path, shutdownCount)

      tempSender ! Done
      tempSender = null

      if (shutdownCount == 2) {
        //context.stop(self)
      }
  }
}
object ExampleMain {
  def props(settings: GatewaySettings, cache: Cache, shard: ActorRef): Props =
    Props(new ExampleMain(settings, cache, shard))

  def registerCmd[Mat](commands: Commands, helpActor: ActorRef)(
      parsedCmdFactory: ParsedCmdFactory[_, Mat]
  ): Mat = {
    val (complete, materialized) = commands.subscribe(parsedCmdFactory)(Keep.both)
    (parsedCmdFactory.refiner, parsedCmdFactory.description) match {
      case (info: AbstractCmdInfo, Some(description)) => helpActor ! HelpCmd.AddCmd(info, description, complete)
      case _                                          =>
    }
    import scala.concurrent.ExecutionContext.Implicits.global
    complete.foreach { _ =>
      println(s"Command completed: ${parsedCmdFactory.description.get.name}")
    }
    materialized
  }

  case object BeginDeathwatch
  case object UnregisterCommands
  case object StopMusic

  //Ass of now, you are still responsible for binding the command logic to names and descriptions yourself
  case class NewCommandsEntry[Mat](
      command: newcommands.NamedComplexCommand[_, Mat],
      description: newcommands.CommandDescription
  )

  def registerNewCommand[Mat](connector: newcommands.CommandConnector, helpActor: ActorRef)(
      entry: NewCommandsEntry[Mat]
  ): Mat = {
    val (materialized, complete) =
      connector.runNewNamedCommand(entry.command)

    //Due to the new commands being a complete break from the old ones, being
    // completely incompatible with some other stuff, we need to do a bit of
    // translation and hackery here
    helpActor ! HelpCmd.AddCmd(
      commands.CmdInfo(entry.command.symbol, entry.command.aliases),
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
