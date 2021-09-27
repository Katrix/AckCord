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

import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import akka.http.scaladsl.model.{HttpMethod, StatusCode, Uri}

/** An exception for Http errors. */
case class HttpException(
    uri: Uri,
    method: HttpMethod,
    statusCode: StatusCode,
    extraInfo: Option[String]
) extends Exception(
      s"$method $uri: ${statusCode.intValue()}, ${statusCode
        .reason()}${extraInfo.fold("")(e => s" $e")}"
    )

/**
  * An exception that signals than an endpoint is ratelimited.
  *
  * @param global
  *   If the rate limit is global.
  * @param tilRetry
  *   The amount of time in milliseconds until the ratelimit goes away.
  * @param uri
  *   The Uri for the request.
  */
case class RatelimitException(
    global: Boolean,
    tilRetry: FiniteDuration,
    uri: Uri,
    identifier: UUID
) extends Exception(
      if (global) "Encountered global ratelimit"
      else s"Encountered ratelimit at $uri $identifier"
    )

/**
  * An exception that signals that a request was dropped.
  * @param uri
  *   The Uri for the request.
  */
case class DroppedRequestException(uri: Uri)
    extends Exception(s"Dropped request at $uri")

/** An exception thrown when parsing JSON if something goes wrong. */
case class HttpJsonDecodeException(message: String) extends Exception(message)
