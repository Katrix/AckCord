/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord.example.music

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{Future, Promise}

import com.sedmelluq.discord.lavaplayer.player._
import com.sedmelluq.discord.lavaplayer.player.event._
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.{AudioItem, AudioPlaylist, AudioTrack, AudioTrackEndReason}

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import akka.pattern.pipe
import akka.stream.Materializer
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.commands.CmdRouter
import net.katsstuff.ackcord.data.{ChannelId, GuildId, RawSnowflake, TChannel, UserId}
import net.katsstuff.ackcord.example.music.DataSender.{StartSendAudio, StopSendAudio}
import net.katsstuff.ackcord.example.{ExampleErrorHandler, ExampleHelpCmdFactory, ExampleMain}
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler.{Login, Logout}
import net.katsstuff.ackcord.http.websocket.gateway.{VoiceStateUpdate, VoiceStateUpdateData}
import net.katsstuff.ackcord.http.websocket.voice.VoiceWsHandler
import net.katsstuff.ackcord.http.websocket.voice.VoiceWsHandler.SetSpeaking
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.{APIMessage, AudioAPIMessage, DiscordClient}

class MusicHandler(client: ClientActor, guildId: GuildId)(implicit mat: Materializer)
    extends FSM[MusicHandler.MusicState, MusicHandler.StateData]
    with ActorLogging {
  import MusicHandler._
  import context.dispatcher

  val commands =
    Seq(QueueCmdFactory(self), StopCmdFactory(self), NextCmdFactory(self), PauseCmdFactory(self))

  val commandRouter: ActorRef = context.actorOf(
    CmdRouter
      .props(
        client,
        needMention = true,
        ExampleErrorHandler.props(client, ExampleMain.allCommandNames),
        Some(ExampleHelpCmdFactory(ExampleMain.allCommandNames))
      ),
    "MusicHandlerCommands"
  )

  commands.foreach(fac => commandRouter ! CmdRouter.RegisterCmd(fac))

  val queue: mutable.Queue[AudioTrack] = mutable.Queue.empty[AudioTrack]

  val player: AudioPlayer = MusicHandler.playerManager.createPlayer()
  player.addListener(new AudioEventSender(self))

  var lastTChannel: TChannel = _

  onTermination {
    case StopEvent(_, _, _) if player != null => player.destroy()
  }

  startWith(Inactive, Connecting(None, None, None, None))

  when(Inactive) {
    case Event(DiscordClient.ShutdownClient, HasVoiceWs(voiceWs, _)) =>
      voiceWs ! Logout
      stop()
    case Event(DiscordClient.ShutdownClient, _) =>
      stop()
    case Event(msg: APIMessage.MessageCreate, _) =>
      commandRouter.forward(msg)
      stay()
    case Event(APIMessage.VoiceStateUpdate(state, c), Connecting(Some(endPoint), _, Some(token), Some(vChannelId)))
        if state.userId == c.current.botUser.id =>
      log.info("Received session id")
      connect(vChannelId, endPoint, c.current.botUser.id, state.sessionId, token)
    case Event(APIMessage.VoiceStateUpdate(state, c), con: Connecting)
        if state.userId == c.current.botUser.id => //We did not have an endPoint and token
      log.info("Received session id")
      stay using con.copy(sessionId = Some(state.sessionId))
    case Event(
        APIMessage.VoiceServerUpdate(token, guild, endPoint, c),
        Connecting(_, Some(sessionId), _, Some(vChannelId))
        ) if guild.id == guildId =>
      val usedEndpoint = if (endPoint.endsWith(":80")) endPoint.dropRight(3) else endPoint
      log.info("Got token and endpoint")
      connect(vChannelId, usedEndpoint, c.current.botUser.id, sessionId, token)
    case Event(APIMessage.VoiceServerUpdate(token, guild, endPoint, _), con: Connecting)
        if guild.id == guildId => //We did not have a sessionId
      log.info("Got token and endpoint")
      val usedEndpoint = if (endPoint.endsWith(":80")) endPoint.dropRight(3) else endPoint
      stay using con.copy(endPoint = Some(usedEndpoint), token = Some(token))
    case Event(AudioAPIMessage.Ready(udpHandler, _, _), HasVoiceWs(voiceWs, vChannelId)) =>
      log.info("Audio ready")
      voiceWs ! SetSpeaking(true)
      val dataSender =
        if (MusicHandler.UseBurstingSender)
          context.actorOf(BurstingDataSender.props(player, udpHandler, voiceWs), "BurstingDataSender")
        else context.actorOf(DataSender.props(player, udpHandler, voiceWs), "DataSender")
      nextTrack(dataSender)
      goto(Active) using CanSendAudio(voiceWs, dataSender, vChannelId)
    case Event(QueueUrl(url, tChannel, vChannelId), con @ Connecting(_, _, _, None)) =>
      client ! VoiceStateUpdate(VoiceStateUpdateData(guildId, Some(vChannelId), selfMute = false, selfDeaf = false))
      log.info("Received queue item in Inactive")
      lastTChannel = tChannel
      MusicHandler.loadItem(url).pipeTo(self)
      stay using con.copy(vChannelId = Some(vChannelId))
    case Event(QueueUrl(url, tChannel, vChannelId), Connecting(_, _, _, Some(inVChannelId))) =>
      if (vChannelId == inVChannelId) MusicHandler.loadItem(url).pipeTo(self)
      else tChannel.sendMessage("Currently joining different channel")
      stay()
      stay()
    case Event(QueueUrl(url, tChannel, vChannelId), HasVoiceWs(_, inVChannelId)) =>
      if (vChannelId == inVChannelId) MusicHandler.loadItem(url).pipeTo(self)
      else tChannel.sendMessage("Currently joining different channel")
      stay()
    case Event(e: MusicHandlerEvents, _) =>
      client ! e.tChannel.sendMessage("Currently not playing music")
      stay()
    case Event(track: AudioTrack, _) =>
      queueTrack(None, track)
      log.info("Received track")
      stay()
    case Event(playlist: AudioPlaylist, _) =>
      log.info("Received playlist")
      if (playlist.isSearchResult) {
        Option(playlist.getSelectedTrack)
          .orElse(playlist.getTracks.asScala.headOption)
          .foreach(queueTrack(None, _))
      } else queueTracks(None, playlist.getTracks.asScala: _*)
      stay()
    case Event(AudioAPIMessage.UserSpeaking(_, _, _, _, _, _), _) => stay() //NO-OP
    case Event(APIMessage.VoiceStateUpdate(_, _), _)              => stay() //NO-OP
    case Event(APIMessage.VoiceServerUpdate(_, _, _, _), _)       => stay() //NO-OP
  }

  when(Active) {
    case Event(DiscordClient.ShutdownClient, CanSendAudio(voiceWs, dataSender, _)) =>
      voiceWs ! Logout
      dataSender ! StopSendAudio
      stop()
    case Event(msg: APIMessage.MessageCreate, _) =>
      commandRouter.forward(msg)
      stay()
    case Event(APIMessage.VoiceStateUpdate(_, _), _)        => stay() //NO-OP
    case Event(APIMessage.VoiceServerUpdate(_, _, _, _), _) => stay() //NO-OP
    case Event(StopMusic(tChannel), CanSendAudio(voiceWs, dataSender, _)) =>
      lastTChannel = tChannel
      player.stopTrack()
      voiceWs ! SetSpeaking(false)
      voiceWs ! Logout
      context.stop(dataSender)

      client ! VoiceStateUpdate(VoiceStateUpdateData(guildId, None, selfMute = false, selfDeaf = false))

      log.info("Stopped and left")
      goto(Inactive) using Connecting(None, None, None, None)
    case Event(QueueUrl(url, tChannel, vChannelId), CanSendAudio(_, _, inVChannelId)) =>
      log.info("Received queue item")
      if (vChannelId == inVChannelId) {
        lastTChannel = tChannel
        MusicHandler.loadItem(url).pipeTo(self)
        stay()
      } else {
        tChannel.sendMessage("Currently playing music for different channel")
        stay()
      }
    case Event(NextTrack(tChannel), CanSendAudio(_, dataSender, _)) =>
      lastTChannel = tChannel
      nextTrack(dataSender)
      stay()
    case Event(TogglePause(tChannel), _) =>
      lastTChannel = tChannel
      player.setPaused(!player.isPaused)
      stay()
    case Event(e: FriendlyException, _) => handleFriendlyException(e, None)
    case Event(track: AudioTrack, CanSendAudio(_, dataSender, _)) =>
      queueTrack(Some(dataSender), track)
      log.info("Received track")
      stay()
    case Event(playlist: AudioPlaylist, CanSendAudio(_, dataSender, _)) =>
      log.info("Received playlist")
      if (playlist.isSearchResult) {
        Option(playlist.getSelectedTrack)
          .orElse(playlist.getTracks.asScala.headOption)
          .foreach(queueTrack(Some(dataSender), _))
      } else queueTracks(Some(dataSender), playlist.getTracks.asScala: _*)
      stay()
    case Event(_: PlayerPauseEvent, _) =>
      log.info("Paused")
      stay()
    case Event(_: PlayerResumeEvent, _) =>
      log.info("Resumed")
      stay()
    case Event(e: TrackStartEvent, _) =>
      client ! lastTChannel.sendMessage(s"Playing: ${trackName(e.track)}")
      stay()
    case Event(e: TrackEndEvent, CanSendAudio(_, dataSender, _)) =>
      val msg = e.endReason match {
        case AudioTrackEndReason.FINISHED    => s"Finished: ${trackName(e.track)}"
        case AudioTrackEndReason.LOAD_FAILED => s"Failed to load: ${trackName(e.track)}"
        case AudioTrackEndReason.STOPPED     => "Stop requested"
        case AudioTrackEndReason.REPLACED    => "Requested next track"
        case AudioTrackEndReason.CLEANUP     => "Leaking audio player"
      }

      client ! lastTChannel.sendMessage(msg)

      if (e.endReason.mayStartNext && queue.nonEmpty) nextTrack(dataSender)
      else if (e.endReason != AudioTrackEndReason.REPLACED) self ! StopMusic(lastTChannel)

      stay()
    case Event(e: TrackExceptionEvent, _) =>
      handleFriendlyException(e.exception, Some(e.track))
    case Event(e: TrackStuckEvent, CanSendAudio(_, dataSender, _)) =>
      client ! lastTChannel.sendMessage(s"Track stuck: ${trackName(e.track)}. Will play next track")
      nextTrack(dataSender)
      stay()
    case Event(AudioAPIMessage.UserSpeaking(_, _, _, _, _, _), _) => stay() //NO-OP
  }

  initialize()

  def trackName(track: AudioTrack): String = track.getInfo.title

  def connect(vChannelId: ChannelId, endPoint: String, clientId: UserId, sessionId: String, token: String): State = {
    val voiceWs =
      context.actorOf(
        VoiceWsHandler.props(endPoint, RawSnowflake(guildId), clientId, sessionId, token, Some(self), None),
        "VoiceWS"
      )
    voiceWs ! Login
    log.info("Connected")
    stay using HasVoiceWs(voiceWs, vChannelId)
  }

  def handleFriendlyException(e: FriendlyException, track: Option[AudioTrack]): State = {
    e.severity match {
      case FriendlyException.Severity.COMMON =>
        client ! lastTChannel.sendMessage(s"Encountered error: ${e.getMessage}")
        stay()
      case FriendlyException.Severity.SUSPICIOUS =>
        client ! lastTChannel.sendMessage(s"Encountered error: ${e.getMessage}")
        stay()
      case FriendlyException.Severity.FAULT =>
        client ! lastTChannel.sendMessage(s"Encountered internal error: ${e.getMessage}")
        throw e
    }
  }

  def queueTrack(dataSenderOpt: Option[ActorRef], track: AudioTrack): Unit = {
    dataSenderOpt match {
      case Some(dataSender) if player.startTrack(track, true) =>
        dataSender ! StartSendAudio
      case _ => queue.enqueue(track)
    }
  }

  def queueTracks(dataSenderOpt: Option[ActorRef], track: AudioTrack*): Unit = {
    queueTrack(dataSenderOpt, track.head)
    queue.enqueue(track.tail: _*)
  }

  def nextTrack(dataSender: ActorRef): Unit = if (queue.nonEmpty) {
    player.playTrack(queue.dequeue())
    dataSender ! StartSendAudio
  }
}
object MusicHandler {
  def props(client: ClientActor)(guildId: GuildId)(implicit mat: Materializer): Props =
    Props(new MusicHandler(client, guildId))

  final val UseBurstingSender = true

  sealed trait MusicState
  case object Inactive extends MusicState
  case object Active   extends MusicState

  sealed trait StateData
  case class Connecting(
      endPoint: Option[String],
      sessionId: Option[String],
      token: Option[String],
      vChannelId: Option[ChannelId]
  ) extends StateData
  case class HasVoiceWs(voiceWs: ActorRef, vChannelId: ChannelId)                         extends StateData
  case class CanSendAudio(voiceWs: ActorRef, dataSender: ActorRef, vChannelId: ChannelId) extends StateData

  sealed trait MusicHandlerEvents {
    def tChannel: TChannel
  }
  case class QueueUrl(url: String, tChannel: TChannel, vChannelId: ChannelId) extends MusicHandlerEvents
  case class StopMusic(tChannel: TChannel)                                    extends MusicHandlerEvents
  case class TogglePause(tChannel: TChannel)                                  extends MusicHandlerEvents
  case class NextTrack(tChannel: TChannel)                                    extends MusicHandlerEvents

  val playerManager: AudioPlayerManager = {
    val man = new DefaultAudioPlayerManager
    AudioSourceManagers.registerRemoteSources(man)
    man.enableGcMonitoring()
    man.getConfiguration.setResamplingQuality(AudioConfiguration.ResamplingQuality.MEDIUM)
    man
  }

  def loadItem(identifier: String): Future[AudioItem] = {
    val promise = Promise[AudioItem]

    playerManager.loadItem(identifier, new AudioLoadResultHandler {
      override def loadFailed(e: FriendlyException): Unit = promise.failure(e)

      override def playlistLoaded(playlist: AudioPlaylist): Unit = promise.success(playlist)

      override def noMatches(): Unit = promise.failure(new NoMatchException)

      override def trackLoaded(track: AudioTrack): Unit = promise.success(track)
    })

    promise.future
  }

  class AudioEventSender(sendTo: ActorRef) extends AudioEventListener {
    override def onEvent(event: AudioEvent): Unit =
      sendTo ! event
  }

  class NoMatchException extends Exception
}
