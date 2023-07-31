package ackcord.interactions.data

import io.circe.{Codec, HCursor, Json}

sealed trait IntOrDouble
object IntOrDouble {
  case class OfInt(i: Int)       extends IntOrDouble
  case class OfDouble(d: Double) extends IntOrDouble

  implicit val codec: Codec[IntOrDouble] = Codec.from(
    (c: HCursor) =>
      c.as[Int]
        .map(OfInt)
        .orElse(c.as[Double].map(OfDouble)),
    {
      case OfInt(i)    => Json.fromInt(i)
      case OfDouble(d) => Json.fromDoubleOrString(d)
    }
  )
}
