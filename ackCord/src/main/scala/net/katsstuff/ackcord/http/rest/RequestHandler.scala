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
import scala.concurrent.duration._
import scala.language.existentials
import scala.util.{Failure, Success, Try}

import akka.NotUsed
import akka.actor.SupervisorStrategy.{Escalate, Stop}
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{ClientError, ServerError}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import net.katsstuff.ackcord.data.CacheSnapshot
import net.katsstuff.ackcord.http.rest.RequestHandler.{HandleRateLimitAndRetry, RateLimitStop, RemoveRateLimit, UpdateRateLimit}
import net.katsstuff.ackcord.util.AckCordSettings
import net.katsstuff.ackcord.{AckCord, DiscordClient, Request, SendHandledDataEvent}

/**
  * An actor responsible for sending requests to Discord. You are free to
  * (and in some cases should) create additional instances of this actor.
  * @param token The credentials to use for authentication.
  *              See utility methods for normal cases of creating one.
  * @param responseFunc A function to apply to all responses. The first value in
  *                     the function is the request, the second is the received
  *                     object (the second type parameter on the request).
  * @param mat The materializer to use
  */
class RequestHandler(token: HttpCredentials, responseFunc: (((Request[A, _], A) forSome {type A}) => Unit))(
    implicit mat: Materializer
) extends Actor
    with Timers
    with ActorLogging {

  implicit val system: ActorSystem = context.system
  import context.dispatcher

  private val requestQueue = {
    val poolClientFlow = Http().superPool[Request[_, _]]()
    val responderSink = Sink.actorRefWithAck[(Try[HttpResponse], Request[_, _])](
      ref = context.actorOf(RequestResponder.props(self, responseFunc), "RESTResponder"),
      onInitMessage = RequestResponder.InitSink,
      ackMessage = RequestResponder.AckSink,
      onCompleteMessage = RequestResponder.CompletedSink,
    )

    Source
      .queue[(HttpRequest, Request[_, _])](64, OverflowStrategy.fail)
      .via(poolClientFlow)
      .toMat(responderSink)(Keep.left)
      .run()
  }

  private val globalRateLimitQueue = new mutable.Queue[Any]
  private val rateLimits           = new mutable.HashMap[Uri, (Long, mutable.Queue[Any])]

  private val auth      = Authorization(token)
  private val userAgent = `User-Agent`(s"DiscordBot (https://github.com/Katrix-/AckCord, ${AckCord.Version})")

  //Like the default strategy except that we escalate if stuff goes wrong
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException ⇒ Stop
    case _: ActorKilledException         ⇒ Stop
    case _: DeathPactException           ⇒ Stop
    case _: Exception                    ⇒ Escalate
  }

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
    //The d here makes IntelliJ happy. They serves no other purpose
    case requestObj @ Request(request, _, _, _) if !rateLimits.contains(request.route.uri) =>
      val route       = request.route
      val httpRequest = HttpRequest(route.method, route.uri, immutable.Seq(auth, userAgent), request.requestBody)

      if (AckCordSettings().LogSentREST) {
        request match {
          case request: ComplexRESTRequest[_, _, _, _] =>
            log.debug("Sending request {} to {} with method {}", request.jsonParams.noSpaces, route.uri, route.method)
          case _ => log.debug("Sending request to {} with method {}", route.uri, route.method)
        }
      }

      queueRequest(httpRequest, requestObj)
    case Status.Failure(e) => throw e
    //If we get here then the request is rate limited
    case fullRequest @ Request(request, _, _, retries) =>
      if (retries > 0) {
        val queue      = rateLimits.get(request.route.uri).map(_._2).getOrElse(mutable.Queue.empty)
        val newRequest = fullRequest.copy(retries = fullRequest.retries - 1)
        queue.enqueue(newRequest)
      } else fullRequest.respondRatelimited()
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

  def rateLimitsReset(headers: Seq[HttpHeader]): Option[Long] = {
    headers
      .find(_.is("Retry-After"))
      .map(_.value().toLong)
      .orElse(headers.find(_.is("X-RateLimit-Reset")).map(_.value().toLong - System.currentTimeMillis()))
  }

  def updateRateLimit(uri: Uri, headers: Seq[HttpHeader]): Unit = {
    for {
      remaining <- headers.find(_.is("X-RateLimit-Remaining"))
      remainingInt = remaining.value.toInt
      if remainingInt <= 0
      reset <- rateLimitsReset(headers)
    } {
      rateLimits.update(uri, (reset, rateLimits.get(uri).map(_._2).getOrElse(mutable.Queue.empty)))
      timers.startSingleTimer(s"RemoveRateLimit$uri", RemoveRateLimit(uri), reset.millis)
    }
  }

  def handleRatelimitAndRetry(headers: Seq[HttpHeader], retryMsg: Request[_, _]): Unit = {
    if (headers.exists(header => header.is("X-RateLimit-Global") && header.value == "true")) {
      log.error("Hit global rate limit")
      rateLimitsReset(headers) match {
        case Some(duration) =>
          context.become(onGlobalRateLimit, discardOld = false)
          timers.startSingleTimer("RemoveGlobalRateLimit", RateLimitStop, duration.millis)
        case None => log.error("No retry time for global rate limit")
      }
    } else if (retryMsg.retries > 0) {
      val newRequest = retryMsg.copy(retries = retryMsg.retries - 1)
      headers
        .find(_.is("Retry-After"))
        .foreach(time => timers.startSingleTimer(newRequest, newRequest, time.value.toLong.millis))
    }
  }

  def queueRequest(httpRequest: HttpRequest, request: Request[_, _]): Unit = {
    requestQueue
      .offer(httpRequest -> request)
      .foreach {
        case QueueOfferResult.Enqueued => //All is fine
        case QueueOfferResult.Dropped =>
          val e = new RuntimeException("Queue overflowed.")
          request.respondError(e)
          throw e
          request.respondError(e)
          throw e
        case QueueOfferResult.QueueClosed =>
          val e = new RuntimeException("Queue was closed (pool shut down) while running the request.")
          request.respondError(e)
          throw e
        case QueueOfferResult.Failure(e) =>
          request.respondError(e)
          throw e
      }
  }
}
object RequestHandler {

  def props(token: HttpCredentials, responseFunc: ((Request[A, _], A) forSome {type A}) => Unit)(
      implicit mat: Materializer
  ): Props =
    Props(new RequestHandler(token, responseFunc))

  def cacheProps(token: HttpCredentials, snowflakeCache: ActorRef)(implicit mat: Materializer): Props = {
    val fun: ((Request[A, _], A) forSome {type A}) => Unit = t => {
      val request = t._1
      val data = t._2
      request.request match {
        case restRequest: BaseRESTRequest[Any @unchecked, _, _] =>
          if (restRequest.hasCustomResponseData) {
            snowflakeCache ! SendHandledDataEvent(
              restRequest.convertToCacheHandlerType(data),
              restRequest.cacheHandler,
              (current: CacheSnapshot,
                prev: CacheSnapshot) => restRequest.findData(data)(current, prev),
              request.sendResponseTo
            )
          } else request.respond(data)
        case _ => request.respond(data)
      }
    }

    props(token, fun)
  }

  private case object RateLimitStop
  private case class RemoveRateLimit(uri: Uri)

  private[rest] case class UpdateRateLimit(uri: Uri, headers: Seq[HttpHeader])
  private[rest] case class HandleRateLimitAndRetry(headers: Seq[HttpHeader], retryObj: Request[_, _])

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
class RequestResponder(parent: ActorRef, responseFunc: (((Request[A, _], A) forSome {type A}) => Unit))(
    implicit mat: Materializer
) extends Actor
    with ActorLogging {
  import RequestResponder._
  import context.dispatcher

  implicit val system: ActorSystem = context.system

  override def receive: Receive = {
    case InitSink =>
      sender() ! AckSink
    case CompletedSink =>
      log.info("RESTHandler sink completed")
      context.stop(self)
    case Success((HttpResponse(StatusCodes.TooManyRequests, headers, entity, _), request: Request[_, _])) =>
      parent ! UpdateRateLimit(request.request.route.uri, headers)
      parent ! HandleRateLimitAndRetry(headers, request)
      entity.discardBytes()
      sender() ! AckSink
    case (Success(HttpResponse(StatusCodes.NoContent, headers, entity, _)), request: Request[_, _]) =>
      parent ! UpdateRateLimit(request.request.route.uri, headers)
      entity.discardBytes()
      sender() ! AckSink
      request.respond(NotUsed)
    case (Success(HttpResponse(responseCode, headers, entity, _)), request: Request[a, b]) =>
      parent ! UpdateRateLimit(request.request.route.uri, headers)
      if (responseCode.isSuccess()) {
        request.request.parseResponse(entity).onComplete {
          case Success(data) =>
            responseFunc(request, data)
          case Failure(e) =>
            parent ! Status.Failure(e)
        }
      } else {
        log.warning("Failed response code {} {} for {}", responseCode.intValue, responseCode.reason, request)
        entity.discardBytes()
        request.respondError(
          new IllegalStateException(s"Failed response code ${responseCode.intValue} ${responseCode.reason}")
        )
      }
      sender() ! AckSink
    case (Success(HttpResponse(e @ ServerError(intValue), _, entity, _)), request: Request[_, _]) =>
      log.error("Server error {} {}", intValue, e.reason)
      entity.toStrict(1.seconds).onComplete {
        case Success(ent) => log.error(ent.toString())
        case Failure(_)   => entity.discardBytes()
      }
      sender() ! AckSink

      request.respondError(new IllegalStateException(s"Server error: $intValue"))
    case (Success(HttpResponse(e @ ClientError(intValue), _, entity, _)), request: Request[_, _]) =>
      log.error("Client error {}: {}", intValue, e.reason)
      entity.discardBytes()
      request.respondError(new IllegalStateException(s"Client error: $intValue"))
    case (Failure(e), request: Request[_, _]) =>
      request.respondError(e)
      throw e
  }
}
object RequestResponder {
  def props(parent: ActorRef, responseFunc: ((Request[A, _], A) forSome {type A}) => Unit)(
      implicit mat: Materializer
  ): Props =
    Props(new RequestResponder(parent, responseFunc))

  private[rest] case object InitSink
  private[rest] case object AckSink
  private[rest] case object CompletedSink
}
