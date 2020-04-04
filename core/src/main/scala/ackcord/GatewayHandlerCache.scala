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
import ackcord.data.{ChannelId, CreatedInvite, Guild, GuildId, TChannel}
import ackcord.gateway.{ComplexGatewayEvent, Dispatch, GatewayHandler}
import ackcord.requests.SupervisionStreams
import ackcord.syntax._
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
      ignoredEvents: Seq[Class[_ <: gateway.ComplexGatewayEvent[_, _]]],
      registry: CacheTypeRegistry,
      log: Logger,
      system: ActorSystem[Nothing]
  ): Behavior[GatewayHandler.Command] = {
    val configSettings = AckCordGatewaySettings()(system)
    val sink = SupervisionStreams.logAndContinue(
      Flow[Dispatch[_]]
        .filter(dispatch => !ignoredEvents.exists(_.isInstance(dispatch.event)))
        .mapConcat(dispatch => eventToCacheUpdate(dispatch.event, registry, log, configSettings).toList)
        .map(update => update.asInstanceOf[APIMessageCacheUpdate[Any]])
        .to(cache.publish)
    )(system)

    GatewayHandler(wsUri, settings, cache.gatewaySubscribe, sink)
  }

  private object GetLazy {
    def unapply[A](later: Later[Decoder.Result[A]]): Option[A] = later.value.toOption
  }

  def eventToCacheUpdate(
      event: ComplexGatewayEvent[_, _],
      registry: CacheTypeRegistry,
      log: Logger,
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

    def getGuildIfDefined(state: CacheSnapshotWithMaps, guildId: Option[GuildId]): Option[Option[Guild]] =
      guildId.fold[Option[Option[Guild]]](Some(None))(state.getGuild(_).map(Some.apply))

    event.data.value match {
      case Right(_) if event.isInstanceOf[gatewayEv.IgnoredEvent] => None
      case Right(_) =>
        val res = event match {
          case gatewayEv.Ready(_, GetLazy(data)) =>
            CacheUpdate(data, state => Some(api.Ready(state)), ReadyUpdater, registry)
          case gatewayEv.Resumed(_) =>
            CacheUpdate(NotUsed, state => Some(api.Resumed(state)), NOOPHandler, registry)
          case gatewayEv.ChannelCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuildChannel(data.id).map(ch => api.ChannelCreate(ch, state)),
              CacheHandlers.rawChannelUpdater,
              registry
            )
          case gatewayEv.ChannelUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuildChannel(data.id).map(ch => api.ChannelUpdate(ch, state)),
              CacheHandlers.rawChannelUpdater,
              registry
            )
          case gatewayEv.ChannelDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.previous.getGuildChannel(data.id).map(ch => api.ChannelDelete(ch, state)),
              CacheHandlers.rawChannelDeleter,
              registry
            )
          case gatewayEv.ChannelPinsUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                  .map(c => api.ChannelPinsUpdate(c, data.timestamp.toOption, state)),
              NOOPHandler,
              registry
            )
          case gatewayEv.GuildCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.id).map(g => api.GuildCreate(g, state)),
              CacheHandlers.rawGuildUpdater,
              registry
            )
          case gatewayEv.GuildUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.id).map(g => api.GuildUpdate(g, state)),
              CacheHandlers.rawGuildUpdater,
              registry
            )
          case gatewayEv.GuildDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.previous.getGuild(data.id).map(g => api.GuildDelete(g, data.unavailable, state)),
              CacheHandlers.guildDeleter,
              registry
            )
          case gatewayEv.GuildBanAdd(_, GetLazy(data)) =>
            CacheUpdate(
              (data.guildId, RawBan(None, data.user)),
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildBanAdd(g, data.user, state)),
              CacheHandlers.rawBanUpdater,
              registry
            )
          case gatewayEv.GuildBanRemove(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildBanRemove(g, data.user, state)),
              CacheHandlers.rawBanDeleter,
              registry
            )
          case gatewayEv.GuildEmojisUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildEmojiUpdate(g, data.emojis.map(_.toEmoji), state)),
              CacheHandlers.guildEmojisUpdater,
              registry
            )
          case gatewayEv.GuildIntegrationsUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.guildId).map(g => api.GuildIntegrationsUpdate(g, state)),
              NOOPHandler,
              registry
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
              registry
            )
          case gatewayEv.GuildMemberRemove(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getGuild(data.guildId).map(g => api.GuildMemberRemove(data.user, g, state)),
              CacheHandlers.rawGuildMemberDeleter,
              registry
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
              registry
            )
          case gatewayEv.GuildMemberChunk(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildMembersChunk(g, data.members.map(_.toGuildMember(g.id)), state)),
              CacheHandlers.rawGuildMemberChunkUpdater,
              registry
            )
          case gatewayEv.GuildRoleCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildRoleCreate(g, data.role.toRole(data.guildId), state)),
              CacheHandlers.roleModifyDataUpdater,
              registry
            )
          case gatewayEv.GuildRoleUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.GuildRoleUpdate(g, data.role.toRole(data.guildId), state)),
              CacheHandlers.roleModifyDataUpdater,
              registry
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
              registry
            )
          case gatewayEv.InviteCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild   <- getGuildIfDefined(state.current, data.guildId)
                  channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
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
              registry
            )
          case gatewayEv.InviteDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild   <- getGuildIfDefined(state.current, data.guildId)
                  channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                } yield api.InviteDelete(guild, channel, data.code, state),
              NOOPHandler,
              registry
            )
          case gatewayEv.MessageCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getMessage(data.id).map(message => api.MessageCreate(message, state)),
              CacheHandlers.rawMessageUpdater,
              registry
            )
          case gatewayEv.MessageUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => state.current.getMessage(data.id).map(message => api.MessageUpdate(message, state)),
              CacheHandlers.rawPartialMessageUpdater,
              registry
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
              registry
            )
          case gatewayEv.MessageDeleteBulk(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId).map { channel =>
                  api.MessageDeleteBulk(data.ids.flatMap(state.previous.getMessage(_).toSeq), channel, state)
                },
              CacheHandlers.rawMessageBulkDeleter,
              registry
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
              registry
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
              registry
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
              registry
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
              registry
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
              registry
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
              registry
            )
          case gatewayEv.UserUpdate(_, GetLazy(data)) =>
            CacheUpdate(data, state => Some(api.UserUpdate(data, state)), CacheHandlers.userUpdater, registry)
          case gatewayEv.VoiceStateUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => Some(api.VoiceStateUpdate(data, state)),
              CacheHandlers.voiceStateUpdater,
              registry
            )
          case gatewayEv.VoiceServerUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getGuild(data.guildId)
                  .map(g => api.VoiceServerUpdate(data.token, g, data.endpoint, state)),
              NOOPHandler,
              registry
            )
          case gatewayEv.WebhookUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                for {
                  guild   <- state.current.getGuild(data.guildId)
                  channel <- guild.channels.get(data.channelId)
                } yield api.WebhookUpdate(guild, channel, state),
              NOOPHandler,
              registry
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
