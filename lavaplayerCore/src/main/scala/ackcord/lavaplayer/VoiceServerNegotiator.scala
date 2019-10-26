package ackcord.lavaplayer

import ackcord.data.{ChannelId, GuildId, UserId}
import ackcord.gateway.{GatewayMessage, VoiceStateUpdate, VoiceStateUpdateData}
import ackcord.{APIMessage, Cache}
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.typed.scaladsl.ActorSink

object VoiceServerNegotiator {

  def apply(
      guildId: GuildId,
      vChannelId: ChannelId,
      cache: Cache,
      replyTo: ActorRef[GotVoiceData]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val system: ActorSystem[Nothing] = ctx.system

    Source
      .single(
        VoiceStateUpdate(VoiceStateUpdateData(guildId, Some(vChannelId), selfMute = false, selfDeaf = false))
          .asInstanceOf[GatewayMessage[Any]]
      )
      .runWith(cache.gatewayPublish)

    val killSwitch = cache.subscribeAPI
      .collect {
        case state: APIMessage.VoiceStateUpdate   => state
        case server: APIMessage.VoiceServerUpdate => server
      }
      .viaMat(KillSwitches.single)(Keep.right)
      .to(
        ActorSink
          .actorRefWithBackpressure(ctx.self, ReceivedEvent, InitSink, AckSink, CompletedSink, _ => CompletedSink)
      )
      .run()

    running(guildId, None, None, replyTo, killSwitch)
  }

  private def running(
      guildId: GuildId,
      tokenEndpoint: Option[(String, String)],
      sessionId: Option[String],
      replyTo: ActorRef[VoiceServerNegotiator.GotVoiceData],
      killSwitch: UniqueKillSwitch
  ): Behavior[Command] = Behaviors.receiveMessage {
    case InitSink(ackTo) =>
      ackTo ! AckSink
      Behaviors.same
    case ReceivedEvent(ackTo, APIMessage.VoiceStateUpdate(state, c)) if state.userId == c.current.botUser.id =>
      ackTo ! AckSink

      tokenEndpoint match {
        case Some((token, endpoint)) =>
          replyTo ! GotVoiceData(state.sessionId, token, endpoint, c.current.botUser.id)
          killSwitch.shutdown()
          Behaviors.same

        case None => running(guildId, None, Some(state.sessionId), replyTo, killSwitch)
      }

    case ReceivedEvent(ackTo, APIMessage.VoiceServerUpdate(vToken, guild, endPoint, c)) if guild.id == guildId =>
      ackTo ! AckSink

      val usedEndpoint = if(endPoint.endsWith(":80")) endPoint.dropRight(3) else endPoint

      sessionId match {
        case Some(session) =>
          replyTo ! GotVoiceData(session, vToken, usedEndpoint, c.current.botUser.id)
          killSwitch.shutdown()
          Behaviors.same
        case None => running(guildId, Some((vToken, usedEndpoint)), None, replyTo, killSwitch)
      }

    case ReceivedEvent(ackTo, _) =>
      ackTo ! AckSink
      Behaviors.same
    case CompletedSink => Behaviors.stopped

    case Stop =>
      killSwitch.shutdown()
      Behaviors.same
  }

  private case object AckSink

  sealed trait Command
  case object Stop extends Command

  private case class InitSink(ackTo: ActorRef[AckSink.type])                           extends Command
  private case class ReceivedEvent(ackTo: ActorRef[AckSink.type], message: APIMessage) extends Command
  private case object CompletedSink                                                    extends Command

  case class GotVoiceData(sessionId: String, token: String, endpoint: String, userId: UserId)

}
