/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
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
import net.katsstuff.ackcord.data.{ChannelId, GuildId, TChannel, TGuildChannel}
import net.katsstuff.ackcord.data.raw.RawBan
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.websocket.gateway.{ComplexGatewayEvent, Dispatch, GatewayHandler}

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
    import net.katsstuff.ackcord.websocket.gateway.{GatewayEvent => gateway}
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
      case gateway.Ready(later) =>
        handleLazy(later)(data => CacheUpdate(data, state => Some(api.Ready(state)), ReadyHandler))
      case gateway.Resumed(later) =>
        handleLazy(later)(data => CacheUpdate(data, state => Some(api.Resumed(state)), NOOPHandler))
      case gateway.ChannelCreate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getChannel(data.id).map(ch => APIMessage.ChannelCreate(ch, state)).value,
            RawHandlers.rawChannelUpdateHandler
          )
        }
      case gateway.ChannelUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuildChannel(data.id).map(ch => APIMessage.ChannelUpdate(ch, state)).value,
            RawHandlers.rawChannelUpdateHandler
          )
        }
      case gateway.ChannelDelete(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.previous.getChannel(data.id).map(ch => APIMessage.ChannelDelete(ch, state)).value,
            RawHandlers.rawChannelDeleteHandler
          )
        }
      case gateway.ChannelPinsUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getTChannel(data.channelId)
                .map(c => APIMessage.ChannelPinsUpdate(c, data.timestamp.toOption, state))
                .value,
            NOOPHandler
          )
        }
      case gateway.GuildCreate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuild(data.id).map(g => APIMessage.GuildCreate(g, state)).value,
            RawHandlers.rawGuildUpdateHandler
          )
        }
      case gateway.GuildUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuild(data.id).map(g => APIMessage.GuildUpdate(g, state)).value,
            RawHandlers.rawGuildUpdateHandler
          )
        }
      case gateway.GuildDelete(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.previous.getGuild(data.id).map(g => APIMessage.GuildDelete(g, data.unavailable, state)).value,
            RawHandlers.deleteGuildDataHandler
          )
        }
      case gateway.GuildBanAdd(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            (data.guildId, RawBan(None, data.user)),
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => APIMessage.GuildBanAdd(g, data.user, state))
                .value,
            RawHandlers.rawBanUpdateHandler
          )
        }
      case gateway.GuildBanRemove(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => APIMessage.GuildBanRemove(g, data.user, state))
                .value,
            RawHandlers.rawBanDeleteHandler
          )
        }
      case gateway.GuildEmojisUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => APIMessage.GuildEmojiUpdate(g, data.emojis.map(_.toEmoji), state))
                .value,
            RawHandlers.guildEmojisUpdateDataHandler
          )
        }
      case gateway.GuildIntegrationsUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getGuild(data.guildId).map(g => APIMessage.GuildIntegrationsUpdate(g, state)).value,
            NOOPHandler
          )
        }
      case gateway.GuildMemberAdd(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                g   <- state.current.getGuild(data.guildId).value
                mem <- g.members.get(data.user.id)
              } yield APIMessage.GuildMemberAdd(mem, g, state),
            RawHandlers.rawGuildMemberWithGuildUpdateHandler
          )
        }
      case gateway.GuildMemberRemove(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current.getGuild(data.guildId).map(g => APIMessage.GuildMemberRemove(data.user, g, state)).value,
            RawHandlers.rawGuildMemberDeleteHandler
          )
        }
      case gateway.GuildMemberUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map { g =>
                  APIMessage.GuildMemberUpdate(
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
      case gateway.GuildMemberChunk(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => APIMessage.GuildMembersChunk(g, data.members.map(_.toGuildMember(g.id)), state))
                .value,
            RawHandlers.rawGuildMemberChunkHandler
          )
        }
      case gateway.GuildRoleCreate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => APIMessage.GuildRoleCreate(g, data.role.toRole(data.guildId), state))
                .value,
            RawHandlers.roleUpdateHandler
          )
        }
      case gateway.GuildRoleUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => APIMessage.GuildRoleUpdate(g, data.role.toRole(data.guildId), state))
                .value,
            RawHandlers.roleUpdateHandler
          )
        }
      case gateway.GuildRoleDelete(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                previousGuild <- state.previous.getGuild(data.guildId).value
                role          <- previousGuild.roles.get(data.roleId)
              } yield APIMessage.GuildRoleDelete(previousGuild, role, state),
            RawHandlers.roleDeleteHandler
          )
        }
      case gateway.MessageCreate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getMessage(data.id).map(message => APIMessage.MessageCreate(message, state)).value,
            RawHandlers.rawMessageUpdateHandler
          )
        }
      case gateway.MessageUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state => state.current.getMessage(data.id).map(message => APIMessage.MessageUpdate(message, state)).value,
            RawHandlers.rawPartialMessageUpdateHandler
          )
        }
      case gateway.MessageDelete(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                message <- state.previous.getMessage(data.id).value
                channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
              } yield APIMessage.MessageDelete(message, channel, state),
            RawHandlers.rawMessageDeleteHandler
          )
        }
      case gateway.MessageDeleteBulk(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId).map { channel =>
                APIMessage.MessageDeleteBulk(data.ids.flatMap(state.previous.getMessage(_).value.toSeq), channel, state)
            },
            RawHandlers.rawMessageDeleteBulkHandler
          )
        }
      case gateway.MessageReactionAdd(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                user     <- state.current.getUser(data.userId).value
                tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                message  <- state.current.getMessage(data.channelId, data.messageId).value
              } yield APIMessage.MessageReactionAdd(user, tChannel, message, data.emoji, state),
            RawHandlers.rawMessageReactionUpdateHandler
          )
        }
      case gateway.MessageReactionRemove(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                user     <- state.current.getUser(data.userId).value
                tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                message  <- state.current.getMessage(data.channelId, data.messageId).value
              } yield APIMessage.MessageReactionRemove(user, tChannel, message, data.emoji, state),
            RawHandlers.rawMessageReactionRemoveHandler
          )
        }
      case gateway.MessageReactionRemoveAll(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                tChannel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
                message  <- state.current.getMessage(data.channelId, data.messageId).value
              } yield APIMessage.MessageReactionRemoveAll(tChannel, message, state),
            RawHandlers.rawMessageReactionRemoveAllHandler
          )
        }
      case gateway.PresenceUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                guild    <- state.current.getGuild(data.guildId).value
                user     <- state.current.getUser(data.user.id).value
                presence <- guild.presences.get(user.id)
              } yield APIMessage.PresenceUpdate(guild, user, data.roles, presence, state),
            PresenceUpdateHandler
          )
        }
      case gateway.TypingStart(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                user    <- state.current.getUser(data.userId).value
                channel <- getChannelUsingMaybeGuildId(state.current, data.guildId, data.channelId)
              } yield APIMessage.TypingStart(channel, user, data.timestamp, state),
            RawHandlers.lastTypedHandler
          )
        }
      case gateway.UserUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(data, state => Some(APIMessage.UserUpdate(data, state)), RawHandlers.userUpdateHandler)
        }
      case gateway.VoiceStateUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(data, state => Some(APIMessage.VoiceStateUpdate(data, state)), Handlers.voiceStateUpdateHandler)
        }
      case gateway.VoiceServerUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              state.current
                .getGuild(data.guildId)
                .map(g => APIMessage.VoiceServerUpdate(data.token, g, data.endpoint, state))
                .value,
            NOOPHandler
          )
        }
      case gateway.WebhookUpdate(later) =>
        handleLazy(later) { data =>
          CacheUpdate(
            data,
            state =>
              for {
                guild   <- state.current.getGuild(data.guildId).value
                channel <- guild.channels.get(data.channelId)
              } yield APIMessage.WebhookUpdate(guild, channel, state),
            NOOPHandler
          )
        }
    }
  }

}
