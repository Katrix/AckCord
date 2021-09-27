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

import scala.language.existentials

import scala.collection.immutable
import scala.concurrent.Future

import ackcord.requests.Requests.RequestProperties
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{FlowWithContext, Keep, Sink, Source}
import akka.{Done, NotUsed}

/**
  * A class holding all the relevant information to create a request stream.
  * Also contains some convenience methods for common operations with requests.
  *
  * This should be instantiated once per bot, and shared between shards.
  *
  * @define backpressures
  *   Backpressures before it hits a ratelimit.
  */
case class Requests(
    settings: RequestSettings,
    alsoProcessRequests: Sink[
      (Request[Data], RequestAnswer[Data]) forSome { type Data },
      NotUsed
    ] = Sink.ignore.mapMaterializedValue(_ => NotUsed)
)(implicit val system: ActorSystem[Nothing]) {

  private def ignoreOrReport[Ctx]: Sink[RequestAnswer[Any], Future[Done]] =
    Sink.foreach {
      case _: RequestResponse[_] =>
      case request: FailedRequest =>
        request.asException.printStackTrace()
    }

  private def addExtraProcessing[Data, Ctx](
      flowWithCtx: FlowWithContext[Request[
        Data
      ], (Request[Data], Ctx), RequestAnswer[
        Data
      ], (Request[Data], Ctx), NotUsed]
  ): FlowWithContext[Request[Data], Ctx, RequestAnswer[Data], Ctx, NotUsed] =
    flowWithCtx.asFlow
      .alsoTo(alsoProcessRequests.contramap(in => (in._2._1, in._1)))
      .asFlowWithContext[Request[Data], Ctx, Ctx]((req, ctx) =>
        (req, (req, ctx))
      )(_._2._2)
      .map(_._1)

  private lazy val rawFlowWithoutRateLimits =
    addExtraProcessing(
      RequestStreams.requestFlowWithoutRatelimit[Any, (Request[Any], Any)](
        settings
      )
    )

  /**
    * A basic request flow which will send requests to Discord, and receive
    * responses. Don't use this if you don't know what you're doing.
    */
  def flowWithoutRateLimits[Data, Ctx]
      : FlowWithContext[Request[Data], Ctx, RequestAnswer[Data], Ctx, NotUsed] =
    rawFlowWithoutRateLimits.asInstanceOf[FlowWithContext[Request[
      Data
    ], Ctx, RequestAnswer[Data], Ctx, NotUsed]]

  private lazy val rawFlow =
    addExtraProcessing(
      RequestStreams.requestFlow[Any, (Request[Any], Any)](settings)
    )

  private lazy val rawOrderedFlow =
    FlowWithContext
      .fromTuples[Request[Any], Any, RequestAnswer[Any], Any, NotUsed](
        RequestStreams.addOrdering(rawFlow.asFlow)
      )

  private lazy val rawRetryFlow = addExtraProcessing(
    RequestStreams.retryRequestFlow[Any, (Request[Any], Any)](settings)
  )

  private lazy val rawOrderedRetryFlow =
    FlowWithContext
      .fromTuples[Request[Any], Any, RequestAnswer[Any], Any, NotUsed](
        RequestStreams.addOrdering(rawRetryFlow.asFlow)
      )

  /**
    * A generic flow for making requests. You should use this one most of the
    * time. $backpressures
    *
    * Some info on ordered requests. Once a response for one request has been
    * received, the next one is sent. The next one is sent regardless of if the
    * previous request failed. Ordered requests still have a few kinks that need
    * to be removed before they always work.
    */
  def flow[Data, Ctx](implicit
      properties: RequestProperties = RequestProperties.default
  ): FlowWithContext[Request[Data], Ctx, RequestAnswer[Data], Ctx, NotUsed] = {
    val flowToUse = properties match {
      case RequestProperties(false, false) => rawFlow
      case RequestProperties(true, false)  => rawRetryFlow
      case RequestProperties(false, true)  => rawOrderedFlow
      case RequestProperties(true, true)   => rawOrderedRetryFlow
    }

    flowToUse.asInstanceOf[FlowWithContext[Request[Data], Ctx, RequestAnswer[
      Data
    ], Ctx, NotUsed]]
  }

  /**
    * A generic sink for making requests and ignoring the results.
    * $backpressures
    */
  def sinkIgnore[Data](implicit
      properties: RequestProperties = RequestProperties.default
  ): Sink[Request[Data], Future[Done]] =
    flow[Data, NotUsed](properties).asFlow
      .map(_._1)
      .toMat(ignoreOrReport)(Keep.right)
      .contramap[Request[Data]](_ -> NotUsed)

  /**
    * A generic flow for making requests. Only returns successful requests.
    * $backpressures
    *
    * @param ignoreFailures
    *   If true, failures will be logged and then ignored. If false, throws the
    *   failures
    */
  def flowSuccess[Data, Ctx](ignoreFailures: Boolean = true)(implicit
      properties: RequestProperties = RequestProperties.default
  ): FlowWithContext[Request[Data], Ctx, Data, Ctx, NotUsed] =
    flow[Data, Ctx](properties)
      .map {
        case RequestResponse(data, _, _, _) =>
          Some(data)
        case request: FailedRequest =>
          if (ignoreFailures) {
            request.asException.printStackTrace()
            None
          } else {
            throw request.asException
          }
      }
      .collect { case Some(value) => value }

  /**
    * Sends a single request.
    * @param request
    *   The request to send.
    * @return
    *   A source of the single request answer.
    */
  def single[Data](request: Request[Data])(implicit
      properties: RequestProperties = RequestProperties.default
  ): Source[RequestAnswer[Data], NotUsed] =
    Source.single(request -> NotUsed).via(flow(properties).asFlow.map(_._1))

  /**
    * Sends a single request, and grabs the data if it's available.
    * @param request
    *   The request to send.
    * @return
    *   A source of the successful request answer.
    */
  def singleSuccess[Data](request: Request[Data])(implicit
      properties: RequestProperties = RequestProperties.default
  ): Source[Data, NotUsed] =
    Source
      .single(request -> NotUsed)
      .via(flowSuccess(ignoreFailures = false)(properties).asFlow.map(_._1))

  /**
    * Sends a single request and gets the response as a future.
    * @param request
    *   The request to send.
    */
  def singleFuture[Data](request: Request[Data])(implicit
      properties: RequestProperties = RequestProperties.default
  ): Future[RequestAnswer[Data]] =
    single(request)(properties).runWith(Sink.head)

  /**
    * Sends a single request and gets the success response as a future if it's
    * available. If the request fails, the future fails.
    * @param request
    *   The request to send.
    */
  def singleFutureSuccess[Data](request: Request[Data])(implicit
      properties: RequestProperties = RequestProperties.default
  ): Future[Data] =
    singleSuccess(request)(properties).runWith(Sink.head)

  /**
    * Sends a single request and ignores the result.
    * @param request
    *   The request to send.
    */
  def singleIgnore[Data](request: Request[Data])(implicit
      properties: RequestProperties = RequestProperties.default
  ): Unit =
    single(request)(properties).runWith(ignoreOrReport)

  /**
    * Sends many requests
    * @param requests
    *   The requests to send.
    * @return
    *   A source of the request answers.
    */
  def many[Data](requests: immutable.Seq[Request[Data]])(implicit
      properties: RequestProperties = RequestProperties.default
  ): Source[RequestAnswer[Data], NotUsed] =
    Source(requests.map(_ -> NotUsed)).via(flow(properties).asFlow.map(_._1))

  /**
    * Sends many requests, and grabs the data if it's available.
    * @param requests
    *   The requests to send.
    * @param ignoreFailures
    *   If true, failures will be logged and then ignored. If false, throws the
    *   failures
    * @return
    *   A source of the successful request answers.
    */
  def manySuccess[Data](
      requests: immutable.Seq[Request[Data]],
      ignoreFailures: Boolean = true
  )(implicit
      properties: RequestProperties = RequestProperties.default
  ): Source[Data, NotUsed] =
    Source(requests.map(_ -> NotUsed))
      .via(flowSuccess(ignoreFailures)(properties).asFlow.map(_._1))

  /**
    * Sends many requests and gets the responses as a future.
    * @param requests
    *   The requests to send.
    */
  def manyFuture[Data](requests: immutable.Seq[Request[Data]])(implicit
      properties: RequestProperties = RequestProperties.default
  ): Future[immutable.Seq[RequestAnswer[Data]]] =
    many(requests)(properties).runWith(Sink.seq)

  /**
    * Sends many requests and gets the success response as a future if it's
    * available. All the requests must succeed for this function to return a a
    * value.
    * @param requests
    *   The requests to send.
    */
  def manyFutureSuccess[Data](requests: immutable.Seq[Request[Data]])(implicit
      properties: RequestProperties = RequestProperties.default
  ): Future[immutable.Seq[Data]] =
    manySuccess(requests, ignoreFailures = false).runWith(Sink.seq)

  /**
    * Sends many requests and ignores the result.
    * @param requests
    *   The requests to send.
    */
  def manyIgnore[Data](requests: immutable.Seq[Request[Data]])(implicit
      properties: RequestProperties = RequestProperties.default
  ): Unit = many(requests)(properties).runWith(ignoreOrReport)
}
object Requests {

  case class RequestProperties(
      retry: Boolean = false,
      ordered: Boolean = false
  )
  object RequestProperties {
    val default: RequestProperties = RequestProperties()
    val retry: RequestProperties = RequestProperties(retry = true)
    val ordered: RequestProperties = RequestProperties(ordered = true)
    val retryOrdered: RequestProperties =
      RequestProperties(retry = true, ordered = true)

    object Implicits {
      implicit val default: RequestProperties = RequestProperties.default
      implicit val retry: RequestProperties = RequestProperties.retry
      implicit val ordered: RequestProperties = RequestProperties.ordered
      implicit val retryOrdered: RequestProperties =
        RequestProperties.retryOrdered
    }
  }
}
