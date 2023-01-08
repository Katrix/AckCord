package ackcord.gateway

import ackcord.gateway.GatewayHandler.NormalGatewayHandler
import ackcord.gateway.GatewayHandlerFactory.GatewayHandlerNormalFactory
import cats.effect.kernel.syntax.all._
import cats.effect.kernel.{Concurrent, Fiber, Ref, Spawn}
import cats.effect.std.Queue
import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import sttp.ws.WebSocket

class CatsGatewayHandlerFactory[F[_]: Concurrent: Spawn] extends GatewayHandlerNormalFactory[F] {
  override def create(
      ws: WebSocket[F],
      resumeData: Option[ResumeData],
      wantedEvents: Set[Int]
  ): F[NormalGatewayHandler[F]] = {
    for {
      heartbeatIntervalRef    <- Ref[F].of(0)
      receivedHeartbeatAckRef <- Ref[F].of(true)
      resumeGatewayUrlRef     <- Ref[F].of[Option[String]](None)
      sessionIdRef            <- Ref[F].of[Option[String]](None)
      lastReceivedSeqRef      <- Ref[F].of[Option[Int]](None)
      //We do unbounded for now, and give it a limit if it becomes a problem
      eventQueue <- Queue.unbounded[F, RawGatewayEvent]
    } yield new NormalGatewayHandler[F] {

      override def grabNextEvent: F[Option[RawGatewayEvent]] = eventQueue.tryTake

      private def receiveRawGatewayEvent: F[RawGatewayEvent] =
        ws.receiveText().map(parser.decode[RawGatewayEvent](_).toTry.get)

      private def initiateConnection: F[Unit] = for {
        helloEvent  <- receiveRawGatewayEvent
        _           <- enqueueEventIfWanted(helloEvent)
        _           <- heartbeatIntervalRef.set(???)
        _           <- sendHeartbeat
        _           <- sendIdentify
        readyEventy <- receiveRawGatewayEvent
        _           <- enqueueEventIfWanted(readyEventy)
        _           <- resumeGatewayUrlRef.set(???)
        _           <- sessionIdRef.set(???)

      } yield ()

      private def resumeConnection(resumeData: ResumeData): F[Unit] = for {
        _ <- sendResume

      } yield ()

      private def sendHeartbeat: F[Unit] = ???

      private def sendIdentify: F[Unit] = ???

      private def sendResume: F[Unit] = ???

      private def enqueueEventIfWanted(event: RawGatewayEvent): F[Unit] =
        if (wantedEvents(event.op)) eventQueue.offer(event) else ().pure

      override def resumeGatewayUrl: F[Option[String]] = resumeGatewayUrlRef.get

      override def lastSeq: F[Option[Int]] = lastReceivedSeqRef.get

      private def makeFiberHandler: F[Fiber[F, Throwable, Unit]] = {
        ws.either(resumeData.fold(initiateConnection)(resumeConnection))
          .flatMap {
            case Right(r) => r.pure
            case Left(Some(closeFrame)) =>
              closeFrame.statusCode match {
                case 40014 => ??? //TODO: Fill in more here. Send error and such
              }
            case Left(None) => ??? //TODO: Send error and such
          }
          .start
      }

      override def start: F[Unit] = makeFiberHandler.void //TODO: Do not throw away the fiber here
    }
  }
}
