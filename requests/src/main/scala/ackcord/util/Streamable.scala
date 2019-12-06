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

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.{Foldable, Id}
import cats.data.OptionT

/**
  * Typeclass for converting some type F[A] to a Source[A, NotUsed]
  */
trait Streamable[F[_]] {
  def toSource[A](fa: F[A]): Source[A, NotUsed]

  def optionToSource[A](opt: OptionT[F, A]): Source[A, NotUsed] = toSource(opt.value).mapConcat(_.toList)
}
object Streamable {
  def apply[F[_]](implicit F: Streamable[F]): Streamable[F] = F

  implicit val idStreamable: Streamable[Id] = new Streamable[Id] {
    override def toSource[A](fa: Id[A]): Source[A, NotUsed]                 = Source.single(fa)
    override def optionToSource[A](opt: OptionT[Id, A]): Source[A, NotUsed] = Source(opt.value.toList)
  }

  implicit val futureStreamable: Streamable[Future] = new Streamable[Future] {
    override def toSource[A](fa: Future[A]): Source[A, NotUsed] = Source.future(fa)
  }

  implicit def futureFoldableStreamable[F[_]: Foldable]: Streamable[λ[A => Future[F[A]]]] =
    new Streamable[λ[A => Future[F[A]]]] {
      override def toSource[A](fa: Future[F[A]]): Source[A, NotUsed] = {
        import cats.syntax.all._
        Source.future(fa).mapConcat(_.toList)
      }
    }

  implicit val futureOptionTStreamable: Streamable[OptionT[Future, *]] = new Streamable[OptionT[Future, *]] {
    override def toSource[A](fa: OptionT[Future, A]): Source[A, NotUsed] = Source.future(fa.value).mapConcat(_.toList)
  }

  implicit val sourceStreamable: Streamable[Source[*, NotUsed]] = new Streamable[Source[?, NotUsed]] {
    override def toSource[A](fa: Source[A, NotUsed]): Source[A, NotUsed] = fa
  }
}
