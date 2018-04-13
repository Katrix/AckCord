/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
package net.katsstuff.ackcord.lavaplayer

import scala.concurrent.{Future, Promise}

import com.sedmelluq.discord.lavaplayer.player.event._
import com.sedmelluq.discord.lavaplayer.player.{AudioLoadResultHandler, AudioPlayer, AudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.{AudioItem, AudioPlaylist, AudioTrack}

import akka.actor.{ActorLogging, ActorRef, FSM, PoisonPill, Props, Status}
import akka.stream.scaladsl.Source
import net.katsstuff.ackcord.lavaplayer.AudioSender.{StartSendAudio, StopSendAudio}
import net.katsstuff.ackcord.lavaplayer.LavaplayerHandler._
import net.katsstuff.ackcord.websocket.gateway.{GatewayMessage, VoiceStateUpdate, VoiceStateUpdateData}
import net.katsstuff.ackcord.websocket.voice.VoiceWsHandler
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.data._

/**
  * An actor to manage a music connection.
  * @param player The player to use. Is not destroyed when the actor shuts down.
  * @param useBursting If a bursting audio sender should be used. Recommended.
  */
class LavaplayerHandler(player: AudioPlayer, guildId: GuildId, cache: Cache, useBursting: Boolean = true)
    extends FSM[MusicState, StateData]
    with ActorLogging {
  import cache.mat

  cache.subscribeAPIActor(self, DiscordShard.StopShard)(
    classOf[APIMessage.Ready],
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

    case Event(ConnectVChannel(vChannelId, force), HasVoiceWs(voiceWs, inVChannelId, firstSender)) =>
      if (vChannelId != inVChannelId) {
        if (force) {
          voiceWs ! Logout
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

    case Event(AudioAPIMessage.Ready(udpHandler, _, _), HasVoiceWs(voiceWs, vChannelId, sendEventsTo)) =>
      log.debug("Audio ready")
      val audioSender =
        if (useBursting)
          context.actorOf(BurstingAudioSender.props(player, udpHandler, voiceWs), "BurstingDataSender")
        else
          context.actorOf(AudioSender.props(player, udpHandler, voiceWs), "DataSender")

      sendEventsTo ! MusicReady

      goto(Active) using CanSendAudio(voiceWs, audioSender, vChannelId)

    case Event(DiscordShard.StopShard, HasVoiceWs(voiceWs, _, _)) =>
      voiceWs ! Logout
      stop()

    case Event(DiscordShard.StopShard, _)                         => stop()
    case Event(AudioAPIMessage.UserSpeaking(_, _, _, _, _, _), _) => stay() //NO-OP
    case Event(APIMessage.VoiceStateUpdate(_, _), _)              => stay() //NO-OP
    case Event(APIMessage.VoiceServerUpdate(_, _, _, _), _)       => stay() //NO-OP
  }

  when(Active) {
    case Event(SetPlaying(speaking), CanSendAudio(_, dataSender, _)) =>
      dataSender ! (if (speaking) StartSendAudio else StopSendAudio)
      stay()

    case Event(DisconnectVChannel, CanSendAudio(voiceWs, dataSender, _)) =>
      dataSender ! StopSendAudio
      voiceWs ! Logout
      dataSender ! PoisonPill

      Source
        .single(
          VoiceStateUpdate(VoiceStateUpdateData(guildId, None, selfMute = false, selfDeaf = false))
            .asInstanceOf[GatewayMessage[Any]]
        )
        .runWith(cache.gatewayPublish)

      log.debug("Left voice channel")
      goto(Inactive) using Idle

    case Event(ConnectVChannel(vChannelId, force), CanSendAudio(voiceWs, dataSender, inVChannelId)) =>
      if (vChannelId != inVChannelId) {
        if (force) {
          log.debug("Moving to new vChannel")
          dataSender ! StopSendAudio
          voiceWs ! Logout
          dataSender ! PoisonPill

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

    case Event(DiscordShard.StopShard, CanSendAudio(voiceWs, dataSender, _)) =>
      voiceWs ! Logout
      dataSender ! StopSendAudio
      stop()

    case Event(APIMessage.VoiceStateUpdate(_, _), _)              => stay() //NO-OP
    case Event(APIMessage.VoiceServerUpdate(_, _, _, _), _)       => stay() //NO-OP
    case Event(AudioAPIMessage.UserSpeaking(_, _, _, _, _, _), _) => stay() //NO-OP
  }

  initialize()

  def connect(
      vChannelId: ChannelId,
      endPoint: String,
      userId: UserId,
      sessionId: String,
      token: String,
      sender: ActorRef
  ): State = {
    val voiceWs =
      context.actorOf(
        VoiceWsHandler.props(endPoint, RawSnowflake(guildId), userId, sessionId, token, Some(self), None),
        "VoiceWS"
      )
    voiceWs ! Login
    log.debug("Music Connected")
    stay using HasVoiceWs(voiceWs, vChannelId, sender)
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
  private case class HasVoiceWs(voiceWs: ActorRef, vChannelId: ChannelId, sender: ActorRef)        extends StateData
  private case class CanSendAudio(voiceWs: ActorRef, audioSender: ActorRef, vChannelId: ChannelId) extends StateData

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
    * Otherwise it fails with [[FriendlyException]].
    */
  def loadItem(playerManager: AudioPlayerManager, identifier: String): Future[AudioItem] = {
    val promise = Promise[AudioItem]

    playerManager.loadItem(identifier, new AudioLoadResultHandler {
      override def loadFailed(e: FriendlyException): Unit = promise.failure(e)

      override def playlistLoaded(playlist: AudioPlaylist): Unit = promise.success(playlist)

      override def noMatches(): Unit = promise.failure(new NoMatchException(identifier))

      override def trackLoaded(track: AudioTrack): Unit = promise.success(track)
    })

    promise.future
  }

  /**
    * An adapter between [[AudioEventListener]] and actors.
    * @param sendTo The actor to send the events to.
    */
  class AudioEventSender(sendTo: ActorRef) extends AudioEventListener {
    override def onEvent(event: AudioEvent): Unit = sendTo ! event
  }

  /**
    * An exception signaling that a [[AudioPlayerManager]] find a track.
    */
  class NoMatchException(val identifier: String) extends Exception(s"No match for identifier $identifier")

  class AlreadyConnectedException(inChannel: ChannelId)
      extends Exception("The client is already connected to a channel in this guild")

  class ForcedConnectedException(inChannel: ChannelId) extends Exception("Connection was forced to another channel")
}
