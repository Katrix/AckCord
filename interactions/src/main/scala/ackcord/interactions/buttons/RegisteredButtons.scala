package ackcord.interactions.buttons

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap

class RegisteredButtons {

  private val handledButtons       = TrieMap.empty[String, ButtonHandler[_]]
  private val handlerToIdentifiers = TrieMap.empty[ButtonHandler[_], Seq[String]]

  //Taken from the standard library to compile for 2.12
  private def updateWith[K, V](map: TrieMap[K, V])(key: K)(remappingFunction: Option[V] => Option[V]): Option[V] = updateWithAux(map)(key)(remappingFunction)

  @tailrec
  private def updateWithAux[K, V](map: TrieMap[K, V])(key: K)(remappingFunction: Option[V] => Option[V]): Option[V] = {
    val previousValue = map.get(key)
    val nextValue = remappingFunction(previousValue)
    (previousValue, nextValue) match {
      case (None, None) => None
      case (None, Some(next)) if map.putIfAbsent(key, next).isEmpty => nextValue
      case (Some(prev), None) if map.remove(key, prev) => None
      case (Some(prev), Some(next)) if map.replace(key, prev, next) => nextValue
      case _ => updateWithAux(map)(key)(remappingFunction)
    }
  }

  def addHandler(identifier: String, handler: ButtonHandler[_]): Unit = {
    if (handledButtons.put(identifier, handler).isEmpty) {
      updateWith(handlerToIdentifiers)(handler) {
        case Some(value) => Some(value :+ identifier)
        case None        => Some(Seq(identifier))
      }
    }
  }

  def removeHandler(identifier: String): Unit =
    handledButtons
      .remove(identifier)
      .foreach(updateWith(handlerToIdentifiers)(_) {
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
