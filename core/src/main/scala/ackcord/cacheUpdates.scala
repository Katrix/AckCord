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
package ackcord

import ackcord.cachehandlers.{
  CacheDeleter,
  CacheHandler,
  CacheSnapshotBuilder,
  CacheTypeRegistry,
  CacheUpdater,
  NOOPHandler
}
import akka.event.LoggingAdapter

/**
  * Represents some sort of event handled by the cache
  */
trait CacheEvent {

  /**
    * Updates a [[ackcord.cachehandlers.CacheSnapshotBuilder]] according to this event.
    */
  def process(builder: CacheSnapshotBuilder)(implicit log: LoggingAdapter): Unit
}

/**
  * An event that should publish an [[APIMessage]].
  * @param data The data.
  * @param sendEvent A function to gather the needed variables to send the
  *                  event.
  * @param handler The handler to process the data of this event with.
  * @param registry The handler registry that the event will use to update the snapshot.
  * @tparam Data The data it contains.
  */
case class APIMessageCacheUpdate[Data](
    data: Data,
    sendEvent: CacheState => Option[APIMessage],
    handler: CacheHandler[Data],
    registry: CacheTypeRegistry
) extends CacheEvent {

  override def process(builder: CacheSnapshotBuilder)(implicit log: LoggingAdapter): Unit =
    handler.handle(builder, data, registry)
}
