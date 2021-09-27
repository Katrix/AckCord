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

import java.time.OffsetDateTime

import scala.collection.immutable
import scala.concurrent.Future

import ackcord.data.DiscordProtocol._
import ackcord.data.{Application, ApplicationId, User}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest, Uri}
import akka.stream.scaladsl.{Sink, Source}
import enumeratum.values.{StringEnum, StringEnumEntry}
import io.circe._
import io.circe.syntax._

object OAuth {

  sealed abstract class Scope(val value: String) extends StringEnumEntry
  object Scope extends StringEnum[Scope] {
    override def values: immutable.IndexedSeq[Scope] = findValues

    case object ActivitiesRead             extends Scope("activities.read")
    case object ActivitiesWrite            extends Scope("activities.write")
    case object ApplicationsBuildsRead     extends Scope("applications.builds.read")
    case object ApplicationsBuildsUpload   extends Scope("applications.builds.upload")
    case object ApplicationsCommands       extends Scope("applications.commands")
    case object ApplicationsCommandsUpdate extends Scope("applications.commands.update")
    case object ApplicationsEntitlements   extends Scope("applications.entitlements")
    case object ApplicationsStoreUpdate    extends Scope("applications.store.update")
    case object Bot                        extends Scope("bot")
    case object Connections                extends Scope("connections")
    case object Email                      extends Scope("email")
    case object GDMJoin                    extends Scope("gdm.join")
    case object Guilds                     extends Scope("guilds")
    case object GuildsJoin                 extends Scope("guilds.join")
    case object Identify                   extends Scope("identify")
    case object MessagesRead               extends Scope("messages.read")
    case object RelationshipsRead          extends Scope("relationships.read")
    case object Rpc                        extends Scope("rpc")
    case object RpcActivitiesWrite         extends Scope("rpc.activities.write")
    case object RpcNotificationsRead       extends Scope("rpc.notifications.read")
    case object RpcVoiceRead               extends Scope("rpc.voice.read")
    case object RpcWriteRead               extends Scope("rpc.voice.write")
    case object WebhookIncoming            extends Scope("webhook.incoming")
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
        "scope"         -> a.scopes.map(_.value).mkString(" ").asJson
      )

    implicit val decoder: Decoder[AccessToken] = (c: HCursor) =>
      for {
        accessToken  <- c.get[String]("access_token")
        tokenType    <- c.get[String]("token_type")
        expiresIn    <- c.get[Int]("expires_in")
        refreshToken <- c.get[String]("refresh_token")
        scopes       <- c.get[String]("scope").map(s => s.split(" ").toSeq.flatMap(Scope.withValueOpt))
      } yield AccessToken(accessToken, tokenType, expiresIn, refreshToken, scopes)
  }

  case class ClientAccessToken(accessToken: String, tokenType: String, expiresIn: Int, scopes: Seq[Scope])
  object ClientAccessToken {
    implicit val encoder: Encoder[ClientAccessToken] = (a: ClientAccessToken) =>
      Json.obj(
        "access_token" -> a.accessToken.asJson,
        "token_type"   -> a.tokenType.asJson,
        "expires_in"   -> a.expiresIn.asJson,
        "scope"        -> a.scopes.map(_.value).mkString(" ").asJson
      )

    implicit val decoder: Decoder[ClientAccessToken] = (c: HCursor) =>
      for {
        accessToken <- c.get[String]("access_token")
        tokenType   <- c.get[String]("token_type")
        expiresIn   <- c.get[Int]("expires_in")
        scopes      <- c.get[String]("scope").map(s => s.split(" ").toSeq.flatMap(Scope.withValueOpt))
      } yield ClientAccessToken(accessToken, tokenType, expiresIn, scopes)
  }

  sealed abstract class GrantType(val name: String)
  object GrantType {
    case object AuthorizationCode extends GrantType("authorization_code")
    case object RefreshToken      extends GrantType("refresh_token")
    case object ClientCredentials extends GrantType("client_credentials")
  }

  sealed abstract class PromptType(val name: String)
  object PromptType {
    case object Consent    extends PromptType("consent")
    case object NonePrompt extends PromptType("none")
  }

  def baseGrant(
      clientId: String,
      scopes: Seq[Scope],
      state: String,
      redirectUri: String,
      responseType: String,
      prompt: PromptType
  ): Uri =
    Routes.oAuth2Authorize.applied.withQuery(
      Uri.Query(
        "response_type" -> responseType,
        "client_id"     -> clientId,
        "scope"         -> scopes.map(_.value).mkString(" "),
        "state"         -> state,
        "redirect_uri"  -> redirectUri,
        "prompt"        -> prompt.name
      )
    )

  def codeGrantUri(clientId: String, scopes: Seq[Scope], state: String, redirectUri: String, prompt: PromptType): Uri =
    baseGrant(clientId, scopes, state, redirectUri, "code", prompt)

  def implicitGrantUri(clientId: String, scopes: Seq[Scope], state: String, redirectUri: String): Uri =
    baseGrant(clientId, scopes, state, redirectUri, "token", PromptType.Consent)

  def baseExchange(
      clientId: String,
      clientSecret: String,
      grantType: GrantType,
      code: Option[String],
      refreshToken: Option[String],
      redirectUri: String,
      scopes: Seq[Scope]
  )(
      implicit system: ActorSystem[Nothing]
  ): Future[AccessToken] = {
    import system.executionContext
    val baseFormData = Map(
      "grant_type"   -> grantType.name,
      "redirect_uri" -> redirectUri,
      "scope"        -> scopes.map(_.value).mkString(" ")
    )

    val formDataWithCode = code.fold(baseFormData)(code => baseFormData + ("code" -> code))
    val formDataWithRefresh =
      refreshToken.fold(formDataWithCode)(token => formDataWithCode + ("refresh_token" -> token))

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Routes.oAuth2Token.applied,
      headers = List(Authorization(BasicHttpCredentials(clientId, clientSecret))),
      entity = FormData(formDataWithRefresh).toEntity
    )

    Http(system.toClassic)
      .singleRequest(request)
      .flatMap(r => Source.single(r.entity).via(RequestStreams.jsonDecode).runWith(Sink.head))
      .map(_.as[AccessToken].fold(throw _, identity))
  }

  def tokenExchange(
      clientId: String,
      clientSecret: String,
      grantType: GrantType,
      code: String,
      redirectUri: String,
      scopes: Seq[Scope]
  )(
      implicit system: ActorSystem[Nothing]
  ): Future[AccessToken] = baseExchange(clientId, clientSecret, grantType, Some(code), None, redirectUri, scopes)

  def refreshTokenExchange(
      clientId: String,
      clientSecret: String,
      grantType: GrantType,
      refreshToken: String,
      redirectUri: String,
      scopes: Seq[Scope]
  )(
      implicit system: ActorSystem[Nothing]
  ): Future[AccessToken] =
    baseExchange(clientId, clientSecret, grantType, None, Some(refreshToken), redirectUri, scopes)

  def clientCredentialsGrant(clientId: String, clientSecret: String, scopes: Seq[Scope])(
      implicit system: ActorSystem[Nothing]
  ): Future[ClientAccessToken] = {
    import system.executionContext
    val formData =
      FormData("grant_type" -> GrantType.ClientCredentials.name, "scope" -> scopes.map(_.value).mkString(" "))

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Routes.oAuth2Token.applied,
      headers = List(Authorization(BasicHttpCredentials(clientId, clientSecret))),
      entity = formData.toEntity
    )

    Http(system.toClassic)
      .singleRequest(request)
      .flatMap(r => Source.single(r.entity).via(RequestStreams.jsonDecode).runWith(Sink.head))
      .map(_.as[ClientAccessToken].fold(throw _, identity))
  }

  case object GetCurrentBotApplicationInformation extends NoParamsNiceResponseRequest[Application] {
    //noinspection NameBooleanParameters
    override def responseDecoder: Decoder[Application] = Decoder[Application]
    override def route: RequestRoute                   = Routes.getCurrentApplication
  }

  case object GetCurrentAuthorizationInformation extends NoParamsNiceResponseRequest[AuthorizationInformation] {
    override def responseDecoder: Decoder[AuthorizationInformation] = {
      implicit val applicationDecoder: Decoder[AuthorizationInformationApplication] =
        derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)

      derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)
    }
    override def route: RequestRoute = Routes.getCurrentAuthorizationInformation
  }

  case class AuthorizationInformation(
      application: AuthorizationInformationApplication,
      scopes: Seq[String],
      expires: OffsetDateTime,
      user: Option[User]
  )

  case class AuthorizationInformationApplication(
      id: ApplicationId,
      name: String,
      icon: String,
      description: String,
      summary: String,
      hook: Boolean,
      botPublic: Boolean,
      botRequireCodeGrant: Boolean,
      verifyKey: String
  )

}
