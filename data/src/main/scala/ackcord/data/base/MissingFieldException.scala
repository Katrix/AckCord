package ackcord.data.base

class MissingFieldException(field: String, obj: AnyRef) extends Exception(s"Missing field $field on object $obj")
