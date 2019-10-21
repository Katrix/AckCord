package ackcord.voice

import java.net.InetSocketAddress

import ackcord.data.{RawSnowflake, UserId}
import ackcord.util.UdpConnectedFlow
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.BidiShape
import akka.stream.scaladsl.{BidiFlow, Concat, Flow, GraphDSL, Keep, Source}
import akka.util.ByteString

import scala.concurrent.{Future, Promise}

object VoiceUDPFlow {

  val silence = ByteString(0xF8, 0xFF, 0xFE)

  val SampleRate = 48000
  val FrameSize  = 960
  val FrameTime  = 20

  def flow(
      remoteAddress: InetSocketAddress,
      ssrc: Int,
      serverId: RawSnowflake,
      userId: UserId,
      secretKeyFut: Future[ByteString]
  )(implicit system: ActorSystem): Flow[ByteString, AudioAPIMessage.ReceivedData, Future[FoundIP]] =
    NaclBidiFlow
      .bidiFlow(ssrc, serverId, userId, secretKeyFut)
      .atopMat(voiceBidi(ssrc).reversed)(Keep.right)
      .join(UdpConnectedFlow.flow(remoteAddress))

  def voiceBidi(ssrc: Int): BidiFlow[ByteString, ByteString, ByteString, ByteString, Future[FoundIP]] = {
    val ipDiscoveryPacket = ByteString(ssrc).padTo(70, 0.toByte)

    val valvePromise = Promise[Unit]
    val valve        = Source.future(valvePromise.future).drop(1).asInstanceOf[Source[ByteString, NotUsed]]

    val ipDiscoveryFlow = Flow[ByteString]
      .viaMat(new IPDiscoveryFlow(() => valvePromise.success(())))(Keep.right)

    BidiFlow
      .fromGraph(GraphDSL.create(ipDiscoveryFlow) { implicit b => ipDiscovery =>
        import GraphDSL.Implicits._

        val voiceIn = b.add(Flow[ByteString])

        val ipDiscoverySource           = b.add(Source.single(ipDiscoveryPacket) ++ valve)
        val ipDiscoveryAndThenVoiceData = b.add(Concat[ByteString]())

        ipDiscoverySource ~> ipDiscoveryAndThenVoiceData
        voiceIn ~> ipDiscoveryAndThenVoiceData

        BidiShape(
          ipDiscovery.in,
          ipDiscovery.out,
          voiceIn.in,
          ipDiscoveryAndThenVoiceData.out
        )
      })
  }

  /**
    * Materialized result after the handshake of a Voice UDP connection
    * @param address Our address or ip address
    * @param port Our port
    */
  case class FoundIP(address: String, port: Int)
}
