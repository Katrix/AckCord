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

import ackcord.APIMessage
import ackcord.data.{GuildChannel, GuildId}
import akka.actor.typed._
import akka.actor.typed.scaladsl._

class APIGuildRouter[Inner](
    ctx: ActorContext[GuildRouter.Command[APIMessage, Inner]],
    replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
    behavior: GuildId => Behavior[Inner],
    notGuildHandler: Option[ActorRef[Inner]],
    handleEvent: (ActorRef[Inner], APIMessage) => Unit,
    shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
) extends GuildRouter[APIMessage, Inner](ctx, replyTo, behavior, notGuildHandler, shutdownBehavior) {

  override def handleThroughMessage(a: APIMessage): Unit = a match {
    case msg: APIMessage.Ready =>
      msg.cache.current.unavailableGuildMap.keys.foreach(sendToGuild(_, msg, handleEvent))
    case msg @ (_: APIMessage.Resumed | _: APIMessage.UserUpdate) => sendToAll(msg, handleEvent)
    case APIMessage.GuildDelete(guild, false, _)                  => stopHandler(guild.id)
    case msg: APIMessage.GuildMessage                             => sendToGuild(msg.guild.id, msg, handleEvent)
    case msg: APIMessage.ChannelMessage =>
      msg.channel match {
        case ch: GuildChannel => sendToGuild(ch.guildId, msg, handleEvent)
        case _                => sendToNotGuild(msg, handleEvent)
      }
    case msg: APIMessage.MessageMessage =>
      msg.message.channelId.resolve(msg.cache.current) match {
        case Some(guildChannel: GuildChannel) => sendToGuild(guildChannel.guildId, msg, handleEvent)
        case _                                => sendToNotGuild(msg, handleEvent)
      }
    case msg @ APIMessage.VoiceStateUpdate(state, _) =>
      state.guildId match {
        case Some(guildId) => sendToGuild(guildId, msg, handleEvent)
        case None          => sendToNotGuild(msg, handleEvent)
      }
  }
}
object APIGuildRouter {

  def router(
      replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[APIMessage]]],
      behavior: GuildId => Behavior[APIMessage],
      notGuildHandler: Option[ActorRef[APIMessage]]
  ): Behavior[GuildRouter.Command[APIMessage, APIMessage]] = Behaviors.setup { ctx =>
    new APIGuildRouter(ctx, replyTo, behavior, notGuildHandler, _ ! _, GuildRouter.OnShutdownStop)
  }

  def partitioner[Inner](
      replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
      behavior: GuildId => Behavior[Inner],
      notGuildHandler: Option[ActorRef[Inner]],
      shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
  ): Behavior[GuildRouter.Command[APIMessage, Inner]] = Behaviors.setup { ctx =>
    new APIGuildRouter(ctx, replyTo, behavior, notGuildHandler, (_, _) => (), shutdownBehavior)
  }
}
