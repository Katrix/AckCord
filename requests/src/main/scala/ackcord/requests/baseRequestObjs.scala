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

import scala.concurrent.Future
import scala.util.{Failure, Success}

import ackcord.CacheSnapshot
import ackcord.data._
import ackcord.util.AckCordRequestSettings
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Sink, Source}
import io.circe._

/**
  * Base trait for all REST requests in AckCord. If you feel an endpoint is
  * missing, and AckCord hasn't added it yet, you can extend this and create
  * your own request. I'd recommend you to extend
  * [[RESTRequest]] tough for simplicity.
  *
  * @tparam RawResponse The response type of the request
  * @tparam NiceResponse A nicer and less raw type of response created from the response.
  */
trait BaseRESTRequest[RawResponse, NiceResponse] extends Request[RawResponse] { self =>

  override def parseResponse(entity: ResponseEntity)(implicit system: ActorSystem[Nothing]): Future[RawResponse] = {
    import system.executionContext
    implicit val logger: LoggingAdapter = Logging(system.classicSystem, "ackcord.rest.ReceivedRESTRequest")

    val baseFlow = RequestStreams.jsonDecode

    val withLogging =
      if (AckCordRequestSettings().LogReceivedREST) {
        baseFlow.log(
          s"Received REST response",
          json => s"From ${route.uri} with method ${route.method.value} and content ${json.noSpaces}"
        )
      } else baseFlow

    withLogging.runWith(Source.single(entity), Sink.head)._2.flatMap { json =>
      Future.fromTry(json.as(responseDecoder).fold(Failure.apply, Success.apply))
    }
  }

  /** A decoder to decode the response. */
  def responseDecoder: Decoder[RawResponse]

  /** Convert the response to a format the cache handler can understand. */
  def toNiceResponse(response: RawResponse): NiceResponse

  /** The permissions needed to use this request. */
  def requiredPermissions: Permission = Permission.None

  override def hasPermissions(implicit c: CacheSnapshot): Boolean = true
}

/**
  * A simpler, request trait where the params are defined explicitly and converted to json.
  * @tparam Params The json parameters of the request.
  */
trait RESTRequest[Params, RawResponse, NiceResponse] extends BaseRESTRequest[RawResponse, NiceResponse] {

  /** The params of this request */
  def params: Params

  /** An encoder for the params of this request */
  def paramsEncoder: Encoder[Params]

  override def bodyForLogging: Option[String] = Some(jsonPrinter.print(jsonParams))

  /** The params of this request converted to json. */
  def jsonParams: Json = paramsEncoder(params)

  def jsonPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)

  def requestBody: RequestEntity =
    if (params == NotUsed) HttpEntity.Empty
    else HttpEntity(ContentTypes.`application/json`, jsonParams.printWith(jsonPrinter))
}

/** A complex REST request with an audit log reason. */
trait ReasonRequest[Self <: ReasonRequest[Self, Params, RawResponse, NiceResponse], Params, RawResponse, NiceResponse]
    extends RESTRequest[Params, RawResponse, NiceResponse] {

  /** A reason to add to the audit log entry. */
  def withReason(reason: String): Self

  def reason: Option[String]

  override def extraHeaders: Seq[HttpHeader] = {
    require(reason.forall(_.length <= 512), "The reason to put in an audit log entry can't be more than 512 characters")
    reason.fold[Seq[HttpHeader]](Nil)(str => Seq(`X-Audit-Log-Reason`(str)))
  }
}

/** A request that takes no params. */
trait NoParamsRequest[RawResponse, NiceResponse] extends RESTRequest[NotUsed, RawResponse, NiceResponse] {
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
  override def params: NotUsed                 = NotUsed
}

/** A request that takes no params with an audit log reason. */
trait NoParamsReasonRequest[Self <: NoParamsReasonRequest[Self, RawResponse, NiceResponse], RawResponse, NiceResponse]
    extends ReasonRequest[Self, NotUsed, RawResponse, NiceResponse]
    with NoParamsRequest[RawResponse, NiceResponse]

/** A request where the response type and the nice response type are the same. */
trait NoNiceResponseRequest[Params, Response] extends RESTRequest[Params, Response, Response] {
  override def toNiceResponse(response: Response): Response = response
}

/**
  * A request, with an audit log reason, where the response type and
  * the nice response type are the same.
  */
trait NoNiceResponseReasonRequest[Self <: NoNiceResponseReasonRequest[Self, Params, Response], Params, Response]
    extends ReasonRequest[Self, Params, Response, Response]
    with NoNiceResponseRequest[Params, Response]

/**
  * A request that takes no params, and where the response type and the
  * nice response type are the same.
  */
trait NoParamsNiceResponseRequest[Response]
    extends NoParamsRequest[Response, Response]
    with NoNiceResponseRequest[NotUsed, Response]

/**
  * A request, with an audit log reason, that takes no params, and where the response type and the
  * nice response type are the same.
  */
trait NoParamsNiceResponseReasonRequest[Self <: NoParamsNiceResponseReasonRequest[Self, Response], Response]
    extends NoParamsReasonRequest[Self, Response, Response]
    with NoNiceResponseReasonRequest[Self, NotUsed, Response]

/** A request that doesn't have a response. */
trait NoResponseRequest[Params] extends NoNiceResponseRequest[Params, NotUsed] {

  override def parseResponse(entity: ResponseEntity)(implicit system: ActorSystem[Nothing]): Future[NotUsed] = {
    entity.discardBytes()
    Future.successful(NotUsed)
  }

  override def responseDecoder: Decoder[NotUsed] = (_: HCursor) => Right(NotUsed)
}

/** A request, with an audit log reason, that doesn't have a response. */
trait NoResponseReasonRequest[Self <: NoResponseReasonRequest[Self, Params], Params]
    extends NoNiceResponseReasonRequest[Self, Params, NotUsed]
    with NoResponseRequest[Params]

/** A request that has neither params nor a response. */
trait NoParamsResponseRequest extends NoParamsRequest[NotUsed, NotUsed] with NoResponseRequest[NotUsed]

/** A request that has neither params nor a response with a reason. */
trait NoParamsResponseReasonRequest[Self <: NoParamsResponseReasonRequest[Self]]
    extends NoParamsReasonRequest[Self, NotUsed, NotUsed]
    with NoResponseReasonRequest[Self, NotUsed]
