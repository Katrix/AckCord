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
package net.katsstuff.akkacord.handlers
package ws

import akka.event.LoggingAdapter
import net.katsstuff.akkacord.APIMessage
import net.katsstuff.akkacord.data.CacheSnapshot
import net.katsstuff.akkacord.http.websocket.WsEvent.RawPartialMessage

object WsHandlerMessageUpdate extends Handler[RawPartialMessage, APIMessage.MessageUpdate] {
  override def handle(snapshot: CacheSnapshot,
      rawMessage: RawPartialMessage)(implicit
      log: LoggingAdapter): AbstractHandlerResult[APIMessage.MessageUpdate] = {
    val newUsers = rawMessage.mentions.getOrElse(Seq.empty).map(u => u.id -> u)
    val res = snapshot.getMessage(rawMessage.channelId, rawMessage.id).map { message =>
      val newMessage = message.copy(
        author = rawMessage.author.getOrElse(message.author),
        content = rawMessage.content.getOrElse(message.content),
        timestamp = rawMessage.timestamp.getOrElse(message.timestamp),
        editedTimestamp = rawMessage.editedTimestamp.orElse(message.editedTimestamp),
        tts = rawMessage.tts.getOrElse(message.tts),
        mentionEveryone = rawMessage.mentionEveryone.getOrElse(message.mentionEveryone),
        mentions = rawMessage.mentions.map(_.map(_.id)).getOrElse(message.mentions),
        mentionRoles = rawMessage.mentionRoles.getOrElse(message.mentionRoles),
        attachment = rawMessage.attachment.getOrElse(message.attachment),
        embeds = rawMessage.embeds.getOrElse(message.embeds),
        reactions = rawMessage.reactions.getOrElse(message.reactions),
        nonce = rawMessage.nonce.orElse(message.nonce),
        pinned = rawMessage.pinned.getOrElse(message.pinned),
        webhookId = rawMessage.webhookId.orElse(message.webhookId)
      )

      val newChannelMessages = snapshot.messages.getOrElse(rawMessage.channelId, Map.empty) + ((rawMessage.id, newMessage))
      val newMessages        = snapshot.messages + ((rawMessage.channelId, newChannelMessages))
      val newSnapshot = snapshot.copy(users = snapshot.users ++ newUsers, messages = newMessages)
      HandlerResult(newSnapshot, APIMessage.MessageUpdate(newMessage, _, _))
    }

    res.getOrElse(NoHandlerResult)
  }
}
