package net.katsstuff.ackcord.util

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Merge, Sink, Source}
import cats.{Alternative, Contravariant, Functor, MonadError}

object StreamInstances {

  type SourceRequest[A] = Source[A, NotUsed]

  implicit val sourceInstance: MonadError[SourceRequest, Throwable] with Alternative[SourceRequest] =
    new MonadError[SourceRequest, Throwable] with Alternative[SourceRequest] {

      override def empty[A]: SourceRequest[A] = Source.empty[A]

      override def pure[A](x: A): SourceRequest[A] = Source.single(x)

      override def map[A, B](fa: SourceRequest[A])(f: A => B): SourceRequest[B] = fa.map(f)

      override def flatMap[A, B](fa: SourceRequest[A])(f: A => SourceRequest[B]): SourceRequest[B] =
        fa.flatMapConcat[B, NotUsed](f)

      override def product[A, B](fa: SourceRequest[A], fb: SourceRequest[B]): SourceRequest[(A, B)] = fa.zip(fb)

      override def combineK[A](x: SourceRequest[A], y: SourceRequest[A]): SourceRequest[A] =
        Source.combine(x, y)(Merge.apply(_))

      override def tailRecM[A, B](a: A)(f: A => SourceRequest[Either[A, B]]): SourceRequest[B] = ???

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
