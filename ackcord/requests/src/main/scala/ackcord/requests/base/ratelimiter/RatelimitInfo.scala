package ackcord.requests.base.ratelimiter

/**
  * Misc info needed to handle ratelimits correctly.
  *
  * @param resetAt
  *   Time in epoch millis when this endpoint ratelimit is reset. Minus if
  *   unknown.
  * @param retryAt
  *   Time in epoch millis when this request (or another one sharing the same
  *   bucket) can be retried.
  * @param tilRatelimit
  *   The amount of requests that can be made until this endpoint is
  *   ratelimited. -1 if unknown.
  * @param bucketLimit
  *   The total amount of requests that can be sent to this to this endpoint
  *   until a ratelimit kicks in.
  * -1 if unknown.
  * @param bucket
  *   The ratelimit bucket for this request. Does not include any parameters.
  * @param isGlobal
  *   Indicates if the ratelimit is global.
  */
case class RatelimitInfo(
    resetAt: Long,
    retryAt: Long,
    tilRatelimit: Int,
    bucketLimit: Int,
    bucket: String,
    isGlobal: Boolean
) {

  /**
    * Returns if this ratelimit info does not contain any unknown placeholders.
    */
  def isValid: Boolean = resetAt > 0 && tilRatelimit != -1 && bucketLimit != -1
}
