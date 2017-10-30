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
package net.katsstuff.ackcord.util

import scala.collection.mutable

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.Logging
import akka.event.Logging.LogLevel
import akka.routing.Broadcast
import akka.stream.Attributes.LogLevels
import net.katsstuff.ackcord.DiscordClient.ShutdownClient
import net.katsstuff.ackcord.data.{GuildChannel, GuildId}
import net.katsstuff.ackcord.util.GuildRouter.{GetGuildActor, ResponseGetGuild, TerminatedGuild}
import net.katsstuff.ackcord.{APIMessage, DiscordClient}

/**
  * Will send all [[APIMessage]]s with the same guild
  * to the same actor. Also obeys [[Broadcast]]
  * Handles
  * - ChannelMessage
  * - GuildMessage
  * - MessageMessage
  * - VoiceStateUpdate
  * It also respects [[DiscordClient.ShutdownClient]].
  * It sends the shutdown to all it's children, and when all the children have
  * stopped, it stops itself. The child actors will not receive any further
  * events once a shutdown has been started.
  * @param props The function to obtain a props used for constructing handlers
  * @param notGuildHandler For some messages that could be directed to a guild
  *                        but not always, if the message is not directed
  *                        towards a guild, it will me sent here instead.
  */
class GuildRouter(props: GuildId => Props, notGuildHandler: Option[ActorRef]) extends Actor with ActorLogging {
  val handlers       = mutable.HashMap.empty[GuildId, ActorRef]
  var isShuttingDown = false

  override def receive: Receive = {
    case msg: APIMessage.GuildMessage => sendToGuild(msg.guild.id, msg)
    case msg: APIMessage.ChannelMessage =>
      msg.channel match {
        case ch: GuildChannel => sendToGuild(ch.guildId, msg)
        case _                => sendToNotGuild(msg)
      }
    case msg: APIMessage.MessageMessage =>
      msg.message.channel(msg.snapshot) match {
        case Some(gchannel: GuildChannel) => sendToGuild(gchannel.guildId, msg)
        case _                            => sendToNotGuild(msg)
      }
    case msg @ APIMessage.VoiceStateUpdate(state, _, _) =>
      state.guildId match {
        case Some(guildId) => sendToGuild(guildId, msg)
        case None          => sendToNotGuild(msg)
      }
    case GetGuildActor(guildId) => if (!isShuttingDown) sender() ! ResponseGetGuild(getGuild(guildId))
    case Broadcast(msg)         => handlers.values.foreach(_ ! msg)
    case DiscordClient.ShutdownClient =>
      isShuttingDown = true
      handlers.values.foreach(_ ! ShutdownClient)
    case TerminatedGuild(guildId) =>
      handlers.remove(guildId)
      val level = if(isShuttingDown) Logging.DebugLevel else Logging.WarningLevel
      log.log(level, "Actor for guild {} shut down", guildId)

      if (isShuttingDown && handlers.isEmpty) {
        context.stop(self)
      }
  }

  def sendToGuild(guildId: GuildId, msg: Any): Unit = if (!isShuttingDown) getGuild(guildId) ! msg

  def sendToNotGuild(msg: Any): Unit = if (!isShuttingDown) notGuildHandler.foreach(_ ! msg)

  def getGuild(guildId: GuildId): ActorRef = {
    lazy val newActor = {
      log.debug("Creating new actor for guild {}", guildId)
      context.watchWith(context.actorOf(props(guildId), guildId.toString), TerminatedGuild(guildId))
    }
    handlers.getOrElseUpdate(guildId, newActor)
  }
}
object GuildRouter {
  def props(props: GuildId => Props, notGuildHandler: Option[ActorRef]): Props =
    Props(new GuildRouter(props, notGuildHandler))
  def props(props: Props, notGuildHandler: Option[ActorRef]): Props =
    Props(new GuildRouter(_ => props, notGuildHandler))

  /**
    * Send to the guild dispatcher to get the actor for that guild
    * @param guildId The guildId to get the actor for
    */
  case class GetGuildActor(guildId: GuildId)

  /**
    * Sent as a response to [[GetGuildActor]]
    * @param guildActor The actor for the specified guild
    */
  case class ResponseGetGuild(guildActor: ActorRef)

  private case class TerminatedGuild(guildId: GuildId)
}
