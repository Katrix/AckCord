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

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.ask
import akka.util.Timeout
import net.katsstuff.ackcord.MusicManager.{ConnectToChannel, DisconnectFromChannel, SetChannelPlaying}
import net.katsstuff.ackcord.data.{ChannelId, GuildId}
import net.katsstuff.ackcord.lavaplayer.LavaplayerHandler
import net.katsstuff.ackcord.lavaplayer.LavaplayerHandler.{ConnectVChannel, DisconnectVChannel, SetPlaying}

class MusicManager(cache: Cache) extends Actor {
  import context.dispatcher

  private val players = mutable.HashMap.empty[GuildId, (AudioPlayer, ActorRef)]

  override def receive: Receive = {
    case ConnectToChannel(guildId, channelId, force, createPlayer, timeoutDur) =>
      implicit val timeout: Timeout = Timeout(timeoutDur)
      val (usedPlayer, actor) = players.getOrElseUpdate(guildId, {
        val player = createPlayer()
        (player, context.actorOf(LavaplayerHandler.props(player, guildId, cache), guildId.asString))
      })
      val replyTo = sender()

      //Status.Failure should be routed to failure
      actor.ask(ConnectVChannel(channelId, force)).onComplete {
        case Success(_) => replyTo ! usedPlayer
        case Failure(e) => replyTo ! Status.Failure(e)
      }
    case DisconnectFromChannel(guildId, destroyPlayer) =>
      players.remove(guildId).foreach {
        case (player, actor) =>
          actor ! DisconnectVChannel
          if (destroyPlayer) {
            player.destroy()
          }
      }
    case SetChannelPlaying(guildId, playing) =>
      players.get(guildId).foreach {
        case (_, actor) =>
          actor ! SetPlaying(playing)
      }
  }
}
object MusicManager {
  def props(cache: Cache): Props = Props(new MusicManager(cache))

  private[ackcord] case class ConnectToChannel(
      guildId: GuildId,
      channelId: ChannelId,
      force: Boolean,
      createPlayer: () => AudioPlayer,
      timeout: FiniteDuration
  )

  private[ackcord] case class DisconnectFromChannel(guildId: GuildId, destroyPlayer: Boolean)
  private[ackcord] case class SetChannelPlaying(guildId: GuildId, playing: Boolean)
}
