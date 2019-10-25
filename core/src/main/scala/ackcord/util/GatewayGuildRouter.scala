package ackcord.util

import ackcord.data.GuildId
import ackcord.gateway.{GatewayEvent, GatewayMessage}
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import cats.Eval
import io.circe.Decoder
import org.slf4j.Logger

class GatewayGuildRouter[Inner](
    ctx: ActorContext[GuildRouter.Command[GatewayMessage[_], Inner]],
    replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
    log: Logger,
    behavior: GuildId => Behavior[Inner],
    notGuildHandler: Option[ActorRef[Inner]],
    handleEvent: (ActorRef[Inner], GatewayMessage[_]) => Unit,
    shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
) extends GuildRouter[GatewayMessage[_], Inner](ctx, replyTo, log, behavior, notGuildHandler, shutdownBehavior) {

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
      handleLazy(msg.data)(data => data.channels.foreach(channelToGuild ++= _.map(_.id -> data.id)))
    case msg: GatewayEvent.ChannelCreate =>
      handleLazyOpt(msg.guildId) { guildId =>
        sendToGuild(guildId, msg, handleEvent)
        handleLazy(msg.channelId)(channelId => channelToGuild.put(channelId, guildId))
      }
    case msg: GatewayEvent.ChannelDelete =>
      handleLazyOpt(msg.guildId)(sendToGuild(_, msg, handleEvent))
      handleLazy(msg.channelId)(channelToGuild.remove)
    case msg: GatewayEvent.GuildEvent[_]           => handleLazy(msg.guildId)(sendToGuild(_, msg, handleEvent))
    case msg: GatewayEvent.ComplexGuildEvent[_, _] => handleLazy(msg.guildId)(sendToGuild(_, msg, handleEvent))
    case msg: GatewayEvent.OptGuildEvent[_] =>
      handleLazy(msg.guildId) {
        case None          => sendToNotGuild(msg, handleEvent)
        case Some(guildId) => sendToGuild(guildId, msg, handleEvent)
      }
    case msg: GatewayEvent.ChannelEvent[_] =>
      handleLazy(msg.channelId) { channelId =>
        channelToGuild.get(channelId).fold(sendToNotGuild(msg, handleEvent))(sendToGuild(_, msg, handleEvent))
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
    new GatewayGuildRouter(ctx, replyTo, ctx.log, behavior, notGuildHandler, _ ! _, GuildRouter.OnShutdownStop)
  }

  def partitioner[Inner](
      replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
      behavior: GuildId => Behavior[Inner],
      notGuildHandler: Option[ActorRef[Inner]],
      shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
  ): Behavior[GuildRouter.Command[GatewayMessage[_], Inner]] = Behaviors.setup { ctx =>
    new GatewayGuildRouter(ctx, replyTo, ctx.log, behavior, notGuildHandler, (_, _) => (), shutdownBehavior)
  }
}
