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

import scala.collection.mutable
import scala.concurrent.duration._

import com.iwebpp.crypto.TweetNaclFast

import akka.actor.{ActorRef, ActorSystem, FSM, Props}
import akka.io.{IO, UdpConnected}
import akka.util.ByteString
import net.katsstuff.ackcord.AudioAPIMessage
import net.katsstuff.ackcord.data.{Snowflake, UserId}

/**
  * Handles the UDP part of voice connection
  * @param address The address to connect to
  * @param ssrc Pur ssrc
  * @param port The port to use for connection
  * @param sendSoundTo The actor to send [[AudioAPIMessage.ReceivedData]] to
  * @param serverId The server id
  * @param userId Our user Id
  */
class VoiceUDPHandler(
    address: String,
    ssrc: Int,
    port: Int,
    sendSoundTo: Option[ActorRef],
    serverId: Snowflake,
    userId: UserId
) extends FSM[VoiceUDPHandler.State, VoiceUDPHandler.Data] {
  import VoiceUDPHandler._

  implicit val system: ActorSystem = context.system
  import context.dispatcher

  IO(UdpConnected) ! UdpConnected.Connect(self, new InetSocketAddress(address, port))

  startWith(Inactive, NoSocket)

  var sequence: Short = 0
  var timestamp = 0

  /**
    * Sometimes we send messages too quickly, in that case we put
    * the remaining in here.
    */
  val queue: mutable.Queue[ByteString] = mutable.Queue.empty[ByteString]

  when(Inactive) {
    case Event(UdpConnected.Connected, NoSocket) => stay using WithSocket(sender())
    case Event(ip: DoIPDiscovery, NoSocket)      =>
      //We received the request a bit early, resend it
      system.scheduler.scheduleOnce(100.millis, self, ip)
      log.info("Resending DoIPDiscovery request")
      stay()
    case Event(ip: DoIPDiscovery, WithSocket(socket)) =>
      val payload = ByteString(ssrc).padTo(70, 0.toByte)
      socket ! UdpConnected.Send(payload)
      stay using ExpectingResponse(socket, IPDiscovery(ip.replyTo))
    case Event(UdpConnected.Received(data), ExpectingResponse(socket, IPDiscovery(replyTo))) =>
      val (addressBytes, portBytes) = data.splitAt(68)

      val address = new String(addressBytes.drop(4).toArray).trim
      val port    = portBytes.asByteBuffer.order(ByteOrder.LITTLE_ENDIAN).getShort

      replyTo ! FoundIP(address, port)
      stay using WithSocket(socket)
    case Event(StartConnection(secretKey), WithSocket(socket)) =>
      goto(Active) using WithSecret(socket, new TweetNaclFast.SecretBox(secretKey.toArray))
    case Event(Disconnect, WithSocket(socket)) =>
      socket ! UdpConnected.Disconnect
      stay()
    case Event(UdpConnected.Disconnect, WithSocket(socket)) =>
      socket ! UdpConnected.Disconnect
      stay()
    case Event(UdpConnected.Disconnected, _) =>
      stop()
  }

  when(Active) {
    case Event(UdpConnected.Received(data), WithSecret(_, secret)) =>
      sendSoundTo.foreach { receiver =>
        val (header, voice) = data.splitAt(12)
        val rtpHeader       = RTPHeader(header)
        val decryptedData   = secret.open(voice.toArray, rtpHeader.asNonce.toArray)
        receiver ! AudioAPIMessage.ReceivedData(ByteString(decryptedData), rtpHeader, serverId, userId)
      }
      stay()
    case Event(SendData(data), WithSecret(socket, secret)) =>
      val header = RTPHeader(sequence, timestamp, ssrc)
      sequence = (sequence + 1).toShort
      timestamp += FrameSize
      val encrypted = secret.box(data.toArray, header.asNonce.toArray)
      val payload   = header.byteString ++ ByteString(encrypted)
      queue.enqueue(payload) //This is removed as soon as we receive the ack if it's the only thing in the queue

      if (queue.size == 1) {
        socket ! UdpConnected.Send(payload, UDPAck)
      } else if (queue.size > 32) {
        log.warning("Behind on audio send")
      }
      stay()
    case Event(UDPAck, WithSecret(socket, _)) =>
      queue.dequeue() //Remove the one we received an ack for

      if (queue.nonEmpty) {
        socket ! UdpConnected.Send(queue.head, UDPAck)
      }
      stay()
    case Event(Disconnect, WithSecret(socket, _)) =>
      socket ! UdpConnected.Disconnect
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
  def props(
      address: String,
      ssrc: Int,
      port: Int,
      sendSoundTo: Option[ActorRef],
      serverId: Snowflake,
      userId: UserId
  ): Props = Props(new VoiceUDPHandler(address, ssrc, port, sendSoundTo, serverId, userId))

  sealed trait State
  case object Inactive extends State
  case object Active   extends State

  sealed trait Data
  case object NoSocket                                                     extends Data
  case class WithSocket(socket: ActorRef)                                  extends Data
  case class WithSecret(socket: ActorRef, secret: TweetNaclFast.SecretBox) extends Data

  /**
    * Used if we're expecting some response from another actor. Currently
    * only used for IP discovery
    * @param socket The socket actor
    * @param expectedTpe The type of response we're expecting
    */
  case class ExpectingResponse(socket: ActorRef, expectedTpe: ExpectedResponseType) extends Data

  case class UDPAck(sequence: Short)

  sealed trait ExpectedResponseType
  case class IPDiscovery(replyTo: ActorRef) extends ExpectedResponseType

  /**
    * Send this when this is in [[Inactive]] to do IP discovery.
    * @param replyTo [[FoundIP]] is sent to this actor when the ip is found
    */
  case class DoIPDiscovery(replyTo: ActorRef)

  /**
    * Sent to an actor after [[DoIPDiscovery]]
    * @param address Our address or ip address
    * @param port Our port
    */
  case class FoundIP(address: String, port: Int)

  /**
    * Send to this to start a connection, and go from [[Inactive]] to [[Active]]
    * @param secretKey The secret key to use for encrypting
    *                  and decrypting voice data.
    */
  case class StartConnection(secretKey: ByteString)

  /**
    * Send to this to start disconnecting.
    */
  case object Disconnect

  /**
    * Send to this to send some data
    * @param data The data to send
    */
  case class SendData(data: ByteString)

  private val nonceLastPart = ByteString.fromArray(new Array(12))
  val silence               = ByteString(0xF8, 0xFF, 0xFE)

  val SampleRate = 48000
  val FrameSize  = 960
  val FrameTime  = 20

  /**
    * Represents the RTP header used for sending and receiving voice data
    * @param tpe The type to use. Should be `0x80`
    * @param version The version to use. Should be `0x78`
    * @param sequence The sequence
    * @param timestamp Timestamp
    * @param ssrc SSRC of sender
    */
  case class RTPHeader(tpe: Byte, version: Byte, sequence: Short, timestamp: Int, ssrc: Int) {
    lazy val byteString: ByteString = {
      val builder = ByteString.newBuilder
      implicit val order: ByteOrder = ByteOrder.BIG_ENDIAN
      builder.putByte(tpe)
      builder.putByte(version)
      builder.putShort(sequence)
      builder.putInt(timestamp)
      builder.putInt(ssrc)

      builder.result()
    }

    def asNonce: ByteString = byteString ++ nonceLastPart
  }
  object RTPHeader {

    /**
      * Deserialize an [[RTPHeader]]
      */
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
