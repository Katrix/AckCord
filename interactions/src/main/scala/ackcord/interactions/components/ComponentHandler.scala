package ackcord.interactions.components

import ackcord.data.{
  ApplicationComponentInteractionData,
  ComponentType,
  InteractionCallbackDataMessage,
  Message,
  RawInteraction
}
import ackcord.interactions._
import ackcord.requests.Requests
import ackcord.{CacheSnapshot, OptFuture}
import cats.syntax.either._

/**
  * A type handling some sort of component interaction.
  * @param interactionTransformer
  *   A transformer to do base processing of the interaction before handling it.
  * @param acceptedComponent
  *   The accepted component types this component handler handles.
  */
abstract class ComponentHandler[BaseInteraction <: ComponentInteraction, InteractionTpe <: BaseInteraction](
    val requests: Requests,
    interactionTransformer: InteractionTransformer[BaseInteraction, InteractionTpe] =
      InteractionTransformer.identity[BaseInteraction],
    acceptedComponent: ComponentType
) extends InteractionHandlerOps {

  /**
    * Respond with a promise that you'll handle the interaction later. Stops
    * showing the loading icon.
    * @param handle
    *   The handler for later.
    */
  def asyncLoading(handle: AsyncToken => OptFuture[_])(implicit interaction: InteractionTpe): InteractionResponse =
    InteractionResponse.UpdateMessageLater(() => handle(AsyncToken.fromInteraction(interaction)))

  //TODO: Determine of this should be exposed or not
  //def acknowledgeLoading: InteractionResponse =
  //  InteractionResponse.UpdateMessageLater(() => OptFuture.unit)

  /**
    * Handle the interaction here.
    * @param interaction
    *   The interaction to handle.
    * @return
    *   A response to the interaction.
    */
  def handle(implicit interaction: InteractionTpe): InteractionResponse

  protected def makeBaseInteraction(
      invocationInfo: InteractionInvocationInfo,
      message: Message,
      interaction: RawInteraction,
      customId: String,
      cacheSnapshot: Option[CacheSnapshot]
  ): BaseInteraction

  def handleRaw(
      clientId: String,
      interaction: RawInteraction,
      customId: String,
      cacheSnapshot: Option[CacheSnapshot]
  ): Option[InteractionResponse] = {
    val isCorrectComponentType = interaction.data
      .collect { case ApplicationComponentInteractionData(componentType, _, _) =>
        acceptedComponent == componentType
      }
      .exists(identity)

    if (isCorrectComponentType) {
      val invocationInfo = InteractionInvocationInfo(
        interaction.id,
        interaction.guildId,
        interaction.channelId.getOrElse(
          throw new IllegalArgumentException("Got an interaction without a channel for a component handler")
        ),
        interaction.member.map(_.user).orElse(interaction.user).get,
        interaction.member,
        interaction.memberPermission,
        interaction.token,
        clientId
      )

      interaction.message match {
        case Some(rawMessage) =>
          val message = rawMessage.toMessage

          Some(
            interactionTransformer
              .filter(makeBaseInteraction(invocationInfo, message, interaction, customId, cacheSnapshot))
              .map(handle(_))
              .leftMap {
                case Some(error) =>
                  InteractionResponse.ChannelMessage(
                    InteractionCallbackDataMessage(content = Some(s"An error occurred: $error")),
                    () => OptFuture.unit
                  )
                case None =>
                  InteractionResponse.ChannelMessage(
                    InteractionCallbackDataMessage(content = Some("An error occurred")),
                    () => OptFuture.unit
                  )
              }
              .merge
          )

        case None =>
          Some(
            InteractionResponse.ChannelMessage(
              InteractionCallbackDataMessage(content = Some(s"Wrong data for component execution")),
              () => OptFuture.unit
            )
          )
      }
    } else {
      None
    }
  }
}
