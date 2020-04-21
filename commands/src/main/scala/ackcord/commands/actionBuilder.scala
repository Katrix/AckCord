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

import ackcord.requests.{Request, Requests}
import ackcord.util.Streamable
import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Merge, Partition, Sink}
import cats.data.OptionT
import cats.~>

/**
  * A mapping over action builders.
  * @tparam I The input message type
  * @tparam O The output message type
  */
trait ActionFunction[-I[_], +O[_], E] { self =>

  /**
    * A flow that represents this mapping.
    */
  def flow[A]: Flow[I[A], Either[Option[E], O[A]], NotUsed]

  /**
    * Chains first this function, and then another one.
    */
  def andThen[O2[_]](that: ActionFunction[O, O2, E]): ActionFunction[I, O2, E] =
    new ActionFunction[I, O2, E] {
      override def flow[A]: Flow[I[A], Either[Option[E], O2[A]], NotUsed] =
        ActionFunction.flowViaEither(self.flow[A], that.flow[A])(Keep.right)
    }
}
object ActionFunction {

  /**
    * Flow for short circuiting eithers.
    */
  def flowViaEither[I, M, O, E, Mat1, Mat2, Mat3](
      flow1: Flow[I, Either[E, M], Mat1],
      flow2: Flow[M, Either[E, O], Mat2]
  )(combine: (Mat1, Mat2) => Mat3): Flow[I, Either[E, O], Mat3] = {
    Flow.fromGraph(GraphDSL.create(flow1, flow2)(combine) { implicit b => (selfFlow, thatFlow) =>
      import GraphDSL.Implicits._

      val selfPartition =
        b.add(Partition[Either[E, M]](2, {
          case Left(_)  => 0
          case Right(_) => 1
        }))
      val selfErr = selfPartition.out(0).map(_.asInstanceOf[Either[E, O]])
      val selfOut = selfPartition.out(1).map(_.getOrElse(sys.error("impossible")))

      val thatPartition =
        b.add(Partition[Either[E, O]](2, {
          case Left(_)  => 0
          case Right(_) => 1
        }))
      val thatErr = thatPartition.out(0)
      val thatOut = thatPartition.out(1)

      val resMerge = b.add(Merge[Either[E, O]](3))

      // format: OFF
      selfFlow ~> selfPartition
      selfOut       ~> thatFlow ~> thatPartition
      selfErr                                    ~> resMerge
      thatOut       ~> resMerge
      thatErr       ~> resMerge
      // format: ON

      FlowShape(
        selfFlow.in,
        resMerge.out
      )
    })
  }
}

/**
  * An [[ActionFunction]] that can't fail, but might return a different
  * message type.
  * @tparam I The input message type
  * @tparam O The output message type
  */
trait ActionTransformer[-I[_], +O[_], E] extends ActionFunction[I, O, E] { self =>

  /**
    * The flow representing this mapping without the eithers.
    */
  def flowMapper[A]: Flow[I[A], O[A], NotUsed]

  override def flow[A]: Flow[I[A], Either[Option[E], O[A]], NotUsed] = flowMapper.map(Right.apply)

  override def andThen[O2[_]](that: ActionFunction[O, O2, E]): ActionFunction[I, O2, E] =
    new ActionFunction[I, O2, E] {
      override def flow[A]: Flow[I[A], Either[Option[E], O2[A]], NotUsed] = flowMapper.via(that.flow)
    }

  /**
    * Chains first this transformer, and then another one. More efficient than
    * the base andThen function.
    */
  def andThen[O2[_]](that: ActionTransformer[O, O2, E]): ActionTransformer[I, O2, E] =
    new ActionTransformer[I, O2, E] {
      override def flowMapper[A]: Flow[I[A], O2[A], NotUsed] = self.flowMapper.via(that.flowMapper)
    }
}
object ActionTransformer {

  /**
    * Converts a [[cats.arrow.FunctionK]] to an [[ActionTransformer]].
    */
  def fromFuncK[I[_], O[_], E](f: I ~> O): ActionTransformer[I, O, E] = new ActionTransformer[I, O, E] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] = Flow[I[A]].map(f(_))
  }
}

/**
  * An [[ActionFunction]] from an input to an output. Used for
  * creating actions.
  * @tparam I The input type of this builder.
  * @tparam O The action message type used by the command.
  * @tparam A The argument type of this command builder.
  */
trait ActionBuilder[-I[_], +O[_], E, A] extends ActionFunction[I, O, E] { self =>
  type Action[B, Mat]

  /**
    * A request helper that belongs to this builder.
    */
  def requests: Requests

  /**
    * Creates an action from a sink.
    * @param sinkBlock The sink that will process this action.
    * @tparam Mat The materialized result of running this action.
    */
  def toSink[Mat](sinkBlock: Sink[O[A], Mat]): Action[A, Mat]

  /**
    * Creates an action that results in some streamable type G
    * @param block The execution of the action.
    * @tparam G The streamable result type.
    */
  def streamed[G[_]](block: O[A] => G[Unit])(implicit streamable: Streamable[G]): Action[A, NotUsed] =
    toSink(Flow[O[A]].flatMapConcat(m => streamable.toSource(block(m))).to(Sink.ignore))

  /**
    * Creates an action that might do a single request, wrapped in an effect type G
    * @param block The execution of the action.
    * @tparam G The streamable result type.
    */
  def streamedOptRequest[G[_]](
      block: O[A] => OptionT[G, Request[Any]]
  )(implicit streamable: Streamable[G]): Action[A, NotUsed] =
    toSink(Flow[O[A]].flatMapConcat(m => streamable.optionToSource(block(m))).to(requests.sinkIgnore))

  /**
    * Creates an action that results in an async result
    * @param block The execution of the action.
    */
  def async(block: O[A] => Future[Unit]): Action[A, NotUsed] =
    toSink(Flow[O[A]].mapAsyncUnordered(requests.parallelism)(block).to(Sink.ignore))

  /**
    * Creates an action that results in an partial async result
    * @param block The execution of the action.
    */
  def asyncOpt(block: O[A] => OptionT[Future, Unit]): Action[A, NotUsed] =
    toSink(Flow[O[A]].mapAsyncUnordered(requests.parallelism)(block(_).value).to(Sink.ignore))

  /**
    * Creates an async action that might do a single request
    * @param block The execution of the action.
    * @tparam G The streamable result type.
    */
  def asyncOptRequest[G[_]](
      block: O[A] => OptionT[Future, Request[Any]]
  ): Action[A, NotUsed] =
    toSink(
      Flow[O[A]].mapAsyncUnordered(requests.parallelism)(block(_).value).mapConcat(_.toList).to(requests.sinkIgnore)
    )

  /**
    * Creates an action that will do a single request
    * @param block The execution of the action.
    */
  def withRequest(block: O[A] => Request[Any]): Action[A, NotUsed] =
    toSink(Flow[O[A]].map(block).to(requests.sinkIgnore))

  /**
    * Creates an action that might do a single request
    * @param block The execution of the action.
    */
  def withRequestOpt(block: O[A] => Option[Request[Any]]): Action[A, NotUsed] =
    toSink(Flow[O[A]].mapConcat(block(_).toList).to(requests.sinkIgnore))

  /**
    * Creates an action that might execute unknown side effects.
    * @param block The execution of the action.
    */
  def withSideEffects(block: O[A] => Unit): Action[A, NotUsed] =
    toSink(Sink.foreach(block).mapMaterializedValue(_ => NotUsed))
}
