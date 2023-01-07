package ackcord.requests.base

import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import sttp.model.{Method, StatusCode, Uri}

/** An exception for Http errors. */
case class HttpException(uri: Uri, method: Method, statusCode: StatusCode, extraInfo: Option[String])
    extends Exception(s"$method $uri: ${statusCode.code}, ${extraInfo.fold("")(e => s" $e")}")

/**
  * An exception that signals than an endpoint is ratelimited.
  *
  * @param global
  *   If the rate limit is global.
  * @param resetAt
  *   The time in epoch millis when the ratelimit is reset.
  * @param uri
  *   The Uri for the request.
  */
case class RatelimitException(global: Boolean, resetAt: Long, uri: Uri, identifier: UUID)
    extends Exception(
      if (global) "Encountered global ratelimit"
      else s"Encountered ratelimit at $uri $identifier"
    )

/**
  * An exception that signals that a request was dropped.
  * @param uri
  *   The Uri for the request.
  */
case class DroppedRequestException(uri: Uri) extends Exception(s"Dropped request at $uri")

/** An exception thrown when parsing JSON if something goes wrong. */
case class HttpJsonDecodeException(message: String) extends Exception(message)
