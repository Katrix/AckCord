package ackcord.requests.base

import scala.concurrent.duration.FiniteDuration

import ackcord.requests.base.ratelimiter.Ratelimiter
import sttp.model.{Header, Uri}

/**
  * @param credentials
  *   The credentials to use when sending the requests.
  * @param ratelimiter
  *   The object to use to track ratelimits and such.
  * @param waitDuration
  *   A function that blocks for the specified amount of time.
  * @param relativeTime
  *   Sets if the ratelimit reset should be calculated using relative time
  *   instead of absolute time. Might help with out of sync time on your device,
  *   but can also lead to slightly slower processing of requests.
  * @param maxRetryCount
  *   How many times to retry requests if an error occurs on retry flows.
  * @param baseUri
  *   The base of the route to send all the requests to.
  * @param userAgent
  *   The user agent to use for this request.
  */
case class RequestSettings[F[_]](
                                  credentials: Option[Header],
                                  ratelimiter: Ratelimiter[F],
                                  waitDuration: FiniteDuration => F[Unit],
                                  relativeTime: Boolean = false,
                                  maxRetryCount: Int = 3,
                                  baseUri: Uri = RequestRoute.defaultBase,
                                  userAgent: Header = RequestHandling.defaultUserAgent
)
