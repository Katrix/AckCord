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

import java.nio.ByteBuffer

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.immutable

import ackcord.data.{RawSnowflake, UserId}
import akka.stream._
import akka.stream.scaladsl.{BidiFlow, GraphDSL, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.iwebpp.crypto.TweetNaclFast

case class BidiShapeWithExtraIn[-In1, +Out1, -In2, +Out2, -EIn](
    in1: Inlet[In1 @uncheckedVariance],
    out1: Outlet[Out1 @uncheckedVariance],
    in2: Inlet[In2 @uncheckedVariance],
    out2: Outlet[Out2 @uncheckedVariance],
    extraIn: Inlet[EIn @uncheckedVariance]
) extends Shape {
  override def inlets: immutable.Seq[Inlet[_]] = immutable.Seq(in1, in2, extraIn)

  override def outlets: immutable.Seq[Outlet[_]] = immutable.Seq(out1, out2)

  override def deepCopy(): Shape =
    BidiShapeWithExtraIn(in1.carbonCopy(), out1.carbonCopy(), in2.carbonCopy(), out2.carbonCopy(), extraIn.carbonCopy())
}

class NaclBidiFlow(ssrc: Int, serverId: RawSnowflake, userId: UserId)
    extends GraphStage[
      BidiShapeWithExtraIn[ByteString, ByteString, ByteString, AudioAPIMessage.ReceivedData, Option[ByteString]]
    ] {

  val in1: Inlet[ByteString]                     = Inlet("NaclBidiFlow.in1")
  val out1: Outlet[ByteString]                   = Outlet("NaclBidiFlow.out1")
  val in2: Inlet[ByteString]                     = Inlet("NaclBidiFlow.in2")
  val out2: Outlet[AudioAPIMessage.ReceivedData] = Outlet("NaclBidiFlow.out2")

  val secretKeysIn: Inlet[Option[ByteString]] = Inlet("NaclBidiFlow.secretKeysIn")

  override def shape
      : BidiShapeWithExtraIn[ByteString, ByteString, ByteString, AudioAPIMessage.ReceivedData, Option[ByteString]] =
    BidiShapeWithExtraIn(in1, out1, in2, out2, secretKeysIn)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private val nonceEncryptBuffer = ByteBuffer.allocate(24)
    private val nonceDecryptBuffer = ByteBuffer.allocate(24)

    var sequence: Short = 0
    var timestamp       = 0

    var currentSecretKey: Option[ByteString] = None

    private def consuming[A, B](in: Inlet[A], out: Outlet[B]) = new InHandler with OutHandler {
      override def onPush(): Unit = grab(in)

      override def onPull(): Unit = pull(in)
    }

    setHandlers(in1, out1, consuming(in1, out1))
    setHandlers(in2, out2, consuming(in2, out2))

    setHandler(
      secretKeysIn,
      new InHandler {
        override def onPush(): Unit = {
          val newKey = grab(secretKeysIn)

          if (currentSecretKey != newKey) {
            currentSecretKey = newKey

            newKey match {
              case Some(someKey) => gotNewSecretKey(someKey)
              case None =>
                setHandlers(in1, out1, consuming(in1, out1))
                setHandlers(in2, out2, consuming(in2, out2))
            }
          }
        }
      }
    )

    override def preStart(): Unit =
      pull(secretKeysIn)

    def gotNewSecretKey(secretKey: ByteString): Unit = {
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

            nonceEncryptBuffer.clear()
            header.nonceToBuffer(nonceEncryptBuffer)
            val encrypted = secret.box(data.toArray, nonceEncryptBuffer.array())
            push(out1, header.byteString ++ ByteString.fromArrayUnsafe(encrypted))
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
              rtpHeader.nonceToBuffer(nonceDecryptBuffer)

              nonceDecryptBuffer.clear()
              val decryptedData = secret.open(voice.toArray, nonceDecryptBuffer.array())
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
    }

  }
}
object NaclBidiFlow {

  def bidiFlow[Mat](
      ssrc: Int,
      serverId: RawSnowflake,
      userId: UserId,
      secretKeys: Source[Option[ByteString], Mat]
  ): BidiFlow[ByteString, ByteString, ByteString, AudioAPIMessage.ReceivedData, Mat] = {
    val graph = GraphDSL.createGraph(secretKeys) { implicit b => keys =>
      import GraphDSL.Implicits._

      val naclBidiFlow = b.add(new NaclBidiFlow(ssrc, serverId, userId))

      keys ~> naclBidiFlow.extraIn

      BidiShape(naclBidiFlow.in1, naclBidiFlow.out1, naclBidiFlow.in2, naclBidiFlow.out2)
    }

    BidiFlow.fromGraph(graph)
  }
}
