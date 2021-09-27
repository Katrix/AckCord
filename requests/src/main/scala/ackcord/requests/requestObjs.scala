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
import scala.concurrent.{ExecutionContext, Future}

import ackcord.CacheSnapshot
import ackcord.requests.Routes.{QueryRoute, Route}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._

/**
  * Used by requests for specifying an uri to send to, together with a method to
  * use.
  * @param uriWithMajor
  *   A string containing the route without any minor parameters filled in
  * @param uriWithoutMajor
  *   A string containing the route without any major or minor parameters filled
  *   in
  * @param uri
  *   The uri to send to
  * @param method
  *   The method to use
  */
case class RequestRoute(uriWithMajor: String, uriWithoutMajor: String, uri: Uri, method: HttpMethod)
object RequestRoute {

  /**
    * Create a [[RequestRoute]] from a [[Routes.Route]] using the raw and
    * applied values for the this route.
    */
  def apply(route: Route, method: HttpMethod): RequestRoute =
    RequestRoute(route.uriWithMajor, route.uriWithoutMajor, route.applied, method)

  /**
    * Create a [[RequestRoute]] from a [[Routes.QueryRoute]] using the raw and
    * applied values for the this route, and adding the query at the end.
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
  * Base super simple trait for all HTTP requests in AckCord.
  * @tparam Data
  *   The parsed response type.
  */
trait Request[+Data] { self =>

  /** An unique identifier to track this request from creation to answer. */
  val identifier: UUID = UUID.randomUUID()

  /** Returns the body of this Request for use in logging. */
  def bodyForLogging: Option[String]

  /** The router for this request. */
  def route: RequestRoute

  /** The body of the request to send. */
  def requestBody: RequestEntity

  /** All the extra headers to send with this request. */
  def extraHeaders: Seq[HttpHeader] = Nil

  /** A flow that can be used to parse the responses from this request. */
  def parseResponse(entity: ResponseEntity)(implicit system: ActorSystem[Nothing]): Future[Data]

  /** Transform the response of this request as a flow. */
  def transformResponse[B](
      f: ExecutionContext => Future[Data] => Future[B]
  ): Request[B] = new Request[B] {

    override val identifier: UUID = self.identifier

    override def route: RequestRoute = self.route

    override def requestBody: RequestEntity = self.requestBody

    override def bodyForLogging: Option[String] = self.bodyForLogging

    override def extraHeaders: Seq[HttpHeader] = self.extraHeaders

    override def parseResponse(entity: ResponseEntity)(implicit system: ActorSystem[Nothing]): Future[B] =
      f(system.executionContext)(self.parseResponse(entity))

    override def hasPermissions(implicit c: CacheSnapshot): Boolean = self.hasPermissions
  }

  /** Map the result of sending this request. */
  def map[B](f: Data => B): Request[B] = transformResponse(implicit ec => _.map(f))

  /** Filter the response of sending this request. */
  def filter(f: Data => Boolean): Request[Data] = transformResponse(implicit ec => _.filter(f))

  /** Map the result if the function is defined for the response data. */
  def collect[B](f: PartialFunction[Data, B]): Request[B] = transformResponse(implicit ec => _.collect(f))

  /** Check if a client has the needed permissions to execute this request. */
  def hasPermissions(implicit c: CacheSnapshot): Boolean
}

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

  /** Apply a function to this answer, if it's a successful response. */
  def map[B](f: Data => B): RequestAnswer[B]

  /**
    * Apply f if this is a successful response, and return this if the result is
    * true, else returns a failed answer.
    */
  def filter(f: Data => Boolean): RequestAnswer[Data]

  /** Apply f and returns the result if this is a successful response. */
  def flatMap[B](f: Data => RequestAnswer[B]): RequestAnswer[B]
}

/** A successful request response. */
case class RequestResponse[+Data](
    data: Data,
    ratelimitInfo: RatelimitInfo,
    route: RequestRoute,
    identifier: UUID
) extends RequestAnswer[Data] {

  override def eitherData: Either[Throwable, Data] = Right(data)

  override def map[B](f: Data => B): RequestResponse[B] = copy(data = f(data))

  override def filter(f: Data => Boolean): RequestAnswer[Data] =
    if (f(data)) this else RequestError(new NoSuchElementException("Predicate failed"), route, identifier)

  override def flatMap[B](f: Data => RequestAnswer[B]): RequestAnswer[B] = f(data)
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

/** A request that did not succeed because of a ratelimit. */
case class RequestRatelimited(
    global: Boolean,
    ratelimitInfo: RatelimitInfo,
    route: RequestRoute,
    identifier: UUID
) extends FailedRequest {

  override def asException: RatelimitException =
    RatelimitException(global, ratelimitInfo.tilReset, route.uri, identifier)

  override def map[B](f: Nothing => B): RequestRatelimited                    = this
  override def filter(f: Nothing => Boolean): RequestRatelimited              = this
  override def flatMap[B](f: Nothing => RequestAnswer[B]): RequestRatelimited = this
}

/** A request that failed for some other reason. */
case class RequestError(e: Throwable, route: RequestRoute, identifier: UUID) extends FailedRequest {
  override def asException: Throwable = e

  override def ratelimitInfo: RatelimitInfo = RatelimitInfo(-1.millis, -1, -1, "")

  override def map[B](f: Nothing => B): RequestError                    = this
  override def filter(f: Nothing => Boolean): RequestError              = this
  override def flatMap[B](f: Nothing => RequestAnswer[B]): RequestError = this
}

/**
  * A request that was dropped before it entered the network, most likely
  * because of timing out while waiting for ratelimits.
  */
case class RequestDropped(route: RequestRoute, identifier: UUID) extends FailedRequest {
  override def asException: DroppedRequestException = DroppedRequestException(route.uri)

  override def ratelimitInfo: RatelimitInfo = RatelimitInfo(-1.millis, -1, -1, "")

  override def map[B](f: Nothing => B): RequestDropped                    = this
  override def filter(f: Nothing => Boolean): RequestDropped              = this
  override def flatMap[B](f: Nothing => RequestAnswer[B]): RequestDropped = this
}
