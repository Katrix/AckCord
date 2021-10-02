package ackcord.requests

import ackcord.data.DiscordProtocol._
import ackcord.data.raw.RawSticker
import ackcord.data.{GuildId, Sticker, StickerId, StickerPack}
import ackcord.util.JsonOption
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{HttpEntity, RequestEntity}
import io.circe.{Decoder, Encoder, derivation}

case class GetSticker(stickerId: StickerId) extends NoParamsRequest[RawSticker, Sticker] {
  override def route: RequestRoute = Routes.getSticker(stickerId)

  override def responseDecoder: Decoder[RawSticker]          = Decoder[RawSticker]
  override def toNiceResponse(response: RawSticker): Sticker = response.toSticker
}

case class ListNitroStickerPacksResponse(stickerPacks: Seq[StickerPack])
case object ListNitroStickerPacks extends NoParamsNiceResponseRequest[ListNitroStickerPacksResponse] {
  override def route: RequestRoute = Routes.listNitroStickerPacks
  override def responseDecoder: Decoder[ListNitroStickerPacksResponse] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)
}

case class ListGuildStickers(guildId: GuildId) extends NoParamsRequest[Seq[RawSticker], Seq[Sticker]] {
  override def route: RequestRoute = Routes.listGuildStickers(guildId)

  override def responseDecoder: Decoder[Seq[RawSticker]]               = Decoder[Seq[RawSticker]]
  override def toNiceResponse(response: Seq[RawSticker]): Seq[Sticker] = response.map(_.toSticker)
}

case class GetGuildSticker(guildId: GuildId, stickerId: StickerId) extends NoParamsRequest[RawSticker, Sticker] {
  override def route: RequestRoute = Routes.getGuildSticker(guildId, stickerId)

  override def responseDecoder: Decoder[RawSticker]          = Decoder[RawSticker]
  override def toNiceResponse(response: RawSticker): Sticker = response.toSticker
}

case class CreateGuildStickerData(
    name: String,
    description: String,
    tags: String,
    file: CreateMessageFile
) {
  require(name.length >= 2 && name.length <= 30, "Invalid length for sticker name")
  require(
    description.isEmpty || (description.length >= 2 && description.length <= 100),
    "Invalid length for sticker description"
  )
  require(tags.length >= 2 && tags.length <= 30, "Invalid length for sticker tags")
}

case class CreateGuildSticker(guildId: GuildId, sticker: CreateGuildStickerData, reason: Option[String] = None)
    extends NoParamsReasonRequest[CreateGuildSticker, RawSticker, Sticker] {
  override def withReason(reason: String): CreateGuildSticker = copy(reason = Some(reason))
  override def route: RequestRoute                            = Routes.createGuildSticker(guildId)

  override def bodyForLogging: Option[String] = Some(sticker.toString)
  override def requestBody: RequestEntity =
    FormData(
      FormData.BodyPart("name", HttpEntity(sticker.name)),
      FormData.BodyPart("description", HttpEntity(sticker.description)),
      FormData.BodyPart("tags", HttpEntity(sticker.tags)),
      FormData.BodyPart("file", sticker.file.toBodyPartEntity)
    ).toEntity()

  override def responseDecoder: Decoder[RawSticker]          = Decoder[RawSticker]
  override def toNiceResponse(response: RawSticker): Sticker = response.toSticker
}

case class ModifyGuildStickerData(
    name: JsonOption[String],
    description: JsonOption[String],
    tags: JsonOption[String]
) {
  require(name.forall(n => n.length >= 2 && n.length <= 30), "Invalid length for sticker name")
  require(
    description.forall(d => d.isEmpty || (d.length >= 2 && d.length <= 100)),
    "Invalid length for sticker description"
  )
  require(tags.forall(t => t.length >= 2 && t.length <= 30), "Invalid length for sticker tags")
}
object ModifyGuildStickerData {
  implicit val encoder: Encoder[ModifyGuildStickerData] = (a: ModifyGuildStickerData) =>
    JsonOption.removeUndefinedToObj(
      "name"        -> a.name.toJson,
      "description" -> a.description.toJson,
      "tags"        -> a.tags.toJson
    )
}

case class ModifyGuildSticker(
    guildId: GuildId,
    stickerId: StickerId,
    params: ModifyGuildStickerData,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildSticker, ModifyGuildStickerData, RawSticker, Sticker] {
  override def withReason(reason: String): ModifyGuildSticker = copy(reason = Some(reason))
  override def route: RequestRoute                            = Routes.modifyGuildSticker(guildId, stickerId)

  override def paramsEncoder: Encoder[ModifyGuildStickerData] = ModifyGuildStickerData.encoder
  override def responseDecoder: Decoder[RawSticker]           = Decoder[RawSticker]

  override def toNiceResponse(response: RawSticker): Sticker = response.toSticker
}

case class DeleteGuildSticker(guildId: GuildId, stickerId: StickerId, reason: Option[String] = None)
    extends NoParamsResponseReasonRequest[DeleteGuildSticker] {
  override def withReason(reason: String): DeleteGuildSticker = copy(reason = Some(reason))
  override def route: RequestRoute                            = Routes.deleteGuildSticker(guildId, stickerId)
}
