package ackcord.data

import io.circe._

trait DiscordIntEnum {
  def value: Int
}
trait DiscordIntEnumCompanion[Obj <: DiscordIntEnum] {

  def unknown(value: Int): Obj

  implicit val codec: Codec[Obj] = Codec.from(
    Decoder[Int].map(unknown),
    Encoder[Int].contramap[Obj](_.value)
  )
}
