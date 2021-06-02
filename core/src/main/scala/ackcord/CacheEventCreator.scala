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
import ackcord.data.{
  Channel,
  ChannelId,
  CreatedInvite,
  Guild,
  GuildChannel,
  GuildId,
  TextChannel,
  TextChannelId,
  TextGuildChannel
}
import ackcord.gateway.{Dispatch, GatewayEvent}
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

  def ackcordGatewayToCacheUpdatePartial(
      registry: CacheTypeRegistry,
      dispatch: Dispatch[_]
  ): PartialFunction[GatewayEvent[_], APIMessageCacheUpdate[_]] = {
    import ackcord.gateway.{GatewayEvent => gatewayEv}
    import ackcord.{APIMessage => api, APIMessageCacheUpdate => CacheUpdate}

    def getChannelUsingMaybeGuildId(
        state: CacheSnapshotWithMaps,
        guildId: Option[GuildId],
        channelId: ChannelId
    ): Option[Channel] =
      guildId.fold(state.getChannel(channelId))(guildId => channelId.asChannelId[GuildChannel].resolve(guildId)(state))

    def getTextChannelUsingMaybeGuildId(
        state: CacheSnapshotWithMaps,
        guildId: Option[GuildId],
        channelId: TextChannelId
    ): Option[TextChannel] =
      guildId.fold(state.getTextChannel(channelId)) { guildId =>
        channelId.asChannelId[TextGuildChannel].resolve(guildId)(state)
      }

    def getGuildIfDefined(state: CacheSnapshotWithMaps, guildId: Option[GuildId]): Option[Option[Guild]] =
      guildId.fold[Option[Option[Guild]]](Some(None))(state.getGuild(_).map(Some.apply))

    {
      case gatewayEv.Ready(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state => Some(api.Ready(data.application.id, state, dispatch.gatewayInfo)),
          ReadyUpdater,
          registry,
          dispatch
        )
      case gatewayEv.Resumed(_) =>
        CacheUpdate(NotUsed, state => Some(api.Resumed(state, dispatch.gatewayInfo)), NOOPHandler, registry, dispatch)
      case gatewayEv.ChannelCreate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            Some(
              api.ChannelCreate(
                data.guildId.flatMap(state.current.getGuild),
                getChannelUsingMaybeGuildId(state.current, data.guildId, data.id)
                  .collect {
                    case g: GuildChannel => g
                  }
                  .orElse(data.toGuildChannel(data.guildId.get))
                  .get,
                state,
                dispatch.gatewayInfo
              )
            ),
          CacheHandlers.rawChannelUpdater,
          registry,
          dispatch
        )
      case gatewayEv.ChannelUpdate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            Some(
              api.ChannelUpdate(
                data.guildId.flatMap(state.current.getGuild),
                getChannelUsingMaybeGuildId(state.current, data.guildId, data.id).orElse(data.toChannel).get,
                state,
                dispatch.gatewayInfo
              )
            ),
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
                getChannelUsingMaybeGuildId(state.previous, data.guildId, data.id)
                  .collect {
                    case c: GuildChannel => c
                  }
                  .orElse(data.toChannel.asInstanceOf[Option[GuildChannel]])
                  .get,
                state,
                dispatch.gatewayInfo
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
                data.lastPinTimestamp.toOption,
                state,
                dispatch.gatewayInfo
              )
            ),
          NOOPHandler,
          registry,
          dispatch
        )
      case gatewayEv.GuildCreate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            Some(
              api.GuildCreate(
                state.current.getGuild(data.id).orElse(data.toGuild).get,
                state,
                dispatch.gatewayInfo
              )
            ),
          CacheHandlers.rawGuildUpdater,
          registry,
          dispatch
        )
      case gatewayEv.GuildUpdate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            Some(
              api.GuildUpdate(
                state.current.getGuild(data.id).orElse(data.toGuild).get,
                state,
                dispatch.gatewayInfo
              )
            ),
          CacheHandlers.rawGuildUpdater,
          registry,
          dispatch
        )
      case gatewayEv.GuildDelete(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            state.previous
              .getGuild(data.id)
              .map(g => api.GuildDelete(g, data.unavailable, state, dispatch.gatewayInfo)),
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
              .map { g =>
                api.GuildBanAdd(
                  g,
                  state.current.getUser(data.user.id).getOrElse(data.user),
                  state,
                  dispatch.gatewayInfo
                )
              },
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
              .map { g =>
                api.GuildBanRemove(
                  g,
                  state.current.getUser(data.user.id).getOrElse(data.user),
                  state,
                  dispatch.gatewayInfo
                )
              },
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
              .map(g => api.GuildEmojiUpdate(g, data.emojis.map(_.toEmoji), state, dispatch.gatewayInfo)),
          CacheHandlers.guildEmojisUpdater,
          registry,
          dispatch
        )
      case gatewayEv.GuildIntegrationsUpdate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            state.current.getGuild(data.guildId).map(g => api.GuildIntegrationsUpdate(g, state, dispatch.gatewayInfo)),
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
            } yield api.GuildMemberAdd(mem, g, state, dispatch.gatewayInfo),
          CacheHandlers.rawGuildMemberWithGuildUpdater,
          registry,
          dispatch
        )
      case gatewayEv.GuildMemberRemove(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map { g =>
                api.GuildMemberRemove(
                  state.current.getUser(data.user.id).getOrElse(data.user),
                  g,
                  state,
                  dispatch.gatewayInfo
                )
              },
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
                  state.current.getUser(data.user.id).getOrElse(data.user),
                  data.nick,
                  data.joinedAt,
                  data.premiumSince,
                  data.deaf.getOrElse(false),
                  data.mute.getOrElse(false),
                  data.pending.getOrElse(false),
                  state,
                  dispatch.gatewayInfo
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
                  data.members.map(m => g.members.getOrElse(m.user.id, m.toGuildMember(g.id))),
                  data.chunkIndex,
                  data.chunkCount,
                  data.notFound,
                  data.nonce,
                  state,
                  dispatch.gatewayInfo
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
              .map { g =>
                api.GuildRoleCreate(
                  g,
                  g.roles.get(data.role.id).getOrElse(data.role.toRole(data.guildId)),
                  state,
                  dispatch.gatewayInfo
                )
              },
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
              .map { g =>
                api.GuildRoleUpdate(
                  g,
                  g.roles.get(data.role.id).getOrElse(data.role.toRole(data.guildId)),
                  state,
                  dispatch.gatewayInfo
                )
              },
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
            } yield api.GuildRoleDelete(previousGuild, role, state, dispatch.gatewayInfo),
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
              channel <- getTextChannelUsingMaybeGuildId(
                state.current,
                data.guildId,
                data.channelId.asChannelId[TextChannel]
              )
            } yield api.InviteCreate(
              guild,
              channel,
              CreatedInvite(
                data.channelId,
                data.code,
                data.createdAt,
                data.guildId,
                data.inviter,
                data.maxAge,
                data.maxUses,
                data.targetUser,
                data.targetType,
                data.targetApplication,
                data.temporary,
                data.uses,
              ),
              state,
              dispatch.gatewayInfo
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
              channel <- getTextChannelUsingMaybeGuildId(
                state.current,
                data.guildId,
                data.channelId.asChannelId[TextChannel]
              )
            } yield api.InviteDelete(guild, channel, data.code, state, dispatch.gatewayInfo),
          NOOPHandler,
          registry,
          dispatch
        )
      case gatewayEv.MessageCreate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            Some(
              api.MessageCreate(
                data.guildId.flatMap(state.current.getGuild),
                state.current.getMessage(data.channelId, data.id).getOrElse(data.toMessage),
                state,
                dispatch.gatewayInfo
              )
            ),
          CacheHandlers.rawMessageUpdater,
          registry,
          dispatch
        )
      case gatewayEv.MessageUpdate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            Some(
              api.MessageUpdate(
                data.channelId
                  .resolve(state.current)
                  .collect {
                    case tc: TextGuildChannel => tc.guild(state.current)
                  }
                  .flatten,
                data.id,
                data.channelId,
                state,
                dispatch.gatewayInfo
              )
            ),
          CacheHandlers.rawPartialMessageUpdater,
          registry,
          dispatch
        )
      case gatewayEv.MessageDelete(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            Some(
              api.MessageDelete(
                data.id,
                data.guildId.flatMap(state.current.getGuild),
                data.channelId,
                state,
                dispatch.gatewayInfo
              )
            ),
          CacheHandlers.rawMessageDeleter,
          registry,
          dispatch
        )
      case gatewayEv.MessageDeleteBulk(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            Some(
              api.MessageDeleteBulk(
                data.ids,
                data.guildId.flatMap(state.current.getGuild),
                data.channelId,
                state,
                dispatch.gatewayInfo
              )
            ),
          CacheHandlers.rawMessageBulkDeleter,
          registry,
          dispatch
        )
      case gatewayEv.MessageReactionAdd(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state => {
            val guild = data.guildId.flatMap(state.current.getGuild)
            Some(
              api.MessageReactionAdd(
                data.userId,
                guild,
                data.channelId,
                data.messageId,
                data.emoji,
                state.current.getUser(data.userId).orElse(data.member.map(_.user)),
                guild
                  .flatMap(_.members.get(data.userId))
                  .orElse(data.member.map(_.toGuildMember(data.guildId.get))),
                state,
                dispatch.gatewayInfo
              )
            )
          },
          CacheHandlers.rawMessageReactionUpdater,
          registry,
          dispatch
        )
      case gatewayEv.MessageReactionRemove(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state => {
            val guild = data.guildId.flatMap(state.current.getGuild)
            Some(
              api.MessageReactionRemove(
                data.userId,
                guild,
                data.channelId,
                data.messageId,
                data.emoji,
                state.current.getUser(data.userId).orElse(data.member.map(_.user)),
                guild
                  .flatMap(_.members.get(data.userId))
                  .orElse(data.member.map(_.toGuildMember(data.guildId.get))),
                state,
                dispatch.gatewayInfo
              )
            )
          },
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
                state,
                dispatch.gatewayInfo
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
                state,
                dispatch.gatewayInfo
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
            } yield api.PresenceUpdate(guild, user, presence, state, dispatch.gatewayInfo),
          PresenceUpdater,
          registry,
          dispatch
        )
      case gatewayEv.TypingStart(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state => {
            val guild = data.guildId.flatMap(state.current.getGuild)
            Some(
              api.TypingStart(
                guild,
                data.channelId,
                data.userId,
                data.timestamp,
                state.current.getUser(data.userId).orElse(data.member.map(_.user)),
                guild
                  .flatMap(_.members.get(data.userId))
                  .orElse(data.member.map(_.toGuildMember(data.guildId.get))),
                state,
                dispatch.gatewayInfo
              )
            )
          },
          CacheHandlers.lastTypedUpdater,
          registry,
          dispatch
        )
      case gatewayEv.UserUpdate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state => Some(api.UserUpdate(state.current.getUser(data.id).getOrElse(data), state, dispatch.gatewayInfo)),
          CacheHandlers.userUpdater,
          registry,
          dispatch
        )
      case gatewayEv.VoiceStateUpdate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            Some(
              api.VoiceStateUpdate(
                data.guildId
                  .flatMap(state.current.getGuild)
                  .flatMap(_.voiceStates.get(data.userId))
                  .getOrElse(data),
                state,
                dispatch.gatewayInfo
              )
            ),
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
              .map(g => api.VoiceServerUpdate(data.token, g, data.endpoint, state, dispatch.gatewayInfo)),
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
            } yield api.WebhookUpdate(guild, channel, state, dispatch.gatewayInfo),
          NOOPHandler,
          registry,
          dispatch
        )

      case gatewayEv.InteractionCreate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state => Some(api.InteractionCreate(data, state, dispatch.gatewayInfo)),
          NOOPHandler,
          registry,
          dispatch
        )

      case gatewayEv.IntegrationCreate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map(guild => api.IntegrationCreate(guild, data.integration, state, dispatch.gatewayInfo)),
          NOOPHandler,
          registry,
          dispatch
        )
      case gatewayEv.IntegrationUpdate(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map(guild => api.IntegrationUpdate(guild, data.integration, state, dispatch.gatewayInfo)),
          NOOPHandler,
          registry,
          dispatch
        )
      case gatewayEv.IntegrationDelete(_, GetLazy(data)) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map(guild =>
                api.IntegrationDelete(guild, data.id, data.applicationId, state, dispatch.gatewayInfo)
              ),
          NOOPHandler,
          registry,
          dispatch
        )
    }
  }

  def ackcordGatewayToCacheUpdateOnly(
      registry: CacheTypeRegistry,
      log: Logger
  ): Dispatch[_] => Option[APIMessageCacheUpdate[_]] =
    dispatch =>
      ackcordGatewayToCacheUpdatePartial(registry, dispatch)
        .andThen(Some(_))
        .applyOrElse(
          dispatch.event,
          (event: GatewayEvent[_]) =>
            event match {
              case _: GatewayEvent.UnknownEvent[_] => None
              case e =>
                log.error(s"Found unhandled gateway event: ${e.name}")
                None
            }
        )

  def eventToCacheUpdate(
      dispatch: Dispatch[_],
      converter: Dispatch[_] => Option[APIMessageCacheUpdate[_]],
      log: Logger,
      settings: AckCordGatewaySettings
  ): Option[APIMessageCacheUpdate[_]] = {
    val event = dispatch.event
    event.data.value match {
      case Right(_) if event.isInstanceOf[GatewayEvent.IgnoredEvent] => None
      case Right(_)                                                  => converter(dispatch)
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
