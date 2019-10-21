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
package ackcord.examplecore

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, MergePreferred, Partition, Source}
import akka.stream.FlowShape

object RetryErrorExampleSimple /*extends App*/ {

  def retryProcessSimple(maxRetryCount: Int = 3) = {
    type Res = Either[(String, Int), Int]
    val graph = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val addContext = builder.add(Flow[Int].map(obj => (obj, 0)))
      val process    = Flow[(Int, Int)].map(req => Right(req._1) -> req._2)

      val processed = builder.add(MergePreferred[(Res, Int)](1))
      val partitioner = builder.add(
        Partition[(Res, Int)](2, {
          case (Right(_), _) => 0
          case (Left(_), _)  => 1
        })
      )

      val successful = partitioner.out(0)
      val failed     = partitioner.out(1)

      val filterAndUpdateRetryCount = builder.add(
        Flow[(Res, Int)]
          .collect {
            case (Left((_, obj)), retryCount) if retryCount + 1 < maxRetryCount => (obj, retryCount + 1)
          }
      )

      val unwrapSuccess = builder.add(
        Flow[(Res, Int)]
          .collect {
            case (Right(value), _) => value
          }
      )

      // format: OFF
      addContext          ~> process ~> processed.in(0);processed ~> partitioner
      processed.preferred <~ process <~ filterAndUpdateRetryCount <~ failed.outlet
                                                                     successful    ~> unwrapSuccess
      // format: ON

      FlowShape(addContext.in, unwrapSuccess.out)
    }

    Flow.fromGraph(graph)
  }

  implicit val system = ActorSystem()

  val data       = Source(List(1, 2, 3))
  val nonWorking = data.flatMapMerge(3, req => Source.single(req).via(retryProcessSimple()))
  val working    = data.via(retryProcessSimple())

  working.runForeach(println)
  nonWorking.runForeach(println)
}
