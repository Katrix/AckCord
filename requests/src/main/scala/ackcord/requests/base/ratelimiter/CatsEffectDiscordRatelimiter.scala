package ackcord.requests.base.ratelimiter

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import scala.concurrent.duration.DurationLong

import ackcord.requests.base.RequestRoute
import ackcord.requests.base.ratelimiter.BaseDiscordRatelimiter._
import ackcord.requests.base.ratelimiter.CatsEffectDiscordRatelimiter._
import cats.effect.kernel._
import cats.effect.kernel.syntax.all._
import cats.effect.std.{AtomicCell, MapRef, Supervisor}
import cats.syntax.all._
import cats.{Monad, Order}
import sttp.monad.{MonadError => SttpMonadError}

//noinspection MutatorLikeMethodIsParameterless
class CatsEffectDiscordRatelimiter[F[_]: Async: SttpMonadError](logRateLimitEvents: Boolean = false)(
    protected val globalRatelimitQueue: CatsPQueue[F],
    routeRateLimitQueues: RouteMap[F, CatsPQueue[F]],
    protected val globalTokenBucket: CatsTokenBucket[F],
    routeTokenBuckets: RouteMap[F, CatsTokenBucket[F]],
    routeLimitsNoParams: MapRef[F, String, Option[Int]],
    uriToBucket: MapRef[F, String, Option[Bucket]],
    globalRatelimitResetAt: Ref[F, Long],
    routeRatelimitResetAt: RouteMap[F, Long],
    supervisor: Supervisor[F],
    globalResetFiber: Ref[F, Option[Fiber[F, Throwable, Unit]]],
    routeResetFibers: RouteMap[F, Fiber[F, Throwable, Unit]]
) extends BaseDiscordRatelimiter[F](logRateLimitEvents) {

  override protected def routeRatelimitQueue(route: RequestRoute): F[PQueue[F]] =
    routeRateLimitQueues.getOrMake(route, CatsPQueue[F]).widen

  override protected def routeTokenBucket(route: RequestRoute): F[Option[TokenBucket[F]]] =
    routeTokenBuckets
      .get(route)
      .widen

  override protected def routeTokenBucketOrCreate(route: RequestRoute): F[TokenBucket[F]] =
    routeTokenBuckets
      .getOrMake(
        route,
        routeLimitsNoParams(route.uriWithoutMajor).get
          .map(_.getOrElse(999))
          .flatMap(limit => CatsTokenBucket.manual[F].flatTap(_.setTokensMaxSize(limit, limit)))
      )
      .widen

  private def acquireTokenLockstep(bucket: CatsTokenBucket[F]): F[(Boolean, Boolean => F[Unit])] =
    for {
      acquireDef   <- Deferred[F, Boolean]
      hasTokensDef <- Deferred[F, Boolean]
      fiber        <- bucket.maybeAcquireToken(acquireDef, hasTokensDef).start
      hasTokens    <- hasTokensDef.get
    } yield (hasTokens, (acquire: Boolean) => acquireDef.complete(acquire) *> fiber.joinWithUnit)

  override protected def acquireTokensFromBuckets(buckets: TokenBucket[F]*): F[Int] = {
    val res = buckets.zipWithIndex.reverse.toList
      .foldLeftM((Nil: List[Boolean => F[Unit]], -1)) {
        case ((acquireAcc, failedIdx), (bucket, idx)) if failedIdx == -1 =>
          bucket match {
            case bucket: CatsTokenBucket[F] =>
              for {
                t <- acquireTokenLockstep(bucket)
                (hasTokens, acquire) = t
                res <-
                  if (hasTokens) (acquire :: acquireAcc, -1).pure
                  else acquire(false).as((acquireAcc, idx))
              } yield res

            case _ => sys.error("Invalid token bucket type used in Ratelimiter")
          }
        case (t, _) => t.pure.widen
      }

    res.flatMap { case (acquireAcc, failedIdx) =>
      val success = failedIdx == -1
      acquireAcc.traverse_(f => f(success)).as(failedIdx)
    }
  }

  override protected def getMonotonicTime: F[Long] = Clock[F].monotonic.map(_.toNanos)

  override protected def updateBucketLocation(route: RequestRoute, bucket: String): F[Unit] =
    for {
      _ <- uriToBucket(route.uriWithMajor).set(Some(bucket))
      _ <- routeRateLimitQueues.updateBucket(route, bucket)
      _ <- routeTokenBuckets.updateBucket(route, bucket)
      _ <- routeRatelimitResetAt.updateBucket(route, bucket)
      _ <- routeResetFibers.updateBucket(route, bucket)
    } yield ()

  override protected def globalRatelimitReset: F[Long] = globalRatelimitResetAt.get

  override protected def routeRatelimitReset(route: RequestRoute): F[Long] =
    routeRatelimitResetAt.get(route).map(_.getOrElse(-1))

  private def sleepUntil(time: Long): F[Unit] =
    Clock[F].realTimeInstant.map(_.until(Instant.ofEpochMilli(time), ChronoUnit.MILLIS).millis).map(Temporal[F].sleep)

  private def replaceGlobalRatelimitResetFiber(newBehavior: F[Unit]): F[Unit] =
    supervisor
      .supervise(newBehavior *> globalResetFiber.set(None))
      .flatMap(newFiber => globalResetFiber.modify(oldFiber => (Some(newFiber), oldFiber)))
      .flatMap(_.traverse(_.cancel))
      .void

  override protected def onGlobalRatelimit(resetAt: Long): F[Unit] =
    globalRatelimitResetAt.set(resetAt) *>
      globalTokenBucket.stop *>
      replaceGlobalRatelimitResetFiber(
        sleepUntil(resetAt) *> resetGlobalRateLimit(resetTokens = true)
      )

  private def releaseWaitingRequests(bucket: CatsTokenBucket[F], queue: CatsPQueue[F]): F[Unit] =
    acquireTokenLockstep(bucket).flatMap { case (hasTokens, acquire) =>
      if (hasTokens)
        queue.tryDequeue.ifM(
          ifTrue = acquire(true) *> releaseWaitingRequests(bucket, queue),
          ifFalse = ().pure
        )
      else ().pure
    }

  private def resetGlobalRateLimit(resetTokens: Boolean): F[Unit] =
    globalTokenBucket.start *>
      (if (resetTokens) globalTokenBucket.resetTokens else ().pure) *>
      releaseWaitingRequests(globalTokenBucket, globalRatelimitQueue) *>
      globalRatelimitQueue.size
        .map(_ > 0)
        .ifM(
          ifTrue =
            replaceGlobalRatelimitResetFiber(Temporal[F].sleep(1.second) *> resetGlobalRateLimit(resetTokens = false)),
          ifFalse = ().pure
        )

  private def replaceRouteRateLimitResetFiber(route: RequestRoute, newBehavior: F[Unit]): F[Unit] =
    supervisor
      .supervise(newBehavior *> routeResetFibers.remove(route))
      .flatMap(newFiber =>
        routeResetFibers.get(route).flatMap(oldFiber => routeResetFibers.set(route, newFiber).as(oldFiber))
      )
      .flatMap(_.traverse_(_.cancel))

  override protected def updateRouteRatelimit(
      route: RequestRoute,
      resetAt: Long,
      retryAt: Long
  ): F[Unit] = {
    for {
      _           <- routeRatelimitResetAt.set(route, resetAt)
      tokenBucket <- routeTokenBuckets.get(route)
      _ <- replaceRouteRateLimitResetFiber(
        route,
        for {
          ratelimitQueue <- routeRateLimitQueues.get(route)

          didRetry <-
            if (retryAt > 0 && retryAt != resetAt)
              sleepUntil(retryAt) *> ratelimitQueue.traverse(_.tryDequeue).map(_.getOrElse(false))
            else false.pure

          _ <- sleepUntil(resetAt)
          //If we succeeded in retrying a request early, we need to reset with one less token than normal
          _ <- tokenBucket.traverse(b => b.resetTokens *> (if (didRetry) b.acquireToken.void else ().pure))
          _ <- ratelimitQueue.fold(().pure) { q =>
            //If we're missing a bucket, I have no idea how we ended up here at all. We'll just release the entire queue
            tokenBucket.fold(q.dequeueAll)(b => releaseWaitingRequests(b, q))
          }
        } yield ()
      )
    } yield ()
  }
}
object CatsEffectDiscordRatelimiter {
  type Bucket = String

  def apply[F[_]: Async: SttpMonadError](
      logRateLimitEvents: Boolean = false,
      globalRequestsPerSecond: Int = 50
  ): F[Ratelimiter[F]] = {
    Supervisor.apply(await = false).use { supervisor =>
      for {
        uriToBuckets <- MapRef.ofConcurrentHashMap[F, String, Bucket]()
        globalQueue <- CatsPQueue[F]
        routeRatelimitQueues <- RouteMap[F, CatsPQueue[F]](uriToBuckets)
        globalTokenBucket <- CatsTokenBucket.auto[F](globalRequestsPerSecond)
        routeTokenBuckets <- RouteMap[F, CatsTokenBucket[F]](uriToBuckets)
        routeLimitsNoParams <- MapRef.ofConcurrentHashMap[F, String, Int]()
        globalRateLimitResetAt <- Ref[F].of(0L)
        routeRateLimitResetAt <- RouteMap[F, Long](uriToBuckets)
        globalResetFiber <- Ref[F].of[Option[Fiber[F, Throwable, Unit]]](None)
        routeResetFibers <- RouteMap[F, Fiber[F, Throwable, Unit]](uriToBuckets)
      } yield new CatsEffectDiscordRatelimiter(logRateLimitEvents)(
        globalQueue,
        routeRatelimitQueues,
        globalTokenBucket,
        routeTokenBuckets,
        routeLimitsNoParams,
        uriToBuckets,
        globalRateLimitResetAt,
        routeRateLimitResetAt,
        supervisor,
        globalResetFiber,
        routeResetFibers
      )
    }
  }

  class RouteMap[F[_]: Monad, A](
      uriToBucket: MapRef[F, String, Option[Bucket]],
      maps: AtomicCell[F, (Map[Bucket, A], Map[String, A])]
  ) {

    def get(route: RequestRoute): F[Option[A]] = for {
      bucket <- uriToBucket(route.uriWithMajor).get
      maps   <- maps.get
    } yield bucket.fold(maps._2.get(route.uriWithMajor)) { bucket =>
      maps._1.get(bucket).orElse(maps._2.get(route.uriWithMajor))
    }

    def getOrMake(route: RequestRoute, make: F[A]): F[A] =
      //Try to get first in case it already has one
      get(route).flatMap {
        case Some(value) => value.pure
        case None =>
          modifyF(
            route,
            {
              case Some(value) => Some(value).pure.widen
              case None        => make.map(Some.apply).widen
            }
          ).map(_.get)
      }

    def updateBucket(route: RequestRoute, bucket: Bucket): F[Unit] =
      maps.update { case (bucketMap, noBucketMap) =>
        noBucketMap.get(route.uriWithMajor) match {
          case Some(value) => (bucketMap.updated(bucket, value), noBucketMap.removed(route.uriWithoutMajor))
          case None        => (bucketMap, noBucketMap)
        }
      }

    def set(route: RequestRoute, value: A): F[Unit] = modifyF(route, _ => Some(value).pure.widen).void

    def modifyF(route: RequestRoute, f: Option[A] => F[Option[A]]): F[Option[A]] =
      maps.evalModify { case (bucketMap, noBucketMap) =>
        uriToBucket(route.uriWithMajor).get.flatMap {
          case Some(bucket) =>
            f(bucketMap.get(bucket).orElse(noBucketMap.get(route.uriWithMajor)))
              .map(value =>
                ((bucketMap.updatedWith(bucket)(_ => value), noBucketMap.removed(route.uriWithMajor)), value)
              )

          case None =>
            f(noBucketMap.get(route.uriWithMajor)).map(value =>
              ((bucketMap, noBucketMap.updatedWith(route.uriWithMajor)(_ => value)), value)
            )
        }
      }

    def remove(route: RequestRoute): F[Unit] = modifyF(route, _ => None.pure.widen).void
  }

  object RouteMap {
    def apply[F[_]: Async, A](uriToBucket: MapRef[F, String, Option[Bucket]]): F[RouteMap[F, A]] =
      AtomicCell[F].of[(Map[Bucket, A], Map[String, A])]((Map.empty, Map.empty)).map(new RouteMap(uriToBucket, _))
  }

  case class QueueElement[F[_]](id: UUID, time: Long, deferred: Deferred[F, Unit])
  object QueueElement {
    implicit def order[F[_]]: Order[QueueElement[F]] = Order.reverse(Order.by(_.time))
  }

  class CatsPQueue[F[_]: Concurrent](underlying: cats.effect.std.PQueue[F, QueueElement[F]])
      extends BaseDiscordRatelimiter.PQueue[F] {
    override def size: F[Int] = underlying.size

    override def enqueueAndWaitTilDequeue(id: UUID, time: Long): F[Unit] =
      for {
        deferred <- Deferred[F, Unit]
        _        <- underlying.offer(QueueElement(id, time, deferred))
        _        <- deferred.get
      } yield ()

    def tryDequeue: F[Boolean] = underlying.tryTake.flatMap {
      case Some(v) => v.deferred.complete(()).as(true)
      case None    => false.pure
    }

    def dequeueAll: F[Unit] = underlying.tryTakeN(None).void
  }
  object CatsPQueue {
    def apply[F[_]: Concurrent]: F[CatsPQueue[F]] =
      for {
        underlying <- cats.effect.std.PQueue.unbounded[F, QueueElement[F]]
      } yield new CatsPQueue(underlying)
  }

  class CatsTokenBucket[F[_]: Monad: Clock](
      tokensPerSecond: Option[Int],
      stoppedRef: Ref[F, Boolean],
      maxSizeRef: Ref[F, Int],
      tokensRemainingRef: AtomicCell[F, Int],
      lastTokenAcquired: Ref[F, Instant]
  ) extends TokenBucket[F] {
    private val timeBetweenTokensMs = tokensPerSecond.map(1D / _).map(_ * 1000).map(_.toLong)

    private def timeBetweenTokensMsIfStarted: F[Option[Long]] = stoppedRef.get.map(if (_) timeBetweenTokensMs else None)

    override def maxSize: F[Int] = maxSizeRef.get

    override def tokensRemaining: F[Int] =
      timeBetweenTokensMsIfStarted.flatMap(_.fold(tokensRemainingRef.get) { betweenTokensMs =>
        for {
          now    <- Clock[F].realTimeInstant
          before <- lastTokenAcquired.get
          timeElapsed = before.until(now, ChronoUnit.MILLIS)
          extraTokens = timeElapsed / betweenTokensMs
          trackedRemaining <- tokensRemainingRef.get
        } yield (trackedRemaining + extraTokens).toInt
      })

    override def setTokensMaxSize(tokens: Int, maxSize: Int): F[Unit] =
      tokensRemainingRef.evalUpdate(_ => maxSizeRef.set(maxSize).as(tokens))

    override def acquireToken: F[Boolean] =
      tokensRemainingRef.evalModify { remaining =>
        for {
          now                  <- Clock[F].realTimeInstant
          timeBetweenMsStarted <- timeBetweenTokensMsIfStarted
          realRemaining <- timeBetweenMsStarted.fold(remaining.pure) { betweenTokensMs =>
            for {
              before <- lastTokenAcquired.get
              timeElapsed = before.until(now, ChronoUnit.MILLIS)
              extraTokens = timeElapsed / betweenTokensMs
            } yield (remaining + extraTokens).toInt
          }
          _ <- if (realRemaining > remaining) lastTokenAcquired.set(now) else ().pure
        } yield (0.max(realRemaining - 1), realRemaining > 0)
      }

    override def giveToken: F[Boolean] = tokensRemainingRef.evalModify(remaining =>
      maxSizeRef.get.map(maxAmount => (maxAmount.min(remaining + 1), remaining < maxAmount))
    )

    def resetTokens: F[Unit] =
      tokensRemainingRef.evalUpdate(_ => Clock[F].realTimeInstant.flatMap(lastTokenAcquired.set) *> maxSizeRef.get)

    def maybeAcquireToken(
        deferredAcquire: DeferredSource[F, Boolean],
        deferredHasTokens: DeferredSink[F, Boolean]
    ): F[Unit] =
      tokensRemainingRef.evalUpdate { remaining =>
        for {
          now                  <- Clock[F].realTimeInstant
          timeBetweenMsStarted <- timeBetweenTokensMsIfStarted
          realRemaining <- timeBetweenMsStarted.fold(remaining.pure) { betweenTokensMs =>
            for {
              before <- lastTokenAcquired.get
              timeElapsed = before.until(now, ChronoUnit.MILLIS)
              extraTokens = timeElapsed / betweenTokensMs
            } yield (remaining + extraTokens).toInt
          }
          _ <- if (realRemaining > remaining) lastTokenAcquired.set(now) else ().pure
          res <- {
            if (remaining > 0)
              deferredHasTokens.complete(true) *> deferredAcquire.get.map(acquire =>
                if (acquire) remaining - 1 else remaining
              )
            else deferredHasTokens.complete(false).as(remaining)
          }
        } yield res
      }

    def stop: F[Unit] = stoppedRef.set(true)

    def start: F[Unit] = stoppedRef.set(false)
  }
  object CatsTokenBucket {
    def manual[F[_]: Async]: F[CatsTokenBucket[F]] =
      for {
        stoppedRef         <- Ref[F].of(false)
        maxSizeRef         <- Ref[F].of(0)
        tokensRemainingRef <- AtomicCell[F].of(0)
        lastTokenAcquired  <- Ref[F].of(Instant.now())
      } yield new CatsTokenBucket[F](None, stoppedRef, maxSizeRef, tokensRemainingRef, lastTokenAcquired)

    def auto[F[_]: Async](tokensPerSecond: Int): F[CatsTokenBucket[F]] =
      for {
        stoppedRef         <- Ref[F].of(false)
        maxSizeRef         <- Ref[F].of(0)
        tokensRemainingRef <- AtomicCell[F].of(0)
        lastTokenAcquired  <- Ref[F].of(Instant.now())
      } yield new CatsTokenBucket[F](
        Some(tokensPerSecond),
        stoppedRef,
        maxSizeRef,
        tokensRemainingRef,
        lastTokenAcquired
      )
  }
}
