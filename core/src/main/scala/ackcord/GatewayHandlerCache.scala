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

import scala.concurrent.Future

import ackcord.cachehandlers._
import ackcord.data.raw.RawBan
import ackcord.data.{CreatedInvite, Guild, GuildChannel, GuildId, TextChannel, TextChannelId, TextGuildChannel}
import ackcord.gateway.{Dispatch, GatewayEvent, GatewayHandler}
import ackcord.requests.SupervisionStreams
import ackcord.util.AckCordGatewaySettings
import akka.NotUsed
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Flow
import cats.Later
import cats.syntax.all._
import io.circe.{ACursor, CursorOp, Decoder, Json}
import org.slf4j.Logger

object GatewayHandlerCache {

  def apply(
      wsUri: Uri,
      settings: GatewaySettings,
      cache: Cache,
      ignoredEvents: Seq[Class[_ <: GatewayEvent[_]]],
      registry: CacheTypeRegistry,
      log: Logger,
      system: ActorSystem[Nothing]
  ): Behavior[GatewayHandler.Command] = {
    import system.executionContext
    val configSettings = AckCordGatewaySettings()(system)
    val sink = SupervisionStreams.logAndContinue(
      Flow[Dispatch[_]]
        .filter(dispatch => !ignoredEvents.exists(_.isInstance(dispatch.event)))
        .mapAsync(cache.parallelism)(dispatch => Future(eventToCacheUpdate(dispatch, registry, log, configSettings).toList))
        .mapConcat(identity)
        .map(update => update.asInstanceOf[APIMessageCacheUpdate[Any]])
        .to(cache.publish)
    )(system)

    GatewayHandler(wsUri, settings, cache.gatewaySubscribe, sink)
  }

  private object GetLazy {
    def unapply[A](later: Later[Decoder.Result[A]]): Option[A] = later.value.toOption
  }

  def eventToCacheUpdate(
      dispatch: Dispatch[_],
      registry: CacheTypeRegistry,
      log: Logger,
      settings: AckCordGatewaySettings
  ): Option[APIMessageCacheUpdate[_]] = {
    import ackcord.gateway.{GatewayEvent => gatewayEv}
    import ackcord.{APIMessage => api, APIMessageCacheUpdate => CacheUpdate}
    val event = dispatch.event

    def getChannelUsingMaybeGuildId(
        state: CacheSnapshotWithMaps,
        guildId: Option[GuildId],
        channelId: TextChannelId
    ): Option[TextChannel] =
      guildId.fold(state.getTextChannel(channelId)) { guildId =>
        channelId.asChannelId[TextGuildChannel].resolve(guildId)(state)
      }

    def getGuildIfDefined(state: CacheSnapshotWithMaps, guildId: Option[GuildId]): Option[Option[Guild]] =
      guildId.fold[Option[Option[Guild]]](Some(None))(state.getGuild(_).map(Some.apply))

    event.data.value match {
      case Right(_) if event.isInstanceOf[gatewayEv.IgnoredEvent] => None
      case Right(_) =>
        val res = event match {
          case gatewayEv.Ready(_, GetLazy(data)) =>
            CacheUpdate(data, state => Some(api.Ready(state)), ReadyUpdater, registry, dispatch)
          case gatewayEv.Resumed(_) =>
            CacheUpdate(NotUsed, state => Some(api.Resumed(state)), NOOPHandler, registry, dispatch)
          case gatewayEv.ChannelCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuildChannel(data.id.asChannelId[GuildChannel])
                  .map(ch => api.ChannelCreate(ch, state)),
              CacheHandlers.rawChannelUpdater,
              registry,
              dispatch
            )
          case gatewayEv.ChannelUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuildChannel(data.id.asChannelId[GuildChannel])
                  .map(ch => api.ChannelUpdate(ch, state)),
              CacheHandlers.rawChannelUpdater,
              registry,
              dispatch
            )
          case gatewayEv.ChannelDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.previous
                  .getGuildChannel(data.id.asChannelId[GuildChannel])
                  .map(ch => api.ChannelDelete(ch, state)),
              CacheHandlers.rawChannelDeleter,
              registry,
              dispatch
            )
          case gatewayEv.ChannelPinsUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                  .map(c => api.ChannelPinsUpdate(c, data.timestamp.toOption, state)),
              NOOPHandler,
              registry,
              dispatch
            )
          case gatewayEv.GuildCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.id).map(g => api.GuildCreate(g, state)),
              CacheHandlers.rawGuildUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.id).map(g => api.GuildUpdate(g, state)),
              CacheHandlers.rawGuildUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.previous.getGuild(data.id).map(g => api.GuildDelete(g, data.unavailable, state)),
              CacheHandlers.guildDeleter,
              registry,
              dispatch
            )
          case gatewayEv.GuildBanAdd(_, GetLazy(data)) =>
            CacheUpdate(
              (data.guildId, RawBan(None, data.user)),
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildBanAdd(g, data.user, state)),
              CacheHandlers.rawBanUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildBanRemove(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildBanRemove(g, data.user, state)),
              CacheHandlers.rawBanDeleter,
              registry,
              dispatch
            )
          case gatewayEv.GuildEmojisUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildEmojiUpdate(g, data.emojis.map(_.toEmoji), state)),
              CacheHandlers.guildEmojisUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildIntegrationsUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.guildId).map(g => api.GuildIntegrationsUpdate(g, state)),
              NOOPHandler,
              registry,
              dispatch
            )
          case gatewayEv.GuildMemberAdd(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  g   <- state.current.getGuild(data.guildId)
                  mem <- g.members.get(data.user.id)
                } yield api.GuildMemberAdd(mem, g, state),
              CacheHandlers.rawGuildMemberWithGuildUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildMemberRemove(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.guildId).map(g => api.GuildMemberRemove(data.user, g, state)),
              CacheHandlers.rawGuildMemberDeleter,
              registry,
              dispatch
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
                      data.premiumSince,
                      state
                    )
                  },
              CacheHandlers.rawGuildMemberUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildMemberChunk(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildMembersChunk(g, data.members.map(_.toGuildMember(g.id)), state)),
              CacheHandlers.rawGuildMemberChunkUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildRoleCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildRoleCreate(g, data.role.toRole(data.guildId), state)),
              CacheHandlers.roleModifyDataUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildRoleUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildRoleUpdate(g, data.role.toRole(data.guildId), state)),
              CacheHandlers.roleModifyDataUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildRoleDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  previousGuild <- state.previous.getGuild(data.guildId)
                  role          <- previousGuild.roles.get(data.roleId)
                } yield api.GuildRoleDelete(previousGuild, role, state),
              CacheHandlers.roleDeleteDataDeleter,
              registry,
              dispatch
            )
          case gatewayEv.InviteCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild <- getGuildIfDefined(state.current, data.guildId)
                  channel <- getChannelUsingMaybeGuildId(
                    state.current,
                    data.guildId,
                    data.channelId.asChannelId[TextChannel]
                  )
                } yield api.InviteCreate(
                  guild,
                  channel,
                  CreatedInvite(
                    data.code,
                    data.guildId,
                    data.channelId,
                    data.inviter,
                    data.uses,
                    data.maxUses,
                    data.maxAge,
                    data.temporary,
                    data.createdAt
                  ),
                  state
                ),
              NOOPHandler,
              registry,
              dispatch
            )
          case gatewayEv.InviteDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild <- getGuildIfDefined(state.current, data.guildId)
                  channel <- getChannelUsingMaybeGuildId(
                    state.current,
                    data.guildId,
                    data.channelId.asChannelId[TextChannel]
                  )
                } yield api.InviteDelete(guild, channel, data.code, state),
              NOOPHandler,
              registry,
              dispatch
            )
          case gatewayEv.MessageCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getMessage(data.id).map(message => api.MessageCreate(message, state)),
              CacheHandlers.rawMessageUpdater,
              registry,
              dispatch
            )
          case gatewayEv.MessageUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getMessage(data.id).map(message => api.MessageUpdate(message, state)),
              CacheHandlers.rawPartialMessageUpdater,
              registry,
              dispatch
            )
          case gatewayEv.MessageDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  message <- state.previous.getMessage(data.id)
                  channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                } yield api.MessageDelete(message, channel, state),
              CacheHandlers.rawMessageDeleter,
              registry,
              dispatch
            )
          case gatewayEv.MessageDeleteBulk(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId).map { channel =>
                  api.MessageDeleteBulk(data.ids.flatMap(state.previous.getMessage(_).toSeq), channel, state)
                },
              CacheHandlers.rawMessageBulkDeleter,
              registry,
              dispatch
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
              CacheHandlers.rawMessageReactionUpdater,
              registry,
              dispatch
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
              CacheHandlers.rawMessageReactionDeleter,
              registry,
              dispatch
            )
          case gatewayEv.MessageReactionRemoveAll(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild    <- getGuildIfDefined(state.current, data.guildId)
                  tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                  message  <- state.current.getMessage(data.channelId, data.messageId)
                } yield api.MessageReactionRemoveAll(guild, tChannel, message, state),
              CacheHandlers.rawMessageReactionAllDeleter,
              registry,
              dispatch
            )
          case gatewayEv.MessageReactionRemoveEmoji(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild    <- getGuildIfDefined(state.current, data.guildId)
                  tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                  message  <- state.current.getMessage(data.channelId, data.messageId)
                } yield api.MessageReactionRemoveEmoji(guild, tChannel, message, data.emoji, state),
              CacheHandlers.rawMessageReactionEmojiDeleter,
              registry,
              dispatch
            )
          case gatewayEv.PresenceUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild    <- state.current.getGuild(data.guildId)
                  user     <- state.current.getUser(data.user.id)
                  presence <- guild.presences.get(user.id)
                } yield api.PresenceUpdate(guild, user, data.roles, presence, data.nick, data.premiumSince, state),
              PresenceUpdater,
              registry,
              dispatch
            )
          case gatewayEv.TypingStart(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  user    <- state.current.getUser(data.userId)
                  channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                } yield api.TypingStart(channel, user, data.timestamp, state),
              CacheHandlers.lastTypedUpdater,
              registry,
              dispatch
            )
          case gatewayEv.UserUpdate(_, GetLazy(data)) =>
            CacheUpdate(data, state => Some(api.UserUpdate(data, state)), CacheHandlers.userUpdater, registry, dispatch)
          case gatewayEv.VoiceStateUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => Some(api.VoiceStateUpdate(data, state)),
              CacheHandlers.voiceStateUpdater,
              registry,
              dispatch
            )
          case gatewayEv.VoiceServerUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.VoiceServerUpdate(data.token, g, data.endpoint, state)),
              NOOPHandler,
              registry,
              dispatch
            )
          case gatewayEv.WebhookUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild   <- state.current.getGuild(data.guildId)
                  channel <- guild.channels.get(data.channelId.asChannelId[GuildChannel])
                } yield api.WebhookUpdate(guild, channel, state),
              NOOPHandler,
              registry,
              dispatch
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

          log.error(s"Failed to parse payload for ${event.name}: ${e.show}\nJson traces:$tracesString", e)
        } else {
          log.error(s"Failed to parse payload for ${event.name}: ${e.show}", e)
        }

        None
    }
  }

}
