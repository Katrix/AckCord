package ackcord.gateway

import ackcord.gateway
import ackcord.gateway.data.{GatewayDispatchEvent, GatewayEvent, GatewayEventBase}
import cats.Applicative
import cats.syntax.all._

trait DispatchEventProcess[F[_]] extends GatewayProcess[F] {

  override def onEvent(event: GatewayEventBase[_], context: gateway.Context): F[gateway.Context] =
    event match {
      case dispatch: GatewayEvent.Dispatch => onDispatchEvent(dispatch.event, context)
      case _                               => F.pure(context)
    }

  def onDispatchEvent(event: GatewayDispatchEvent, context: gateway.Context): F[gateway.Context]
}
object DispatchEventProcess {
  abstract class Base[F[_]: Applicative] extends GatewayProcess.Base[F] with DispatchEventProcess[F]
}
