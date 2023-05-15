package ackcord.gateway

import java.util.UUID

import scala.concurrent.duration.Duration

import ackcord.data.ChannelId
import ackcord.gateway.data.{GatewayDispatchType, GatewayEvent, GatewayEventBase}
import ackcord.gateway.impl.CatsGatewayHandlerFactory
import ackcord.requests._
import ackcord.requests.base.ratelimiter.Ratelimiter
import ackcord.requests.base.{FailedRequest, RequestAnswer}
import cats.effect.kernel.Resource
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import sttp.client3._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.client3.impl.cats.implicits._
import sttp.model.Method

object Testing extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val inflate: Inflate[IO]          = Inflate.newInflate[IO]
    implicit val logFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

    val log = logFactory.getLoggerFromClass(this.getClass)

    val requests = for {
      backend         <- HttpClientCatsBackend.resource[IO]()
      requestSettings <- Resource.eval(RequestSettings.simpleF[IO](args(0)))
    } yield {
      Requests.ofNoProcessinng(
        backend,
        requestSettings.copy(
          ratelimiter = new Ratelimiter[IO] {
            override def ratelimitRequest[Req](
                route: RequestRoute,
                request: Req,
                id: UUID
            ): IO[Either[FailedRequest.RequestDropped, Req]] = IO.pure(Right(request))

            override def queryRemainingRequests(route: RequestRoute): IO[Either[Duration, Int]] = IO.pure(Right(999))

            override def reportRatelimits[A](answer: RequestAnswer[A]): IO[Unit] = IO.unit
          }
        )
      )
    }

    val handlerFactory = new CatsGatewayHandlerFactory[IO]

    val console = cats.effect.std.Console[IO]

    requests

    requests
      .map(req =>
        req -> new GatewayConnector.NormalGatewayConnector[IO, CatsGatewayHandlerFactory.CatsGatewayHandler[IO]](
          req,
          handlerFactory
        )
      )
      .use { case (req, connector) =>
        connector.start(
          IdentifyData
            .default(args(0), GatewayIntents.Guilds ++ GatewayIntents.GuildMessages ++ GatewayIntents.MessageContent)
        )(new GatewayCallbacks[IO, CatsGatewayHandlerFactory.CatsGatewayHandler[IO]] {
          override def onCreateHandler(handler: CatsGatewayHandlerFactory.CatsGatewayHandler[IO]): IO[Unit] =
            console.println(handler)

          override def onEvent(handler: CatsGatewayHandlerFactory.CatsGatewayHandler[IO], event: GatewayEventBase[_])
              : IO[Unit] =
            console.println(event) *> (event match {
              case GatewayEvent.Dispatch(ev) if ev.t == GatewayDispatchType.MessageCreate =>
                val j = ev.d.hcursor

                val res = for {
                  channelId <- j.get[ChannelId]("channel_id")
                  content   <- j.get[String]("content")
                } yield {
                  if (content == "!info")
                    req
                      .runRequest(
                        CreateMessageContainer.createMessage(
                          channelId,
                          CreateMessageContainer.CreateMessageBody.make20("Hello from AckCord 2.0")
                        )
                      )
                      .flatMap(r => IO.fromEither(r.eitherData))
                      .void
                  else IO.unit
                }

                val ret = res.sequence.flatMap(r => IO.fromEither(r))

                ret.onError(e => log.error(e)("Failed to send request"))

              case _ => IO.unit
            })

          override def onDisconnected(behavior: DisconnectBehavior): IO[DisconnectBehavior] =
            console.println(behavior).as(behavior)
        })
      }
      .attempt
      .flatMap {
        case Right(_) => IO.pure(ExitCode.Success)
        case Left(e)  => logFactory.getLoggerFromClass(this.getClass).error(e)("Failed").as((ExitCode.Error))
      }
  }

}
