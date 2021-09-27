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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import cats.data.OptionT
import cats.instances.future._
import cats.instances.option._
import cats.syntax.all._
import cats.{Eval, MonadError, StackSafeMonad}

/**
  * A future that might be missing a value. A nice wrapper around
  * `Future[Option[A]]`
  * @param value
  *   The wrapped future
  */
class OptFuture[+A](val value: Future[Option[A]]) {

  def map[B](f: A => B)(implicit ec: ExecutionContext): OptFuture[B] =
    OptFuture(value.map(_.map(f)))

  def flatMap[B](f: A => OptFuture[B])(implicit
      ec: ExecutionContext
  ): OptFuture[B] =
    OptFuture(value.flatMap(_.flatTraverse(f(_).value)))

  def flatMapF[B](f: A => Future[Option[B]])(implicit
      ec: ExecutionContext
  ): OptFuture[B] =
    OptFuture(value.flatMap(_.flatTraverse(f(_))))

  def semiflatMap[B](f: A => Future[B])(implicit
      ec: ExecutionContext
  ): OptFuture[B] =
    OptFuture(value.flatMap(_.traverse(f)))

  def subflatMap[B](f: A => Option[B])(implicit
      ec: ExecutionContext
  ): OptFuture[B] =
    OptFuture(value.map(_.flatMap(f)))

  def filter(f: A => Boolean)(implicit ec: ExecutionContext): OptFuture[A] =
    OptFuture(value.map(_.filter(f)))

  final def withFilter(p: A => Boolean)(implicit
      ec: ExecutionContext
  ): OptFuture[A] =
    filter(p)(ec)

  def collect[B](f: PartialFunction[A, B])(implicit
      ec: ExecutionContext
  ): OptFuture[B] =
    OptFuture(value.map(_.collect(f)))

  def foreach(f: A => Unit)(implicit ec: ExecutionContext): Unit =
    value.foreach(_.foreach(f))

  def zip[B](
      other: OptFuture[B]
  )(implicit ec: ExecutionContext): OptFuture[(A, B)] =
    OptFuture(
      value.zip(other.value).map {
        case (Some(a), Some(b)) => Some((a, b))
        case _                  => None
      }
    )
}
object OptFuture {

  def apply[A](value: Future[Option[A]]): OptFuture[A] = new OptFuture(value)

  def fromFuture[A](value: Future[A])(implicit
      ec: ExecutionContext
  ): OptFuture[A] = OptFuture(value.map(Some(_)))

  def fromOption[A](value: Option[A]): OptFuture[A] = OptFuture(
    Future.successful(value)
  )

  def pure[A](value: A): OptFuture[A] = OptFuture(
    Future.successful(Some(value))
  )

  val unit: OptFuture[Unit] = pure(())

  implicit class OptFutureOps[A](private val optFuture: OptFuture[A])
      extends AnyVal {
    def toOptionT: OptionT[Future, A] = OptionT(optFuture.value)
  }

  implicit def catsInstanceForOptFuture(implicit
      ec: ExecutionContext
  ): MonadError[OptFuture, Throwable] =
    new MonadError[OptFuture, Throwable] with StackSafeMonad[OptFuture] {
      override def flatMap[A, B](fa: OptFuture[A])(
          f: A => OptFuture[B]
      ): OptFuture[B] = fa.flatMap(f)

      override def raiseError[A](e: Throwable): OptFuture[A] =
        OptFuture.fromFuture(Future.failed(e))

      override def handleErrorWith[A](fa: OptFuture[A])(
          f: Throwable => OptFuture[A]
      ): OptFuture[A] = OptFuture(
        fa.value.recoverWith { case e => f(e).value }
      )

      override def handleError[A](fa: OptFuture[A])(
          f: Throwable => A
      ): OptFuture[A] = OptFuture(
        fa.value.recover { case e => Some(f(e)) }
      )

      override def attempt[A](
          fa: OptFuture[A]
      ): OptFuture[Either[Throwable, A]] = OptFuture(
        fa.value.transformWith {
          case Success(value)     => Future.successful(value.map(Right(_)))
          case Failure(exception) => Future.successful(Some(Left(exception)))
        }
      )

      override def recover[A](fa: OptFuture[A])(
          pf: PartialFunction[Throwable, A]
      ): OptFuture[A] =
        OptFuture(fa.value.recover(pf.andThen(Some(_))))

      override def recoverWith[A](fa: OptFuture[A])(
          pf: PartialFunction[Throwable, OptFuture[A]]
      ): OptFuture[A] =
        OptFuture(fa.value.recoverWith(pf.andThen(_.value)))

      override def redeemWith[A, B](
          fa: OptFuture[A]
      )(
          recover: Throwable => OptFuture[B],
          bind: A => OptFuture[B]
      ): OptFuture[B] = OptFuture(
        fa.value.transformWith {
          case Success(Some(a))   => bind(a).value
          case Success(None)      => Future.successful(None)
          case Failure(exception) => recover(exception).value
        }
      )

      override def catchNonFatal[A](a: => A)(implicit
          ev: Throwable <:< Throwable
      ): OptFuture[A] =
        OptFuture.fromFuture(Future(a))

      override def catchNonFatalEval[A](a: Eval[A])(implicit
          ev: Throwable <:< Throwable
      ): OptFuture[A] =
        OptFuture.fromFuture(Future(a.value))

      override def pure[A](x: A): OptFuture[A] = OptFuture.pure(x)
    }
}
