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

import scala.language.{higherKinds, implicitConversions}

import scala.concurrent.Future

import ackcord.util.StreamInstances
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.{Alternative, Contravariant, Functor, MonadError}

package object ackcord {

  val RequestHelper: requests.RequestHelper.type = requests.RequestHelper
  type RequestHelper = requests.RequestHelper

  val BotAuthentication: requests.BotAuthentication.type = requests.BotAuthentication
  type BotAuthentication = requests.BotAuthentication.type

  val GatewaySettings: gateway.GatewaySettings.type = gateway.GatewaySettings
  type GatewaySettings = gateway.GatewaySettings

  val GatewayLogin: gateway.GatewayHandler.Login.type = gateway.GatewayHandler.Login
  type GatewayLogin = gateway.GatewayHandler.Login.type

  val GatewayLogout: gateway.GatewayHandler.Logout.type = gateway.GatewayHandler.Logout
  type GatewayLogout = gateway.GatewayHandler.Logout.type

  val Streamable: util.Streamable.type = util.Streamable
  type Streamable[F[_]] = util.Streamable[F]

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
