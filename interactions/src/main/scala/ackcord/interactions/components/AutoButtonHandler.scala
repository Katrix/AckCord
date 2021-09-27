package ackcord.interactions.components

import ackcord.interactions.{ComponentInteraction, DataInteractionTransformer}
import ackcord.requests.Requests

abstract class AutoButtonHandler[Interaction <: ComponentInteraction](
    identifier: String,
    requests: Requests,
    interactionTransformer: DataInteractionTransformer[shapeless.Const[ComponentInteraction]#λ, shapeless.Const[
      Interaction
    ]#λ] = DataInteractionTransformer.identity[shapeless.Const[ComponentInteraction]#λ],
    registeredComponents: RegisteredComponents = GlobalRegisteredComponents
) extends ButtonHandler(requests, interactionTransformer) {
  registeredComponents.addHandler(identifier, this)

  def unregisterButtonHandler(): Unit = registeredComponents.removeHandler(this)
}
