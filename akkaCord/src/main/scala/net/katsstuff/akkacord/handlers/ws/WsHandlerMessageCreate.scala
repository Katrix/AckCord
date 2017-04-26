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
import net.katsstuff.akkacord.data.{CacheSnapshot, Message}
import net.katsstuff.akkacord.http.RawMessage

object WsHandlerMessageCreate extends Handler[RawMessage, APIMessage.MessageCreate] {
  override def handle(snapshot: CacheSnapshot, rawMessage: RawMessage)(implicit
      log: LoggingAdapter): AbstractHandlerResult[APIMessage.MessageCreate] = {
    val users = rawMessage.mentions.map(u => u.id -> u)
    val message = Message(
      id = rawMessage.id,
      channelId = rawMessage.channelId,
      author = rawMessage.author,
      content = rawMessage.content,
      timestamp = rawMessage.timestamp,
      editedTimestamp = rawMessage.editedTimestamp,
      tts = rawMessage.tts,
      mentionEveryone = rawMessage.mentionEveryone,
      mentions = rawMessage.mentions.map(_.id),
      mentionRoles = rawMessage.mentionRoles,
      attachment = rawMessage.attachment,
      embeds = rawMessage.embeds,
      reactions = rawMessage.reactions.getOrElse(Seq.empty),
      nonce = rawMessage.nonce,
      pinned = rawMessage.pinned,
      webhookId = rawMessage.webhookId
    )

    val newChannelMessages = snapshot.messages.getOrElse(rawMessage.channelId, Map.empty) + ((message.id, message))
    val newMessages        = snapshot.messages + ((rawMessage.channelId, newChannelMessages))
    val newSnapshot = snapshot.copy(users = snapshot.users ++ users, messages = newMessages)

    HandlerResult(newSnapshot, APIMessage.MessageCreate(message, _, _))
  }
}
