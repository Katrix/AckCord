package ackcord.interactions.components

import ackcord.interactions.{InteractionTransformer, MenuInteraction}
import ackcord.requests.Requests

abstract class AutoMenuHandler[Interaction <: MenuInteraction](
    identifier: String,
    requests: Requests,
    interactionTransformer: InteractionTransformer[MenuInteraction, Interaction] =
      InteractionTransformer.identity[MenuInteraction],
    registeredButtons: RegisteredComponents = GlobalRegisteredComponents
) extends MenuHandler(requests, interactionTransformer) {
  registeredButtons.addHandler(identifier, this)

  def unregisterButtonHandler(): Unit = registeredButtons.removeHandler(this)
}
