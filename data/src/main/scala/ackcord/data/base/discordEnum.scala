package ackcord.data.base

import io.circe._

trait DiscordEnum[A] {
  def value: A
}
abstract class DiscordEnumCompanion[A, Obj <: DiscordEnum[A]](implicit decoder: Decoder[A], encoder: Encoder[A]) {

  def unknown(value: A): Obj

  implicit val codec: Codec[Obj] = Codec.from(
    Decoder[A].map(unknown),
    Encoder[A].contramap[Obj](_.value)
  )
}
