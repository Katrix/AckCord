/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord

import scala.concurrent.Future
import scala.concurrent.duration._

import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import com.typesafe.config.ConfigFactory

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Stash}
import akka.http.scaladsl.coding.Deflate
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, ValidUpgrade, WebSocketUpgradeResponse}
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.testkit.TestKit
import akka.util.ByteString
import io.circe.{parser, Encoder, Json}
import net.katsstuff.ackcord.MockedGateway.{HasSetClient, SendMessage, SetClient, SetUseCompression}
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler.{Login, Logout}
import net.katsstuff.ackcord.http.websocket.gateway._

class GatewaySpec extends TestKit(ActorSystem("TestSystem", ConfigFactory.parseString("""
    |akka {
    |  loglevel = "DEBUG"
    |}
  """.stripMargin))) with FunSuiteLike with Matchers with BeforeAndAfterAll {
  import GatewayProtocol._

  implicit val materializer:   Materializer     = ActorMaterializer()
  implicit val notUsedEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.Null

  var gatewayNameNum = 0
  var handlerNameNum = 0

  val settings = DiscordClientSettings(token = "token123Abc")
  def mkGateway: ActorRef = {
    val a = system.actorOf(MockedGateway.props(testActor), s"Gateway$gatewayNameNum")
    gatewayNameNum += 1
    a
  }

  def mkHandler(gateway: ActorRef, cache: Option[ActorRef] = None): ActorRef = {
    val a = system.actorOf(MockedGatewayHandler.props(settings, gateway, cache), s"GatewayHandler$handlerNameNum")
    handlerNameNum += 1
    a
  }

  def mkGatewayAndHandler(cache: Option[ActorRef] = None): (ActorRef, ActorRef) = {
    val gateway = mkGateway
    val handler = mkHandler(gateway, cache)
    (gateway, handler)
  }

  def readyDispatch(seq: Int, sessionId: String): Dispatch[GatewayEvent.ReadyData] = {
    Dispatch(
      seq,
      GatewayEvent.Ready(
        GatewayEvent.ReadyData(
          v = 6,
          user = User(
            id = UserId(Snowflake("12345")),
            username = "TestBot",
            discriminator = "1234",
            avatar = None,
            bot = Some(true),
            mfaEnabled = None,
            verified = None,
            email = None
          ),
          privateChannels = Seq.empty,
          guilds = Seq.empty,
          sessionId = sessionId,
          _trace = Seq.empty
        )
      ),
    )
  }

  def defaultLogin(
      gateway: ActorRef,
      handler: ActorRef,
      sessionId: String,
      readySeq: Int,
      login: Boolean = true
  ): Unit = {
    if (login) {
      handler ! Login
    }
    expectMsg(HasSetClient)
    expectNoMessage(50.millis)

    gateway ! SendMessage(Hello(HelloData(400, Seq.empty)))
    expectMsgType[Identify]

    expectMsg(Heartbeat(None))
    gateway ! SendMessage(HeartbeatACK)

    gateway ! SendMessage(readyDispatch(readySeq, sessionId))
    within(300.millis, 500.millis) {
      expectMsg(Heartbeat(Some(readySeq)))
      gateway ! SendMessage(HeartbeatACK)
    }
  }

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  test("Mocked gateway should login and logout successfully") {
    val (gateway, handler) = mkGatewayAndHandler()
    handler ! Login
    expectMsg(HasSetClient)
    expectNoMessage(50.millis)
    handler ! Logout
    expectNoMessage(50.millis)
  }

  test("Gateway handler should identify with the correct token when sent a hello packet") {
    val (gateway, handler) = mkGatewayAndHandler()
    handler ! Login
    expectMsg(HasSetClient)
    expectNoMessage(50.millis)
    gateway ! SendMessage(Hello(HelloData(100, Seq.empty)))
    val identifyData = expectMsgType[Identify].d
    handler ! Logout
    assert(identifyData.token == settings.token)
    expectMsgType[Heartbeat]
  }

  test("Gateway handler should heartbeat as specified in the hello packet") {
    val (gateway, handler) = mkGatewayAndHandler()
    handler ! Login
    expectMsg(HasSetClient)
    expectNoMessage(50.millis)

    gateway ! SendMessage(Hello(HelloData(400, Seq.empty)))
    expectMsgType[Identify]

    expectMsg(Heartbeat(None))
    gateway ! SendMessage(HeartbeatACK)

    within(300.millis, 500.millis) {
      expectMsg(Heartbeat(None))
      gateway ! SendMessage(HeartbeatACK)
    }
    handler ! Logout
  }

  test("Gateway handler should heartbeat with correct seq after ready") {
    val (gateway, handler) = mkGatewayAndHandler()
    val sessionId          = "sessionabc123"
    val seq                = 15
    defaultLogin(gateway, handler, sessionId, seq)
    handler ! Logout
  }

  test("Gateway handler should reconnect with resume data when told to reconnect") {
    val (gateway, handler) = mkGatewayAndHandler()
    val sessionId          = "sessionabc123"
    val seq                = 15

    defaultLogin(gateway, handler, sessionId, seq)

    gateway ! SendMessage(Reconnect)
    expectMsg(HasSetClient)
    expectNoMessage(50.millis)

    gateway ! SendMessage(Hello(HelloData(200, Seq.empty)))
    val resume = expectMsgType[Resume].d

    assert(resume.seq == seq)
    assert(resume.sessionId == sessionId)
    assert(resume.token == settings.token)

    expectMsg(Heartbeat(Some(15)))
    gateway ! SendMessage(HeartbeatACK)

    handler ! Logout
  }

  test("Gateway handler should do a fresh reconnect when it receives an invalid session error") {
    val (gateway, handler) = mkGatewayAndHandler()
    val sessionId          = "sessionabc123"
    val seq                = 15

    defaultLogin(gateway, handler, sessionId, seq)

    gateway ! SendMessage(Reconnect)
    expectMsg(HasSetClient)
    expectNoMessage(50.millis)

    gateway ! SendMessage(Hello(HelloData(200, Seq.empty)))
    expectMsgType[Resume].d

    expectMsg(Heartbeat(Some(15)))
    gateway ! SendMessage(HeartbeatACK)

    gateway ! SendMessage(InvalidSession(false))
    expectNoMessage(4.5.seconds)
    defaultLogin(gateway, handler, sessionId, seq, login = false)

    handler ! Logout
  }
}

class MockedGatewayHandler(settings: DiscordClientSettings, gateway: ActorRef, cache: Option[ActorRef])(
    implicit mat: Materializer
) extends GatewayHandler(Uri./, settings, cache, identity) {

  override def wsFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = {
    val response = ValidUpgrade(HttpResponse(), None)

    val sendToServer = Sink.foreach[Message](gateway ! _)
    val sendToClient = Source.actorRef[Message](128, OverflowStrategy.fail).mapMaterializedValue { actor =>
      gateway ! SetClient(actor)
      Future.successful(response)
    }

    Flow.fromSinkAndSourceMat(sendToServer, sendToClient)(Keep.right)
  }
}
object MockedGatewayHandler {
  def props(settings: DiscordClientSettings, gateway: ActorRef, cache: Option[ActorRef])(
      implicit mat: Materializer
  ): Props =
    Props(new MockedGatewayHandler(settings, gateway, cache))
}

class MockedGateway(sendMessagesTo: ActorRef) extends Actor with Stash {
  import GatewayProtocol._

  var client: ActorRef = _
  var useCompression = false

  override def receive: Receive = {
    case SetClient(newClient) =>
      client = newClient
      unstashAll()
      sendMessagesTo ! HasSetClient
    case send @ SendMessage(msg) if client != null =>
      val payload =
        if (useCompression) BinaryMessage(Deflate.encode(ByteString.fromString(send.encoder(msg).noSpaces)))
        else TextMessage(send.encoder(msg).noSpaces)

      client ! payload
    case SendMessage(_) => stash()
    case TextMessage.Strict(text) =>
      parser.parse(text).flatMap(_.as[GatewayMessage[_]]) match {
        case Right(msg) => sendMessagesTo ! msg
        case Left(e)    => sendMessagesTo ! e
      }
    case SetUseCompression(compression) => useCompression = compression
  }
}
object MockedGateway {
  def props(sendMessagesTo: ActorRef): Props = Props(new MockedGateway(sendMessagesTo))
  case class SetClient(ref: ActorRef)
  case object HasSetClient
  case class SendMessage[A](msg: GatewayMessage[A])(implicit val encoder: Encoder[GatewayMessage[A]])
  case class SetUseCompression(useCompression: Boolean)
}
