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
import ackcord.commands.{HelpCommand, PrefixParser}
import ackcord.examplecore.music.MusicHandler
import ackcord.gateway.{GatewayEvent, GatewaySettings}
import ackcord.requests.{BotAuthentication, Ratelimiter, Requests}
import ackcord.util.{APIGuildRouter, GuildRouter}
import akka.actor.CoordinatedShutdown
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.adapter._
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
  implicit val system: ActorSystem[Nothing] = context.system
  import ExampleMain._

  private val events = Events.create(
    //We can set some gateway events here that we want AckCord to completely
    //ignore. For anything listed here, the JSON will never be deserialized.
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

  private val wsUri =
    try {
      Await.result(DiscordShard.fetchWsGateway, 30.seconds)
    } catch {
      case NonFatal(e) =>
        println("Could not connect to Discord")
        throw e
    }

  private val shard       = context.spawn(DiscordShard(wsUri, settings, events), "DiscordShard")
  private val ratelimiter = context.spawn(Ratelimiter(), "Ratelimiter")

  private val requests: Requests =
    new Requests(
      BotAuthentication(settings.token),
      ratelimiter,
      millisecondPrecision =
        false, //My system is pretty bad at syncing stuff up, so I need to be very generous when it comes to ratelimits
      relativeTime = true
    )

  val controllerCommands: Seq[CommandsEntry[NotUsed]] = {
    val controller = new NewCommandsController(requests)
    Seq(
      CommandsEntry(controller.hello, commands.CommandDescription("Hello", "Say hello")),
      CommandsEntry(controller.copy, commands.CommandDescription("Copy", "Make the bot say what you said")),
      CommandsEntry(
        controller.guildInfo,
        commands.CommandDescription("Guild info", "Prints info about the current guild")
      ),
      CommandsEntry(
        controller.parsingNumbers,
        commands.CommandDescription("Parse numbers", "Have the bot parse two numbers")
      ),
      CommandsEntry(
        controller.sendFile,
        commands.CommandDescription("Send file", "Send a file in an embed")
      ),
      CommandsEntry(
        controller.adminsOnly,
        commands.CommandDescription("Elevanted command", "Command only admins can use")
      ),
      CommandsEntry(
        controller.timeDiff,
        commands.CommandDescription("Time diff", "Checks the time between sending and seeing a message")
      ),
      CommandsEntry(controller.ping, commands.CommandDescription("Ping", "Checks if the bot is alive")),
      CommandsEntry(
        controller.maybeFail,
        commands.CommandDescription("MaybeFail", "A command that sometimes fails and throws an exception")
      ),
      CommandsEntry(
        controller.ratelimitTest("ratelimitTest", requests.sinkIgnore[Any]),
        commands.CommandDescription("Ratelimit test", "Checks that ratelimiting is working as intended")
      ),
      CommandsEntry(
        controller.ratelimitTest("ratelimitTestOrdered", requests.sinkIgnore[Any](Requests.RequestProperties.ordered)),
        commands.CommandDescription("Ratelimit test", "Checks that ratelimiting is working as intended")
      ),
      CommandsEntry(
        controller.kill,
        commands.CommandDescription("Kill", "Kills the bot")
      )
    )
  }

  val helpCommand = new ExampleHelpCommand(requests)

  val killSwitch: SharedKillSwitch = KillSwitches.shared("Commands")

  val commandConnector = new commands.CommandConnector(
    events.subscribeAPI
      .collectType[APIMessage.MessageCreate]
      .map(m => m.message -> m.cache.current)
      .via(killSwitch.flow),
    requests,
    requests.parallelism
  )

  def registerCommand[Mat](entry: CommandsEntry[Mat]): Mat =
    ExampleMain.registerNewCommand(commandConnector, helpCommand)(entry)

  controllerCommands.foreach(registerCommand)
  registerCommand(
    CommandsEntry(
      helpCommand.command.toNamed(PrefixParser.structured(needsMention = true, Seq("!"), Seq("help"))),
      commands.CommandDescription("Help", "This command right here", "[query]|[page]")
    )
  )

  private val registerCmdObjMusic = new FunctionK[CommandsEntry, cats.Id] {
    override def apply[A](fa: CommandsEntry[A]): A = registerCommand(fa)
  }

  val guildRouterMusic: ActorRef[GuildRouter.Command[APIMessage, MusicHandler.Command]] = {
    context.spawn(
      APIGuildRouter.partitioner(
        None,
        MusicHandler(requests, registerCmdObjMusic, events),
        None,
        GuildRouter.OnShutdownSendMsg(MusicHandler.Shutdown)
      ),
      "MusicHandler"
    )
  }

  val killSwitchMusicHandler: UniqueKillSwitch = events.subscribeAPI
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
  sealed trait Command

  case class BeginDeathwatch(replyTo: ActorRef[Done])    extends Command
  case class UnregisterCommands(replyTo: ActorRef[Done]) extends Command
  case class StopMusic(replyTo: ActorRef[Done])          extends Command
  case class StopShard(replyTo: ActorRef[Done])          extends Command
  case object RestartShard                               extends Command
  case object CommandsUnregistered                       extends Command

  //Ass of now, you are still responsible for binding the command logic to names and descriptions yourself
  case class CommandsEntry[Mat](
      command: commands.NamedComplexCommand[_, Mat],
      description: commands.CommandDescription
  )

  def registerNewCommand[Mat](connector: commands.CommandConnector, helpCommand: HelpCommand)(
      entry: CommandsEntry[Mat]
  ): Mat = {
    val registration = connector.runNewNamedCommand(entry.command)
    helpCommand.registerCommand(entry.command.prefixParser, entry.description, registration.onDone)

    import scala.concurrent.ExecutionContext.Implicits.global
    registration.onDone.foreach(_ => println(s"Command completed: ${entry.description.name}"))
    registration.materialized
  }
}
