package ackcord

package object data extends SnowflakeDefs with PermissionsDefs with ImageHashDefs with ImageDataDefs {

  type MissingFieldException = base.MissingFieldException
  val MissingFieldException: base.MissingFieldException.type = base.MissingFieldException
}
