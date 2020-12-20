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

import java.util.concurrent.TimeoutException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import ackcord.AckCord
import ackcord.util.AckCordRequestSettings
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.scaladsl.{Flow, FlowWithContext, GraphDSL, Keep, Merge, Partition, Sink, Source}
import akka.stream.{Attributes, FlowShape}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import io.circe.Json

object RequestStreams {

  private def findCustomHeader[H <: ModeledCustomHeader[H]](
      companion: ModeledCustomHeaderCompanion[H],
      response: HttpResponse
  ): Option[H] =
    response.headers.collectFirst {
      case h if h.lowercaseName == companion.lowercaseName => companion.parse(h.value).toOption
    }.flatten

  private def remainingRequests(response: HttpResponse): Int =
    findCustomHeader(`X-RateLimit-Remaining`, response).fold(-1)(_.remaining)

  private def requestsForUri(response: HttpResponse): Int =
    findCustomHeader(`X-RateLimit-Limit`, response).fold(-1)(_.limit)

  private def timeTilReset(relativeTime: Boolean, response: HttpResponse): FiniteDuration = {
    if (relativeTime) {
      findCustomHeader(`X-RateLimit-Reset-After`, response).fold(-1.millis)(_.resetIn)
    } else {
      findCustomHeader(`X-RateLimit-Reset`, response).fold(-1.millis) { header =>
        // A race condition exists with using the absolute rate limit
        // if the reset time sent from Discord is before the time that this line runs
        // then this value would be negative and RatelimitInfo would be invalid
        // Taking the max of the difference and 1 millisecond ensures that the next
        // rate limit reset is in the future.
        (header.resetAt.toEpochMilli - System.currentTimeMillis()).millis.max(1.milli)
      }
    }
  }

  private def isGlobalRatelimit(response: HttpResponse): Boolean =
    findCustomHeader(`X-Ratelimit-Global`, response).fold(false)(_.isGlobal)

  private def requestBucket(route: RequestRoute, response: HttpResponse): String =
    findCustomHeader(`X-RateLimit-Bucket`, response).fold(route.uriWithoutMajor)(
      _.identifier
    ) //Sadly this is not always present

  val defaultUserAgent: `User-Agent` = `User-Agent`(
    s"DiscordBot (https://github.com/Katrix/AckCord, ${AckCord.Version})"
  )

  /**
    * A basic request flow which will send requests to Discord, and
    * receive responses. This flow does not account for ratelimits. Only
    * use it if you know what you're doing.
    */
  def requestFlowWithoutRatelimit[Data, Ctx](
      requestSettings: RequestSettings
  )(implicit system: ActorSystem[Nothing]): FlowWithContext[Request[Data], Ctx, RequestAnswer[Data], Ctx, NotUsed] =
    FlowWithContext.fromTuples(
      createHttpRequestFlow[Data, Ctx](requestSettings)
        .via(requestHttpFlow)
        .via(Flow.apply[(Try[HttpResponse], (Request[Data], Ctx))].map(t => ((t._1, t._2._1), t._2._2)))
        .via(requestParser[Data, Ctx](requestSettings))
        .asFlow
        .alsoTo(sendRatelimitUpdates[Data](requestSettings).contramap[(RequestAnswer[Data], Ctx)](_._1))
    )

  /**
    * A request flow which will send requests to Discord, and receive responses.
    * If it encounters a ratelimit it will backpressure.
    */
  def requestFlow[Data, Ctx](
      requestSettings: RequestSettings
  )(implicit system: ActorSystem[Nothing]): FlowWithContext[Request[Data], Ctx, RequestAnswer[Data], Ctx, NotUsed] = {

    val graph = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val in = builder.add(Flow[(Request[Data], Ctx)])
      val buffer =
        builder.add(Flow[(Request[Data], Ctx)].buffer(requestSettings.bufferSize, requestSettings.overflowStrategy))
      val ratelimits = builder.add(ratelimitFlow[Data, Ctx](requestSettings))

      val partition = builder.add(
        Partition[(MaybeRequest[Data], Ctx)](
          2,
          {
            case (_: RequestDropped, _) => 1
            case (_: Request[_], _)     => 0
          }
        )
      )
      val requests = partition.out(0).collect { case (request: Request[Data], ctx) => request -> ctx }
      val dropped  = partition.out(1).collect { case (dropped: RequestDropped, ctx) => dropped -> ctx }

      val network = builder.add(requestFlowWithoutRatelimit[Data, Ctx](requestSettings))
      val out     = builder.add(Merge[(RequestAnswer[Data], Ctx)](2))

      // format: OFF
      in ~> buffer ~> ratelimits ~> partition
                                    requests ~> network ~> out
                                    dropped  ~>            out
      // format: ON

      FlowShape(in.in, out.out)
    }

    FlowWithContext.fromTuples(Flow.fromGraph(graph))
  }

  /**
    * A request flow which obeys route specific rate limits, but not global ones.
    */
  def ratelimitFlow[Data, Ctx](requestSettings: RequestSettings)(
      implicit system: ActorSystem[Nothing]
  ): FlowWithContext[Request[Data], Ctx, MaybeRequest[Data], Ctx, NotUsed] = {
    implicit val triggerTimeout: Timeout = Timeout(requestSettings.maxAllowedWait)
    FlowWithContext.fromTuples(
      Flow[(Request[Data], Ctx)]
        .mapAsyncUnordered(requestSettings.parallelism) {
          case (request, ctx) =>
            //We don't use ask here to get be able to create a RequestDropped instance
            import system.executionContext
            val future = requestSettings.ratelimitActor.ask[Ratelimiter.Response[(Request[Data], Ctx)]](
              Ratelimiter.WantToPass(request.route, request.identifier, _, (request, ctx))
            )

            future
              .flatMap {
                case Ratelimiter.CanPass(a)       => Future.successful(a)
                case Ratelimiter.FailedRequest(e) => Future.failed(e)
              }
              .recover {
                case _: TimeoutException => RequestDropped(request.route, request.identifier) -> ctx
              }
        }
        .named("Ratelimiter")
    )
  }

  private def createHttpRequestFlow[Data, Ctx](requestSettings: RequestSettings)(
      implicit system: ActorSystem[Nothing]
  ): FlowWithContext[Request[Data], Ctx, HttpRequest, (Request[Data], Ctx), NotUsed] = {
    implicit val logger: LoggingAdapter = Logging(system.classicSystem, "ackcord.rest.SentRESTRequest")

    val baseFlow = FlowWithContext[Request[Data], Ctx]

    val withLogging =
      if (AckCordRequestSettings().LogSentREST)
        baseFlow.log(
          "Sent REST request",
          { request =>
            val loggingBody = request.bodyForLogging.fold("")(body => s" and content $body")
            s"to ${request.route.uri} with method ${request.route.method}$loggingBody"
          }
        )
      else baseFlow

    withLogging.via(Flow[(Request[Data], Ctx)].map {
      case (request, ctx) =>
        val route = request.route
        val auth  = requestSettings.credentials.map(Authorization(_))
        val httpRequest = HttpRequest(
          route.method,
          route.uri,
          requestSettings.userAgent +: (auth.toSeq ++ request.extraHeaders),
          request.requestBody
        )

        (httpRequest, (request, ctx))
    })
  }.withAttributes(Attributes.name("CreateRequest"))

  private def requestHttpFlow[Data, Ctx](
      implicit system: ActorSystem[Nothing]
  ): FlowWithContext[HttpRequest, (Request[Data], Ctx), Try[HttpResponse], (Request[Data], Ctx), NotUsed] = {
    import akka.actor.typed.scaladsl.adapter._
    FlowWithContext.fromTuples(Http(system.toClassic).superPool[(Request[Data], Ctx)]())
  }

  private def requestParser[Data, Ctx](requestSettings: RequestSettings)(
      implicit system: ActorSystem[Nothing]
  ): FlowWithContext[(Try[HttpResponse], Request[Data]), Ctx, RequestAnswer[Data], Ctx, NotUsed] = {
    FlowWithContext[(Try[HttpResponse], Request[Data]), Ctx]
      .mapAsync(requestSettings.parallelism) {
        {
          case (response, request) =>
            import system.executionContext

            val route = request.route
            response match {
              case Success(httpResponse) =>
                val tilReset     = timeTilReset(requestSettings.relativeTime, httpResponse)
                val tilRatelimit = remainingRequests(httpResponse)
                val bucketLimit  = requestsForUri(httpResponse)
                val bucket       = requestBucket(route, httpResponse)

                val ratelimitInfo = RatelimitInfo(
                  tilReset,
                  tilRatelimit,
                  bucketLimit,
                  bucket
                )

                httpResponse.status match {
                  case StatusCodes.TooManyRequests =>
                    httpResponse.discardEntityBytes()
                    Future.successful(
                      RequestRatelimited(
                        isGlobalRatelimit(httpResponse),
                        ratelimitInfo,
                        route,
                        request.identifier
                      )
                    )
                  case e if e.isFailure() =>
                    httpResponse.entity.dataBytes
                      .runFold(ByteString.empty)(_ ++ _)
                      .map { eBytes =>
                        RequestError(HttpException(e, Some(eBytes.utf8String)), route, request.identifier)
                      }
                  case StatusCodes.NoContent =>
                    httpResponse.discardEntityBytes()

                    request
                      .parseResponse(HttpEntity.Empty)
                      .map(RequestResponse(_, ratelimitInfo, route, request.identifier))
                  case _ => //Should be success
                    request
                      .parseResponse(httpResponse.entity)
                      .map(RequestResponse(_, ratelimitInfo, route, request.identifier))
                }

              case Failure(e) =>
                Future.successful(RequestError(e, route, request.identifier))
            }
        }
      }
  }.withAttributes(Attributes.name("RequestParser"))

  private def sendRatelimitUpdates[Data](requestSettings: RequestSettings): Sink[RequestAnswer[Data], Future[Done]] =
    Sink
      .foreach[RequestAnswer[Data]] { answer =>
        val isGlobal = answer match {
          case ratelimited: RequestRatelimited => ratelimited.global
          case _                               => false
        }

        requestSettings.ratelimitActor ! Ratelimiter.UpdateRatelimits(
          answer.route,
          answer.ratelimitInfo,
          isGlobal,
          answer.identifier
        )
      }
      .async
      .named("SendAnswersToRatelimiter")

  /**
    * A flow that only returns successful responses.
    */
  def dataResponses[Data, Ctx]: FlowWithContext[RequestAnswer[Data], Ctx, Data, Ctx, NotUsed] =
    FlowWithContext[RequestAnswer[Data], Ctx].collect {
      case response: RequestResponse[Data] => response.data
    }

  /**
    * A request flow that will retry failed requests.
    */
  def retryRequestFlow[Data, Ctx](
      requestSettings: RequestSettings
  )(implicit system: ActorSystem[Nothing]): FlowWithContext[Request[Data], Ctx, RequestAnswer[Data], Ctx, NotUsed] = {
    FlowWithContext.fromTuples(
      Flow[(Request[Data], Ctx)].flatMapMerge(
        requestSettings.parallelism,
        i => {
          val singleFlow = Source
            .single(i)
            .via(RequestStreams.requestFlow(requestSettings))
            .map {
              case (value: RequestResponse[Data], ctx) => value -> ctx
              case (err: FailedRequest, ctx)           => throw RetryFailedRequestException(err, ctx)
            }

          singleFlow
            .recoverWithRetries(
              requestSettings.maxRetryCount,
              {
                case RetryFailedRequestException(_, _) => singleFlow
              }
            )
            .recover {
              case RetryFailedRequestException(err, ctx) => err -> ctx.asInstanceOf[Ctx]
            }
        }
      )
    )
  }

  def addOrdering[A, B](inner: Flow[A, B, NotUsed]): Flow[A, B, NotUsed] = Flow[A].flatMapConcat { a =>
    Source.single(a).via(inner)
  }

  def bytestringFromResponse: Flow[ResponseEntity, ByteString, NotUsed] =
    Flow[ResponseEntity].flatMapConcat(_.dataBytes.fold(ByteString.empty)(_ ++ _))

  def jsonDecode: Flow[ResponseEntity, Json, NotUsed] = {
    Flow[ResponseEntity]
      .map { entity =>
        val isValid =
          entity.contentType == ContentTypes.NoContentType || entity.contentType == ContentTypes.`application/json`

        if (isValid) {
          entity
        } else {
          throw HttpJsonDecodeException("Invalid content type for json")
        }
      }
      .via(bytestringFromResponse)
      .map {
        case ByteString.empty => throw HttpJsonDecodeException("No data for json decode")
        case bytes            => io.circe.jawn.parseByteBuffer(bytes.asByteBuffer)
      }
      .map(_.fold(throw _, identity))
  }

  def removeContext[I, O, Mat](withContext: FlowWithContext[I, NotUsed, O, NotUsed, Mat]): Flow[I, O, Mat] =
    Flow[I].map(_ -> NotUsed).viaMat(withContext)(Keep.right).map(_._1)
}
