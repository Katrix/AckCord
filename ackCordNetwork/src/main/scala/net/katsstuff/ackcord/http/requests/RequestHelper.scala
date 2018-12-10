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

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.{Done, NotUsed}

/**
  * A class holding all the relevant information to create a request stream.
  * Also contains some convenience methods for common operations with requests.
  *
  * This should be instantiated once per bot, and shared between shards.
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

  /**
    * A generic flow for making requests. You should use this one most of
    * the time. Backpressures before it hits a ratelimit.
    */
  def flow[Data, Ctx]: Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed] =
    rawFlow.asInstanceOf[Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed]]

  /**
    * A generic sink for making requests and ignoring the results.
    * Backpressures before it hits a ratelimit.
    */
  def sinkIgnore[Data, Ctx]: Sink[Request[Data, Ctx], Future[Done]] =
    flow[Data, Ctx].toMat(Sink.ignore)(Keep.right)

  /**
    * A generic flow for making requests. Only returns successful requests.
    * Backpressures before it hits a ratelimit.
    */
  def flowSuccess[Data, Ctx]: Flow[Request[Data, Ctx], (Data, Ctx), NotUsed] =
    flow[Data, Ctx].collect {
      case RequestResponse(data, ctx, _, _, _, _, _) => data -> ctx
    }

  /**
    * Sends a single request.
    * @param request The request to send.
    * @return A source of the single request answer.
    */
  def single[Data, Ctx](request: Request[Data, Ctx]): Source[RequestAnswer[Data, Ctx], NotUsed] =
    Source.single(request).via(flow)

  /**
    * Sends many requests
    * @param requests The requests to send.
    * @return A source of the request answers.
    */
  def many[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]]): Source[RequestAnswer[Data, Ctx], NotUsed] =
    Source(requests).via(flow)

  /**
    * Sends a single request and gets the response as a future.
    * @param request The request to send.
    */
  def singleFuture[Data, Ctx](request: Request[Data, Ctx]): Future[RequestAnswer[Data, Ctx]] =
    single(request).runWith(Sink.head)

  /**
    * Sends many requests and gets the responses as a future.
    * @param requests The requests to send.
    */
  def manyFuture[Data, Ctx](
      requests: immutable.Seq[Request[Data, Ctx]]
  ): Future[immutable.Seq[RequestAnswer[Data, Ctx]]] =
    many(requests).runWith(Sink.seq)

  /**
    * Sends a single request and ignores the result.
    * @param request The request to send.
    */
  def singleIgnore[Data, Ctx](request: Request[Data, Ctx]): Unit =
    single(request).runWith(Sink.ignore)

  /**
    * Sends many requests and ignores the result.
    * @param requests The requests to send.
    */
  def manyIgnore[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]]): Unit =
    many(requests).runWith(Sink.ignore)

  private lazy val rawRetryFlow = RequestStreams.retryRequestFlow(
    credentials,
    bufferSize,
    overflowStrategy,
    maxAllowedWait,
    parallelism,
    maxRetryCount,
    ratelimitActor
  )

  /**
    * A request flow that will retry failed requests.
    */
  def retryFlow[Data, Ctx]: Flow[Request[Data, Ctx], RequestResponse[Data, Ctx], NotUsed] =
    rawRetryFlow.asInstanceOf[Flow[Request[Data, Ctx], RequestResponse[Data, Ctx], NotUsed]]

  /**
    * A sink for making requests and ignoring the results that will retry on error.
    * Backpressures before it hits a ratelimit.
    */
  def retrySinkIgnore[Data, Ctx]: Sink[Request[Data, Ctx], Future[Done]] =
    retryFlow[Data, Ctx].toMat(Sink.ignore)(Keep.right)

  @deprecated("Prefer singleRetry instead", since = "0.11.0")
  def retry[Data, Ctx](request: Request[Data, Ctx]): Source[RequestResponse[Data, Ctx], NotUsed] =
    singleRetry(request)

  /**
    * Sends a single request which will retry if it fails.
    *
    * NOTE: This Source sometimes stops working.
    * @param request The request to send.
    * @return A source of the retried request.
    */
  def singleRetry[Data, Ctx](request: Request[Data, Ctx]): Source[RequestResponse[Data, Ctx], NotUsed] =
    Source.single(request).via(retryFlow)

  /**
    * Sends many which will retry if they fail.
    *
    * @param requests The requests to send.
    * @return A source of the retried requests.
    */
  def manyRetry[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]]): Source[RequestResponse[Data, Ctx], NotUsed] =
    Source(requests).via(retryFlow)

  @deprecated("Prefer singleRetryFuture instead", since = "0.11.0")
  def retryFuture[Data, Ctx](request: Request[Data, Ctx]): Future[RequestResponse[Data, Ctx]] =
    singleRetryFuture(request)

  /**
    * Sends a single request with retries if it fails, and gets the response as a future.
    * @param request The request to send.
    */
  def singleRetryFuture[Data, Ctx](request: Request[Data, Ctx]): Future[RequestResponse[Data, Ctx]] =
    singleRetry(request).runWith(Sink.head)

  /**
    * Sends many requests with retries if they fail, and gets the response as a future.
    * @param requests The requests to send.
    */
  def manyRetryFuture[Data, Ctx](
      requests: immutable.Seq[Request[Data, Ctx]]
  ): Future[immutable.Seq[RequestResponse[Data, Ctx]]] =
    manyRetry(requests).runWith(Sink.seq)

  @deprecated("Prefer singleRetryIgnore instead", since = "0.11.0")
  def retryIgnore[Data, Ctx](request: Request[Data, Ctx]): Unit = singleRetryIgnore(request)

  /**
    * Sends a single request with retries if it fails, and ignores the result.
    * @param request The request to send.
    */
  def singleRetryIgnore[Data, Ctx](request: Request[Data, Ctx]): Unit = singleRetry(request).runWith(Sink.ignore)

  /**
    * Sends many requests with retries if they fail, and ignores the result.
    * @param requests The requests to send.
    */
  def manyRetryIgnore[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]]): Unit =
    manyRetry(requests).runWith(Sink.ignore)
}
object RequestHelper {

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
