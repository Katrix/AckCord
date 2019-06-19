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

import ackcord.cachehandlers._
import ackcord.data.raw.RawBan
import ackcord.data.{ChannelId, GuildId, TChannel}
import ackcord.gateway.{ComplexGatewayEvent, Dispatch, GatewayHandler}
import ackcord.syntax._
import ackcord.util.AckCordGatewaySettings
import akka.actor.{ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Flow
import cats.Later
import cats.syntax.all._
import io.circe.{ACursor, CursorOp, Decoder, Json}

object GatewayHandlerCache {

  def props(wsUri: Uri, settings: GatewaySettings, cache: Cache, log: LoggingAdapter, system: ActorSystem): Props = {
    import cache.mat

    val configSettings = AckCordGatewaySettings()(system)
    val sink = Flow[Dispatch[_]]
      .mapConcat(dispatch => eventToCacheUpdate(dispatch.event, log, configSettings).toList)
      .map(update => update.asInstanceOf[APIMessageCacheUpdate[Any]])
      .to(cache.publish)

    Props(new GatewayHandler(wsUri, settings, cache.gatewaySubscribe, sink))
  }

  private object GetLazy {
    def unapply[A](later: Later[Decoder.Result[A]]): Option[A] = later.value.toOption
  }

  def eventToCacheUpdate(
      event: ComplexGatewayEvent[_, _],
      log: LoggingAdapter,
      settings: AckCordGatewaySettings
  ): Option[APIMessageCacheUpdate[_]] = {
    import ackcord.gateway.{GatewayEvent => gatewayEv}
    import ackcord.{APIMessage => api, APIMessageCacheUpdate => CacheUpdate}

    def getChannelUsingMaybeGuildId(
                                     state: CacheSnapshotWithMaps,
                                     guildId: Option[GuildId],
                                     channelId: ChannelId
    ): Option[TChannel] =
      guildId.fold(state.getTChannel(channelId)) { guildId =>
        state.getGuildChannel(guildId, channelId).flatMap(_.asTChannel)
      }

    event.data.value match {
      case Right(_) =>
        val res = event match {
          case gatewayEv.Ready(_, GetLazy(data)) =>
            CacheUpdate(data, state => Some(api.Ready(state)), ReadyHandler)
          case gatewayEv.Resumed(_, GetLazy(data)) =>
            CacheUpdate(data, state => Some(api.Resumed(state)), NOOPHandler)
          case gatewayEv.ChannelCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuildChannel(data.id).map(ch => api.ChannelCreate(ch, state)),
              RawHandlers.rawChannelUpdateHandler
            )
          case gatewayEv.ChannelUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuildChannel(data.id).map(ch => api.ChannelUpdate(ch, state)),
              RawHandlers.rawChannelUpdateHandler
            )
          case gatewayEv.ChannelDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.previous.getGuildChannel(data.id).map(ch => api.ChannelDelete(ch, state)),
              RawHandlers.rawChannelDeleteHandler
            )
          case gatewayEv.ChannelPinsUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                  .map(c => api.ChannelPinsUpdate(c, data.timestamp.toOption, state)),
              NOOPHandler
            )
          case gatewayEv.GuildCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.id).map(g => api.GuildCreate(g, state)),
              RawHandlers.rawGuildUpdateHandler
            )
          case gatewayEv.GuildUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.id).map(g => api.GuildUpdate(g, state)),
              RawHandlers.rawGuildUpdateHandler
            )
          case gatewayEv.GuildDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.previous.getGuild(data.id).map(g => api.GuildDelete(g, data.unavailable, state)),
              RawHandlers.deleteGuildDataHandler
            )
          case gatewayEv.GuildBanAdd(_, GetLazy(data)) =>
            CacheUpdate(
              (data.guildId, RawBan(None, data.user)),
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildBanAdd(g, data.user, state)),
              RawHandlers.rawBanUpdateHandler
            )
          case gatewayEv.GuildBanRemove(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildBanRemove(g, data.user, state)),
              RawHandlers.rawBanDeleteHandler
            )
          case gatewayEv.GuildEmojisUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildEmojiUpdate(g, data.emojis.map(_.toEmoji), state)),
              RawHandlers.guildEmojisUpdateDataHandler
            )
          case gatewayEv.GuildIntegrationsUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.guildId).map(g => api.GuildIntegrationsUpdate(g, state)),
              NOOPHandler
            )
          case gatewayEv.GuildMemberAdd(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  g   <- state.current.getGuild(data.guildId)
                  mem <- g.members.get(data.user.id)
                } yield api.GuildMemberAdd(mem, g, state),
              RawHandlers.rawGuildMemberWithGuildUpdateHandler
            )
          case gatewayEv.GuildMemberRemove(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.guildId).map(g => api.GuildMemberRemove(data.user, g, state)),
              RawHandlers.rawGuildMemberDeleteHandler
            )
          case gatewayEv.GuildMemberUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map { g =>
                    api.GuildMemberUpdate(
                      g,
                      data.roles.flatMap(state.current.getRole(data.guildId, _)),
                      data.user,
                      data.nick,
                      state
                    )
                  },
              RawHandlers.rawGuildMemberUpdateHandler
            )
          case gatewayEv.GuildMemberChunk(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildMembersChunk(g, data.members.map(_.toGuildMember(g.id)), state)),
              RawHandlers.rawGuildMemberChunkHandler
            )
          case gatewayEv.GuildRoleCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildRoleCreate(g, data.role.toRole(data.guildId), state)),
              RawHandlers.roleUpdateHandler
            )
          case gatewayEv.GuildRoleUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildRoleUpdate(g, data.role.toRole(data.guildId), state)),
              RawHandlers.roleUpdateHandler
            )
          case gatewayEv.GuildRoleDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  previousGuild <- state.previous.getGuild(data.guildId)
                  role          <- previousGuild.roles.get(data.roleId)
                } yield api.GuildRoleDelete(previousGuild, role, state),
              RawHandlers.roleDeleteHandler
            )
          case gatewayEv.MessageCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getMessage(data.id).map(message => api.MessageCreate(message, state)),
              RawHandlers.rawMessageUpdateHandler
            )
          case gatewayEv.MessageUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getMessage(data.id).map(message => api.MessageUpdate(message, state)),
              RawHandlers.rawPartialMessageUpdateHandler
            )
          case gatewayEv.MessageDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  message <- state.previous.getMessage(data.id)
                  channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                } yield api.MessageDelete(message, channel, state),
              RawHandlers.rawMessageDeleteHandler
            )
          case gatewayEv.MessageDeleteBulk(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId).map { channel =>
                  api.MessageDeleteBulk(data.ids.flatMap(state.previous.getMessage(_).toSeq), channel, state)
              },
              RawHandlers.rawMessageDeleteBulkHandler
            )
          case gatewayEv.MessageReactionAdd(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  user     <- state.current.getUser(data.userId)
                  tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                  message  <- state.current.getMessage(data.channelId, data.messageId)
                } yield api.MessageReactionAdd(user, tChannel, message, data.emoji, state),
              RawHandlers.rawMessageReactionUpdateHandler
            )
          case gatewayEv.MessageReactionRemove(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  user     <- state.current.getUser(data.userId)
                  tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                  message  <- state.current.getMessage(data.channelId, data.messageId)
                } yield api.MessageReactionRemove(user, tChannel, message, data.emoji, state),
              RawHandlers.rawMessageReactionRemoveHandler
            )
          case gatewayEv.MessageReactionRemoveAll(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                  message  <- state.current.getMessage(data.channelId, data.messageId)
                } yield api.MessageReactionRemoveAll(tChannel, message, state),
              RawHandlers.rawMessageReactionRemoveAllHandler
            )
          case gatewayEv.PresenceUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild    <- state.current.getGuild(data.guildId)
                  user     <- state.current.getUser(data.user.id)
                  presence <- guild.presences.get(user.id)
                } yield api.PresenceUpdate(guild, user, data.roles, presence, state),
              PresenceUpdateHandler
            )
          case gatewayEv.TypingStart(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  user    <- state.current.getUser(data.userId)
                  channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                } yield api.TypingStart(channel, user, data.timestamp, state),
              RawHandlers.lastTypedHandler
            )
          case gatewayEv.UserUpdate(_, GetLazy(data)) =>
            CacheUpdate(data, state => Some(api.UserUpdate(data, state)), RawHandlers.userUpdateHandler)
          case gatewayEv.VoiceStateUpdate(_, GetLazy(data)) =>
            CacheUpdate(data, state => Some(api.VoiceStateUpdate(data, state)), Handlers.voiceStateUpdateHandler)
          case gatewayEv.VoiceServerUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.VoiceServerUpdate(data.token, g, data.endpoint, state)),
              NOOPHandler
            )
          case gatewayEv.WebhookUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild   <- state.current.getGuild(data.guildId)
                  channel <- guild.channels.get(data.channelId)
                } yield api.WebhookUpdate(guild, channel, state),
              NOOPHandler
            )
        }

        Some(res)
      case Left(e) =>
        if (settings.LogJsonTraces) {
          val traces = e.history
            .foldRight((Nil: List[(String, Json)], event.rawData.hcursor: ACursor)) {
              case (op, (acc, cursor)) =>
                val currentJson          = cursor.as[Json].getOrElse(Json.obj())
                val currentHistoryString = CursorOp.opsToPath(cursor.history)

                val nextCursor = cursor.replayOne(op)
                val message = if (nextCursor.succeeded) {
                  s"Succeeded: $currentHistoryString"
                } else {
                  s"Failed: $currentHistoryString"
                }

                ((message, currentJson) :: acc, nextCursor)
            }
            ._1
            .toVector

          val maybeUniqueTraces = if (settings.OnlyUniqueTraces) {
            traces
              .foldLeft((Nil: List[(String, Json)], Set.empty[Json])) {
                case ((acc, seen), t @ (_, json)) => if (seen.contains(json)) (acc, seen) else (t :: acc, seen + json)
              }
              ._1
              .toVector
          } else traces

          val traceAmount   = settings.NumTraces
          val tracesToPrint = if (traceAmount == -1) maybeUniqueTraces else maybeUniqueTraces.takeRight(traceAmount)

          val tracesString = tracesToPrint.reverseIterator.map(t => t._1 -> t._2.noSpaces).mkString("\n")

          log.error(e, s"Failed to parse payload for ${event.name}: ${e.show}\nJson traces:$tracesString")
        } else {
          log.error(e, s"Failed to parse payload for ${event.name}: ${e.show}")
        }

        None
    }
  }

}
