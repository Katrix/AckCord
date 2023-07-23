package ackcord.data.base

import io.circe.{Codec, Decoder, Encoder}

abstract class DiscordOpaqueCompanion[Underlying](
    implicit underlyingEncoder: Encoder[Underlying],
    underlyingDecoder: Decoder[Underlying]
) {
  private[data] type Base
  private[data] trait Tag extends Any

  type OpaqueType <: Base with Tag

  def apply(underlying: Underlying): OpaqueType = underlying.asInstanceOf[OpaqueType]

  def underlying(opaque: OpaqueType): Underlying = opaque.asInstanceOf[Underlying]

  implicit val opaqueCodec: Codec[OpaqueType] =
    Codec
      .from(underlyingDecoder, underlyingEncoder)
      .iemap[OpaqueType](u => Right(u.asInstanceOf[OpaqueType]))(o => o.asInstanceOf[Underlying])

}
