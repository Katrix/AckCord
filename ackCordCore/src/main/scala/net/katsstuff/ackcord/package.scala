package net.katsstuff

import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.{Alternative, Contravariant, Functor, MonadError}
import net.katsstuff.ackcord.util.StreamInstances

package object ackcord {

  val RequestHelper: requests.RequestHelper.type = requests.RequestHelper
  type RequestHelper = requests.RequestHelper

  val BotAuthentication: requests.BotAuthentication.type = requests.BotAuthentication
  type BotAuthentication = requests.BotAuthentication.type

  val Request: requests.Request.type = requests.Request
  type Request[Data, Ctx] = requests.Request[Data, Ctx]

  val GatewaySettings: gateway.GatewaySettings.type = gateway.GatewaySettings
  type GatewaySettings = gateway.GatewaySettings

  val GatewayLogin: gateway.GatewayHandler.Login.type = gateway.GatewayHandler.Login
  type GatewayLogin = gateway.GatewayHandler.Login.type

  val GatewayLogout: gateway.GatewayHandler.Logout.type = gateway.GatewayHandler.Logout
  type GatewayLogout = gateway.GatewayHandler.Logout.type

  val VoiceLogin: voice.VoiceWsHandler.Login.type = voice.VoiceWsHandler.Login
  type VoiceLogin = voice.VoiceWsHandler.Login.type

  val VoiceLogout: voice.VoiceWsHandler.Logout.type = voice.VoiceWsHandler.Logout
  type VoiceLogout = voice.VoiceWsHandler.Logout.type

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
