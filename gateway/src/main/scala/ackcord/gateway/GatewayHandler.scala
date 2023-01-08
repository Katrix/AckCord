package ackcord.gateway

import sttp.capabilities.Streams
import sttp.ws.WebSocketFrame

trait GatewayHandler[F[_]] {

  def resumeGatewayUrl: F[Option[String]]

  def lastSeq: F[Option[Int]]
}
object GatewayHandler {

  trait NormalGatewayHandler[F[_]] extends GatewayHandler[F] {

    def grabNextEvent: F[Option[RawGatewayEvent]]

    def start: F[Unit]
  }

  trait StreamGatewayHandler[F[_], S] extends GatewayHandler[F] {

    def stream(s: Streams[S]): s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  }
}
