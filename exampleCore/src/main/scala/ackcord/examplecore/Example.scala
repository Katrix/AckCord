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

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

import ackcord._
import ackcord.cachehandlers.CacheTypeRegistry
import ackcord.oldcommands._
import ackcord.examplecore.music.MusicHandler
import ackcord.gateway.{GatewayEvent, GatewaySettings}
import ackcord.requests.{BotAuthentication, Ratelimiter, RequestHelper}
import ackcord.util.{APIGuildRouter, GuildRouter}
import akka.actor.CoordinatedShutdown
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.adapter._
import akka.pattern.gracefulStop
import akka.stream.scaladsl.Keep
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{KillSwitches, SharedKillSwitch, UniqueKillSwitch}
import akka.util.Timeout
import akka.{Done, NotUsed}
import cats.arrow.FunctionK
import org.slf4j.Logger

object Example {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("Please specify a token")
      sys.exit()
    }

    val token = args.head

    val settings = GatewaySettings(token = token)
    ActorSystem(Behaviors.setup[ExampleMain.Command](ctx => new ExampleMain(ctx, ctx.log, settings)), "ExampleCore")
  }
}

class ExampleMain(ctx: ActorContext[ExampleMain.Command], log: Logger, settings: GatewaySettings)
    extends AbstractBehavior[ExampleMain.Command](ctx) {
  import context.executionContext
  implicit val system: ActorSystem[Nothing] = context.system
  import ExampleMain._

  private val cache = Cache.create

  private val wsUri = try {
    Await.result(DiscordShard.fetchWsGateway, 30.seconds)
  } catch {
    case NonFatal(e) =>
      println("Could not connect to Discord")
      throw e
  }

  private val shard = context.spawn(
    DiscordShard(
      wsUri,
      settings,
      cache,
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
    ),
    "DiscordShard"
  )

  private val ratelimiter = context.spawn(Ratelimiter(), "Ratelimiter")

  private val requests: RequestHelper =
    new RequestHelper(
      BotAuthentication(settings.token),
      ratelimiter,
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
      KillCmdFactory
    )
  }

  val controllerCommands: Seq[NewCommandsEntry[NotUsed]] = {
    val controller = new NewCommandsController(requests)
    Seq(
      NewCommandsEntry(controller.hello, commands.CommandDescription("Hello", "Say hello")),
      NewCommandsEntry(controller.copy, commands.CommandDescription("Copy", "Make the bot say what you said")),
      NewCommandsEntry(
        controller.guildInfo,
        commands.CommandDescription("Guild info", "Prints info about the current guild")
      ),
      NewCommandsEntry(
        controller.parsingNumbers,
        commands.CommandDescription("Parse numbers", "Have the bot parse two numbers")
      ),
      NewCommandsEntry(
        controller.adminsOnly,
        commands.CommandDescription("Elevanted command", "Command only admins can use")
      ),
      NewCommandsEntry(
        controller.timeDiff,
        commands.CommandDescription("Time diff", "Checks the time between sending and seeing a message")
      ),
      NewCommandsEntry(controller.ping, commands.CommandDescription("Ping", "Checks if the bot is alive")),
      NewCommandsEntry(
        controller.maybeFail,
        commands.CommandDescription("MaybeFail", "A command that sometimes fails and throws an exception")
      )
    )
  }

  private val helpMonitor = context.spawn[HelpCmd.HandlerReply](
    Behaviors.receiveMessage {
      case HelpCmd.CommandTerminated(_) => Behaviors.same
      case HelpCmd.NoCommandsRemaining =>
        context.self ! CommandsUnregistered
        Behaviors.same
    },
    "HelpMonitor"
  )

  val helpCmdActor: ActorRef[ExampleHelpCmd.Command] = context.spawn(ExampleHelpCmd(requests, helpMonitor), "HelpCmd")
  val helpCmd                                        = ExampleHelpCmdFactory(helpCmdActor)

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

  val commandConnector = new commands.CommandConnector(
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
    .runForeach(_ => context.self ! RestartShard)

  private val registerCmdObjMusic = new FunctionK[MusicHandler.MatCmdFactory, cats.Id] {
    override def apply[A](fa: MusicHandler.MatCmdFactory[A]): A = registerCmd(fa)
  }

  val guildRouterMusic: ActorRef[GuildRouter.Command[APIMessage, MusicHandler.Command]] = {
    context.spawn(
      APIGuildRouter.partitioner(
        None,
        MusicHandler(requests, registerCmdObjMusic, cache),
        None,
        GuildRouter.OnShutdownSendMsg(MusicHandler.Shutdown)
      ),
      "MusicHandler"
    )
  }

  val killSwitchMusicHandler: UniqueKillSwitch = cache.subscribeAPI
    .viaMat(KillSwitches.single)(Keep.right)
    .collect {
      case ready: APIMessage.Ready        => GuildRouter.EventMessage(ready)
      case create: APIMessage.GuildCreate => GuildRouter.EventMessage(create)
    }
    .to(ActorSink.actorRef(guildRouterMusic, GuildRouter.Shutdown, _ => GuildRouter.Shutdown))
    .run()
  shard ! DiscordShard.StartShard

  private var shutdownCount              = 0
  private var isShuttingDown             = false
  private var doneSender: ActorRef[Done] = _

  private val shutdown = CoordinatedShutdown(system.toClassic)

  shutdown.addTask("before-service-unbind", "begin-deathwatch") { () =>
    implicit val timeout: Timeout = Timeout(shutdown.timeout("before-service-unbind"))
    context.self.ask[Done](ExampleMain.BeginDeathwatch)
  }

  shutdown.addTask("service-unbind", "unregister-commands") { () =>
    implicit val timeout: Timeout = Timeout(shutdown.timeout("service-unbind"))
    context.self.ask[Done](ExampleMain.UnregisterCommands)
  }

  shutdown.addTask("service-requests-done", "stop-help-command") { () =>
    val timeout = shutdown.timeout("service-requests-done")
    gracefulStop(helpCmdActor.toClassic, timeout).map(_ => Done)
  }

  shutdown.addTask("service-requests-done", "stop-music") { () =>
    implicit val timeout: Timeout = Timeout(shutdown.timeout("service-requests-done"))
    context.self.ask[Done](ExampleMain.StopMusic)
  }

  shutdown.addTask("service-stop", "stop-discord") { () =>
    implicit val timeout: Timeout = Timeout(shutdown.timeout("service-stop"))
    context.self.ask[Done](StopShard)
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case RestartShard =>
        shard ! DiscordShard.RestartShard

      case CommandsUnregistered =>
        if (isShuttingDown) {
          doneSender ! Done
          doneSender = null
        }

      case ExampleMain.BeginDeathwatch(replyTo) =>
        isShuttingDown = true

        context.watch(shard)
        context.watch(guildRouterMusic)

        replyTo ! Done

      case ExampleMain.UnregisterCommands(replyTo) =>
        doneSender = replyTo
        killSwitch.shutdown()

      case ExampleMain.StopMusic(replyTo) =>
        doneSender = replyTo
        killSwitchMusicHandler.shutdown()

      case StopShard(replyTo) =>
        doneSender = replyTo
        shard ! DiscordShard.StopShard
    }

    Behaviors.same
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case Terminated(act) if isShuttingDown =>
      shutdownCount += 1
      log.info("Actor shut down: {} Shutdown count: {}", act.path, shutdownCount)

      doneSender ! Done
      doneSender = null

      if (shutdownCount == 2) {
        //context.stop(self)
      }
      Behaviors.same
  }
}
object ExampleMain {
  def registerCmd[Mat](commands: Commands, helpActor: ActorRef[ExampleHelpCmd.Command])(
      parsedCmdFactory: ParsedCmdFactory[_, Mat]
  ): Mat = {
    val (complete, materialized) = commands.subscribe(parsedCmdFactory)(Keep.both)
    (parsedCmdFactory.refiner, parsedCmdFactory.description) match {
      case (info: AbstractCmdInfo, Some(description)) =>
        helpActor ! ExampleHelpCmd.BaseCommandWrapper(HelpCmd.AddCmd(info, description, complete))
      case _ =>
    }
    import scala.concurrent.ExecutionContext.Implicits.global
    complete.foreach { _ =>
      println(s"Command completed: ${parsedCmdFactory.description.get.name}")
    }
    materialized
  }

  sealed trait Command

  case class BeginDeathwatch(replyTo: ActorRef[Done])    extends Command
  case class UnregisterCommands(replyTo: ActorRef[Done]) extends Command
  case class StopMusic(replyTo: ActorRef[Done])          extends Command
  case class StopShard(replyTo: ActorRef[Done])          extends Command
  case object RestartShard                               extends Command
  case object CommandsUnregistered                       extends Command

  //Ass of now, you are still responsible for binding the command logic to names and descriptions yourself
  case class NewCommandsEntry[Mat](
      command: commands.NamedComplexCommand[_, Mat],
      description: commands.CommandDescription
  )

  def registerNewCommand[Mat](connector: commands.CommandConnector, helpActor: ActorRef[ExampleHelpCmd.Command])(
      entry: NewCommandsEntry[Mat]
  ): Mat = {
    val (materialized, complete) =
      connector.runNewNamedCommand(entry.command)

    //Due to the new commands being a complete break from the old ones, being
    // completely incompatible with some other stuff, we need to do a bit of
    // translation and hackery here
    helpActor ! ExampleHelpCmd.BaseCommandWrapper(
      HelpCmd.AddCmd(
        oldcommands.CmdInfo(entry.command.symbol, entry.command.aliases),
        oldcommands.CmdDescription(
          entry.description.name,
          entry.description.description,
          entry.description.usage,
          entry.description.extra
        ),
        complete
      )
    )
    import scala.concurrent.ExecutionContext.Implicits.global
    complete.foreach { _ =>
      println(s"Command completed: ${entry.description.name}")
    }
    materialized
  }
}
