/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package ackcord.requests

import java.time.Instant

import scala.concurrent.duration._
import scala.util.{Success, Try}

import akka.http.scaladsl.model.headers.{GenericHttpCredentials, ModeledCustomHeader, ModeledCustomHeaderCompanion}

object BotAuthentication {
  def apply(token: String): GenericHttpCredentials = GenericHttpCredentials("Bot", token)
}

final class `X-RateLimit-Remaining`(val remaining: Int) extends ModeledCustomHeader[`X-RateLimit-Remaining`] {
  override def companion: ModeledCustomHeaderCompanion[`X-RateLimit-Remaining`] = `X-RateLimit-Remaining`

  override def value: String              = remaining.toString
  override def renderInRequests: Boolean  = false
  override def renderInResponses: Boolean = true
}
object `X-RateLimit-Remaining` extends ModeledCustomHeaderCompanion[`X-RateLimit-Remaining`] {
  override def name: String                                       = "X-RateLimit-Remaining"
  override def parse(value: String): Try[`X-RateLimit-Remaining`] = Try(new `X-RateLimit-Remaining`(value.toInt))
}

final class `X-RateLimit-Reset`(val resetAt: Instant) extends ModeledCustomHeader[`X-RateLimit-Reset`] {
  override def companion: ModeledCustomHeaderCompanion[`X-RateLimit-Reset`] = `X-RateLimit-Reset`

  override def value: String              = resetAt.getEpochSecond.toString
  override def renderInRequests: Boolean  = false
  override def renderInResponses: Boolean = true
}
object `X-RateLimit-Reset` extends ModeledCustomHeaderCompanion[`X-RateLimit-Reset`] {
  override def name: String = "X-RateLimit-Reset"
  override def parse(value: String): Try[`X-RateLimit-Reset`] =
    Try(new `X-RateLimit-Reset`(Instant.ofEpochMilli((value.toDouble * 1000).toLong)))
}
final class `X-RateLimit-Reset-After`(val resetIn: FiniteDuration)
    extends ModeledCustomHeader[`X-RateLimit-Reset-After`] {
  override def companion: ModeledCustomHeaderCompanion[`X-RateLimit-Reset-After`] = `X-RateLimit-Reset-After`

  override def value: String              = (resetIn.toMillis.toDouble / 1000).toString
  override def renderInRequests: Boolean  = false
  override def renderInResponses: Boolean = true
}
object `X-RateLimit-Reset-After` extends ModeledCustomHeaderCompanion[`X-RateLimit-Reset-After`] {
  override def name: String = "X-RateLimit-Reset-After"
  override def parse(value: String): Try[`X-RateLimit-Reset-After`] =
    Try(new `X-RateLimit-Reset-After`(value.toDouble.millis))
}

final class `X-RateLimit-Bucket`(val identifier: String) extends ModeledCustomHeader[`X-RateLimit-Bucket`] {
  override def companion: ModeledCustomHeaderCompanion[`X-RateLimit-Bucket`] = `X-RateLimit-Bucket`

  override def value: String              = identifier
  override def renderInRequests: Boolean  = false
  override def renderInResponses: Boolean = true
}
object `X-RateLimit-Bucket` extends ModeledCustomHeaderCompanion[`X-RateLimit-Bucket`] {
  override def name: String = "X-RateLimit-Bucket"

  override def parse(value: String): Try[`X-RateLimit-Bucket`] = Success(new `X-RateLimit-Bucket`(value))
}

final class `X-Ratelimit-Global`(val isGlobal: Boolean) extends ModeledCustomHeader[`X-Ratelimit-Global`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Ratelimit-Global`] = `X-Ratelimit-Global`

  override def value: String              = isGlobal.toString
  override def renderInRequests: Boolean  = false
  override def renderInResponses: Boolean = true
}
object `X-Ratelimit-Global` extends ModeledCustomHeaderCompanion[`X-Ratelimit-Global`] {
  override def name: String         = "X-Ratelimit-Global"
  override def parse(value: String) = Try(new `X-Ratelimit-Global`(value.toBoolean))
}

final class `X-RateLimit-Limit`(val limit: Int) extends ModeledCustomHeader[`X-RateLimit-Limit`] {
  override def companion: ModeledCustomHeaderCompanion[`X-RateLimit-Limit`] = `X-RateLimit-Limit`

  override def value: String              = limit.toString
  override def renderInRequests: Boolean  = false
  override def renderInResponses: Boolean = true
}
object `X-RateLimit-Limit` extends ModeledCustomHeaderCompanion[`X-RateLimit-Limit`] {
  override def name: String                                   = "X-RateLimit-Limit"
  override def parse(value: String): Try[`X-RateLimit-Limit`] = Try(new `X-RateLimit-Limit`(value.toInt))
}

final class `Retry-After`(val tilReset: FiniteDuration) extends ModeledCustomHeader[`Retry-After`] {
  override def companion: ModeledCustomHeaderCompanion[`Retry-After`] = `Retry-After`

  override def value: String     = tilReset.toMillis.toString
  override def renderInRequests  = false
  override def renderInResponses = true
}
object `Retry-After` extends ModeledCustomHeaderCompanion[`Retry-After`] {
  override def name: String                             = "Retry-After"
  override def parse(value: String): Try[`Retry-After`] = Try(new `Retry-After`(value.toLong.seconds))
}

final class `X-Audit-Log-Reason`(val reason: String) extends ModeledCustomHeader[`X-Audit-Log-Reason`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Audit-Log-Reason`] = `X-Audit-Log-Reason`

  override def value: String     = reason
  override def renderInRequests  = false
  override def renderInResponses = true
}
object `X-Audit-Log-Reason` extends ModeledCustomHeaderCompanion[`X-Audit-Log-Reason`] {
  override def name: String                                    = "X-Audit-Log-Reason"
  override def parse(value: String): Try[`X-Audit-Log-Reason`] = Success(new `X-Audit-Log-Reason`(value))
}
