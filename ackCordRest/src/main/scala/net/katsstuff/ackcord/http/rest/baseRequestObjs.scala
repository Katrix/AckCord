package net.katsstuff.ackcord.http.rest

import scala.concurrent.Future
import scala.language.higherKinds

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, RequestEntity, ResponseEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Flow
import cats.{Monad, MonadError}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe._
import net.katsstuff.ackcord.CacheSnapshotLike
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.requests.{`X-Audit-Log-Reason`, Request}
import net.katsstuff.ackcord.util.{AckCordSettings, MapWithMaterializer}

/**
  * Base trait for all REST requests in AckCord. If you feel an endpoint is
  * missing, and AckCord hasn't added it yet, you can extend this and create
  * your own request. I'd recommend you to extend
  * [[RESTRequest]] tough for simplicity.
  *
  * @tparam RawResponse The response type of the request
  * @tparam NiceResponse A nicer and less raw type of response created from the response.
  */
trait BaseRESTRequest[RawResponse, NiceResponse, Ctx] extends Request[RawResponse, Ctx] {

  override def parseResponse(
      parallelism: Int
  )(implicit system: ActorSystem): Flow[ResponseEntity, RawResponse, NotUsed] = {
    val baseFlow = MapWithMaterializer
      .flow { implicit mat => responseEntity: ResponseEntity =>
        Unmarshal(responseEntity).to[Json]
      }
      .mapAsyncUnordered(parallelism)(identity)

    val withLogging =
      if (AckCordSettings().LogReceivedREST)
        baseFlow.log(
          s"Received REST response",
          json => s"From ${route.uri} with method ${route.method.value} and content ${json.noSpaces}"
        )
      else baseFlow

    withLogging.mapAsyncUnordered(parallelism)(json => Future.fromTry(json.as(responseDecoder).toTry))
  }

  /**
    * A decoder to decode the response.
    */
  def responseDecoder: Decoder[RawResponse]

  /**
    * Convert the response to a format the cache handler can understand.
    */
  def toNiceResponse(response: RawResponse): NiceResponse

  /**
    * The permissions needed to use this request.
    */
  def requiredPermissions: Permission = Permission.None

  /**
    * Check if a client has the needed permissions to execute this request.
    */
  def hasPermissions[F[_]: Monad](implicit c: CacheSnapshotLike[F]): F[Boolean] = Monad[F].pure(true)
}

/**
  * A simpler, request trait where the params are defined explicitly and converted to json.
  * @tparam Params The json parameters of the request.
  */
trait RESTRequest[Params, RawResponse, NiceResponse, Ctx] extends BaseRESTRequest[RawResponse, NiceResponse, Ctx] {

  /**
    * The params of this request
    */
  def params: Params

  /**
    * An encoder for the params of this request
    */
  def paramsEncoder: Encoder[Params]

  override def bodyForLogging: Option[String] = Some(jsonPrinter.pretty(jsonParams))

  /**
    * The params of this request converted to json.
    */
  def jsonParams: Json = paramsEncoder(params)

  def jsonPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)

  def requestBody: RequestEntity =
    if (params == NotUsed) HttpEntity.Empty
    else HttpEntity(ContentTypes.`application/json`, jsonParams.pretty(jsonPrinter))
}

/**
  * A complex REST request with an audit log reason.
  */
trait ReasonRequest[Self <: ReasonRequest[Self, Params, RawResponse, NiceResponse, Ctx], Params, RawResponse, NiceResponse, Ctx]
    extends RESTRequest[Params, RawResponse, NiceResponse, Ctx] {

  /**
    * A reason to add to the audit log entry.
    */
  def withReason(reason: String): Self

  def reason: Option[String]

  override def extraHeaders: Seq[HttpHeader] =
    reason.fold[Seq[HttpHeader]](Nil)(str => Seq(`X-Audit-Log-Reason`(str)))
}

/**
  * A request that takes no params.
  */
trait NoParamsRequest[RawResponse, NiceResponse, Ctx] extends RESTRequest[NotUsed, RawResponse, NiceResponse, Ctx] {
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
  override def params:        NotUsed          = NotUsed
}

/**
  * A request that takes no params with an audit log reason.
  */
trait NoParamsReasonRequest[Self <: NoParamsReasonRequest[Self, RawResponse, NiceResponse, Ctx], RawResponse, NiceResponse, Ctx]
    extends ReasonRequest[Self, NotUsed, RawResponse, NiceResponse, Ctx] {
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
  override def params:        NotUsed          = NotUsed
}

/**
  * A request where the response type and the nice response type are the same.
  */
trait NoNiceResponseRequest[Params, Response, Ctx] extends RESTRequest[Params, Response, Response, Ctx] {
  override def toNiceResponse(response: Response): Response = response
}

/**
  * A request, with an audit log reason, where the response type and
  * the nice response type are the same.
  */
trait NoNiceResponseReasonRequest[Self <: NoNiceResponseReasonRequest[Self, Params, Response, Ctx], Params, Response, Ctx]
    extends ReasonRequest[Self, Params, Response, Response, Ctx] {
  override def toNiceResponse(response: Response): Response = response
}

/**
  * A request that takes no params, and where the response type and the
  * nice response type are the same.
  */
trait NoParamsNiceResponseRequest[Response, Ctx]
    extends NoParamsRequest[Response, Response, Ctx]
    with NoNiceResponseRequest[NotUsed, Response, Ctx]

/**
  * A request, with an audit log reason, that takes no params, and where the response type and the
  * nice response type are the same.
  */
trait NoParamsNiceResponseReasonRequest[Self <: NoParamsNiceResponseReasonRequest[Self, Response, Ctx], Response, Ctx]
    extends NoParamsReasonRequest[Self, Response, Response, Ctx]
    with NoNiceResponseReasonRequest[Self, NotUsed, Response, Ctx]

/**
  * A request that doesn't have a response.
  */
trait NoResponseRequest[Params, Ctx] extends NoNiceResponseRequest[Params, NotUsed, Ctx] {
  override def responseDecoder: Decoder[NotUsed] = (_: HCursor) => Right(NotUsed)
}

/**
  * A request, with an audit log reason, that doesn't have a response.
  */
trait NoResponseReasonRequest[Self <: NoResponseReasonRequest[Self, Params, Ctx], Params, Ctx]
    extends NoNiceResponseReasonRequest[Self, Params, NotUsed, Ctx] {
  override def responseDecoder: Decoder[NotUsed] = (_: HCursor) => Right(NotUsed)
}

/**
  * A request that has neither params nor a response.
  */
trait NoParamsResponseRequest[Ctx] extends NoParamsRequest[NotUsed, NotUsed, Ctx] with NoResponseRequest[NotUsed, Ctx]

/**
  * A request that has neither params nor a response with a reason.
  */
trait NoParamsResponseReasonRequest[Self <: NoParamsResponseReasonRequest[Self, Ctx], Ctx]
    extends NoParamsReasonRequest[Self, NotUsed, NotUsed, Ctx]
    with NoResponseReasonRequest[Self, NotUsed, Ctx]
