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

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

import ackcord.data.{GuildId, VoiceGuildChannelId}
import ackcord.lavaplayer.LavaplayerHandler
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl._
import akka.util.Timeout
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer

object MusicManager {

  private[ackcord] def apply(
      events: Events,
      players: Map[GuildId, (AudioPlayer, ActorRef[LavaplayerHandler.Command])] = Map.empty
  ): Behavior[Command] = Behaviors.receive {
    case (ctx, ConnectToChannel(guildId, channelId, force, createPlayer, timeoutDur, replyTo)) =>
      implicit val timeout: Timeout             = Timeout(timeoutDur)
      implicit val system: ActorSystem[Nothing] = ctx.system
      import ctx.executionContext

      val (usedPlayer, actor) = players.getOrElse(
        guildId, {
          val player = createPlayer()
          (player, ctx.spawn(LavaplayerHandler(player, guildId, events), guildId.asString))
        }
      )

      //TODO: Handle errors
      actor.ask[LavaplayerHandler.Reply](LavaplayerHandler.ConnectVoiceChannel(channelId, force, _)).onComplete {
        case Success(_) => replyTo ! GotPlayer(usedPlayer)
        case Failure(e) => replyTo ! GotError(e)
      }

      apply(events, players.updated(guildId, (usedPlayer, actor)))

    case (_, DisconnectFromChannel(guildId, destroyPlayer)) =>
      players.get(guildId) match {
        case Some((player, actor)) =>
          actor ! LavaplayerHandler.DisconnectVoiceChannel

          if (destroyPlayer) {
            player.destroy()
            actor ! LavaplayerHandler.Shutdown
            apply(events, players - guildId)
          } else {
            Behaviors.same
          }
        case None => Behaviors.same
      }

    case (_, SetChannelPlaying(guildId, playing)) =>
      players.get(guildId).foreach {
        case (_, actor) =>
          actor ! LavaplayerHandler.SetPlaying(playing)
      }
      Behaviors.same
  }

  sealed trait Command

  sealed trait ConnectToChannelResponse
  case class GotPlayer(player: AudioPlayer) extends ConnectToChannelResponse
  case class GotError(e: Throwable)         extends ConnectToChannelResponse

  private[ackcord] case class ConnectToChannel(
      guildId: GuildId,
      channelId: VoiceGuildChannelId,
      force: Boolean,
      createPlayer: () => AudioPlayer,
      timeout: FiniteDuration,
      replyTo: ActorRef[ConnectToChannelResponse]
  ) extends Command

  private[ackcord] case class DisconnectFromChannel(guildId: GuildId, destroyPlayer: Boolean) extends Command
  private[ackcord] case class SetChannelPlaying(guildId: GuildId, playing: Boolean)           extends Command
}
