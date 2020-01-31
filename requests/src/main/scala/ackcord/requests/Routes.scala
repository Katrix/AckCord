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
package ackcord.requests

import java.net.URLEncoder

import ackcord.AckCord
import ackcord.data._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{HttpMethod, Uri}
import shapeless._

/**
  * All the routes used by AckCord
  */
object Routes {

  val discord = "discordapp.com"

  val base: Route =
    Route(
      s"https://$discord/api/v${AckCord.DiscordApiVersion}",
      s"https://$discord/api/v${AckCord.DiscordApiVersion}",
      s"https://$discord/api/v${AckCord.DiscordApiVersion}"
    )
  val cdn: Route = Route(s"https://cdn.$discord", s"https://cdn.$discord", s"https://cdn.$discord")

  //WS
  val gateway: Route    = base / "gateway"
  val botGateway: Route = gateway / "bot"

  //REST
  type InviteCode = String

  /**
    * Emoji is a bit more complicated than the others.
    * If it's a custom emoji, the format is `name:id` for example `rust:232722868583006209`.
    * If it's a normal emoji, it's encoded using percent encoding, for example `%F0%9F%92%A9`.
    */
  type Emoji = String

  case class Route(uriWithMajor: String, uriWithoutMajor: String, applied: Uri) {
    require(
      uriWithMajor.count(_ == '/') == applied.toString.count(_ == '/'),
      "Raw route and applied route are unbalanced"
    )

    @deprecated("Prefer uriWithMajor", since = "0.15.0")
    def rawRoute: String = uriWithMajor

    def toRequest(method: HttpMethod): RequestRoute = RequestRoute(this, method)

    def /(next: String): Route =
      if (next.isEmpty) this
      else Route(s"$uriWithMajor/$next", s"$uriWithoutMajor/$next", s"$applied/$next")

    def /[A](parameter: MinorParameter[A]): RouteFunction[A] =
      RouteFunction { value =>
        Route(
          s"$uriWithMajor/${parameter.name}",
          s"$uriWithoutMajor/${parameter.name}",
          s"$applied/${parameter.print(value)}"
        )
      }

    def /[A](parameter: MajorParameter[A]): RouteFunction[A] =
      RouteFunction { value =>
        Route(s"$uriWithMajor/$value", s"$uriWithoutMajor/${parameter.name}", s"$applied/${parameter.print(value)}")
      }

    def /(other: Route): Route =
      if (other.uriWithMajor.isEmpty) this
      else
        Route(
          s"$uriWithMajor/${other.uriWithMajor}",
          s"$uriWithoutMajor/${other.uriWithoutMajor}",
          s"$applied/${other.applied}"
        )

    def ++(other: String) = Route(s"$uriWithMajor$other", s"$uriWithoutMajor$other", s"$applied$other")

    def ++[A](parameter: ConcatParameter[A]): RouteFunction[A] =
      RouteFunction { value =>
        Route(
          s"$uriWithMajor${parameter.print(value)}",
          s"$uriWithoutMajor${parameter.print(value)}",
          s"$applied${parameter.print(value)}"
        )
      }

    def +?[A](query: QueryParameter[A]): QueryRouteFunction[Option[A]] =
      QueryRouteFunction {
        case Some(value) =>
          QueryRoute(
            uriWithMajor,
            uriWithoutMajor,
            applied,
            Vector(query.name -> query.print(value))
          )
        case None => QueryRoute(uriWithMajor, uriWithoutMajor, applied, Vector.empty)
      }
  }

  case class QueryRoute(
      uriWithMajor: String,
      uriWithoutMajor: String,
      applied: Uri,
      queryParts: Vector[(String, String)]
  ) {
    require(
      uriWithMajor.count(_ == '/') == applied.toString.count(_ == '/'),
      "Raw route and applied route are unbalanced"
    )

    @deprecated("Prefer uriWithMajor", since = "0.15.0")
    def rawRoute: String = uriWithMajor

    def toRequest(method: HttpMethod): RequestRoute = RequestRoute(this, method)

    def ++(other: String) = QueryRoute(s"$uriWithMajor$other", s"$uriWithoutMajor$other", s"$applied$other", queryParts)

    def ++[A](parameter: ConcatParameter[A]): QueryRouteFunction[A] =
      QueryRouteFunction { value =>
        QueryRoute(
          s"$uriWithMajor${parameter.print(value)}",
          s"$uriWithoutMajor${parameter.print(value)}",
          s"$applied${parameter.print(value)}",
          queryParts
        )
      }

    def +?[A](query: QueryParameter[A]): QueryRouteFunction[Option[A]] =
      QueryRouteFunction {
        case Some(value) =>
          QueryRoute(
            uriWithMajor,
            uriWithoutMajor,
            applied,
            queryParts :+ (query.name -> query.print(value))
          )
        case None => this
      }
  }

  case class RouteFunction[A](route: A => Route) {

    def toRequest[Repr <: HList](
        method: HttpMethod
    )(
        implicit unflatten: FlattenUnflatten[A, Repr],
        toFn: shapeless.ops.function.FnFromProduct[Repr => RequestRoute]
    ): toFn.Out = {
      val f = (repr: Repr) => route(unflatten.toIn(repr)).toRequest(method)
      toFn.apply(f)
    }

    def /(next: String): RouteFunction[A] = RouteFunction(route.andThen(_ / next))

    def /[B](parameter: MinorParameter[B]): RouteFunction[(A, B)] = {
      val uncurried = Function.uncurried(route.andThen(_ / parameter).andThen(_.route))
      RouteFunction(uncurried.tupled)
    }

    def /[B](parameter: MajorParameter[B]): RouteFunction[(A, B)] = {
      val uncurried = Function.uncurried(route.andThen(_ / parameter).andThen(_.route))
      RouteFunction(uncurried.tupled)
    }

    def /(other: Route): RouteFunction[A] = RouteFunction(route.andThen(_ / other))

    def ++(other: String): RouteFunction[A] = RouteFunction(route.andThen(_ ++ other))

    def ++[B](parameter: ConcatParameter[B]): RouteFunction[(A, B)] = {
      val uncurried = Function.uncurried(route.andThen(_ ++ parameter).andThen(_.route))
      RouteFunction(uncurried.tupled)
    }

    def +?[B](query: QueryParameter[B]): QueryRouteFunction[(A, Option[B])] = {
      val uncurried = Function.uncurried(route.andThen(_ +? query).andThen(_.route))
      QueryRouteFunction(uncurried.tupled)
    }
  }

  case class QueryRouteFunction[A](route: A => QueryRoute) {
    def toRequest[Repr <: HList](
        method: HttpMethod
    )(
        implicit unflatten: FlattenUnflatten[A, Repr],
        toFn: shapeless.ops.function.FnFromProduct[Repr => RequestRoute]
    ): toFn.Out = {
      val f = (repr: Repr) => route(unflatten.toIn(repr)).toRequest(method)
      toFn.apply(f)
    }

    def ++(other: String): QueryRouteFunction[A] = QueryRouteFunction(route.andThen(_ ++ other))

    def ++[B](parameter: ConcatParameter[B]): QueryRouteFunction[(A, B)] = {
      val uncurried = Function.uncurried(route.andThen(_ ++ parameter).andThen(_.route))
      QueryRouteFunction(uncurried.tupled)
    }

    def +?[B](query: QueryParameter[B]): QueryRouteFunction[(A, Option[B])] = {
      val uncurried = Function.uncurried(route.andThen(_ +? query).andThen(_.route))
      QueryRouteFunction(uncurried.tupled)
    }
  }

  class MajorParameter[A](val name: String, val print: A => String)
  class MinorParameter[A](val name: String, val print: A => String)
  class QueryParameter[A](val name: String, val print: A => String)
  class ConcatParameter[A](val print: A => String)

  def query[A](name: String, print: A => String, requireFn: A => Unit = (_: A) => ()): QueryParameter[A] = {
    val realPrint: A => String = a => {
      requireFn(a)
      print(a)
    }
    new QueryParameter[A](name, realPrint)
  }

  def upcast[A, B >: A](a: A): B = a

  val guildId: MajorParameter[GuildId]                  = new MajorParameter("guildId", _.asString)
  val channelId: MajorParameter[ChannelId]              = new MajorParameter("channelId", _.asString)
  val webhookId: MajorParameter[SnowflakeType[Webhook]] = new MajorParameter("webhookId", _.asString)

  val messageId: MinorParameter[MessageId]                = new MinorParameter("messageId", _.asString)
  val emoji: MinorParameter[Emoji]                        = new MinorParameter("emoji", URLEncoder.encode(_, "UTF-8"))
  val emojiId: MinorParameter[EmojiId]                    = new MinorParameter("emojiId", _.asString)
  val userId: MinorParameter[UserId]                      = new MinorParameter("userId", _.asString)
  val permissionOverwriteId: MinorParameter[UserOrRoleId] = new MinorParameter("permissionOverwriteId", _.asString)
  val roleId: MinorParameter[RoleId]                      = new MinorParameter("roleId", _.asString)
  val integrationId: MinorParameter[IntegrationId]        = new MinorParameter("integrationId", _.asString)
  val inviteCodeParam: MinorParameter[String]             = new MinorParameter("inviteCode", identity)
  val token: MinorParameter[String]                       = new MinorParameter("token", identity)
  val hash: MinorParameter[String]                        = new MinorParameter("hash", identity)
  val applicationId: MinorParameter[RawSnowflake]         = new MinorParameter("applicationId", _.asString)
  val teamId: MinorParameter[SnowflakeType[Team]]         = new MinorParameter("teamId", _.asString)

  //Audit log

  val guilds: Route                 = base / "guilds"
  val guild: RouteFunction[GuildId] = guilds / guildId

  val getGuildAuditLogs: (
      GuildId,
      Option[UserId],
      Option[AuditLogEvent],
      Option[RawSnowflake],
      Option[Int]
  ) => RequestRoute = {
    val base = guild / "audit-logs"
    val queries = base +?
      query[UserId]("user_id", _.asString) +?
      query[AuditLogEvent]("action_type", _.value.toString) +?
      query[RawSnowflake]("before", _.asString) +?
      query[Int]("limit", _.toString, a => require(a >= 1 && a <= 100, "Limit must be between 1 and 100"))

    upcast(queries.toRequest(GET))
  }

  //Channel routes

  val channel: RouteFunction[ChannelId] = base / "channels" / channelId

  val getChannel: ChannelId => RequestRoute         = upcast(channel.toRequest(GET))
  val modifyChannel: ChannelId => RequestRoute      = upcast(channel.toRequest(PATCH))
  val deleteCloseChannel: ChannelId => RequestRoute = upcast(channel.toRequest(DELETE))

  val channelMessages: RouteFunction[ChannelId]             = channel / "messages"
  val channelMessage: RouteFunction[(ChannelId, MessageId)] = channelMessages / messageId

  //We handle queries later for this one to ensure mutually exclusive params
  val getChannelMessages: ChannelId => RequestRoute             = upcast(channelMessages.toRequest(GET))
  val getChannelMessage: (ChannelId, MessageId) => RequestRoute = upcast(channelMessage.toRequest(GET))
  val createMessage: ChannelId => RequestRoute                  = upcast(channelMessages.toRequest(POST))
  val editMessage: (ChannelId, MessageId) => RequestRoute       = upcast(channelMessage.toRequest(PATCH))
  val deleteMessage: (ChannelId, MessageId) => RequestRoute     = upcast(channelMessage.toRequest(DELETE))
  val bulkDeleteMessages: ChannelId => RequestRoute             = upcast(channelMessages / "bulk-delete" toRequest POST)

  val reactions: RouteFunction[(ChannelId, MessageId)]                 = channelMessage / "reactions"
  val emojiReactions: RouteFunction[((ChannelId, MessageId), Emoji)]   = reactions / emoji
  val modifyMeReaction: RouteFunction[((ChannelId, MessageId), Emoji)] = emojiReactions / "@me"

  val createReaction: (ChannelId, MessageId, Emoji) => RequestRoute    = upcast(modifyMeReaction.toRequest(PUT))
  val deleteOwnReaction: (ChannelId, MessageId, Emoji) => RequestRoute = upcast(modifyMeReaction.toRequest(DELETE))
  val deleteUserReaction: (ChannelId, MessageId, Emoji, UserId) => RequestRoute = upcast(
    emojiReactions / userId toRequest DELETE
  )

  val getReactions: (ChannelId, MessageId, Emoji, Option[UserId], Option[UserId], Option[Int]) => RequestRoute = {
    val queries = emojiReactions +?
      query[UserId]("before", _.asString) +?
      query[UserId]("after", _.asString) +?
      query[Int]("limit", _.toString, a => require(a >= 1 && a <= 100, "Limit must be between 1 and 100"))

    upcast(queries.toRequest(GET))
  }
  val deleteAllReactions: (ChannelId, MessageId) => RequestRoute = upcast(reactions.toRequest(DELETE))

  val channelPermissions: RouteFunction[(ChannelId, UserOrRoleId)] = channel / "permissions" / permissionOverwriteId

  val editChannelPermissions: (ChannelId, UserOrRoleId) => RequestRoute   = upcast(channelPermissions.toRequest(PUT))
  val deleteChannelPermissions: (ChannelId, UserOrRoleId) => RequestRoute = upcast(channelPermissions.toRequest(DELETE))

  val channelInvites: RouteFunction[ChannelId] = channel / "invites"

  val getChannelInvites: ChannelId => RequestRoute    = upcast(channelInvites.toRequest(GET))
  val createChannelInvites: ChannelId => RequestRoute = upcast(channelInvites.toRequest(POST))

  val triggerTyping: ChannelId => RequestRoute = upcast(channel / "typing" toRequest POST)

  val pinnedMessage: RouteFunction[ChannelId]     = channel / "pins"
  val getPinnedMessage: ChannelId => RequestRoute = upcast(pinnedMessage.toRequest(GET))

  val channelPinnedMessage: RouteFunction[(ChannelId, MessageId)]     = pinnedMessage / messageId
  val addPinnedChannelMessage: (ChannelId, MessageId) => RequestRoute = upcast(channelPinnedMessage.toRequest(PUT))
  val deletePinnedChannelMessage: (ChannelId, MessageId) => RequestRoute = upcast(
    channelPinnedMessage.toRequest(DELETE)
  )

  val groupDmRecipient: RouteFunction[(ChannelId, UserId)]        = channel / userId
  val groupDmAddRecipient: (ChannelId, UserId) => RequestRoute    = upcast(groupDmRecipient.toRequest(PUT))
  val groupDmRemoveRecipient: (ChannelId, UserId) => RequestRoute = upcast(groupDmRecipient.toRequest(DELETE))

  //Emoji routes

  val guildEmojis: RouteFunction[GuildId]       = guild / "emojis"
  val listGuildEmojis: GuildId => RequestRoute  = upcast(guildEmojis.toRequest(GET))
  val createGuildEmoji: GuildId => RequestRoute = upcast(guildEmojis.toRequest(POST))

  val guildEmoji: RouteFunction[(GuildId, EmojiId)]        = guildEmojis / emojiId
  val getGuildEmoji: (GuildId, EmojiId) => RequestRoute    = upcast(guildEmoji.toRequest(GET))
  val modifyGuildEmoji: (GuildId, EmojiId) => RequestRoute = upcast(guildEmoji.toRequest(PATCH))
  val deleteGuildEmoji: (GuildId, EmojiId) => RequestRoute = upcast(guildEmoji.toRequest(DELETE))

  //Guild routes
  val createGuild: RequestRoute            = upcast(guilds.toRequest(POST))
  val getGuild: GuildId => RequestRoute    = upcast(guild.toRequest(GET))
  val modifyGuild: GuildId => RequestRoute = upcast(guild.toRequest(PATCH))
  val deleteGuild: GuildId => RequestRoute = upcast(guild.toRequest(DELETE))

  val guildChannels: RouteFunction[GuildId]                 = guild / "channels"
  val getGuildChannels: GuildId => RequestRoute             = upcast(guildChannels.toRequest(GET))
  val createGuildChannel: GuildId => RequestRoute           = upcast(guildChannels.toRequest(POST))
  val modifyGuildChannelsPositions: GuildId => RequestRoute = upcast(guildChannels.toRequest(PATCH))

  val guildMembers: RouteFunction[GuildId]              = guild / "members"
  val guildMember: RouteFunction[(GuildId, UserId)]     = guildMembers / userId
  val getGuildMember: (GuildId, UserId) => RequestRoute = upcast(guildMember.toRequest(GET))
  val listGuildMembers: (GuildId, Option[Int], Option[UserId]) => RequestRoute = {
    val queries = guildMembers +?
      query[Int]("limit", _.toString, a => require(a >= 1 && a <= 1000, "Limit must be between 1 and 1000")) +?
      query[UserId]("after", _.asString)

    upcast(queries.toRequest(GET))
  }
  val addGuildMember: (GuildId, UserId) => RequestRoute    = upcast(guildMember.toRequest(PUT))
  val modifyGuildMember: (GuildId, UserId) => RequestRoute = upcast(guildMember.toRequest(PATCH))
  val removeGuildMember: (GuildId, UserId) => RequestRoute = upcast(guildMember.toRequest(DELETE))
  val modifyCurrentNick: GuildId => RequestRoute           = upcast(guildMembers / "@me" / "nick" toRequest PATCH)

  val guildMemberRole: RouteFunction[((GuildId, UserId), RoleId)] = guildMember / "roles" / roleId

  val addGuildMemberRole: (GuildId, UserId, RoleId) => RequestRoute    = upcast(guildMemberRole.toRequest(PUT))
  val removeGuildMemberRole: (GuildId, UserId, RoleId) => RequestRoute = upcast(guildMemberRole.toRequest(DELETE))

  val guildBans: RouteFunction[GuildId]                = guild / "bans"
  val guildMemberBan: RouteFunction[(GuildId, UserId)] = guildBans / userId

  val getGuildBans: GuildId => RequestRoute          = upcast(guildBans.toRequest(GET))
  val getGuildBan: (GuildId, UserId) => RequestRoute = upcast(guildMemberBan.toRequest(GET))
  val createGuildMemberBan: (GuildId, UserId, Option[Int], Option[String]) => RequestRoute = {
    val queries = guildMemberBan +?
      query[Int](
        "delete-message-days",
        _.toString,
        a => require(a >= 0 && a <= 7, "Delete message days must be between 0 and 7")
      ) +?
      query[String]("reason", identity)

    upcast(queries.toRequest(PUT))
  }
  val removeGuildMemberBan: (GuildId, UserId) => RequestRoute = upcast(guildMemberBan.toRequest(DELETE))

  val guildRoles: RouteFunction[GuildId]                = guild / "roles"
  val getGuildRole: GuildId => RequestRoute             = upcast(guildRoles.toRequest(GET))
  val createGuildRole: GuildId => RequestRoute          = upcast(guildRoles.toRequest(POST))
  val modifyGuildRolePositions: GuildId => RequestRoute = upcast(guildRoles.toRequest(PATCH))

  val guildRole: RouteFunction[(GuildId, RoleId)]        = guildRoles / roleId
  val modifyGuildRole: (GuildId, RoleId) => RequestRoute = upcast(guildRole.toRequest(PATCH))
  val deleteGuildRole: (GuildId, RoleId) => RequestRoute = upcast(guildRole.toRequest(DELETE))

  val daysQuery: QueryParameter[Int] =
    query[Int]("days", _.toString, a => require(a >= 1, "Can't prune for zero or negative days"))

  val guildPrune: QueryRouteFunction[(GuildId, Option[Int])]     = guild / "prune" +? daysQuery
  val getGuildPruneCount: (GuildId, Option[Int]) => RequestRoute = upcast(guildPrune.toRequest(GET))
  val beginGuildPrune: (GuildId, Option[Int], Option[Boolean]) => RequestRoute = {
    upcast(guildPrune +? query[Boolean]("compute_prune_count", _.toString) toRequest POST)
  }

  val getGuildVoiceRegions: GuildId => RequestRoute = upcast(guild / "regions" toRequest GET)
  val getGuildInvites: GuildId => RequestRoute      = upcast(guild / "invites" toRequest GET)

  val guildIntegrations: RouteFunction[GuildId]        = guild / "integrations"
  val getGuildIntegrations: GuildId => RequestRoute    = upcast(guildIntegrations.toRequest(GET))
  val createGuildIntegrations: GuildId => RequestRoute = upcast(guildIntegrations.toRequest(POST))

  val guildIntegration: RouteFunction[(GuildId, IntegrationId)]        = guildIntegrations / integrationId
  val modifyGuildIntegration: (GuildId, IntegrationId) => RequestRoute = upcast(guildIntegration.toRequest(PATCH))
  val deleteGuildIntegration: (GuildId, IntegrationId) => RequestRoute = upcast(guildIntegration.toRequest(DELETE))
  val syncGuildIntegration: (GuildId, IntegrationId) => RequestRoute   = upcast(guildIntegration / "sync" toRequest PATCH)

  val guildEmbed: RouteFunction[GuildId]         = guild / "embed"
  val getGuildEmbed: GuildId => RequestRoute     = upcast(guildEmbed.toRequest(GET))
  val modifyGuildEmbed: GuildId => RequestRoute  = upcast(guildEmbed.toRequest(PATCH))
  val getGuildVanityUrl: GuildId => RequestRoute = upcast(guild / "vanity-url" toRequest GET)

  val style: QueryParameter[WidgetImageStyle] = new QueryParameter("style", _.value)

  val getGuildWidgetImage: (GuildId, Option[WidgetImageStyle]) => RequestRoute = upcast(
    guild / "widget.png" +? style toRequest GET
  )

  //Invites
  val invites: Route                           = base / "invites"
  val inviteCode: RouteFunction[String]        = invites / inviteCodeParam
  val getInvite: InviteCode => RequestRoute    = upcast(inviteCode.toRequest(GET))
  val deleteInvite: InviteCode => RequestRoute = upcast(inviteCode.toRequest(DELETE))

  //Users
  val users: Route                 = base / "users"
  val currentUser: Route           = users / "@me"
  val getCurrentUser: RequestRoute = upcast(currentUser.toRequest(GET))

  val getUser: UserId => RequestRoute = upcast(users / userId toRequest GET)
  val modifyCurrentUser: RequestRoute = upcast(currentUser.toRequest(PATCH))
  val currentUserGuilds: Route        = currentUser / "guilds"
  val getCurrentUserGuilds: (Option[GuildId], Option[GuildId], Option[Int]) => RequestRoute = {
    val queries = currentUserGuilds +?
      query[GuildId]("before", _.asString) +?
      query[GuildId]("after", _.asString) +?
      query[Int]("limit", _.toString, a => require(a >= 1 && a <= 100, "Limit must be between 1 and 100"))

    upcast(queries.toRequest(GET))
  }
  val leaveGuild: GuildId => RequestRoute = upcast(currentUserGuilds / guildId toRequest DELETE)

  val userDMs: Route           = currentUser / "channels"
  val getUserDMs: RequestRoute = upcast(userDMs.toRequest(GET))
  val createDM: RequestRoute   = upcast(userDMs.toRequest(POST))

  val getUserConnections: RequestRoute = upcast(currentUser / "connections" toRequest GET)

  //Voice
  val listVoiceRegions: RequestRoute = upcast(base / "voice" / "regions" toRequest GET)

  //WebHook
  val webhook: RouteFunction[SnowflakeType[Webhook]]                    = base / "webhooks" / webhookId
  val webhookWithToken: RouteFunction[(SnowflakeType[Webhook], String)] = webhook / token
  val channelWebhooks: RouteFunction[ChannelId]                         = channel / "webhooks"

  val createWebhook: ChannelId => RequestRoute                              = upcast(channelWebhooks.toRequest(POST))
  val getChannelWebhooks: ChannelId => RequestRoute                         = upcast(channelWebhooks.toRequest(GET))
  val getGuildWebhooks: GuildId => RequestRoute                             = upcast(guild / "webhooks" toRequest GET)
  val getWebhook: SnowflakeType[Webhook] => RequestRoute                    = upcast(webhook.toRequest(GET))
  val getWebhookWithToken: (SnowflakeType[Webhook], String) => RequestRoute = upcast(webhookWithToken.toRequest(GET))
  val modifyWebhook: SnowflakeType[Webhook] => RequestRoute                 = upcast(webhook.toRequest(PATCH))
  val modifyWebhookWithToken: (SnowflakeType[Webhook], String) => RequestRoute = upcast(
    webhookWithToken.toRequest(PATCH)
  )
  val deleteWebhook: SnowflakeType[Webhook] => RequestRoute = upcast(webhook.toRequest(DELETE))
  val deleteWebhookWithToken: (SnowflakeType[Webhook], String) => RequestRoute = upcast(
    webhookWithToken.toRequest(DELETE)
  )

  val waitQuery: QueryParameter[Boolean] = query[Boolean]("wait", _.toString)

  val executeWebhook: (SnowflakeType[Webhook], InviteCode, Option[Boolean]) => RequestRoute = upcast(
    webhookWithToken +? waitQuery toRequest POST
  )
  val executeSlackWebhook: (SnowflakeType[Webhook], InviteCode, Option[Boolean]) => RequestRoute = upcast(
    webhookWithToken / "slack" +? waitQuery toRequest POST
  )
  val executeGithubWebhook: (SnowflakeType[Webhook], InviteCode, Option[Boolean]) => RequestRoute = upcast(
    webhookWithToken / "github" +? waitQuery toRequest POST
  )

  val size: QueryParameter[Int]               = new QueryParameter("size", _.toString)
  val extension: ConcatParameter[ImageFormat] = new ConcatParameter(_.extension)
  val discriminator: MinorParameter[Int]      = new MinorParameter("discriminator", i => (i % 5).toString)

  //Images
  val emojiImage: (EmojiId, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "emojis" / emojiId ++ extension +? size toRequest GET
  )
  val guildIconImage: (GuildId, String, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "icons" / guildId / hash ++ extension +? size toRequest GET
  )
  val guildSplashImage: (GuildId, String, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "splashes" / guildId / hash ++ extension +? size toRequest GET
  )
  val discoverySplashImage: (GuildId, String, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "splashes" / guildId / hash ++ extension +? size toRequest GET
  )
  val guildBannerImage: (GuildId, String, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "banners" / guildId / hash ++ extension +? size toRequest GET
  )
  val defaultUserAvatarImage: (Int, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "embed" / "avatars" / discriminator ++ extension +? size toRequest GET
  )
  val userAvatarImage: (UserId, String, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "avatars" / userId / hash ++ extension +? size toRequest GET
  )
  val applicationIconImage: (RawSnowflake, String, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "app-icons" / applicationId / hash ++ extension +? size toRequest GET
  )
  val applicationAssetImage: (RawSnowflake, String, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "app-assets" / applicationId / hash ++ extension +? size toRequest GET
  )

  val teamIconImage: (SnowflakeType[Team], String, ImageFormat, Option[Int]) => RequestRoute = upcast(
    cdn / "team-icons" / teamId / hash ++ extension +? size toRequest GET
  )

  //OAuth
  val oAuth2: Route                       = base / "oauth2"
  val oAuth2Authorize: Route              = oAuth2 / "authorize"
  val oAuth2Token: Route                  = oAuth2 / "token"
  val oAuth2Revoke: Route                 = oAuth2Token / "revoke"
  val getCurrentApplication: RequestRoute = (oAuth2 / "applications" / "@me").toRequest(GET)
}
