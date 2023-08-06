package ackcord

import ackcord.gateway.GatewayConnector.HandleReconnect
import ackcord.gateway.GatewayHandlerFactory.GatewayHandlerNormalFactory
import ackcord.gateway.GatewayProcessComponent.FAlter
import ackcord.gateway.data.{GatewayDispatchEvent, GatewayEventBase, GatewayIntents}
import ackcord.gateway.impl.CatsGatewayHandlerFactory
import ackcord.gateway.{
  Context,
  DisconnectBehavior,
  DispatchEventProcess,
  DoAsync,
  GatewayConnector,
  GatewayContextUpdater,
  GatewayHandler,
  GatewayProcess,
  GatewayProcessComponent,
  GatewayProcessHandler,
  IdentifyData,
  Inflate
}
import ackcord.requests.{RequestSettings, Requests}
import cats.effect.ExitCode
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.Supervisor
import cats.syntax.all._
import cats.{Applicative, Monad}
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
      processors: Seq[GatewayProcessComponent[F]]
  )(implicit F: _root_.cats.MonadError[F, Throwable], doAsync: DoAsync[F])
      extends BotSettings[F, P, Handler] {
    type Self = NormalBotSettings[F, P, Handler]

    def installContext(newProcessors: GatewayContextUpdater[F]*): Self =
      copy(processors = processors ++ newProcessors)

    def installContextResource(
        newProcessors: Resource[F, GatewayContextUpdater[F]]*
    ): Resource[F, Self] =
      newProcessors.sequence.map(ps => installContext(ps: _*))

    def installContextEval(
        newProcessors: F[GatewayContextUpdater[F]]*
    ): Resource[F, Self] =
      installContextResource(newProcessors.map(Resource.eval): _*)

    def install(newProcessors: GatewayProcessHandler[F]*): Self =
      copy(processors = processors ++ newProcessors)

    def installResource(newProcessors: Resource[F, GatewayProcessHandler[F]]*): Resource[F, Self] =
      newProcessors.sequence.map(ps => install(ps: _*))

    def installEval(newProcessors: F[GatewayProcessHandler[F]]*): Resource[F, Self] =
      installResource(newProcessors.map(Resource.eval): _*)

    def installContextEventListener(
        f: gateway.Context => PartialFunction[GatewayDispatchEvent, F[gateway.Context]]
    ): Self =
      installContext(new GatewayContextUpdater.Base[F]("AnonymousEventListener") with DispatchEventProcess[F] {

        override def onDispatchEvent(event: GatewayDispatchEvent, context: Context): F[Context] = {
          val f2 = f(context)

          if (f2.isDefinedAt(event)) f2(event)
          else F.pure(context)
        }
      })

    def installEventListener(
        f: PartialFunction[GatewayDispatchEvent, F[Unit]]
    ): Self =
      install(new GatewayProcessHandler.Base[F]("AnonymousEventListener") with DispatchEventProcess[F] {
        override def onDispatchEvent(event: GatewayDispatchEvent, context: Context): F[Unit] =
          if (f.isDefinedAt(event)) f(event)
          else F.unit
      })

    def assembledProcessor: GatewayProcess[F] = {
      val log = loggerFactory.getLoggerFromClass(classOf[GatewayProcess[F]])
      val M   = F

      val allComponent: GatewayProcessComponent[F] { type Return[A] = A } = new GatewayProcessComponent[F] {
        override def F: Monad[F] = M

        override def name: String = "TopProcessComponent"

        override type Return[A] = A
        override def makeReturn[A](value: A): A = value

        override def valueFromReturn[A](ret: A): Option[A] = Some(ret)

        override def children: Seq[GatewayProcessComponent[F]] = processors
      }

      implicit val alter: FAlter[F] = FAlter.log(log).andThen(FAlter.async)

      new GatewayProcess[F] {
        override def F: Applicative[F] = allComponent.F

        override def name: String = allComponent.name

        override def onCreateHandler(context: Context): F[Context] =
          allComponent.onCreateHandlerAll(context)

        override def onEvent(event: GatewayEventBase[_], context: Context): F[Context] =
          allComponent.onEventAll(event, context)

        override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] =
          allComponent.onDisconnectedAll(behavior)
      }
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

      val handlerFactory               = new CatsGatewayHandlerFactory[F]
      implicit val doAsync: DoAsync[F] = DoAsync.doAsyncSupervisor(supervisor)

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
        Seq.empty
      )
    }
  }

  //TODO
  trait StreamBotSettings[F[_], P, S, Handler <: GatewayHandler.StreamGatewayHandler[F, S]]
}
