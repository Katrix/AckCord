package net.katsstuff.ackcord.http

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

package object oauth {

  def baseGrant(clientId: String, scopes: Seq[Scope], state: String, redirectUri: String, responseType: String): Uri =
    Routes.oAuth2Authorize.applied.withQuery(
      Uri.Query(
        "response_type" -> responseType,
        "client_id"     -> clientId,
        "scope"         -> scopes.map(_.name).mkString(" "),
        "state"         -> state,
        "redirect_uri"  -> redirectUri
      )
    )

  def codeGrantUri(clientId: String, scopes: Seq[Scope], state: String, redirectUri: String): Uri =
    baseGrant(clientId, scopes, state, redirectUri, "code")

  def implicitGrantUri(clientId: String, scopes: Seq[Scope], state: String, redirectUri: String): Uri =
    baseGrant(clientId, scopes, state, redirectUri, "token")

  def tokenExchange(clientId: String, clientSecret: String, grantType: GrantType, code: String, redirectUri: String)(
      implicit system: ActorSystem,
      mat: Materializer
  ): Future[AccessToken] = {
    import system.dispatcher
    val formData = FormData("grant_type" -> grantType.name, "code" -> code, "redirect_uri" -> redirectUri)

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Routes.oAuth2Token.applied,
      headers = List(Authorization(BasicHttpCredentials(clientId, clientSecret))),
      entity = formData.toEntity
    )

    Http()
      .singleRequest(request)
      .flatMap(Unmarshal(_).to[AccessToken])
  }

  def clientCredentialsGrant(clientId: String, clientSecret: String, scopes: Seq[Scope])(
      implicit system: ActorSystem,
      mat: Materializer
  ): Future[ClientAccessToken] = {
    import system.dispatcher
    val formData =
      FormData("grant_type" -> GrantType.ClientCredentials.name, "scope" -> scopes.map(_.name).mkString(" "))

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Routes.oAuth2Token.applied,
      headers = List(Authorization(BasicHttpCredentials(clientId, clientSecret))),
      entity = formData.toEntity
    )

    Http()
      .singleRequest(request)
      .flatMap(Unmarshal(_).to[ClientAccessToken])
  }
}
