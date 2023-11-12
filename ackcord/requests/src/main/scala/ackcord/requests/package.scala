package ackcord

package object requests {
  type AckCordRequest[Response, -R] = base.AckCordRequest[Response, R]

  type ComplexRequest[Params, Response, -R1, -R2] = base.ComplexRequest[Params, Response, R1, R2]
  val ComplexRequest: base.ComplexRequest.type = base.ComplexRequest

  type EncodeBody[-Params, -R] = base.EncodeBody[Params, R]
  val EncodeBody: base.EncodeBody.type = base.EncodeBody

  type ParseResponse[Response, -R] = base.ParseResponse[Response, R]
  val ParseResponse: base.ParseResponse.type = base.ParseResponse

  type Request[Params, Response] = ComplexRequest[Params, Response, Any, Any]
  val Request: base.ComplexRequest.type = base.ComplexRequest

  val Parameters: base.Parameters.type = base.Parameters

  type Route = base.Route
  val Route: base.Route.type = base.Route

  type RequestRoute = base.RequestRoute
  val RequestRoute: base.RequestRoute.type = base.RequestRoute

  type Requests[F[_], +R] = base.Requests[F, R]
  val Requests: base.Requests.type = base.Requests

  type RequestSettings[F[_]] = base.RequestSettings[F]
  val RequestSettings: base.RequestSettings.type = base.RequestSettings
}
