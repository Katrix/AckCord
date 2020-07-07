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
package ackcord

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

import ackcord.MusicManager.{ConnectToChannel, DisconnectFromChannel, SetChannelPlaying}
import ackcord.commands._
import ackcord.data.{GuildId, VoiceGuildChannelId}
import ackcord.lavaplayer.LavaplayerHandler
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import cats.data.OptionT
import com.sedmelluq.discord.lavaplayer.player.{AudioPlayer, AudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.track.AudioItem

/**
  * Trait used to interface with Discord stuff from high level.
  */
trait DiscordClient {

  type OptFuture[A] = OptionT[Future, A]

  /**
    * The shards of this client
    */
  def shards: Future[Seq[ActorRef[DiscordShard.Command]]]

  /**
    * Streams housing events and messages sent to and from Discord.
    */
  def events: Events

  @deprecated("Prefer events", since = "0.17")
  def cache: Events = events

  /**
    * The global commands object used by the client
    */
  def commands: CommandConnector

  /**
    * The low level requests object used by the client
    */
  def requests: Requests

  /**
    * The high level requests helper for use in user code.
    */
  val requestsHelper: RequestsHelper

  def musicManager: Future[ActorRef[MusicManager.Command]]

  implicit val system: ActorSystem[Nothing] = requests.system

  implicit val executionContext: ExecutionContextExecutor = requests.system.executionContext

  /**
    * Login the shards of this client. Note that this method just sends the
    * login signal. It does not block until a response is received.
    */
  def login(): Unit

  /**
    * Logout the shards of this client
    * @param timeout The amount of time to wait before forcing logout.
    */
  def logout(timeout: FiniteDuration = 1.minute): Future[Boolean]

  /**
    * Logs out the shards of this client, and then shuts down the actor system.
    * @param timeout The amount of time to wait for logout to succeed before forcing shutdown.
    */
  def shutdownAckCord(timeout: FiniteDuration = 1.minute): Future[Unit] =
    logout(timeout)
      .transformWith { _ =>
        requests.system.terminate()

        requests.system.whenTerminated
      }
      .map(_ => ())

  /**
    * Logs out the shards of this client, and then shuts down the JVM.
    * @param timeout The amount of time to wait for logout to succeed before forcing shutdown.
    * @return
    */
  def shutdownJVM(timeout: FiniteDuration = 1.minute): Future[Unit]

  /**
    * Runs a function whenever [[APIMessage]]s are received.
    *
    * If you use IntelliJ you might have to specify the execution type.
    * (Normally Id, SourceRequest or Future)
    * @param handler The handler function
    * @param streamable A way to convert your execution type to a stream.
    * @tparam G The execution type
    * @return An event registration to handle the listener's lifecycle.
    */
  def onEventStreamable[G[_]](handler: CacheSnapshot => PartialFunction[APIMessage, G[Unit]])(
      implicit streamable: Streamable[G]
  ): EventRegistration[NotUsed]

  /**
    * Runs a function whenever [[APIMessage]]s are received.
    *
    * @param handler The handler function
    * @return An event registration to handle the listener's lifecycle.
    */
  def onEventSideEffects(handler: CacheSnapshot => PartialFunction[APIMessage, Unit]): EventRegistration[NotUsed] =
    onEventStreamable[cats.Id](handler)

  /**
    * Runs a function whenever [[APIMessage]]s are received without the cache
    * snapshot.
    *
    * @param handler The handler function
    * @return An event registration to handle the listener's lifecycle.
    */
  def onEventSideEffectsIgnore(handler: PartialFunction[APIMessage, Unit]): EventRegistration[NotUsed] =
    onEventStreamable[cats.Id](_ => handler)

  /**
    * Runs an async function whenever [[APIMessage]]s are received.
    *
    * @param handler The handler function
    * @return An event registration to handle the listener's lifecycle.
    */
  def onEventAsync(
      handler: CacheSnapshot => PartialFunction[APIMessage, OptionT[Future, Unit]]
  ): EventRegistration[NotUsed] =
    onEventStreamable(handler)

  /**
    * Registers an [[EventListener]], created inside an [[EventsController]].
    * @param listener The listener to run
    * @tparam A The type events this listener takes.
    * @tparam Mat The materialized result of running the listener graph.
    * @return An event registration to handle the listener's lifecycle.
    */
  def registerListener[A <: APIMessage, Mat](listener: EventListener[A, Mat]): EventRegistration[Mat]

  /**
    * Starts many listeners at the same time. They must all have a
    * materialized value of NotUsed.
    * @param listeners The listeners to run.
    * @return The listeners together with their registrations.
    */
  def bulkRegisterListeners(
      listeners: EventListener[_ <: APIMessage, NotUsed]*
  ): Seq[(EventListener[_ <: APIMessage, NotUsed], EventRegistration[NotUsed])] =
    listeners.map(l => l -> registerListener(l))

  /**
    * Join a voice channel.
    * @param guildId The guildId of the voice channel.
    * @param channelId The channelId of the voice channel.
    * @param createPlayer A named argument to create a player if one doesn't
    *                     already exist.
    * @param force The the join should be force even if already connected to
    *              somewhere else (move channel).
    * @param timeoutDur The timeout duration before giving up,
    * @return A future containing the used player.
    */
  def joinChannel(
      guildId: GuildId,
      channelId: VoiceGuildChannelId,
      createPlayer: => AudioPlayer,
      force: Boolean = false,
      timeoutDur: FiniteDuration = 30.seconds
  ): Future[AudioPlayer] = {
    implicit val timeout: Timeout                  = Timeout(timeoutDur)

    musicManager
      .flatMap(
        _.ask[MusicManager.ConnectToChannelResponse](
          ConnectToChannel(guildId, channelId, force, () => createPlayer, timeoutDur, _)
        )
      )
      .flatMap {
        case MusicManager.GotPlayer(player) => Future.successful(player)
        case MusicManager.GotError(e)       => Future.failed(e)
      }
  }

  /**
    * Leave a voice channel.
    * @param guildId The guildId to leave the voice channel in.
    * @param destroyPlayer If the player used for this guild should be destroyed.
    */
  def leaveChannel(guildId: GuildId, destroyPlayer: Boolean = false): Unit =
    musicManager.foreach(_ ! DisconnectFromChannel(guildId, destroyPlayer))

  /**
    * Set a bot as speaking/playing in a channel. This is required before
    * sending any sound.
    */
  def setPlaying(guildId: GuildId, playing: Boolean): Unit =
    musicManager.foreach(_ ! SetChannelPlaying(guildId, playing))

  /**
    * Load a track using LavaPlayer.
    */
  def loadTrack(playerManager: AudioPlayerManager, identifier: String): Future[AudioItem] =
    LavaplayerHandler.loadItem(playerManager, identifier)
}
