package ackcord.util

import ackcord.APIMessage
import ackcord.data.{GuildChannel, GuildId}
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import org.slf4j.Logger

class APIGuildRouter[Inner](
    ctx: ActorContext[GuildRouter.Command[APIMessage, Inner]],
    replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
    log: Logger,
    behavior: GuildId => Behavior[Inner],
    notGuildHandler: Option[ActorRef[Inner]],
    handleEvent: (ActorRef[Inner], APIMessage) => Unit,
    shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
) extends GuildRouter[APIMessage, Inner](ctx, replyTo, log, behavior, notGuildHandler, shutdownBehavior) {

  override def handleThroughMessage(a: APIMessage): Unit = a match {
    case msg: APIMessage.Ready =>
      msg.cache.current.unavailableGuildMap.keys.foreach(sendToGuild(_, msg, handleEvent))
    case msg @ (_: APIMessage.Resumed | _: APIMessage.UserUpdate) => sendToAll(msg, handleEvent)
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
    new APIGuildRouter(ctx, replyTo, ctx.log, behavior, notGuildHandler, _ ! _, GuildRouter.OnShutdownStop)
  }

  def partitioner[Inner](
      replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[Inner]]],
      behavior: GuildId => Behavior[Inner],
      notGuildHandler: Option[ActorRef[Inner]],
      shutdownBehavior: GuildRouter.ShutdownBehavior[Inner]
  ): Behavior[GuildRouter.Command[APIMessage, Inner]] = Behaviors.setup { ctx =>
    new APIGuildRouter(ctx, replyTo, ctx.log, behavior, notGuildHandler, (_, _) => (), shutdownBehavior)
  }
}
