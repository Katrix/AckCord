package ackcord.data

class MissingFieldException(field: String, obj: AnyRef) extends Exception(s"Missing field $field on object $obj")
