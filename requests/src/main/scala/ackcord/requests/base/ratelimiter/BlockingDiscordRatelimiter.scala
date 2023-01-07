package ackcord.requests.base.ratelimiter

import java.util.UUID

import scala.concurrent.duration.Duration

import ackcord.requests.base.{FailedRequest, RequestAnswer, RequestRoute}
import cats.Id

class BlockingDiscordRatelimiter extends Ratelimiter[Id] {

  override def ratelimitRequest[Req](
      route: RequestRoute,
      request: Req,
      id: UUID
  ): Id[Either[FailedRequest.RequestDropped, Req]] = ???

  override def queryRemainingRequests(route: RequestRoute): Id[Either[Duration, Int]] = ???

  override def reportRatelimits[A](answer: RequestAnswer[A]): Id[Unit] = ???
}
