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

import ackcord.data.GuildId
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import org.slf4j.Logger

abstract class GuildRouter[Event, Inner](
    ctx: ActorContext[GuildRouter.Command[Event, Inner]],
    replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
    behavior: GuildId => Behavior[Inner],
    notGuildHandler: Option[ActorRef[Inner]],
    shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
) extends AbstractBehavior[GuildRouter.Command[Event, Inner]](ctx) {
  import GuildRouter._

  val log: Logger = context.log

  val handlers       = mutable.HashMap.empty[GuildId, ActorRef[Inner]]
  var isShuttingDown = false

  def handleThroughMessage(a: Event): Unit

  override def onMessage(msg: Command[Event, Inner]): Behavior[Command[Event, Inner]] = msg match {
    case EventMessage(a) =>
      handleThroughMessage(a)
      Behaviors.same
    case TerminatedGuild(guildId) =>
      handlers.remove(guildId)

      if (isShuttingDown && handlers.isEmpty) {
        Behaviors.stopped
      } else {
        Behaviors.same
      }

    case GetGuildActor(guildId, replyTo) =>
      replyTo ! GetGuildActorReply(getGuild(guildId))
      Behaviors.same
    case SendToGuildActor(guildId, msg) =>
      sendToGuild[Inner](guildId, msg, _ ! _)
      Behaviors.same
    case Broadcast(msg) =>
      sendToAll[Inner](msg, _ ! _)
      Behaviors.same
    case Shutdown =>
      isShuttingDown = true

      if (handlers.nonEmpty) {
        shutdownBehavior match {
          case OnShutdownSendMsg(msg)     => sendToAll[Inner](msg, _ ! _)
          case GuildRouter.OnShutdownStop => handlers.values.foreach(context.stop)
        }

        Behaviors.same
      } else {
        Behaviors.stopped
      }
  }

  def sendToGuild[A](guildId: GuildId, msg: A, handle: (ActorRef[Inner], A) => Unit): Unit =
    if (!isShuttingDown) handle(getGuild(guildId), msg)

  def sendToNotGuild[A](msg: A, handle: (ActorRef[Inner], A) => Unit): Unit =
    if (!isShuttingDown) notGuildHandler.foreach(handle(_, msg))

  def sendToAll[A](msg: A, handle: (ActorRef[Inner], A) => Unit): Unit = handlers.values.foreach(handle(_, msg))

  def getGuild(guildId: GuildId): ActorRef[Inner] = {
    lazy val newActor = {
      log.debug("Creating new actor for guild {}", guildId)
      val newActor = context.spawn(behavior(guildId), guildId.asString)
      context.watchWith(newActor, TerminatedGuild(guildId))
      replyTo.foreach(_ ! GuildActorCreated(newActor, guildId))
      newActor
    }
    handlers.getOrElseUpdate(guildId, newActor)
  }

  def stopHandler(guildId: GuildId): Unit = handlers.get(guildId).foreach { handler =>
    shutdownBehavior match {
      case GuildRouter.OnShutdownSendMsg(msg) => handler ! msg
      case GuildRouter.OnShutdownStop         => context.stop(handler)
    }
  }
}

object GuildRouter {

  def partitioner[Inner](
      replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
      behavior: GuildId => Behavior[Inner],
      notGuildHandler: Option[ActorRef[Inner]],
      shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
  ): Behavior[Command[Nothing, Inner]] = Behaviors.setup { ctx =>
    new GuildRouter[Nothing, Inner](ctx, replyTo, behavior, notGuildHandler, shutdownBehavior) {
      override def handleThroughMessage(a: Nothing): Unit = sys.error("impossible")
    }
  }

  sealed trait ShutdownBehavior[+Inner]
  case class OnShutdownSendMsg[Inner](msg: Inner) extends ShutdownBehavior[Inner]
  case object OnShutdownStop                      extends ShutdownBehavior[Nothing]

  sealed trait Command[+Event, +Inner]
  case class EventMessage[Event](a: Event) extends Command[Event, Nothing]
  case object Shutdown                     extends Command[Nothing, Nothing]

  private case class TerminatedGuild(guildId: GuildId) extends Command[Nothing, Nothing]

  /**
    * Send to the guild dispatcher to get the actor for that guild
    * @param guildId
    *   The guildId to get the actor for
    */
  case class GetGuildActor[Inner](guildId: GuildId, replyTo: ActorRef[GetGuildActorReply[Inner]])
      extends Command[Nothing, Inner]

  /**
    * Send a message to a guild actor that this router manages.
    * @param guildId
    *   The guildId of the actor to send to.
    * @param msg
    *   The message to send.
    */
  case class SendToGuildActor[Inner](guildId: GuildId, msg: Inner) extends Command[Nothing, Inner]

  /**
    * Sent as a response to [[GetGuildActor]]
    * @param guildActor
    *   The actor for the specified guild
    */
  case class GetGuildActorReply[Inner](guildActor: ActorRef[Inner])

  /** Send a command to all guild actors that currently exists. */
  case class Broadcast[Inner](msg: Inner) extends Command[Nothing, Inner]

  case class GuildActorCreated[Inner](actor: ActorRef[Inner], guildId: GuildId)
}
