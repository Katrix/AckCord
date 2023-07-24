//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.requests

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/requests/ApplicationRequests.yaml

import ackcord.data._
import ackcord.data.base._
import io.circe.Json
import sttp.model.Method

object ApplicationRequests {

  val getCurrentApplication: Request[Unit, Application] =
    Request.restRequest(
      route = (Route.Empty / "applications" / "@me").toRequest(Method.GET)
    )

}