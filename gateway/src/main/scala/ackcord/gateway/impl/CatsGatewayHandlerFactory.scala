package ackcord.gateway.impl

import scala.concurrent.duration._

import ackcord.gateway.GatewayHandlerFactory.GatewayHandlerNormalFactory
import ackcord.gateway.data.{GatewayDispatchType, GatewayEvent}
import ackcord.gateway.{Context, ContextKey, DisconnectBehavior, GatewayProcess, IdentifyData, Inflate, ResumeData}
import cats.Applicative
import cats.data.OptionT
import cats.effect.kernel._
import cats.effect.std.{Queue, Supervisor}
import cats.effect.syntax.all._
import cats.syntax.all._
import org.typelevel.log4cats.{Logger, LoggerFactory}
import sttp.ws.{WebSocket, WebSocketFrame}

class CatsGatewayHandlerFactory[F[_]: Temporal: Inflate: LoggerFactory]
    extends GatewayHandlerNormalFactory[F, CatsGatewayHandlerFactory.CatsGatewayHandler[F]] {

  override val handlerContextKey: ContextKey[CatsGatewayHandlerFactory.CatsGatewayHandler[F]] =
    ContextKey.makeWithoutDefault

  override def create(
      ws: WebSocket[F],
      identifyData: IdentifyData,
      resumeData: Option[ResumeData],
      handle: GatewayProcess[F],
      logMessages: Boolean
  ): F[CatsGatewayHandlerFactory.CatsGatewayHandler[F]] = {
    for {
      log <- LoggerFactory[F].fromClass(classOf[CatsGatewayHandlerFactory.CatsGatewayHandler[F]])

      receivedHeartbeatAckRef <- Ref[F].of(true)
      heartbeatNowQueue       <- Queue.synchronous[F, Unit]

      resumeGatewayUrlRef <- Ref[F].of[Option[String]](None)
      sessionIdRef        <- Ref[F].of[Option[String]](None)
      lastReceivedSeqRef  <- Ref[F].of[Option[Int]](None)
      disconnectBehavior  <- Deferred[F, DisconnectBehavior]

      handler = new CatsGatewayHandlerFactory.CatsGatewayHandler(
        ws,
        identifyData,
        handle,
        log,
        logMessages,
        receivedHeartbeatAckRef,
        heartbeatNowQueue,
        resumeGatewayUrlRef,
        sessionIdRef,
        lastReceivedSeqRef,
        disconnectBehavior,
        handlerContextKey
      )

      _ <- resumeData.traverse(handler.sendResume)
    } yield handler
  }
}
object CatsGatewayHandlerFactory {

  class CatsGatewayHandler[F[_]: Temporal: Inflate](
      ws: WebSocket[F],
      identifyData: IdentifyData,
      handle: GatewayProcess[F],
      log: Logger[F],
      logMessages: Boolean,
      receivedHeartbeatAckRef: Ref[F, Boolean],
      heartbeatNowQueue: Queue[F, Unit],
      resumeGatewayUrlRef: Ref[F, Option[String]],
      sessionIdRef: Ref[F, Option[String]],
      lastReceivedSeqRef: Ref[F, Option[Int]],
      disconnectBehavior: Deferred[F, DisconnectBehavior],
      handlerContextKey: ContextKey[CatsGatewayHandler[F]]
  ) extends NormalGatewayHandlerBase[F](ws, identifyData, log, logMessages) {

    private def reconnect(resumable: Boolean): F[Unit] =
      disconnectBehavior.complete(DisconnectBehavior.Reconnect(resumable)).void

    private def fatalExit(error: String): F[Unit] =
      disconnectBehavior.complete(DisconnectBehavior.FatalError(error)).void

    protected def handleClose(close: Option[WebSocketFrame.Close]): F[Unit] = close match {
      case Some(WebSocketFrame.Close(4000, _)) => reconnect(true)
      case Some(WebSocketFrame.Close(4001, _)) => log.warn("Sent unknown opcode to Discord") *> reconnect(true)
      case Some(WebSocketFrame.Close(4002, _)) => log.warn("Sent invalid payload to Discord") *> reconnect(true)
      case Some(WebSocketFrame.Close(4003, _)) => log.warn("Sent request before identifying") *> reconnect(true)
      case Some(WebSocketFrame.Close(4004, _)) => fatalExit("Authentification failed")
      case Some(WebSocketFrame.Close(4005, _)) => log.warn("Sent identify to Discord more than once") *> reconnect(true)
      case Some(WebSocketFrame.Close(4007, _)) =>
        log.info("Resumed with invalid seq. Events might be lost") *> reconnect(true)
      case Some(WebSocketFrame.Close(4008, _)) => log.warn("Ratelimited. Reconnecting") *> reconnect(true)
      case Some(WebSocketFrame.Close(4009, _)) => reconnect(true)
      case Some(WebSocketFrame.Close(4010, _)) => fatalExit("Invalid shard")
      case Some(WebSocketFrame.Close(4011, _)) => fatalExit("Sharding required")
      case Some(WebSocketFrame.Close(4012, _)) => fatalExit("Invalid API version")
      case Some(WebSocketFrame.Close(4013, _)) => fatalExit("Invalid intents")
      case Some(WebSocketFrame.Close(4014, _)) => fatalExit("Disallowed intents")
      case Some(WebSocketFrame.Close(_, _))    => reconnect(false)
      case None                                => log.warn("Received close without close code") *> reconnect(true)
    }

    private def disconnectAndReconnect(reason: String, code: Int): F[Unit] =
      ws.send(WebSocketFrame.Close(code, reason)) *> reconnect(true)

    private def receiveSingleEvent(supervisor: Supervisor[F], inflater: Inflate.PureInflater[F]): F[Unit] =
      receiveRawGatewayEvent(inflater)
        .flatMap {
          case Some(js) =>
            GatewayEvent.tryDecode(js) match {
              case Right(ev) =>
                val handleExternally = handle
                  .onEvent(ev, Context.empty.add(handlerContextKey, this))
                  .void
                  .handleErrorWith { e =>
                    log.error(e)("Encountered error while handling event")
                  }
                  .race(
                    Temporal[F].sleep(3.seconds) *> log.error(new Exception("Event execution took too long"))(
                      "Handling of event too too long and has timed out. Make sure to not block in event handlers"
                    )
                  )
                  .void

                val actOnEvent = ev match {
                  case GatewayEvent.Dispatch(ev) =>
                    val setSeq = lastReceivedSeqRef.set(Some(ev.s))

                    //We don't access to dispatch type for a tiny bit more safety. Don't want to crash here
                    val setResumeDataEither = if (ev.t == GatewayDispatchType.Ready) {
                      val d = ev.d.json.hcursor
                      for {
                        resumeGatewayUrl <- d.get[String]("resume_gateway_url")
                        sessionId        <- d.get[String]("session_id")
                      } yield for {
                        _ <- resumeGatewayUrlRef.set(Some(resumeGatewayUrl))
                        _ <- sessionIdRef.set(Some(sessionId))
                      } yield ()
                    } else Right(().pure)

                    setResumeDataEither match {
                      case Right(setRefs) => setSeq *> setRefs
                      case Left(_) =>
                        val reason =
                          s"Received ${GatewayDispatchType.Ready.value} with invalid resume_gateway_url or session_id"
                        log.warn(reason) *> disconnectAndReconnect(reason, code = 4002)
                    }
                  case GatewayEvent.Reconnect(_)       => reconnect(true)
                  case GatewayEvent.InvalidSession(ev) => reconnect(ev.d)
                  case GatewayEvent.Hello(ev) =>
                    for {
                      _ <- startHeartbeat(ev.d.heartbeatInterval.millis, supervisor)
                      _ <- sendIdentify
                    } yield ()

                  case GatewayEvent.Heartbeat(_)    => heartbeatNowQueue.offer(())
                  case GatewayEvent.HeartbeatACK(_) => receivedHeartbeatAckRef.set(true)
                  case _                            => Applicative[F].unit
                }

                Concurrent[F].uncancelable(p => p(actOnEvent) *> handleExternally)
              case Left(_) =>
                disconnectAndReconnect("Received payload with invalid op field", code = 4002).widen
            }
          case None => ().pure
        }
        .handleErrorWith { e =>
          log.error(e)("Exception raised while handling WebSocket event. Please report this to AckCord")
        }

    private def checkHeartbeatAckReceived: F[Unit] = receivedHeartbeatAckRef.get.ifM(
      ifTrue = ().pure,
      ifFalse = log.warn("Did not receive HeartbeatACK. Reconnecting") *> disconnectAndReconnect(
        "Did not receive HeartbeatACK",
        4000
      )
    )

    private def startHeartbeat(heartbeatInterval: FiniteDuration, supervisor: Supervisor[F]): F[Unit] = {
      val F      = Temporal[F]
      val jitter = math.random()
      supervisor
        .supervise(
          F.sleep(jitter * heartbeatInterval) *> sendHeartbeat *> ().iterateForeverM { _ =>
            F.race(
              F.sleep(heartbeatInterval),
              heartbeatNowQueue.take
            ) *>
              heartbeatNowQueue.tryTake *> //Just in case
              checkHeartbeatAckReceived *>
              sendHeartbeat
          }
        )
        .void
    }

    def run: F[DisconnectBehavior] = {
      val receiveResource = for {
        supervisor <- Supervisor[F](await = false)
        inflator   <- Inflate[F].newInflater()
      } yield receiveSingleEvent(supervisor, inflator)

      receiveResource
        .use(recv => false.iterateUntilM(_ => recv *> disconnectBehavior.tryGet.map(_.isDefined))(identity))
        .flatMap(_ => disconnectBehavior.get)
    }

    override protected[CatsGatewayHandlerFactory] def sendResume(resumeData: ResumeData): F[Unit] =
      super.sendResume(resumeData)

    override def resumeData: F[Option[ResumeData]] =
      (for {
        sessionId  <- OptionT(sessionIdRef.get)
        seq        <- OptionT(lastReceivedSeqRef.get)
        gatewayUrl <- OptionT(resumeGatewayUrlRef.get)
      } yield ResumeData(sessionId, seq, gatewayUrl)).value

    override def lastSeq: F[Option[Int]] = lastReceivedSeqRef.get
  }
}
