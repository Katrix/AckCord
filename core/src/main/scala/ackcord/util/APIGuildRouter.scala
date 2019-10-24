package ackcord.util

import ackcord.APIMessage
import ackcord.data.{GuildChannel, GuildId}
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import org.slf4j.Logger

class APIGuildRouter(
    ctx: ActorContext[GuildRouter.Command[APIMessage]],
    replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[APIMessage]]],
    log: Logger,
    behavior: GuildId => Behavior[APIMessage],
    notGuildHandler: Option[ActorRef[APIMessage]]
) extends GuildRouter[APIMessage](ctx, replyTo, log, behavior, notGuildHandler) {
  override def handleThroughMessage(a: APIMessage): Unit = a match {
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
  }
}
object APIGuildRouter {

  def apply(
      replyTo: Option[ActorRef[GuildRouter.GuildActorCreated[APIMessage]]],
      behavior: GuildId => Behavior[APIMessage],
      notGuildHandler: Option[ActorRef[APIMessage]]
  ): Behavior[GuildRouter.Command[APIMessage]] = Behaviors.setup { ctx =>
    new APIGuildRouter(ctx, replyTo, ctx.log, behavior, notGuildHandler)
  }
}
