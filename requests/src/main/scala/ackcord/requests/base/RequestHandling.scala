package ackcord.requests.base

import java.time.Instant

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

  private def logSentREST: Boolean     = ???
  private def logReceivedREST: Boolean = ???

  private def logSentRESTLevel: Level     = ???
  private def logReceivedRESTLevel: Level = ???

  private def remainingRequests[A](response: SttpResponse[A]): Int =
    response.headers.find(_.name == "X-RateLimit-Remaining").fold(-1)(_.value.toInt)

  private def requestsForUri[A](response: SttpResponse[A]): Int =
    response.headers.find(_.name == "X-RateLimit-Limit").fold(-1)(_.value.toInt)

  private def resetAt[A](response: SttpResponse[A]): Long =
    response.headers.find(_.name == "X-RateLimit-Reset").fold(-1L) { header =>
      (header.value.toDouble * 1000).toLong
    }

  private def retryAt[A](response: SttpResponse[A]): Long =
    response.headers.find(_.name == "Retry-After").fold(-1L)(h => Instant.now().plusSeconds(h.value.toInt).toEpochMilli)

  private def isGlobalRatelimit[A](response: SttpResponse[A]): Boolean =
    response.headers.find(_.name == "X-Ratelimit-Global").fold(false)(_.value.toBoolean)

  private def requestBucket[A](route: RequestRoute, response: SttpResponse[A]): String =
    response.headers
      .find(_.name == "X-RateLimit-Bucket")
      .fold(route.uriWithoutMajor)(_.value) //Sadly this is not always present

  val defaultUserAgent: Header = Header.userAgent(
    s"DiscordBot (https://github.com/Katrix/AckCord, ${AckCordInfo.Version})"
  )

  def runRequestWithoutRatelimits[Response, R, R1 >: R, F[_]](
      request: AckCordRequest[Response, R1],
      backend: SttpBackend[F, R with Effect[F]],
      settings: RequestSettings[F]
  ): F[RequestAnswer[Response]] = {
    implicit val monad: MonadError[F] = backend.responseMonad

    def logBody(): Unit = {
      if (logSentREST) {
        sentReceivedLogger
          .atLevel(logSentRESTLevel)
          .log(
            s"Sent REST request to ${request.route.method} ${request.route.uri} with body ${request.bodyForLogging}"
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
        baseSttpRequest = request.toSttpRequest(settings.baseUri)
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
            request.identifier
          )

        case (Left(err), _) =>
          FailedRequest.RequestError(err, request.route, request.identifier)
      }
    } { case NonFatal(e) =>
      monad.unit(FailedRequest.RequestError(e, request.route, request.identifier))
    }
  }

  def runRequest[Response, R, F[_]](
      request: AckCordRequest[Response, R],
      backend: SttpBackend[F, R with Effect[F]],
      settings: RequestSettings[F]
  ): F[RequestAnswer[Response]] =
    settings.ratelimiter
      .ratelimitRequest(request.route, request, request.identifier)
      .flatMap[RequestAnswer[Response]] {
        case Right(req)    => runRequestWithoutRatelimits(req, backend, settings)
        case Left(dropped) => backend.responseMonad.unit(dropped)
      }(backend.responseMonad)

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
