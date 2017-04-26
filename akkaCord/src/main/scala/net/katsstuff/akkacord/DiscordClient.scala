/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
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
package net.katsstuff.akkacord

import akka.AkkaException
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.event.EventStream
import net.katsstuff.akkacord.http.websocket.{WsHandler, WsMessage}

class DiscordClient(token: String, eventStream: EventStream, settings: DiscordClientSettings) extends Actor with ActorLogging {
  private implicit val system = context.system

  private val cache     = system.actorOf(SnowflakeCache.props(eventStream), "SnowflakeCache")
  private val wsHandler = system.actorOf(WsHandler.props(token, cache, settings), "WsHandler")

  override def supervisorStrategy: SupervisorStrategy = {
    val strategy: PartialFunction[Throwable, SupervisorStrategy.Directive] = {
      case _: NotImplementedError => SupervisorStrategy.Resume
      case e: Exception if !e.isInstanceOf[AkkaException] =>
        e.printStackTrace()
        SupervisorStrategy.Resume
    }

    OneForOneStrategy()(strategy orElse SupervisorStrategy.defaultDecider)
  }

  override def preStart(): Unit =
    context.watch(wsHandler)

  override def receive: Receive = {
    case DiscordClient.ShutdownClient => wsHandler.forward(DiscordClient.ShutdownClient)
    case wsMessage: WsMessage[_] => wsHandler.forward(wsMessage)
    case Terminated(`wsHandler`) => system.terminate()
  }
}
object DiscordClient {
  def props(token: String, eventStream: EventStream, settings: DiscordClientSettings): Props =
    Props(classOf[DiscordClient], token, eventStream, settings)
  case object ShutdownClient
}

case class DiscordClientSettings(
    token:                String,
    system:               ActorSystem,
    eventStream:          EventStream,
    maxReconnectAttempts: Int = 5,
    largeThreshold:       Int = 100,
    shardNum:             Int = 0,
    shardTotal:           Int = 1
) {
  def connect: ActorRef = system.actorOf(DiscordClient.props(token, eventStream, this), "DiscordClient")
}