package ackcord.gateway.impl

import ackcord.data.UndefOrSome
import ackcord.gateway.data.{GatewayEvent, GatewayEventBase}
import ackcord.gateway.{Compress, GatewayHandler, IdentifyData, Inflate, ResumeData}
import io.circe._
import io.circe.syntax._
import org.typelevel.log4cats.Logger
import sttp.monad.syntax._
import sttp.ws.{WebSocket, WebSocketFrame}

abstract class NormalGatewayHandlerBase[F[_]](
    ws: WebSocket[F],
    identifyData: IdentifyData,
    log: Logger[F],
    logMessages: Boolean
) extends GatewayHandler.NormalGatewayHandler[F] {
  import ws.monad

  protected def handleClose(close: Option[WebSocketFrame.Close]): F[Unit]

  protected def sendEvent[E <: GatewayEventBase[_]: Encoder](ev: E): F[Unit] = {
    val msg = ev.asJson.noSpaces
    if (msg.length > 4096) {
      ws.monad.error(
        new Exception("Event too send too big. Can only send at most 4096 bytes in a message over the gateway")
      )
    } else {
      val logF = if (logMessages) log.debug(s"Sending message to Discord: $msg") else ().unit

      logF.flatMap { _ =>
        ws.either(ws.sendText(msg)).flatMap {
          case Right(value) => value.unit
          case Left(close)  => handleClose(close)
        }
      }
    }
  }

  protected def receiveCompressedText(inflater: Inflate.PureInflater[F]): F[String] = {
    type Tpe = Either[Array[Byte], String]
    ws
      .receiveDataFrame()
      .flatMap {
        case WebSocketFrame.Text(payload, false, _)   => ws.receiveText().map(s => Right(payload + s): Tpe)
        case WebSocketFrame.Text(payload, true, _)    => (Right(payload): Tpe).unit
        case WebSocketFrame.Binary(payload, false, _) => ws.receiveBinary(true).map(b => Left(payload ++ b): Tpe)
        case WebSocketFrame.Binary(payload, true, _)  => (Left(payload): Tpe).unit
      }
      .flatMap {
        case Right(str) => str.unit
        case Left(bytes) =>
          identifyData.compress match {
            case Compress.NoCompress => new String(bytes, "UTF-8").unit
            case Compress.PerMessageCompress =>
              for {
                _   <- inflater.reset
                res <- inflater.inflateToString(bytes, "UTF-8")
              } yield res
            case Compress.ZLibStreamCompress =>
              inflater.inflateToString(bytes, "UTF-8")
          }
      }
      .flatTap(msg => if (logMessages) log.debug(s"Received message from Discord: $msg") else ().unit)
  }

  protected def receiveRawGatewayEvent(inflater: Inflate.PureInflater[F]): F[Option[Json]] =
    ws.either(receiveCompressedText(inflater).flatMap(s => ws.monad.fromTry(parser.parse(s).toTry))).flatMap {
      case Right(value) => (Some(value): Option[Json]).unit
      case Left(close)  => handleClose(close).map(_ => None)
    }

  protected def sendHeartbeat: F[Unit] =
    lastSeq.flatMap(s => sendEvent(GatewayEvent.Heartbeat.make20(d = s)))

  protected def sendIdentify: F[Unit] =
    sendEvent(
      GatewayEvent.Identify.make20(d =
        GatewayEvent.Identify.Data.make20(
          identifyData.token,
          identifyData.properties,
          UndefOrSome(identifyData.compress == Compress.PerMessageCompress),
          identifyData.largeThreshold,
          identifyData.shard,
          identifyData.presence,
          identifyData.intents
        )
      )
    )

  protected def sendResume(resumeData: ResumeData): F[Unit] =
    sendEvent(
      GatewayEvent.Resume.make20(d =
        GatewayEvent.Resume.Data.make20(identifyData.token, resumeData.sessionId, resumeData.seq)
      )
    )
}
