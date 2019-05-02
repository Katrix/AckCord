package ackcord.voice

import ackcord.data.{RawSnowflake, UserId}
import akka.NotUsed
import akka.stream.scaladsl.BidiFlow
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.iwebpp.crypto.TweetNaclFast

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class NaclBidiFlow(ssrc: Int, serverId: RawSnowflake, userId: UserId, secretKeyFut: Future[ByteString])
    extends GraphStage[BidiShape[ByteString, ByteString, ByteString, AudioAPIMessage.ReceivedData]] {

  val in1: Inlet[ByteString]                     = Inlet("NaclBidiFlow.in1")
  val out1: Outlet[ByteString]                   = Outlet("NaclBidiFlow.out1")
  val in2: Inlet[ByteString]                     = Inlet("NaclBidiFlow.in2")
  val out2: Outlet[AudioAPIMessage.ReceivedData] = Outlet("NaclBidiFlow.out2")

  override def shape: BidiShape[ByteString, ByteString, ByteString, AudioAPIMessage.ReceivedData] =
    BidiShape(in1, out1, in2, out2)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    var sequence: Short = 0
    var timestamp       = 0

    private def consuming[A, B](in: Inlet[A], out: Outlet[B]) = new InHandler with OutHandler {
      override def onPush(): Unit = grab(in)

      override def onPull(): Unit = pull(in)
    }

    setHandlers(in1, out1, consuming(in1, out1))
    setHandlers(in2, out2, consuming(in2, out2))

    override def preStart(): Unit = {
      val cb = getAsyncCallback[Try[ByteString]](onFutureCompleted).invoke _
      secretKeyFut.onComplete(cb)(materializer.executionContext)
    }

    def onFutureCompleted(res: Try[ByteString]): Unit = res match {
      case Success(secretKey) =>
        val secret = new TweetNaclFast.SecretBox(secretKey.toArray)

        setHandlers(
          in1,
          out1,
          new InHandler with OutHandler {
            override def onPush(): Unit = {
              val data   = grab(in1)
              val header = RTPHeader(sequence, timestamp, ssrc)
              sequence = (sequence + 1).toShort
              timestamp += VoiceUDPFlow.FrameSize
              val encrypted = secret.box(data.toArray, header.asNonce.toArray)
              push(out1, header.byteString ++ ByteString(encrypted))
            }

            override def onPull(): Unit = pull(in1)
          }
        )

        setHandlers(
          in2,
          out2,
          new InHandler with OutHandler {
            override def onPush(): Unit = {
              val data               = grab(in2)
              val (rtpHeader, voice) = RTPHeader.fromBytes(data)
              if (voice.length >= 16 && rtpHeader.version != -55 && rtpHeader.version != -56) { //FIXME: These break stuff
                val decryptedData = secret.open(voice.toArray, rtpHeader.asNonce.toArray)
                if (decryptedData != null) {
                  val byteStringDecrypted = ByteString(decryptedData)
                  push(out2, AudioAPIMessage.ReceivedData(byteStringDecrypted, rtpHeader, serverId, userId))
                } else {
                  failStage(new Exception(s"Failed to decrypt voice data Header: $rtpHeader Received voice: $voice"))
                }
              }
            }

            override def onPull(): Unit = pull(in2)
          }
        )

      case Failure(e) => failStage(e)
    }

  }
}
object NaclBidiFlow {

  def bidiFlow(
      ssrc: Int,
      serverId: RawSnowflake,
      userId: UserId,
      secretKeyFut: Future[ByteString]
  ): BidiFlow[ByteString, ByteString, ByteString, AudioAPIMessage.ReceivedData, NotUsed] =
    BidiFlow.fromGraph(new NaclBidiFlow(ssrc, serverId, userId, secretKeyFut))
}
