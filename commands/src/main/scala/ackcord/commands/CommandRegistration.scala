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
package ackcord.commands

import scala.concurrent.Future

import akka.Done
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}

case class CommandRegistration[Mat](
    materialized: Mat,
    onDone: Future[Done],
    killSwitch: UniqueKillSwitch
) {

  def stop(): Unit = killSwitch.shutdown()
}
object CommandRegistration {
  def toSink[A, M](
      source: Source[A, M]
  ): RunnableGraph[CommandRegistration[M]] =
    source.viaMat(KillSwitches.single)(Keep.both).toMat(Sink.ignore) {
      case ((m, killSwitch), done) =>
        CommandRegistration(m, done, killSwitch)
    }

  def withRegistration[A, M](
      source: Source[A, M]
  ): Source[A, CommandRegistration[M]] =
    source.viaMat(KillSwitches.single)(Keep.both).watchTermination() {
      case ((m, killSwitch), done) =>
        CommandRegistration(m, done, killSwitch)
    }
}
