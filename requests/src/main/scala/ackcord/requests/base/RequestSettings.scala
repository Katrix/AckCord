package ackcord.requests.base

import scala.concurrent.duration.FiniteDuration

import ackcord.requests.base.ratelimiter.{CatsEffectDiscordRatelimiter, Ratelimiter}
import cats.effect.kernel.Async
import cats.effect.{IO, Resource}
import cats.syntax.all._
import org.typelevel.log4cats.{Logger, LoggerFactory}
import sttp.client3.impl.cats.implicits._
import sttp.model.{Header, Uri}

/**
  * @param credentials
  *   The credentials to use when sending the requests.
  * @param ratelimiter
  *   The object to use to track ratelimits and such.
  * @param waitDuration
  *   A function that blocks for the specified amount of time.
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
    logger: Logger[F],
    waitDuration: FiniteDuration => F[Unit],
    maxRetryCount: Int = 3,
    baseUri: Uri = RequestRoute.defaultBase,
    userAgent: Header = RequestHandling.defaultUserAgent
)
object RequestSettings {
  def simpleF[F[_]](
      token: String
  )(implicit F: Async[F], logFactory: LoggerFactory[F]): Resource[F, RequestSettings[F]] = {
    for {
      ratelimiter <- CatsEffectDiscordRatelimiter[F]()
      log         <- Resource.eval(logFactory.fromClass(classOf[Requests[F, Any]]))
    } yield RequestSettings(
      Some(Header.authorization("Bot", token)),
      ratelimiter,
      log,
      F.sleep
    )
  }
}
