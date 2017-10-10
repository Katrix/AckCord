/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord.http.websocket.voice

import java.net.InetSocketAddress
import java.nio.ByteOrder

import com.iwebpp.crypto.TweetNaclFast
import scala.concurrent.duration._

import akka.actor.{ActorRef, FSM, Props, Status}
import akka.io.{IO, UdpConnected}
import akka.stream.ThrottleMode
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.ByteString
import net.katsstuff.ackcord.http.websocket.voice.VoiceWsHandler.SetDataSource

class VoiceUDPHandler(address: String, ssrc: Int, port: Int, wsHandler: ActorRef)
    extends FSM[VoiceUDPHandler.State, VoiceUDPHandler.Data] {
  import VoiceUDPHandler._
  import context.system
  IO(UdpConnected) ! UdpConnected.Connect(self, new InetSocketAddress(address, port))

  startWith(Inactive, NoSocket)

  var sequence: Short = 0
  var timestamp = 0

  when(Inactive) {
    case Event(UdpConnected.Connected, NoSocket) => stay using WithSocket(sender())
    case Event(DoIPDiscovery, WithSocket(socket)) =>
      val payload = ByteString(ssrc).padTo(70, 0)
      socket ! UdpConnected.Send(payload)
      stay using ExpectingResponse(socket, IPDiscovery(sender()))
    case Event(UdpConnected.Received(data), ExpectingResponse(socket, IPDiscovery(replyTo))) =>
      val (addressBytes, portBytes) = data.splitAt(68)

      val address = new String(addressBytes.drop(4).toArray).trim //TODO: addressBytes.drop(4).utf8String?
      val port    = portBytes.asByteBuffer.order(ByteOrder.LITTLE_ENDIAN).getShort

      replyTo ! FoundIP(address, port)
      stay using WithSocket(socket)
    case Event(StartConnection(secretKey), WithSocket(socket)) =>
      goto(Active) using WithSecret(socket, new TweetNaclFast.SecretBox(secretKey.toArray))
    case Event(UdpConnected.Disconnect, WithSocket(socket)) =>
      socket ! UdpConnected.Disconnect
      stay()
    case Event(UdpConnected.Disconnected, _) =>
      stop()
  }

  when(Active) {
    case Event(UdpConnected.Received(data), WithSecret(_, secret)) =>
      val (header, voice) = data.splitAt(12)
      val rtpHeader       = RTPHeader(header)
      val decryptedData   = secret.open(voice.toArray, rtpHeader.asNonce.toArray)

      wsHandler ! VoiceWsHandler.ReceivedData(ByteString(decryptedData), rtpHeader)

      stay()
    case Event(SinkInit, _) =>
      sender() ! SinkAck
      stay()
    case Event(SendData(data), WithSecret(socket, secret)) =>
      val header = RTPHeader(sequence, timestamp, ssrc)
      sequence += 1
      timestamp += FrameSize
      val encrypted = secret.box(data.toArray, header.asNonce.toArray)
      socket ! UdpConnected.Send(header.byteString ++ ByteString(encrypted))
      sender() ! SinkAck
      stay()
    case Event(SinkComplete, _) =>
      for (i <- 1 to 5) {
        context.system.scheduler.scheduleOnce(i * 20.millis, self, SendData(silence))
      }

      stay()
    case Event(Status.Failure(e), _) => throw e
    case Event(SetDataSource(source), _) =>
      source
        .via(Flow[ByteString].throttle(1, 20.millis, 1, ThrottleMode.Shaping))
        .via(Flow.fromFunction(SendData))
        .runWith(
          Sink.actorRefWithAck(
            ref = self,
            onInitMessage = SinkInit,
            ackMessage = SinkAck,
            onCompleteMessage = SinkComplete
          )
        )

      stay()
    case Event(Disconnect, WithSecret(socket, _)) =>
      socket ! UdpConnected.Disconnected
      stay()
    case Event(UdpConnected.Disconnect, WithSecret(socket, _)) =>
      socket ! UdpConnected.Disconnect
      stay()
    case Event(UdpConnected.Disconnected, _) =>
      stop()
  }

  initialize()
}
object VoiceUDPHandler {
  def props(address: String, ssrc: Int, port: Int, wsHandler: ActorRef): Props =
    Props(new VoiceUDPHandler(address, ssrc, port, wsHandler))

  sealed trait State
  case object Inactive extends State
  case object Active   extends State

  sealed trait Data
  case object NoSocket                                                     extends Data
  case class WithSocket(socket: ActorRef)                                  extends Data
  case class WithSecret(socket: ActorRef, secret: TweetNaclFast.SecretBox) extends Data

  case class ExpectingResponse(socket: ActorRef, expectedTpe: ExpectedResponseType) extends Data

  sealed trait ExpectedResponseType
  case class IPDiscovery(replyTo: ActorRef) extends ExpectedResponseType

  case object SinkInit
  case object SinkAck
  case object SinkComplete

  case object DoIPDiscovery
  case class FoundIP(address: String, port: Int)
  case class StartConnection(secretKey: ByteString)
  case object Disconnect
  case class SendData(data: ByteString)

  private val nonceLastPart = ByteString(new Array(12))
  val silence               = ByteString(0xF8, 0xFF, 0xFE)

  val SampleRate = 48000
  val FrameSize  = 960
  val FrameTime  = 20

  case class RTPHeader(tpe: Byte, version: Byte, sequence: Short, timestamp: Int, ssrc: Int) {
    lazy val byteString: ByteString = {
      val builder = ByteString.newBuilder
      implicit val order: ByteOrder = ByteOrder.BIG_ENDIAN
      builder.putByte(tpe)
      builder.putByte(version)
      builder.putShort(sequence)
      builder.putShort(sequence)
      builder.putInt(timestamp)
      builder.putInt(ssrc)

      builder.result()
    }

    def asNonce: ByteString = byteString ++ nonceLastPart
  }
  object RTPHeader {
    def apply(bytes: ByteString): RTPHeader = {
      require(bytes.length >= 12)

      val buffer    = bytes.asByteBuffer.order(ByteOrder.BIG_ENDIAN)
      val tpe       = buffer.get()
      val version   = buffer.get()
      val sequence  = buffer.getShort()
      val timestamp = buffer.getInt()
      val ssrc      = buffer.getInt()
      RTPHeader(tpe, version, sequence, timestamp, ssrc)
    }
    def apply(sequence: Short, timestamp: Int, ssrc: Int): RTPHeader =
      RTPHeader(0x80.toByte, 0x78, sequence, timestamp, ssrc)
  }
}
