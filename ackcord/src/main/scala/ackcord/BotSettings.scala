package ackcord

import ackcord.gateway.GatewayConnector.HandleReconnect
import ackcord.gateway.GatewayHandlerFactory.GatewayHandlerNormalFactory
import ackcord.gateway.data.GatewayIntents
import ackcord.gateway.impl.CatsGatewayHandlerFactory
import ackcord.gateway.{GatewayConnector, GatewayHandler, GatewayProcess, IdentifyData, Inflate}
import ackcord.requests.{RequestSettings, Requests}
import cats.Monad
import cats.effect.ExitCode
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.Supervisor
import cats.syntax.all._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import sttp.capabilities.WebSockets
import sttp.client3.SttpBackend
import sttp.monad.MonadError

sealed trait BotSettings[F[_], P, Handler <: GatewayHandler[F]] {

  def token: String
  def intents: GatewayIntents
  def editIdentifyData: IdentifyData => IdentifyData
  def backend: SttpBackend[F, P]
  def requestSettings: RequestSettings[F]
  def loggerFactory: LoggerFactory[F]

  lazy val requests: Requests[F, P] = Requests.ofNoProcessinng(backend, requestSettings)
}
object BotSettings {

  case class NormalBotSettings[F[_]: Monad, P <: WebSockets, Handler <: GatewayHandler.NormalGatewayHandler[F]](
      token: String,
      intents: GatewayIntents,
      editIdentifyData: IdentifyData => IdentifyData,
      backend: SttpBackend[F, P],
      requestSettings: RequestSettings[F],
      loggerFactory: LoggerFactory[F],
      logGatewayMessages: Boolean,
      handlerFactory: GatewayHandlerNormalFactory[F, Handler],
      handleReconnect: HandleReconnect[F],
      supervisor: Supervisor[F],
      contextProcessors: Seq[GatewayProcess[F]],
      processors: Seq[GatewayProcess[F]]
  )(implicit F: _root_.cats.MonadError[F, Throwable])
      extends BotSettings[F, P, Handler] {

    def useContext(newProcessors: GatewayProcess[F]*): NormalBotSettings[F, P, Handler] =
      copy(contextProcessors = contextProcessors ++ newProcessors)

    def useContextResource(
        newProcessors: Resource[F, GatewayProcess[F]]*
    ): Resource[F, NormalBotSettings[F, P, Handler]] =
      newProcessors.sequence.map(ps => useContext(ps: _*))

    def use(newProcessors: GatewayProcess[F]*): NormalBotSettings[F, P, Handler] =
      copy(processors = processors ++ newProcessors)

    def useResource(newProcessors: Resource[F, GatewayProcess[F]]*): Resource[F, NormalBotSettings[F, P, Handler]] =
      newProcessors.sequence.map(ps => use(ps: _*))

    def assembledProcessor: GatewayProcess[F] = {
      val log = loggerFactory.getLoggerFromClass(classOf[GatewayProcess[F]])
      GatewayProcess.superviseIgnoreContext(
        supervisor,
        GatewayProcess.logErrors(
          log,
          GatewayProcess.sequenced(
            contextProcessors.map(GatewayProcess.logErrors(log, _)) ++
              processors
                .map(GatewayProcess.logErrors(log, _))
                .map(GatewayProcess.superviseIgnoreContext(supervisor, _)): _*
          )
        )
      )
    }

    def start: F[ExitCode] = {
      implicit val logFac: LoggerFactory[F]      = loggerFactory
      implicit val reconnect: HandleReconnect[F] = handleReconnect
      val req                                    = requests

      val connector = new GatewayConnector.NormalGatewayConnector[F, Handler](req, handlerFactory, logGatewayMessages)
      connector
        .start(editIdentifyData(IdentifyData.default(token, intents)))(assembledProcessor)
        .attempt
        .flatMap {
          case Right(_) => ExitCode.Success.pure
          case Left(e)  => loggerFactory.getLoggerFromClass(this.getClass).error(e)("Failed").as(ExitCode.Error)
        }
    }

    def startResource: Resource[F, ExitCode] = Resource.eval(start)
  }

  def cats[F[_]: Async, P <: WebSockets](
      token: String,
      intents: GatewayIntents,
      sttpBackend: Resource[F, SttpBackend[F, P]]
  )(
      implicit handleReconnect: HandleReconnect[F]
  ): Resource[F, NormalBotSettings[F, P, CatsGatewayHandlerFactory.CatsGatewayHandler[F]]] = {
    implicit val loggerFactory: Slf4jFactory[F] = Slf4jFactory.create[F]

    for {
      backend         <- sttpBackend
      requestSettings <- RequestSettings.simpleF[F](token)
      supervisor      <- Supervisor.apply(await = false)
    } yield {
      implicit val monadError: MonadError[F] = backend.responseMonad
      implicit val inflate: Inflate[F]       = Inflate.newInflate

      val handlerFactory = new CatsGatewayHandlerFactory[F]

      NormalBotSettings(
        token,
        intents,
        identity,
        backend,
        requestSettings,
        loggerFactory,
        logGatewayMessages = false,
        handlerFactory,
        handleReconnect,
        supervisor,
        Seq.empty,
        Seq.empty
      )
    }
  }

  //TODO
  trait StreamBotSettings[F[_], P, S, Handler <: GatewayHandler.StreamGatewayHandler[F, S]]
}
