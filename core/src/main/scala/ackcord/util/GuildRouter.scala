package ackcord.util

import ackcord.data.{ChannelId, GuildId}
import akka.actor.typed.scaladsl._
import akka.actor.typed._
import org.slf4j.Logger
import scala.collection.mutable

abstract private[util] class GuildRouter[A](
    ctx: ActorContext[GuildRouter.Command[A]],
    replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[A]]],
    log: Logger,
    behavior: GuildId => Behavior[A],
    notGuildHandler: Option[ActorRef[A]]
) extends AbstractBehavior[GuildRouter.Command[A]](ctx) {
  import GuildRouter._

  val handlers       = mutable.HashMap.empty[GuildId, ActorRef[A]]
  val channelToGuild = mutable.HashMap.empty[ChannelId, GuildId]
  var isShuttingDown = false

  def handleThroughMessage(a: A): Unit

  override def onMessage(msg: Command[A]): Behavior[Command[A]] = {
    msg match {
      case ThroughMessage(a)               => handleThroughMessage(a)
      case TerminatedGuild(guildId)        => handlers.remove(guildId)
      case GetGuildActor(guildId, replyTo) => replyTo ! GetGuildActorReply(handlers.get(guildId))
      case SendToGuildActor(guildId, msg)  => handlers.get(guildId).foreach(_ ! msg)
    }
    Behaviors.same
  }

  def sendToGuild(guildId: GuildId, msg: A): Unit = if (!isShuttingDown) getGuild(guildId) ! msg

  def sendToNotGuild(msg: A): Unit = if (!isShuttingDown) notGuildHandler.foreach(_ ! msg)

  def sendToAll(msg: A): Unit = handlers.values.foreach(_ ! msg)

  def getGuild(guildId: GuildId): ActorRef[A] = {
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

  sealed trait Command[+A]
  case class ThroughMessage[A](a: A) extends Command[A]

  private case class TerminatedGuild(guildId: GuildId) extends Command[Nothing]

  /**
    * Send to the guild dispatcher to get the actor for that guild
    * @param guildId The guildId to get the actor for
    */
  case class GetGuildActor[A](guildId: GuildId, replyTo: ActorRef[GetGuildActorReply[A]]) extends Command[A]

  /**
    * Send a message to a guild actor that this router manages.
    * @param guildId The guildId of the actor to send to.
    * @param msg The message to send.
    */
  case class SendToGuildActor[A](guildId: GuildId, msg: A) extends Command[A]

  /**
    * Sent as a response to [[GetGuildActor]]
    * @param guildActor The actor for the specified guild
    */
  case class GetGuildActorReply[A](guildActor: Option[ActorRef[A]])

  /**
    * Send a command to all guild actors that currently exists.
    */
  case class Broadcast[A](msg: A) extends Command[A]

  case class GuildActorCreated[A](actor: ActorRef[A], guildId: GuildId)
}
