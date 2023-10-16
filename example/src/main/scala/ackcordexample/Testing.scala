package ackcordexample

import ackcord.BotSettings
import ackcord.data._
import ackcord.gateway._
import ackcord.gateway.data.{GatewayDispatchEvent, GatewayEventBase}
import ackcord.interactions.{
  ApplicationCommandController,
  Components,
  CreatedApplicationCommand,
  InteractionsRegistrar,
  SlashCommand
}
import ackcord.requests.{ChannelRequests, Requests}
import cats.effect.std.Console
import cats.effect.{ExitCode, IO, Resource, ResourceApp}
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import sttp.client3.httpclient.cats.HttpClientCatsBackend

object Testing extends ResourceApp {
  class StringCommandProcessor(commands: Map[String, (String, ChannelId) => IO[Unit]], log: Logger[IO])
      extends GatewayProcessHandler.Base[IO]("StringCommandProcessor")
      with DispatchEventProcess[IO] {
    override def onDispatchEvent(event: GatewayDispatchEvent, context: Context): IO[Unit] = event match {
      case messageCreate: GatewayDispatchEvent.MessageCreate =>
        val channelId = messageCreate.message.channelId
        val content   = messageCreate.message.content

        commands
          .find(t => content.startsWith(t._1))
          .map[IO[Unit]](t => t._2(content, channelId))
          .getOrElse(IO.unit)

      case _ => IO.unit
    }
  }

  class PrintlnProcessor[Handler <: GatewayHandler[IO]](console: Console[IO], handlerKey: ContextKey[Handler])
      extends GatewayProcessHandler.Base[IO]("PrintlnProcessor") {
    override def onCreateHandler(context: Context): IO[Unit] =
      console.println(context.access(handlerKey))

    override def onEvent(
        event: GatewayEventBase[_],
        context: Context
    ): IO[Unit] =
      console.println(event)

    override def onDisconnected(behavior: DisconnectBehavior): IO[Unit] =
      console.println(behavior)
  }

  class MyCommands(requests: Requests[IO, Any], components: Components[IO], respondToPing: Boolean)
      extends ApplicationCommandController.Base[IO](requests, components, respondToPing) {
    val infoCommand: SlashCommand[IO, Unit] = SlashCommand.command("info", "Get some info") { invocation =>
      sendMessage(
        MessageData.ofContent("Hello from AckCord 2.0 application commands")
      ).doAsync(invocation) { implicit async =>
        sendAsyncMessage(
          CreateFollowupMessageBody.ofContent("Here's another message")
        )
      }
    }

    val infoCommandAsync: SlashCommand[IO, Unit] = SlashCommand.command("infoAsync", "Get some info async") {
      invocation =>
        async(invocation) { implicit a =>
          sendAsyncMessage(
            CreateFollowupMessageBody.ofContent("Hello from AckCord 2.0 async application commands")
          )
        }
    }

    override val allCommands: Seq[CreatedApplicationCommand[IO]] = Seq(
      infoCommand,
      infoCommandAsync
    )
  }

  override def run(args: List[String]): Resource[IO, ExitCode] = {
    val token = args.head

    BotSettings
      .cats(
        token,
        GatewayIntents.Guilds ++ GatewayIntents.GuildMessages ++ GatewayIntents.MessageContent,
        HttpClientCatsBackend.resource[IO]()
      )
      .mproduct { settings =>
        Resource.eval(
          Components
            .ofCats[IO](settings.requests, respondToPing = false)
            .map(new MyCommands(settings.requests, _, respondToPing = true))
        )
      }
      .map { case (settings, commands) =>
        settings
          .installEventListener { case ready: GatewayDispatchEvent.Ready =>
            InteractionsRegistrar
              .createGuildCommands[IO](
                ready.application.id,
                GuildId("269988507378909186": String),
                settings.requests,
                replaceAll = true,
                commands.allCommands: _*
              )
              .void
          }
          .install(commands)
      }
      .map { settings =>
        val console = Console[IO]
        val req     = settings.requests

        val log = settings.loggerFactory.getLoggerFromClass(this.getClass)

        settings
          .installEventListener { case _: GatewayDispatchEvent.Ready =>
            console.println("Ready")
          }
          .install(
            new PrintlnProcessor(console, settings.handlerFactory.handlerContextKey),
            new StringCommandProcessor(
              Map(
                "!info" -> ((_, channelId) => {
                  req
                    .runRequest(
                      ChannelRequests.createMessage(
                        channelId,
                        ChannelRequests.CreateMessageBody.make20(content = UndefOrSome("Hello from AckCord 2.0"))
                      )
                    )
                    .void
                }),
                "!ratelimitTest " -> ((content, channelId) => {
                  List
                    .tabulate(content.substring("!ratelimitTest ".length).toInt)(i =>
                      ChannelRequests.createMessage(
                        channelId,
                        ChannelRequests.CreateMessageBody
                          .make20(content = UndefOrSome(s"Ratelimit message ${i + 1}"))
                      )
                    )
                    .traverse_(req.runRequest)
                })
              ),
              log
            )
          )
      }
      .flatMap(_.startResource)
  }
}
