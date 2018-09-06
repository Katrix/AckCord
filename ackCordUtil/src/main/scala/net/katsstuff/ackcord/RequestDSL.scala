/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord

import scala.language.{higherKinds, implicitConversions}

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import cats.Monad
import cats.data.OptionT
import net.katsstuff.ackcord.http.requests.{Request, RequestAnswer, RequestResponse}
import net.katsstuff.ackcord.util.Streamable

/**
  * Base trait for a RequestDSL object. An RequestDSL object is a program
  * that will evaluate to a value gotten by running requests, while hiding the
  * streaming implementation.
  */
@deprecated("Use RequestRunner instead", since = "0.11")
sealed trait RequestDSL[+A] {

  def map[B](f: A => B): RequestDSL[B]
  def filter(f: A => Boolean): RequestDSL[A]
  def flatMap[B](f: A => RequestDSL[B]): RequestDSL[B]
  def collect[B](f: PartialFunction[A, B]): RequestDSL[B]

  def flatten[B](implicit ev: A <:< RequestDSL[B]): RequestDSL[B] = flatMap(ev)

  /**
    * Run this request using the given flow.
    */
  def toSource[B, Ctx](flow: Flow[Request[B, Ctx], RequestAnswer[B, Ctx], NotUsed]): Source[A, NotUsed]
}
@deprecated("Use RequestRunner instead", since = "0.11")
object RequestDSL {

  implicit def wrap[A](request: Request[A, _]): RequestDSL[A] = SingleRequest(request)

  /**
    * Lift a pure value into the dsl.
    */
  def pure[A](a: A): RequestDSL[A] = SourceRequest(Source.single(a))

  /**
    * Lift a an optional pure value into the dsl.
    */
  def optionPure[A](opt: Option[A]): RequestDSL[A] = opt.fold[RequestDSL[A]](SourceRequest(Source.empty))(pure)

  /**
    * Lift a an optional request into the dsl.
    */
  def optionRequest[A](opt: Option[Request[A, _]]): RequestDSL[A] =
    opt.fold[RequestDSL[A]](SourceRequest(Source.empty))(SingleRequest.apply)

  /**
    * Lifts a [[akka.stream.scaladsl.Source]] into the dsl.
    */
  def fromSource[A](source: Source[A, NotUsed]): RequestDSL[A] = SourceRequest(source)

  /**
    * Converts an F into a [[akka.stream.scaladsl.Source]] ad lifts it into the dsl.
    */
  def liftF[F[_]: Streamable, A](fa: F[A]): RequestDSL[A] = fromSource(Streamable[F].toSource(fa))

  /**
    * Lifts an [[cats.data.OptionT]] into a [[Source]] and lifts it into the dsl.
    */
  def liftOptionT[F[_]: Streamable, A](opt: OptionT[F, A]): RequestDSL[A] =
    fromSource(Streamable[F].optionToSource(opt))

  /**
    * Lifts a future request into the dsl.
    */
  def liftRequest[F[_]: Streamable, A](fRequest: F[RequestDSL[A]]): RequestDSL[A] =
    liftF(fRequest).flatten

  /**
    * Run a RequestDSL using the specified flow.
    */
  def apply[Data, Ctx, B](flow: Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed])(
      dsl: RequestDSL[B]
  ): Source[B, NotUsed] = dsl.toSource(flow)

  implicit val monad: Monad[RequestDSL] = new Monad[RequestDSL] {
    override def map[A, B](fa: RequestDSL[A])(f: A => B): RequestDSL[B] = fa.map(f)

    override def flatMap[A, B](fa: RequestDSL[A])(f: A => RequestDSL[B]): RequestDSL[B] = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => RequestDSL[Either[A, B]]): RequestDSL[B] =
      f(a).flatMap {
        case Left(_)  => tailRecM(a)(f) //Think this is the best we can do as flatMap is a separate object.
        case Right(v) => RequestDSL.pure(v)
      }

    override def pure[A](x: A): RequestDSL[A] = RequestDSL.pure(x)
  }

  private case class SingleRequest[A](request: Request[A, _]) extends RequestDSL[A] {
    override def map[B](f: A => B)                                   = SingleRequest(request.map(f))
    override def filter(f: A => Boolean)                             = SingleRequest(request.filter(f))
    override def flatMap[B](f: A => RequestDSL[B])                   = AndThenRequestDSL(this, f)
    override def collect[B](f: PartialFunction[A, B]): RequestDSL[B] = SingleRequest(request.collect(f))

    override def toSource[B, Ctx](flow: Flow[Request[B, Ctx], RequestAnswer[B, Ctx], NotUsed]): Source[A, NotUsed] = {
      val casted = flow.asInstanceOf[Flow[Request[A, _], RequestAnswer[A, _], NotUsed]]

      Source.single(request).via(casted).collect {
        case res: RequestResponse[A, _] => res.data
      }
    }
  }

  private case class AndThenRequestDSL[A, +B](request: RequestDSL[A], f: A => RequestDSL[B]) extends RequestDSL[B] {
    override def map[C](g: B => C): RequestDSL[C]                 = AndThenRequestDSL(request, f.andThen(_.map(g)))
    override def filter(g: B => Boolean): RequestDSL[B]           = AndThenRequestDSL(request, f.andThen(_.filter(g)))
    override def flatMap[C](g: B => RequestDSL[C]): RequestDSL[C] = AndThenRequestDSL(this, g)
    override def collect[C](g: PartialFunction[B, C]): RequestDSL[C] =
      AndThenRequestDSL(request, f.andThen(_.collect(g)))

    override def toSource[C, Ctx](flow: Flow[Request[C, Ctx], RequestAnswer[C, Ctx], NotUsed]): Source[B, NotUsed] =
      request.toSource(flow).flatMapConcat(s => f(s).toSource(flow))
  }

  private case class SourceRequest[A](source: Source[A, NotUsed]) extends RequestDSL[A] {
    override def map[B](f: A => B): RequestDSL[B]                    = SourceRequest(source.map(f))
    override def filter(f: A => Boolean): RequestDSL[A]              = SourceRequest(source.filter(f))
    override def flatMap[B](f: A => RequestDSL[B]): RequestDSL[B]    = AndThenRequestDSL(this, f)
    override def collect[B](f: PartialFunction[A, B]): RequestDSL[B] = SourceRequest(source.collect(f))

    override def toSource[B, Ctx](flow: Flow[Request[B, Ctx], RequestAnswer[B, Ctx], NotUsed]): Source[A, NotUsed] =
      source
  }
}
