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
package net.katsstuff.ackcord.http.rest

import scala.collection.{immutable, mutable}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{ClientError, ServerError}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import net.katsstuff.ackcord.http.Routes
import net.katsstuff.ackcord.http.rest.RESTHandler.{HandleRateLimitAndRetry, ProcessedRequest, RateLimitStop, RemoveRateLimit, UpdateRateLimit}
import net.katsstuff.ackcord.util.AckCordSettings
import net.katsstuff.ackcord.{AckCord, DiscordClient, MiscHandlerEvent, Request, RequestFailed, RequestResponse}

/**
  * An actor responsible for sending REST requests to Discord. You are free to
  * (and in some cases should) create additional instances of this actor
  * @param token The credentials to use for authentication.
  *              See utility methods for normal cases of creating one.
  * @param responseProcessor An actor which receive all responses sent through this actor
  * @param responseFunc A function to apply to all responses before sending them to the [[responseProcessor]]
  * @param mat The materializer to use
  */
class RESTHandler(
    token: HttpCredentials,
    responseProcessor: Option[ActorRef],
    responseFunc: ((ComplexRESTRequest[_, Any, Any], Any) => Any)
)(implicit mat: Materializer)
    extends Actor
    with ActorLogging {

  implicit val system: ActorSystem = context.system
  import context.dispatcher

  val responder: ActorRef = context.actorOf(RESTResponder.props(self, responseProcessor, responseFunc), "RESTResponder")

  private val requestQueue = {
    val poolClientFlow = Http().cachedHostConnectionPoolHttps[ProcessedRequest[_, _, _]](Routes.discord)
    val responderSink = Sink.actorRefWithAck[(Try[HttpResponse], ProcessedRequest[_, _, _])](
      ref = responder,
      onInitMessage = RESTResponder.InitSink,
      ackMessage = RESTResponder.AckSink,
      onCompleteMessage = RESTResponder.CompletedSink,
    )

    Source
      .queue[(HttpRequest, ProcessedRequest[_, _, _])](64, OverflowStrategy.fail)
      .via(poolClientFlow)
      .toMat(responderSink)(Keep.left)
      .run()
  }

  private val globalRateLimitQueue = new mutable.Queue[Any]
  private val rateLimits           = new mutable.HashMap[Uri, (Int, mutable.Queue[Any])]

  private val auth      = Authorization(token)
  private val userAgent = `User-Agent`(s"DiscordBot (https://github.com/Katrix-/AckCord, ${AckCord.Version})")

  override def receive: Receive = {
    case DiscordClient.ShutdownClient =>
      requestQueue.watchCompletion().foreach { _ =>
        context.stop(self)
      }
      requestQueue.complete()
    case RemoveRateLimit(uri) =>
      rateLimits.get(uri).foreach {
        case (_, queue) =>
          queue.dequeueAll(_ => true).foreach(self ! _)
      }
      rateLimits.remove(uri)
    case UpdateRateLimit(uri, headers)              => updateRateLimit(uri, headers)
    case HandleRateLimitAndRetry(headers, retryObj) => handleRatelimitAndRetry(headers, retryObj)
    //The d and h here makes IntelliJ happy. They serves no other purpose
    case Request(request: ComplexRESTRequest[_, d, h], contextual, sendResponseTo)
        if !rateLimits.contains(request.route.uri) =>
      val route       = request.route
      val httpRequest = HttpRequest(route.method, route.uri, immutable.Seq(auth, userAgent), request.createBody)

      if(AckCordSettings().LogSentREST) {
        log.debug("Sending request {} to {} with method {}", request.toJsonParams.noSpaces, route.uri, route.method)
      }

      queueRequest(httpRequest, request, contextual, sendResponseTo)
    case Status.Failure(e) => throw e
    //If we get here then the request is rate limited
    case fullRequest @ Request(request: ComplexRESTRequest[_, _, _], _, _) =>
      val duration = rateLimits.get(request.route.uri).map(_._1).getOrElse(0)
      system.scheduler.scheduleOnce(duration.millis, self, fullRequest)
  }

  def onGlobalRateLimit: Receive = {
    case DiscordClient.ShutdownClient =>
      requestQueue.watchCompletion().foreach { _ =>
        context.stop(self)
      }
      requestQueue.complete()
    case RateLimitStop =>
      context.unbecome()
      globalRateLimitQueue.dequeueAll(_ => true).foreach(self ! _)
    case other =>
      globalRateLimitQueue.enqueue(other)
  }

  def updateRateLimit(uri: Uri, headers: Seq[HttpHeader]): Unit = {
    for {
      remaining <- headers.find(_.is("X-RateLimit-Remaining"))
      remainingInt = remaining.value().toInt
      if remainingInt <= 0
      retry <- headers.find(_.is("Retry-After"))
    } {
      val retryInt = retry.value().toInt
      rateLimits.update(uri, (retryInt, rateLimits.get(uri).map(_._2).getOrElse(mutable.Queue.empty)))
      system.scheduler.scheduleOnce(retryInt.millis, self, RemoveRateLimit(uri))
    }
  }

  def handleRatelimitAndRetry[A](headers: Seq[HttpHeader], retryMsg: A): Unit = {
    if (headers.exists(header => header.is("X-RateLimit-Global") && header.value == "true")) {
      log.error("Hit global rate limit")
      headers
        .find(_.is("Retry-After"))
        .map(_.value().toLong)
        .orElse(headers.find(_.is("X-RateLimit-Reset")).map(_.value().toLong - System.currentTimeMillis())) match {
        case Some(duration) =>
          context.become(onGlobalRateLimit, discardOld = false)
          system.scheduler.scheduleOnce(duration.millis, self, RateLimitStop)
        case None => log.error("No retry time for global rate limit")
      }
    } else {
      headers
        .find(_.is("Retry-After"))
        .foreach(time => system.scheduler.scheduleOnce(time.value.toLong.millis, self, retryMsg))
    }
  }

  def queueRequest(
      httpRequest: HttpRequest,
      restRequest: ComplexRESTRequest[_, _, _],
      context: Any,
      sendTo: Option[ActorRef]
  ): Unit = {
    requestQueue
      .offer(httpRequest -> ProcessedRequest(restRequest, context, sendTo))
      .foreach {
        case QueueOfferResult.Enqueued => //All is fine
        case QueueOfferResult.Dropped =>
          val e = new RuntimeException("Queue overflowed.")
          sendTo.foreach(_ ! RequestFailed(e, context))
          throw e
        case QueueOfferResult.Failure(e) =>
          sendTo.foreach(_ ! RequestFailed(e, context))
          throw e
        case QueueOfferResult.QueueClosed =>
          val e = new RuntimeException("Queue was closed (pool shut down) while running the request.")
          sendTo.foreach(_ ! RequestFailed(e, context))
          throw e
      }
  }
}
object RESTHandler {
  def props(
      token: HttpCredentials,
      responseProcessor: Option[ActorRef],
      responseFunc: ((ComplexRESTRequest[_, Any, Any], Any) => Any)
  )(implicit mat: Materializer): Props =
    Props(new RESTHandler(token, responseProcessor, responseFunc))

  def cacheProps(token: HttpCredentials, snowflakeCache: ActorRef)(implicit mat: Materializer): Props =
    props(token, Some(snowflakeCache), (req, data) => MiscHandlerEvent(data, req.handleResponse))

  private case object RateLimitStop
  private case class RemoveRateLimit(uri: Uri)

  private[rest] case class ProcessedRequest[Response, HandlerType, Context](
      restRequest: ComplexRESTRequest[_, Response, HandlerType],
      context: Context,
      sendTo: Option[ActorRef]
  )

  private[rest] case class UpdateRateLimit(uri: Uri, headers: Seq[HttpHeader])
  private[rest] case class HandleRateLimitAndRetry[A](headers: Seq[HttpHeader], retryObj: A)

  /**
    * Create credentials used by bots
    */
  def botCredentials(token: String): HttpCredentials = GenericHttpCredentials("Bot", token)

  /**
    * Create OAuth2 credentials
    */
  def oAuth2Credentials(token: String): HttpCredentials = OAuth2BearerToken(token)
}

/**
  * Actor responsible for responding the http responses
  */
class RESTResponder(
    parent: ActorRef,
    responseProcessor: Option[ActorRef],
    responseFunc: ((ComplexRESTRequest[_, Any, Any], Any) => Any)
)(implicit mat: Materializer)
    extends Actor
    with ActorLogging
    with FailFastCirceSupport {
  import RESTResponder._
  import context.dispatcher

  override def receive: Receive = {
    case InitSink =>
      sender() ! AckSink
    case CompletedSink =>
      log.info("RESTHandler sink completed")
      context.stop(self)
    case Success((HttpResponse(StatusCodes.TooManyRequests, headers, entity, _), req: ProcessedRequest[_, _, _])) =>
      parent ! UpdateRateLimit(req.restRequest.route.uri, headers)
      parent ! HandleRateLimitAndRetry(headers, Request(req.restRequest, req.context, req.sendTo))
      entity.discardBytes()
      sender() ! AckSink
    case (Success(HttpResponse(StatusCodes.NoContent, headers, entity, _)), ProcessedRequest(request, ctx, sendTo))
        if request.expectedResponseCode == StatusCodes.NoContent =>
      parent ! UpdateRateLimit(request.route.uri, headers)
      entity.discardBytes()
      sender() ! AckSink
      sendTo.foreach(_ ! RequestResponse((), ctx))
    case (Success(HttpResponse(response, headers, entity, _)), ProcessedRequest(request, ctx, sendTo)) =>
      parent ! UpdateRateLimit(request.route.uri, headers)
      if (request.expectedResponseCode == response) {
        Unmarshal(entity)
          .to[Json]
          .flatMap { json =>
            if(AckCordSettings().LogReceivedREST) {
              log.debug("Received response: {}", json.noSpaces)
            }
            Future.fromTry(json.as(request.responseDecoder).map(request.processResponse).toTry)
          }
          .onComplete {
            case Success(data) =>
              responseProcessor.foreach(_ ! responseFunc(request, data))
              sendTo.foreach(_ ! RequestResponse(data, ctx))
            case Failure(e) =>
              parent ! Status.Failure(e)
              sendTo.foreach(_ ! RequestFailed(e, ctx))
          }
      } else {
        log.warning("Unexpected response code {} for {}", response.intValue, request)
        entity.discardBytes()
        sendTo.foreach(
          _ ! RequestFailed(new IllegalStateException(s"Unexpected response code ${response.intValue()}"), ctx)
        )
      }
      sender() ! AckSink
    case (Success(HttpResponse(e @ ServerError(intValue), _, entity, _)), ProcessedRequest(_, ctx, sendTo)) =>
      log.error("Server error {} {}", intValue, e.reason)
      entity.toStrict(1.seconds).onComplete {
        case Success(ent) => log.error(ent.toString())
        case Failure(_)   => entity.discardBytes()
      }
      sender() ! AckSink
      sendTo.foreach(_ ! RequestFailed(new IllegalStateException(s"Server error: $intValue"), ctx))
    case (Success(HttpResponse(e @ ClientError(intValue), _, entity, _)), ProcessedRequest(_, ctx, sendTo)) =>
      log.error("Client error {}: {}", intValue, e.reason)
      entity.discardBytes()
      sendTo.foreach(_ ! RequestFailed(new IllegalStateException(s"Client error: $intValue"), ctx))
    case (Failure(e), ProcessedRequest(_, ctx, sendTo)) =>
      sendTo.foreach(_ ! RequestFailed(e, ctx))
      throw e
  }
}
object RESTResponder {
  def props(
      parent: ActorRef,
      responseProcessor: Option[ActorRef],
      responseFunc: ((ComplexRESTRequest[_, Any, Any], Any) => Any)
  )(implicit mat: Materializer): Props =
    Props(new RESTResponder(parent, responseProcessor, responseFunc))

  private[rest] case object InitSink
  private[rest] case object AckSink
  private[rest] case object CompletedSink
}
