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
package net.katsstuff.ackcord

import akka.actor.Props
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Flow
import cats.Later
import io.circe.Decoder
import net.katsstuff.ackcord.cachehandlers.{Handlers, NOOPHandler, PresenceUpdateHandler, RawHandlers, ReadyHandler}
import net.katsstuff.ackcord.data.{ChannelId, GuildId, TChannel}
import net.katsstuff.ackcord.data.raw.RawBan
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.gateway.{ComplexGatewayEvent, Dispatch, GatewayHandler}

object GatewayHandlerCache {

  def props(wsUri: Uri, settings: GatewaySettings, cache: Cache, log: LoggingAdapter): Props = {
    import cache.mat

    val sink = Flow[Dispatch[_]]
      .mapConcat(dispatch => eventToCacheUpdate(dispatch.event, log).toList)
      .map(update => update.asInstanceOf[APIMessageCacheUpdate[Any]])
      .to(cache.publish)

    Props(new GatewayHandler(wsUri, settings, cache.gatewaySubscribe, sink))
  }

  def eventToCacheUpdate(event: ComplexGatewayEvent[_, _], log: LoggingAdapter): Option[APIMessageCacheUpdate[_]] = {
    import net.katsstuff.ackcord.gateway.{GatewayEvent => gatewayEv}
    import net.katsstuff.ackcord.{APIMessage => api, APIMessageCacheUpdate => CacheUpdate}

    def handleLazy[A](
        later: Later[Decoder.Result[A]]
    )(f: A => APIMessageCacheUpdate[_]): Option[APIMessageCacheUpdate[_]] = {
      later.value match {
        case Right(value) => Some(f(value))
        case Left(e) =>
          log.error(e, "Failed to parse payload")
          None
      }
    }

    def getChannelUsingMaybeGuildId(
        state: CacheSnapshotId,
        guildId: Option[GuildId],
        channelId: ChannelId
    ): Option[TChannel] =
      guildId.fold(state.getTChannel(channelId).value) { guildId =>
        state.getGuildChannel(guildId, channelId).value.flatMap(_.asTChannel)
      }

    event match {
      case gatewayEv.Ready(later) =>
        handleLazy(later)(data => CacheUpdate(data, state => Some(api.Ready(state)), ReadyHandler))
      case gatewayEv.Resumed(later) =>
        handleLazy(later)(data => CacheUpdate(data, state => Some(api.Resumed(state)), NOOPHandler))
      case gatewayEv.ChannelCreate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuildChannel(data.id).map(ch => api.ChannelCreate(ch, state)).value,
            RawHandlers.rawChannelUpdateHandler
          )
        }
      case gatewayEv.ChannelUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuildChannel(data.id).map(ch => api.ChannelUpdate(ch, state)).value,
            RawHandlers.rawChannelUpdateHandler
          )
        }
      case gatewayEv.ChannelDelete(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.previous.getGuildChannel(data.id).map(ch => api.ChannelDelete(ch, state)).value,
            RawHandlers.rawChannelDeleteHandler
          )
        }
      case gatewayEv.ChannelPinsUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getTChannel(data.channelId)
                .map(c => api.ChannelPinsUpdate(c, data.timestamp.toOption, state))
                .value,
            NOOPHandler
          )
        }
      case gatewayEv.GuildCreate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuild(data.id).map(g => api.GuildCreate(g, state)).value,
            RawHandlers.rawGuildUpdateHandler
          )
        }
      case gatewayEv.GuildUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuild(data.id).map(g => api.GuildUpdate(g, state)).value,
            RawHandlers.rawGuildUpdateHandler
          )
        }
      case gatewayEv.GuildDelete(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.previous.getGuild(data.id).map(g => api.GuildDelete(g, data.unavailable, state)).value,
            RawHandlers.deleteGuildDataHandler
          )
        }
      case gatewayEv.GuildBanAdd(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            (data.guildId, RawBan(None, data.user)),
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => api.GuildBanAdd(g, data.user, state))
                .value,
            RawHandlers.rawBanUpdateHandler
          )
        }
      case gatewayEv.GuildBanRemove(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => api.GuildBanRemove(g, data.user, state))
                .value,
            RawHandlers.rawBanDeleteHandler
          )
        }
      case gatewayEv.GuildEmojisUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => api.GuildEmojiUpdate(g, data.emojis.map(_.toEmoji), state))
                .value,
            RawHandlers.guildEmojisUpdateDataHandler
          )
        }
      case gatewayEv.GuildIntegrationsUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuild(data.guildId).map(g => api.GuildIntegrationsUpdate(g, state)).value,
            NOOPHandler
          )
        }
      case gatewayEv.GuildMemberAdd(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                g   <- state.current.getGuild(data.guildId).value
                mem <- g.members.get(data.user.id)
              } yield api.GuildMemberAdd(mem, g, state),
            RawHandlers.rawGuildMemberWithGuildUpdateHandler
          )
        }
      case gatewayEv.GuildMemberRemove(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuild(data.guildId).map(g => api.GuildMemberRemove(data.user, g, state)).value,
            RawHandlers.rawGuildMemberDeleteHandler
          )
        }
      case gatewayEv.GuildMemberUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map { g =>
                  api.GuildMemberUpdate(
                    g,
                    data.roles.flatMap(state.current.getRole(data.guildId, _).value),
                    data.user,
                    data.nick,
                    state
                  )
                }
                .value,
            RawHandlers.rawGuildMemberUpdateHandler
          )
        }
      case gatewayEv.GuildMemberChunk(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => api.GuildMembersChunk(g, data.members.map(_.toGuildMember(g.id)), state))
                .value,
            RawHandlers.rawGuildMemberChunkHandler
          )
        }
      case gatewayEv.GuildRoleCreate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => api.GuildRoleCreate(g, data.role.toRole(data.guildId), state))
                .value,
            RawHandlers.roleUpdateHandler
          )
        }
      case gatewayEv.GuildRoleUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => api.GuildRoleUpdate(g, data.role.toRole(data.guildId), state))
                .value,
            RawHandlers.roleUpdateHandler
          )
        }
      case gatewayEv.GuildRoleDelete(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                previousGuild <- state.previous.getGuild(data.guildId).value
                role          <- previousGuild.roles.get(data.roleId)
              } yield api.GuildRoleDelete(previousGuild, role, state),
            RawHandlers.roleDeleteHandler
          )
        }
      case gatewayEv.MessageCreate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getMessage(data.id).map(message => api.MessageCreate(message, state)).value,
            RawHandlers.rawMessageUpdateHandler
          )
        }
      case gatewayEv.MessageUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getMessage(data.id).map(message => api.MessageUpdate(message, state)).value,
            RawHandlers.rawPartialMessageUpdateHandler
          )
        }
      case gatewayEv.MessageDelete(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                message <- state.previous.getMessage(data.id).value
                channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
              } yield api.MessageDelete(message, channel, state),
            RawHandlers.rawMessageDeleteHandler
          )
        }
      case gatewayEv.MessageDeleteBulk(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId).map { channel =>
                api.MessageDeleteBulk(data.ids.flatMap(state.previous.getMessage(_).value.toSeq), channel, state)
            },
            RawHandlers.rawMessageDeleteBulkHandler
          )
        }
      case gatewayEv.MessageReactionAdd(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                user     <- state.current.getUser(data.userId).value
                tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                message  <- state.current.getMessage(data.channelId, data.messageId).value
              } yield api.MessageReactionAdd(user, tChannel, message, data.emoji, state),
            RawHandlers.rawMessageReactionUpdateHandler
          )
        }
      case gatewayEv.MessageReactionRemove(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                user     <- state.current.getUser(data.userId).value
                tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                message  <- state.current.getMessage(data.channelId, data.messageId).value
              } yield api.MessageReactionRemove(user, tChannel, message, data.emoji, state),
            RawHandlers.rawMessageReactionRemoveHandler
          )
        }
      case gatewayEv.MessageReactionRemoveAll(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                message  <- state.current.getMessage(data.channelId, data.messageId).value
              } yield api.MessageReactionRemoveAll(tChannel, message, state),
            RawHandlers.rawMessageReactionRemoveAllHandler
          )
        }
      case gatewayEv.PresenceUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                guild    <- state.current.getGuild(data.guildId).value
                user     <- state.current.getUser(data.user.id).value
                presence <- guild.presences.get(user.id)
              } yield api.PresenceUpdate(guild, user, data.roles, presence, state),
            PresenceUpdateHandler
          )
        }
      case gatewayEv.TypingStart(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                user    <- state.current.getUser(data.userId).value
                channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
              } yield api.TypingStart(channel, user, data.timestamp, state),
            RawHandlers.lastTypedHandler
          )
        }
      case gatewayEv.UserUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(data, state => Some(api.UserUpdate(data, state)), RawHandlers.userUpdateHandler)
        }
      case gatewayEv.VoiceStateUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(data, state => Some(api.VoiceStateUpdate(data, state)), Handlers.voiceStateUpdateHandler)
        }
      case gatewayEv.VoiceServerUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => api.VoiceServerUpdate(data.token, g, data.endpoint, state))
                .value,
            NOOPHandler
          )
        }
      case gatewayEv.WebhookUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                guild   <- state.current.getGuild(data.guildId).value
                channel <- guild.channels.get(data.channelId)
              } yield api.WebhookUpdate(guild, channel, state),
            NOOPHandler
          )
        }
    }
  }

}
