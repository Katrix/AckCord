package ackcord.interactions.components

import ackcord.interactions.{ComponentInteraction, InteractionTransformer}
import ackcord.requests.Requests

/**
  * An [[ButtonHandler]] that registers itself when an instance of it is
  * created.
  *
  * @param identifier
  *   The identifiers of the buttons this handler handles.
  * @param interactionTransformer
  *   A transformer to do base processing of the interaction before handling it.
  * @param registeredComponents
  *   Where to register this handler to.
  */
abstract class MultiAutoButtonHandler[Interaction <: ComponentInteraction](
    identifier: Seq[String],
    requests: Requests,
    interactionTransformer: InteractionTransformer[ComponentInteraction, Interaction] =
      InteractionTransformer.identity[ComponentInteraction],
    registeredComponents: RegisteredComponents = GlobalRegisteredComponents
) extends ButtonHandler(requests, interactionTransformer)
