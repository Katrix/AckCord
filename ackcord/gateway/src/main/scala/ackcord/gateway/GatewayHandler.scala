package ackcord.gateway

import sttp.capabilities.Streams
import sttp.ws.WebSocketFrame

sealed trait GatewayHandler[F[_]] {

  def lastSeq: F[Option[Int]]

  def resumeData: F[Option[ResumeData]]
}
object GatewayHandler {

  trait NormalGatewayHandler[F[_]] extends GatewayHandler[F] {

    def run: F[DisconnectBehavior]
  }

  trait StreamGatewayHandler[F[_], S] extends GatewayHandler[F] {

    def stream(s: Streams[S]): s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  }
}
