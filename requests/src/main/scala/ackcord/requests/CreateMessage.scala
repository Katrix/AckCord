package ackcord.requests

import ackcord.data.ChannelId
import ackcord.data.base.{DiscordObject, DiscordObjectCompanion}
import io.circe.Json

import sttp.model.Method

object CreateMessageContainer {

  class CreateMessageBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {
    @inline def content: String = selectDynamic[String]("content")
  }
  object CreateMessageBody extends DiscordObjectCompanion[CreateMessageBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): CreateMessageBody = new CreateMessageBody(json, cache)

    def make20(content: String): CreateMessageBody = makeRawFromFields("content" := content)

  }

  def createMessage(
      channelId: ChannelId,
      body: CreateMessageBody
  ): Request[CreateMessageBody, Json] =
    Request.restRequest(
      route = (Route.Empty / "channels" / Parameters.ofChannelId(channelId) / "messages").toRequest(Method.POST),
      params = body
    )

}
