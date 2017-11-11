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

import scala.collection.mutable

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.EventStream
import net.katsstuff.ackcord.handlers.CacheSnapshotBuilder
import net.katsstuff.ackcord.http.websocket.gateway.GatewayEvent.ReadyData

/**
  * Handles managing the cache and converting the raw objects received into
  * more friendlier objects to work with.
  * @param eventStream And eventStream to push events to
  */
class SnowflakeCache(eventStream: EventStream) extends Actor with ActorLogging {

  private var state: CacheState = _

  /**
    * We only handle events when we are ready to, and we have received
    * the ready event.
    */
  private def isReady: Boolean = state != null

  override def receive: Receive = {
    case readyEvent @ APIMessageHandlerEvent(_: ReadyData, sendEvent, _) =>
      val builder = new CacheSnapshotBuilder(
        null, //The event will populate this,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty
      )

      readyEvent.handle(builder)(log)

      val snapshot = builder.toImmutable
      state = CacheState(snapshot, snapshot)

      sendEvent(state).foreach(eventStream.publish)
    case handlerEvent: CacheHandlerEvent[_] if isReady =>
      val builder = CacheSnapshotBuilder(state.current)
      handlerEvent.handle(builder)(log)

      state = state.update(builder.toImmutable)

      handlerEvent match {
        case event: APIMessageHandlerEvent[_] =>
          event.sendEvent(state).foreach(eventStream.publish)
        case event: SendHandledDataEvent[_] =>
          event.findData(state).foreach(event.sendTo ! _)
        case _ =>
      }
    case _ if !isReady => log.error("Received event before ready")
  }
}
object SnowflakeCache {
  def props(eventStream: EventStream): Props = Props(new SnowflakeCache(eventStream))
}