package ackcord.requests

import ackcord.data._
import ackcord.data.DiscordProtocol._
import ackcord.util.{JsonOption, JsonUndefined}
import io.circe.{Decoder, Encoder, derivation}

case class CreateStageInstanceData(
    channelId: StageGuildChannelId,
    topic: String,
    privacyLevel: StageInstancePrivacyLevel = StageInstancePrivacyLevel.GuildOnly
)

case class CreateStageInstance(params: CreateStageInstanceData)
    extends NoNiceResponseRequest[CreateStageInstanceData, StageInstance] {
  override def route: RequestRoute = Routes.createStageInstance

  override def paramsEncoder: Encoder[CreateStageInstanceData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)
  override def responseDecoder: Decoder[StageInstance] = Decoder[StageInstance]
}

case class GetStageInstance(channelId: StageGuildChannelId) extends NoParamsNiceResponseRequest[StageInstance] {
  override def route: RequestRoute = Routes.getStageInstance(channelId)

  override def responseDecoder: Decoder[StageInstance] = Decoder[StageInstance]
}

case class UpdateStageInstanceData(
    topic: JsonOption[String] = JsonUndefined,
    privacyLevel: JsonOption[StageInstancePrivacyLevel] = JsonUndefined
)
object UpdateStageInstanceData {
  implicit val encoder: Encoder[UpdateStageInstanceData] = (a: UpdateStageInstanceData) =>
    JsonOption.removeUndefinedToObj(
      "topic"         -> a.topic.toJson,
      "privacy_level" -> a.privacyLevel.toJson
    )
}
case class UpdateStageInstance(channelId: StageGuildChannelId, params: UpdateStageInstanceData)
    extends NoNiceResponseRequest[UpdateStageInstanceData, StageInstance] {
  override def route: RequestRoute = Routes.updateStageInstance(channelId)

  override def paramsEncoder: Encoder[UpdateStageInstanceData] = UpdateStageInstanceData.encoder
  override def responseDecoder: Decoder[StageInstance] = Decoder[StageInstance]
}

case class DeleteStageInstance(channelId: StageGuildChannelId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteStageInstance(channelId)
}
