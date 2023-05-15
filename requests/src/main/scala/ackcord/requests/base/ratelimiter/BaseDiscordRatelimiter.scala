package ackcord.requests.base.ratelimiter

import java.util.UUID

import scala.concurrent.duration._

import ackcord.requests.base.ratelimiter.BaseDiscordRatelimiter.{PQueue, TokenBucket}
import ackcord.requests.base.{FailedRequest, RequestAnswer, RequestRoute}
import org.slf4j.LoggerFactory
import sttp.monad.MonadError
import sttp.monad.syntax._

abstract class BaseDiscordRatelimiter[F[_]](logRateLimitEvents: Boolean = false)(implicit monad: MonadError[F])
    extends Ratelimiter[F] {

  private val log = LoggerFactory.getLogger(this.getClass)

  /** The priority queue for global ratelimits. */
  protected def globalRatelimitQueue: PQueue[F]

  /** The priority queue for route ratelimits. */
  protected def routeRatelimitQueue(route: RequestRoute): F[PQueue[F]]

  /** The token bucket for global ratelimits. */
  protected def globalTokenBucket: TokenBucket[F]

  /** The token bucket for ratelimits for the given route. */
  protected def routeTokenBucket(route: RequestRoute): F[Option[TokenBucket[F]]]

  /**
    * The token bucket for ratelimits for the given route. Create it if it does
    * not yet exist.
    */
  protected def routeTokenBucketOrCreate(route: RequestRoute): F[TokenBucket[F]]

  /**
    * Acquire tokens from all the given buckets at the same time. If any of the
    * buckets are missing tokens, no tokens are taken from any bucket.
    * @param buckets
    *   The buckets to acquire tokens from. The buckets are checked from right
    *   to left. More general buckets should be placed on the left, while more
    *   specific ones on the right.
    * @return
    *   A number indicating the first bucket missing tokens. -1 If all buckets
    *   had tokens.
    */
  protected def acquireTokensFromBuckets(buckets: TokenBucket[F]*): F[Int]

  protected def getMonotonicTime: F[Long]

  protected def updateBucketLocation(route: RequestRoute, bucket: String): F[Unit]

  protected def globalRatelimitReset: F[Long]
  protected def routeRatelimitReset(route: RequestRoute): F[Long]

  protected def onGlobalRatelimit(resetAt: Long): F[Unit]

  protected def updateRouteRatelimit(route: RequestRoute, resetAt: Long, retryAt: Long): F[Unit]

  final override def ratelimitRequest[Req](
      route: RequestRoute,
      request: Req,
      id: UUID
  ): F[Either[FailedRequest.RequestDropped, Req]] = {
    val logging = if (logRateLimitEvents) {

      for {
        routeBucket <- routeTokenBucket(route)
        globalBucket = globalTokenBucket
        routeMaxSize          <- routeBucket.fold(monad.unit(-1))(_.maxSize)
        routeTokensRemaining  <- routeBucket.fold(monad.unit(-1))(_.tokensRemaining)
        routeRequestsWaiting  <- routeRatelimitQueue(route).flatMap(_.size)
        routeResetAt          <- routeRatelimitReset(route)
        globalMaxSize         <- globalBucket.maxSize
        globalTokensRemaining <- globalBucket.tokensRemaining
        globalRequestsWaiting <- globalRatelimitQueue.size
        globalResetAt         <- globalRatelimitReset
        _ <- monad.blocking(
          log.debug(
            s"""|
                |Got incoming request: ${route.uriWithMajor} $id
                |Route Limit: $routeMaxSize
                |Route remaining requests: $routeTokensRemaining
                |Route requests waiting: $routeRequestsWaiting
                |Route ratelimit reset: $routeResetAt
                |Global limit: $globalMaxSize
                |Global remaining requests: $globalTokensRemaining
                |Global requests waiting: $globalRequestsWaiting
                |Global ratelimit reset: $globalResetAt
                |Current time: ${System.currentTimeMillis()}
                |""".stripMargin
          )
        )
      } yield ()
    } else monad.unit(())

    logging.flatMap(_ => getMonotonicTime).flatMap(time => ratelimitRequestWithTime(route, request, id, time))
  }

  protected def ratelimitRequestWithTime[Req](
      route: RequestRoute,
      request: Req,
      id: UUID,
      time: Long
  ): F[Either[FailedRequest.RequestDropped, Req]] =
    for {
      routeBucket     <- routeTokenBucket(route)
      failedBucketIds <- acquireTokensFromBuckets(globalTokenBucket :: routeBucket.toList: _*)
      ratelimitQueue = failedBucketIds match {
        case -1 => None
        case 0  => Some(monad.unit(globalRatelimitQueue))
        case 1  => Some(routeRatelimitQueue(route))
      }

      res <- ratelimitQueue match {
        case None => monad.unit(Right(request))
        case Some(queueF) =>
          queueF
            .flatMap(_.enqueueAndWaitTilDequeue(id, time))
            .flatMap(_ => ratelimitRequestWithTime(route, request, id, time))
      }
    } yield res

  override def queryRemainingRequests(route: RequestRoute): F[Either[Duration, Int]] = {
    def remainingOrResetsAt(bucket: TokenBucket[F], resetAt: F[Long], currentTime: Long): F[Either[Duration, Int]] =
      bucket.tokensRemaining.flatMap(remaining =>
        if (remaining == 0) resetAt.map(at => Left((at - currentTime).millis)) else monad.unit(Right(remaining))
      )

    for {
      currentTime <- monad.blocking(System.currentTimeMillis())
      globalRes   <- remainingOrResetsAt(globalTokenBucket, globalRatelimitReset, currentTime)
      res <- globalRes match {
        case Right(_) =>
          routeTokenBucket(route).flatMap {
            case Some(bucket) => remainingOrResetsAt(bucket, routeRatelimitReset(route), currentTime)
            case None         => monad.unit(Right(-1): Either[Duration, Int])
          }
        case l => monad.unit(l)
      }
    } yield res
  }

  override def reportRatelimits[A](answer: RequestAnswer[A]): F[Unit] = {
    val route = answer.route

    val info @ RatelimitInfo(resetAt, retryAt, remainingRequestsAmount, bucketLimit, bucket, isGlobal) =
      answer.ratelimitInfo

    val logging = if (logRateLimitEvents) {
      monad.blocking(
        log.debug(
          s"""|
              |Updating ratelimits info: ${route.method.method} ${route.uriWithMajor} ${answer.identifier}
              |IsValid ${info.isValid}
              |Bucket: $bucket
              |BucketLimit: $bucketLimit
              |Global: $isGlobal
              |ResetAt: $resetAt
              |RetryAt: $retryAt
              |RemainingAmount: $remainingRequestsAmount
              |Current time: ${System.currentTimeMillis()}
              |""".stripMargin
        )
      )
    } else monad.unit(())

    if (info.isValid) {
      for {
        _           <- logging
        routeBucket <- routeTokenBucketOrCreate(route)
        _           <- routeBucket.setTokensMaxSize(tokens = remainingRequestsAmount, maxSize = bucketLimit)
        _           <- updateBucketLocation(route, bucket)
        _ <-
          if (isGlobal)
            onGlobalRatelimit(resetAt)
          else
            updateRouteRatelimit(route, resetAt, retryAt)
      } yield ()
    } else logging
  }
}
object BaseDiscordRatelimiter {
  trait PQueue[F[_]] {

    /** The amount of elements in the queue currently. */
    def size: F[Int]

    /**
      * Enqueue an id and time into the given queue, and wait for it to be
      * dequeued.
      */
    def enqueueAndWaitTilDequeue(id: UUID, time: Long): F[Unit]
  }

  trait TokenBucket[F[_]] {

    /** The current max size of the bucket. */
    def maxSize: F[Int]

    /** The amount of tokens currently remaining in a bucket. */
    def tokensRemaining: F[Int]

    /**
      * Set the current amount of tokens in the bucket, and the max size of the
      * bucket.
      */
    def setTokensMaxSize(tokens: Int, maxSize: Int): F[Unit]

    /**
      * Acquire a token from the bucket.
      * @return
      *   A boolean indicating if the operation succeeded.
      */
    def acquireToken: F[Boolean]

    /**
      * Place a token in a bucket.
      * @return
      *   A boolean indicating if the bucket was not at the max size.
      */
    def giveToken: F[Boolean]
  }
}
