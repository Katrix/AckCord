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
import ackcord.lavaplayer.LavaplayerHandler._
import ackcord.util.Switch
import ackcord.voice.{AudioAPIMessage, VoiceUDPFlow, VoiceWsHandler}
import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, FSM, PoisonPill, Props, Status}
import akka.stream.{KillSwitches, SharedKillSwitch, SourceShape}
import akka.stream.scaladsl.{GraphDSL, Sink, Source}
import akka.util.ByteString

/**
  * An actor to manage a music connection.
  * @param player The player to use. Is not destroyed when the actor shuts down.
  * @param useBursting If a bursting audio sender should be used. Recommended.
  */
class LavaplayerHandler(player: AudioPlayer, guildId: GuildId, cache: Cache, useBursting: Boolean = true)
    extends FSM[MusicState, StateData]
    with ActorLogging {
  import context.system

  cache.subscribeAPIActor(self, DiscordShard.StopShard, Status.Failure)(
    classOf[APIMessage.VoiceServerUpdate],
    classOf[APIMessage.VoiceStateUpdate]
  )

  startWith(Inactive, Idle)

  when(Inactive) {
    case Event(ConnectVChannel(vChannelId, _), Idle) =>
      log.debug("Connecting to new voice channel")
      Source
        .single(
          VoiceStateUpdate(VoiceStateUpdateData(guildId, Some(vChannelId), selfMute = false, selfDeaf = false))
            .asInstanceOf[GatewayMessage[Any]]
        )
        .runWith(cache.gatewayPublish)
      stay using Connecting(None, None, None, vChannelId, sender())

    case Event(ConnectVChannel(vChannelId, force), Connecting(_, _, _, inVChannelId, firstSender)) =>
      if (vChannelId != inVChannelId) {
        if (force) {
          firstSender ! Status.Failure(new ForcedConnectedException(inVChannelId))
          stay using Connecting(None, None, None, vChannelId, sender())
        } else {
          sender() ! Status.Failure(new AlreadyConnectedException(inVChannelId))
          stay()
        }
      } else {
        //Ignored
        stay()
      }

    case Event(ConnectVChannel(vChannelId, force), HasVoiceWs(voiceWs, inVChannelId, firstSender, _, killSwitch)) =>
      if (vChannelId != inVChannelId) {
        if (force) {
          killSwitch.shutdown()

          voiceWs ! VoiceWsHandler.Logout
          firstSender ! Status.Failure(new ForcedConnectedException(inVChannelId))
          stay using Connecting(None, None, None, vChannelId, sender())
        } else {
          sender() ! Status.Failure(new AlreadyConnectedException(inVChannelId))
          stay()
        }
      } else {
        //Ignored
        stay()
      }

    case Event(APIMessage.VoiceStateUpdate(state, c), Connecting(Some(endPoint), _, Some(vToken), vChannelId, sender))
        if state.userId == c.current.botUser.id =>
      log.debug("Received session id")
      connect(vChannelId, endPoint, c.current.botUser.id, state.sessionId, vToken, sender)

    case Event(APIMessage.VoiceStateUpdate(state, c), con: Connecting)
        if state.userId == c.current.botUser.id => //We did not have an endPoint and token
      log.debug("Received session id")
      stay using con.copy(sessionId = Some(state.sessionId))

    case Event(
        APIMessage.VoiceServerUpdate(vToken, guild, endPoint, c),
        Connecting(_, Some(sessionId), _, vChannelId, sender)
        ) if guild.id == guildId =>
      val usedEndpoint = if (endPoint.endsWith(":80")) endPoint.dropRight(3) else endPoint
      log.debug("Got token and endpoint")
      connect(vChannelId, usedEndpoint, c.current.botUser.id, sessionId, vToken, sender)

    case Event(APIMessage.VoiceServerUpdate(vToken, guild, endPoint, _), con: Connecting)
        if guild.id == guildId => //We did not have a sessionId
      log.debug("Got token and endpoint")
      val usedEndpoint = if (endPoint.endsWith(":80")) endPoint.dropRight(3) else endPoint
      stay using con.copy(endPoint = Some(usedEndpoint), token = Some(vToken))

    case Event(
        AudioAPIMessage.Ready(_, _),
        HasVoiceWs(voiceWs, vChannelId, sendEventsTo, toggle, killSwitch)
        ) =>
      log.debug("Audio ready")

      sendEventsTo ! MusicReady

      goto(Active) using CanSendAudio(voiceWs, vChannelId, toggle, killSwitch)

    case Event(DiscordShard.StopShard, HasVoiceWs(voiceWs, _, _, _, killSwitch)) =>
      killSwitch.shutdown()
      context.watchWith(voiceWs, PoisonPill)
      voiceWs ! VoiceWsHandler.Logout
      stay()

    case Event(PoisonPill, _) =>
      stop()

    case Event(DiscordShard.StopShard, _)                         => stop()
    case Event(AudioAPIMessage.UserSpeaking(_, _, _, _, _, _), _) => stay() //NO-OP
    case Event(APIMessage.VoiceStateUpdate(_, _), _)              => stay() //NO-OP
    case Event(APIMessage.VoiceServerUpdate(_, _, _, _), _)       => stay() //NO-OP
  }

  when(Active) {
    case Event(SetPlaying(speaking), CanSendAudio(voiceWs, _, toggle, _)) =>
      toggle.set(speaking)
      voiceWs ! VoiceWsHandler.SetSpeaking(speaking)
      stay()

    case Event(DisconnectVChannel, CanSendAudio(voiceWs, _, _, killSwitch)) =>
      killSwitch.shutdown()
      voiceWs ! VoiceWsHandler.Logout

      Source
        .single(
          VoiceStateUpdate(VoiceStateUpdateData(guildId, None, selfMute = false, selfDeaf = false))
            .asInstanceOf[GatewayMessage[Any]]
        )
        .runWith(cache.gatewayPublish)

      log.debug("Left voice channel")
      goto(Inactive) using Idle

    case Event(ConnectVChannel(vChannelId, force), CanSendAudio(voiceWs, inVChannelId, _, killSwitch)) =>
      if (vChannelId != inVChannelId) {
        if (force) {
          log.debug("Moving to new vChannel")

          killSwitch.shutdown()
          voiceWs ! VoiceWsHandler.Logout

          Source
            .single(
              VoiceStateUpdate(VoiceStateUpdateData(guildId, Some(vChannelId), selfMute = false, selfDeaf = false))
                .asInstanceOf[GatewayMessage[Any]]
            )
            .runWith(cache.gatewayPublish)

          goto(Inactive) using Connecting(None, None, None, vChannelId, sender())
        } else {
          sender() ! Status.Failure(new AlreadyConnectedException(inVChannelId))
          stay()
        }
      } else {
        //Ignored
        stay()
      }

    case Event(DiscordShard.StopShard, CanSendAudio(voiceWs, _, _, killSwitch)) =>
      context.watchWith(voiceWs, PoisonPill)
      voiceWs ! VoiceWsHandler.Logout
      killSwitch.shutdown()
      goto(Inactive)

    case Event(APIMessage.VoiceStateUpdate(_, _), _)              => stay() //NO-OP
    case Event(APIMessage.VoiceServerUpdate(_, _, _, _), _)       => stay() //NO-OP
    case Event(AudioAPIMessage.UserSpeaking(_, _, _, _, _, _), _) => stay() //NO-OP
  }

  initialize()

  private def soundProducer(toggle: AtomicBoolean) = {
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

  def connect(
      vChannelId: ChannelId,
      endPoint: String,
      userId: UserId,
      sessionId: String,
      token: String,
      sender: ActorRef
  ): State = {
    val toggle                 = new AtomicBoolean(true)
    val (killSwitch, producer) = soundProducer(toggle)
    val voiceWs =
      context.actorOf(
        VoiceWsHandler.props(
          endPoint,
          RawSnowflake(guildId),
          userId,
          sessionId,
          token,
          Some(self),
          soundProducer = producer,
          Sink.ignore.mapMaterializedValue(_ => NotUsed)
        ),
        "VoiceWS"
      )
    voiceWs ! VoiceWsHandler.Login
    log.debug("Music Connected")
    stay using HasVoiceWs(voiceWs, vChannelId, sender, toggle, killSwitch)
  }
}
object LavaplayerHandler {
  def props(player: AudioPlayer, guildId: GuildId, cache: Cache, useBursting: Boolean = true): Props =
    Props(new LavaplayerHandler(player, guildId, cache, useBursting))

  sealed trait MusicState
  private case object Inactive extends MusicState
  private case object Active   extends MusicState

  sealed trait StateData
  private case object Idle extends StateData
  private case class Connecting(
      endPoint: Option[String],
      sessionId: Option[String],
      token: Option[String],
      vChannelId: ChannelId,
      sender: ActorRef
  ) extends StateData
  private case class HasVoiceWs(
      voiceWs: ActorRef,
      vChannelId: ChannelId,
      sender: ActorRef,
      toggle: AtomicBoolean,
      killSwitch: SharedKillSwitch
  ) extends StateData
  private case class CanSendAudio(
      voiceWs: ActorRef,
      vChannelId: ChannelId,
      toggle: AtomicBoolean,
      killSwitch: SharedKillSwitch
  ) extends StateData

  /**
    * Connect to a voice channel.
    * @param channelId The channel to connect to
    * @param force If it should connect even if it's already connecting, or is connected to another channel(move)
    */
  case class ConnectVChannel(channelId: ChannelId, force: Boolean = false)

  /**
    * Disconnect from a voice channel
    */
  case object DisconnectVChannel

  /**
    * Cancel the connection to a voice channel
    */
  case object CancelConnection

  /**
    * Sent as a response to [[ConnectVChannel]] when everything is ready.
    */
  case object MusicReady

  /**
    * Set if the bot should be playing(speaking) or not. This is required to send sound.
    */
  case class SetPlaying(speaking: Boolean)

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
  class AudioEventSender(sendTo: ActorRef) extends AudioEventListener {
    override def onEvent(event: AudioEvent): Unit = sendTo ! event
  }

  /**
    * An exception signaling that a [[com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager]] find a track.
    */
  class NoMatchException(val identifier: String) extends Exception(s"No match for identifier $identifier")

  class AlreadyConnectedException(inChannel: ChannelId)
      extends Exception("The client is already connected to a channel in this guild")

  class ForcedConnectedException(inChannel: ChannelId) extends Exception("Connection was forced to another channel")
}
