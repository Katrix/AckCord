/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
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
package net.katsstuff.akkacord.http.rest

import scala.collection.{immutable, mutable}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.StatusCodes.{ClientError, ServerError}
import akka.http.scaladsl.model.headers.{Authorization, HttpCredentials, `User-Agent`}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import net.katsstuff.akkacord.http.rest.RESTHandler.{ProcessedRequest, RateLimitStop, RemoveRateLimit}
import net.katsstuff.akkacord.http.rest.Requests.{CreateMessage, CreateMessageData}
import net.katsstuff.akkacord.{AkkaCord, DiscordClient, Request, RequestHandlerEvent}

//Designed after http://doc.akka.io/docs/akka-http/current/scala/http/client-side/host-level.html#examples
class RESTHandler(token: HttpCredentials, snowflakeCache: ActorRef)(implicit mat: Materializer)
    extends Actor
    with ActorLogging
    with FailFastCirceSupport {

  import context.dispatcher

  private val requestQueue = {
    val poolClientFlow = Http(context.system).cachedHostConnectionPoolHttps[ProcessedRequest[_, _, _]]("discordapp.com")
    val selfSink = Sink.actorRefWithAck[(Try[HttpResponse], ProcessedRequest[_, _, _])](
      ref = self,
      onInitMessage = RESTHandler.InitSink,
      ackMessage = RESTHandler.AckSink,
      onCompleteMessage = RESTHandler.CompletedSink,
    )

    Source
      .queue[(HttpRequest, ProcessedRequest[_, _, _])](64, OverflowStrategy.fail)
      .via(poolClientFlow)
      .toMat(selfSink)(Keep.left)
      .run()
  }

  private val rateLimits           = new mutable.HashMap[Uri, Int]
  private val globalRateLimitQueue = new mutable.Queue[Any]

  private val auth      = Authorization(token)
  private val userAgent = `User-Agent`(s"DiscordBot (https://github.com/Katrix-/AkkaCord, ${AkkaCord.Version})")

  override def receive: Receive = {
    case DiscordClient.ShutdownClient =>
      requestQueue.watchCompletion().foreach { _ =>
        context.stop(self)
      }
      requestQueue.complete()
    case RESTHandler.InitSink =>
      sender() ! RESTHandler.AckSink
    case RemoveRateLimit(uri) => rateLimits.remove(uri)
    //The d and h here makes IntelliJ happy. They serves no other purpose
    case Request(request: ComplexRESTRequest[_, d, h], contextual, sendResponseTo)
        if !rateLimits.contains(request.route.uri) =>
      val body = request match {
        case CreateMessage(_, CreateMessageData(_, _, _, Some(file), _)) =>
          val filePart =
            FormData.BodyPart.fromPath(file.getFileName.toString, ContentTypes.`application/octet-stream`, file)
          val jsonPart = FormData.BodyPart(
            "payload_json",
            HttpEntity(ContentTypes.`application/json`, request.toJsonParams.noSpaces)
          )

          FormData(filePart, jsonPart).toEntity()
        case _ =>
          if (request.params == NotUsed) HttpEntity.Empty
          else HttpEntity(ContentTypes.`application/json`, request.toJsonParams.noSpaces)
      }

      val route       = request.route
      val httpRequest = HttpRequest(route.method, route.uri, immutable.Seq(auth, userAgent), body)

      log.debug(s"Sending request $httpRequest to ${route.uri} with method ${route.method}")

      queueRequest(httpRequest, request, contextual, sendResponseTo)
    case Success((HttpResponse(StatusCodes.TooManyRequests, headers, entity, _), req: ProcessedRequest[_, _, _])) =>
      updateRateLimit(req.restRequest.route.uri, headers)
      handleRatelimit(headers, req)
      entity.discardBytes()
      sender() ! RESTHandler.AckSink
    case (Success(HttpResponse(StatusCodes.NoContent, headers, entity, _)), ProcessedRequest(request, _, _))
        if request.expectedResponseCode == StatusCodes.NoContent =>
      updateRateLimit(request.route.uri, headers)
      entity.discardBytes()
      sender() ! RESTHandler.AckSink
    case (Success(HttpResponse(response, headers, entity, _)), ProcessedRequest(request, ctx, sendTo)) =>
      updateRateLimit(request.route.uri, headers)
      if (request.expectedResponseCode == response) {
        Unmarshal(entity)
          .to[Json]
          .flatMap { json =>
            log.debug(s"Received response: ${json.noSpaces}")
            Future.fromTry(json.as(request.responseDecoder).map(request.processResponse).toTry)
          }
          .map(data => RequestHandlerEvent(data, sendTo, ctx)(request.handleResponse))
          .pipeTo(snowflakeCache)
      } else {
        log.warning(s"Unexpected response code ${response.intValue()} for $request")
        entity.discardBytes()
      }
      sender() ! RESTHandler.AckSink
    case (Success(HttpResponse(e @ ServerError(intValue), _, entity, _)), _) =>
      log.error(s"Server error $intValue ${e.reason}")
      entity.toStrict(1.seconds).onComplete {
        case Success(ent) => log.error(ent.toString())
        case Failure(_)   => entity.discardBytes()
      }
      sender() ! RESTHandler.AckSink
    case (Success(HttpResponse(e @ ClientError(intValue), _, entity, _)), _) =>
      log.error(s"Client error $intValue: ${e.reason}")
      entity.discardBytes()
    case Status.Failure(e) =>
      e.printStackTrace()
      sender() ! RESTHandler.AckSink
    case Failure(e) => e.printStackTrace()
    //If we get here then the request is rate limited
    case fullRequest @ Request(request: ComplexRESTRequest[_, _, _], _, _) =>
      val duration = rateLimits.getOrElse(request.route.uri, 0)
      context.system.scheduler.scheduleOnce(duration.millis, self, fullRequest)
  }

  def onGlobalRateLimit: Receive = {
    case DiscordClient.ShutdownClient =>
      requestQueue.watchCompletion().foreach { _ =>
        context.stop(self)
      }
      requestQueue.complete()
    case RateLimitStop =>
      context.unbecome()
      globalRateLimitQueue.foreach(self ! _)
      globalRateLimitQueue.clear()
    case others =>
      globalRateLimitQueue.enqueue(others)
  }

  def updateRateLimit(uri: Uri, headers: Seq[HttpHeader]): Unit = {
    for {
      remaining <- headers.find(_.is("X-RateLimit-Remaining"))
      remainingInt = remaining.value().toInt
      if remainingInt <= 0
      retry <- headers.find(_.is("Retry-After"))
    } {
      val retryInt = retry.value().toInt
      rateLimits.put(uri, retryInt)
      context.system.scheduler.scheduleOnce(retryInt.millis, self, RemoveRateLimit(uri))
    }
  }

  def handleRatelimit[A](headers: Seq[HttpHeader], retryMsg: A): Unit = {
    if (headers.exists(header => header.is("X-RateLimit-Global") && header.value == "true")) {
      log.error("Hit global rate limit")
      headers
        .find(_.is("Retry-After"))
        .map(_.value().toLong)
        .orElse(headers.find(_.is("X-RateLimit-Reset")).map(_.value().toLong - System.currentTimeMillis())) match {
        case Some(duration) =>
          context.become(onGlobalRateLimit, discardOld = false)
          context.system.scheduler.scheduleOnce(duration.millis, self, RateLimitStop)
        case None => log.error("No retry time for global rate limit")
      }
    } else {
      headers
        .find(_.is("Retry-After"))
        .foreach(time => context.system.scheduler.scheduleOnce(time.value.toLong.millis, self, retryMsg))
    }
  }

  def queueRequest(
      httpRequest: HttpRequest,
      restRequest: ComplexRESTRequest[_, _, _],
      context: _,
      sendTo: Option[ActorRef]
  ): Unit = {
    requestQueue
      .offer(httpRequest -> ProcessedRequest(restRequest, context, sendTo))
      .foreach {
        case QueueOfferResult.Enqueued    => //All is fine
        case QueueOfferResult.Dropped     => self ! Failure(new RuntimeException("Queue overflowed."))
        case QueueOfferResult.Failure(ex) => self ! Failure(ex)
        case QueueOfferResult.QueueClosed =>
          self ! Failure(new RuntimeException("Queue was closed (pool shut down) while running the request."))
      }
  }
}
object RESTHandler {
  def props(token: HttpCredentials, snowflakeCache: ActorRef): Props =
    Props(new RESTHandler(token, snowflakeCache))
  case object RateLimitStop
  case class RemoveRateLimit(uri: Uri)

  private case class ProcessedRequest[Response, HandlerType, Context](
      restRequest: ComplexRESTRequest[_, Response, HandlerType],
      context: Context,
      sendTo: Option[ActorRef]
  )

  private case object InitSink
  private case object AckSink
  private case object CompletedSink
}
