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
package ackcord.lavaplayer

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

import com.sedmelluq.discord.lavaplayer.player.event._
import com.sedmelluq.discord.lavaplayer.player.{AudioLoadResultHandler, AudioPlayer, AudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.{AudioItem, AudioPlaylist, AudioTrack}
import ackcord._
import ackcord.data._
import ackcord.gateway.{GatewayMessage, VoiceStateUpdate, VoiceStateUpdateData}
import ackcord.util.Switch
import ackcord.voice.{AudioAPIMessage, VoiceUDPFlow, VoiceWsHandler}
import akka.NotUsed
import akka.actor.typed.scaladsl._
import akka.actor.typed._
import akka.stream.{KillSwitches, SharedKillSwitch, SourceShape}
import akka.stream.scaladsl.{GraphDSL, Sink, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.util.ByteString
import org.slf4j.Logger

object LavaplayerHandler {
  sealed trait InactiveState
  private case object Idle extends InactiveState

  private case class Connecting(
      endPoint: Option[String],
      sessionId: Option[String],
      token: Option[String],
      vChannelId: ChannelId,
      sender: ActorRef[Reply]
  ) extends InactiveState

  private case class HasVoiceWs(
      voiceWs: ActorRef[VoiceWsHandler.Command],
      vChannelId: ChannelId,
      sender: ActorRef[Reply],
      toggle: AtomicBoolean,
      killSwitch: SharedKillSwitch
  ) extends InactiveState

  private case class CanSendAudio(
      voiceWs: ActorRef[VoiceWsHandler.Command],
      inVChannelId: ChannelId,
      toggle: AtomicBoolean,
      killSwitch: SharedKillSwitch
  )

  case class Parameters(
      player: AudioPlayer,
      guildId: GuildId,
      cache: Cache,
      useBursting: Boolean = true,
      context: ActorContext[Command],
      log: Logger
  )

  def apply(player: AudioPlayer, guildId: GuildId, cache: Cache, useBursting: Boolean = true): Behavior[Command] =
    Behaviors.setup { context =>
      val log                                   = context.log
      implicit val system: ActorSystem[Nothing] = context.system

      cache.subscribeAPI
        .collect {
          case msg: APIMessage.VoiceServerUpdate => APIServerEvent(msg)
          case msg: APIMessage.VoiceStateUpdate  => APIStateEvent(msg)
        }
        .runWith(ActorSink.actorRef(context.self, Shutdown, SendException))

      inactive(Parameters(player, guildId, cache, useBursting, context, log), Idle)
    }

  private def soundProducer(toggle: AtomicBoolean, player: AudioPlayer) = {
    val killSwitch = KillSwitches.shared("StopMusic")

    //FIXME: Don't think the switch is working completely properly. Could probably save some cycles if we fixed it
    val source = Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val switch  = b.add(new Switch[ByteString](toggle, List.fill(5)(VoiceUDPFlow.silence), Nil))
      val silence = b.add(Source.maybe[ByteString])
      val music   = b.add(LavaplayerSource.source(player).throttle(1, 20.millis).via(killSwitch.flow))

      music ~> switch.in0
      silence ~> switch.in1

      SourceShape(switch.out)
    })

    (killSwitch, source)
  }

  private def readyListener(replyTo: ActorRef[WsReady.type]): Behavior[AudioAPIMessage] = Behaviors.receiveMessage {
    case _: AudioAPIMessage.Ready =>
      replyTo ! WsReady
      Behaviors.same
    case _: AudioAPIMessage.UserSpeaking => Behaviors.same
    case _: AudioAPIMessage.ReceivedData => Behaviors.same
  }

  def inactive(parameters: Parameters, state: InactiveState): Behavior[Command] = Behaviors.receiveMessage { msg =>
    import parameters._
    implicit val system: ActorSystem[Nothing] = context.system

    def connect(
        vChannelId: ChannelId,
        endPoint: String,
        userId: UserId,
        sessionId: String,
        token: String,
        sender: ActorRef[Reply]
    ): Behavior[Command] = {
      val toggle                 = new AtomicBoolean(true)
      val (killSwitch, producer) = soundProducer(toggle, player)

      val readyListenerActor = context.spawn(readyListener(context.self), "ReadyListener")

      val voiceWs =
        context.spawn(
          VoiceWsHandler(
            endPoint,
            RawSnowflake(guildId),
            userId,
            sessionId,
            token,
            Some(readyListenerActor),
            soundProducer = producer,
            Sink.ignore.mapMaterializedValue(_ => NotUsed)
          ),
          "VoiceWS"
        )
      voiceWs ! VoiceWsHandler.Login
      log.debug("Music Connected")
      inactive(parameters, HasVoiceWs(voiceWs, vChannelId, sender, toggle, killSwitch))
    }

    (msg, state) match {
      case (ConnectVChannel(vChannelId, _, replyTo), Idle) =>
        log.debug("Connecting to new voice channel")
        Source
          .single(
            VoiceStateUpdate(VoiceStateUpdateData(guildId, Some(vChannelId), selfMute = false, selfDeaf = false))
              .asInstanceOf[GatewayMessage[Any]]
          )
          .runWith(cache.gatewayPublish)
        inactive(parameters, Connecting(None, None, None, vChannelId, replyTo))

      case (ConnectVChannel(newVChannelId, force, replyTo), Connecting(_, _, _, inVChannelId, firstSender)) =>
        if (newVChannelId != inVChannelId) {
          if (force) {
            firstSender ! ForcedConnectionFailure(inVChannelId, newVChannelId)
            inactive(parameters, Connecting(None, None, None, newVChannelId, replyTo))
          } else {
            replyTo ! AlreadyConnectedFailure(inVChannelId, newVChannelId)
            Behaviors.same
          }
        } else {
          //Ignored
          Behaviors.same
        }

      case (
          ConnectVChannel(newVChannelId, force, replyTo),
          HasVoiceWs(voiceWs, inVChannelId, firstSender, _, killSwitch)
          ) =>
        if (newVChannelId != inVChannelId) {
          if (force) {
            killSwitch.shutdown()
            voiceWs ! VoiceWsHandler.Logout

            firstSender ! ForcedConnectionFailure(inVChannelId, newVChannelId)
            inactive(parameters, Connecting(None, None, None, newVChannelId, replyTo))
          } else {
            replyTo ! AlreadyConnectedFailure(inVChannelId, newVChannelId)
            Behaviors.same
          }
        } else {
          //Ignored
          Behaviors.same
        }

      case (
          APIStateEvent(APIMessage.VoiceStateUpdate(state, c)),
          Connecting(Some(endPoint), _, Some(vToken), vChannelId, sender)
          ) if state.userId == c.current.botUser.id =>
        log.debug("Received session id")
        connect(vChannelId, endPoint, c.current.botUser.id, state.sessionId, vToken, sender)

      case (APIStateEvent(APIMessage.VoiceStateUpdate(state, c)), con: Connecting)
          if state.userId == c.current.botUser.id => //We did not have an endPoint and token
        log.debug("Received session id")
        inactive(parameters, con.copy(sessionId = Some(state.sessionId)))

      case (
          APIServerEvent(APIMessage.VoiceServerUpdate(vToken, guild, endPoint, c)),
          Connecting(_, Some(sessionId), _, vChannelId, sender)
          ) if guild.id == guildId =>
        val usedEndpoint = if (endPoint.endsWith(":80")) endPoint.dropRight(3) else endPoint
        log.debug("Got token and endpoint")
        connect(vChannelId, usedEndpoint, c.current.botUser.id, sessionId, vToken, sender)

      case (APIServerEvent(APIMessage.VoiceServerUpdate(vToken, guild, endPoint, _)), con: Connecting)
          if guild.id == guildId => //We did not have a sessionId
        log.debug("Got token and endpoint")
        val usedEndpoint = if (endPoint.endsWith(":80")) endPoint.dropRight(3) else endPoint
        inactive(parameters, con.copy(endPoint = Some(usedEndpoint), token = Some(vToken)))

      case (
          WsReady,
          HasVoiceWs(voiceWs, vChannelId, sendEventsTo, toggle, killSwitch)
          ) =>
        log.debug("Audio ready")

        sendEventsTo ! MusicReady

        active(parameters, CanSendAudio(voiceWs, vChannelId, toggle, killSwitch))

      case (Shutdown, HasVoiceWs(voiceWs, _, _, _, killSwitch)) =>
        killSwitch.shutdown()
        context.watchWith(voiceWs, StopNow)
        voiceWs ! VoiceWsHandler.Logout
        Behaviors.same

      case (StopNow, _) =>
        Behaviors.stopped

      case (Shutdown, _) => Behaviors.stopped
    }
  }

  def active(parameters: LavaplayerHandler.Parameters, state: CanSendAudio): Behavior[Command] = {
    import parameters._
    import state._
    implicit val system: ActorSystem[Nothing] = context.system

    Behaviors.receiveMessage {
      case SetPlaying(speaking) =>
        toggle.set(speaking)
        voiceWs ! VoiceWsHandler.SetSpeaking(speaking)
        Behaviors.same

      case DisconnectVChannel =>
        killSwitch.shutdown()
        voiceWs ! VoiceWsHandler.Logout

        Source
          .single(
            VoiceStateUpdate(VoiceStateUpdateData(guildId, None, selfMute = false, selfDeaf = false))
              .asInstanceOf[GatewayMessage[Any]]
          )
          .runWith(cache.gatewayPublish)

        log.debug("Left voice channel")
        inactive(parameters, Idle)

      case ConnectVChannel(newVChannelId, force, replyTo) =>
        if (newVChannelId != inVChannelId) {
          if (force) {
            log.debug("Moving to new vChannel")

            killSwitch.shutdown()
            voiceWs ! VoiceWsHandler.Logout

            Source
              .single(
                VoiceStateUpdate(VoiceStateUpdateData(guildId, Some(newVChannelId), selfMute = false, selfDeaf = false))
                  .asInstanceOf[GatewayMessage[Any]]
              )
              .runWith(cache.gatewayPublish)

            inactive(parameters, Connecting(None, None, None, newVChannelId, replyTo))
          } else {
            replyTo ! AlreadyConnectedFailure(inVChannelId, newVChannelId)
            Behaviors.same
          }
        } else {
          //Ignored
          Behaviors.same
        }

      case Shutdown =>
        context.watchWith(voiceWs, StopNow)
        voiceWs ! VoiceWsHandler.Logout
        killSwitch.shutdown()
        inactive(parameters, Idle)

      case StopNow => Behaviors.stopped

      case APIStateEvent(_)  => Behaviors.same //NO-OP
      case APIServerEvent(_) => Behaviors.same //NO-OP
      case WsReady           => Behaviors.same //NO-OP
      case SendException(e)  => throw e
    }

  }

  sealed trait Command
  sealed trait Reply

  /**
    * Connect to a voice channel.
    * @param channelId The channel to connect to
    * @param force If it should connect even if it's already connecting, or is connected to another channel(move)
    */
  case class ConnectVChannel(channelId: ChannelId, force: Boolean = false, replyTo: ActorRef[Reply]) extends Command

  /**
    * Disconnect from a voice channel
    */
  case object DisconnectVChannel extends Command

  /**
    * Sent as a response to [[ConnectVChannel]] when everything is ready.
    */
  case object MusicReady extends Reply

  /**
    * Sent as a response to [[ConnectVChannel]] if the client is already
    * connected to a different voice channel in this guild.
    * @param connectedVChannelId The currently connected voice channel
    * @param triedVChannelId The channel that was tried and failed
    */
  case class AlreadyConnectedFailure(connectedVChannelId: ChannelId, triedVChannelId: ChannelId) extends Reply

  /**
    * Sent if a connection initially succeeded, but is forced away by
    * something else.
    * @param oldVChannelId The old voice channel id before the switch
    * @param newVChannelId The new voice channel id after the switch
    */
  case class ForcedConnectionFailure(oldVChannelId: ChannelId, newVChannelId: ChannelId) extends Reply

  /**
    * Set if the bot should be playing(speaking) or not. This is required to send sound.
    */
  case class SetPlaying(speaking: Boolean) extends Command

  /**
    * Stops this lavaplyer handler gracefully, and logs out of the voice gateway if connected.
    */
  case object Shutdown extends Command

  private case object StopNow                                          extends Command
  private case object WsReady                                          extends Command
  private case class APIServerEvent(msg: APIMessage.VoiceServerUpdate) extends Command
  private case class APIStateEvent(msg: APIMessage.VoiceStateUpdate)   extends Command
  private case class SendException(e: Throwable)                       extends Command

  /**
    * Tries to load an item given an identifier and returns it as a future.
    * If there were no matches, the future fails with [[NoMatchException]].
    * Otherwise it fails with [[com.sedmelluq.discord.lavaplayer.tools.FriendlyException]].
    */
  def loadItem(playerManager: AudioPlayerManager, identifier: String): Future[AudioItem] = {
    val promise = Promise[AudioItem]

    playerManager.loadItem(
      identifier,
      new AudioLoadResultHandler {
        override def loadFailed(e: FriendlyException): Unit = promise.failure(e)

        override def playlistLoaded(playlist: AudioPlaylist): Unit = promise.success(playlist)

        override def noMatches(): Unit = promise.failure(new NoMatchException(identifier))

        override def trackLoaded(track: AudioTrack): Unit = promise.success(track)
      }
    )

    promise.future
  }

  /**
    * An adapter between [[com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener]] and actors.
    * @param sendTo The actor to send the events to.
    */
  class AudioEventSender[A](sendTo: ActorRef[A], wrap: AudioEvent => A) extends AudioEventListener {
    override def onEvent(event: AudioEvent): Unit = sendTo ! wrap(event)
  }

  /**
    * An exception signaling that a [[com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager]] find a track.
    */
  class NoMatchException(val identifier: String) extends Exception(s"No match for identifier $identifier")

  class ForcedConnectedException(inChannel: ChannelId) extends Exception("Connection was forced to another channel")
}
