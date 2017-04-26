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

import scala.collection.mutable

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.{EventStream, LoggingAdapter}
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.handlers.{CacheHandler, CacheSnapshotBuilder}
import net.katsstuff.akkacord.http.websocket.WsEvent.ReadyData

class SnowflakeCache(eventStream: EventStream) extends Actor with ActorLogging {

  private var prevSnapshot: CacheSnapshot = _
  private var snapshot:     CacheSnapshot = _

  private def updateSnapshot(newSnapshot: CacheSnapshot): Unit = {
    if (newSnapshot ne snapshot) {
      prevSnapshot = snapshot
      snapshot = newSnapshot
    }
  }

  private def isReady: Boolean = prevSnapshot != null && snapshot != null

  override def receive: Receive = {
    case readyHandler: CacheHandlerEvent[_]
        if readyHandler.data.isInstanceOf[ReadyData] => //An instanceOf test isn't really the best way here, but I just say a one time exception
      val builder = new CacheSnapshotBuilder(
        null, //The event will populate this
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty,
        mutable.Map.empty
      )

      readyHandler.handle(builder)(log)

      prevSnapshot = builder.toImmutable
      snapshot = prevSnapshot

      readyHandler match {
        case APIMessageHandlerEvent(data, event)           => event(data)(snapshot, prevSnapshot).foreach(eventStream.publish)
        case RequestHandlerEvent(data, sendTo, contextual) => sendTo ! RequestResponse(data, contextual)
        case _                                             =>
      }
    case handlerEvent: CacheHandlerEvent[_] if isReady =>
      val builder = CacheSnapshotBuilder(snapshot)
      handlerEvent.handle(builder)(log)

      updateSnapshot(builder.toImmutable)
      handlerEvent match {
        case APIMessageHandlerEvent(data, event)           => event(data)(snapshot, prevSnapshot).foreach(eventStream.publish)
        case RequestHandlerEvent(data, sendTo, contextual) => sendTo ! RequestResponse(data, contextual)
        case _                                             =>
      }
    case _ if !isReady => log.error("Received event before ready")
  }
}
object SnowflakeCache {
  def props(eventStream: EventStream): Props = Props(classOf[SnowflakeCache], eventStream)
}

sealed trait CacheHandlerEvent[Data] {
  def data:    Data
  def handler: CacheHandler[Data]

  def handle(builder: CacheSnapshotBuilder)(implicit log: LoggingAdapter): Unit = handler.handle(builder, data)
}
case class APIMessageHandlerEvent[Data](data: Data, sendEvent: Data => (CacheSnapshot, CacheSnapshot) => Option[APIMessage])(
    implicit val handler:                     CacheHandler[Data]
) extends CacheHandlerEvent[Data]
case class RequestHandlerEvent[Data, Context](data: Data, sendTo: ActorRef, context: Context = NotUsed)(implicit val handler: CacheHandler[Data])
    extends CacheHandlerEvent[Data]
case class MiscHandlerEvent[Data](data: Data)(implicit val handler: CacheHandler[Data]) extends CacheHandlerEvent[Data]

case class RequestResponse[Data, Context](data: Data, context: Context)
