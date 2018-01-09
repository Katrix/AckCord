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
package net.katsstuff.ackcord

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

import com.sedmelluq.discord.lavaplayer.player.{AudioPlayer, AudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.track.AudioItem

import akka.actor.{ActorRef, Terminated}
import akka.pattern.{ask, gracefulStop}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.util.Timeout
import akka.{Done, NotUsed}
import net.katsstuff.ackcord.DiscordShard.{ShardActor, StartShard, StopShard}
import net.katsstuff.ackcord.MusicManager.{ConnectToChannel, DisconnectFromChannel, SetChannelPlaying}
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.data.{CacheSnapshot, ChannelId, GuildId}
import net.katsstuff.ackcord.http.requests.RequestHelper
import net.katsstuff.ackcord.lavaplayer.LavaplayerHandler
import net.katsstuff.ackcord.util.MessageParser

/**
  * Core class used to interface with Discord stuff from high level.
  * @param shards The shards of this client
  * @param cache The cache used by the client
  * @param commands The commands object used by the client
  * @param requests The requests object used by the client
  */
case class DiscordClient(shards: Seq[ShardActor], cache: Cache, commands: Commands, requests: RequestHelper) {
  import requests.{mat, system}

  require(shards.nonEmpty, "No shards")

  var shardShutdownManager: ActorRef = _
  val musicManager:         ActorRef = system.actorOf(MusicManager.props(cache), "MusicManager")

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  /**
    * Login the shards of this client.
    */
  def login(): Future[Done] = {
    require(shardShutdownManager == null, "Already logged in")
    shardShutdownManager = system.actorOf(ShardShutdownManager.props(shards), "ShutdownManager")

    if (shards.lengthCompare(1) > 0) DiscordShard.startShards(shards)
    else
      Future {
        shards.head ! StartShard
        Done
      }
  }

  /**
    * Logout the shards of this client
    * @param timeout The amount of time to wait before forcing logout.
    */
  def logout(timeout: FiniteDuration = 1.minute): Future[Boolean] = {
    require(shardShutdownManager != null, "Logout before login")
    val res = gracefulStop(shardShutdownManager, timeout, StopShard)
    shardShutdownManager = null
    res
  }

  /**
    * Logs out the shards of this client, and then shuts down the actor system.
    * @param timeout The amount of time to wait before forcing shutdown.
    */
  def shutdown(timeout: FiniteDuration = 1.minute): Future[Terminated] =
    logout(timeout).flatMap(_ => system.terminate())

  private def runDSL(source: Source[RequestDSL[Unit], NotUsed]): (UniqueKillSwitch, Future[Done]) = {
    source
      .viaMat(KillSwitches.single)(Keep.right)
      .flatMapConcat(_.toSource(requests.flow))
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  //Event handling

  /**
    * Run a [[RequestDSL]] with a [[CacheSnapshot]] when an event happens.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def onEventDSLC(
      handler: CacheSnapshot => PartialFunction[APIMessage, RequestDSL[Unit]]
  ): (UniqueKillSwitch, Future[Done]) = runDSL {
    cache.subscribeAPI.collect {
      case msg if handler(msg.cache.current).isDefinedAt(msg) => handler(msg.cache.current)(msg)
    }
  }

  /**
    * Run a [[RequestDSL]] when an event happens.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def onEventDSL(handler: PartialFunction[APIMessage, RequestDSL[Unit]]): (UniqueKillSwitch, Future[Done]) =
    onEventDSLC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }

  /**
    * Run some code with a [[CacheSnapshot]] when an event happens.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def onEventC(handler: CacheSnapshot => PartialFunction[APIMessage, Unit]): (UniqueKillSwitch, Future[Done]) =
    onEventDSLC { c =>
      {
        case msg if handler(c).isDefinedAt(msg) => RequestDSL.pure(handler(c)(msg))
      }
    }

  /**
    * Run some code when an event happens.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def onEvent(handler: PartialFunction[APIMessage, Unit]): (UniqueKillSwitch, Future[Done]) = {
    onEventC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }
  }

  //Command handling

  /**
    * Run a [[RequestDSL]] with a [[CacheSnapshot]] when raw command arrives.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def onRawCommandDSLC(
      handler: CacheSnapshot => PartialFunction[RawCmd, RequestDSL[Unit]]
  ): (UniqueKillSwitch, Future[Done]) = {
    runDSL {
      commands.subscribe.collect {
        case cmd: RawCmd if handler(cmd.c).isDefinedAt(cmd) => handler(cmd.c)(cmd)
      }
    }
  }

  /**
    * Run a [[RequestDSL]] when raw command arrives.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def onRawCommandDSL(handler: PartialFunction[RawCmd, RequestDSL[Unit]]): (UniqueKillSwitch, Future[Done]) =
    onRawCommandDSLC { _ =>
      {
        case cmd if handler.isDefinedAt(cmd) => handler(cmd)
      }
    }

  /**
    * Run some code with a [[CacheSnapshot]] when raw command arrives.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def onRawCommandC(handler: CacheSnapshot => PartialFunction[RawCmd, Unit]): (UniqueKillSwitch, Future[Done]) = {
    onRawCommandDSLC { c =>
      {
        case msg if handler(c).isDefinedAt(msg) => RequestDSL.pure(handler(c)(msg))
      }
    }
  }

  /**
    * Run some code when raw command arrives.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def onRawCommand(handler: PartialFunction[RawCmd, Unit]): (UniqueKillSwitch, Future[Done]) =
    onRawCommandC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }

  /**
    * Register a command which runs a [[RequestDSL]] with a [[CacheSnapshot]].
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def registerCommandDSLC[A](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(
      handler: CacheSnapshot => ParsedCmd[A] => RequestDSL[Unit]
  )(implicit parser: MessageParser[A]): (UniqueKillSwitch, Future[Done]) = {
    val sink: RequestHelper => Sink[ParsedCmd[A], UniqueKillSwitch] = requests => {
      ParsedCmdFlow[A]
        .map(handler)
        .flatMapConcat(_.toSource(requests.flow))
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.both).swap
  }

  /**
    * Register a command which runs a [[RequestDSL]].
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def registerCommandDSL[A](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(handler: ParsedCmd[A] => RequestDSL[Unit])(implicit parser: MessageParser[A]): (UniqueKillSwitch, Future[Done]) = {
    val sink: RequestHelper => Sink[ParsedCmd[A], UniqueKillSwitch] = requests => {
      ParsedCmdFlow[A]
        .map(_ => handler)
        .flatMapConcat(_.toSource(requests.flow))
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.both).swap
  }

  /**
    * Register a command which runs some code with a [[CacheSnapshot]].
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def registerCommandC[A](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(
      handler: CacheSnapshot => ParsedCmd[A] => Unit
  )(implicit parser: MessageParser[A]): (UniqueKillSwitch, Future[Done]) = {
    val sink: RequestHelper => Sink[ParsedCmd[A], UniqueKillSwitch] = _ => {
      ParsedCmdFlow[A]
        .map(handler)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.both).swap
  }

  /**
    * Register a command which runs some code.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done.
    */
  def registerCommand[A](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(handler: ParsedCmd[A] => Unit)(implicit parser: MessageParser[A]): (UniqueKillSwitch, Future[Done]) = {
    val sink: RequestHelper => Sink[ParsedCmd[A], UniqueKillSwitch] = _ => {
      ParsedCmdFlow[A]
        .map(_ => handler)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.both).swap
  }

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
