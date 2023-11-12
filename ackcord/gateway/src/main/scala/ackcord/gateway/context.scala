package ackcord.gateway

class Context(map: Map[ContextKey[_], Any]) {
  def access[A](key: ContextKey[A]): A = map.getOrElse(key, key.default).asInstanceOf[A]

  def add[A](key: ContextKey[A], data: A): Context = new Context(map.updated(key, data))
}

object Context {
  def empty: Context = new Context(Map.empty)
}

trait ContextKey[A] {
  def default: A
}

object ContextKey {
  def make[A](defaultValue: A): ContextKey[A] = new ContextKey[A] {
    override def default: A = defaultValue
  }

  def makeWithoutDefault[A]: ContextKey[A] = new ContextKey[A] {
    override def default: A = throw new NoSuchElementException("ContextKey.default")
  }
}
