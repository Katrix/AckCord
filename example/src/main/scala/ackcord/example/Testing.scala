package ackcord.example

import ackcord.BotSettings
import ackcord.data._
import ackcord.gateway._
import ackcord.gateway.data.{GatewayDispatchType, GatewayEvent, GatewayEventBase}
import ackcord.requests.CreateMessageContainer
import cats.effect.std.Console
import cats.effect.{ExitCode, IO, Resource, ResourceApp}
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import sttp.client3.httpclient.cats.HttpClientCatsBackend

object Testing extends ResourceApp {
  class StringCommandProcessor(commands: Map[String, (String, ChannelId) => IO[Unit]], log: Logger[IO])
      extends GatewayProcess.Base[IO] {
    override def name: String = "StringCommandProcessor"

    override def onEvent(
        event: GatewayEventBase[_],
        context: Context
    ): IO[Context] = event match {
      case GatewayEvent.Dispatch(ev) if ev.t == GatewayDispatchType.MessageCreate =>
        val j = ev.d.hcursor

        val res = for {
          channelId <- j.get[ChannelId]("channel_id")
          content   <- j.get[String]("content")
        } yield commands
          .find(t => content.startsWith(t._1))
          .map[IO[Unit]](t => t._2(content, channelId))
          .getOrElse(IO.unit)

        val ret = res.sequence.flatMap(r => IO.fromEither(r))
        ret.onError(e => log.error(e)("Failed to handle command")).as(context)

      case _ => IO.pure(context)
    }
  }

  class PrintlnProcessor[Handler <: GatewayHandler[IO]](console: Console[IO], handlerKey: ContextKey[Handler])
      extends GatewayProcess[IO] {
    override def name: String = "PrintlnProcessor"

    override def onCreateHandler(context: Context): IO[Context] =
      console.println(context.access(handlerKey)).as(context)

    override def onEvent(
        event: GatewayEventBase[_],
        context: Context
    ): IO[Context] =
      console.println(event).as(context)

    override def onDisconnected(behavior: DisconnectBehavior): IO[DisconnectBehavior] =
      console.println(behavior).as(behavior)
  }

  override def run(args: List[String]): Resource[IO, ExitCode] = {
    val token = args.head

    BotSettings
      .cats(
        token,
        GatewayIntents.Guilds ++ GatewayIntents.GuildMessages ++ GatewayIntents.MessageContent,
        HttpClientCatsBackend.resource[IO]()
      )
      .map { settings =>
        val console = Console[IO]
        val req     = settings.requests

        val log = settings.loggerFactory.getLoggerFromClass(this.getClass)

        settings.use(
          new PrintlnProcessor(console, settings.handlerFactory.handlerContextKey),
          new StringCommandProcessor(
            Map(
              "!info" -> ((_, channelId) => {
                req
                  .runRequest(
                    CreateMessageContainer.createMessage(
                      channelId,
                      CreateMessageContainer.CreateMessageBody.make20("Hello from AckCord 2.0")
                    )
                  )
                  .void
              }),
              "!ratelimitTest " -> ((content, channelId) => {
                List
                  .tabulate(content.substring("!ratelimitTest ".length).toInt)(i =>
                    CreateMessageContainer.createMessage(
                      channelId,
                      CreateMessageContainer.CreateMessageBody.make20(s"Ratelimit message ${i + 1}")
                    )
                  )
                  .traverse_(req.runRequest)
                  .void
              })
            ),
            log
          )
        )
      }
      .flatMap(_.startResource)
  }
}
