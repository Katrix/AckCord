package ackcord.requests.base

import sttp.model.{Method, Uri}
import sttp.client3.UriContext

import Parameters._

case class Route(uriWithMajor: String, uriWithoutMajor: String, applied: Uri, queryParts: Vector[(String, String)]) {
  require(
    uriWithMajor.count(_ == '/') == applied.toString.count(_ == '/'),
    "Raw route and applied route are unbalanced"
  )

  def toRequest(method: Method): RequestRoute = RequestRoute(this, method)

  def /(next: String): Route =
    if (next.isEmpty) this
    else Route(s"$uriWithMajor/$next", s"$uriWithoutMajor/$next", uri"$applied/$next", queryParts)

  def /[A](parameter: MinorParameter[A]): Route =
    Route(
      s"$uriWithMajor/${parameter.name}",
      s"$uriWithoutMajor/${parameter.name}",
      uri"$applied/${parameter.print}",
      queryParts
    )

  def /[A](parameter: MajorParameter[A]): Route =
    Route(
      s"$uriWithMajor/${parameter.print}",
      s"$uriWithoutMajor/${parameter.name}",
      uri"$applied/${parameter.print}",
      queryParts
    )

  def /(other: Route): Route =
    if (other.uriWithMajor.isEmpty) this
    else
      Route(
        s"$uriWithMajor/${other.uriWithMajor}",
        s"$uriWithoutMajor/${other.uriWithoutMajor}",
        uri"$applied/${other.applied}",
        queryParts ++ other.queryParts
      )

  def ++(other: String): Route =
    Route(s"$uriWithMajor$other", s"$uriWithoutMajor$other", uri"$applied$other", queryParts)

  def ++[A](parameter: ConcatParameter[A]): Route =
    Route(
      s"$uriWithMajor${parameter.print}",
      s"$uriWithoutMajor${parameter.print}",
      uri"$applied${parameter.print}",
      queryParts
    )

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
        Route(uriWithMajor, uriWithoutMajor, applied, queryParts)
    }

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
