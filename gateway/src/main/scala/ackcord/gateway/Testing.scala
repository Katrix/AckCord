package ackcord.gateway

import ackcord.data.ChannelId
import ackcord.gateway.GatewayProcess.ContextKey
import ackcord.gateway.data.{GatewayDispatchType, GatewayEvent, GatewayEventBase}
import ackcord.gateway.impl.CatsGatewayHandlerFactory
import ackcord.requests._
import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import cats.{Id, ~>}
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.{Logger, LoggerFactory}
import sttp.client3._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.client3.impl.cats.implicits._

object Testing extends IOApp {

  class StringCommandProcessor[Handler](commands: Map[String, (String, ChannelId) => IO[Unit]], log: Logger[IO])
      extends GatewayProcess.Base[IO, Handler] {
    override def name: String = "StringCommandProcessor"

    override def onEvent(
        handler: Handler,
        event: GatewayEventBase[_],
        context: ContextKey ~> Id
    ): IO[ContextKey ~> Id] = event match {
      case GatewayEvent.Dispatch(ev) if ev.t == GatewayDispatchType.MessageCreate =>
        val j = ev.d.hcursor

        val res = for {
          channelId <- j.get[ChannelId]("channel_id")
          content   <- j.get[String]("content")
        } yield commands.find(t => content.startsWith(t._1)).map(t => t._2(content, channelId)).getOrElse(().pure)

        val ret = res.sequence.flatMap(r => IO.fromEither(r))
        ret.onError(e => log.error(e)("Failed to handle command")).as(context)

      case _ => context.pure
    }
  }

  class PrintlnProcessor[Handler](console: Console[IO]) extends GatewayProcess[IO, Handler] {
    override def name: String = "PrintlnProcessor"

    override def onCreateHandler(handler: Handler): IO[Unit] =
      console.println(handler)

    override def onEvent(
        handler: Handler,
        event: GatewayEventBase[_],
        context: ContextKey ~> Id
    ): IO[ContextKey ~> Id] =
      console.println(event).as(context)

    override def onDisconnected(behavior: DisconnectBehavior): IO[DisconnectBehavior] =
      console.println(behavior).as(behavior)
  }

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val inflate: Inflate[IO]          = Inflate.newInflate[IO]
    implicit val logFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

    val log = logFactory.getLoggerFromClass(this.getClass)

    val token = args(0)

    val requests = for {
      backend         <- HttpClientCatsBackend.resource[IO]()
      requestSettings <- RequestSettings.simpleF[IO](token)
    } yield Requests.ofNoProcessinng(backend, requestSettings)

    val handlerFactory = new CatsGatewayHandlerFactory[IO]

    val console = cats.effect.std.Console[IO]

    requests
      .use { req =>
        val connector = new GatewayConnector.NormalGatewayConnector(req, handlerFactory)
        connector.start(
          IdentifyData
            .default(token, GatewayIntents.Guilds ++ GatewayIntents.GuildMessages ++ GatewayIntents.MessageContent)
        )(GatewayProcess.sequenced[IO, CatsGatewayHandlerFactory.CatsGatewayHandler[IO]](
          new PrintlnProcessor(console),
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
                  .onError(e => log.error(e)("Encountered error while running requests"))
                  .start
                  .void
              })
            ),
            log
          )
        ))
      }
      .attempt
      .flatMap {
        case Right(_) => IO.pure(ExitCode.Success)
        case Left(e)  => logFactory.getLoggerFromClass(this.getClass).error(e)("Failed").as(ExitCode.Error)
      }
  }

}
