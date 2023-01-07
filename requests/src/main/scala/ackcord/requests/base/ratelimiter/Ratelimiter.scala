package ackcord.requests.base.ratelimiter

import java.util.UUID

import scala.concurrent.duration.Duration

import ackcord.requests.base.FailedRequest.RequestDropped
import ackcord.requests.base.{RequestAnswer, RequestRoute}

/**
  * An object used to ratelimit requests to Discord to avoid being ratelimited
  * by Discord.
  */
trait Ratelimiter[F[_]] {

  /**
    * Ratelimit a request, waiting until it is safe to send. During this time,
    * the request might be dropped if it waited too long to be sent.
    * @param route
    *   The route of the request.
    * @param request
    *   An object to be returned when a request to the given route is safe to
    *   send. Normally the request object itself.
    * @param id
    *   An ID to track this request by.
    */
  def ratelimitRequest[Req](route: RequestRoute, request: Req, id: UUID): F[Either[RequestDropped, Req]]

  /**
    * Query how many requests remain before the given route is ratelimited. A
    * either is returned indicating if the route is ratelimited or not, and how
    * long until it switches state.
    *
    * @return
    *   <p>If a Right is returned, the route is currently not ratelimited, and
    *   the given number is how many numbers can be sent before it will be
    *   ratelimited.</p><p>If a left is returned, the route is currently
    *   ratelimited, and the duration indicates when it will no longer be
    *   so.</p>
    */
  def queryRemainingRequests(route: RequestRoute): F[Either[Duration, Int]]

  /** Report ratelimit information found in the given request answer. */
  def reportRatelimits[A](answer: RequestAnswer[A]): F[Unit]
}
