package ackcord.requests.base

import ackcord.requests.base.Requests.RequestWithAnswer
import sttp.capabilities.Effect
import sttp.client3.SttpBackend
import sttp.monad.MonadError

class Requests[F[_], +R](
    backend: SttpBackend[F, R with Effect[F]],
    settings: RequestSettings[F],
    alsoProcessRequests: RequestWithAnswer[_] => F[Unit]
) {
  implicit private val F: MonadError[F] = backend.responseMonad

  def addExtraProcessing[Response, R1 >: R](
      request: AckCordRequest[Response, R1],
      res: F[RequestAnswer[Response]]
  ): F[RequestAnswer[Response]] =
    F.flatTap(res)(answer => alsoProcessRequests(RequestWithAnswer(request, answer)))

  def runRequestWithoutRatelimits[Response, R1 >: R](
      request: AckCordRequest[Response, R1]
  ): F[RequestAnswer[Response]] =
    addExtraProcessing(request, RequestHandling.runRequestWithoutRatelimits(request, backend, settings))

  def runRequest[Response, R1 >: R](request: AckCordRequest[Response, R1]): F[RequestAnswer[Response]] =
    addExtraProcessing(request, RequestHandling.runRequest(request, backend, settings))

  def runRequestWithRetry[Response, R1 >: R](request: AckCordRequest[Response, R1]): F[RequestAnswer[Response]] =
    addExtraProcessing(
      request,
      RequestHandling.runRequestWithRetry(RequestHandling.runRequest(request, backend, settings), settings)(
        backend.responseMonad
      )
    )
}
object Requests {
  def ofNoProcessinng[F[_], R](
      backend: SttpBackend[F, R with Effect[F]],
      settings: RequestSettings[F]
  ): Requests[F, R] =
    new Requests(backend, settings, _ => backend.responseMonad.unit(()))

  case class RequestWithAnswer[Data](request: AckCordRequest[Data, _], requestAnswer: RequestAnswer[Data])
}
