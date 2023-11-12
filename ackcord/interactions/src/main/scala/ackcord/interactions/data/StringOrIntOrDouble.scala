package ackcord.interactions.data

import io.circe.{Codec, HCursor, Json}

sealed trait StringOrIntOrDouble
object StringOrIntOrDouble {
  case class OfString(s: String) extends StringOrIntOrDouble
  case class OfInt(i: Int)       extends StringOrIntOrDouble
  case class OfDouble(d: Double) extends StringOrIntOrDouble

  implicit val codec: Codec[StringOrIntOrDouble] = Codec.from(
    (c: HCursor) =>
      c.as[String]
        .map(OfString)
        .orElse(c.as[Int].map(OfInt))
        .orElse(c.as[Double].map(OfDouble)),
    {
      case OfString(s) => Json.fromString(s)
      case OfInt(i)    => Json.fromInt(i)
      case OfDouble(d) => Json.fromDoubleOrString(d)
    }
  )
}
