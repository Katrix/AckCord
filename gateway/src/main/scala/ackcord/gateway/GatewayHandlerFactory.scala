package ackcord.gateway

import ackcord.gateway.GatewayHandler.{NormalGatewayHandler, StreamGatewayHandler}
import sttp.ws.WebSocket

sealed trait GatewayHandlerFactory[F[_]]
object GatewayHandlerFactory {

  trait GatewayHandlerNormalFactory[F[_]] extends GatewayHandlerFactory[F] {
    def create(ws: WebSocket[F], resumeData: Option[ResumeData], wantedEvents: Set[Int]): F[NormalGatewayHandler[F]]
  }

  trait GatewayHandlerStreamsFactory[F[_], S] extends GatewayHandlerFactory[F]  {
    def create(resumeData: Option[ResumeData], wantedEvents: Set[Int]): F[StreamGatewayHandler[F, S]]
  }
}
