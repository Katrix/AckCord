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

import akka.actor.{ActorRef, FSM, Props}
import akka.io.{IO, UdpConnected}

class VoiceUDPHandler(address: String, ssrc: Int, port: Int) extends FSM[VoiceUDPHandler.State, VoiceUDPHandler.Data] {
  import VoiceUDPHandler._
  import context.system
  IO(UdpConnected) ! UdpConnected.Connect(self, new InetSocketAddress(address, port))

  startWith(Inactive, NoSocket)

  when(Inactive) {
    case Event(UdpConnected.Connected, NoSocket) => stay using WithSocket(sender())
    case Event(UdpConnected.Disconnect, WithSocket(socket)) =>
      socket ! UdpConnected.Disconnect
      stay()
    case Event(UdpConnected.Disconnected, _) =>
      stop()
  }

  when(Active) {
    case Event(UdpConnected.Disconnect, WithSocket(socket)) =>
      socket ! UdpConnected.Disconnect
      stay()
    case Event(UdpConnected.Disconnected, _) =>
      stop()
  }

  initialize()
}
object VoiceUDPHandler {
  def props(address: String, ssrc: Int, port: Int): Props = Props(new VoiceUDPHandler(address, ssrc, port))

  sealed trait State
  case object Inactive extends State
  case object Active   extends State

  sealed trait Data
  case object NoSocket                    extends Data
  case class WithSocket(socket: ActorRef) extends Data
}
