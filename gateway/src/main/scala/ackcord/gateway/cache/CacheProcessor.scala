package ackcord.gateway.cache

import ackcord.data.CacheSnapshot
import ackcord.gateway.data.{GatewayEventBase, GatewayEventOp}
import ackcord.gateway.{Context, ContextKey, DisconnectBehavior, GatewayProcess}
import cats.Applicative
import cats.syntax.all._
import org.typelevel.log4cats.Logger

class CacheProcessor[F[_]: Applicative](log: Logger[F]) extends GatewayProcess.Base[F] {
  override def name: String = "CacheProcessor"

  val getCache: ContextKey[CacheSnapshot] = ContextKey.make

  override def onEvent(event: GatewayEventBase[_], context: Context): F[Context] =
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
}
