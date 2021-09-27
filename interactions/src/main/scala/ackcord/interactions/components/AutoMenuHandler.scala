package ackcord.interactions.components

import ackcord.interactions.{DataInteractionTransformer, MenuInteraction}
import ackcord.requests.Requests

abstract class AutoMenuHandler[Interaction <: MenuInteraction](
    identifier: String,
    requests: Requests,
    interactionTransformer: DataInteractionTransformer[shapeless.Const[
      MenuInteraction
    ]#λ, shapeless.Const[
      Interaction
    ]#λ] =
      DataInteractionTransformer.identity[shapeless.Const[MenuInteraction]#λ],
    registeredButtons: RegisteredComponents = GlobalRegisteredComponents
) extends MenuHandler(requests, interactionTransformer) {
  registeredButtons.addHandler(identifier, this)

  def unregisterButtonHandler(): Unit = registeredButtons.removeHandler(this)
}
