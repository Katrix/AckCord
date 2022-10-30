package ackcord.data

import io.circe.Codec

abstract class DiscordOpaqueCompanion[Underlying](implicit underlyingCodec: Codec[Underlying]) {
  private[data] type Base
  private[data] trait Tag extends Any

  type OpaqueType <: Base with Tag

  def apply(underlying: Underlying): OpaqueType = underlying.asInstanceOf[OpaqueType]

  def underlying(opaque: OpaqueType): Underlying = opaque.asInstanceOf[Underlying]

  implicit val opaqueCodec: Codec[OpaqueType] =
    underlyingCodec.iemap[OpaqueType](u => Right(u.asInstanceOf[OpaqueType]))(o => o.asInstanceOf[Underlying])

}
