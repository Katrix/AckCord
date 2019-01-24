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
package net.katsstuff.ackcord.requests

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.{Done, NotUsed}
import net.katsstuff.ackcord.requests.RequestHelper.RequestProperties

/**
  * A class holding all the relevant information to create a request stream.
  * Also contains some convenience methods for common operations with requests.
  *
  * This should be instantiated once per bot, and shared between shards.
  *
  * @define backpressures Backpressures before it hits a ratelimit.
  */
case class RequestHelper(
    credentials: HttpCredentials,
    ratelimitActor: ActorRef,
    parallelism: Int = 4,
    maxRetryCount: Int = 3,
    bufferSize: Int = 32,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
    maxAllowedWait: FiniteDuration = 2.minutes
)(implicit val system: ActorSystem, val mat: Materializer) {

  private lazy val rawFlowWithoutRateLimits =
    RequestStreams.requestFlowWithoutRatelimit(credentials, parallelism, ratelimitActor)

  /**
    * A basic request flow which will send requests to Discord, and
    * receive responses. Don't use this if you don't know what you're doing.
    */
  def flowWithoutRateLimits[Data, Ctx]: Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed] =
    rawFlowWithoutRateLimits.asInstanceOf[Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed]]

  private lazy val rawFlow =
    RequestStreams.requestFlow(credentials, bufferSize, overflowStrategy, maxAllowedWait, parallelism, ratelimitActor)

  private lazy val rawOrderedFlow = RequestStreams.addOrdering(rawFlow)

  private lazy val rawRetryFlow = RequestStreams.retryRequestFlow(
    credentials,
    bufferSize,
    overflowStrategy,
    maxAllowedWait,
    parallelism,
    maxRetryCount,
    ratelimitActor
  )

  private lazy val rawOrderedRetryFlow = RequestStreams.addOrdering(rawRetryFlow)

  /**
    * A generic flow for making requests. You should use this one most of
    * the time. $backpressures
    *
    * Some info on ordered requests.
    * Once a response for one request has been received, the next one is sent.
    * The next one is sent regardless of if the previous request failed.
    * Ordered requests still have a few kinks that need to be removed before
    * they always work.
    */
  def flow[Data, Ctx](
      implicit properties: RequestProperties = RequestProperties.default
  ): Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed] = {
    val flowToUse = properties match {
      case RequestProperties(false, false) => rawFlow
      case RequestProperties(true, false)  => rawRetryFlow
      case RequestProperties(false, true)  => rawOrderedFlow
      case RequestProperties(true, true)   => rawOrderedRetryFlow
    }

    flowToUse.asInstanceOf[Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed]]
  }

  /**
    * A generic sink for making requests and ignoring the results.
    * $backpressures
    */
  def sinkIgnore[Data, Ctx](
      implicit properties: RequestProperties = RequestProperties.default
  ): Sink[Request[Data, Ctx], Future[Done]] =
    flow[Data, Ctx](properties).toMat(Sink.ignore)(Keep.right)

  /**
    * A generic flow for making requests. Only returns successful requests.
    * $backpressures
    */
  def flowSuccess[Data, Ctx](
      implicit properties: RequestProperties = RequestProperties.default
  ): Flow[Request[Data, Ctx], (Data, Ctx), NotUsed] =
    flow[Data, Ctx](properties).collect {
      case RequestResponse(data, ctx, _, _, _, _, _) => data -> ctx
    }

  /**
    * Sends a single request.
    * @param request The request to send.
    * @return A source of the single request answer.
    */
  def single[Data, Ctx](request: Request[Data, Ctx])(
      implicit properties: RequestProperties = RequestProperties.default
  ): Source[RequestAnswer[Data, Ctx], NotUsed] = Source.single(request).via(flow(properties))

  /**
    * Sends many requests
    * @param requests The requests to send.
    * @return A source of the request answers.
    */
  def many[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]])(
      implicit properties: RequestProperties = RequestProperties.default
  ): Source[RequestAnswer[Data, Ctx], NotUsed] =
    Source(requests).via(flow(properties))

  /**
    * Sends a single request and gets the response as a future.
    * @param request The request to send.
    */
  def singleFuture[Data, Ctx](request: Request[Data, Ctx])(
      implicit properties: RequestProperties = RequestProperties.default
  ): Future[RequestAnswer[Data, Ctx]] =
    single(request)(properties).runWith(Sink.head)

  /**
    * Sends many requests and gets the responses as a future.
    * @param requests The requests to send.
    */
  def manyFuture[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]])(
      implicit properties: RequestProperties = RequestProperties.default
  ): Future[immutable.Seq[RequestAnswer[Data, Ctx]]] =
    many(requests)(properties).runWith(Sink.seq)

  /**
    * Sends a single request and ignores the result.
    * @param request The request to send.
    */
  def singleIgnore[Data, Ctx](request: Request[Data, Ctx])(
      implicit properties: RequestProperties = RequestProperties.default
  ): Unit = single(request)(properties).runWith(Sink.ignore)

  /**
    * Sends many requests and ignores the result.
    * @param requests The requests to send.
    */
  def manyIgnore[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]])(
      implicit properties: RequestProperties = RequestProperties.default
  ): Unit = many(requests)(properties).runWith(Sink.ignore)

  /**
    * A request flow that will retry failed requests. $backpressures
    */
  @deprecated("Prefer flow with a custom RequestProperties", since = "0.12.0")
  def retryFlow[Data, Ctx]: Flow[Request[Data, Ctx], RequestResponse[Data, Ctx], NotUsed] =
    rawRetryFlow.asInstanceOf[Flow[Request[Data, Ctx], RequestResponse[Data, Ctx], NotUsed]]

  /**
    * A sink for making requests and ignoring the results that will retry on error.
    * $backpressures
    */
  @deprecated("Prefer sinkIgnore with a custom RequestProperties", since = "0.12.0")
  def retrySinkIgnore[Data, Ctx]: Sink[Request[Data, Ctx], Future[Done]] =
    retryFlow[Data, Ctx].toMat(Sink.ignore)(Keep.right)

  /**
    * Sends a single request which will retry if it fails.
    *
    * NOTE: This Source sometimes stops working.
    * @param request The request to send.
    * @return A source of the retried request.
    */
  @deprecated("Prefer single with a custom RequestProperties", since = "0.12.0")
  def singleRetry[Data, Ctx](request: Request[Data, Ctx]): Source[RequestResponse[Data, Ctx], NotUsed] =
    Source.single(request).via(retryFlow)

  /**
    * Sends many requests which will retry if they fail.
    *
    * @param requests The requests to send.
    * @return A source of the retried requests.
    */
  @deprecated("Prefer many with a custom RequestProperties", since = "0.12.0")
  def manyRetry[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]]): Source[RequestResponse[Data, Ctx], NotUsed] =
    Source(requests).via(retryFlow)

  /**
    * Sends a single request with retries if it fails, and gets the response as a future.
    * @param request The request to send.
    */
  @deprecated("Prefer singleFuture with a custom RequestProperties", since = "0.12.0")
  def singleRetryFuture[Data, Ctx](request: Request[Data, Ctx]): Future[RequestResponse[Data, Ctx]] =
    singleRetry(request).runWith(Sink.head)

  /**
    * Sends many requests with retries if they fail, and gets the response as a future.
    * @param requests The requests to send.
    */
  @deprecated("Prefer manyFuture with a custom RequestProperties", since = "0.12.0")
  def manyRetryFuture[Data, Ctx](
      requests: immutable.Seq[Request[Data, Ctx]]
  ): Future[immutable.Seq[RequestResponse[Data, Ctx]]] =
    manyRetry(requests).runWith(Sink.seq)

  /**
    * Sends a single request with retries if it fails, and ignores the result.
    * @param request The request to send.
    */
  @deprecated("Prefer singleIgnore with a custom RequestProperties", since = "0.12.0")
  def singleRetryIgnore[Data, Ctx](request: Request[Data, Ctx]): Unit = singleRetry(request).runWith(Sink.ignore)

  /**
    * Sends many requests with retries if they fail, and ignores the result.
    * @param requests The requests to send.
    */
  @deprecated("Prefer manyIgnore with a custom RequestProperties", since = "0.12.0")
  def manyRetryIgnore[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]]): Unit =
    manyRetry(requests).runWith(Sink.ignore)
}
object RequestHelper {

  case class RequestProperties(
      retry: Boolean = false,
      ordered: Boolean = false
  )
  object RequestProperties {
    val default      = RequestProperties()
    val retry        = RequestProperties(retry = true)
    val ordered      = RequestProperties(ordered = true)
    val retryOrdered = RequestProperties(retry = true, ordered = true)

    object Implicits {
      implicit val default: RequestProperties      = RequestProperties.default
      implicit val retry: RequestProperties        = RequestProperties.retry
      implicit val ordered: RequestProperties      = RequestProperties.ordered
      implicit val retryOrdered: RequestProperties = RequestProperties.retryOrdered
    }
  }

  def create(
      credentials: HttpCredentials,
      parallelism: Int = 4,
      maxRetryCount: Int = 3,
      bufferSize: Int = 32,
      overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
      maxAllowedWait: FiniteDuration = 2.minutes
  )(implicit system: ActorSystem, mat: Materializer): RequestHelper =
    new RequestHelper(
      credentials,
      system.actorOf(Ratelimiter.props),
      parallelism,
      maxRetryCount,
      bufferSize,
      overflowStrategy,
      maxAllowedWait
    )
}
