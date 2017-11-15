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
package net.katsstuff.ackcord.commands

import akka.NotUsed
import akka.actor.Actor
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.commands.CmdRouter.{NoCommand, UnknownCommand}
import net.katsstuff.ackcord.data.{CacheSnapshot, ChannelId, Message}
import net.katsstuff.ackcord.http.requests.RequestWrapper
import net.katsstuff.ackcord.http.requests.Requests.{CreateMessage, CreateMessageData}

/**
  * A default command error handler that will send a message as response to the invalid command.
  */
trait CmdErrorHandler extends Actor {
  def client: ClientActor

  override def receive: Receive = {
    case NoCommand(msg, c) =>
      noCommandReply(msg)(c).foreach(sendMsg(msg.channelId, _))
    case UnknownCommand(msg, cat, command, args, c) =>
      unknownCommandReply(msg, cat, command, args)(c).foreach(sendMsg(msg.channelId, _))
  }

  private def sendMsg(channelId: ChannelId, data: CreateMessageData): Unit =
    client ! RequestWrapper(CreateMessage(channelId, data), NotUsed, self)

  /**
    * Create a reply for errors where no command was specified.
    * @param msg The base message.
    * @param c The current cache.
    */
  def noCommandReply(msg: Message)(implicit c: CacheSnapshot): Option[CreateMessageData]

  /**
    * Create a reply for errors where no command by that name is known.
    * @param msg The base message.
    * @param category The category that was used.
    * @param command The unknown command.
    * @param args The args passed in.
    * @param c The current cache.
    */
  def unknownCommandReply(msg: Message, category: CmdCategory, command: String, args: List[String])(
      implicit c: CacheSnapshot
  ): Option[CreateMessageData]
}
