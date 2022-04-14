package ackcord.interactions.components

import ackcord.interactions.{ComponentInteraction, InteractionTransformer}
import ackcord.requests.Requests

/**
  * An [[MultiAutoButtonHandler]] supporting only a single identifier.
  * @param identifier
  *   The identifier of the button this handler handles.
  * @param interactionTransformer
  *   A transformer to do base processing of the interaction before handling it.
  * @param registeredComponents
  *   Where to register this handler to.
  */
abstract class AutoButtonHandler[Interaction <: ComponentInteraction](
    identifier: String,
    requests: Requests,
    interactionTransformer: InteractionTransformer[ComponentInteraction, Interaction] =
      InteractionTransformer.identity[ComponentInteraction],
    registeredComponents: RegisteredComponents = GlobalRegisteredComponents
) extends MultiAutoButtonHandler(Seq(identifier), requests, interactionTransformer, registeredComponents) {
  registeredComponents.addHandler(identifier, this)
}
