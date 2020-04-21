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

import scala.collection.mutable

import ackcord.data.{GuildChannel, GuildChannelId, GuildId}
import ackcord.gateway.{GatewayEvent, GatewayMessage}
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import cats.Eval
import io.circe.Decoder

class GatewayGuildRouter[Inner](
    ctx: ActorContext[GuildRouter.Command[GatewayMessage[_], Inner]],
    replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
    behavior: GuildId => Behavior[Inner],
    notGuildHandler: Option[ActorRef[Inner]],
    handleEvent: (ActorRef[Inner], GatewayMessage[_]) => Unit,
    shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
) extends GuildRouter[GatewayMessage[_], Inner](ctx, replyTo, behavior, notGuildHandler, shutdownBehavior) {

  val channelToGuild = mutable.HashMap.empty[GuildChannelId, GuildId]

  def handleLazy[A, B](later: Eval[Decoder.Result[A]])(f: A => B): Option[B] = {
    later.value match {
      case Right(value) => Some(f(value))
      case Left(e) =>
        log.error("Failed to parse payload", e)
        None
    }
  }

  def handleLazyOpt[A, B](later: Eval[Decoder.Result[Option[A]]])(f: A => B): Option[B] = {
    later.value match {
      case Right(value) => value.map(f)
      case Left(e) =>
        log.error("Failed to parse payload", e)
        None
    }
  }

  override def handleThroughMessage(a: GatewayMessage[_]): Unit = a match {
    case msg: GatewayEvent.GuildCreate =>
      handleLazy(msg.guildId)(guildId => sendToGuild(guildId, msg, handleEvent))
      handleLazy(msg.data)(data => data.channels.foreach(channelToGuild ++= _.map(_.id.asChannelId[GuildChannel] -> data.id)))
    case msg: GatewayEvent.ChannelCreate =>
      handleLazyOpt(msg.guildId) { guildId =>
        sendToGuild(guildId, msg, handleEvent)
        handleLazy(msg.channelId)(channelId => channelToGuild.put(channelId.asChannelId[GuildChannel], guildId))
      }
    case msg: GatewayEvent.ChannelDelete =>
      handleLazyOpt(msg.guildId)(sendToGuild(_, msg, handleEvent))
      handleLazy(msg.channelId)(id => channelToGuild.remove(id.asChannelId[GuildChannel]))

    case msg: GatewayEvent.GuildDelete =>
      handleLazy(msg.data) { guild =>
        if (!guild.unavailable) {
          stopHandler(guild.id)
        } else {
          sendToGuild(guild.id, msg, handleEvent)
        }
      }

    case msg: GatewayEvent.GuildEvent[_]           => handleLazy(msg.guildId)(sendToGuild(_, msg, handleEvent))
    case msg: GatewayEvent.ComplexGuildEvent[_, _] => handleLazy(msg.guildId)(sendToGuild(_, msg, handleEvent))
    case msg: GatewayEvent.OptGuildEvent[_] =>
      handleLazy(msg.guildId) {
        case None          => sendToNotGuild(msg, handleEvent)
        case Some(guildId) => sendToGuild(guildId, msg, handleEvent)
      }
    case msg: GatewayEvent.ChannelEvent[_] =>
      handleLazy(msg.channelId) { channelId =>
        channelToGuild.get(channelId.asChannelId[GuildChannel]).fold(sendToNotGuild(msg, handleEvent))(sendToGuild(_, msg, handleEvent))
      }
    case _ =>
  }
}
object GatewayGuildRouter {

  def router(
      replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[GatewayMessage[_]]]],
      behavior: GuildId => Behavior[GatewayMessage[_]],
      notGuildHandler: Option[ActorRef[GatewayMessage[_]]]
  ): Behavior[GuildRouter.Command[GatewayMessage[_], GatewayMessage[_]]] = Behaviors.setup { ctx =>
    new GatewayGuildRouter(ctx, replyTo, behavior, notGuildHandler, _ ! _, GuildRouter.OnShutdownStop)
  }

  def partitioner[Inner](
      replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
      behavior: GuildId => Behavior[Inner],
      notGuildHandler: Option[ActorRef[Inner]],
      shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
  ): Behavior[GuildRouter.Command[GatewayMessage[_], Inner]] = Behaviors.setup { ctx =>
    new GatewayGuildRouter(ctx, replyTo, behavior, notGuildHandler, (_, _) => (), shutdownBehavior)
  }
}
