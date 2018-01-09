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

import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethod, RequestEntity, ResponseEntity, Uri}
import akka.stream.scaladsl.Flow
import cats.CoflatMap

/**
  * Used by requests for specifying an uri to send to,
  * together with a method to use.
  * @param uri The uri to send to
  * @param method The method to use
  */
case class RequestRoute(uri: Uri, method: HttpMethod)

sealed trait MaybeRequest[+Data, Ctx] {

  def context: Ctx
}

/**
  * Base super simple trait for all HTTP requests in AckCord.
  * @tparam Data The parsed response type.
  */
trait Request[+Data, Ctx] extends MaybeRequest[Data, Ctx] { self =>

  /**
    * The context to send with this request.
    */
  def context: Ctx

  /**
    * Updates the context of this request.
    */
  def withContext[NewCtx](newContext: NewCtx): Request[Data, NewCtx] = new Request[Data, NewCtx] {

    override def context: NewCtx = newContext

    override def route: RequestRoute = self.route

    override def requestBody: RequestEntity = self.requestBody

    override def parseResponse(parallelism: Int)(implicit system: ActorSystem): Flow[ResponseEntity, Data, NotUsed] =
      self.parseResponse(parallelism)
  }

  /**
    * The router for this request.
    */
  def route: RequestRoute

  /**
    * The body of the request to send.
    */
  def requestBody: RequestEntity

  /**
    * A flow that can be used to parse the responses from this request.
    */
  def parseResponse(parallelism: Int)(implicit system: ActorSystem): Flow[ResponseEntity, Data, NotUsed]

  /**
    * Transform the response of this request given an execution context.
    */
  def transformResponse[B](
      f: Flow[ResponseEntity, Data, NotUsed] => Flow[ResponseEntity, B, NotUsed]
  ): Request[B, Ctx] = new Request[B, Ctx] {

    override def context: Ctx = self.context

    override def route: RequestRoute = self.route

    override def requestBody: RequestEntity = self.requestBody

    override def parseResponse(parallelism: Int)(implicit system: ActorSystem) = f(self.parseResponse(parallelism))
  }

  /**
    * Map the result of sending this request.
    */
  def map[B](f: Data => B): Request[B, Ctx] = transformResponse(_.map(f))

  /**
    * Filter the response of sending this request.
    */
  def filter(f: Data => Boolean): Request[Data, Ctx] = transformResponse(_.filter(f))

  /**
    * Map the result if the function is defined for the response data.
    */
  def collect[B](f: PartialFunction[Data, B]): Request[B, Ctx] = transformResponse(_.collect(f))
}
object Request {
  implicit def instance[Ctx]: CoflatMap[({ type L[A] = Request[A, Ctx] })#L] =
    new CoflatMap[({ type L[A] = Request[A, Ctx] })#L] {
      override def map[A, B](fa: Request[A, Ctx])(f: A => B):                     Request[B, Ctx] = fa.map(f)
      override def coflatMap[A, B](fa: Request[A, Ctx])(f: Request[A, Ctx] => B): Request[B, Ctx] = fa.map(_ => f(fa))
    }
}

/**
  * Sent as a response to a request.
  */
sealed trait RequestAnswer[+Data, Ctx] {

  /**
    * The context sent with this request.
    */
  def context: Ctx

  /**
    * Updates the context of this request response.
    */
  def withContext[NewCtx](context: NewCtx): RequestAnswer[Data, NewCtx]

  /**
    * The amount of time until this endpoint ratelimit is reset.
    * Minus if unknown.
    */
  def tilReset: FiniteDuration

  /**
    * The amount of requests that can be made until this endpoint is ratelimited.
    * -1 if unknown.
    */
  def remainingRequests: Int

  /**
    * The total amount of requests that can be sent to this to this endpoint
    * until a ratelimit kicks in.
    * -1 if unknown.
    */
  def uriRequestLimit: Int

  def uri: Uri

  /**
    * An option that contains the response data if this is a success, or None if it's a failure.
    */
  def optData: Option[Data]

  /**
    * Apply a function to this answer, if it's a successful response.
    */
  def map[B](f: Data => B): RequestAnswer[B, Ctx]

  /**
    * Apply f if this is a successful response, and return this if the result
    * is true, else returns a failed answer.
    */
  def filter(f: Data => Boolean): RequestAnswer[Data, Ctx]

  /**
    * Apply f and returns the result if this is a successful response.
    */
  def flatMap[B](f: Data => RequestAnswer[B, Ctx]): RequestAnswer[B, Ctx]
}

/**
  * A successful request response.
  */
case class RequestResponse[+Data, Ctx](
    data: Data,
    context: Ctx,
    remainingRequests: Int,
    tilReset: FiniteDuration,
    uriRequestLimit: Int,
    uri: Uri
) extends RequestAnswer[Data, Ctx] {

  override def withContext[NewCtx](context: NewCtx): RequestResponse[Data, NewCtx] = copy(context = context)

  override def optData:              Option[Data]            = Some(data)
  override def map[B](f: Data => B): RequestResponse[B, Ctx] = copy(data = f(data))
  override def filter(f: Data => Boolean): RequestAnswer[Data, Ctx] =
    if (f(data)) this else RequestError(context, new NoSuchElementException("Predicate failed"), uri)
  override def flatMap[B](f: Data => RequestAnswer[B, Ctx]): RequestAnswer[B, Ctx] = f(data)
}

/**
  * A failed request.
  */
sealed trait FailedRequest[Ctx] extends RequestAnswer[Nothing, Ctx] {

  def asException: Throwable
}

/**
  * A request that did not succeed because of a ratelimit.
  */
case class RequestRatelimited[Ctx](
    context: Ctx,
    global: Boolean,
    tilReset: FiniteDuration,
    uriRequestLimit: Int,
    uri: Uri
) extends FailedRequest[Ctx] {

  override def withContext[NewCtx](context: NewCtx): RequestRatelimited[NewCtx] = copy(context = context)

  override def remainingRequests: Int                = 0
  override def asException:       RatelimitException = new RatelimitException(global, tilReset, uri)

  override def optData:                                         None.type               = None
  override def map[B](f: Nothing => B):                         RequestRatelimited[Ctx] = this
  override def filter(f: Nothing => Boolean):                   RequestRatelimited[Ctx] = this
  override def flatMap[B](f: Nothing => RequestAnswer[B, Ctx]): RequestRatelimited[Ctx] = this
}

/**
  * A request that failed for some other reason.
  */
case class RequestError[Ctx](context: Ctx, e: Throwable, uri: Uri) extends FailedRequest[Ctx] {
  override def asException: Throwable = e

  override def withContext[NewCtx](context: NewCtx): RequestError[NewCtx] = copy(context = context)

  override def tilReset:          FiniteDuration = -1.millis
  override def remainingRequests: Int            = -1
  override def uriRequestLimit:   Int            = -1

  override def optData:                                         None.type         = None
  override def map[B](f: Nothing => B):                         RequestError[Ctx] = this
  override def filter(f: Nothing => Boolean):                   RequestError[Ctx] = this
  override def flatMap[B](f: Nothing => RequestAnswer[B, Ctx]): RequestError[Ctx] = this
}

case class RequestDropped[Ctx](context: Ctx, uri: Uri) extends MaybeRequest[Nothing, Ctx] with FailedRequest[Ctx] {
  override def asException = new DroppedRequestException(uri)

  override def withContext[NewCtx](context: NewCtx): RequestDropped[NewCtx] = copy(context = context)

  override def tilReset:          FiniteDuration = -1.millis
  override def remainingRequests: Int            = -1
  override def uriRequestLimit:   Int            = -1

  override def optData: None.type = None

  override def map[B](f: Nothing => B):                         RequestDropped[Ctx] = this
  override def filter(f: Nothing => Boolean):                   RequestDropped[Ctx] = this
  override def flatMap[B](f: Nothing => RequestAnswer[B, Ctx]): RequestDropped[Ctx] = this
}
