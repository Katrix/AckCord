/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ackcord.voice

import java.net.InetSocketAddress

import scala.concurrent.{Future, Promise}

import ackcord.data.{RawSnowflake, UserId}
import ackcord.util.UdpConnectedFlow
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.{BidiShape, OverflowStrategy}
import akka.stream.scaladsl.{BidiFlow, Concat, Flow, GraphDSL, Keep, Source}
import akka.util.ByteString

object VoiceUDPFlow {

  val silence = ByteString(0xF8, 0xFF, 0xFE)

  val SampleRate = 48000
  val FrameSize  = 960
  val FrameTime  = 20

  def flow[Mat](
      remoteAddress: InetSocketAddress,
      ssrc: Int,
      serverId: RawSnowflake,
      userId: UserId,
      secretKeys: Source[Option[ByteString], Mat]
  )(implicit system: ActorSystem[Nothing]): Flow[ByteString, AudioAPIMessage.ReceivedData, (Mat, Future[FoundIP])] =
    NaclBidiFlow
      .bidiFlow(ssrc, serverId, userId, secretKeys)
      .atopMat(voiceBidi(ssrc).reversed)(Keep.both)
      .async
      .join(Flow[ByteString].buffer(32, OverflowStrategy.backpressure).via(UdpConnectedFlow.flow(remoteAddress)))

  def voiceBidi(ssrc: Int): BidiFlow[ByteString, ByteString, ByteString, ByteString, Future[FoundIP]] = {
    val ipDiscoveryPacket = Compat.padBytestring(ByteString(ssrc), 70, 0.toByte)

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
