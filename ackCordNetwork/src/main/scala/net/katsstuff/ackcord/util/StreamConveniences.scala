package net.katsstuff.ackcord.util

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Merge, Sink, Source}
import cats.{Alternative, Contravariant, Functor, MonadError}

object StreamConveniences {

  implicit val sourceInstance: MonadError[Source[?, Any], Throwable] with Alternative[Source[?, Any]] =
    new MonadError[Source[?, Any], Throwable] with Alternative[Source[?, Any]] {

      override def empty[A]: Source[A, Any] = Source.empty[A]

      override def pure[A](x: A): Source[A, Any] = Source.single(x)

      override def map[A, B](fa: Source[A, Any])(f: A => B): Source[B, Any] = fa.map(f)

      override def flatMap[A, B](fa: Source[A, Any])(f: A => Source[B, Any]): Source[B, Any] =
        fa.flatMapConcat[B, Any](f)

      override def product[A, B](fa: Source[A, Any], fb: Source[B, Any]): Source[(A, B), Any] = fa.zip(fb)

      override def combineK[A](x: Source[A, Any], y: Source[A, Any]): Source[A, Any] =
        Source.combine(x, y)(Merge.apply(_))

      override def tailRecM[A, B](a: A)(f: A => Source[Either[A, B], Any]): Source[B, Any] = ???

      override def raiseError[A](e: Throwable): Source[A, Any] = Source.failed(e)
      override def handleErrorWith[A](fa: Source[A, Any])(f: Throwable => Source[A, Any]): Source[A, Any] =
        fa.recoverWithRetries[A](5, {
          case e: Throwable => f(e).mapMaterializedValue(_ => NotUsed)
        })
    }

  implicit def flowInstance[In, Mat]: Functor[Flow[In, ?, Mat]] = new Functor[Flow[In, ?, Mat]] {
    override def map[A, B](fa: Flow[In, A, Mat])(f: A => B): Flow[In, B, Mat] = fa.map(f)
  }

  implicit def sinkInstance[Mat]: Contravariant[Sink[?, Mat]] =
    new Contravariant[Sink[?, Mat]] {
      override def contramap[A, B](fa: Sink[A, Mat])(f: B => A): Sink[B, Mat] = fa.contramap(f)
    }

  implicit class SourceForSyntax[A, M1](private val source: Source[A, M1]) extends AnyVal {
    def flatMap[B, M2](f: A => Source[B, M2]): Source[B, M1] = source.flatMapConcat(f)
  }
}
