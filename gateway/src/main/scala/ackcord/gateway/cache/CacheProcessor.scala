package ackcord.gateway.cache

import ackcord.data.CacheSnapshot
import ackcord.gateway.GatewayProcess.ContextKey
import ackcord.gateway.data.{GatewayEventBase, GatewayEventOp}
import ackcord.gateway.{DisconnectBehavior, GatewayProcess}
import cats.syntax.all._
import cats.{Applicative, Id, ~>}
import org.typelevel.log4cats.Logger

class CacheProcessor[F[_]: Applicative, Handler](log: Logger[F]) extends GatewayProcess[F, Handler] {
  override def name: String = "CacheProcessor"

  override def onCreateHandler(handler: Handler): F[Unit] = ().pure

  val getCache: ContextKey[CacheSnapshot] = new ContextKey[CacheSnapshot] {}

  override def onEvent(handler: Handler, event: GatewayEventBase[_], context: ContextKey ~> Id): F[ContextKey ~> Id] =
    if (event.op == GatewayEventOp.Dispatch) {

      /* TODO
      val dispatchEvent = ???
      specialHandlers.get(dispatchEvent.name) match {
        case None =>
          if (dispatchEvent.name.endsWith("Create")) onCreate(dispatchEvent)
          else if (dispatchEvent.name.endsWith("Update")) onUpdate(dispatchEvent)
          else if (dispatchEvent.name.endsWith("Delete")) onDelete(dispatchEvent)
          else if (ignoredEvents.contains(dispatchEvent.name)) ().pure
          else {
            log.warn(s"Unknown event ${dispatchEvent.name}")
          }

        case Some(handler) => handler(dispatchEvent)
      }
       */

      context.pure

    } else context.pure

  override def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior] =
    behavior.pure
}
