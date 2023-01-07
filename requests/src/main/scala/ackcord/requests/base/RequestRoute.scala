package ackcord.requests.base

import ackcord.data.AckCordInfo
import sttp.client3._
import sttp.model.{Method, Uri}

/**
  * Used by requests for specifying an uri to send to, together with a method to
  * use.
  * @param uriWithMajor
  *   A string containing the route without any minor parameters filled in
  * @param uriWithoutMajor
  *   A string containing the route without any major or minor parameters filled
  *   in
  * @param uri
  *   The uri to send to
  * @param method
  *   The method to use
  */
case class RequestRoute(uriWithMajor: String, uriWithoutMajor: String, uri: Uri, method: Method) {

  /**
    * Set the URI and method of a Sttp request.
    * @param base
    *   The base URI to add on to the URI in this object. The scheme and
    *   authority are replaced with the ones found on the base. The segments of
    *   the base come before the segments of the route.
    * @param request
    *   The Sttp request object to modify.
    */
  def setSttpUriMethod[T, R](base: Uri, request: RequestT[Empty, T, R]): RequestT[Identity, T, R] =
    request.method(
      method,
      uri.copy(
        scheme = base.scheme,
        authority = base.authority,
        pathSegments = base.pathSegments.addSegments(uri.pathSegments.segments),
        querySegments = base.querySegments ++ uri.querySegments
      )
    )
}
object RequestRoute {

  val defaultBase: Uri = Uri(s"https://discord.com/api/v${AckCordInfo.DiscordApiVersion}")

  /**
    * Create a [[RequestRoute]] from a [[Route]] using the raw and applied
    * values for the this route, and adding the query at the end.
    */
  def apply(route: Route, method: Method): RequestRoute =
    RequestRoute(
      route.uriWithMajor,
      route.uriWithoutMajor,
      route.applied.addQuerySegments(route.queryParts.map(t => Uri.QuerySegment.KeyValue(t._1, t._2))),
      method
    )
}
