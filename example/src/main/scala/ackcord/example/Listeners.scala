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

package ackcord.example

import ackcord._
import ackcord.data._
import akka.NotUsed

class Listeners(client: DiscordClient) extends EventsController(client.requests) {

  val MessageEvent: EventListenerBuilder[TextChannelEventListenerMessage, APIMessage.MessageCreate] =
    TextChannelEvent.on[APIMessage.MessageCreate]

  def listen(inChannel: TextChannelId, identifier: String): EventListener[APIMessage.MessageCreate, NotUsed] =
    MessageEvent.withSideEffects { m =>
      if (m.channel.id == inChannel) {
        println(s"$identifier: ${m.event.message.content}")
      }
    }

  def stopListen(
      inChannel: TextChannelId,
      identifier: String,
      listener: EventRegistration[NotUsed],
      stopper: EventRegistration[NotUsed]
  ): EventListener[APIMessage.MessageCreate, NotUsed] =
    MessageEvent.withSideEffects { m =>
      if (m.channel.id == inChannel && m.event.message.content == "stop listen " + identifier) {
        listener.stop()
        stopper.stop()
      }
    }

  val createListeners: EventListener[APIMessage.MessageCreate, NotUsed] =
    MessageEvent.withSideEffects { m =>
      val startMessage = m.event.message

      if (startMessage.content.startsWith("start listen ")) {
        val identifier = startMessage.content.replaceFirst("start listen ", "")

        val listener = client.registerListener(listen(startMessage.channelId, identifier))

        lazy val stopper: EventRegistration[NotUsed] =
          client.registerListener(stopListen(startMessage.channelId, identifier, listener, stopper))

        //Initialize stopper
        stopper
      }
    }
}
