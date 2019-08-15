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

import scala.concurrent.duration._

import ackcord.CacheSnapshot
import ackcord.requests.Routes.{QueryRoute, Route}
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Flow

/**
  * Used by requests for specifying an uri to send to,
  * together with a method to use.
  * @param uriWithMajor A string containing the route without any minor parameters filled in
  * @param uriWithoutMajor A string containing the route without any major or minor parameters filled in
  * @param uri The uri to send to
  * @param method The method to use
  */
case class RequestRoute(uriWithMajor: String, uriWithoutMajor: String, uri: Uri, method: HttpMethod) {

  @deprecated("Prefer uriWithMajor", since = "0.15.0")
  def rawRoute: String = uriWithMajor
}
object RequestRoute {

  /**
    * Create a [[RequestRoute]] from a [[Routes.Route]] using the raw and applied
    * values for the this route.
    */
  def apply(route: Route, method: HttpMethod): RequestRoute =
    RequestRoute(route.uriWithMajor, route.uriWithoutMajor, route.applied, method)

  /**
    * Create a [[RequestRoute]] from a [[Routes.QueryRoute]] using the raw and applied
    * values for the this route, and adding the query at the end.
    */
  def apply(queryRoute: QueryRoute, method: HttpMethod): RequestRoute =
    RequestRoute(
      queryRoute.uriWithMajor,
      queryRoute.uriWithoutMajor,
      queryRoute.applied.withQuery(Uri.Query(queryRoute.queryParts: _*)),
      method
    )
}

/**
  * Base trait for all requests before they enter the network flow.
  * @tparam Data The response type for the request.
  * @tparam Ctx The type of the context to send with this request.
  */
sealed trait MaybeRequest[+Data, Ctx] {

  /**
    * The context to send with this request.
    */
  def context: Ctx
}

/**
  * Base super simple trait for all HTTP requests in AckCord.
  * @tparam Data The parsed response type.
  */
trait Request[+Data, Ctx] extends MaybeRequest[Data, Ctx] { self =>

  /**
    * An unique identifier to track this request from creation to answer.
    */
  val identifier: UUID = UUID.randomUUID()

  /**
    * Updates the context of this request.
    */
  def withContext[NewCtx](newContext: NewCtx): Request[Data, NewCtx] = new Request[Data, NewCtx] {

    override val identifier: UUID = self.identifier

    override def context: NewCtx = newContext

    override def route: RequestRoute = self.route

    override def requestBody: RequestEntity = self.requestBody

    override def bodyForLogging: Option[String] = self.bodyForLogging

    override def extraHeaders: Seq[HttpHeader] = self.extraHeaders

    override def parseResponse(parallelism: Int)(implicit system: ActorSystem): Flow[ResponseEntity, Data, NotUsed] =
      self.parseResponse(parallelism)

    override def hasPermissions(implicit c: CacheSnapshot): Boolean = self.hasPermissions
  }

  /**
    * Returns the body of this Request for use in logging.
    */
  def bodyForLogging: Option[String]

  /**
    * The router for this request.
    */
  def route: RequestRoute

  /**
    * The body of the request to send.
    */
  def requestBody: RequestEntity

  /**
    * All the extra headers to send with this request.
    */
  def extraHeaders: Seq[HttpHeader] = Nil

  /**
    * A flow that can be used to parse the responses from this request.
    */
  def parseResponse(parallelism: Int)(implicit system: ActorSystem): Flow[ResponseEntity, Data, NotUsed]

  /**
    * Transform the response of this request as a flow.
    */
  def transformResponse[B](
      f: Flow[ResponseEntity, Data, NotUsed] => Flow[ResponseEntity, B, NotUsed]
  ): Request[B, Ctx] = new Request[B, Ctx] {

    override val identifier: UUID = self.identifier

    override def context: Ctx = self.context

    override def route: RequestRoute = self.route

    override def requestBody: RequestEntity = self.requestBody

    override def bodyForLogging: Option[String] = self.bodyForLogging

    override def extraHeaders: Seq[HttpHeader] = self.extraHeaders

    override def parseResponse(parallelism: Int)(implicit system: ActorSystem): Flow[ResponseEntity, B, NotUsed] =
      f(self.parseResponse(parallelism))

    override def hasPermissions(implicit c: CacheSnapshot): Boolean = self.hasPermissions
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

  /**
    * Check if a client has the needed permissions to execute this request.
    */
  def hasPermissions(implicit c: CacheSnapshot): Boolean
}

/**
  * Sent as a response to a request.
  */
sealed trait RequestAnswer[+Data, Ctx] {

  /**
    * An unique identifier to track this request from creation to answer.
    */
  def identifier: UUID

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

  /**
    * The route for this request
    */
  def route: RequestRoute

  /**
    * The uri for this request.
    */
  @deprecated("Prefer route.uri", since = "0.15.0")
  def uri: Uri = route.uri

  @deprecated("Prefer route.uriWithMajor", since = "0.15.0")
  def rawRoute: String = route.uriWithMajor

  /**
    * The ratelimit bucket for this request. Does not include any parameters.
    */
  def ratelimitBucket: String

  /**
    * An either that either contains the data, or the exception if this is a failure.
    */
  def eitherData: Either[Throwable, Data]

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
    route: RequestRoute,
    ratelimitBucket: String,
    identifier: UUID
) extends RequestAnswer[Data, Ctx] {

  override def withContext[NewCtx](context: NewCtx): RequestResponse[Data, NewCtx] = copy(context = context)

  override def eitherData: Either[Throwable, Data] = Right(data)

  override def map[B](f: Data => B): RequestResponse[B, Ctx] = copy(data = f(data))

  override def filter(f: Data => Boolean): RequestAnswer[Data, Ctx] =
    if (f(data)) this else RequestError(context, new NoSuchElementException("Predicate failed"), route, identifier)

  override def flatMap[B](f: Data => RequestAnswer[B, Ctx]): RequestAnswer[B, Ctx] = f(data)
}

/**
  * A failed request.
  */
sealed trait FailedRequest[Ctx] extends RequestAnswer[Nothing, Ctx] {

  /**
    * Get the exception associated with this failed request, or makes one
    * if one does not exist.
    */
  def asException: Throwable

  override def eitherData: Either[Throwable, Nothing] = Left(asException)
}

/**
  * A request that did not succeed because of a ratelimit.
  */
case class RequestRatelimited[Ctx](
    context: Ctx,
    global: Boolean,
    tilReset: FiniteDuration,
    uriRequestLimit: Int,
    route: RequestRoute,
    ratelimitBucket: String,
    identifier: UUID
) extends FailedRequest[Ctx] {

  override def withContext[NewCtx](context: NewCtx): RequestRatelimited[NewCtx] = copy(context = context)

  override def remainingRequests: Int          = 0
  override def asException: RatelimitException = new RatelimitException(global, tilReset, route.uri, identifier)

  override def map[B](f: Nothing => B): RequestRatelimited[Ctx]                         = this
  override def filter(f: Nothing => Boolean): RequestRatelimited[Ctx]                   = this
  override def flatMap[B](f: Nothing => RequestAnswer[B, Ctx]): RequestRatelimited[Ctx] = this
}

/**
  * A request that failed for some other reason.
  */
case class RequestError[Ctx](context: Ctx, e: Throwable, route: RequestRoute, identifier: UUID)
    extends FailedRequest[Ctx] {
  override def asException: Throwable = e

  override def withContext[NewCtx](context: NewCtx): RequestError[NewCtx] = copy(context = context)

  override def tilReset: FiniteDuration = -1.millis
  override def remainingRequests: Int   = -1
  override def uriRequestLimit: Int     = -1
  override def ratelimitBucket: String  = ""

  override def map[B](f: Nothing => B): RequestError[Ctx]                         = this
  override def filter(f: Nothing => Boolean): RequestError[Ctx]                   = this
  override def flatMap[B](f: Nothing => RequestAnswer[B, Ctx]): RequestError[Ctx] = this
}

/**
  * A request that was dropped before it entered the network, most likely
  * because of timing out while waiting for ratelimits.
  */
case class RequestDropped[Ctx](context: Ctx, route: RequestRoute, identifier: UUID)
    extends MaybeRequest[Nothing, Ctx]
    with FailedRequest[Ctx] {
  override def asException = new DroppedRequestException(route.uri)

  override def withContext[NewCtx](context: NewCtx): RequestDropped[NewCtx] = copy(context = context)

  override def tilReset: FiniteDuration = -1.millis
  override def remainingRequests: Int   = -1
  override def uriRequestLimit: Int     = -1
  override def ratelimitBucket: String  = ""

  override def map[B](f: Nothing => B): RequestDropped[Ctx]                         = this
  override def filter(f: Nothing => Boolean): RequestDropped[Ctx]                   = this
  override def flatMap[B](f: Nothing => RequestAnswer[B, Ctx]): RequestDropped[Ctx] = this
}
