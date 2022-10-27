package ackcord.requests.base

import java.util.UUID

import scala.concurrent.duration._

import ackcord.requests.base.ratelimiter.RatelimitInfo

/** Sent as a response to a request. */
sealed trait RequestAnswer[+Data] {

  /** An unique identifier to track this request from creation to answer. */
  def identifier: UUID

  /** Information about ratelimits gotten from this request. */
  def ratelimitInfo: RatelimitInfo

  /** The route for this request */
  def route: RequestRoute

  /**
    * An either that either contains the data, or the exception if this is a
    * failure.
    */
  def eitherData: Either[Throwable, Data]
}

/** A successful request response. */
case class RequestResponse[+Data](
    data: Data,
    ratelimitInfo: RatelimitInfo,
    route: RequestRoute,
    identifier: UUID
) extends RequestAnswer[Data] {

  override def eitherData: Either[Throwable, Data] = Right(data)
}

/** A failed request. */
sealed trait FailedRequest extends RequestAnswer[Nothing] {

  /**
    * Get the exception associated with this failed request, or makes one if one
    * does not exist.
    */
  def asException: Throwable

  override def eitherData: Either[Throwable, Nothing] = Left(asException)
}
object FailedRequest {

  /** A request that did not succeed because of a ratelimit. */
  case class RequestRatelimited(
      global: Boolean,
      ratelimitInfo: RatelimitInfo,
      route: RequestRoute,
      identifier: UUID
  ) extends FailedRequest {

    override def asException: RatelimitException =
      RatelimitException(global, ratelimitInfo.tilReset, route.uri, identifier)
  }

  /** A request that failed for some other reason. */
  case class RequestError(e: Throwable, route: RequestRoute, identifier: UUID) extends FailedRequest {
    override def asException: Throwable = e

    override def ratelimitInfo: RatelimitInfo = RatelimitInfo(-1.millis, -1, -1, "")
  }

  /**
    * A request that was dropped before it entered the network, most likely
    * because of timing out while waiting for ratelimits.
    */
  case class RequestDropped(route: RequestRoute, identifier: UUID) extends FailedRequest {
    override def asException: DroppedRequestException = DroppedRequestException(route.uri)

    override def ratelimitInfo: RatelimitInfo = RatelimitInfo(-1.millis, -1, -1, "")
  }
}
