/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord.http.requests

import java.time.Instant

import scala.concurrent.duration._
import scala.util.Try

import akka.http.scaladsl.model.headers.{GenericHttpCredentials, ModeledCustomHeader, ModeledCustomHeaderCompanion}

object BotAuthentication {
  def apply(token: String): GenericHttpCredentials = GenericHttpCredentials("Bot", token)
}

final class `X-RateLimit-Remaining`(val remaining: Int) extends ModeledCustomHeader[`X-RateLimit-Remaining`] {
  override def companion: ModeledCustomHeaderCompanion[`X-RateLimit-Remaining`] = `X-RateLimit-Remaining`

  override def value:             String  = remaining.toString
  override def renderInRequests:  Boolean = false
  override def renderInResponses: Boolean = true
}
object `X-RateLimit-Remaining` extends ModeledCustomHeaderCompanion[`X-RateLimit-Remaining`] {
  def apply(remaining: Int):         `X-RateLimit-Remaining`      = new `X-RateLimit-Remaining`(remaining)
  override def name:                 String                       = "X-RateLimit-Remaining"
  override def parse(value: String): Try[`X-RateLimit-Remaining`] = Try(new `X-RateLimit-Remaining`(value.toInt))
}

final class `X-RateLimit-Reset`(val resetAt: Instant) extends ModeledCustomHeader[`X-RateLimit-Reset`] {
  override def companion: ModeledCustomHeaderCompanion[`X-RateLimit-Reset`] = `X-RateLimit-Reset`

  override def value:             String  = resetAt.getEpochSecond.toString
  override def renderInRequests:  Boolean = false
  override def renderInResponses: Boolean = true
}
object `X-RateLimit-Reset` extends ModeledCustomHeaderCompanion[`X-RateLimit-Reset`] {
  def apply(tilReset: Instant): `X-RateLimit-Reset` = new `X-RateLimit-Reset`(tilReset)
  override def name:            String              = "X-RateLimit-Reset"
  override def parse(value: String): Try[`X-RateLimit-Reset`] =
    Try(new `X-RateLimit-Reset`(Instant.ofEpochSecond(value.toLong)))
}

final class `X-Ratelimit-Global`(val isGlobal: Boolean) extends ModeledCustomHeader[`X-Ratelimit-Global`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Ratelimit-Global`] = `X-Ratelimit-Global`

  override def value:             String  = isGlobal.toString
  override def renderInRequests:  Boolean = false
  override def renderInResponses: Boolean = true
}
object `X-Ratelimit-Global` extends ModeledCustomHeaderCompanion[`X-Ratelimit-Global`] {
  def apply(isGlobal: Boolean): `X-Ratelimit-Global` = new X$minusRatelimit$minusGlobal(isGlobal)
  override def name:            String               = "X-Ratelimit-Global"
  override def parse(value: String) = Try(new `X-Ratelimit-Global`(value.toBoolean))
}

final class `X-RateLimit-Limit`(val limit: Int) extends ModeledCustomHeader[`X-RateLimit-Limit`] {
  override def companion: ModeledCustomHeaderCompanion[`X-RateLimit-Limit`] = `X-RateLimit-Limit`

  override def value:             String  = limit.toString
  override def renderInRequests:  Boolean = false
  override def renderInResponses: Boolean = true
}
object `X-RateLimit-Limit` extends ModeledCustomHeaderCompanion[`X-RateLimit-Limit`] {
  def apply(limit: Int):             `X-RateLimit-Limit`      = new `X-RateLimit-Limit`(limit)
  override def name:                 String                   = "X-RateLimit-Limit"
  override def parse(value: String): Try[`X-RateLimit-Limit`] = Try(new `X-RateLimit-Limit`(value.toInt))
}

final class `Retry-After`(val tilReset: FiniteDuration) extends ModeledCustomHeader[`Retry-After`] {
  override def companion: ModeledCustomHeaderCompanion[`Retry-After`] = `Retry-After`

  override def value: String = tilReset.toMillis.toString
  override def renderInRequests()  = false
  override def renderInResponses() = true
}
object `Retry-After` extends ModeledCustomHeaderCompanion[`Retry-After`] {
  override def name:                 String             = "Retry-After"
  override def parse(value: String): Try[`Retry-After`] = Try(new `Retry-After`(value.toLong.millis))
}

final class `X-Audit-Log-Reason`(val reason: String) extends ModeledCustomHeader[`X-Audit-Log-Reason`] {
  override def companion: ModeledCustomHeaderCompanion[`X-Audit-Log-Reason`] = `X-Audit-Log-Reason`

  override def value: String = reason
  override def renderInRequests()  = false
  override def renderInResponses() = true
}
object `X-Audit-Log-Reason` extends ModeledCustomHeaderCompanion[`X-Audit-Log-Reason`] {
  override def name:                 String                    = "Retry-After"
  override def parse(value: String): Try[`X-Audit-Log-Reason`] = Try(new `X-Audit-Log-Reason`(value))
}
