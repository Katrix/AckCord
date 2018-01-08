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
package net.katsstuff.ackcord.highlvl

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

import com.sedmelluq.discord.lavaplayer.player.{AudioPlayer, AudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.track.AudioItem

import akka.actor.ActorRef
import akka.pattern.{ask, gracefulStop}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.util.Timeout
import akka.{Done, NotUsed}
import net.katsstuff.ackcord.DiscordShard.ShardActor
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.data.{CacheSnapshot, ChannelId, GuildId}
import net.katsstuff.ackcord.highlvl.MusicManager.{ConnectToChannel, DisconnectFromChannel, SetChannelPlaying}
import net.katsstuff.ackcord.http.requests.RequestHelper
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler.{Login, Logout}
import net.katsstuff.ackcord.lavaplayer.LavaplayerHandler
import net.katsstuff.ackcord.util.MessageParser
import net.katsstuff.ackcord.{APIMessage, Cache, DiscordShard, RequestDSL}

case class DiscordClient(shards: Seq[ShardActor], cache: Cache, commands: Commands, requests: RequestHelper) {
  import requests.{mat, system}

  require(shards.nonEmpty, "No shards")

  var shardShutdownManager: ActorRef = _
  val musicManager:         ActorRef = system.actorOf(MusicManager.props(cache), "MusicManager")

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  def login(): Future[Done] = {
    shardShutdownManager = system.actorOf(ShardShutdownManager.props(shards), "ShutdownManager")

    if (shards.lengthCompare(1) > 0) DiscordShard.loginShards(shards)
    else
      Future {
        shards.head ! Login
        Done
      }
  }

  def logout(timeout: FiniteDuration): Future[Boolean] = {
    require(shardShutdownManager != null, "Logout before login")
    gracefulStop(shardShutdownManager, timeout, Logout)
  }

  def runDSL(source: Source[RequestDSL[Unit], NotUsed]): (UniqueKillSwitch, Future[Done]) = {
    source
      .viaMat(KillSwitches.single)(Keep.right)
      .flatMapConcat(_.toSource(requests.flow))
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  //Event handling

  def onEventDSLC(
      handler: CacheSnapshot => PartialFunction[APIMessage, RequestDSL[Unit]]
  ): (UniqueKillSwitch, Future[Done]) = runDSL {
    cache.subscribeAPI.collect {
      case msg if handler(msg.cache.current).isDefinedAt(msg) => handler(msg.cache.current)(msg)
    }
  }

  def onEventDSL(handler: PartialFunction[APIMessage, RequestDSL[Unit]]): (UniqueKillSwitch, Future[Done]) =
    onEventDSLC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }

  def onEventC(handler: CacheSnapshot => PartialFunction[APIMessage, Unit]): (UniqueKillSwitch, Future[Done]) =
    onEventDSLC { c =>
      {
        case msg if handler(c).isDefinedAt(msg) => RequestDSL.pure(handler(c)(msg))
      }
    }

  def onEvent(handler: PartialFunction[APIMessage, Unit]): (UniqueKillSwitch, Future[Done]) = {
    onEventC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }
  }

  //Command handling

  def onRawCommandDSLC(
      handler: CacheSnapshot => PartialFunction[RawCmd, RequestDSL[Unit]]
  ): (UniqueKillSwitch, Future[Done]) = {
    runDSL {
      commands.subscribe.collect {
        case cmd: RawCmd if handler(cmd.c).isDefinedAt(cmd) => handler(cmd.c)(cmd)
      }
    }
  }

  def onRawCommandDSL(handler: PartialFunction[RawCmd, RequestDSL[Unit]]): (UniqueKillSwitch, Future[Done]) =
    onRawCommandDSLC { _ =>
      {
        case cmd if handler.isDefinedAt(cmd) => handler(cmd)
      }
    }

  def onRawCommandC(handler: CacheSnapshot => PartialFunction[RawCmd, Unit]): (UniqueKillSwitch, Future[Done]) = {
    onRawCommandDSLC { c =>
      {
        case msg if handler(c).isDefinedAt(msg) => RequestDSL.pure(handler(c)(msg))
      }
    }
  }

  def onRawCommand(handler: PartialFunction[RawCmd, Unit]): (UniqueKillSwitch, Future[Done]) =
    onRawCommandC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }

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

  def leaveChannel(guildId: GuildId, destroyPlayer: Boolean = false): Unit =
    musicManager ! DisconnectFromChannel(guildId, destroyPlayer)

  def setPlaying(guildId: GuildId, playing: Boolean): Unit =
    musicManager ! SetChannelPlaying(guildId, playing)

  def loadTrack(playerManager: AudioPlayerManager, identifier: String): Future[AudioItem] =
    LavaplayerHandler.loadItem(playerManager, identifier)
}
