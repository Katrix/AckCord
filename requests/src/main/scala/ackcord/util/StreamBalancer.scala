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

import akka.NotUsed
import akka.stream.scaladsl.{Balance, Flow, GraphDSL, Merge}
import akka.stream.{FlowShape, Graph}

object StreamBalancer {

  def balanceMerge[I, O](parallelism: Int, flow: Graph[FlowShape[I, O], NotUsed]): Flow[I, O, NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val balance = builder.add(Balance[I](parallelism))
      val merge = builder.add(Merge[O](parallelism))

      for (i <- 0 until parallelism) {
        balance.out(i) ~> flow.async ~> merge.in(i)
      }

      FlowShape(balance.in, merge.out)
    })
  }
}
