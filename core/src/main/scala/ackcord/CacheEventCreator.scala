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
import ackcord.data.{CreatedInvite, Guild, GuildChannel, GuildId, TextChannel, TextChannelId, TextGuildChannel}
import ackcord.gateway.Dispatch
import ackcord.util.AckCordGatewaySettings
import akka.NotUsed
import cats.Later
import cats.syntax.all._
import io.circe.{ACursor, CursorOp, Decoder, Json}
import org.slf4j.Logger

object CacheEventCreator {

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
                Some(
                  api.ChannelCreate(
                    data.guildId.flatMap(state.current.getGuild),
                    data.toGuildChannel(data.guildId.get).get,
                    state
                  )
                ),
              CacheHandlers.rawChannelUpdater,
              registry,
              dispatch
            )
          case gatewayEv.ChannelUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => Some(api.ChannelUpdate(data.guildId.flatMap(state.current.getGuild), data.toChannel.get, state)),
              CacheHandlers.rawChannelUpdater,
              registry,
              dispatch
            )
          case gatewayEv.ChannelDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                Some(
                  api.ChannelDelete(
                    data.guildId.flatMap(state.current.getGuild),
                    data.toChannel.get.asInstanceOf[GuildChannel],
                    state
                  )
                ),
              CacheHandlers.rawChannelDeleter,
              registry,
              dispatch
            )
          case gatewayEv.ChannelPinsUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                Some(
                  api.ChannelPinsUpdate(
                    data.guildId.flatMap(state.current.getGuild),
                    data.channelId,
                    data.timestamp.toOption,
                    state
                  )
                ),
              NOOPHandler,
              registry,
              dispatch
            )
          case gatewayEv.GuildCreate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => Some(api.GuildCreate(data.toGuild.get, state)),
              CacheHandlers.rawGuildUpdater,
              registry,
              dispatch
            )
          case gatewayEv.GuildUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state => Some(api.GuildUpdate(data.toGuild.get, state)),
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
                      data.joinedAt,
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
                  .map { g =>
                    api.GuildMembersChunk(
                      g,
                      data.members.map(_.toGuildMember(g.id)),
                      data.chunkIndex,
                      data.chunkCount,
                      data.notFound,
                      data.nonce,
                      state
                    )
                  },
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
                    data.createdAt,
                    data.targetUser,
                    data.targetUserType
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
              state => Some(api.MessageCreate(data.guildId.flatMap(state.current.getGuild), data.toMessage, state)),
              CacheHandlers.rawMessageUpdater,
              registry,
              dispatch
            )
          case gatewayEv.MessageUpdate(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                state.current
                  .getMessage(data.id)
                  .map(message => api.MessageUpdate(message.guild(state.current), message, state)),
              CacheHandlers.rawPartialMessageUpdater,
              registry,
              dispatch
            )
          case gatewayEv.MessageDelete(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                Some(api.MessageDelete(data.id, data.guildId.flatMap(state.current.getGuild), data.channelId, state)),
              CacheHandlers.rawMessageDeleter,
              registry,
              dispatch
            )
          case gatewayEv.MessageDeleteBulk(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                Some(
                  api.MessageDeleteBulk(data.ids, data.guildId.flatMap(state.current.getGuild), data.channelId, state)
                ),
              CacheHandlers.rawMessageBulkDeleter,
              registry,
              dispatch
            )
          case gatewayEv.MessageReactionAdd(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                Some(
                  api.MessageReactionAdd(
                    data.userId,
                    data.guildId.flatMap(state.current.getGuild),
                    data.channelId,
                    data.messageId,
                    data.emoji,
                    data.member.map(_.user).orElse(state.current.getUser(data.userId)),
                    data.member.map(_.toGuildMember(data.guildId.get)),
                    state
                  )
                ),
              CacheHandlers.rawMessageReactionUpdater,
              registry,
              dispatch
            )
          case gatewayEv.MessageReactionRemove(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                Some(
                  api.MessageReactionRemove(
                    data.userId,
                    data.guildId.flatMap(state.current.getGuild),
                    data.channelId,
                    data.messageId,
                    data.emoji,
                    data.member.map(_.user).orElse(state.current.getUser(data.userId)),
                    data.member.map(_.toGuildMember(data.guildId.get)),
                    state
                  )
                ),
              CacheHandlers.rawMessageReactionDeleter,
              registry,
              dispatch
            )
          case gatewayEv.MessageReactionRemoveAll(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                Some(
                  api.MessageReactionRemoveAll(
                    data.guildId.flatMap(state.current.getGuild),
                    data.channelId,
                    data.messageId,
                    state
                  )
                ),
              CacheHandlers.rawMessageReactionAllDeleter,
              registry,
              dispatch
            )
          case gatewayEv.MessageReactionRemoveEmoji(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                Some(
                  api.MessageReactionRemoveEmoji(
                    data.guildId.flatMap(state.current.getGuild),
                    data.channelId,
                    data.messageId,
                    data.emoji,
                    state
                  )
                ),
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
                } yield api.PresenceUpdate(guild, user, presence, state),
              PresenceUpdater,
              registry,
              dispatch
            )
          case gatewayEv.TypingStart(_, GetLazy(data)) =>
            CacheUpdate(
              data,
              state =>
                Some(
                  api.TypingStart(
                    data.guildId.flatMap(state.current.getGuild),
                    data.channelId,
                    data.userId,
                    data.timestamp,
                    data.member.map(_.user).orElse(state.current.getUser(data.userId)),
                    data.member.map(_.toGuildMember(data.guildId.get)),
                    state
                  )
                ),
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
