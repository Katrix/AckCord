package ackcord.gateway

import ackcord.gateway.GatewayHandler.{NormalGatewayHandler, StreamGatewayHandler}
import ackcord.gateway.GatewayProcess.ContextKey
import sttp.ws.WebSocket

sealed trait GatewayHandlerFactory[F[_], Handler <: GatewayHandler[F]]
object GatewayHandlerFactory {

  trait GatewayHandlerNormalFactory[F[_], Handler <: NormalGatewayHandler[F]] extends GatewayHandlerFactory[F, Handler] {

    val handlerContextKey: ContextKey[Handler]

    def create(ws: WebSocket[F], identifyData: IdentifyData, resumeData: Option[ResumeData], handle: GatewayProcess[F]): F[Handler]
  }

  trait GatewayHandlerStreamsFactory[F[_], S, Handler <: StreamGatewayHandler[F, S]] extends GatewayHandlerFactory[F, Handler]  {
    def create(identifyData: IdentifyData, resumeData: Option[ResumeData]): F[Handler]
  }
}
