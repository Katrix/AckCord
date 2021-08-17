package ackcord.interactions.components

import ackcord.CacheSnapshot
import ackcord.data.{ApplicationComponentInteractionData, ComponentType, Message, RawInteraction}
import ackcord.interactions._
import ackcord.requests.Requests

abstract class MenuHandler[InteractionTpe <: MenuInteraction](
    requests: Requests,
    interactionTransformer: DataInteractionTransformer[shapeless.Const[MenuInteraction]#λ, shapeless.Const[
      InteractionTpe
    ]#λ] = DataInteractionTransformer.identity[shapeless.Const[MenuInteraction]#λ]
) extends ComponentHandler[MenuInteraction, InteractionTpe](requests, interactionTransformer, ComponentType.SelectMenu) {

  override protected def makeBaseInteraction(
      invocationInfo: InteractionInvocationInfo,
      message: Message,
      interaction: RawInteraction,
      cacheSnapshot: Option[CacheSnapshot]
  ): MenuInteraction = cacheSnapshot match {
    case Some(c) =>
      BaseCacheMenuInteraction(
        invocationInfo,
        message,
        interaction.data
          .collect { case ApplicationComponentInteractionData(_, _, values) => values }
          .flatten
          .getOrElse(Nil),
        c
      )
    case None =>
      StatelessMenuInteraction(
        invocationInfo,
        message,
        interaction.data
          .collect { case ApplicationComponentInteractionData(_, _, values) => values }
          .flatten
          .getOrElse(Nil)
      )
  }
}
