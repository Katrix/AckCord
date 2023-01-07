package ackcord.requests.base

import Parameters._
import sttp.client3.UriContext
import sttp.model.{Method, Uri}

/**
  * A route to make requests to.
  * @param uriWithMajor
  *   The URI with major parameters visible
  * @param uriWithoutMajor
  *   The URI without major parameters visible
  * @param applied
  *   The fully applied URI, with both major and minor parameters visible
  * @param queryParts
  *   The query parts of the URI
  */
case class Route(uriWithMajor: String, uriWithoutMajor: String, applied: Uri, queryParts: Vector[(String, String)]) {
  require(
    uriWithMajor.count(_ == '/') == applied.toString.count(_ == '/'),
    "Raw route and applied route are unbalanced"
  )

  /** Finishes the construction of this route, by giving it a method. */
  def toRequest(method: Method): RequestRoute = RequestRoute(this, method)

  /** Adds a string as a path segment to the route. */
  def /(next: String): Route =
    if (next.isEmpty) this
    else Route(s"$uriWithMajor/$next", s"$uriWithoutMajor/$next", uri"$applied/$next", queryParts)

  /** Adds a minor parameter as a path segment to the route. */
  def /[A](parameter: MinorParameter[A]): Route =
    Route(
      s"$uriWithMajor/${parameter.name}",
      s"$uriWithoutMajor/${parameter.name}",
      uri"$applied/${parameter.print}",
      queryParts
    )

  /** Adds a major parameter as a path segment to the route. */
  def /[A](parameter: MajorParameter[A]): Route =
    Route(
      s"$uriWithMajor/${parameter.print}",
      s"$uriWithoutMajor/${parameter.name}",
      uri"$applied/${parameter.print}",
      queryParts
    )

  /**
    * Combine two routes together, placing the path segments of the passed in
    * route after the path segments of this route.
    */
  def /(other: Route): Route =
    if (other.uriWithMajor.isEmpty) this
    else
      Route(
        s"$uriWithMajor/${other.uriWithMajor}",
        s"$uriWithoutMajor/${other.uriWithoutMajor}",
        uri"$applied/${other.applied}",
        queryParts ++ other.queryParts
      )

  /**
    * Adds a raw string to this route without adding a new path segment. Query
    * parts are always added last, so this method can not be used to add those.
    */
  def ++(other: String): Route =
    Route(s"$uriWithMajor$other", s"$uriWithoutMajor$other", uri"$applied$other", queryParts)

  /**
    * Adds a concat parameter to this route without adding a new path segment.
    * Query parts are always added last, so this method can not be used to add
    * those.
    */
  def ++[A](parameter: ConcatParameter[A]): Route =
    Route(
      s"$uriWithMajor${parameter.print}",
      s"$uriWithoutMajor${parameter.print}",
      uri"$applied${parameter.print}",
      queryParts
    )

  /** Adds a new query parameter to this route if it has a value. */
  def +?[A](query: QueryParameter[A]): Route =
    query.value match {
      case Some(value) =>
        Route(
          uriWithMajor,
          uriWithoutMajor,
          applied,
          queryParts.appended((query.name, query.print(value)))
        )
      case None =>
        this
    }

  /**
    * Adds a new query parameter to this route as many times as it has values.
    */
  def ++?[A](query: SeqQueryParameter[A]): Route =
    query.value match {
      case None | Some(Nil) => Route(uriWithMajor, uriWithoutMajor, applied, queryParts)
      case Some(values) =>
        Route(
          uriWithMajor,
          uriWithoutMajor,
          applied,
          queryParts ++ values.map(value => query.name -> query.print(value)).toVector
        )
    }
}
