package net.katsstuff

import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}

import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.{Alternative, Contravariant, Functor, MonadError}
import net.katsstuff.ackcord.util.StreamInstances

package object ackcord {

  val RequestHelper: http.requests.RequestHelper.type = http.requests.RequestHelper
  type RequestHelper = http.requests.RequestHelper

  val BotAuthentication: http.requests.BotAuthentication.type = http.requests.BotAuthentication
  type BotAuthentication = http.requests.BotAuthentication.type

  val Request: http.requests.Request.type = http.requests.Request
  type Request[Data, Ctx] = http.requests.Request[Data, Ctx]

  val GatewaySettings: websocket.gateway.GatewaySettings.type = websocket.gateway.GatewaySettings
  type GatewaySettings = websocket.gateway.GatewaySettings

  val GatewayLogin: websocket.gateway.GatewayHandler.Login.type = websocket.gateway.GatewayHandler.Login
  type GatewayLogin = websocket.gateway.GatewayHandler.Login.type

  val GatewayLogout: websocket.gateway.GatewayHandler.Logout.type = websocket.gateway.GatewayHandler.Logout
  type GatewayLogout = websocket.gateway.GatewayHandler.Logout.type

  val VoiceLogin: websocket.voice.VoiceWsHandler.Login.type = websocket.voice.VoiceWsHandler.Login
  type VoiceLogin = websocket.voice.VoiceWsHandler.Login.type

  val VoiceLogout: websocket.voice.VoiceWsHandler.Logout.type = websocket.voice.VoiceWsHandler.Logout
  type VoiceLogout = websocket.voice.VoiceWsHandler.Logout.type

  val Streamable: util.Streamable.type = util.Streamable
  type Streamable[F[_]] = util.Streamable[F]

  val MessageParser: util.MessageParser.type = util.MessageParser
  type MessageParser[A] = util.MessageParser[A]

  val JsonOption: util.JsonOption.type = util.JsonOption
  type JsonOption[A] = util.JsonOption[A]

  val JsonSome: util.JsonSome.type = util.JsonSome
  type JsonSome[A] = util.JsonSome[A]

  val JsonNull: util.JsonNull.type = util.JsonNull
  type JsonNull = util.JsonNull.type

  val JsonUndefined: util.JsonUndefined.type = util.JsonUndefined
  type JsonUndefined = util.JsonUndefined.type

  type SourceRequest[A]       = StreamInstances.SourceRequest[A]
  type FutureVectorRequest[A] = Future[Vector[A]]

  implicit def sourceSyntax[A, M](source: Source[A, M]): StreamInstances.SourceFlatmap[A, M] =
    StreamInstances.SourceFlatmap(source)

  implicit val sourceMonadInstance: MonadError[SourceRequest, Throwable] with Alternative[SourceRequest] =
    StreamInstances.sourceInstance
  implicit def flowFunctorInstance[In, Mat]: Functor[Flow[In, ?, Mat]]     = StreamInstances.flowInstance[In, Mat]
  implicit def sinkContravariantInstance[Mat]: Contravariant[Sink[?, Mat]] = StreamInstances.sinkInstance[Mat]
}
