package ackcord.requests.base

import scala.annotation.unused

import ackcord.requests.base.Requests.RequestWithAnswer
import sttp.capabilities.Effect
import sttp.client3.SttpBackend
import sttp.monad.MonadError
import sttp.monad.syntax._

/**
  * An object used for making requests.
  * @param backend
  *   The Sttp backend to use for making requests.
  * @param settings
  *   Settings to use when making requests.
  * @param alsoProcessRequests
  *   Extra processing to do with all request answers.
  */
class Requests[F[_], +R](
    backend: SttpBackend[F, R],
    settings: RequestSettings[F],
    alsoProcessRequests: RequestWithAnswer[_] => F[Unit]
) {
  implicit val F: MonadError[F] = backend.responseMonad

  private def addExtraProcessing[Response, R1 >: R with Effect[F]](
      request: AckCordRequest[Response, R1],
      res: F[RequestAnswer[Response]]
  ): F[RequestAnswer[Response]] =
    F.flatTap(res)(answer => alsoProcessRequests(RequestWithAnswer(request, answer)))

  /**
    * Run a request without ratelimiting. In almost all cases, you should not be
    * using this function.
    */
  def runRequestWithoutRatelimits[Response, R1 >: R with Effect[F]](
      request: AckCordRequest[Response, R1]
  )(implicit @unused iKnowWhatImDoing: Requests.IWantToMakeRequestsWithoutRatelimits): F[RequestAnswer[Response]] =
    addExtraProcessing(request, RequestHandling.runRequestWithoutRatelimits[Response, R, R1, F](request, backend, settings))

  /**
    * Run a normal request, returning the [[RequestAnswer]]. If it fails, it
    * will not be retried.
    */
  def runRequestToAnswer[Response, R1 >: R with Effect[F]](
      request: AckCordRequest[Response, R1]
  ): F[RequestAnswer[Response]] =
    addExtraProcessing(request, RequestHandling.runRequest[Response, R, R1, F](request, backend, settings))

  /**
    * Run a normal request, returning the response type. If it fails, it will
    * not be retried.
    */
  def runRequest[Response, R1 >: R with Effect[F]](request: AckCordRequest[Response, R1]): F[Response] =
    runRequestToAnswer(request).flatMap {
      case RequestResponse(data, _, _, _) => F.unit(data)
      case request: FailedRequest         => F.error(request.asException)
    }

  /**
    * Run a request, returning the [[RequestAnswer]] while retrying if it fails.
    */
  def runRequestToAnswerWithRetry[Response, R1 >: R with Effect[F]](
      request: AckCordRequest[Response, R1]
  ): F[RequestAnswer[Response]] =
    addExtraProcessing(
      request,
      RequestHandling.runRequestWithRetry(RequestHandling.runRequest[Response, R, R1, F](request, backend, settings), settings)(
        backend.responseMonad
      )
    )

  /** Run a request, returning the response type while retrying if it fails. */
  def runRequestWithRetry[Response, R1 >: R with Effect[F]](
      request: AckCordRequest[Response, R1]
  ): F[Response] = runRequestToAnswerWithRetry(request).flatMap {
    case RequestResponse(data, _, _, _) => F.unit(data)
    case request: FailedRequest         => F.error(request.asException)
  }
}
object Requests {

  /** Make a [[Requests]] with no extra processing. */
  def ofNoProcessinng[F[_], R](
      backend: SttpBackend[F, R],
      settings: RequestSettings[F]
  ): Requests[F, R] =
    new Requests(backend, settings, _ => backend.responseMonad.unit(()))

  /** A request together with the answer to the request. */
  case class RequestWithAnswer[Data](request: AckCordRequest[Data, _], requestAnswer: RequestAnswer[Data])

  trait IWantToMakeRequestsWithoutRatelimits
}
