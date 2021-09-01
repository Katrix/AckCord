/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 Katrix
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

import akka.http.scaladsl.model.headers.{HttpCredentials, `User-Agent`}
import akka.stream.OverflowStrategy

/**
  * @param credentials The credentials to use when sending the requests.
  * @param ratelimiter The object to use to track ratelimits and such
  * @param relativeTime Sets if the ratelimit reset should be calculated
  *                     using relative time instead of absolute time. Might
  *                     help with out of sync time on your device, but can
  *                     also lead to slightly slower processing of requests.
  * @param parallelism How many requests to try to process at once.
  * @param maxRetryCount How many times to retry requests if an error occurs
  *                      on retry flows
  * @param bufferSize The size of the internal buffer used before messages
  *                   are sent.
  * @param overflowStrategy The strategy to use if the buffer overflows.
  */
case class RequestSettings(
    credentials: Option[HttpCredentials],
    ratelimiter: Ratelimiter,
    relativeTime: Boolean = false,
    parallelism: Int = 4,
    maxRetryCount: Int = 3,
    bufferSize: Int = 32,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
    userAgent: `User-Agent` = RequestStreams.defaultUserAgent
)
