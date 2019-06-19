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
package ackcord.util

import java.util.UUID

import scala.collection.mutable

import ackcord.data.{ChannelId, GuildChannel, GuildId}
import ackcord.gateway.GatewayEvent
import ackcord.util.GuildRouter._
import ackcord.{APIMessage, DiscordShard}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.Logging
import akka.routing.Broadcast
import cats.Eval
import io.circe.Decoder

/**
  * Will send all [[APIMessage]]s with the same guild
  * to the same actor. Also obeys [[https://doc.akka.io/api/akka/current/akka/routing/Broadcast.htmlBroadcast]].
  *
  * Handles
  * - [[APIMessage.ChannelMessage]]
  * - [[APIMessage.GuildMessage]]
  * - [[APIMessage.MessageMessage]]
  * - [[APIMessage.VoiceStateUpdate]]
  * - [[ackcord.gateway.GatewayEvent.GuildEvent]]
  * - [[ackcord.gateway.GatewayEvent.ComplexGuildEvent]]
  * - [[ackcord.gateway.GatewayEvent.OptGuildEvent]]
  *
  * This actor has a small cache for figuring out what actor to send messages
  * to for the gateway channel events.
  *
  * Global events like [[APIMessage.Ready]], [[APIMessage.Resumed]] and
  * [[APIMessage.UserUpdate]] are send to all actors.
  *
  * It also respects [[DiscordShard.StopShard]].
  * It sends the shutdown to all it's children, and when all the children have
  * stopped, it stops itself. The child actors will not receive any further
  * events once a shutdown has been started.
  *
  * @param props The function to obtain a props used for constructing handlers
  * @param notGuildHandler For some messages that could be directed to a guild
  *                        but not always, if the message is not directed
  *                        towards a guild, it will me sent here instead.
  */
class GuildRouter(props: GuildId => Props, notGuildHandler: Option[ActorRef]) extends Actor with ActorLogging {
  val handlers       = mutable.HashMap.empty[GuildId, ActorRef]
  var channelToGuild = mutable.HashMap.empty[ChannelId, GuildId]
  val createMsgs     = mutable.HashMap.empty[UUID, Any]
  var isShuttingDown = false

  def handleLazy[A, B](later: Eval[Decoder.Result[A]])(f: A => B): Option[B] = {
    later.value match {
      case Right(value) => Some(f(value))
      case Left(e) =>
        log.error(e, "Failed to parse payload")
        None
    }
  }

  def handleLazyOpt[A, B](later: Eval[Decoder.Result[Option[A]]])(f: A => B): Option[B] = {
    later.value match {
      case Right(value) => value.map(f)
      case Left(e) =>
        log.error(e, "Failed to parse payload")
        None
    }
  }

  override def receive: Receive = {
    case msg: APIMessage.Ready =>
      msg.cache.current.unavailableGuildMap.keys.foreach(sendToGuild(_, msg))
    case msg @ (_: APIMessage.Resumed | _: APIMessage.UserUpdate) => sendToAll(msg)
    case msg: APIMessage.GuildMessage                             => sendToGuild(msg.guild.id, msg)
    case msg: APIMessage.ChannelMessage =>
      msg.channel match {
        case ch: GuildChannel => sendToGuild(ch.guildId, msg)
        case _                => sendToNotGuild(msg)
      }
    case msg: APIMessage.MessageMessage =>
      msg.message.channelId.resolve(msg.cache.current) match {
        case Some(guildChannel: GuildChannel) => sendToGuild(guildChannel.guildId, msg)
        case _                                => sendToNotGuild(msg)
      }
    case msg @ APIMessage.VoiceStateUpdate(state, _) =>
      state.guildId match {
        case Some(guildId) => sendToGuild(guildId, msg)
        case None          => sendToNotGuild(msg)
      }
    case msg: GatewayEvent.GuildCreate =>
      handleLazy(msg.guildId)(guildId => sendToGuild(guildId, msg))
      handleLazy(msg.data)(data => data.channels.foreach(channelToGuild ++= _.map(_.id -> data.id)))
    case msg: GatewayEvent.ChannelCreate =>
      handleLazyOpt(msg.guildId) { guildId =>
        sendToGuild(guildId, msg)
        handleLazy(msg.channelId)(channelId => channelToGuild.put(channelId, guildId))
      }
    case msg: GatewayEvent.ChannelDelete =>
      handleLazyOpt(msg.guildId)(sendToGuild(_, msg))
      handleLazy(msg.channelId)(channelToGuild.remove)
    case msg: GatewayEvent.GuildEvent[_]           => handleLazy(msg.guildId)(sendToGuild(_, msg))
    case msg: GatewayEvent.ComplexGuildEvent[_, _] => handleLazy(msg.guildId)(sendToGuild(_, msg))
    case msg: GatewayEvent.OptGuildEvent[_] =>
      handleLazy(msg.guildId) {
        case None          => sendToNotGuild(msg)
        case Some(guildId) => sendToGuild(guildId, msg)
      }
    case msg: GatewayEvent.ChannelEvent[_] =>
      handleLazy(msg.channelId) { channelId =>
        channelToGuild.get(channelId).fold(sendToNotGuild(msg))(sendToGuild(_, msg))
      }
    case GetGuildActor(guildId)         => if (!isShuttingDown) sender() ! ResponseGetGuild(getGuild(guildId))
    case SendToGuildActor(guildId, msg) => sendToGuild(guildId, msg)
    case Broadcast(msg)                 => sendToAll(msg)
    case DiscordShard.StopShard =>
      isShuttingDown = true
      sendToAll(DiscordShard.StopShard)
      if (handlers.isEmpty) context.stop(self)
    case TerminatedGuild(guildId) =>
      handlers.remove(guildId)
      val level = if (isShuttingDown) Logging.DebugLevel else Logging.WarningLevel
      log.log(level, "Actor for guild {} stopped", guildId)

      if (isShuttingDown && handlers.isEmpty) {
        context.stop(self)
      }
    case add @ AddCreateMsg(msg, sendToExisting) =>
      val uuid = UUID.randomUUID()
      createMsgs.put(uuid, msg)
      if (add.tieToLifecycle != ActorRef.noSender) {
        context.watchWith(add.tieToLifecycle, RemoveCreateMsg(uuid))
      }

      if (sendToExisting) {
        handlers.values.foreach(_ ! msg)
      }
    case RemoveCreateMsg(uuid) =>
      createMsgs.remove(uuid)
  }

  def sendToGuild(guildId: GuildId, msg: Any): Unit = if (!isShuttingDown) getGuild(guildId) ! msg

  def sendToNotGuild(msg: Any): Unit = if (!isShuttingDown) notGuildHandler.foreach(_ ! msg)

  def sendToAll(msg: Any): Unit = handlers.values.foreach(_ ! msg)

  def getGuild(guildId: GuildId): ActorRef = {
    lazy val newActor = {
      log.debug("Creating new actor for guild {}", guildId)
      val newActor = context.actorOf(props(guildId), guildId.asString)
      createMsgs.values.foreach(newActor ! _)
      context.watchWith(newActor, TerminatedGuild(guildId))
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
    * Send a message to a guild actor that this router manages.
    * @param guildId The guildId of the actor to send to.
    * @param msg The message to send.
    */
  case class SendToGuildActor(guildId: GuildId, msg: Any)

  /**
    * Sent as a response to [[GetGuildActor]]
    * @param guildActor The actor for the specified guild
    */
  case class ResponseGetGuild(guildActor: ActorRef)

  /**
    * Send this to a guild router to send a message to all
    * actors created from this point on by this router.
    * @param msg The message to send.
    * @param sendToExisting If this message should also be sent to the existing
    *                       actors in this GuildRouter.
    * @param tieToLifecycle An actor to bind this message to. When the specified
    *                       actor stops, the message is unregistered.
    */
  case class AddCreateMsg(msg: Any, sendToExisting: Boolean)(implicit val tieToLifecycle: ActorRef = ActorRef.noSender)

  private case class TerminatedGuild(guildId: GuildId)

  private case class RemoveCreateMsg(uuid: UUID)
}
