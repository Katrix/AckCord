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

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal

import ackcord._
import ackcord.cachehandlers.CacheTypeRegistry
import ackcord.commands._
import ackcord.data.{GuildId, RawSnowflake}
import ackcord.examplecore.music.MusicHandler
import ackcord.gateway.{GatewayEvent, GatewaySettings}
import ackcord.requests.{BotAuthentication, Ratelimiter, RequestSettings, Requests}
import ackcord.slashcommands.CommandRegistrar
import ackcord.slashcommands.raw.{GetGuildCommands, RawInteraction}
import ackcord.util.{APIGuildRouter, GuildRouter}
import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.adapter._
import akka.stream.scaladsl.Keep
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{KillSwitches, SharedKillSwitch, UniqueKillSwitch}
import akka.util.Timeout
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
  import system.executionContext

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
      RequestSettings(
        Some(BotAuthentication(settings.token)),
        ratelimiter,
        relativeTime = true
      )
    )

  val controllerCommands: Seq[NamedDescribedCommand[_]] = {
    val controller = new CommandsController(requests)
    Seq(
      controller.hello,
      controller.copy,
      controller.setShouldMention,
      controller.modifyPrefixSymbols,
      controller.guildInfo,
      controller.sendFile,
      controller.adminsOnly,
      controller.timeDiff,
      controller.ping,
      controller.maybeFail,
      controller.ratelimitTest("ratelimitTest", requests.sinkIgnore[Any]),
      controller.ratelimitTest("ratelimitTestOrdered", requests.sinkIgnore[Any](Requests.RequestProperties.ordered)),
      controller.kill
    )
  }

  events.subscribeAPI.runForeach {
    case mc: APIMessage.MessageCreate =>
      import ackcord.syntax._
      if (mc.message.content.startsWith("Msg")) {
        requests.singleIgnore(mc.message.createReaction("❌"))(Requests.RequestProperties.retry)
      }
    case _ =>
  }

  val helpCommand = new ExampleHelpCommand(requests)

  val killSwitch: SharedKillSwitch = KillSwitches.shared("Commands")

  val baseSlashCommands = new SlashCommandsController(requests)

  val registerCommands = true
  if (registerCommands) {
    requests
      .singleFuture(GetGuildCommands(RawSnowflake("288367502130413568"), GuildId("269988507378909186")))
      .onComplete(println)

    /*
    CommandRegistrar
      .createGuildCommands(
        RawSnowflake("288367502130413568"),
        GuildId("269988507378909186"),
        requests,
        baseSlashCommands.groupTest,
        baseSlashCommands.subcommand,
        baseSlashCommands.subcommandWithArg
      )
      .onComplete(println)
     */
  }

  {
    import ackcord.slashcommands.raw.CommandsProtocol._
    events
      .commandInteractions[RawInteraction]
      .to(
        CommandRegistrar.gatewayCommands(
          baseSlashCommands.ping,
          baseSlashCommands.echo,
          baseSlashCommands.nudge,
          baseSlashCommands.asyncTest,
          baseSlashCommands.groupTest,
        )("288367502130413568", requests)
      )
      .run()
  }

  val commandConnector = new CommandConnector(
    events.subscribeAPI
      .collectType[APIMessage.MessageCreate]
      .map(m => m.message -> m.cache.current)
      .via(killSwitch.flow),
    requests,
    requests.settings.parallelism
  )

  def registerCommand[Mat](entry: NamedDescribedComplexCommand[_, Mat]): Mat =
    ExampleMain.registerNewCommand(commandConnector, helpCommand)(entry)

  controllerCommands.foreach(registerCommand)
  registerCommand(
    helpCommand.command
      .toNamed(PrefixParser.structured(needsMention = true, Seq("!"), Seq("help")))
      .toDescribed(CommandDescription("Help", "This command right here", "[query]|[page]"))
  )

  private def registerCmdObjMusic[A]: FunctionK[NamedDescribedComplexCommand[A, *], cats.Id] =
    new FunctionK[NamedDescribedComplexCommand[A, *], cats.Id] {
      override def apply[C](fa: NamedDescribedComplexCommand[A, C]): C = registerCommand(fa)
    }

  val guildRouterMusic: ActorRef[GuildRouter.Command[APIMessage, MusicHandler.Command]] = {
    context.spawn(
      APIGuildRouter.partitioner(
        None,
        MusicHandler(requests, registerCmdObjMusic[Any], events),
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

  def registerNewCommand[Mat](connector: CommandConnector, helpCommand: HelpCommand)(
      command: NamedDescribedComplexCommand[_, Mat]
  )(implicit ec: ExecutionContext): Mat = {
    val registration = connector.runNewNamedCommandWithHelp(command, helpCommand)
    registration.onDone.foreach(_ => println(s"Command completed: ${command.description.name}"))
    registration.materialized
  }
}
