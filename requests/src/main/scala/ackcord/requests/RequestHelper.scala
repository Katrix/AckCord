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

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

import ackcord.requests.RequestHelper.RequestProperties
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.OverflowStrategy
import akka.{Done, NotUsed}

/**
  * A class holding all the relevant information to create a request stream.
  * Also contains some convenience methods for common operations with requests.
  *
  * This should be instantiated once per bot, and shared between shards.
  *
  * @define backpressures Backpressures before it hits a ratelimit.
  * @param millisecondPrecision Sets if the requests should use millisecond
  *                             precision for the ratelimits. If using this,
  *                             make sure your system is properly synced to
  *                             an NTP server.
  * @param relativeTime Sets if the ratelimit reset should be calculated
  *                     using relative time instead of absolute time. Might
  *                     help with out of sync time on your device, but can
  *                     also lead to slightly slower processing of requests.
  */
case class RequestHelper(
    credentials: HttpCredentials,
    ratelimitActor: ActorRef,
    millisecondPrecision: Boolean = true,
    relativeTime: Boolean = false,
    parallelism: Int = 4,
    maxRetryCount: Int = 3,
    bufferSize: Int = 32,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
    maxAllowedWait: FiniteDuration = 2.minutes
)(implicit val system: ActorSystem) {

  private def ignoreOrReport[Ctx]: Sink[RequestAnswer[Any, Ctx], Future[Done]] = Sink.foreach {
    case _: RequestResponse[_, _] =>
    case request: FailedRequest[_] =>
      request.asException.printStackTrace()
  }

  private lazy val rawFlowWithoutRateLimits =
    RequestStreams.requestFlowWithoutRatelimit(
      credentials,
      millisecondPrecision,
      relativeTime,
      parallelism,
      ratelimitActor
    )

  /**
    * A basic request flow which will send requests to Discord, and
    * receive responses. Don't use this if you don't know what you're doing.
    */
  def flowWithoutRateLimits[Data, Ctx]: Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed] =
    rawFlowWithoutRateLimits.asInstanceOf[Flow[Request[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed]]

  private lazy val rawFlow =
    RequestStreams.requestFlow(
      credentials,
      millisecondPrecision,
      relativeTime,
      bufferSize,
      overflowStrategy,
      maxAllowedWait,
      parallelism,
      ratelimitActor
    )

  private lazy val rawOrderedFlow = RequestStreams.addOrdering(rawFlow)

  private lazy val rawRetryFlow = RequestStreams.retryRequestFlow(
    credentials,
    millisecondPrecision,
    relativeTime,
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
    flow[Data, Ctx](properties).toMat(ignoreOrReport)(Keep.right)

  /**
    * A generic flow for making requests. Only returns successful requests.
    * $backpressures
    */
  def flowSuccess[Data, Ctx](
      implicit properties: RequestProperties = RequestProperties.default
  ): Flow[Request[Data, Ctx], (Data, Ctx), NotUsed] =
    flow[Data, Ctx](properties)
      .map {
        case RequestResponse(data, ctx, _, _, _) =>
          Some(data -> ctx)
        case request: FailedRequest[_] =>
          request.asException.printStackTrace()
          None
      }
      .collect { case Some(value) => value }

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
  ): Unit =
    single(request)(properties).runWith(ignoreOrReport)

  /**
    * Sends many requests and ignores the result.
    * @param requests The requests to send.
    */
  def manyIgnore[Data, Ctx](requests: immutable.Seq[Request[Data, Ctx]])(
      implicit properties: RequestProperties = RequestProperties.default
  ): Unit = many(requests)(properties).runWith(ignoreOrReport)
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
      millisecondPrecision: Boolean = true,
      relativeTime: Boolean = false,
      parallelism: Int = 4,
      maxRetryCount: Int = 3,
      bufferSize: Int = 32,
      overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
      maxAllowedWait: FiniteDuration = 2.minutes
  )(implicit system: ActorSystem): RequestHelper =
    new RequestHelper(
      credentials,
      system.actorOf(Ratelimiter.props),
      millisecondPrecision,
      relativeTime,
      parallelism,
      maxRetryCount,
      bufferSize,
      overflowStrategy,
      maxAllowedWait
    )
}
