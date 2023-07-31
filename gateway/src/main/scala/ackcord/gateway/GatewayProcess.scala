package ackcord.gateway

import ackcord.gateway.GatewayProcess.Context
import ackcord.gateway.data.GatewayEventBase
import cats.effect.std.Supervisor
import cats.syntax.all._
import cats.{Applicative, ApplicativeError, Monad}
import org.typelevel.log4cats.Logger

trait GatewayProcess[F[_]] {

  def F: Applicative[F]

  def name: String

  def onCreateHandler(context: Context): F[Context] = F.pure(context)

  def onEvent(event: GatewayEventBase[_], context: Context): F[Context] = F.pure(context)

  def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] = F.pure(behavior)

  override def toString: String = name
}
object GatewayProcess {

  abstract class Base[F[_]](implicit val F: Applicative[F]) extends GatewayProcess[F]

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

  def sequenced[F[_]](callbacks: GatewayProcess[F]*)(implicit FM: Monad[F]): GatewayProcess[F] = new GatewayProcess[F] {
    override def name: String = s"SequencedProcessor(${callbacks.map(_.name).mkString(", ")})"

    override def F: Applicative[F] = FM

    override def onCreateHandler(context: Context): F[Context] =
      callbacks.foldLeftM(context)((c, cb) => cb.onCreateHandler(c))

    override def onEvent(event: GatewayEventBase[_], context: Context): F[Context] =
      callbacks.foldLeftM(context)((c, cb) => cb.onEvent(event, c))

    override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] =
      callbacks.foldLeftM(behavior)((b, cb) => cb.onDisconnected(b))
  }

  def superviseIgnoreContext[F[_]](
      supervisor: Supervisor[F],
      process: GatewayProcess[F]
  )(implicit FApp: Applicative[F]): GatewayProcess[F] = new GatewayProcess[F] {
    override def name: String = s"Supervisor(${process.name})"

    override def F: Applicative[F] = FApp

    override def onCreateHandler(context: Context): F[Context] =
      supervisor.supervise(process.onCreateHandler(context)).as(context)

    override def onEvent(event: GatewayEventBase[_], context: Context): F[Context] =
      supervisor.supervise(process.onEvent(event, context)).as(context)

    override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] =
      process.onDisconnected(behavior)
  }

  def logErrors[F[_]](log: Logger[F], process: GatewayProcess[F])(
      implicit FApp: ApplicativeError[F, Throwable]
  ): GatewayProcess[F] = new GatewayProcess[F] {
    override def name: String = s"LoggErrors(${process.name})"

    override def F: Applicative[F] = FApp

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
