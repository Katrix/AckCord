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

package ackcord

import ackcord.requests.{Request, RequestHelper, RequestResponse}
import ackcord.util.StreamInstances.SourceRequest
import ackcord.util.Streamable
import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import cats.data.OptionT
import cats.{Alternative, Applicative, FlatMap, Foldable, Monad}

import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.{Failure, Success}

trait RequestRunner[F[_], G[_]] {

  def run[A](request: Request[A, NotUsed])(implicit c: CacheSnapshot[G]): F[A]

  def runMany[A](requests: immutable.Seq[Request[A, NotUsed]])(implicit c: CacheSnapshot[G]): F[A]

  def fromSource[A](source: Source[A, NotUsed]): F[A]

  def unit: F[Unit]

  //From here on it's all convenience methods

  def pure[A](a: A)(implicit F: Applicative[F]): F[A] = F.pure(a)

  def optionPure[A](opt: Option[A])(implicit F: Alternative[F]): F[A] = opt.fold(F.empty[A])(F.pure)

  def runOption[A](opt: Option[Request[A, NotUsed]])(implicit F: Alternative[F], c: CacheSnapshot[G]): F[A] =
    opt.fold(F.empty[A])(run[A])

  def liftStreamable[H[_], A](ga: H[A])(implicit streamable: Streamable[H]): F[A] = fromSource(streamable.toSource(ga))

  def liftOptionT[H[_], A](opt: OptionT[H, A])(implicit streamable: Streamable[H]): F[A] =
    fromSource(streamable.optionToSource(opt))

  def liftFoldable[H[_], A](ga: H[A])(implicit F: Alternative[F], G: Foldable[H]): F[A] =
    G.foldLeft(ga, F.empty[A])((acc, a) => F.combineK(acc, F.pure(a)))

  def runFoldable[H[_], A](
      request: H[Request[A, NotUsed]]
  )(implicit F: Alternative[F], FM: Monad[F], G: Foldable[H], c: CacheSnapshot[G]): F[A] =
    FM.flatMap(liftFoldable(request))(run[A])

  def runOptionT[H[_], A](
      opt: OptionT[H, Request[A, NotUsed]]
  )(implicit streamable: Streamable[H], F: FlatMap[F], c: CacheSnapshot[G]): F[A] =
    F.flatMap(fromSource(streamable.optionToSource[Request[A, NotUsed]](opt)))(run[A])

}
object RequestRunner {
  def apply[F[_], G[_]](implicit runner: RequestRunner[F, G]): RequestRunner[F, G] = runner

  implicit def sourceRequestRunner[G[_]](
      implicit requests: RequestHelper,
      G: Monad[G],
      streamable: Streamable[G]
  ): RequestRunner[SourceRequest, G] =
    new RequestRunner[SourceRequest, G] {
      override def run[A](request: Request[A, NotUsed])(implicit c: CacheSnapshot[G]): SourceRequest[A] =
        streamable.toSource(request.hasPermissions).flatMapConcat {
          case false => Source.failed(new RequestPermissionException(request))
          //case true  => requests.retry(request).map(_.data) //FIXME: Retry is broken
          case true =>
            requests.single(request).collect {
              case RequestResponse(data, _, _, _, _, _, _) => data
            }
        }

      override def runMany[A](requestSeq: immutable.Seq[Request[A, NotUsed]])(
          implicit c: CacheSnapshot[G]
      ): SourceRequest[A] = {
        import cats.instances.vector._
        import cats.syntax.all._

        val requestVec = requestSeq.toVector
        streamable.toSource(requestVec.forallM(_.hasPermissions)).flatMapConcat {
          case false =>
            streamable
              .toSource(requestVec.findM(_.hasPermissions.map(!_)))
              .flatMapConcat(request => Source.failed(new RequestPermissionException(request.get)))
          case true =>
            requests.many(requestSeq).collect {
              case RequestResponse(data, _, _, _, _, _, _) => data
            }
        }
      }

      override def fromSource[A](source: Source[A, NotUsed]): SourceRequest[A] = source

      override def unit: SourceRequest[Unit] = Source.single(())
    }

  implicit def futureRequestRunner[F[_], G[_]](
      implicit requests: RequestHelper,
      F: Alternative[F],
      G: Monad[G],
      streamable: Streamable[G]
  ): RequestRunner[λ[A => Future[F[A]]], G] = new RequestRunner[λ[A => Future[F[A]]], G] {
    import requests.mat
    import requests.mat.executionContext

    override def run[A](request: Request[A, NotUsed])(implicit c: CacheSnapshot[G]): Future[F[A]] =
      streamable.toSource(request.hasPermissions).runWith(Sink.head).flatMap {
        case false => Future.failed(new RequestPermissionException(request))
        case true =>
          requests
            .singleFuture(request)
            .flatMap(res => Future.fromTry(res.eitherData.fold(Failure.apply, Success.apply)))
            .map(F.pure)
      }

    override def runMany[A](requestSeq: immutable.Seq[Request[A, NotUsed]])(
        implicit c: CacheSnapshot[G]
    ): Future[F[A]] = {
      import cats.instances.vector._
      import cats.syntax.all._

      val requestVec = requestSeq.toVector
      streamable
        .toSource(requestVec.forallM(_.hasPermissions))
        .flatMapConcat {
          case false =>
            streamable
              .toSource(requestVec.findM(_.hasPermissions.map(!_)))
              .flatMapConcat(request => Source.failed(new RequestPermissionException(request.get)))
          case true =>
            requests.many(requestSeq).collect {
              case RequestResponse(data, _, _, _, _, _, _) => data
            }
        }
        .runFold(F.empty[A])((acc, a) => F.combineK(acc, F.pure(a)))
    }

    override def fromSource[A](source: Source[A, NotUsed]): Future[F[A]] =
      source.runWith(Sink.fold(F.empty[A])((acc, a) => F.combineK(acc, F.pure(a))))

    override def unit: Future[F[Unit]] = Future.successful(F.pure(()))
  }
}
