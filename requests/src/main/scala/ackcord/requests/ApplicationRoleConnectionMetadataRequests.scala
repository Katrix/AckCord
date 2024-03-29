//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.requests

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/requests/ApplicationRoleConnectionMetadataRequests.yaml

import ackcord.data._
import sttp.model.Method

object ApplicationRoleConnectionMetadataRequests {

  def getApplicationRoleConnectionMetadataRecords(
      applicationId: ApplicationId
  ): Request[Unit, Seq[ApplicationRoleConnectionMetadata]] =
    Request.restRequest(
      route = (Route.Empty / "applications" / Parameters[ApplicationId](
        "applicationId",
        applicationId
      ) / "role-connections" / "metadata").toRequest(Method.GET)
    )

  def updateApplicationRoleConnectionMetadataRecords(
      applicationId: ApplicationId,
      body: Seq[ApplicationRoleConnectionMetadata]
  ): Request[Seq[ApplicationRoleConnectionMetadata], Seq[ApplicationRoleConnectionMetadata]] =
    Request.restRequest(
      route = (Route.Empty / "applications" / Parameters[ApplicationId](
        "applicationId",
        applicationId
      ) / "role-connections" / "metadata").toRequest(Method.PUT),
      params = body
    )
}
