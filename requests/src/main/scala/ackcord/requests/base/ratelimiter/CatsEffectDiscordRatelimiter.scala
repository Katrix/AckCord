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
class CatsEffectDiscordRatelimiter[F[_]: Async: SttpMonadError] private (logRateLimitEvents: Boolean = false)(
    catsGlobalRatelimitQueue: CatsPQueue[F],
    routeRateLimitQueues: RouteMap[F, CatsPQueue[F]],
    catsGlobalTokenBucket: CatsTokenBucket[F],
    routeTokenBuckets: RouteMap[F, CatsTokenBucket[F]],
    routeLimitsNoParams: MapRef[F, String, Option[Int]],
    uriToBucket: MapRef[F, String, Option[Bucket]],
    supervisor: Supervisor[F]
) extends BaseDiscordRatelimiter[F](logRateLimitEvents) {

  override protected def globalRatelimitQueue: PQueue[F] = catsGlobalRatelimitQueue

  override protected def globalTokenBucket: TokenBucket[F] = catsGlobalTokenBucket

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

  //This is a resource to make sure the acquire action is always set. It defaults to false if nothing has set it
  private def acquireTokenLockstep(bucket: CatsTokenBucket[F]): Resource[F, (Boolean, Boolean => F[Unit])] =
    Resource.make(for {
      acquireDef   <- Deferred[F, Boolean]
      hasTokensDef <- Deferred[F, Boolean]
      fiber        <- bucket.maybeAcquireToken(acquireDef, hasTokensDef).start
      hasTokens    <- hasTokensDef.get
    } yield (hasTokens, (acquire: Boolean) => acquireDef.complete(acquire) *> fiber.joinWithUnit))(t => t._2(false))

  override protected def acquireTokensFromBuckets(buckets: TokenBucket[F]*): F[Int] = {
    buckets.zipWithIndex.reverse.toList
      .foldLeftM((Nil: List[Boolean => F[Unit]], -1)) {
        case ((acquireAcc, failedAtIdx), (bucket, idx)) if failedAtIdx == -1 =>
          bucket match {
            case bucket: CatsTokenBucket[F] =>
              acquireTokenLockstep(bucket).map { case (hasTokens, acquire) =>
                if (hasTokens) (acquire :: acquireAcc, -1)
                else (acquireAcc, idx)
              }

            case _ => sys.error("Invalid token bucket type used in Ratelimiter")
          }
        case (t, _) => t.pure[Resource[F, *]].widen
      }
      .use { case (acquireAcc, failedAtIdx) =>
        val success = failedAtIdx == -1
        acquireAcc.traverse_(f => f(success)).as(failedAtIdx)
      }
  }

  override protected def getMonotonicTime: F[Long] = Clock[F].monotonic.map(_.toNanos)

  override protected def associateRouteWithBucket(route: RequestRoute, bucket: String): F[Unit] =
    for {
      _ <- uriToBucket(route.uriWithMajor).set(Some(bucket))
      _ <- routeRateLimitQueues.updateBucket(route, bucket)
      _ <- routeTokenBuckets.updateBucket(route, bucket)
    } yield ()

  override protected def globalRatelimitRetry: F[Long] = catsGlobalTokenBucket.retryAt

  override protected def routeRatelimitReset(route: RequestRoute): F[Long] =
    routeTokenBuckets.get(route).flatMap(_.flatTraverse(_.resetAt)).map(_.getOrElse(-1))

  override protected def onGlobalRatelimit(retryAt: Long): F[Unit] =
    catsGlobalTokenBucket.setResetRetryAt(-1, retryAt)

  private def releaseWaitingRequests(bucket: CatsTokenBucket[F], queue: CatsPQueue[F]): F[Unit] =
    acquireTokenLockstep(bucket).use { case (hasTokens, acquire) =>
      if (hasTokens)
        queue.tryDequeue.ifM(
          ifTrue = acquire(false) *> releaseWaitingRequests(bucket, queue),
          ifFalse = acquire(false)
        )
      else ().pure
    }

  override protected def updateRouteRatelimit(
      route: RequestRoute,
      resetAt: Long,
      retryAt: Long
  ): F[Unit] =
    for {
      tokenBucket <- routeTokenBuckets.get(route)
      _           <- tokenBucket.traverse(_.setResetRetryAt(resetAt, retryAt))
    } yield ()

  protected def start: F[Unit] = {
    def checkOnGlobalQueue: F[Unit] = releaseWaitingRequests(catsGlobalTokenBucket, catsGlobalRatelimitQueue)

    def checkOnRouteQueues: F[Unit] =
      for {
        queues       <- routeRateLimitQueues.all
        tokenBuckets <- routeTokenBuckets.all
        r <- queues.toSeq.traverse_ { case (key, q) =>
          q.size.flatMap { size =>
            if (size == 0) ().pure
            else
              tokenBuckets.get(key) match {
                case Some(bucket) => releaseWaitingRequests(bucket, q)
                case None         => q.dequeueAll //Without a bucket we can't really know what to do
              }
          }
        }
      } yield r

    def behavior: F[Unit] =
      ().iterateForeverM(_ => checkOnGlobalQueue *> checkOnRouteQueues *> Temporal[F].sleep(1.second))

    supervisor.supervise(behavior).void
  }
}
object CatsEffectDiscordRatelimiter {
  type Bucket = String

  def apply[F[_]: Async: SttpMonadError](
      logRateLimitEvents: Boolean = false,
      globalRequestsPerSecond: Int = 50
  ): Resource[F, Ratelimiter[F]] = {
    Supervisor(await = false).flatMap { supervisor =>
      Resource.eval(
        for {
          uriToBuckets         <- MapRef.ofConcurrentHashMap[F, String, Bucket]()
          globalQueue          <- CatsPQueue[F]
          routeRatelimitQueues <- RouteMap[F, CatsPQueue[F]](uriToBuckets)
          globalTokenBucket    <- CatsTokenBucket.auto[F](globalRequestsPerSecond)
          routeTokenBuckets    <- RouteMap[F, CatsTokenBucket[F]](uriToBuckets)
          routeLimitsNoParams  <- MapRef.ofConcurrentHashMap[F, String, Int]()
          ratelimiter = new CatsEffectDiscordRatelimiter(logRateLimitEvents)(
            globalQueue,
            routeRatelimitQueues,
            globalTokenBucket,
            routeTokenBuckets,
            routeLimitsNoParams,
            uriToBuckets,
            supervisor
          )
          _ <- ratelimiter.start
        } yield ratelimiter
      )
    }
  }

  private class RouteMap[F[_]: Monad, A](
      uriToBucket: MapRef[F, String, Option[Bucket]],
      maps: AtomicCell[F, Map[Either[Bucket, String], A]]
  ) {

    def all: F[Map[Either[Bucket, Bucket], A]] = maps.get

    def get(route: RequestRoute): F[Option[A]] = for {
      bucket <- uriToBucket(route.uriWithMajor).get
      maps   <- maps.get
    } yield bucket.fold(maps.get(Right(route.uriWithMajor))) { bucket =>
      maps.get(Left(bucket)).orElse(maps.get(Right(route.uriWithMajor)))
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
      maps.update { map =>
        map.get(Right(route.uriWithMajor)) match {
          case Some(value) => map.updated(Left(bucket), value).removed(Right(route.uriWithoutMajor))
          case None        => map
        }
      }

    private def modifyF(route: RequestRoute, f: Option[A] => F[Option[A]]): F[Option[A]] =
      maps.evalModify { map =>
        uriToBucket(route.uriWithMajor).get.flatMap {
          case Some(bucket) =>
            f(map.get(Left(bucket)).orElse(map.get(Right(route.uriWithMajor))))
              .map(value => (map.updatedWith(Left(bucket))(_ => value).removed(Right(route.uriWithMajor)), value))

          case None =>
            f(map.get(Right(route.uriWithMajor))).map(value =>
              (map.updatedWith(Right(route.uriWithMajor))(_ => value), value)
            )
        }
      }
  }

  private object RouteMap {
    def apply[F[_]: Async, A](uriToBucket: MapRef[F, String, Option[Bucket]]): F[RouteMap[F, A]] =
      AtomicCell[F].of[Map[Either[Bucket, String], A]](Map.empty).map(new RouteMap(uriToBucket, _))
  }

  private case class QueueElement[F[_]](id: UUID, time: Long, deferred: Deferred[F, Unit])
  private object QueueElement {
    implicit def order[F[_]]: Order[QueueElement[F]] = Order.reverse(Order.by(_.time))
  }

  private class CatsPQueue[F[_]: Concurrent](underlying: cats.effect.std.PQueue[F, QueueElement[F]])
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

    def dequeueAll: F[Unit] = underlying.tryTakeN(None).flatMap(xs => xs.traverse_(_.deferred.complete(())))
  }
  private object CatsPQueue {
    def apply[F[_]: Concurrent]: F[CatsPQueue[F]] =
      for {
        underlying <- cats.effect.std.PQueue.unbounded[F, QueueElement[F]]
      } yield new CatsPQueue(underlying)
  }

  class CatsTokenBucket[F[_]: Monad: Clock](
      tokensPerSecond: Option[Int],
      resetAtRef: Option[Ref[F, (Long, Long)]],
      retryAtRef: Ref[F, Long],
      maxSizeRef: Ref[F, Int],
      tokensRemainingRef: AtomicCell[F, Double],
      lastTokenAcquired: Ref[F, Option[Instant]]
  ) extends TokenBucket[F] {
    private val autoTimeBetweenTokensMs = tokensPerSecond.map(1D / _).map(_ * 1000).map(_.toLong)

    private def timeBetweenTokensMsIfStarted(now: Instant, missing: Double): F[Option[Long]] =
      retryAtRef.get.flatMap { at =>
        if (at > now.toEpochMilli || missing < 0.01) (None: Option[Long]).pure
        else
          autoTimeBetweenTokensMs match {
            case Some(value) => (Some(value): Option[Long]).pure
            case None =>
              resetAtRef
                .flatTraverse { resetAtR =>
                  resetAtR.get.map { case (set, resetAt) =>
                    if (resetAt > 0) Some(((resetAt - set) / missing).toLong)
                    else None
                  }
                }
          }
      }

    override def maxSize: F[Int] = maxSizeRef.get

    private def calcExtraTokens(now: Instant, missing: Double): F[Double] = {
      timeBetweenTokensMsIfStarted(now, missing).flatMap(_.fold(0D.pure) { betweenTokensMs =>
        lastTokenAcquired.get.map(_.fold(0D) { before =>
          val timeElapsed = before.until(now, ChronoUnit.MILLIS)
          timeElapsed.toDouble / betweenTokensMs
        })
      })
    }

    override def tokensRemaining: F[Int] =
      for {
        max       <- maxSize
        now       <- Clock[F].realTimeInstant
        remaining <- tokensRemainingRef.get
        extra     <- calcExtraTokens(now, max - remaining)
      } yield max.min((remaining + extra).toInt)

    override def setTokensMaxSize(tokens: Int, maxSize: Int): F[Unit] =
      tokensRemainingRef.evalUpdate { _ =>
        maxSizeRef.set(maxSize).as(tokens)
      }

    override def acquireToken: F[Boolean] =
      tokensRemainingRef.evalModify { remaining =>
        for {
          now   <- Clock[F].realTimeInstant
          max   <- maxSize
          extra <- calcExtraTokens(now, max - remaining)
          _     <- lastTokenAcquired.set(Some(now))
        } yield {
          val realRemaining = max.toDouble.min(remaining + extra)
          val canAcquire    = realRemaining.toInt > 0
          (if (canAcquire) realRemaining - 1 else realRemaining, canAcquire)
        }
      }

    override def giveToken: F[Boolean] = tokensRemainingRef.evalModify { remaining =>
      maxSizeRef.get.map(maxAmount => (maxAmount.toDouble.min(remaining + 1), remaining < maxAmount))
    }

    def maybeAcquireToken(
        deferredAcquire: DeferredSource[F, Boolean],
        deferredHasTokens: DeferredSink[F, Boolean]
    ): F[Unit] =
      tokensRemainingRef.evalUpdate { remaining =>
        for {
          now   <- Clock[F].realTimeInstant
          max   <- maxSize
          extra <- calcExtraTokens(now, max - remaining)
          _     <- lastTokenAcquired.set(Some(now))
          res <- {
            val realRemaining = max.toDouble.min(remaining + extra)
            if (realRemaining.toInt > 0)
              deferredHasTokens.complete(true) *> deferredAcquire.get.map { acquire =>
                if (acquire) realRemaining - 1 else realRemaining
              }
            else deferredHasTokens.complete(false).as(realRemaining)
          }
        } yield res
      }

    def retryAt: F[Long] = retryAtRef.get

    def resetAt: F[Option[Long]] = resetAtRef.traverse(_.get.map(_._2))

    def setResetRetryAt(resetAt: Long, retryAt: Long): F[Unit] =
      resetAtRef.traverse_(ref => Clock[F].realTime.flatMap(now => ref.set((now.toMillis, resetAt)))) *>
        retryAtRef.set(retryAt)

    def toStringF: F[String] = for {
      resetAt   <- resetAtRef.traverse(_.get)
      retryAt   <- retryAtRef.get
      maxSize   <- maxSizeRef.get
      remaining <- tokensRemainingRef.get
      last      <- lastTokenAcquired.get
      now       <- Clock[F].realTimeInstant
      extra     <- calcExtraTokens(now, maxSize - remaining)
    } yield s"CatsTokenBucket($tokensPerSecond, $resetAt, $retryAt, $maxSize, $remaining + $extra, $last)"
  }
  private object CatsTokenBucket {
    def manual[F[_]: Async]: F[CatsTokenBucket[F]] =
      for {
        resetAtRef         <- Ref[F].of((-1L, -1L))
        retryAtRef         <- Ref[F].of(-1L)
        maxSizeRef         <- Ref[F].of(0)
        tokensRemainingRef <- AtomicCell[F].of(0D)
        lastTokenAcquired  <- Ref[F].of(None: Option[Instant])
      } yield new CatsTokenBucket[F](
        None,
        Some(resetAtRef),
        retryAtRef,
        maxSizeRef,
        tokensRemainingRef,
        lastTokenAcquired
      )

    def auto[F[_]: Async](tokensPerSecond: Int): F[CatsTokenBucket[F]] =
      for {
        retryAtRef         <- Ref[F].of(-1L)
        maxSizeRef         <- Ref[F].of(tokensPerSecond)
        tokensRemainingRef <- AtomicCell[F].of(tokensPerSecond.toDouble)
        lastTokenAcquired  <- Ref[F].of(None: Option[Instant])
      } yield new CatsTokenBucket[F](
        Some(tokensPerSecond),
        None,
        retryAtRef,
        maxSizeRef,
        tokensRemainingRef,
        lastTokenAcquired
      )
  }
}
