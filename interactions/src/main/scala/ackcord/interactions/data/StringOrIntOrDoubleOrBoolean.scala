package ackcord.interactions.data

import io.circe.{Codec, HCursor, Json}

sealed trait StringOrIntOrDoubleOrBoolean
object StringOrIntOrDoubleOrBoolean {
  case class OfString(s: String)   extends StringOrIntOrDoubleOrBoolean
  case class OfInt(i: Int)         extends StringOrIntOrDoubleOrBoolean
  case class OfDouble(d: Double)   extends StringOrIntOrDoubleOrBoolean
  case class OfBoolean(b: Boolean) extends StringOrIntOrDoubleOrBoolean

  implicit val codec: Codec[StringOrIntOrDoubleOrBoolean] = Codec.from(
    (c: HCursor) =>
      c.as[String]
        .map(OfString)
        .orElse(c.as[Int].map(OfInt))
        .orElse(c.as[Double].map(OfDouble))
        .orElse(c.as[Boolean].map(OfBoolean)),
    {
      case OfString(s)  => Json.fromString(s)
      case OfInt(i)     => Json.fromInt(i)
      case OfDouble(d)  => Json.fromDoubleOrString(d)
      case OfBoolean(b) => Json.fromBoolean(b)
    }
  )
}
