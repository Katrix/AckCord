package ackcord.gateway

import ackcord.gateway.GatewayProcess.Context

import scala.annotation.tailrec
import ackcord.requests._
import ackcord.requests.base.{EncodeBody, Requests}
import io.circe.{Decoder, HCursor}
import org.typelevel.log4cats.{Logger, LoggerFactory}
import sttp.capabilities.{Effect, Streams, WebSockets}
import sttp.client3.{Request => _, _}
import sttp.model.{Method, Uri}
import sttp.monad.syntax._
import sttp.ws.WebSocket

trait GatewayConnector[F[_], Handler <: GatewayHandler[F]] {

  def handlerFactory: GatewayHandlerFactory[F, Handler]
}
//noinspection ScalaUnusedSymbol
object GatewayConnector {

  private case class GatewayResult(url: String)
  private object GatewayResult {
    implicit lazy val decoder: Decoder[GatewayResult] = (c: HCursor) => c.get[String]("url").map(GatewayResult(_))
  }

  private case class GatewayBotResult(url: String, shards: Int, sessionStartLimit: GatewayBotResult.SessionStartLimit)
  private object GatewayBotResult {
    case class SessionStartLimit(total: Int, remaining: Int, resetAfter: Int, maxConcurrency: Int)
    object SessionStartLimit {
      implicit lazy val decoder: Decoder[SessionStartLimit] = (c: HCursor) =>
        for {
          total          <- c.get[Int]("total")
          remaining      <- c.get[Int]("remaining")
          resetAfter     <- c.get[Int]("reset_after")
          maxConcurrency <- c.get[Int]("max_concurrency")
        } yield SessionStartLimit(total, remaining, resetAfter, maxConcurrency)
    }

    implicit lazy val decoder: Decoder[GatewayBotResult] = (c: HCursor) =>
      for {
        url               <- c.get[String]("url")
        shards            <- c.get[Int]("shards")
        sessionStartLimit <- c.get[SessionStartLimit]("session_start_limit")
      } yield GatewayBotResult(url, shards, sessionStartLimit)
  }

  private val getGateway: Request[Unit, GatewayResult] = Request.restRequest[Unit, GatewayResult](
    route = (Route.Empty / "gateway").toRequest(Method.GET)
  )

  private val getGatewayBot = Request.restRequest[Unit, GatewayBotResult](
    route = (Route.Empty / "gateway" / "bot").toRequest(Method.GET)
  )

  private class ParseResponseAsWebsocket[F[_], A](handle: WebSocket[F] => F[A])
      extends ParseResponse[A, Effect[F] with WebSockets] {
    override def setSttpResponse[T, R1](
        request: RequestT[Identity, T, R1]
    ): RequestT[Identity, Either[Throwable, Either[String, A]], R1 with Effect[F] with WebSockets] =
      request.response(asWebSocket(handle)).mapResponse(e => Right(e))
  }

  trait HandleReconnect[F[_]] {
    def handleReconnect(
        wsUrl: String,
        connect: (Option[ResumeData], String) => F[(DisconnectBehavior, Option[ResumeData])]
    ): F[Unit]
  }
  object HandleReconnect {

    implicit val handleReconnectId: HandleReconnect[cats.Id] = (
        wsUrl: String,
        connect: (Option[ResumeData], String) => (DisconnectBehavior, Option[ResumeData])
    ) => {

      @tailrec
      def inner(result: (DisconnectBehavior, Option[ResumeData])): Unit = result match {
        case (DisconnectBehavior.FatalError(error), _) => throw new Exception(error)
        case (DisconnectBehavior.Reconnect(true), Some(resumeData)) =>
          inner(connect(Some(resumeData), resumeData.resumeGatewayUrl))

        case (DisconnectBehavior.Reconnect(_), _) =>
          inner(connect(None, wsUrl))

        case (DisconnectBehavior.Done, _) =>
      }

      inner(connect(None, wsUrl))
    }

    implicit def handleReconnectF[F[_]](implicit F: cats.MonadError[F, Throwable]): HandleReconnect[F] =
      (
          wsUrl: String,
          connect: (Option[ResumeData], String) => F[(DisconnectBehavior, Option[ResumeData])]
      ) => {
        type Tpe = Either[(DisconnectBehavior, Option[ResumeData]), Unit]
        F.flatMap(connect(None, wsUrl)) { r =>
          F.tailRecM(r) {
            case (DisconnectBehavior.FatalError(error), _) => F.raiseError[Tpe](new Exception(error))
            case (DisconnectBehavior.Reconnect(true), Some(resumeData)) =>
              F.map(connect(Some(resumeData), resumeData.resumeGatewayUrl))(Left(_): Tpe)
            case (DisconnectBehavior.Reconnect(_), _) =>
              F.map(connect(None, wsUrl))(Left(_): Tpe)

            case (DisconnectBehavior.Done, _) => F.pure(Right(()): Tpe)
          }
        }
      }
  }

  class NormalGatewayConnector[F[_], Handler <: GatewayHandler.NormalGatewayHandler[F]](
      requests: Requests[F, WebSockets],
      val handlerFactory: GatewayHandlerFactory.GatewayHandlerNormalFactory[F, Handler]
  )(implicit handleReconnect: HandleReconnect[F], logFactory: LoggerFactory[F])
      extends GatewayConnector[F, Handler] {
    import requests.F

    private def connect(
        identifyData: IdentifyData,
        handle: GatewayProcess[F],
        log: Logger[F],
        resumeData: Option[ResumeData],
        wsUrl: String
    ): F[(DisconnectBehavior, Option[ResumeData])] = {
      val request = ComplexRequest(
        route = RequestRoute(wsUrl, wsUrl, Uri.unsafeParse(wsUrl), Method.GET),
        requestBody = EncodeBody.NoBody,
        parseResponse = new ParseResponseAsWebsocket((ws: WebSocket[F]) => {
          for {
            _           <- log.info("Connected with Websockets. Making handler")
            handler     <- handlerFactory.create(ws, identifyData, resumeData, handle)
            _           <- log.info("Handler callback")
            _           <- handle.onCreateHandler(Context.empty.add(handlerFactory.handlerContextKey, handler))
            _           <- log.info("Running connection")
            behavior    <- handler.run
            _           <- log.info("Disconnected")
            newBehavior <- handle.onDisconnected(behavior)
            resume      <- handler.resumeData
          } yield (newBehavior, resume)
        })
      )

      requests.runRequest(request)
    }

    def start(identifyData: IdentifyData)(handle: GatewayProcess[F]): F[Unit] =
      for {
        log   <- logFactory.fromClass(this.getClass)
        _     <- log.info("Finding WS Url")
        wsUrl <- requests.runRequest(getGateway).map(_.url)
        _     <- log.info(s"Found WS Url at $wsUrl. Connecting to Discord")
        _     <- handleReconnect.handleReconnect(wsUrl, connect(identifyData, handle, log, _, _))
      } yield ()
  }

  class StreamGatewayConnector[F[_], S, Handler <: GatewayHandler.StreamGatewayHandler[F, S]](
      requests: Requests[F, Streams[S]],
      val handlerFactory: GatewayHandlerFactory.GatewayHandlerStreamsFactory[F, S, Handler]
  )
}
