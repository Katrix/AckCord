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

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.immutable

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}

//TODO: Maybe use a third inlet to determine where to listen to
class Switch[A](ref: AtomicBoolean, emitChangeTrue: immutable.Seq[A], emitChangeFalse: immutable.Seq[A])
    extends GraphStage[FanInShape2[A, A, A]] {
  override val shape: FanInShape2[A, A, A] = new FanInShape2[A, A, A]("Switch")

  val in1: Inlet[A]  = shape.in0
  val in2: Inlet[A]  = shape.in1
  val out: Outlet[A] = shape.out

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {

      private var lastState: Boolean = ref.get()
      private var waitingOther: A    = _

      private def activeIn(): Inlet[A] = {
        val newState = ref.get()
        val newIn    = if (newState) in1 else in2

        if (lastState != newState) {
          lastState = newState

          emitMultiple(out, if (newState) emitChangeTrue else emitChangeFalse)

          if (waitingOther != null) {
            emit(out, waitingOther)
            waitingOther = null.asInstanceOf[A]
          }

          tryPull(newIn)
        }

        newIn
      }

      private def setInHandler(in: Inlet[A]): Unit = {
        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = {

              if (activeIn() == in) {
                emit(out, grab(in))
              } else {
                require(waitingOther == null, "Pushed other when a waiting other was already defined")
                waitingOther = grab(in)
              }
            }
          }
        )
      }

      setInHandler(in1)
      setInHandler(in2)

      setHandler(out, this)

      override def onPull(): Unit = pull(activeIn())
    }
}
