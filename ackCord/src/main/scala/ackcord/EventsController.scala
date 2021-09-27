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

import scala.concurrent.ExecutionContext

import cats.~>

abstract class EventsController(val requests: Requests) {

  val requestHelper: RequestsHelper = new RequestsHelper(requests)

  implicit val ec: ExecutionContext = requests.system.executionContext

  implicit def findCache[A](implicit
      message: EventListenerMessage[A]
  ): CacheSnapshot = message.cacheSnapshot

  /**
    * The base event handler builder that you can build off if you don't like
    * the default provided builder.
    */
  val baseEventBuilder: EventListenerBuilder[EventListenerMessage, APIMessage] =
    EventListenerBuilder.rawBuilder(requests)

  /** An alias for the base builder. */
  val Event: EventListenerBuilder[EventListenerMessage, APIMessage] =
    baseEventBuilder

  val GuildEvent: EventListenerBuilder[GuildEventListenerMessage, APIMessage] =
    Event.andThen(
      EventListenerBuilder.guildEvent { g =>
        Lambda[EventListenerMessage ~> GuildEventListenerMessage](m =>
          GuildEventListenerMessage.Default(g, m)
        )
      }
    )

  val ChannelEvent
      : EventListenerBuilder[ChannelEventListenerMessage, APIMessage] =
    Event.andThen(
      EventListenerBuilder.channelEvent { c =>
        Lambda[EventListenerMessage ~> ChannelEventListenerMessage](m =>
          ChannelEventListenerMessage.Default(c, m)
        )
      }
    )

  val TextChannelEvent
      : EventListenerBuilder[TextChannelEventListenerMessage, APIMessage] =
    ChannelEvent.andThen(
      EventListenerBuilder.textChannelEvent { c =>
        Lambda[ChannelEventListenerMessage ~> TextChannelEventListenerMessage](
          m => TextChannelEventListenerMessage.Default(c, m)
        )
      }
    )

  val TextGuildChannelEvent
      : EventListenerBuilder[TextGuildChannelEventListenerMessage, APIMessage] =
    ChannelEvent.andThen(
      EventListenerBuilder.textGuildChannelEvent { (c, g) =>
        Lambda[
          ChannelEventListenerMessage ~> TextGuildChannelEventListenerMessage
        ](m => TextGuildChannelEventListenerMessage.Default(c, g, m))
      }
    )

  val VGuildChannelEvent
      : EventListenerBuilder[VGuildChannelEventListenerMessage, APIMessage] =
    ChannelEvent.andThen(
      EventListenerBuilder.voiceGuildChannelEvent { (c, g) =>
        Lambda[
          ChannelEventListenerMessage ~> VGuildChannelEventListenerMessage
        ](m => VGuildChannelEventListenerMessage.Default(c, g, m))
      }
    )

  val GuildUserEvent
      : EventListenerBuilder[GuildUserEventListenerMessage, APIMessage] =
    GuildEvent.andThen(
      EventListenerBuilder.guildUserEvent { (g, u, gm) =>
        Lambda[GuildEventListenerMessage ~> GuildUserEventListenerMessage](m =>
          GuildUserEventListenerMessage.Default(g, u, gm, m)
        )
      }
    )

}
