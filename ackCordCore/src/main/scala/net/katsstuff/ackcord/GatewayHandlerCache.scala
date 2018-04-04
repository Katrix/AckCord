package net.katsstuff.ackcord

import akka.actor.Props
import akka.http.scaladsl.model.Uri
import net.katsstuff.ackcord.cachehandlers.{Handlers, NOOPHandler, PresenceUpdateHandler, RawHandlers, ReadyHandler}
import net.katsstuff.ackcord.data.raw.RawBan
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.websocket.gateway.{ComplexGatewayEvent, Dispatch, GatewayHandler, GatewaySettings}

object GatewayHandlerCache {
  def props(wsUri: Uri, settings: GatewaySettings, cache: Cache): Props = {
    import cache.mat

    val sink = cache.publish.contramap { (dispatch: Dispatch[_]) =>
      eventToCacheUpdate(dispatch.event).asInstanceOf[CacheUpdate[Any]]
    }

    Props(new GatewayHandler(wsUri, settings, cache.gatewaySubscribe, sink))
  }

  def eventToCacheUpdate(event: ComplexGatewayEvent[_, _]): APIMessageCacheUpdate[_] = {
    import net.katsstuff.ackcord.websocket.gateway.{GatewayEvent => gateway}
    import net.katsstuff.ackcord.{APIMessage => api, APIMessageCacheUpdate => CacheUpdate}

    event match {
      case gateway.Ready(data)   => CacheUpdate(data, state => Some(api.Ready(state)), ReadyHandler)
      case gateway.Resumed(data) => CacheUpdate(data, state => Some(api.Resumed(state)), NOOPHandler)
      case gateway.ChannelCreate(data) =>
        CacheUpdate(
          data,
          state => state.current.getChannel(data.id).map(ch => APIMessage.ChannelCreate(ch, state)),
          RawHandlers.rawChannelUpdateHandler
        )
      case gateway.ChannelUpdate(data) =>
        CacheUpdate(
          data,
          state => state.current.getGuildChannel(data.id).map(ch => APIMessage.ChannelUpdate(ch, state)),
          RawHandlers.rawChannelUpdateHandler
        )
      case gateway.ChannelDelete(data) =>
        CacheUpdate(
          data,
          state => state.previous.getChannel(data.id).map(ch => APIMessage.ChannelDelete(ch, state)),
          RawHandlers.rawChannelDeleteHandler
        )
      case gateway.ChannelPinsUpdate(data) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getTChannel(data.channelId)
              .map(c => APIMessage.ChannelPinsUpdate(c, data.timestamp, state)),
          NOOPHandler
        )
      case gateway.GuildCreate(data) =>
        CacheUpdate(
          data,
          state => state.current.getGuild(data.id).map(g => APIMessage.GuildCreate(g, state)),
          RawHandlers.rawGuildUpdateHandler
        )
      case gateway.GuildUpdate(data) =>
        CacheUpdate(
          data,
          state => state.current.getGuild(data.id).map(g => APIMessage.GuildUpdate(g, state)),
          RawHandlers.rawGuildUpdateHandler
        )
      case gateway.GuildDelete(data) =>
        CacheUpdate(
          data,
          state => state.previous.getGuild(data.id).map(g => APIMessage.GuildDelete(g, data.unavailable, state)),
          RawHandlers.deleteGuildDataHandler
        )
      case gateway.GuildBanAdd(data) =>
        CacheUpdate(
          (data.head, RawBan(None, gateway.userGen.from(data.tail))),
          state =>
            state.current
              .getGuild(data.head)
              .map(g => APIMessage.GuildBanAdd(g, gateway.userGen.from(data.tail), state)),
          RawHandlers.rawBanUpdateHandler
        )
      case gateway.GuildBanRemove(data) =>
        CacheUpdate(
          (data.head, gateway.userGen.from(data.tail)),
          state =>
            state.current
              .getGuild(data.head)
              .map(g => APIMessage.GuildBanRemove(g, gateway.userGen.from(data.tail), state)),
          RawHandlers.rawBanDeleteHandler
        )
      case gateway.GuildEmojisUpdate(data) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map(g => APIMessage.GuildEmojiUpdate(g, data.emojis.map(_.toEmoji), state)),
          RawHandlers.guildEmojisUpdateDataHandler
        )
      case gateway.GuildIntegrationsUpdate(data) =>
        CacheUpdate(
          data,
          state => state.current.getGuild(data.guildId).map(g => APIMessage.GuildIntegrationsUpdate(g, state)),
          NOOPHandler
        )
      case gateway.GuildMemberAdd(data) =>
        CacheUpdate(
          data,
          state =>
            for {
              g   <- state.current.getGuild(data.guildId)
              mem <- g.members.get(data.user.id)
            } yield APIMessage.GuildMemberAdd(mem, g, state),
          RawHandlers.rawGuildMemberWithGuildUpdateHandler
        )
      case gateway.GuildMemberRemove(data) =>
        CacheUpdate(
          data,
          state => state.current.getGuild(data.guildId).map(g => APIMessage.GuildMemberRemove(data.user, g, state)),
          RawHandlers.rawGuildMemberDeleteHandler
        )
      case gateway.GuildMemberUpdate(data) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map { g =>
                APIMessage.GuildMemberUpdate(
                  g,
                  data.roles.flatMap(state.current.getRole(data.guildId, _)),
                  data.user,
                  data.nick,
                  state
                )
              },
          RawHandlers.rawGuildMemberUpdateHandler
        )
      case gateway.GuildMemberChunk(data) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map(g => APIMessage.GuildMembersChunk(g, data.members.map(_.toGuildMember(g.id)), state)),
          RawHandlers.rawGuildMemberChunkHandler
        )
      case gateway.GuildRoleCreate(data) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map(g => APIMessage.GuildRoleCreate(g, data.role.toRole(data.guildId), state)),
          RawHandlers.roleUpdateHandler
        )
      case gateway.GuildRoleUpdate(data) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map(g => APIMessage.GuildRoleUpdate(g, data.role.toRole(data.guildId), state)),
          RawHandlers.roleUpdateHandler
        )
      case gateway.GuildRoleDelete(data) =>
        CacheUpdate(
          data,
          state =>
            for {
              previousGuild <- state.previous.getGuild(data.guildId)
              role          <- previousGuild.roles.get(data.roleId)
            } yield APIMessage.GuildRoleDelete(previousGuild, role, state),
          RawHandlers.roleDeleteHandler
        )
      case gateway.MessageCreate(data) =>
        CacheUpdate(
          data,
          state => state.current.getMessage(data.id).map(message => APIMessage.MessageCreate(message, state)),
          RawHandlers.rawMessageUpdateHandler
        )
      case gateway.MessageUpdate(data) =>
        CacheUpdate(
          data,
          state => state.current.getMessage(data.id).map(message => APIMessage.MessageUpdate(message, state)),
          RawHandlers.rawPartialMessageUpdateHandler
        )
      case gateway.MessageDelete(data) =>
        CacheUpdate(
          data,
          state =>
            for {
              message <- state.previous.getMessage(data.id)
              channel <- state.current.getTChannel(data.channelId)
            } yield APIMessage.MessageDelete(message, channel, state),
          RawHandlers.rawMessageDeleteHandler
        )
      case gateway.MessageDeleteBulk(data) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getTChannel(data.channelId)
              .map { channel =>
                APIMessage.MessageDeleteBulk(data.ids.flatMap(state.previous.getMessage(_).toSeq), channel, state)
              },
          RawHandlers.rawMessageDeleteBulkHandler
        )
      case gateway.MessageReactionAdd(data) =>
        CacheUpdate(
          data,
          state =>
            for {
              user     <- state.current.getUser(data.userId)
              tChannel <- state.current.getTChannel(data.channelId)
              message  <- state.current.getMessage(data.channelId, data.messageId)
            } yield APIMessage.MessageReactionAdd(user, tChannel, message, data.emoji, state),
          RawHandlers.rawMessageReactionUpdateHandler
        )
      case gateway.MessageReactionRemove(data) =>
        CacheUpdate(
          data,
          state =>
            for {
              user     <- state.current.getUser(data.userId)
              tChannel <- state.current.getTChannel(data.channelId)
              message  <- state.current.getMessage(data.channelId, data.messageId)
            } yield APIMessage.MessageReactionRemove(user, tChannel, message, data.emoji, state),
          RawHandlers.rawMessageReactionRemoveHandler
        )
      case gateway.MessageReactionRemoveAll(data) =>
        CacheUpdate(
          data,
          state =>
            for {
              tChannel <- state.current.getChannel(data.channelId).flatMap(_.asTChannel)
              message  <- state.current.getMessage(data.channelId, data.messageId)
            } yield APIMessage.MessageReactionRemoveAll(tChannel, message, state),
          RawHandlers.rawMessageReactionRemoveAllHandler
        )
      case gateway.PresenceUpdate(data) =>
        CacheUpdate(
          data,
          state =>
            for {
              guild    <- state.current.getGuild(data.guildId)
              user     <- state.current.getUser(data.user.id)
              presence <- guild.presences.get(user.id)
            } yield APIMessage.PresenceUpdate(guild, user, data.roles, presence, state),
          PresenceUpdateHandler
        )
      case gateway.TypingStart(data) =>
        CacheUpdate(
          data,
          state =>
            for {
              user    <- state.current.getUser(data.userId)
              channel <- state.current.getTChannel(data.channelId)
            } yield APIMessage.TypingStart(channel, user, data.timestamp, state),
          RawHandlers.lastTypedHandler
        )
      case gateway.UserUpdate(data) =>
        CacheUpdate(data, state => Some(APIMessage.UserUpdate(data, state)), RawHandlers.userUpdateHandler)
      case gateway.VoiceStateUpdate(data) =>
        CacheUpdate(data, state => Some(APIMessage.VoiceStateUpdate(data, state)), Handlers.voiceStateUpdateHandler)
      case gateway.VoiceServerUpdate(data) =>
        CacheUpdate(
          data,
          state =>
            state.current
              .getGuild(data.guildId)
              .map(g => APIMessage.VoiceServerUpdate(data.token, g, data.endpoint, state)),
          NOOPHandler
        )
      case gateway.WebhookUpdate(data) =>
        CacheUpdate(
          data,
          state =>
            for {
              guild   <- state.current.getGuild(data.guildId)
              channel <- guild.channels.get(data.channelId)
            } yield APIMessage.WebhookUpdate(guild, channel, state),
          NOOPHandler
        )
    }
  }

}
