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

import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import cats.{Alternative, Applicative, Foldable, Monad}
import net.katsstuff.ackcord.http.requests.{Request, RequestHelper, RequestResponse}
import net.katsstuff.ackcord.util.Streamable

trait RequestDSL[F[_]] {
  def wrapRequest[A](request: Request[A, _]): F[A]

  def fromSource[A](source: Source[A, NotUsed]): F[A]

  //From here on it's all convenience methods

  def pure[A](a: A)(implicit F: Applicative[F]): F[A] = F.pure(a)

  def optionPure[A](opt: Option[A])(implicit F: Alternative[F]): F[A] = opt.fold(F.empty[A])(F.pure)

  def optionRequest[A](opt: Option[Request[A, _]])(implicit F: Alternative[F]): F[A] = opt.fold(F.empty[A])(wrapRequest)

  def liftStreamable[G[_], A](ga: G[A])(implicit streamable: Streamable[G]): F[A] = fromSource(streamable.toSource(ga))

  def liftFoldable[G[_], A](ga: G[A])(implicit F: Alternative[F], G: Foldable[G]): F[A] =
    G.foldLeft(ga, F.empty[A])((acc, a) => F.combineK(acc, F.pure(a)))

  def wrapFRequest[G[_], A](request: G[Request[A, _]])(implicit F: Alternative[F], FM: Monad[F], G: Foldable[G]): F[A] =
    FM.flatMap(liftFoldable(request))(wrapRequest)
}

object RequestDSL {
  def apply[F[_]](implicit DSL: RequestDSL[F]): RequestDSL[F] = DSL

  implicit def sourceRequestDsl(implicit requests: RequestHelper): RequestDSL[Source[?, Any]] =
    new RequestDSL[Source[?, Any]] {
      implicit override def wrapRequest[A](request: Request[A, _]): Source[A, _] = requests.single(request).collect {
        case res: RequestResponse[A, _] => res.data
      }

      override def fromSource[A](source: Source[A, NotUsed]): Source[A, _] = source
    }

  implicit def futureRequestDsl[F[_]](
      implicit requests: RequestHelper,
      F: Alternative[F]
  ): RequestDSL[λ[A => Future[F[A]]]] = new RequestDSL[λ[A => Future[F[A]]]] {
    import requests.mat
    import requests.mat.executionContext
    implicit override def wrapRequest[A](request: Request[A, _]): Future[F[A]] =
      requests
        .singleFuture(request)
        .collect { case res: RequestResponse[A, _] => res.data }
        .map(F.pure)

    override def fromSource[A](source: Source[A, NotUsed]): Future[F[A]] =
      source.runWith(Sink.fold(F.empty[A])((acc, a) => F.combineK(acc, F.pure(a))))
  }

  def futureHeadRequestDsl(implicit requests: RequestHelper): RequestDSL[Future] = new RequestDSL[Future] {
    import requests.mat
    import requests.mat.executionContext

    implicit override def wrapRequest[A](request: Request[A, _]): Future[A] =
      requests
        .singleFuture(request)
        .collect { case res: RequestResponse[A, _] => res.data }

    override def fromSource[A](source: Source[A, NotUsed]): Future[A] = source.runWith(Sink.head)
  }

  def futureLastRequestDsl(implicit requests: RequestHelper): RequestDSL[Future] = new RequestDSL[Future] {
    import requests.mat
    import requests.mat.executionContext

    implicit override def wrapRequest[A](request: Request[A, _]): Future[A] =
      requests
        .singleFuture(request)
        .collect { case res: RequestResponse[A, _] => res.data }

    override def fromSource[A](source: Source[A, NotUsed]): Future[A] = source.runWith(Sink.last)
  }
}
