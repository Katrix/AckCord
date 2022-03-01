package ackcord.interactions.components

import ackcord.interactions.{InteractionTransformer, MenuInteraction}
import ackcord.requests.Requests

/**
  * An [[MenuHandler]] that registers itself when an instance of it is created.
  * @param identifiers The identifiers of the menus this handler handles.
  * @param interactionTransformer
  *   A transformer to do base processing of the interaction before handling it.
  * @param registeredComponents Where to register this handler to.
  */
abstract class AutoMenuHandler[Interaction <: MenuInteraction](
    identifiers: Seq[String],
    requests: Requests,
    interactionTransformer: InteractionTransformer[MenuInteraction, Interaction] =
      InteractionTransformer.identity[MenuInteraction],
    registeredComponents: RegisteredComponents = GlobalRegisteredComponents
) extends MenuHandler(requests, interactionTransformer) {
  identifiers.foreach(registeredComponents.addHandler(_, this))

  /**
    * Unregister this handler.
    */
  def unregisterMenuHandler(): Unit = registeredComponents.removeHandler(this)
}
