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
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.StatusCodes.{ClientError, ServerError}
import akka.http.scaladsl.model.headers.{`User-Agent`, Authorization, HttpCredentials}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import net.katsstuff.akkacord.http.rest.RESTHandler.{RateLimitStop, RemoveRateLimit}
import net.katsstuff.akkacord.http.rest.Requests.CreateMessage
import net.katsstuff.akkacord.{AkkaCord, DiscordClient, Request, RequestHandlerEvent}

//Designed after http://doc.akka.io/docs/akka-http/current/scala/http/client-side/host-level.html#examples
class RESTHandler(token: HttpCredentials, snowflakeCache: ActorRef)
    extends Actor
    with ActorLogging
    with FailFastCirceSupport {

  import context.dispatcher
  implicit val mat = ActorMaterializer()

  private val requestQueue = {
    val poolClientFlow = Http(context.system).cachedHostConnectionPoolHttps[Promise[HttpResponse]]("discordapp.com")
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](64, OverflowStrategy.fail)
      .via(poolClientFlow)
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p)    => p.failure(e)
      })(Keep.left)
      .run()
  }

  private val rateLimits           = new mutable.HashMap[Uri, Int]
  private val globalRateLimitQueue = new mutable.Queue[Any]

  private val auth      = Authorization(token)
  private val userAgent = `User-Agent`(s"DiscordBot (https://github.com/Katrix-/AkkaCord, ${AkkaCord.Version})")

  override def receive: Receive = {
    case DiscordClient.ShutdownClient =>
      requestQueue.watchCompletion().foreach { _ =>
        mat.shutdown()
        context.stop(self)
      }
      requestQueue.complete()
    case RemoveRateLimit(uri) => rateLimits.remove(uri)
    case fullRequest @ Request(request: ComplexRESTRequest[_, d, h], contextual, sendResponseTo) //The d and h here makes IntelliJ happy. They serves no other purpose
        if !rateLimits.contains(request.route.uri) =>
      val body = request match {
        case CreateMessage(_, data) if data.file.isDefined =>
          val file = data.file.get
          val filePart =
            FormData.BodyPart.fromPath(file.getFileName.toString, ContentTypes.`application/octet-stream`, file)
          val jsonPart = FormData.BodyPart(
            "payload_json",
            HttpEntity(ContentTypes.`application/json`, request.toJsonParams.noSpaces)
          )

          FormData(filePart, jsonPart).toEntity()
        case _ =>
          val params =
            if (request.params == NotUsed) None
            else Some(request.toJsonParams)

          params.fold(HttpEntity.Empty)(json => HttpEntity(ContentTypes.`application/json`, json.noSpaces))
      }

      val route = request.route

      val httpRequest = HttpRequest(method = route.method, uri = route.uri, immutable.Seq(auth, userAgent), body)

      log.debug(s"Sending request $httpRequest")

      queueRequest(httpRequest).onComplete {
        case Success(HttpResponse(StatusCodes.TooManyRequests, headers, entity, _)) =>
          updateRateLimit(route.uri, headers)
          handleRatelimit(headers, fullRequest)
          entity.discardBytes()
        case Success(HttpResponse(StatusCodes.NoContent, headers, entity, _))
            if request.expectedResponseCode == StatusCodes.NoContent =>
          updateRateLimit(route.uri, headers)
          entity.discardBytes()
        case Success(HttpResponse(response, headers, entity, _)) =>
          updateRateLimit(route.uri, headers)
          if (request.expectedResponseCode == response) {
            Unmarshal(entity)
              .to[Json]
              .flatMap { json =>
                log.debug(s"Received response: ${json.noSpaces}")
                Future.fromTry(json.as(request.responseDecoder).map(request.processResponse).toTry)
              }
              .map(data => RequestHandlerEvent(data, sendResponseTo, contextual)(request.handleResponse))
              .pipeTo(snowflakeCache)
          } else {
            log.warning(s"Unexpected response code ${response.intValue()} for $request")
            entity.discardBytes()
          }
        case Success(HttpResponse(e @ ServerError(intValue), _, entity, _)) =>
          log.error(s"Server error $intValue ${e.reason}")
          entity.toStrict(1.seconds).onComplete {
            case Success(ent) => log.error(ent.toString())
            case Failure(_)   => entity.discardBytes()
          }
        case Success(HttpResponse(e @ ClientError(intValue), _, entity, _)) =>
          log.error(s"Client error $intValue: ${e.reason}")
          entity.discardBytes()
        case Failure(e) => e.printStackTrace()
      }
    //If we get here then the request is rate limited
    case fullRequest @ Request(request: ComplexRESTRequest[_, _, _], _, _) =>
      val duration = rateLimits.getOrElse(request.route.uri, 0)
      context.system.scheduler.scheduleOnce(duration.millis, self, fullRequest)
  }

  def onGlobalRateLimit: Receive = {
    case DiscordClient.ShutdownClient =>
      requestQueue.watchCompletion().foreach { _ =>
        context.stop(self)
        mat.shutdown()
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

  def handleRatelimit(headers: Seq[HttpHeader], request: Request[_, _]): Unit = {
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
        .foreach(time => context.system.scheduler.scheduleOnce(time.value.toLong.millis, self, request))
    }
  }

  def queueRequest(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    requestQueue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued    => responsePromise.future
      case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(new RuntimeException("Queue was closed (pool shut down) while running the request."))
    }
  }
}
object RESTHandler {
  def props(token: HttpCredentials, snowflakeCache: ActorRef): Props =
    Props(classOf[RESTHandler], token, snowflakeCache)
  case object RateLimitStop
  case class RemoveRateLimit(uri: Uri)
}
