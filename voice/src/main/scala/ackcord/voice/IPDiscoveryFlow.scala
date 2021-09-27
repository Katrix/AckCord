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

import java.nio.ByteOrder

import scala.concurrent.{Future, Promise}

import akka.stream.scaladsl.Flow
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

class IPDiscoveryFlow(openValve: () => Unit)
    extends GraphStageWithMaterializedValue[
      FlowShape[ByteString, ByteString],
      Future[VoiceUDPFlow.FoundIP]
    ] {

  val in: Inlet[ByteString] = Inlet("IPDiscoveryFlow.in")
  val out: Outlet[ByteString] = Outlet("IPDiscoveryFlow.out")

  override def shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes
  ): (GraphStageLogic, Future[VoiceUDPFlow.FoundIP]) = {
    val promise = Promise[VoiceUDPFlow.FoundIP]()
    val logic = new GraphStageLogicWithLogging(shape)
      with InHandler
      with OutHandler {

      override def onPush(): Unit = {
        val data = grab(in)
        log.debug(s"Grabbing data for IP discovery $data")
        val byteBuf = data.asByteBuffer.order(ByteOrder.BIG_ENDIAN)
        val tpe = byteBuf.getShort

        require(tpe == 0x2, s"Was expecting IP discovery result, got $tpe")

        byteBuf.getShort //Length
        byteBuf.getInt //SSRC
        val nullTermString = new Array[Byte](64)
        byteBuf.get(nullTermString)
        val address = new String(
          nullTermString,
          0,
          nullTermString.iterator.takeWhile(_ != 0).length
        )
        val port = byteBuf.getChar.toInt //Char is unsigned short

        promise.success(VoiceUDPFlow.FoundIP(address, port))
        log.debug("Success doing IP discovery")

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = push(out, grab(in))
          }
        )

        openValve()
      }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFailure(ex: Throwable): Unit = {
        promise.tryFailure(new Exception("Connection failed.", ex))
        super.onUpstreamFailure(ex)
      }

      setHandlers(in, out, this)
    }

    (logic, promise.future)
  }
}
object IPDiscoveryFlow {
  def flow(
      openValve: () => Unit
  ): Flow[ByteString, ByteString, Future[VoiceUDPFlow.FoundIP]] =
    Flow.fromGraph(new IPDiscoveryFlow(openValve))
}
