package ackcord.requests.base.ratelimiter

import scala.concurrent.duration._

/**
  * Misc info needed to handle ratelimits correctly.
  *
  * @param tilReset
  *   The amount of time until this endpoint ratelimit is reset. Minus if
  *   unknown.
  * @param tilRatelimit
  *   The amount of requests that can be made until this endpoint is
  *   ratelimited. -1 if unknown.
  * @param bucketLimit
  *   The total amount of requests that can be sent to this to this endpoint
  *   until a ratelimit kicks in.
  * -1 if unknown.
  * @param bucket
  *   The ratelimit bucket for this request. Does not include any parameters.
  */
case class RatelimitInfo(
    tilReset: FiniteDuration,
    tilRatelimit: Int,
    bucketLimit: Int,
    bucket: String
) {

  /**
    * Returns if this ratelimit info does not contain any unknown placeholders.
    */
  def isValid: Boolean = tilReset > 0.millis && tilRatelimit != -1 && bucketLimit != -1
}
