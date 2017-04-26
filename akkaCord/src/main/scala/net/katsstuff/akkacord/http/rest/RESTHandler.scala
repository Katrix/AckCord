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

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.StatusCodes.{ClientError, ServerError}
import akka.http.scaladsl.model.headers.{Authorization, HttpCredentials, `User-Agent`}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import net.katsstuff.akkacord.http.rest.RESTRequest.CreateMessage
import net.katsstuff.akkacord.{AkkaCord, DiscordClient}

//Designed after http://doc.akka.io/docs/akka-http/current/scala/http/client-side/host-level.html#examples
class RESTHandler(token: HttpCredentials, snowflakeCache: ActorRef) extends Actor with ActorLogging with FailFastCirceSupport {

  import akka.pattern.pipe

  import context.dispatcher
  implicit val mat = ActorMaterializer()

  private val requestQueue = {
    val poolClientFlow = Http(context.system).cachedHostConnectionPool[Promise[HttpResponse]]("discordapp.com")
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](64, OverflowStrategy.fail)
      .via(poolClientFlow)
      .toMat(Sink.foreach({
        case ((Success(resp), p)) => p.success(resp)
        case ((Failure(e), p))    => p.failure(e)
      }))(Keep.left)
      .run()
  }

  private val auth      = Authorization(token)
  private val userAgent = `User-Agent`(s"DiscordBot (https://github.com/Katrix-/AkkaCord, ${AkkaCord.Version})")

  override def receive: Receive = {
    case DiscordClient.ShutdownClient =>
      requestQueue.complete()
      self ! PoisonPill
    case request: RESTRequest[_, _] =>
      request match {
        case CreateMessage(_, data) if data.file.isDefined =>
          val file = data.file.get
          val filePart = FormData.BodyPart.fromPath(file.getFileName.toString, ContentTypes.`application/octet-stream`, file)
          ???
        case _ =>
      }

      val params =
        if (request.params == NotUsed) None
        else Some(request.toJsonParams)

      val route = request.route
      val httpRequest =
        HttpRequest(method = route.method, uri = route.uri, immutable.Seq(auth, userAgent), params.fold(HttpEntity.Empty)(_.noSpaces))

      val from = sender()

      queueRequest(httpRequest).onComplete {
        case Success(HttpResponse(StatusCodes.TooManyRequests, headers, entity, _)) =>
          handleHeaders(headers)
          entity.discardBytes()
          ???
        case Success(HttpResponse(StatusCodes.NoContent, headers, entity, _)) if request.expectedResponseCode == StatusCodes.NoContent =>
          handleHeaders(headers)
          entity.discardBytes()
        case Success(HttpResponse(response, headers, entity, _)) =>
          handleHeaders(headers)
          if (request.expectedResponseCode == response) {
            Unmarshal(entity).to[Json].flatMap(json => Future.fromTry(json.as(request.responseDecoder).toTry)).pipeTo(snowflakeCache)
          } else {
            log.warning(s"Unexpected response code ${response.intValue()} for $request")
            entity.discardBytes()
          }
        case Success(HttpResponse(e @ ServerError(intValue), _, entity, _)) =>
          log.error(s"Server error $intValue ${e.reason}")
          entity.toStrict(1.seconds).map(e => log.error(e.toString()))
        case Success(HttpResponse(e @ ClientError(intValue), _, entity, _)) =>
          log.error(s"Client error $intValue: ${e.reason}")
          entity.discardBytes()
        case Failure(e) => e.printStackTrace()
      }
  }

  def handleHeaders(headers: Seq[HttpHeader]): Unit = ???

  def queueRequest(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    requestQueue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued    => responsePromise.future
      case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
    }
  }
}
