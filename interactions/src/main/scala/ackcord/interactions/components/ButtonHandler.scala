package ackcord.interactions.components

import ackcord.CacheSnapshot
import ackcord.data.{ComponentType, Message, RawInteraction}
import ackcord.interactions._
import ackcord.requests.Requests

abstract class ButtonHandler[InteractionTpe <: ComponentInteraction](
    requests: Requests,
    interactionTransformer: DataInteractionTransformer[shapeless.Const[
      ComponentInteraction
    ]#λ, shapeless.Const[
      InteractionTpe
    ]#λ] = DataInteractionTransformer
      .identity[shapeless.Const[ComponentInteraction]#λ]
) extends ComponentHandler[ComponentInteraction, InteractionTpe](
      requests,
      interactionTransformer,
      ComponentType.Button
    ) {

  override protected def makeBaseInteraction(
      invocationInfo: InteractionInvocationInfo,
      message: Message,
      interaction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): ComponentInteraction = cacheSnapshot match {
    case Some(value) =>
      BaseCacheComponentInteraction(invocationInfo, message, value)
    case None => StatelessComponentInteraction(invocationInfo, message)
  }
}
