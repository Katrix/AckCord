package ackcord.requests.base.ratelimiter

import ackcord.requests.base.FailedRequest.RequestDropped
import ackcord.requests.base.{RequestAnswer, RequestRoute}

import scala.concurrent.duration.Duration

trait Ratelimiter[F[_]] {

  def ratelimitRequest[Req](route: RequestRoute, request: Req): F[Either[RequestDropped, Req]]

  def queryRemainingRequests(route: RequestRoute): F[Either[Duration, Int]]

  def reportRatelimits[A](answer: RequestAnswer[A]): F[Unit]
}
