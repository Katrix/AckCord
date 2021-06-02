package ackcord.interactions.buttons

import ackcord.data.{RawInteraction, RawInteractionApplicationCommandCallbackData}
import ackcord.requests.Requests
import ackcord.interactions._
import ackcord.{CacheSnapshot, OptFuture}
import cats.syntax.either._

abstract class ButtonHandler[InteractionTpe <: ButtonInteraction](
    val requests: Requests,
    interactionTransformer: DataInteractionTransformer[shapeless.Const[ButtonInteraction]#λ, shapeless.Const[
      InteractionTpe
    ]#λ] = DataInteractionTransformer.identity[shapeless.Const[ButtonInteraction]#λ]
) extends InteractionHandlerOps {

  def asyncLoading(handle: AsyncToken => OptFuture[_])(implicit interaction: InteractionTpe): InteractionResponse =
    InteractionResponse.AcknowledgeLoading(() => handle(AsyncToken.fromInteraction(interaction)))

  def acknowledgeLoading: InteractionResponse =
    InteractionResponse.AcknowledgeLoading(() => OptFuture.unit)

  def handle(implicit interaction: ButtonInteraction): InteractionResponse

  def handleRaw(clientId: String, interaction: RawInteraction, cacheSnapshot: Option[CacheSnapshot]): InteractionResponse = {
    val invocationInfo = InteractionInvocationInfo(
      interaction.id,
      interaction.guildId,
      interaction.channelId,
      interaction.member.map(_.user).orElse(interaction.user).get,
      interaction.member,
      interaction.memberPermission,
      interaction.token,
      clientId
    )

    interaction.message match {
      case Some(rawMessage) =>
        val message = rawMessage.toMessage

        val base = cacheSnapshot match {
          case Some(value) => BaseCacheButtonInteraction(invocationInfo, message, value)
          case None        => StatelessButtonInteraction(invocationInfo, message)
        }

        interactionTransformer
          .filter(base)
          .map(handle(_))
          .leftMap {
            case Some(error) =>
              InteractionResponse.ChannelMessage(
                RawInteractionApplicationCommandCallbackData(content = Some(s"An error occurred: $error")),
                () => OptFuture.unit
              )
            case None =>
              InteractionResponse.ChannelMessage(
                RawInteractionApplicationCommandCallbackData(content = Some("An error occurred")),
                () => OptFuture.unit
              )
          }
          .merge

      case None =>
        InteractionResponse.ChannelMessage(
          RawInteractionApplicationCommandCallbackData(content = Some(s"Wrong data for button execution")),
          () => OptFuture.unit
        )
    }
  }
}
