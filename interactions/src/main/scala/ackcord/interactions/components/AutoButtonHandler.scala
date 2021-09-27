package ackcord.interactions.components

import ackcord.interactions.{ComponentInteraction, InteractionTransformer}
import ackcord.requests.Requests

abstract class AutoButtonHandler[Interaction <: ComponentInteraction](
    identifier: String,
    requests: Requests,
    interactionTransformer: InteractionTransformer[ComponentInteraction, Interaction] =
      InteractionTransformer.identity[ComponentInteraction],
    registeredComponents: RegisteredComponents = GlobalRegisteredComponents
) extends ButtonHandler(requests, interactionTransformer) {
  registeredComponents.addHandler(identifier, this)

  def unregisterButtonHandler(): Unit = registeredComponents.removeHandler(this)
}
