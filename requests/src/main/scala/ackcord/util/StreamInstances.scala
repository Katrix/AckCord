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
import akka.stream.scaladsl.{Flow, Merge, Sink, Source}
import cats.{Alternative, Contravariant, Functor, MonadError, StackSafeMonad}

object StreamInstances {

  type SourceRequest[A] = Source[A, NotUsed]

  implicit val sourceInstance: MonadError[SourceRequest, Throwable] with Alternative[SourceRequest] =
    new MonadError[SourceRequest, Throwable] with Alternative[SourceRequest] with StackSafeMonad[SourceRequest] {

      override def empty[A]: SourceRequest[A] = Source.empty[A]

      override def pure[A](x: A): SourceRequest[A] = Source.single(x)

      override def map[A, B](fa: SourceRequest[A])(f: A => B): SourceRequest[B] = fa.map(f)

      override def flatMap[A, B](fa: SourceRequest[A])(f: A => SourceRequest[B]): SourceRequest[B] =
        fa.flatMapConcat[B, NotUsed](f)

      override def product[A, B](fa: SourceRequest[A], fb: SourceRequest[B]): SourceRequest[(A, B)] = fa.zip(fb)

      override def combineK[A](x: SourceRequest[A], y: SourceRequest[A]): SourceRequest[A] =
        Source.combine(x, y)(Merge.apply(_))

      override def raiseError[A](e: Throwable): SourceRequest[A] = Source.failed(e)
      override def handleErrorWith[A](fa: SourceRequest[A])(f: Throwable => SourceRequest[A]): SourceRequest[A] =
        fa.recoverWithRetries[A](5, {
          case e: Throwable => f(e).mapMaterializedValue(_ => NotUsed)
        })
    }

  implicit def flowInstance[In, Mat]: Functor[Flow[In, ?, Mat]] = new Functor[Flow[In, ?, Mat]] {
    override def map[A, B](fa: Flow[In, A, Mat])(f: A => B): Flow[In, B, Mat] = fa.map(f)
  }

  implicit def sinkInstance[Mat]: Contravariant[Sink[?, Mat]] = new Contravariant[Sink[?, Mat]] {
    override def contramap[A, B](fa: Sink[A, Mat])(f: B => A): Sink[B, Mat] = fa.contramap(f)
  }

  //For syntax on Source can be brittle
  implicit class SourceFlatmap[A, M1](private val source: Source[A, M1]) extends AnyVal {
    def flatMap[B, M2](f: A => Source[B, M2]): Source[B, M1] = source.flatMapConcat(f)
  }
}
