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

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{ClientError, ServerError}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, StatusCodes}
import akka.pattern.{AskTimeoutException, ask}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Partition, Sink}
import akka.stream.{FlowShape, Materializer, OverflowStrategy}
import akka.util.Timeout
import akka.{Done, NotUsed}
import net.katsstuff.ackcord.AckCord
import net.katsstuff.ackcord.util.AckCordSettings

object RequestStreams {

  private var _uriRatelimitActor: ActorRef = _
  def uriRateLimitActor(implicit system: ActorSystem): ActorRef = {
    if (_uriRatelimitActor == null) {
      _uriRatelimitActor = system.actorOf(Ratelimiter.props)
    }

    _uriRatelimitActor
  }

  /**
    * Create credentials used by bots
    */
  def botCredentials(token: String): HttpCredentials = GenericHttpCredentials("Bot", token)

  /**
    * Create OAuth2 credentials
    */
  def oAuth2Credentials(token: String): HttpCredentials = OAuth2BearerToken(token)

  private def remainingRequests(headers: Seq[HttpHeader]): Int =
    headers.find(_.is("X-RateLimit-Remaining")).map(remaining => remaining.value.toInt).getOrElse(-1)

  private def timeTilReset(headers: Seq[HttpHeader]): Long = {
    headers
      .find(_.is("Retry-After"))
      .map(_.value().toLong)
      .orElse(
        headers
          .find(_.is("X-RateLimit-Reset"))
          .map(_.value().toLong.seconds - System.currentTimeMillis().millis)
          .map(_.toMillis)
      )
      .getOrElse(-1)
  }

  private def isGlobalRatelimit(headers: Seq[HttpHeader]): Boolean =
    headers.exists(header => header.is("X-RateLimit-Global") && header.value == "true")

  private val userAgent = `User-Agent`(s"DiscordBot (https://github.com/Katrix-/AckCord, ${AckCord.Version})")

  def requestFlow[Data, Ctx](credentials: HttpCredentials, parallelism: Int = 4)(
      implicit mat: Materializer,
      system: ActorSystem
  ): Flow[RequestWrapper[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed] = {
    val flow = createHttpRequestFlow[Data, Ctx](credentials)
      .via(requestHttpFlow)
      .via(requestParser)
      .mapAsync(parallelism)(identity)

    flow.alsoTo(sendRatelimitUpdates)
  }

  def requestFlowWithRatelimit[Data, Ctx](
      bufferSize: Int,
      overflowStrategy: OverflowStrategy,
      maxAllowedWait: FiniteDuration,
      credentials: HttpCredentials,
      parallelism: Int = 4
  )(
      implicit mat: Materializer,
      system: ActorSystem
  ): Flow[RequestWrapper[Data, Ctx], RequestAnswer[Data, Ctx], NotUsed] = {
    val uriRatelimiterActor = uriRateLimitActor(system)

    val graph = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val inFlow = builder.add(Flow[RequestWrapper[Data, Ctx]])

      val globalRatelimitBuffer = builder.add(Flow[RequestWrapper[Data, Ctx]].buffer(bufferSize, overflowStrategy))
      val globalRateLimiter     = builder.add(new GlobalRatelimit[Data, Ctx])
      val uriRateLimiter        = builder.add(requestsWithRouteRatelimit[Data, Ctx](uriRatelimiterActor, maxAllowedWait))

      val partition = builder.add(Partition[SentRequest[Data, Ctx]](2, {
        case _: RequestWrapper[_, _] => 0
        case _: RequestDropped[_, _] => 1
      }))

      val requests = builder.add(requestFlow[Data, Ctx](credentials, parallelism))

      val answerBroadcaster = builder.add(Broadcast[RequestAnswer[Data, Ctx]](2))

      val answerMerger = builder.add(Merge[RequestAnswer[Data, Ctx]](2))

      inFlow ~> globalRatelimitBuffer ~> globalRateLimiter.in0; globalRateLimiter.out ~> uriRateLimiter ~> partition

      partition.out(0).collect { case wrapper: RequestWrapper[Data, Ctx] => wrapper } ~> requests ~> answerBroadcaster
      partition.out(1).collect { case dropped: RequestDropped[Data, Ctx] => dropped } ~> answerMerger

      answerBroadcaster ~> answerMerger
      globalRateLimiter.in1 <~ answerBroadcaster

      FlowShape(inFlow.in, answerMerger.out)
    }

    Flow.fromGraph(graph)
  }

  def requestsWithRouteRatelimit[Data, Ctx](ratelimiter: ActorRef, maxAllowedWait: FiniteDuration)(
      implicit system: ActorSystem
  ): Flow[SentRequest[Data, Ctx], SentRequest[Data, Ctx], NotUsed] = {
    implicit val triggerTimeout: Timeout = Timeout(maxAllowedWait)
    Flow[SentRequest[Data, Ctx]].mapAsync(4) {
      case wrapper @ RequestWrapper(request, _, _) =>
        import system.dispatcher
        val future = ratelimiter ? Ratelimiter.WantToPass(request.route.uri)

        future.map(_ => wrapper).recover {
          case _: AskTimeoutException => wrapper.toDropped
        }
      case dropped: RequestDropped[_, _] => Future.successful(dropped)
    }
  }

  def createHttpRequestFlow[Data, Ctx](credentials: HttpCredentials)(
      implicit system: ActorSystem
  ): Flow[RequestWrapper[Data, Ctx], (HttpRequest, RequestWrapper[Data, Ctx]), NotUsed] = {
    Flow.fromFunction[RequestWrapper[Data, Ctx], (HttpRequest, RequestWrapper[Data, Ctx])] {
      case wrapper @ RequestWrapper(request, _, _) =>
        val route = request.route
        val auth  = Authorization(credentials)

        if (AckCordSettings().LogSentREST) {
          request match {
            case request: ComplexRESTRequest[_, _, _, _] =>
              system.log.debug(
                "Sent REST request to {} with method {} and content {}",
                route.uri,
                route.method.value,
                request.jsonParams.noSpaces
              )
            case _ =>
          }
        }

        (HttpRequest(route.method, route.uri, immutable.Seq(auth, userAgent), request.requestBody), wrapper)
    }
  }

  def requestHttpFlow[Data, Ctx](
      implicit mat: Materializer,
      system: ActorSystem
  ): Flow[(HttpRequest, RequestWrapper[Data, Ctx]), (Try[HttpResponse], RequestWrapper[Data, Ctx]), NotUsed] =
    Http().superPool[RequestWrapper[Data, Ctx]]()

  def requestParser[Data, Ctx](
      implicit mat: Materializer,
      system: ActorSystem
  ): Flow[(Try[HttpResponse], RequestWrapper[Data, Ctx]), Future[RequestAnswer[Data, Ctx]], NotUsed] =
    Flow.fromFunction[(Try[HttpResponse], RequestWrapper[Data, Ctx]), Future[RequestAnswer[Data, Ctx]]] {
      case (response, request) =>
        import system.dispatcher
        response match {
          case Success(httpResponse) =>
            val headers      = httpResponse.headers
            val tilReset     = timeTilReset(headers)
            val remainingReq = remainingRequests(headers)

            httpResponse.status match {
              case StatusCodes.TooManyRequests =>
                httpResponse.discardEntityBytes()
                Future.successful(RequestRatelimited(request.context, tilReset, isGlobalRatelimit(headers), request))
              case StatusCodes.NoContent =>
                httpResponse.discardEntityBytes()
                Future.successful(RequestResponseNoData(request.context, remainingReq, tilReset, request))
              case e @ (_: ServerError | _: ClientError) =>
                httpResponse.discardEntityBytes()
                Future.successful(RequestError(request.context, new HttpException(e), request))
              case _ => //Should be success
                request.request
                  .parseResponse(httpResponse.entity)
                  .map[RequestAnswer[Data, Ctx]](
                    response => RequestResponse(response, request.context, remainingReq, tilReset, request)
                  )
                  .recover {
                    case NonFatal(e) => RequestError(request.context, e, request)
                  }
            }

          case Failure(e) => Future.successful(RequestError(request.context, e, request))
        }
    }

  def sendRatelimitUpdates[Data, Ctx]: Sink[RequestAnswer[Data, Ctx], Future[Done]] =
    Sink.foreach[RequestAnswer[Data, Ctx]] { answer =>
      val tilReset          = answer.tilReset
      val remainingRequests = answer.remainingRequests
      val uri               = answer.toWrapper.request.route.uri
      if (_uriRatelimitActor != null && tilReset != -1 && remainingRequests != -1) {
        _uriRatelimitActor ! Ratelimiter.UpdateRatelimits(uri, tilReset, remainingRequests)
      }
    }
}
