package ackcord.data.base

class MissingFieldException private (val message: String, val missingOn: AnyRef) extends Exception(message)
object MissingFieldException {
  def default(field: String, obj: AnyRef): MissingFieldException =
    new MissingFieldException(s"Missing field $field on object $obj", obj)

  def messageAndData(message: String, missingOn: AnyRef): MissingFieldException =
    new MissingFieldException(message, missingOn)
}
