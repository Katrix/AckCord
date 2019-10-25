package ackcord.util

import ackcord.data.{ChannelId, GuildId}
import akka.actor.typed.scaladsl._
import akka.actor.typed._
import org.slf4j.Logger
import scala.collection.mutable

abstract private[util] class GuildRouter[Event, Inner](
    ctx: ActorContext[GuildRouter.Command[Event, Inner]],
    replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
    log: Logger,
    behavior: GuildId => Behavior[Inner],
    notGuildHandler: Option[ActorRef[Inner]],
    shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
) extends AbstractBehavior[GuildRouter.Command[Event, Inner]](ctx) {
  import GuildRouter._

  val handlers       = mutable.HashMap.empty[GuildId, ActorRef[Inner]]
  val channelToGuild = mutable.HashMap.empty[ChannelId, GuildId]
  var isShuttingDown = false

  def handleThroughMessage(a: Event): Unit

  override def onMessage(msg: Command[Event, Inner]): Behavior[Command[Event, Inner]] = msg match {
    case EventMessage(a) =>
      handleThroughMessage(a)
      Behaviors.same
    case TerminatedGuild(guildId) =>
      handlers.remove(guildId)

      if (isShuttingDown) {
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

      shutdownBehavior match {
        case OnShutdownSendMsg(msg)     => sendToAll[Inner](msg, _ ! _)
        case GuildRouter.OnShutdownStop => handlers.values.foreach(context.stop)
      }

      Behaviors.same
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
}

object GuildRouter {

  sealed trait ShutdownBehavior[+Inner]
  case class OnShutdownSendMsg[Inner](msg: Inner) extends ShutdownBehavior[Inner]
  case object OnShutdownStop                      extends ShutdownBehavior[Nothing]

  sealed trait Command[+Event, +Inner]
  case class EventMessage[Event](a: Event) extends Command[Event, Nothing]
  case object Shutdown                     extends Command[Nothing, Nothing]

  private case class TerminatedGuild(guildId: GuildId) extends Command[Nothing, Nothing]

  /**
    * Send to the guild dispatcher to get the actor for that guild
    * @param guildId The guildId to get the actor for
    */
  case class GetGuildActor[Inner](guildId: GuildId, replyTo: ActorRef[GetGuildActorReply[Inner]])
      extends Command[Nothing, Inner]

  /**
    * Send a message to a guild actor that this router manages.
    * @param guildId The guildId of the actor to send to.
    * @param msg The message to send.
    */
  case class SendToGuildActor[Inner](guildId: GuildId, msg: Inner) extends Command[Nothing, Inner]

  /**
    * Sent as a response to [[GetGuildActor]]
    * @param guildActor The actor for the specified guild
    */
  case class GetGuildActorReply[Inner](guildActor: ActorRef[Inner])

  /**
    * Send a command to all guild actors that currently exists.
    */
  case class Broadcast[Inner](msg: Inner) extends Command[Nothing, Inner]

  case class GuildActorCreated[Inner](actor: ActorRef[Inner], guildId: GuildId)
}
