package ackcord.requests.base.ratelimiter

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration.Duration

import ackcord.requests.base.{FailedRequest, RequestAnswer, RequestRoute}

class FutureDiscordRatelimiter extends Ratelimiter[Future] {
  override def ratelimitRequest[Req](
      route: RequestRoute,
      request: Req,
      id: UUID
  ): Future[Either[FailedRequest.RequestDropped, Req]] = ???

  override def queryRemainingRequests(route: RequestRoute): Future[Either[Duration, Int]] = ???

  override def reportRatelimits[A](answer: RequestAnswer[A]): Future[Unit] = ???
}
