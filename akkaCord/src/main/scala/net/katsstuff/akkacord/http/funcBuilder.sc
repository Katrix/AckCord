class ActionBuilder[Takes] {
  def takes[NewTake]: ActionBuilder[(Takes, NewTake)] = new ActionBuilder[(Takes, NewTake)]
  def build[Out](f: Takes => Out): Takes => Out = takes => f(takes)
}
object FuncBuilder {
  def startsWith[Start]: ActionBuilder[Start] = new ActionBuilder[Start]
}

trait StringAction {
  def apply(str: String): Boolean
}

FuncBuilder.startsWith[Int].takes[String].takes[Boolean].build { case (((int), string), boolean) =>
  s"$int $string $boolean"
}