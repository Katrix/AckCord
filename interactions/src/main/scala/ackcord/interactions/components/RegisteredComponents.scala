package ackcord.interactions.components

import scala.collection.concurrent.TrieMap

/**
  * A class keeping track of all the active components and the handlers for
  * their interactions. By default, the global [[GlobalRegisteredComponents]] is
  * used, but you can also make and use your own.
  */
class RegisteredComponents {

  private val handledComponents    = TrieMap.empty[String, ComponentHandler[_, _]]
  private val handlerToIdentifiers = TrieMap.empty[ComponentHandler[_, _], Seq[String]]

  /**
    * Adds a new component handler.
    * @param identifier
    *   The identifier to associate with the handler.
    * @param handler
    *   The handler.
    */
  def addHandler(identifier: String, handler: ComponentHandler[_, _]): Unit = {
    if (handledComponents.put(identifier, handler).isEmpty) {
      ackcord.interactions.updateWith(handlerToIdentifiers)(handler) {
        case Some(value) => Some(value :+ identifier)
        case None        => Some(Seq(identifier))
      }
    }
  }

  /**
    * Remove the handler associated with an identifier.
    * @param identifier
    *   The identifier to remove the handler for.
    */
  def removeHandler(identifier: String): Unit =
    handledComponents
      .remove(identifier)
      .foreach(ackcord.interactions.updateWith(handlerToIdentifiers)(_) {
        case Some(Seq(`identifier`)) => None
        case Some(seq)               => Some(seq.filter(_ != identifier))
        case None                    => None
      })

  /**
    * Remove all handling of the specified handler.
    * @param handler
    *   The handler to completely unregister.
    */
  def removeHandler(handler: ComponentHandler[_, _]): Unit =
    handlerToIdentifiers.remove(handler).foreach(_.foreach(handledComponents.remove))

  /** Try to get the handler for a given identifier. */
  def handlerForIdentifier(identifier: String): Option[ComponentHandler[_, _]] =
    handledComponents.get(identifier)
}

/** The global [[RegisteredComponents]] used by default. */
object GlobalRegisteredComponents extends RegisteredComponents
