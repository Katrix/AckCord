package ackcord.interactions.buttons

import ackcord.interactions.{ButtonInteraction, DataInteractionTransformer}
import ackcord.requests.Requests

abstract class AutoButtonHandler[Interaction <: ButtonInteraction](
    identifier: String,
    requests: Requests,
    interactionTransformer: DataInteractionTransformer[shapeless.Const[ButtonInteraction]#λ, shapeless.Const[
      Interaction
    ]#λ] = DataInteractionTransformer.identity[shapeless.Const[ButtonInteraction]#λ],
    registeredButtons: RegisteredButtons = GlobalRegisteredButtons
) extends ButtonHandler(requests, interactionTransformer) {
  registeredButtons.addHandler(identifier, this)

  def unregisterButtonHandler(): Unit = registeredButtons.removeHandler(this)
}
