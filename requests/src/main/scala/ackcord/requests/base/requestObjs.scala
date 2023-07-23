package ackcord.requests.base

import java.io.InputStream
import java.nio.file.Path
import java.util.UUID

import ackcord.data.{CacheSnapshot, GuildChannelId, GuildId, Permissions}
import ackcord.requests.Request
import cats.data.Ior
import io.circe.{Decoder, Encoder, Json}
import sttp.capabilities.Streams
import sttp.client3.circe._
import sttp.client3.{Request => _, Response => _, _}
import sttp.model.{Part, Uri}

trait AckCordRequest[Response, -R] {
  def route: RequestRoute

  def bodyForLogging: String

  def identifier: UUID

  def hasPermissions(implicit c: CacheSnapshot): Boolean

  def toSttpRequest(base: Uri): RequestT[Identity, Either[Throwable, Either[String, Response]], R]
}

case class ComplexRequest[Params, Response, -R1, -R2](
    route: RequestRoute,
    requestBody: EncodeBody[Params, R1],
    parseResponse: ParseResponse[Response, R2],
    extraHeaders: Map[String, String] = Map.empty,
    requiredPermissions: Permissions = Permissions.None,
    permissionContext: Option[Ior[GuildId, GuildChannelId]] = None
) extends AckCordRequest[Response, R1 with R2] {

  def bodyForLogging: String = requestBody.bodyForLogging

  val identifier: UUID = UUID.randomUUID()

  def hasPermissions(implicit c: CacheSnapshot): Boolean = true //TODO

  def toSttpRequest(base: Uri): RequestT[Identity, Either[Throwable, Either[String, Response]], R1 with R2] = {
    val start            = basicRequest
    val withUriMethod    = route.setSttpUriMethod(base, start)
    val withBody         = requestBody.setSttpBody(withUriMethod)
    val withExtraHeaders = withBody.headers(extraHeaders)
    parseResponse.setSttpResponse(withExtraHeaders)
  }
}
object ComplexRequest {

  def complexBaseRestRequest[Params, Response, R1, R2](
      route: RequestRoute,
      requestBody: EncodeBody[Params, R1] = EncodeBody.NoBody,
      parseResponse: Option[ParseResponse[Response, R2]] = None,
      extraHeaders: Map[String, String] = Map.empty,
      requiredPermissions: Permissions = Permissions.None
  )(
      implicit ev: shapeless.OrElse[ParseResponse[Unit, Any] =:= ParseResponse[Response, R2], Decoder[Response]]
  ): ComplexRequest[Params, Response, R1, R2] = ComplexRequest(
    route,
    requestBody,
    parseResponse.getOrElse(
      ev.fold(
        ev => ev(ParseResponse.ExpectNoBody),
        decoder => ParseResponse.AsJsonResponse()(decoder)
      )
    ),
    extraHeaders,
    requiredPermissions
  )

  def baseRestRequest[Params, Response](
      route: RequestRoute,
      requestBody: EncodeBody[Params, Any] = EncodeBody.NoBody,
      parseResponse: Option[ParseResponse[Response, Any]] = None,
      extraHeaders: Map[String, String] = Map.empty,
      requiredPermissions: Permissions = Permissions.None
  )(
      implicit ev: shapeless.OrElse[ParseResponse[Unit, Any] =:= ParseResponse[Response, Any], Decoder[Response]]
  ): Request[Params, Response] =
    complexBaseRestRequest(route, requestBody, parseResponse, extraHeaders, requiredPermissions)

  //noinspection ComparingUnrelatedTypes
  def complexRestRequest[Params: Encoder, Response: Decoder, R1, R2](
      route: RequestRoute,
      params: Params = (),
      requestBody: Option[EncodeBody[Params, R1]] = None,
      parseResponse: Option[ParseResponse[Response, R2]] = None,
      extraHeaders: Map[String, String] = Map.empty,
      requiredPermissions: Permissions = Permissions.None
  ): ComplexRequest[Params, Response, R1, R2] = complexBaseRestRequest(
    route,
    requestBody.getOrElse(
      if (params == ()) EncodeBody.NoBody.asInstanceOf[EncodeBody[Params, R1]] else EncodeBody.EncodeJson(params)
    ),
    parseResponse,
    extraHeaders,
    requiredPermissions
  )

  def restRequest[Params: Encoder, Response: Decoder](
      route: RequestRoute,
      params: Params = (),
      requestBody: Option[EncodeBody[Params, Any]] = None,
      parseResponse: Option[ParseResponse[Response, Any]] = None,
      extraHeaders: Map[String, String] = Map.empty,
      requiredPermissions: Permissions = Permissions.None
  ): Request[Params, Response] = complexRestRequest(
    route,
    params,
    requestBody,
    parseResponse,
    extraHeaders,
    requiredPermissions
  )

}

trait EncodeBody[-Params, -R] {
  def bodyForLogging: String

  def setSttpBody[R1, T](request: RequestT[Identity, T, R1]): RequestT[Identity, T, R1 with R]
}
object EncodeBody {
  case object NoBody extends EncodeBody[Unit, Any] {
    override def bodyForLogging: String = "None"

    override def setSttpBody[R1, T](request: RequestT[Identity, T, R1]): RequestT[Identity, T, R1 with Any] = request
  }

  case class EncodeJson[Params](params: Params)(implicit val encoder: Encoder[Params]) extends EncodeBody[Params, Any] {
    override def bodyForLogging: String = encoder(params).noSpaces

    override def setSttpBody[R1, T](request: RequestT[Identity, T, R1]): RequestT[Identity, T, R1] =
      request.body(params)
  }
  case class InputStreamBody(data: InputStream) extends EncodeBody[InputStream, Any] {
    override def bodyForLogging: String = "Sending byte body"

    override def setSttpBody[R1, T](request: RequestT[Identity, T, R1]): RequestT[Identity, T, R1 with Any] =
      request.body(data)
  }

  trait Multipart[+A, -R] {
    def filename: String

    def withName(name: String): Multipart[A, R]

    def bodyForLogging: String

    def toSttpPart: Part[RequestBody[R]]
  }
  object Multipart {
    case class EncodeJson[Params](params: Params, name: String)(implicit val encoder: Encoder[Params])
        extends Multipart[Params, Any] {
      override def bodyForLogging: String = s"$name: ${encoder(params).noSpaces}"

      override def toSttpPart: Part[RequestBody[Any]] = multipart(name, params).fileName(filename)

      override def withName(name: String): EncodeJson[Params] = copy(name = name)

      override def filename: String = name
    }
    case class FilePart(file: Path, name: Option[String], fileNameOpt: Option[String]) extends Multipart[Path, Any] {
      override def bodyForLogging: String = s"$name: Sending file ${file.getFileName}"

      override def toSttpPart: Part[RequestBody[Any]] =
        multipartFile(name.getOrElse(file.getFileName.toString), file).fileName(filename)

      override def withName(name: String): FilePart = copy(name = Some(name))

      override def filename: String = fileNameOpt.getOrElse(file.getFileName.toString)
    }
    case class InputStreamPart(data: InputStream, name: String, filename: String) extends Multipart[InputStream, Any] {
      override def bodyForLogging: String = s"$name: Sending byte body"

      override def toSttpPart: Part[RequestBody[Any]] = multipart(name, data).fileName(filename)

      override def withName(name: String): InputStreamPart = copy(name = name)
    }
    case class StreamPart[S, BinStream](
        s: Streams[S] { type BinaryStream = BinStream },
        data: BinStream,
        name: String,
        filename: String
    ) extends Multipart[Nothing, S] {
      override def bodyForLogging: String = s"$name: Sending stream"

      override def toSttpPart: Part[RequestBody[S]] = multipartStream(s)(name, data).fileName(filename)

      override def withName(name: String): StreamPart[S, BinStream] = copy(name = name)
    }
    case class StringPart(data: String, name: String) extends Multipart[String, Any] {
      override def filename: String = name

      override def withName(name: String): StringPart = copy(name = name)

      override def bodyForLogging: String = data

      override def toSttpPart: Part[RequestBody[Any]] = multipart(name, data)
    }
    case class FormDataPart(data: Map[String, String], name: String) extends Multipart[Map[String, String], Any] {
      override def filename: String = name

      override def withName(name: String): FormDataPart = copy(name = name)

      override def bodyForLogging: String = data.toString()

      override def toSttpPart: Part[RequestBody[Any]] = multipart(name, data)
    }
  }

  case class MultipartBody[Params, -R2](mainPart: Multipart[Params, R2], extraParts: Seq[Multipart[_, R2]])
      extends EncodeBody[Params, R2] {
    override def setSttpBody[R1, T](request: RequestT[Identity, T, R1]): RequestT[Identity, T, R1 with R2] =
      request.multipartBody(mainPart.toSttpPart, extraParts.map(_.toSttpPart): _*)

    override def bodyForLogging: String =
      s"Sending multipart body:\n${mainPart.bodyForLogging}\n${extraParts.map(_.bodyForLogging).mkString("\n")}"
  }
}

//TODO: Logging
trait ParseResponse[Response, -R] { self =>
  def setSttpResponse[T, R1](
      request: RequestT[Identity, T, R1]
  ): RequestT[Identity, Either[Throwable, Either[String, Response]], R1 with R]

  def map[NewResponse](f: Response => NewResponse): ParseResponse[NewResponse, R] = new ParseResponse[NewResponse, R] {
    override def setSttpResponse[T, R1](
        request: RequestT[Identity, T, R1]
    ): RequestT[Identity, Either[Throwable, Either[String, NewResponse]], R1 with R] = {
      val selfRequest = self.setSttpResponse(request)
      selfRequest.response(selfRequest.response.map(_.map(_.map(f))))
    }
  }
}
object ParseResponse {
  case object ExpectNoBody extends ParseResponse[Unit, Any] {
    override def setSttpResponse[T, R1](
        request: RequestT[Identity, T, R1]
    ): RequestT[Identity, Either[Throwable, Either[String, Unit]], R1 with Any] =
      request.response(asEither(asStringAlways, ignore).map(Right(_)))
  }
  case object AsJson extends ParseResponse[Json, Any] {
    override def setSttpResponse[T, R1](
        request: RequestT[Identity, T, R1]
    ): RequestT[Identity, Either[Throwable, Either[String, Json]], R1 with Any] =
      request.response(asEither(asStringAlways, asJsonAlways[Json]).map {
        case Right(Right(json)) => Right(Right(json))
        case Right(Left(err))   => Left(err)
        case Left(err)          => Right(Left(err))
      })
  }

  case class AsJsonResponse[A]()(implicit decoder: Decoder[A]) extends ParseResponse[A, Any] {
    override def setSttpResponse[T, R1](
        request: RequestT[Identity, T, R1]
    ): RequestT[Identity, Either[Throwable, Either[String, A]], R1 with Any] =
      request.response(asEither(asStringAlways, asJsonAlways[A]).map {
        case Right(Right(json)) => Right(Right(json))
        case Right(Left(err))   => Left(err)
        case Left(err)          => Right(Left(err))
      })
  }
}
