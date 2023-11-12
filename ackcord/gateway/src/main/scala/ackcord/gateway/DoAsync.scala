package ackcord.gateway

import cats.Functor
import cats.effect.std.Supervisor
import cats.syntax.all._

trait DoAsync[F[_]] {

  def async(program: F[Unit]): F[Unit]
}
object DoAsync {
  def doAsyncSupervisor[F[_]: Functor](supervisor: Supervisor[F]): DoAsync[F] =
    (program: F[Unit]) => supervisor.supervise(program).void
}
