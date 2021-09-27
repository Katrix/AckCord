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

package ackcord.util

import java.net.InetSocketAddress

import scala.collection.immutable

import ackcord.util.UdpConnectedFlow.UDPAck
import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.io.Inet.SocketOption
import akka.io.{IO, UdpConnected}
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStageLogic.StageActorRef
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

class UdpConnectedFlow(
    remoteAddress: InetSocketAddress,
    localAddress: Option[InetSocketAddress],
    connectOptions: immutable.Iterable[SocketOption]
)(implicit
    system: ActorSystem[Nothing]
) extends GraphStage[FlowShape[ByteString, ByteString]] {

  val in: Inlet[ByteString] = Inlet("UdpConnectedFlow.in")
  val out: Outlet[ByteString] = Outlet("UdpConnectedFlow.out")

  override def shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogicWithLogging(shape) with InHandler with OutHandler {

      private var socket: ActorRef = _
      private var nextElement: ByteString = _

      private var canSend = true
      private var hasSentDisconnect = false
      private var shouldDisconnect = false

      var self: ActorRef = _

      override def preStart(): Unit = {
        self = getStageActor(actorReceive).ref
        implicit val selfSender: ActorRef = self
        IO(UdpConnected)(system.toClassic) ! UdpConnected.Connect(
          self,
          remoteAddress,
          localAddress,
          connectOptions
        )
      }

      override def onUpstreamFinish(): Unit =
        if (!hasSentDisconnect) {
          disconnect()
        }

      override def onDownstreamFinish(cause: Throwable): Unit =
        if (socket != null && !hasSentDisconnect) {
          sendDisconnect()
        }

      override def postStop(): Unit = {
        if (!hasSentDisconnect) {
          log.warning(
            "Stopped without properly disconnecting. Not dangerous, but probably indicates an error somewhere"
          )
          disconnect()
        }
      }

      private def disconnect(): Unit = {
        if (socket != null) {
          if (nextElement != null || !canSend) {
            shouldDisconnect = true
          } else {
            sendDisconnect()
          }
        }
      }

      private def sendDisconnect(): Unit = {
        hasSentDisconnect = true
        implicit val selfSender: ActorRef = self
        socket ! UdpConnected.Disconnect
      }

      def actorReceive: StageActorRef.Receive = {
        case (sender, UdpConnected.Connected) =>
          socket = sender
          pull(in)
        case (_, UdpConnected.Disconnected) =>
          completeStage()
        case (_, UdpConnected.CommandFailed(cmd: UdpConnected.Connect)) =>
          failStage(
            new IllegalArgumentException(
              s"Unable to bind to [${cmd.localAddress}]"
            )
          )
        case (_, UdpConnected.CommandFailed(cmd)) =>
          failStage(new IllegalStateException(s"Command failed: $cmd"))
        case (_, UdpConnected.Received(data)) =>
          emit(out, data)
        case (_, UDPAck) =>
          if (nextElement != null) {
            send(nextElement)
            nextElement = null
            tryPull(in)
          } else {
            canSend = true

            if (shouldDisconnect) {
              sendDisconnect()
            }
          }
        case (_, event) =>
          failStage(new IllegalArgumentException(s"Unknown event: $event"))
      }

      override def onPush(): Unit = {
        if (canSend) {
          canSend = false
          send(grab(in))
          tryPull(in)
        } else {
          nextElement = grab(in)
        }
      }

      private def send(data: ByteString): Unit = {
        //log.info(s"Sending data $data $name")
        implicit val selfSender: ActorRef = self
        socket ! UdpConnected.Send(data, UDPAck)
      }

      override def onPull(): Unit = ()

      setHandlers(in, out, this)
    }
}
object UdpConnectedFlow {
  private object UDPAck

  def flow(
      remoteAddress: InetSocketAddress,
      localAddress: Option[InetSocketAddress] = None,
      connectOptions: immutable.Iterable[SocketOption] = Nil
  )(implicit
      system: ActorSystem[Nothing]
  ): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(
      new UdpConnectedFlow(remoteAddress, localAddress, connectOptions)
    )
}
