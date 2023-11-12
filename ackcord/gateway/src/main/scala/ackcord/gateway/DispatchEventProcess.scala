package ackcord.gateway

import ackcord.gateway
import ackcord.gateway.data.{GatewayDispatchEvent, GatewayEvent, GatewayEventBase}

trait DispatchEventProcess[F[_]] extends GatewayProcessComponent[F] {

  override def onEvent(event: GatewayEventBase[_], context: gateway.Context): F[Return[gateway.Context]] =
    event match {
      case dispatch: GatewayEvent.Dispatch => onDispatchEvent(dispatch.event, context)
      case _                               => makeReturnF(context)
    }

  def onDispatchEvent(event: GatewayDispatchEvent, context: gateway.Context): F[Return[gateway.Context]] =
    makeReturnF(context)
}
