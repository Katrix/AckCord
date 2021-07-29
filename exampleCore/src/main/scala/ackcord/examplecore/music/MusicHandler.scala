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

package ackcord.examplecore.music

import scala.collection.immutable.Queue
import scala.concurrent.duration._

import ackcord.commands.{NamedDescribedCommand, NamedDescribedComplexCommand}
import ackcord.data.raw.RawMessage
import ackcord.data.{GuildId, TextChannel, VoiceGuildChannelId}
import ackcord.examplecore.Compat
import ackcord.lavaplayer.LavaplayerHandler
import ackcord.lavaplayer.LavaplayerHandler.AudioEventSender
import ackcord.requests.Request
import ackcord.syntax._
import ackcord.{Events, Requests}
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import akka.util.Timeout
import cats.arrow.FunctionK
import com.sedmelluq.discord.lavaplayer.player.event._
import com.sedmelluq.discord.lavaplayer.player.{
  AudioConfiguration,
  AudioPlayer,
  AudioPlayerManager,
  DefaultAudioPlayerManager
}
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.{AudioItem, AudioPlaylist, AudioTrack, AudioTrackEndReason}
import org.slf4j.Logger

object MusicHandler {

  case class Parameters(
      helper: Requests,
      events: Events,
      context: ActorContext[Command],
      log: Logger,
      player: AudioPlayer,
      msgQueue: SourceQueueWithComplete[Request[RawMessage]],
      lavaplayerHandler: ActorRef[LavaplayerHandler.Command]
  )

  def apply(requests: Requests, registerCmd: FunctionK[NamedDescribedComplexCommand[Any, *], cats.Id], events: Events)(
      guildId: GuildId
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      implicit val system: ActorSystem[Nothing] = ctx.system
      val player                                = MusicHandler.playerManager.createPlayer()

      player.addListener(new AudioEventSender(ctx.self, AudioEventWrapper))

      {
        implicit val timeout: Timeout = 30.seconds
        val cmds                      = new MusicCommands(requests, guildId, ctx.self)

        Seq(
          cmds.queue,
          cmds.stop,
          cmds.next,
          cmds.pause
        ).foreach { command: NamedDescribedCommand[_] => registerCmd(command.asInstanceOf[NamedDescribedCommand[Any]]) }
      }

      inactive(
        Parameters(
          requests,
          events,
          ctx,
          ctx.log,
          player,
          Source.queue(32, OverflowStrategy.dropHead).to(requests.sinkIgnore[RawMessage]).run(),
          ctx.spawn(LavaplayerHandler(player, guildId, events), "LavaplayerHandler")
        ),
        None,
        None,
        Queue.empty
      )
    }

  def inactive(
      parameters: Parameters,
      inVoiceChannel: Option[VoiceGuildChannelId],
      lastTextChannel: Option[TextChannel],
      queue: Queue[AudioTrack]
  ): Behavior[Command] = {
    import parameters._
    import context.executionContext

    Behaviors
      .receiveMessage[Command] {
        case Shutdown =>
          context.watchWith(lavaplayerHandler, StopNow)
          lavaplayerHandler ! LavaplayerHandler.Shutdown
          Behaviors.same

        case StopNow => Behaviors.stopped

        case GotoActive =>
          log.debug("MusicReady")
          active(parameters, inVoiceChannel.get, lastTextChannel, nextTrack(queue, parameters))

        case QueueUrl(url, tChannel, vChannelId, replyTo) if inVoiceChannel.isEmpty =>
          //TODO: Stop this at some point
          val lavaplayerReplyHandler = Behaviors.receiveMessage[LavaplayerHandler.Reply] {
            case LavaplayerHandler.MusicReady(_, _) =>
              context.self ! GotoActive
              Behaviors.same
            case LavaplayerHandler.AlreadyConnectedFailure(_, _) =>
              Behaviors.same
            case LavaplayerHandler.ForcedConnectionFailure(_, _) =>
              Behaviors.same
          }

          log.info("Received queue item in Inactive")
          lavaplayerHandler ! LavaplayerHandler.ConnectVoiceChannel(
            vChannelId,
            replyTo = context.spawnAnonymous(lavaplayerReplyHandler)
          )

          LavaplayerHandler.loadItem(MusicHandler.playerManager, url).foreach { item =>
            replyTo ! CommandAck
            context.self ! ReceivedAudioItem(item)
          }
          inactive(parameters, inVoiceChannel = Some(vChannelId), lastTextChannel = Some(tChannel), queue)

        case QueueUrl(url, tChannel, vChannelId, replyTo) =>
          if (inVoiceChannel.contains(vChannelId)) {
            LavaplayerHandler.loadItem(MusicHandler.playerManager, url).foreach { item =>
              replyTo ! CommandAck
              context.self ! ReceivedAudioItem(item)
            }
          } else {
            msgQueue
              .offer(tChannel.sendMessage("Currently joining different channel"))
              .foreach(_ => replyTo ! CommandAck)
          }
          Behaviors.same

        case e: MusicHandlerEvents =>
          val e2 = e.asInstanceOf[MusicHandlerEvents]
          msgQueue
            .offer(e2.tChannel.sendMessage("Currently not playing music"))
            .foreach(_ => e2.replyTo ! CommandAck)
          Behaviors.same

        case ReceivedAudioItem(track: AudioTrack) =>
          log.info("Received track")
          inactive(parameters, inVoiceChannel, lastTextChannel, queueTrack(isActive = false, parameters, queue, track))

        case ReceivedAudioItem(playlist: AudioPlaylist) =>
          log.info("Received playlist")
          val newQueue = if (playlist.isSearchResult) {
            Option(playlist.getSelectedTrack)
              .orElse(Compat.convertJavaList(playlist.getTracks).headOption)
              .fold(queue)(queueTrack(false, parameters, queue, _))
          } else queueTracks(false, parameters, queue, Compat.convertJavaList(playlist.getTracks): _*)
          inactive(parameters, inVoiceChannel, lastTextChannel, newQueue)

        case ReceivedAudioItem(item) =>
          log.warn(s"Unknown audio item type ${item.getClass}")
          Behaviors.same

        case SentFriendlyException(e) => handleFriendlyException(e, None, msgQueue, lastTextChannel)

        case StopMusicInside =>
          log.info("Stopped and left")

          player.stopTrack()
          lavaplayerHandler ! LavaplayerHandler.DisconnectVoiceChannel

          inactive(parameters, None, None, Queue.empty)

        case AudioEventWrapper(_) => Behaviors.same //Ignore
      }
      .receiveSignal { case (_, PostStop) =>
        player.destroy()
        Behaviors.stopped
      }
  }

  def active(
      parameters: MusicHandler.Parameters,
      inVoiceChannel: VoiceGuildChannelId,
      lastTextChannel: Option[TextChannel],
      queue: Queue[AudioTrack]
  ): Behavior[Command] = {
    import parameters._
    import context.executionContext

    Behaviors
      .receiveMessage[Command] {
        case Shutdown =>
          player.stopTrack()
          context.watchWith(lavaplayerHandler, StopNow)
          lavaplayerHandler ! LavaplayerHandler.Shutdown
          inactive(parameters, None, None, Queue.empty)

        case StopNow => Behaviors.stopped

        case StopMusic(tChannel, replyTo) =>
          log.info("Stopped and left")

          player.stopTrack()
          lavaplayerHandler ! LavaplayerHandler.DisconnectVoiceChannel
          replyTo ! CommandAck

          inactive(parameters, None, Some(tChannel), Queue.empty)

        case StopMusicInside =>
          log.info("Stopped and left")

          player.stopTrack()
          lavaplayerHandler ! LavaplayerHandler.DisconnectVoiceChannel

          inactive(parameters, None, None, Queue.empty)

        case QueueUrl(url, tChannel, vChannelId, replyTo) =>
          log.info("Received queue item")
          if (vChannelId == inVoiceChannel) {
            LavaplayerHandler.loadItem(MusicHandler.playerManager, url).foreach { item =>
              replyTo ! CommandAck
              context.self ! ReceivedAudioItem(item)
            }
          } else {
            msgQueue
              .offer(tChannel.sendMessage("Currently playing music for different channel"))
              .foreach(_ => replyTo ! CommandAck)
          }
          Behaviors.same

        case NextTrack(tChannel, replyTo) =>
          replyTo ! CommandAck
          active(parameters, inVoiceChannel, Some(tChannel), nextTrack(queue, parameters))

        case TogglePause(tChannel, replyTo) =>
          player.setPaused(!player.isPaused)
          replyTo ! CommandAck
          active(parameters, inVoiceChannel, Some(tChannel), queue)

        case SentFriendlyException(e) => handleFriendlyException(e, None, msgQueue, lastTextChannel)
        case ReceivedAudioItem(track: AudioTrack) =>
          log.info("Received track")
          active(parameters, inVoiceChannel, lastTextChannel, queueTrack(isActive = true, parameters, queue, track))

        case ReceivedAudioItem(playlist: AudioPlaylist) =>
          log.info("Received playlist")
          val newQueue = if (playlist.isSearchResult) {
            Option(playlist.getSelectedTrack)
              .orElse(Compat.convertJavaList(playlist.getTracks).headOption)
              .fold(queue)(queueTrack(isActive = true, parameters, queue, _))
          } else queueTracks(isActive = true, parameters, queue, Compat.convertJavaList(playlist.getTracks): _*)
          active(parameters, inVoiceChannel, lastTextChannel, newQueue)

        case AudioEventWrapper(_: PlayerPauseEvent) =>
          log.info("Paused")
          Behaviors.same

        case AudioEventWrapper(_: PlayerResumeEvent) =>
          log.info("Resumed")
          Behaviors.same

        case AudioEventWrapper(e: TrackStartEvent) =>
          lastTextChannel.foreach(tChannel => msgQueue.offer(tChannel.sendMessage(s"Playing: ${trackName(e.track)}")))
          Behaviors.same

        case AudioEventWrapper(e: TrackEndEvent) =>
          val msg = e.endReason match {
            case AudioTrackEndReason.FINISHED    => s"Finished: ${trackName(e.track)}"
            case AudioTrackEndReason.LOAD_FAILED => s"Failed to load: ${trackName(e.track)}"
            case AudioTrackEndReason.STOPPED     => "Stop requested"
            case AudioTrackEndReason.REPLACED    => "Requested next track"
            case AudioTrackEndReason.CLEANUP     => "Leaking audio player"
          }

          lastTextChannel.foreach(tChannel => msgQueue.offer(tChannel.sendMessage(msg)))

          val newQueue =
            if (e.endReason.mayStartNext && queue.nonEmpty) nextTrack(queue, parameters)
            else if (e.endReason != AudioTrackEndReason.REPLACED) {
              context.self ! StopMusicInside
              queue
            } else queue

          active(parameters, inVoiceChannel, lastTextChannel, newQueue)

        case AudioEventWrapper(e: TrackExceptionEvent) =>
          handleFriendlyException(e.exception, Some(e.track), msgQueue, lastTextChannel)

        case AudioEventWrapper(e: TrackStuckEvent) =>
          lastTextChannel.foreach(tChannel =>
            msgQueue.offer(tChannel.sendMessage(s"Track stuck: ${trackName(e.track)}. Will play next track"))
          )
          active(parameters, inVoiceChannel, lastTextChannel, nextTrack(queue, parameters))
        case AudioEventWrapper(e) => throw new Exception(s"Unknown audio event $e")
        case ReceivedAudioItem(e) => throw new Exception(s"Unknown audio item $e")
        case GotoActive           => Behaviors.same
      }
      .receiveSignal { case (_, PostStop) =>
        player.destroy()
        Behaviors.stopped
      }
  }

  def trackName(track: AudioTrack): String = track.getInfo.title

  def handleFriendlyException(
      e: FriendlyException,
      track: Option[AudioTrack],
      msgQueue: SourceQueueWithComplete[Request[RawMessage]],
      lastTextChannel: Option[TextChannel]
  ): Behavior[Command] = {
    e.severity match {
      case FriendlyException.Severity.COMMON =>
        lastTextChannel.foreach(tChannel => msgQueue.offer(tChannel.sendMessage(s"Encountered error: ${e.getMessage}")))
        Behaviors.same
      case FriendlyException.Severity.SUSPICIOUS =>
        lastTextChannel.foreach(tChannel => msgQueue.offer(tChannel.sendMessage(s"Encountered error: ${e.getMessage}")))
        Behaviors.same
      case FriendlyException.Severity.FAULT =>
        lastTextChannel.foreach(tChannel =>
          msgQueue.offer(tChannel.sendMessage(s"Encountered internal error: ${e.getMessage}"))
        )
        throw e
    }
  }

  def queueTrack(
      isActive: Boolean,
      parameters: Parameters,
      queue: Queue[AudioTrack],
      track: AudioTrack
  ): Queue[AudioTrack] = {
    import parameters._
    if (isActive && player.startTrack(track, true)) {
      lavaplayerHandler ! LavaplayerHandler.SetPlaying(true)
      queue
    } else {
      queue.enqueue(track)
    }
  }

  def queueTracks(
      isActive: Boolean,
      parameters: Parameters,
      queue: Queue[AudioTrack],
      track: AudioTrack*
  ): Queue[AudioTrack] = {
    val newQueue = queueTrack(isActive, parameters, queue, track.head)
    Compat.enqueueMany(newQueue, track.tail)
  }

  def nextTrack(queue: Queue[AudioTrack], parameters: Parameters): Queue[AudioTrack] = {
    import parameters._
    if (queue.nonEmpty) {
      lavaplayerHandler ! LavaplayerHandler.SetPlaying(true)
      val (track, newQueue) = queue.dequeue
      player.playTrack(track)
      newQueue
    } else {
      queue
    }
  }

  final val UseBurstingSender = true

  case object CommandAck

  sealed trait Command
  case class ReceivedAudioItem(item: AudioItem)                  extends Command
  case object GotoActive                                         extends Command
  case object Shutdown                                           extends Command
  private case object StopNow                                    extends Command
  private case class SentFriendlyException(e: FriendlyException) extends Command
  private case class AudioEventWrapper(event: AudioEvent)        extends Command
  case object StopMusicInside                                    extends Command

  sealed trait MusicHandlerEvents extends Command {
    def replyTo: ActorRef[CommandAck.type]
    def tChannel: TextChannel
  }
  case class QueueUrl(
      url: String,
      tChannel: TextChannel,
      vChannelId: VoiceGuildChannelId,
      replyTo: ActorRef[CommandAck.type]
  ) extends MusicHandlerEvents

  case class StopMusic(tChannel: TextChannel, replyTo: ActorRef[CommandAck.type])   extends MusicHandlerEvents
  case class TogglePause(tChannel: TextChannel, replyTo: ActorRef[CommandAck.type]) extends MusicHandlerEvents
  case class NextTrack(tChannel: TextChannel, replyTo: ActorRef[CommandAck.type])   extends MusicHandlerEvents

  val playerManager: AudioPlayerManager = {
    val man = new DefaultAudioPlayerManager
    AudioSourceManagers.registerRemoteSources(man)
    AudioSourceManagers.registerLocalSource(man)
    man.enableGcMonitoring()
    man.getConfiguration.setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH)
    man
  }
}
