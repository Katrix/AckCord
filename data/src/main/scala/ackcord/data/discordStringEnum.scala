package ackcord.data

import io.circe._

trait DiscordStringEnum {
  def value: String
}
trait DiscordStringEnumCompanion[Obj <: DiscordStringEnum] {

  def unknown(value: String): Obj

  implicit val codec: Codec[Obj] = Codec.from(
    Decoder[String].map(unknown),
    Encoder[String].contramap[Obj](_.value)
  )
}
