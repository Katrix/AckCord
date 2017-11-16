/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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

import akka.actor.ActorRef

/**
  * Sent as a response from a [[RequestWrapper]].
  *
  * @tparam Data The received data type.
  * @tparam Ctx The context type.
  */
trait RequestAnswer[+Data, Ctx] {

  /**
    * The context sent with the request.
    */
  def context: Ctx

  /**
    * The amount of time in seconds until this endpoints ratelimit is reset.
    * -1 if not known.
    */
  def tilReset: Long

  /**
    * The amount of requests that can be made until this endpoint is ratelimited.
    * -1 if not known.
    */
  def remainingRequests: Int

  /**
    * Get the request wrapper representation that was used to sent this request.
    */
  def toWrapper: RequestWrapper[Data, Ctx]
}

/**
  * Trait for all successful requests.
  */
sealed trait SuccessfulRequest[+Data, Ctx] extends RequestAnswer[Data, Ctx]

/**
  * Sent when a [[RequestWrapper]] succeeds.
  *
  * @param data The received data.
  * @param remainingRequests The amount of requests that can be made on
  *                               this endpoint until a ratelimit. -1 if unknown.
  * @param context The context sent with the request.
  * @tparam Data The received data type.
  * @tparam Ctx The context type.
  */
case class RequestResponse[+Data, Ctx](
    data: Data,
    context: Ctx,
    remainingRequests: Int,
    tilReset: Long,
    toWrapper: RequestWrapper[Data, Ctx]
) extends SuccessfulRequest[Data, Ctx]

/**
  * Send when a [[RequestWrapper]] succeeds with no data.
  *
  * @param remainingRequests The amount of requests that can be made on
  *                               this endpoint until a ratelimit. -1 if unknown.
  * @param context The context sent with the request.
  * @tparam Ctx The context type.
  */
case class RequestResponseNoData[+Data, Ctx](
    context: Ctx,
    remainingRequests: Int,
    tilReset: Long,
    toWrapper: RequestWrapper[Data, Ctx]
) extends SuccessfulRequest[Data, Ctx]

/**
  * Trait for all the types of failed requests.
  */
sealed trait RequestFailed[+Data, Ctx] extends RequestAnswer[Data, Ctx] {
  def asException: Throwable
}

/**
  * Send if a request was rate limited.
  * @param tilReset The amount of milliseconds when the request can be retried. Will be -1 if not known.
  * @param global If the ratelimit is global.
  */
case class RequestRatelimited[+Data, Ctx](
    context: Ctx,
    tilReset: Long,
    global: Boolean,
    toWrapper: RequestWrapper[Data, Ctx]
) extends RequestFailed[Data, Ctx] {

  override def remainingRequests: Int                = 0
  override def asException:       RatelimitException = new RatelimitException(global, tilReset, toWrapper.request.route.uri)
}

/**
  * Sent when a [[RequestWrapper]] encounters an error.
  *
  * @param e The error that failed this request.
  * @param context The context sent with the request.
  * @tparam Ctx The context type.
  */
case class RequestError[+Data, Ctx](context: Ctx, e: Throwable, toWrapper: RequestWrapper[Data, Ctx])
    extends RequestFailed[Data, Ctx] {
  override def tilReset:          Long      = -1
  override def remainingRequests: Int       = -1
  override def asException:       Throwable = e
}

/**
  * Sent when a request was dropped before being sent because of ratelimits.
  *
  * @param context The data to send with the request.
  * @tparam Data The expected response type.
  * @tparam Ctx The context type.
  */
case class RequestDropped[+Data, Ctx](context: Ctx, toWrapper: RequestWrapper[Data, Ctx])
    extends RequestFailed[Data, Ctx]
    with SentRequest[Data, Ctx] {
  override def tilReset:          Long = -1
  override def remainingRequests: Int  = -1
  override def asException = new DroppedRequestException(toWrapper.request.route.uri)
}

sealed trait SentRequest[+Data, Ctx] {

  /**
    * Get the request wrapper representation of this request.
    */
  def toWrapper: RequestWrapper[Data, Ctx]
}

/**
  * Used to wrap a request in such a way that the handler know who to respond to.
  *
  * @param request The request object.
  * @param context The data to send with the request.
  * @param sendResponseTo The actor to send the reply to in the form of [[RequestAnswer]].
  * @tparam Data The expected response type.
  * @tparam Ctx The context type.
  */
case class RequestWrapper[+Data, Ctx](request: Request[Data], context: Ctx, sendResponseTo: ActorRef)
    extends SentRequest[Data, Ctx] {

  /**
    * Convert this request to a dropped request.
    */
  def toDropped:          RequestDropped[Data, Ctx] = RequestDropped(context, this)
  override def toWrapper: RequestWrapper[Data, Ctx] = this
}
