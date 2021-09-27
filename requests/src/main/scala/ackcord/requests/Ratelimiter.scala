package ackcord.requests

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, TimeoutException}

import akka.NotUsed
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, RecipientRef}
import akka.stream.scaladsl.{Flow, FlowWithContext, Sink}
import akka.util.Timeout

trait Ratelimiter {

  def ratelimitRequests[A, Ctx]: FlowWithContext[Request[A], Ctx, Either[
    RequestDropped,
    Request[A]
  ], Ctx, NotUsed]

  def queryRemainingRequests(route: RequestRoute): Future[Either[Duration, Int]]

  def reportRatelimits[A]: Sink[RequestAnswer[A], NotUsed]
}
object Ratelimiter {

  def ofActor(
      ratelimitActor: ActorRef[RatelimiterActor.Command],
      parallelism: Int = 4
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): Ratelimiter =
    new ActorRatelimiter(
      ratelimitActor,
      parallelism
    )

  def ofRecipient(
      ratelimitRecipient: RecipientRef[RatelimiterActor.Command],
      parallelism: Int = 4
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): Ratelimiter =
    new RecipientRatelimiter(
      ratelimitRecipient,
      parallelism
    )

  class RecipientRatelimiter(
      ref: RecipientRef[RatelimiterActor.Command],
      parallelism: Int
  )(implicit
      system: ActorSystem[Nothing],
      timeout: Timeout
  ) extends Ratelimiter {
    protected def watchFlow[A]: Flow[A, A, NotUsed] =
      Flow[A] //Not possible to watch

    override def ratelimitRequests[A, Ctx]: FlowWithContext[Request[
      A
    ], Ctx, Either[RequestDropped, Request[A]], Ctx, NotUsed] =
      FlowWithContext.fromTuples(
        watchFlow[(Request[A], Ctx)]
          .mapAsyncUnordered(parallelism) { case (request, ctx) =>
            //We don't use ask here to get be able to create a RequestDropped instance
            import system.executionContext
            val future = ref.ask[RatelimiterActor.Response[(Request[A], Ctx)]](
              RatelimiterActor.WantToPass(
                request.route,
                request.identifier,
                _,
                (request, ctx)
              )
            )

            future
              .flatMap {
                case RatelimiterActor.CanPass(a) =>
                  Future.successful((Right(a._1), a._2))
                case RatelimiterActor.FailedRequest(e) => Future.failed(e)
              }
              .recover { case _: TimeoutException =>
                Left(RequestDropped(request.route, request.identifier)) -> ctx
              }
          }
      )

    override def queryRemainingRequests(
        route: RequestRoute
    ): Future[Either[Duration, Int]] =
      ref.ask(RatelimiterActor.QueryRatelimits(route, _))

    override def reportRatelimits[A]: Sink[RequestAnswer[A], NotUsed] =
      Sink
        .foreach[RequestAnswer[A]](answer =>
          ref ! RatelimiterActor.UpdateRatelimits(
            answer.route,
            answer.ratelimitInfo,
            answer match {
              case ratelimited: RequestRatelimited => ratelimited.global
              case _                               => false
            },
            answer.identifier
          )
        )
        .async
        .mapMaterializedValue(_ => NotUsed)
  }

  class ActorRatelimiter(
      ref: ActorRef[RatelimiterActor.Command],
      parallelism: Int
  )(implicit
      system: ActorSystem[Nothing],
      timeout: Timeout
  ) extends RecipientRatelimiter(ref, parallelism) {

    override protected def watchFlow[A]: Flow[A, A, NotUsed] =
      Flow[A].watch(ref.toClassic)
  }
}
