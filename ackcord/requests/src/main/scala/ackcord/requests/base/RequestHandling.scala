package ackcord.requests.base

import java.time.Instant
import java.util.Locale

import scala.concurrent.duration._
import scala.util.control.NonFatal

import ackcord.data.AckCordInfo
import ackcord.requests.base.ratelimiter.RatelimitInfo
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import sttp.capabilities.Effect
import sttp.client3.{Response => SttpResponse, _}
import sttp.model.{Header, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._

object RequestHandling {

  private val sentReceivedLogger = LoggerFactory.getLogger(getClass.getName + ".SentReceivedRequests")

  private def logSentREST: Boolean     = true //TODO
  private def logReceivedREST: Boolean = true //TODO

  private def logSentRESTLevel: Level     = Level.INFO //TODO
  private def logReceivedRESTLevel: Level = Level.INFO //TODO

  private def findHeader(response: SttpResponse[_], header: String): Option[String] =
    response.headers.find(_.name.toLowerCase(Locale.ROOT) == header.toLowerCase(Locale.ROOT)).map(_.value)

  private def remainingRequests[A](response: SttpResponse[A]): Int =
    findHeader(response, "X-RateLimit-Remaining").fold(-1)(_.toInt)

  private def requestsForUri[A](response: SttpResponse[A]): Int =
    findHeader(response, "X-RateLimit-Limit").fold(-1)(_.toInt)

  private def resetAt[A](response: SttpResponse[A]): Long =
    findHeader(response, "X-RateLimit-Reset-After").fold(-1L) { value =>
      System.currentTimeMillis() + (value.toDouble * 1000).toLong
    }

  private def retryAt[A](response: SttpResponse[A]): Long =
    findHeader(response, "Retry-After").fold(-1L)(v => Instant.now().plusSeconds(v.toInt).toEpochMilli)

  private def isGlobalRatelimit[A](response: SttpResponse[A]): Boolean =
    findHeader(response, "X-Ratelimit-Global").fold(false)(_.toBoolean)

  private def requestBucket[A](route: RequestRoute, response: SttpResponse[A]): String =
    findHeader(response, "X-RateLimit-Bucket").getOrElse(route.uriWithoutMajor) //Sadly this is not always present

  val defaultUserAgent: Header = Header.userAgent(
    s"DiscordBot (https://github.com/Katrix/AckCord, ${AckCordInfo.Version})"
  )

  def runRequestWithoutRatelimits[Response, R, R1 >: R with Effect[F], F[_]](
      request: AckCordRequest[Response, R1],
      backend: SttpBackend[F, R],
      settings: RequestSettings[F]
  ): F[RequestAnswer[Response]] = {
    implicit val monad: MonadError[F] = backend.responseMonad

    val baseSttpRequest = request.toSttpRequest(settings.baseUri)

    def logBody(): Unit = {
      if (logSentREST) {
        sentReceivedLogger
          .atLevel(logSentRESTLevel)
          .log(
            s"Sent REST request to ${request.route.method} ${baseSttpRequest.uri} with body ${request.bodyForLogging}"
          )
      }
    }

    def logResponse(res: SttpResponse[Option[String]]): Unit =
      if (logReceivedREST) {
        val logBody = res.body.isDefined
        sentReceivedLogger.atLevel(logReceivedRESTLevel).log(res.show(includeBody = logBody))
      }

    backend.responseMonad.handleError[RequestAnswer[Response]] {
      for {
        _ <- backend.responseMonad.unit(())
        _ <- monad.blocking(logBody())
        resWithStrBody <- backend.send(
          baseSttpRequest
            .headers(settings.credentials.toList :+ settings.userAgent: _*)
            .response(asBothOption(baseSttpRequest.response, asStringAlways))
        )
        _ <- monad.blocking(logResponse(resWithStrBody.copy(body = resWithStrBody.body._2)))
        res = resWithStrBody.copy(body = resWithStrBody.body._1)
        ratelimitInfo = RatelimitInfo(
          resetAt(res),
          retryAt(res),
          remainingRequests(res),
          requestsForUri(res),
          requestBucket(request.route, res),
          isGlobalRatelimit(res)
        )
      } yield (res.body, res.code) match {
        case (Right(Right(body)), _) => RequestResponse(body, ratelimitInfo, request.route, request.identifier)

        case (_, StatusCode.TooManyRequests) =>
          FailedRequest.RequestRatelimited(ratelimitInfo, request.route, request.identifier)

        case (Right(Left(errorMsg)), code) =>
          FailedRequest.RequestError(
            HttpException(request.route.uri, request.route.method, code, extraInfo = Some(errorMsg)),
            request.route,
            request.identifier,
            ratelimitInfo
          )

        case (Left(err), _) =>
          FailedRequest.RequestError(err, request.route, request.identifier, ratelimitInfo)
      }
    } { case NonFatal(e) =>
      val ratelimitInfo = RatelimitInfo(-1, -1, -1, -1, "", isGlobal = false)
      monad.unit(FailedRequest.RequestError(e, request.route, request.identifier, ratelimitInfo))
    }
  }

  def runRequest[Response, R, R1 >: R with Effect[F], F[_]](
      request: AckCordRequest[Response, R1],
      backend: SttpBackend[F, R],
      settings: RequestSettings[F]
  ): F[RequestAnswer[Response]] = {
    implicit val F: MonadError[F] = backend.responseMonad

    settings.ratelimiter
      .ratelimitRequest(request.route, request, request.identifier)
      .flatMap[RequestAnswer[Response]] {
        case Right(req) =>
          runRequestWithoutRatelimits(req, backend, settings).flatTap(settings.ratelimiter.reportRatelimits)
        case Left(dropped) => backend.responseMonad.unit(dropped)
      }
  }

  def runRequestWithRetry[Response, F[_]](
      runRequest: F[RequestAnswer[Response]],
      settings: RequestSettings[F],
      timesRetried: Int = 0
  )(
      implicit F: MonadError[F]
  ): F[RequestAnswer[Response]] = {
    val runWithWait = F.flatMap(settings.waitDuration(500.millis * Math.pow(2, timesRetried).toLong))(_ => runRequest)
    if (timesRetried + 1 >= settings.maxRetryCount) runWithWait
    else
      runWithWait.flatMap {
        case res: RequestResponse[Response] => F.unit(res)
        case _: FailedRequest               => runRequestWithRetry(runRequest, settings, timesRetried + 1)
      }
  }

}
