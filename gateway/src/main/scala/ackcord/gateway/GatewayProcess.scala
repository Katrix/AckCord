package ackcord.gateway

import ackcord.gateway.GatewayProcess.ContextKey
import ackcord.gateway.data.GatewayEventBase
import cats.syntax.all._
import cats.{Applicative, Id, Monad, Traverse, ~>}

trait GatewayProcess[F[_], Handler] {

  def name: String

  def onCreateHandler(handler: Handler): F[Unit]

  def onEvent(handler: Handler, event: GatewayEventBase[_], context: ContextKey ~> Id): F[ContextKey ~> Id]

  def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior]

}
object GatewayProcess {

  trait ContextKey[Res]

  abstract class Base[F[_]: Applicative, Handler] extends GatewayProcess[F, Handler] {
    override def onCreateHandler(handler: Handler): F[Unit] = ().pure

    override def onEvent(handler: Handler, event: GatewayEventBase[_], context: ContextKey ~> Id): F[ContextKey ~> Id] =
      context.pure

    override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] = behavior.pure
  }

  def sequenced[F[_]: Monad, Handler](
      callbacks: GatewayProcess[F, Handler]*
  ): GatewayProcess[F, Handler] = new GatewayProcess[F, Handler] {
    override def name: String = s"SequencedProcessor(${callbacks.map(_.name).mkString(", ")})"

    override def onCreateHandler(handler: Handler): F[Unit] =
      callbacks.traverse_(_.onCreateHandler(handler))

    override def onEvent(handler: Handler, event: GatewayEventBase[_], context: ContextKey ~> Id): F[ContextKey ~> Id] =
      callbacks.foldLeftM(context)((c, cb) => cb.onEvent(handler, event, c))

    override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] =
      callbacks.foldLeftM(behavior)((b, cb) => cb.onDisconnected(b))
  }
}
