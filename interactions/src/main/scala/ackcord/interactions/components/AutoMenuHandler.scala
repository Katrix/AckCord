package ackcord.interactions.components

import ackcord.interactions.{InteractionTransformer, MenuInteraction}
import ackcord.requests.Requests

/**
  * An [[MultiAutoMenuHandler]] supporting only a single identifier..
  * @param identifier
  *   The identifier of the menu this handler handles.
  * @param interactionTransformer
  *   A transformer to do base processing of the interaction before handling it.
  * @param registeredComponents
  *   Where to register this handler to.
  */
abstract class AutoMenuHandler[Interaction <: MenuInteraction](
    identifier: String,
    requests: Requests,
    interactionTransformer: InteractionTransformer[MenuInteraction, Interaction] =
      InteractionTransformer.identity[MenuInteraction],
    registeredComponents: RegisteredComponents = GlobalRegisteredComponents
) extends MultiAutoMenuHandler(Seq(identifier), requests, interactionTransformer, registeredComponents)
