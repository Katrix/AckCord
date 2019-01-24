/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ackcord.requests

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe._
import io.circe.syntax._

object OAuth extends FailFastCirceSupport {

  sealed abstract class Scope(val name: String)
  object Scope {
    case object Bot                  extends Scope("bot")
    case object Connections          extends Scope("connections")
    case object Email                extends Scope("email")
    case object Identify             extends Scope("identify")
    case object Guilds               extends Scope("guilds")
    case object GuildsJoin           extends Scope("guilds.join")
    case object GroupDMJoin          extends Scope("gdm.join")
    case object MessagesRead         extends Scope("messages.read")
    case object Rpc                  extends Scope("rpc")
    case object RpcApi               extends Scope("rpc.api")
    case object RpcNotificationsRead extends Scope("rpc.notifications.read")
    case object WebhookIncoming      extends Scope("webhook.incoming")

    def fromString(string: String): Option[Scope] = string match {
      case "bot"                    => Some(Bot)
      case "connections"            => Some(Connections)
      case "email"                  => Some(Email)
      case "identify"               => Some(Identify)
      case "guilds"                 => Some(Guilds)
      case "guilds.join"            => Some(GuildsJoin)
      case "gdm.join"               => Some(GroupDMJoin)
      case "messages.read"          => Some(MessagesRead)
      case "rpc"                    => Some(Rpc)
      case "rpc.api"                => Some(RpcApi)
      case "rpc.notifications.read" => Some(RpcNotificationsRead)
      case "webhook.incoming"       => Some(WebhookIncoming)
      case _                        => None
    }
  }

  case class AccessToken(
      accessToken: String,
      tokenType: String,
      expiresIn: Int,
      refreshToken: String,
      scopes: Seq[Scope]
  )
  object AccessToken {
    implicit val encoder: Encoder[AccessToken] = (a: AccessToken) =>
      Json.obj(
        "access_token"  -> a.accessToken.asJson,
        "token_type"    -> a.tokenType.asJson,
        "expires_in"    -> a.expiresIn.asJson,
        "refresh_token" -> a.refreshToken.asJson,
        "scope"         -> a.scopes.map(_.name).mkString(" ").asJson
    )

    implicit val decoder: Decoder[AccessToken] = (c: HCursor) =>
      for {
        accessToken  <- c.get[String]("access_token").right
        tokenType    <- c.get[String]("token_type").right
        expiresIn    <- c.get[Int]("expires_in").right
        refreshToken <- c.get[String]("refresh_token").right
        scopes       <- c.get[String]("scope").right.map(s => s.split(" ").toSeq.flatMap(Scope.fromString)).right
      } yield AccessToken(accessToken, tokenType, expiresIn, refreshToken, scopes)
  }

  case class ClientAccessToken(accessToken: String, tokenType: String, expiresIn: Int, scopes: Seq[Scope])
  object ClientAccessToken {
    implicit val encoder: Encoder[ClientAccessToken] = (a: ClientAccessToken) =>
      Json.obj(
        "access_token" -> a.accessToken.asJson,
        "token_type"   -> a.tokenType.asJson,
        "expires_in"   -> a.expiresIn.asJson,
        "scope"        -> a.scopes.map(_.name).mkString(" ").asJson
    )

    implicit val decoder: Decoder[ClientAccessToken] = (c: HCursor) =>
      for {
        accessToken <- c.get[String]("access_token").right
        tokenType   <- c.get[String]("token_type").right
        expiresIn   <- c.get[Int]("expires_in").right
        scopes      <- c.get[String]("scope").right.map(s => s.split(" ").toSeq.flatMap(Scope.fromString)).right
      } yield ClientAccessToken(accessToken, tokenType, expiresIn, scopes)
  }

  sealed abstract class GrantType(val name: String)
  object GrantType {
    case object AuthorizationCode extends GrantType("authorization_code")
    case object RefreshToken      extends GrantType("refresh_token")
    case object ClientCredentials extends GrantType("client_credentials")
  }

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
