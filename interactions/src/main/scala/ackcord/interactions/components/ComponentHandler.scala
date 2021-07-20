package ackcord.interactions.components

import ackcord.data.{Message, RawInteraction, RawInteractionApplicationCommandCallbackData}
import ackcord.interactions._
import ackcord.requests.Requests
import ackcord.{CacheSnapshot, OptFuture}
import cats.syntax.either._

abstract class ComponentHandler[BaseInteraction <: ComponentInteraction, InteractionTpe <: BaseInteraction](
    val requests: Requests,
    interactionTransformer: DataInteractionTransformer[shapeless.Const[BaseInteraction]#λ, shapeless.Const[
      InteractionTpe
    ]#λ] = DataInteractionTransformer.identity[shapeless.Const[BaseInteraction]#λ]
) extends InteractionHandlerOps {

  def asyncLoading(handle: AsyncToken => OptFuture[_])(implicit interaction: InteractionTpe): InteractionResponse =
    InteractionResponse.UpdateMessageLater(() => handle(AsyncToken.fromInteraction(interaction)))

  def acknowledgeLoading: InteractionResponse =
    InteractionResponse.UpdateMessageLater(() => OptFuture.unit)

  def handle(implicit interaction: InteractionTpe): InteractionResponse

  protected def makeBaseInteraction(invocationInfo: InteractionInvocationInfo, message: Message, interaction: RawInteraction, cacheSnapshot: Option[CacheSnapshot]): BaseInteraction

  def handleRaw(
      clientId: String,
      interaction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): InteractionResponse = {
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

        interactionTransformer
          .filter(makeBaseInteraction(invocationInfo, message, interaction, cacheSnapshot))
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
          RawInteractionApplicationCommandCallbackData(content = Some(s"Wrong data for component execution")),
          () => OptFuture.unit
        )
    }
  }
}
