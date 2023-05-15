package ackcord.gateway

import ackcord.gateway.data.GatewayEventBase

trait GatewayCallbacks[F[_], Handler] {

  def onCreateHandler(handler: Handler): F[Unit]

  def onEvent(handler: Handler, event: GatewayEventBase[_]): F[Unit]

  def onDisconnected(behavior: DisconnectBehavior): F[DisconnectBehavior]

}
