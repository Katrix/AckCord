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

package ackcord.requests

import akka.actor.typed.ActorSystem
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorAttributes, Attributes, Supervision}

object SupervisionStreams {

  def addLogAndContinueFunction[G](
      addAtributes: Attributes => G
  )(implicit system: ActorSystem[Nothing]): G =
    addAtributes(ActorAttributes.supervisionStrategy {
      case _: RetryFailedRequestException[_] => Supervision.Stop
      case e =>
        system.log.error("Unhandled exception in stream", e)
        Supervision.Resume
    })

  def logAndContinue[M](graph: RunnableGraph[M])(implicit
      system: ActorSystem[Nothing]
  ): RunnableGraph[M] =
    addLogAndContinueFunction(graph.addAttributes)

  def logAndContinue[Out, Mat](source: Source[Out, Mat])(implicit
      system: ActorSystem[Nothing]
  ): Source[Out, Mat] =
    addLogAndContinueFunction(source.addAttributes)

  def logAndContinue[In, Out, Mat](
      flow: Flow[In, Out, Mat]
  )(implicit system: ActorSystem[Nothing]): Flow[In, Out, Mat] =
    addLogAndContinueFunction(flow.addAttributes)

  def logAndContinue[In, Mat](sink: Sink[In, Mat])(implicit
      system: ActorSystem[Nothing]
  ): Sink[In, Mat] =
    addLogAndContinueFunction(sink.addAttributes)
}
