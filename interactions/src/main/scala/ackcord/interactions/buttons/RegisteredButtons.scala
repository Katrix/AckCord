package ackcord.interactions.buttons

class RegisteredButtons {

  private val handledButtons       = collection.concurrent.TrieMap.empty[String, ButtonHandler[_]]
  private val handlerToIdentifiers = collection.concurrent.TrieMap.empty[ButtonHandler[_], Seq[String]]

  def addHandler(identifier: String, handler: ButtonHandler[_]): Unit = {
    if (handledButtons.put(identifier, handler).isEmpty) {
      handlerToIdentifiers.updateWith(handler) {
        case Some(value) => Some(value :+ identifier)
        case None        => Some(Seq(identifier))
      }
    }
  }

  def removeHandler(identifier: String): Unit =
    handledButtons
      .remove(identifier)
      .foreach(handlerToIdentifiers.updateWith(_) {
        case Some(Seq(`identifier`)) => None
        case Some(seq)               => Some(seq.filter(_ != identifier))
        case None                    => None
      })

  def removeHandler(handler: ButtonHandler[_]): Unit =
    handlerToIdentifiers.remove(handler).foreach(_.foreach(handledButtons.remove))

  def handlerForIdentifier(identifier: String): Option[ButtonHandler[_]] =
    handledButtons.get(identifier)
}

object GlobalRegisteredButtons extends RegisteredButtons
