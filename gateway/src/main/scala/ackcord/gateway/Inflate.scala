package ackcord.gateway

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

import scala.util.Using

import cats.Functor
import cats.effect.kernel.Resource
import sttp.monad.MonadError

trait Inflate[F[_]] {

  def newInflater(bufferSize: Int = 1024 * 8): Resource[F, Inflate.PureInflater[F]]
}
object Inflate {

  def apply[F[_]](implicit F: Inflate[F]): Inflate[F] = F

  trait PureInflater[F[_]] {
    def reset: F[Unit]

    def inflate(arr: Array[Byte]): F[Array[Byte]]

    def inflateToString(arr: Array[Byte], charset: String): F[String]
  }

  def newInflate[F[_]: Functor](implicit F: MonadError[F]): Inflate[F] = new Inflate[F] {
    override def newInflater(bufferSize: Int): Resource[F, PureInflater[F]] =
      Resource.make(F.blocking((new Inflater(), new Array[Byte](bufferSize))))(in => F.blocking(in._1.end())).map {
        case (inflater, buffer) =>
          new PureInflater[F] {
            override def reset: F[Unit] = F.blocking(inflater.reset())

            private def inflateImpl[A](arr: Array[Byte], finish: ByteArrayOutputStream => A): F[A] =
              F.blocking {
                Using(new ByteArrayOutputStream()) { os =>
                  inflater.setInput(arr)
                  while (!inflater.finished()) {
                    val size = inflater.inflate(buffer)
                    os.write(buffer, 0, size)
                  }

                  finish(os)
                }.get
              }

            override def inflate(arr: Array[Byte]): F[Array[Byte]] = inflateImpl(arr, _.toByteArray)

            override def inflateToString(arr: Array[Byte], charset: String): F[String] =
              inflateImpl(arr, _.toString(charset))
          }
      }
  }
}
