package ackcord.interactions.components

import scala.collection.concurrent.TrieMap

class RegisteredComponents {

  private val handledComponents = TrieMap.empty[String, ComponentHandler[_, _]]
  private val handlerToIdentifiers =
    TrieMap.empty[ComponentHandler[_, _], Seq[String]]

  def addHandler(identifier: String, handler: ComponentHandler[_, _]): Unit = {
    if (handledComponents.put(identifier, handler).isEmpty) {
      ackcord.interactions.updateWith(handlerToIdentifiers)(handler) {
        case Some(value) => Some(value :+ identifier)
        case None        => Some(Seq(identifier))
      }
    }
  }

  def removeHandler(identifier: String): Unit =
    handledComponents
      .remove(identifier)
      .foreach(ackcord.interactions.updateWith(handlerToIdentifiers)(_) {
        case Some(Seq(`identifier`)) => None
        case Some(seq)               => Some(seq.filter(_ != identifier))
        case None                    => None
      })

  def removeHandler(handler: ComponentHandler[_, _]): Unit =
    handlerToIdentifiers
      .remove(handler)
      .foreach(_.foreach(handledComponents.remove))

  def handlerForIdentifier(identifier: String): Option[ComponentHandler[_, _]] =
    handledComponents.get(identifier)
}

object GlobalRegisteredComponents extends RegisteredComponents
