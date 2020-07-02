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

import scala.reflect.ClassTag

import ackcord.cachehandlers.{CacheHandler, CacheSnapshotBuilder, CacheTypeRegistry}
import ackcord.gateway.Dispatch
import ackcord.requests.{BaseRESTRequest, Request, RequestAnswer, RequestResponse}
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import org.slf4j.Logger

/**
  * Represents some sort of event handled by the cache
  */
trait CacheEvent {

  /**
    * Updates a [[ackcord.cachehandlers.CacheSnapshotBuilder]] according to this event.
    */
  def process(builder: CacheSnapshotBuilder)(implicit log: Logger): Unit
}

/**
  * An event that should publish an [[APIMessage]].
  * @param data The data.
  * @param sendEvent A function to gather the needed variables to send the
  *                  event.
  * @param handler The handler to process the data of this event with.
  * @param registry The handler registry that the event will use to update the snapshot.
  * @param dispatch The low level message that created this update.
  * @tparam Data The data it contains.
  */
case class APIMessageCacheUpdate[Data](
    data: Data,
    sendEvent: CacheState => Option[APIMessage],
    handler: CacheHandler[Data],
    registry: CacheTypeRegistry,
    dispatch: Dispatch[_]
) extends CacheEvent {

  override def process(builder: CacheSnapshotBuilder)(implicit log: Logger): Unit =
    handler.handle(builder, data, registry)
}

/**
  * A cache event that will try to put the data of the response into the cache.
  * @param requestResponse The response to the request.
  * @param request The request used to get the response.
  * @param registry The handler registry that the event will use to update the snapshot.
  * @tparam Data The type of the request response.
  */
case class RequestCacheUpdate[Data](
    requestResponse: RequestResponse[Data],
    request: Request[Data],
    registry: CacheTypeRegistry
) extends CacheEvent {

  override def process(builder: CacheSnapshotBuilder)(implicit log: Logger): Unit = {
    registry.getUpdater(ClassTag[Data](requestResponse.data.getClass)) match {
      case Some(updater) => updater.handle(builder, requestResponse.data, registry)
      case None =>
        request match {
          case request: BaseRESTRequest[Data, nice] =>
            val niceData = request.toNiceResponse(requestResponse.data)
            registry.updateData(builder)(niceData)(ClassTag[nice](niceData.getClass))

          case _ =>
        }
    }
  }
}
object RequestCacheUpdate {

  /**
    * An extra processor for [[Requests]] which will try to place the gotten
    * objects in the cache. Note: This might fail to place the type in the
    * registry if there is no handler for it in the cache registry passed in.
    *
    * @param cache The cache to place the objects in.
    * @param registry The cache registry to use for finding cache handlers.
    */
  def requestsProcessor[Data](
      cache: Cache,
      registry: CacheTypeRegistry
  ): Sink[(Request[Data], RequestAnswer[Data]), NotUsed] =
    Flow[(Request[Data], RequestAnswer[Data])]
      .collect {
        case (request, response: RequestResponse[Data]) => RequestCacheUpdate(response, request, registry)
      }
      .to(cache.publish)
}
