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
package net.katsstuff.ackcord

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.higherKinds
import scala.reflect.ClassTag

import com.sedmelluq.discord.lavaplayer.player.{AudioPlayer, AudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.track.AudioItem

import akka.actor.{ActorRef, Terminated}
import akka.pattern.{ask, gracefulStop}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.util.Timeout
import akka.{Done, NotUsed}
import cats.{Id, Monad}
import net.katsstuff.ackcord.MusicManager.{ConnectToChannel, DisconnectFromChannel, SetChannelPlaying}
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.data.{ChannelId, GuildId}
import net.katsstuff.ackcord.lavaplayer.LavaplayerHandler

/**
  * Trait used to interface with Discord stuff from high level.
  */
trait DiscordClient[F[_]] extends CommandsHelper[F] {

  /**
    * The shards of this client
    */
  def shards: Seq[ActorRef]

  /**
    * The cache used by the client
    */
  def cache: Cache

  /**
    * The global commands object used by the client
    */
  def commands: Commands[F]

  /**
    * The requests object used by the client
    */
  def requests: RequestHelper

  require(shards.nonEmpty, "No shards")

  var shardShutdownManager: ActorRef = _
  val musicManager: ActorRef         = requests.system.actorOf(MusicManager.props(cache), "MusicManager")

  implicit val executionContext: ExecutionContextExecutor = requests.system.dispatcher

  /**
    * Login the shards of this client.
    */
  def login(): Future[Done] = {
    val req = requests
    import req.mat

    require(shardShutdownManager == null, "Already logged in")
    shardShutdownManager = req.system.actorOf(ShardShutdownManager.props(shards), "ShutdownManager")

    DiscordShard.startShards(shards)
  }

  /**
    * Logout the shards of this client
    * @param timeout The amount of time to wait before forcing logout.
    */
  def logout(timeout: FiniteDuration = 1.minute): Future[Boolean] = {
    require(shardShutdownManager != null, "Logout before login")
    val res = gracefulStop(shardShutdownManager, timeout, DiscordShard.StopShard)
    shardShutdownManager = null
    res
  }

  /**
    * Logs out the shards of this client, and then shuts down the actor system.
    * @param timeout The amount of time to wait before forcing shutdown.
    */
  def shutdown(timeout: FiniteDuration = 1.minute): Future[Terminated] =
    logout(timeout).flatMap(_ => requests.system.terminate())

  protected def runDSL(source: Source[RequestDSL[Unit], NotUsed]): (UniqueKillSwitch, Future[Done]) = {
    val req = requests
    import req.mat

    source
      .viaMat(KillSwitches.single)(Keep.right)
      .flatMapConcat(_.toSource(requests.flow))
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  /**
    * Runs a [[RequestDSL]] once, and returns the result.
    */
  def runDSL[A](dsl: RequestDSL[A]): Future[A] = {
    val req = requests
    import req.mat
    dsl.toSource(requests.flow).toMat(Sink.head)(Keep.right).run()
  }

  //Event handling

  /**
    * Run a [[RequestDSL]] with a [[CacheSnapshot]] when an event happens.
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def onEventDSLC(
      handler: CacheSnapshot[F] => PartialFunction[APIMessage, RequestDSL[Unit]]
  ): (UniqueKillSwitch, Future[Done])

  /**
    * Run a [[RequestDSL]] when an event happens.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def onEventDSL(handler: PartialFunction[APIMessage, RequestDSL[Unit]]): (UniqueKillSwitch, Future[Done]) =
    onEventDSLC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }

  /**
    * Run some code with a [[CacheSnapshot]] when an event happens.
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def onEventC(handler: CacheSnapshot[F] => PartialFunction[APIMessage, Unit]): (UniqueKillSwitch, Future[Done]) = {
    onEventDSLC { c =>
      {
        case msg if handler(c).isDefinedAt(msg) => RequestDSL.pure(handler(c)(msg))
      }
    }
  }

  /**
    * Run some code when an event happens.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def onEvent(handler: PartialFunction[APIMessage, Unit]): (UniqueKillSwitch, Future[Done]) = {
    onEventC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }
  }

  /**
    * Registers an [[EventHandler]] that will be called when an event happens.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerHandler[A <: APIMessage](
      handler: EventHandler[A]
  )(implicit classTag: ClassTag[A], F: Monad[F], streamable: Streamable[F]): (UniqueKillSwitch, Future[Done]) =
    onEventC { implicit c =>
      {
        case msg if classTag.runtimeClass.isInstance(msg) => handler.handle[F](msg.asInstanceOf[A])
      }
    }

  /**
    * Registers an [[EventHandlerDSL]] that will be run when an event happens.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerHandler[A <: APIMessage](
      handler: EventHandlerDSL[A]
  )(implicit classTag: ClassTag[A], F: Monad[F], streamable: Streamable[F]): (UniqueKillSwitch, Future[Done]) =
    onEventDSLC { implicit c =>
      {
        case msg if classTag.runtimeClass.isInstance(msg) => handler.handle[F](msg.asInstanceOf[A])
      }
    }

  /**
    * Creates a new commands object to handle commands if the global settings are unfitting.
    * @param settings The settings to use for the commands object
    * @return A killswitch to stop this command helper, together with the command helper.
    */
  def newCommandsHelper(settings: CommandSettings): (UniqueKillSwitch, CommandsHelper[F])

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
      channelId: ChannelId,
      createPlayer: => AudioPlayer,
      force: Boolean = false,
      timeoutDur: FiniteDuration = 30.seconds
  ): Future[AudioPlayer] = {
    implicit val timeout: Timeout = Timeout(timeoutDur)
    musicManager.ask(ConnectToChannel(guildId, channelId, force, () => createPlayer, timeoutDur)).mapTo[AudioPlayer]
  }

  /**
    * Leave a voice channel.
    * @param guildId The guildId to leave the voice channel in.
    * @param destroyPlayer If the player used for this guild should be destroyed.
    */
  def leaveChannel(guildId: GuildId, destroyPlayer: Boolean = false): Unit =
    musicManager ! DisconnectFromChannel(guildId, destroyPlayer)

  /**
    * Set a bot as speaking/playing in a channel. This is required before
    * sending any sound.
    */
  def setPlaying(guildId: GuildId, playing: Boolean): Unit =
    musicManager ! SetChannelPlaying(guildId, playing)

  /**
    * Load a track using LavaPlayer.
    */
  def loadTrack(playerManager: AudioPlayerManager, identifier: String): Future[AudioItem] =
    LavaplayerHandler.loadItem(playerManager, identifier)
}
case class CoreDiscordClient(shards: Seq[ActorRef], cache: Cache, commands: Commands[Id], requests: RequestHelper)
    extends DiscordClient[Id] {

  override def onEventDSLC(
      handler: CacheSnapshot[Id] => PartialFunction[APIMessage, RequestDSL[Unit]]
  ): (UniqueKillSwitch, Future[Done]) = runDSL {
    cache.subscribeAPI.collect {
      case msg if handler(msg.cache.current).isDefinedAt(msg) => handler(msg.cache.current)(msg)
    }
  }

  override def newCommandsHelper(settings: CommandSettings): (UniqueKillSwitch, CommandsHelper[Id]) = {
    val (killSwitch, newCommands) = CoreCommands.create(
      settings.needMention,
      settings.categories,
      cache.subscribeAPI.viaMat(KillSwitches.single)(Keep.right),
      requests
    )

    killSwitch -> SeperateCommandsHelper(newCommands, requests)
  }
}
