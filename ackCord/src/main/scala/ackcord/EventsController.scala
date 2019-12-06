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

import ackcord.commands.CommandMessage
import cats.~>

abstract class EventsController(val requests: Requests) {

  val requestHelper: RequestsHelper = new RequestsHelper(requests)

  implicit val ec: ExecutionContext = requests.system.executionContext

  implicit def findCache[A](implicit message: CommandMessage[A]): CacheSnapshot = message.cache

  /**
    * The base event handler builder that you can build off if you don't like the
    * default provided builder.
    */
  val baseEventBuilder: EventHandlerBuilder[EventHandlerMessage, APIMessage] = EventHandlerBuilder.rawBuilder(requests)

  /**
    * An alias for the base builder.
    */
  val Event: EventHandlerBuilder[EventHandlerMessage, APIMessage] = baseEventBuilder

  val GuildEvent: EventHandlerBuilder[GuildEventHandlerMessage, APIMessage] =
    Event.andThen(
      EventHandlerBuilder.guildEvent { g =>
        Lambda[EventHandlerMessage ~> GuildEventHandlerMessage](m => GuildEventHandlerMessage.Default(g, m))
      }
    )

  val ChannelEvent: EventHandlerBuilder[ChannelEventHandlerMessage, APIMessage] =
    Event.andThen(
      EventHandlerBuilder.channelEvent { c =>
        Lambda[EventHandlerMessage ~> ChannelEventHandlerMessage](m => ChannelEventHandlerMessage.Default(c, m))
      }
    )

  val TChannelEvent: EventHandlerBuilder[TChannelEventHandlerMessage, APIMessage] =
    ChannelEvent.andThen(
      EventHandlerBuilder.tChannelEvent { c =>
        Lambda[ChannelEventHandlerMessage ~> TChannelEventHandlerMessage](
          m => TChannelEventHandlerMessage.Default(c, m)
        )
      }
    )
  val TGuildChannelEvent: EventHandlerBuilder[TGuildChannelEventHandlerMessage, APIMessage] = ChannelEvent.andThen(
    EventHandlerBuilder.tGuildChannelEvent { (c, g) =>
      Lambda[ChannelEventHandlerMessage ~> TGuildChannelEventHandlerMessage](
        m => TGuildChannelEventHandlerMessage.Default(c, g, m)
      )
    }
  )
  val VGuildChannelEvent: EventHandlerBuilder[VGuildChannelEventHandlerMessage, APIMessage] = ChannelEvent.andThen(
    EventHandlerBuilder.vGuildChannelEvent { (c, g) =>
      Lambda[ChannelEventHandlerMessage ~> VGuildChannelEventHandlerMessage](
        m => VGuildChannelEventHandlerMessage.Default(c, g, m)
      )
    }
  )

  val GuildUserEvent: EventHandlerBuilder[GuildUserEventHandlerMessage, APIMessage] = GuildEvent.andThen(
    EventHandlerBuilder.guildUserEvent { (g, u, gm) =>
      Lambda[GuildEventHandlerMessage ~> GuildUserEventHandlerMessage](
        m => GuildUserEventHandlerMessage.Default(g, u, gm, m)
      )
    }
  )

}
