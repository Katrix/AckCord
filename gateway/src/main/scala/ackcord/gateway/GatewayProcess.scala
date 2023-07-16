package ackcord.gateway

import ackcord.gateway.GatewayProcess.Context
import ackcord.gateway.data.GatewayEventBase
import cats.effect.std.Supervisor
import cats.syntax.all._
import cats.{Applicative, ApplicativeError, Monad}
import org.typelevel.log4cats.Logger

trait GatewayProcess[F[_]] {

  def name: String

  def onCreateHandler(context: Context): F[Context]

  def onEvent(event: GatewayEventBase[_], context: Context): F[Context]

  def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior]

  override def toString: String = name
}
object GatewayProcess {

  class Context(map: Map[ContextKey[_], Any]) {
    def access[A](key: ContextKey[A]): A = map(key).asInstanceOf[A]

    def add[A](key: ContextKey[A], data: A): Context = new Context(map.updated(key, data))
  }
  object Context {
    def empty: Context = new Context(Map.empty)
  }

  trait ContextKey[A]
  object ContextKey {
    def make[A]: ContextKey[A] = new ContextKey[A] {}
  }

  abstract class Base[F[_]: Applicative] extends GatewayProcess[F] {
    override def onCreateHandler(context: Context): F[Context] = context.pure

    override def onEvent(event: GatewayEventBase[_], context: Context): F[Context] =
      context.pure

    override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] = behavior.pure
  }

  def sequenced[F[_]: Monad](callbacks: GatewayProcess[F]*): GatewayProcess[F] = new GatewayProcess[F] {
    override def name: String = s"SequencedProcessor(${callbacks.map(_.name).mkString(", ")})"

    override def onCreateHandler(context: Context): F[Context] =
      callbacks.foldLeftM(context)((c, cb) => cb.onCreateHandler(c))

    override def onEvent(event: GatewayEventBase[_], context: Context): F[Context] =
      callbacks.foldLeftM(context)((c, cb) => cb.onEvent(event, c))

    override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] =
      callbacks.foldLeftM(behavior)((b, cb) => cb.onDisconnected(b))
  }

  def superviseIgnoreContext[F[_]: Applicative](
      supervisor: Supervisor[F],
      process: GatewayProcess[F]
  ): GatewayProcess[F] = new GatewayProcess[F] {
    override def name: String = s"Supervisor(${process.name})"

    override def onCreateHandler(context: Context): F[Context] =
      supervisor.supervise(process.onCreateHandler(context)).as(context)

    override def onEvent(event: GatewayEventBase[_], context: Context): F[Context] =
      supervisor.supervise(process.onEvent(event, context)).as(context)

    override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] =
      process.onDisconnected(behavior)
  }

  def logErrors[F[_]](log: Logger[F], process: GatewayProcess[F])(
      implicit F: ApplicativeError[F, Throwable]
  ): GatewayProcess[F] = new GatewayProcess[F] {
    override def name: String = s"LoggErrors(${process.name})"

    override def onCreateHandler(context: Context): F[Context] =
      process
        .onCreateHandler(context)
        .handleErrorWith(e => log.error(e)("Encountered error while processing handler creation").as(context))

    override def onEvent(event: GatewayEventBase[_], context: Context): F[Context] =
      process
        .onEvent(event, context)
        .handleErrorWith(e => log.error(e)("Encountered error while processing event").as(context))

    override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] =
      process
        .onDisconnected(behavior)
        .handleErrorWith(e => log.error(e)("Encountered error while processing disconnect").as(behavior))
  }
}
