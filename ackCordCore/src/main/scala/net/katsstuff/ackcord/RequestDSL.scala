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

import scala.language.implicitConversions

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import cats.Monad
import net.katsstuff.ackcord.http.requests.{Request, RequestAnswer, RequestResponse}

/**
  * Base trait for a RequestDSL object. An RequestDSL object is a program
  * that will evaluate to a value gotten by running requests, while hiding the
  * streaming implementation.
  */
sealed trait RequestDSL[+A] {

  def map[B](f: A => B):                    RequestDSL[B]
  def filter(f: A => Boolean):              RequestDSL[A]
  def flatMap[B](f: A => RequestDSL[B]):    RequestDSL[B]
  def collect[B](f: PartialFunction[A, B]): RequestDSL[B]

  /**
    * Run this request using the given flow.
    */
  def toSource[B, Ctx](flow: Flow[Request[B, Ctx], RequestAnswer[B, Ctx], NotUsed]): Source[A, NotUsed]
}
object RequestDSL {

  implicit def wrap[A](request: Request[A, _]): RequestDSL[A] = SingleRequest(request)

  /**
    * Lift a pure value into the dsl.
    */
  def pure[A](a: A): RequestDSL[A] = Pure(a)

  /**
    * Alias for [[pure]].
    */
  def lift[A](a: A): RequestDSL[A] = pure(a)

  /**
    * Lift a an optional pure value into the dsl.
    */
  def maybePure[A](opt: Option[A]): RequestDSL[A] = opt.fold[RequestDSL[A]](NoRequest)(Pure.apply)

  /**
    * Alias for [[maybePure]]
    */
  def liftOption[A](opt: Option[A]): RequestDSL[A] = maybePure(opt)

  /**
    * Lift a an optional request into the dsl.
    */
  def maybeRequest[A](opt: Option[Request[A, _]]): RequestDSL[A] =
    opt.fold[RequestDSL[A]](NoRequest)(SingleRequest.apply)

  /**
    * Alias for [[maybeRequest]]
    */
  def liftOptionalRequest[A](opt: Option[Request[A, _]]): RequestDSL[A] = maybeRequest(opt)

  /**
    * Run a RequestDSL using the specified flow.
    */
  def apply[Data, Ctx, B](flow: Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed])(
      dsl: RequestDSL[B]
  ): Source[B, NotUsed] = dsl.toSource(flow)

  implicit val monad: Monad[RequestDSL] = new Monad[RequestDSL] {
    override def map[A, B](fa: RequestDSL[A])(f: A => B):                 RequestDSL[B] = fa.map(f)
    override def flatMap[A, B](fa: RequestDSL[A])(f: A => RequestDSL[B]): RequestDSL[B] = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => RequestDSL[Either[A, B]]): RequestDSL[B] = {
      f(a).flatMap {
        case Left(_)  => tailRecM(a)(f) //Think this is the best we can do as flatMap is a separate object.
        case Right(v) => Pure(v)
      }
    }

    override def pure[A](x: A) = Pure(x)
  }

  private case class Pure[+A](a: A) extends RequestDSL[A] {
    override def map[B](f: A => B):                    RequestDSL[B] = Pure(f(a))
    override def filter(f: A => Boolean):              RequestDSL[A] = if (f(a)) this else NoRequest
    override def flatMap[B](f: A => RequestDSL[B]):    RequestDSL[B] = f(a)
    override def collect[B](f: PartialFunction[A, B]): RequestDSL[B] = maybePure(f.lift(a))

    override def toSource[B, Ctx](flow: Flow[Request[B, Ctx], RequestAnswer[B, Ctx], NotUsed]): Source[A, NotUsed] =
      Source.single(a)
  }

  private case object NoRequest extends RequestDSL[Nothing] {
    override def map[B](f: Nothing => B):                    RequestDSL[B]       = this
    override def filter(f: Nothing => Boolean):              RequestDSL[Nothing] = this
    override def flatMap[B](f: Nothing => RequestDSL[B]):    RequestDSL[B]       = this
    override def collect[B](f: PartialFunction[Nothing, B]): RequestDSL[B]       = this

    override def toSource[B, Ctx](
        flow: Flow[Request[B, Ctx], RequestAnswer[B, Ctx], NotUsed]
    ): Source[Nothing, NotUsed] =
      Source.empty[Nothing]
  }

  private case class SingleRequest[A](request: Request[A, _]) extends RequestDSL[A] {
    override def map[B](f: A => B)                 = SingleRequest(request.map(f))
    override def filter(f: A => Boolean)           = SingleRequest(request.filter(f))
    override def flatMap[B](f: A => RequestDSL[B]) = AndThenRequestDSL(this, f)
    override def collect[B](f: PartialFunction[A, B]): RequestDSL[B] = SingleRequest(request.collect(f))

    override def toSource[B, Ctx](flow: Flow[Request[B, Ctx], RequestAnswer[B, Ctx], NotUsed]): Source[A, NotUsed] = {
      val casted = flow.asInstanceOf[Flow[Request[A, _], RequestAnswer[A, _], NotUsed]]

      Source.single(request).via(casted).collect {
        case res: RequestResponse[A, _] => res.data
      }
    }
  }

  private case class AndThenRequestDSL[A, +B](request: RequestDSL[A], f: A => RequestDSL[B]) extends RequestDSL[B] {
    override def map[C](g: B => C):                 RequestDSL[C] = AndThenRequestDSL(request, f.andThen(_.map(g)))
    override def filter(g: B => Boolean):           RequestDSL[B] = AndThenRequestDSL(request, f.andThen(_.filter(g)))
    override def flatMap[C](g: B => RequestDSL[C]): RequestDSL[C] = AndThenRequestDSL(this, g)
    override def collect[C](g: PartialFunction[B, C]): RequestDSL[C] =
      AndThenRequestDSL(request, f.andThen(_.collect(g)))

    override def toSource[C, Ctx](flow: Flow[Request[C, Ctx], RequestAnswer[C, Ctx], NotUsed]): Source[B, NotUsed] =
      request.toSource(flow).flatMapConcat(s => f(s).toSource(flow))
  }
}
