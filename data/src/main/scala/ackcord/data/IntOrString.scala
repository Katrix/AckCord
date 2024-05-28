package ackcord.data

import io.circe.{Codec, Decoder, Json}

sealed trait IntOrString
object IntOrString {
  case class AsString(s: String) extends IntOrString
  case class AsInt(i: Int)       extends IntOrString

  implicit val codec: Codec[IntOrString] = Codec.from(
    Decoder[Int].map(AsInt.apply).or(Decoder[String].map(AsString.apply)),
    {
      case AsString(s) => Json.fromString(s)
      case AsInt(i)    => Json.fromInt(i)
    }
  )
}
