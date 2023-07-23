package ackcord.gateway

import ackcord.gateway
import ackcord.gateway.data.{GatewayDispatchEvent, GatewayEvent, GatewayEventBase}
import cats.Applicative
import cats.syntax.all._

abstract class DispatchEventProcess[F[_]: Applicative] extends GatewayProcess[F] {

  override def onEvent(event: GatewayEventBase[_], context: gateway.Context): F[gateway.Context] =
    event match {
      case dispatch: GatewayEvent.Dispatch => onDispatchEvent(dispatch.event, context)
      case _                               => context.pure
    }

  def onDispatchEvent(event: GatewayDispatchEvent, context: gateway.Context): F[gateway.Context]
}
