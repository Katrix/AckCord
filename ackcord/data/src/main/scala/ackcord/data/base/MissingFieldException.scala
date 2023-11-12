package ackcord.data.base

import io.circe.Json

class MissingFieldException private (val message: String, val missingOn: AnyRef) extends Exception(message)
object MissingFieldException {
  def default(field: String, obj: AnyRef): MissingFieldException =
    obj match {
      case json: Json =>
        if (json.hcursor.downField(field).succeeded)
          new MissingFieldException(s"Found field $field on object $obj, but count not decode it", obj)
        else new MissingFieldException(s"Missing field $field on object $obj", obj)

      case _ => new MissingFieldException(s"Missing field $field on object $obj", obj)
    }

  def messageAndData(message: String, missingOn: AnyRef): MissingFieldException =
    new MissingFieldException(message, missingOn)
}
