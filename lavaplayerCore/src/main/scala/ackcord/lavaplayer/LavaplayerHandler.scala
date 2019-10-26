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

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

import ackcord._
import ackcord.data._
import ackcord.gateway.{GatewayMessage, VoiceStateUpdate, VoiceStateUpdateData}
import ackcord.util.Switch
import ackcord.voice.{AudioAPIMessage, VoiceHandler, VoiceUDPFlow}
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.scaladsl.{GraphDSL, Sink, Source}
import akka.stream.{SourceShape, ThrottleMode}
import akka.util.ByteString
import com.sedmelluq.discord.lavaplayer.player.event._
import com.sedmelluq.discord.lavaplayer.player.{AudioLoadResultHandler, AudioPlayer, AudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.{AudioItem, AudioPlaylist, AudioTrack}
import org.slf4j.Logger

object LavaplayerHandler {
  sealed trait InactiveState
  private case object Idle extends InactiveState

  private case class Connecting(
      vChannelId: ChannelId,
      sender: ActorRef[Reply],
      negotiator: ActorRef[VoiceServerNegotiator.Command]
  ) extends InactiveState

  private case class HasVoiceWs(
      voiceHandler: ActorRef[VoiceHandler.Command],
      vChannelId: ChannelId,
      sender: ActorRef[Reply],
      toggle: AtomicBoolean
  ) extends InactiveState

  private case class CanSendAudio(
      voiceHandler: ActorRef[VoiceHandler.Command],
      inVChannelId: ChannelId,
      toggle: AtomicBoolean,
      sender: ActorRef[Reply]
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
      inactive(Parameters(player, guildId, cache, useBursting, context, context.log), Idle)
    }

  private def soundProducer(toggle: AtomicBoolean, player: AudioPlayer) =
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val switch  = b.add(new Switch[ByteString](toggle, List.fill(5)(VoiceUDPFlow.silence), Nil))
      val silence = b.add(Source.maybe[ByteString])
      val music   = b.add(LavaplayerSource.source(player).throttle(1, 20.millis, maximumBurst = 10, ThrottleMode.Shaping))

      music ~> switch.in0
      silence ~> switch.in1

      SourceShape(switch.out)
    })

  private def readyListener(replyTo: ActorRef[WsReady.type]): Behavior[AudioAPIMessage] = Behaviors.receiveMessage {
    case _: AudioAPIMessage.Ready =>
      replyTo ! WsReady
      Behaviors.same
    case _: AudioAPIMessage.UserSpeaking => Behaviors.same
    case _: AudioAPIMessage.ReceivedData => Behaviors.same
  }

  def handleConflictingConnect(
      command: ConnectVChannel,
      parameters: Parameters,
      newVChannelId: ChannelId,
      inVChannelId: ChannelId,
      force: Boolean,
      firstSender: ActorRef[Reply],
      newSender: ActorRef[Reply],
      voiceHandler: Option[ActorRef[VoiceHandler.Command]]
  ): Behavior[Command] = {
    if (newVChannelId != inVChannelId) {
      if (force) {
        parameters.context.child("ServerNegotiator").foreach {
          case negotiator: ActorRef[VoiceServerNegotiator.Command @unchecked] =>
            parameters.context.watchWith(negotiator, command)
            negotiator ! VoiceServerNegotiator.Stop
        }

        voiceHandler.foreach(_ ! VoiceHandler.Logout)
        firstSender ! ForcedConnectionFailure(inVChannelId, newVChannelId)

        inactive(parameters, Idle)
      } else {
        newSender ! AlreadyConnectedFailure(inVChannelId, newVChannelId)
        Behaviors.same
      }
    } else {
      //Ignored
      Behaviors.same
    }
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
      val toggle   = new AtomicBoolean(true)
      val producer = soundProducer(toggle, player)

      val readyListenerActor = context.spawn(readyListener(context.self), "ReadyListener")

      val voiceWs = context.spawn(
        VoiceHandler(
          endPoint,
          RawSnowflake(guildId),
          userId,
          sessionId,
          token,
          Some(readyListenerActor),
          soundProducer = producer,
          Sink.ignore.mapMaterializedValue(_ => NotUsed)
        ),
        "VoiceHandler"
      )
      log.debug("Music Connected")
      inactive(parameters, HasVoiceWs(voiceWs, vChannelId, sender, toggle))
    }

    (msg, state) match {
      case (ConnectVChannel(vChannelId, _, replyTo), Idle) =>
        log.debug("Connecting to new voice channel")
        val adaptedSelf = context.messageAdapter[VoiceServerNegotiator.GotVoiceData] { m =>
          GotVoiceData(m.sessionId, m.token, m.endpoint, m.userId)
        }
        val negotiator =
          context.spawn(VoiceServerNegotiator(guildId, vChannelId, cache, adaptedSelf), "ServerNegotiator")

        inactive(parameters, Connecting(vChannelId, replyTo, negotiator))

      case (connect @ ConnectVChannel(newVChannelId, force, replyTo), Connecting(inVChannelId, firstSender, _)) =>
        handleConflictingConnect(connect, parameters, newVChannelId, inVChannelId, force, firstSender, replyTo, None)

      case (
          connect @ ConnectVChannel(newVChannelId, force, replyTo),
          HasVoiceWs(voiceHandler, inVChannelId, firstSender, _)
          ) =>
        handleConflictingConnect(
          connect,
          parameters,
          newVChannelId,
          inVChannelId,
          force,
          firstSender,
          replyTo,
          Some(voiceHandler)
        )

      case (DisconnectVChannel, Idle) =>
        Behaviors.same

      case (DisconnectVChannel, con: Connecting) =>
        con.negotiator ! VoiceServerNegotiator.Stop

        Source
          .single(
            VoiceStateUpdate(VoiceStateUpdateData(guildId, None, selfMute = false, selfDeaf = false))
              .asInstanceOf[GatewayMessage[Any]]
          )
          .runWith(cache.gatewayPublish)

        inactive(parameters, Idle)

      case (DisconnectVChannel, hasWs: HasVoiceWs) =>
        hasWs.voiceHandler ! VoiceHandler.Logout

        Source
          .single(
            VoiceStateUpdate(VoiceStateUpdateData(guildId, None, selfMute = false, selfDeaf = false))
              .asInstanceOf[GatewayMessage[Any]]
          )
          .runWith(cache.gatewayPublish)

        inactive(parameters, Idle)

      case (GotVoiceData(sessionId, token, endpoint, userId), Connecting(inVChannelId, replyTo, _)) =>
        log.debug("Received session id, token and endpoint")
        connect(inVChannelId, endpoint, userId, sessionId, token, replyTo)

      case (GotVoiceData(_, _, _, _), _) =>
        Behaviors.same

      case (
          WsReady,
          HasVoiceWs(voiceWs, vChannelId, sendEventsTo, toggle)
          ) =>
        log.debug("Audio ready")

        sendEventsTo ! MusicReady

        active(parameters, CanSendAudio(voiceWs, vChannelId, toggle, sendEventsTo))

      case (WsReady, _) =>
        Behaviors.same

      case (Shutdown, HasVoiceWs(voiceWs, _, _, _)) =>
        context.watchWith(voiceWs, StopNow)
        voiceWs ! VoiceHandler.Logout
        Behaviors.same

      case (StopNow, _) =>
        Behaviors.stopped

      case (Shutdown, _)      => Behaviors.stopped
      case (SetPlaying(_), _) => Behaviors.same
    }
  }

  def active(parameters: LavaplayerHandler.Parameters, state: CanSendAudio): Behavior[Command] = {
    import parameters._
    import state._
    implicit val system: ActorSystem[Nothing] = context.system

    Behaviors.receiveMessage {
      case SetPlaying(speaking) =>
        toggle.set(speaking)
        voiceHandler ! VoiceHandler.SetSpeaking(speaking)
        Behaviors.same

      case DisconnectVChannel =>
        voiceHandler ! VoiceHandler.Logout

        Source
          .single(
            VoiceStateUpdate(VoiceStateUpdateData(guildId, None, selfMute = false, selfDeaf = false))
              .asInstanceOf[GatewayMessage[Any]]
          )
          .runWith(cache.gatewayPublish)

        log.debug("Left voice channel")
        inactive(parameters, Idle)

      case connect @ ConnectVChannel(newVChannelId, force, replyTo) =>
        handleConflictingConnect(
          connect,
          parameters,
          newVChannelId,
          inVChannelId,
          force,
          replyTo,
          replyTo,
          Some(voiceHandler)
        )

      case Shutdown =>
        context.watchWith(voiceHandler, StopNow)
        voiceHandler ! VoiceHandler.Logout
        inactive(parameters, Idle)

      case StopNow => Behaviors.stopped

      case WsReady => Behaviors.same //NO-OP

      case GotVoiceData(_, _, _, _) => Behaviors.same
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

  private case object StopNow extends Command
  private case object WsReady extends Command

  private case class GotVoiceData(sessionId: String, token: String, endpoint: String, userId: UserId) extends Command

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
