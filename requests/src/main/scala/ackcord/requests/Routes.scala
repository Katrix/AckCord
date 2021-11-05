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
import java.time.OffsetDateTime

import ackcord.AckCord
import ackcord.data._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{HttpMethod, Uri}
import shapeless._

/** All the routes used by AckCord */
object Routes {

  val discord    = "discord.com"
  val discordCdn = "cdn.discordapp.com"

  val base: Route =
    Route(
      s"https://$discord/api/v${AckCord.DiscordApiVersion}",
      s"https://$discord/api/v${AckCord.DiscordApiVersion}",
      s"https://$discord/api/v${AckCord.DiscordApiVersion}"
    )
  val cdn: Route = Route(s"https://$discordCdn", s"https://$discordCdn", s"https://$discordCdn")

  //WS
  val gateway: Route    = base / "gateway"
  val botGateway: Route = gateway / "bot"

  //REST
  type InviteCode = String

  /**
    * Emoji is a bit more complicated than the others. If it's a custom emoji,
    * the format is `name:id` for example `rust:232722868583006209`. If it's a
    * normal emoji, it's encoded using percent encoding, for example
    * `%F0%9F%92%A9`.
    */
  type Emoji = String

  case class Route(uriWithMajor: String, uriWithoutMajor: String, applied: Uri) {
    require(
      uriWithMajor.count(_ == '/') == applied.toString.count(_ == '/'),
      "Raw route and applied route are unbalanced"
    )

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

    def ++?[A](query: SeqQueryParameter[A]): QueryRouteFunction[Option[Seq[A]]] =
      QueryRouteFunction {
        case None | Some(Nil) => QueryRoute(uriWithMajor, uriWithoutMajor, applied, Vector.empty)
        case Some(values) =>
          QueryRoute(
            uriWithMajor,
            uriWithoutMajor,
            applied,
            values.map(value => query.name -> query.print(value)).toVector
          )
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

    def ++?[A](query: SeqQueryParameter[A]): QueryRouteFunction[Option[Seq[A]]] =
      QueryRouteFunction {
        case None | Some(Nil) => this
        case Some(values) =>
          QueryRoute(
            uriWithMajor,
            uriWithoutMajor,
            applied,
            queryParts ++ values.map(value => query.name -> query.print(value))
          )
      }
  }

  case class RouteFunction[A](route: A => Route) {

    def toRequest[Repr <: HList, Res](
        method: HttpMethod
    )(
        implicit unflatten: FlattenUnflatten[A, Repr],
        toFn: shapeless.ops.function.FnFromProduct.Aux[Repr => RequestRoute, Res]
    ): Res = {
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

    def ++?[B](query: SeqQueryParameter[B]): QueryRouteFunction[(A, Option[Seq[B]])] = {
      val uncurried = Function.uncurried(route.andThen(_ ++? query).andThen(_.route))
      QueryRouteFunction(uncurried.tupled)
    }
  }

  case class QueryRouteFunction[A](route: A => QueryRoute) {
    def toRequest[Repr <: HList, Res](
        method: HttpMethod
    )(
        implicit unflatten: FlattenUnflatten[A, Repr],
        toFn: shapeless.ops.function.FnFromProduct.Aux[Repr => RequestRoute, Res]
    ): Res = {
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

    def ++?[B](query: SeqQueryParameter[B]): QueryRouteFunction[(A, Option[Seq[B]])] = {
      val uncurried = Function.uncurried(route.andThen(_ ++? query).andThen(_.route))
      QueryRouteFunction(uncurried.tupled)
    }
  }

  class MajorParameter[A](val name: String, val print: A => String)
  class MinorParameter[A](val name: String, val print: A => String)
  class QueryParameter[A](val name: String, val print: A => String)
  class SeqQueryParameter[A](val name: String, val print: A => String)
  class ConcatParameter[A](val print: A => String)

  def query[A](name: String, print: A => String, requireFn: A => Unit = (_: A) => ()): QueryParameter[A] = {
    val realPrint: A => String = a => {
      requireFn(a)
      print(a)
    }
    new QueryParameter[A](name, realPrint)
  }

  def seqQuery[A](name: String, print: A => String, requireFn: A => Unit = (_: A) => ()): SeqQueryParameter[A] = {
    val realPrint: A => String = a => {
      requireFn(a)
      print(a)
    }
    new SeqQueryParameter[A](name, realPrint)
  }

  val guildId: MajorParameter[GuildId]     = new MajorParameter("guildId", _.asString)
  val channelId: MajorParameter[ChannelId] = new MajorParameter("channelId", _.asString)

  //Webhook id is only a major parameter with the token. As such we just deem the token the major part
  val webhookToken: MajorParameter[String] = new MajorParameter("webhookToken", identity)

  val webhookId: MinorParameter[SnowflakeType[Webhook]]   = new MinorParameter("webhookId", _.asString)
  val messageId: MinorParameter[MessageId]                = new MinorParameter("messageId", _.asString)
  val emoji: MinorParameter[Emoji]                        = new MinorParameter("emoji", URLEncoder.encode(_, "UTF-8"))
  val emojiId: MinorParameter[EmojiId]                    = new MinorParameter("emojiId", _.asString)
  val userId: MinorParameter[UserId]                      = new MinorParameter("userId", _.asString)
  val permissionOverwriteId: MinorParameter[UserOrRoleId] = new MinorParameter("permissionOverwriteId", _.asString)
  val roleId: MinorParameter[RoleId]                      = new MinorParameter("roleId", _.asString)
  val integrationId: MinorParameter[IntegrationId]        = new MinorParameter("integrationId", _.asString)
  val inviteCodeParam: MinorParameter[String]             = new MinorParameter("inviteCode", identity)
  val hash: MinorParameter[String]                        = new MinorParameter("hash", identity)
  val applicationId: MinorParameter[ApplicationId]        = new MinorParameter("applicationId", _.asString)
  val achievementId: MinorParameter[String]               = new MinorParameter("achievementId", identity)
  val assetId: MinorParameter[String]                     = new MinorParameter("assetId", identity)
  val teamId: MinorParameter[SnowflakeType[Team]]         = new MinorParameter("teamId", _.asString)
  val templateCode: MinorParameter[String]                = new MinorParameter("code", identity)
  val stageChannelId: MinorParameter[StageGuildChannelId] = new MinorParameter("stageChannelId", _.asString)
  val stickerId: MinorParameter[StickerId]                = new MinorParameter("stickerId", _.asString)

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

    queries.toRequest(GET)
  }

  //Channel routes

  val channel: RouteFunction[ChannelId] = base / "channels" / channelId

  val getChannel: ChannelId => RequestRoute         = channel.toRequest(GET)
  val modifyChannel: ChannelId => RequestRoute      = channel.toRequest(PATCH)
  val deleteCloseChannel: ChannelId => RequestRoute = channel.toRequest(DELETE)

  val channelMessages: RouteFunction[ChannelId]             = channel / "messages"
  val channelMessage: RouteFunction[(ChannelId, MessageId)] = channelMessages / messageId

  //We handle queries later for this one to ensure mutually exclusive params
  val getChannelMessages: ChannelId => RequestRoute             = channelMessages.toRequest(GET)
  val getChannelMessage: (ChannelId, MessageId) => RequestRoute = channelMessage.toRequest(GET)
  val createMessage: ChannelId => RequestRoute                  = channelMessages.toRequest(POST)
  val editMessage: (ChannelId, MessageId) => RequestRoute       = channelMessage.toRequest(PATCH)
  val deleteMessage: (ChannelId, MessageId) => RequestRoute     = channelMessage.toRequest(DELETE)
  val bulkDeleteMessages: ChannelId => RequestRoute             = channelMessages / "bulk-delete" toRequest POST
  val crosspostMessage: (ChannelId, MessageId) => RequestRoute  = channelMessage.toRequest(POST)

  val reactions: RouteFunction[(ChannelId, MessageId)]                 = channelMessage / "reactions"
  val emojiReactions: RouteFunction[((ChannelId, MessageId), Emoji)]   = reactions / emoji
  val modifyMeReaction: RouteFunction[((ChannelId, MessageId), Emoji)] = emojiReactions / "@me"

  val createReaction: (ChannelId, MessageId, Emoji) => RequestRoute    = modifyMeReaction.toRequest(PUT)
  val deleteOwnReaction: (ChannelId, MessageId, Emoji) => RequestRoute = modifyMeReaction.toRequest(DELETE)
  val deleteUserReaction: (ChannelId, MessageId, Emoji, UserId) => RequestRoute =
    emojiReactions / userId toRequest DELETE

  val getReactions: (ChannelId, MessageId, Emoji, Option[UserId], Option[Int]) => RequestRoute = {
    val queries = emojiReactions +?
      query[UserId]("after", _.asString) +?
      query[Int]("limit", _.toString, a => require(a >= 1 && a <= 100, "Limit must be between 1 and 100"))

    queries.toRequest(GET)
  }
  val deleteAllReactions: (ChannelId, MessageId) => RequestRoute = reactions.toRequest(DELETE)
  val deleteAllReactionsForEmoji: (ChannelId, MessageId, String) => RequestRoute =
    reactions / emoji / "emoji-object" toRequest DELETE

  val channelPermissions: RouteFunction[(ChannelId, UserOrRoleId)] = channel / "permissions" / permissionOverwriteId

  val editChannelPermissions: (ChannelId, UserOrRoleId) => RequestRoute   = channelPermissions.toRequest(PUT)
  val deleteChannelPermissions: (ChannelId, UserOrRoleId) => RequestRoute = channelPermissions.toRequest(DELETE)

  val channelInvites: RouteFunction[ChannelId] = channel / "invites"

  val getChannelInvites: ChannelId => RequestRoute    = channelInvites.toRequest(GET)
  val createChannelInvites: ChannelId => RequestRoute = channelInvites.toRequest(POST)

  val triggerTyping: ChannelId => RequestRoute = channel / "typing" toRequest POST

  val pinnedMessage: RouteFunction[ChannelId]     = channel / "pins"
  val getPinnedMessage: ChannelId => RequestRoute = pinnedMessage.toRequest(GET)

  val channelPinnedMessage: RouteFunction[(ChannelId, MessageId)]        = pinnedMessage / messageId
  val addPinnedChannelMessage: (ChannelId, MessageId) => RequestRoute    = channelPinnedMessage.toRequest(PUT)
  val deletePinnedChannelMessage: (ChannelId, MessageId) => RequestRoute = channelPinnedMessage.toRequest(DELETE)

  val followNewsChannel: ChannelId => RequestRoute = (channel / "followers").toRequest(POST)

  val groupDmRecipient: RouteFunction[(ChannelId, UserId)]        = channel / userId
  val groupDmAddRecipient: (ChannelId, UserId) => RequestRoute    = groupDmRecipient.toRequest(PUT)
  val groupDmRemoveRecipient: (ChannelId, UserId) => RequestRoute = groupDmRecipient.toRequest(DELETE)

  //Channel Threads
  val startThreadWithMessage: (ChannelId, MessageId) => RequestRoute = (channelMessage / "threads").toRequest(POST)

  val channelThreads: RouteFunction[ChannelId]             = channel / "threads"
  val startThreadWithoutMessage: ChannelId => RequestRoute = channelThreads.toRequest(POST)

  val threadMembers: RouteFunction[ChannelId]                 = channel / "thread-members"
  val threadMemberMe: RouteFunction[ChannelId]                = threadMembers / "@me"
  val threadMemberUser: RouteFunction[(ChannelId, UserId)]    = threadMembers / userId
  val joinThread: ChannelId => RequestRoute                   = threadMemberMe.toRequest(PUT)
  val addThreadMember: (ChannelId, UserId) => RequestRoute    = threadMemberUser.toRequest(PUT)
  val leaveThread: ChannelId => RequestRoute                  = threadMemberMe.toRequest(DELETE)
  val removeThreadMember: (ChannelId, UserId) => RequestRoute = threadMemberUser.toRequest(DELETE)
  val getThreadMember: (ChannelId, UserId) => RequestRoute    = threadMemberUser.toRequest(GET)
  val listThreadMembers: ChannelId => RequestRoute            = threadMembers.toRequest(GET)

  val archivedThreads: RouteFunction[ChannelId]            = channelThreads / "archived"
  val beforeTimestampQuery: QueryParameter[OffsetDateTime] = query[OffsetDateTime]("before", _.toString)
  val limitQuery: QueryParameter[Int]                      = query[Int]("limit", _.toString)

  val listActiveGuildThreads: GuildId => RequestRoute     = (guild / "threads" / "active").toRequest(GET)
  val listActiveChannelThreads: ChannelId => RequestRoute = (channelThreads / "active").toRequest(GET)
  val listPublicArchivedThreads: (ChannelId, Option[OffsetDateTime], Option[Int]) => RequestRoute =
    (archivedThreads / "public" +? beforeTimestampQuery +? limitQuery).toRequest(GET)
  val listPrivateArchivedThreads: (ChannelId, Option[OffsetDateTime], Option[Int]) => RequestRoute =
    (archivedThreads / "private" +? beforeTimestampQuery +? limitQuery).toRequest(GET)
  val listJoinedPrivateArchivedThreads: (ChannelId, Option[OffsetDateTime], Option[Int]) => RequestRoute =
    (channel / "users" / "@me" / "threads" / "archived" / "private" +? beforeTimestampQuery +? limitQuery)
      .toRequest(GET)

  //Emoji routes

  val guildEmojis: RouteFunction[GuildId]       = guild / "emojis"
  val listGuildEmojis: GuildId => RequestRoute  = guildEmojis.toRequest(GET)
  val createGuildEmoji: GuildId => RequestRoute = guildEmojis.toRequest(POST)

  val guildEmoji: RouteFunction[(GuildId, EmojiId)]        = guildEmojis / emojiId
  val getGuildEmoji: (GuildId, EmojiId) => RequestRoute    = guildEmoji.toRequest(GET)
  val modifyGuildEmoji: (GuildId, EmojiId) => RequestRoute = guildEmoji.toRequest(PATCH)
  val deleteGuildEmoji: (GuildId, EmojiId) => RequestRoute = guildEmoji.toRequest(DELETE)

  //Sticker routes
  val getSticker: StickerId => RequestRoute = base / "stickers" / stickerId toRequest GET
  val listNitroStickerPacks: RequestRoute   = base / "stickers-packs" toRequest GET

  val guildStickers: RouteFunction[GuildId]             = guild / "stickers"
  val guildSticker: RouteFunction[(GuildId, StickerId)] = guildStickers / stickerId

  val listGuildStickers: GuildId => RequestRoute               = guildStickers.toRequest(GET)
  val getGuildSticker: (GuildId, StickerId) => RequestRoute    = guildSticker.toRequest(GET)
  val createGuildSticker: GuildId => RequestRoute              = guildStickers.toRequest(POST)
  val modifyGuildSticker: (GuildId, StickerId) => RequestRoute = guildSticker.toRequest(PATCH)
  val deleteGuildSticker: (GuildId, StickerId) => RequestRoute = guildSticker.toRequest(DELETE)

  //Guild routes
  val withCountQuery: QueryParameter[Boolean] = query[Boolean]("with_counts", _.toString)

  val createGuild: RequestRoute                            = guilds.toRequest(POST)
  val getGuild: (GuildId, Option[Boolean]) => RequestRoute = (guild +? withCountQuery).toRequest(GET)
  val getGuildPreview: GuildId => RequestRoute             = guild / "preview" toRequest GET
  val modifyGuild: GuildId => RequestRoute                 = guild.toRequest(PATCH)
  val deleteGuild: GuildId => RequestRoute                 = guild.toRequest(DELETE)

  val guildChannels: RouteFunction[GuildId]                 = guild / "channels"
  val getGuildChannels: GuildId => RequestRoute             = guildChannels.toRequest(GET)
  val createGuildChannel: GuildId => RequestRoute           = guildChannels.toRequest(POST)
  val modifyGuildChannelsPositions: GuildId => RequestRoute = guildChannels.toRequest(PATCH)

  val guildMembers: RouteFunction[GuildId]              = guild / "members"
  val guildMember: RouteFunction[(GuildId, UserId)]     = guildMembers / userId
  val getGuildMember: (GuildId, UserId) => RequestRoute = guildMember.toRequest(GET)
  val listGuildMembers: (GuildId, Option[Int], Option[UserId]) => RequestRoute = {
    val queries = guildMembers +?
      query[Int]("limit", _.toString, a => require(a >= 1 && a <= 1000, "Limit must be between 1 and 1000")) +?
      query[UserId]("after", _.asString)

    queries.toRequest(GET)
  }
  val searchGuildMembers: GuildId => RequestRoute = guildMembers / "search" toRequest GET

  val addGuildMember: (GuildId, UserId) => RequestRoute      = guildMember.toRequest(PUT)
  val modifyGuildMember: (GuildId, UserId) => RequestRoute   = guildMember.toRequest(PATCH)
  val removeGuildMember: (GuildId, UserId) => RequestRoute   = guildMember.toRequest(DELETE)
  @deprecated val modifyCurrentNick: GuildId => RequestRoute = guildMembers / "@me" / "nick" toRequest PATCH
  val modifyCurrentMember: GuildId => RequestRoute           = guildMembers / "@me" toRequest PATCH

  val guildMemberRole: RouteFunction[((GuildId, UserId), RoleId)] = guildMember / "roles" / roleId

  val addGuildMemberRole: (GuildId, UserId, RoleId) => RequestRoute    = guildMemberRole.toRequest(PUT)
  val removeGuildMemberRole: (GuildId, UserId, RoleId) => RequestRoute = guildMemberRole.toRequest(DELETE)

  val guildBans: RouteFunction[GuildId]                = guild / "bans"
  val guildMemberBan: RouteFunction[(GuildId, UserId)] = guildBans / userId

  val getGuildBans: GuildId => RequestRoute                   = guildBans.toRequest(GET)
  val getGuildBan: (GuildId, UserId) => RequestRoute          = guildMemberBan.toRequest(GET)
  val createGuildMemberBan: (GuildId, UserId) => RequestRoute = guildMemberBan.toRequest(PUT)
  val removeGuildMemberBan: (GuildId, UserId) => RequestRoute = guildMemberBan.toRequest(DELETE)

  val guildRoles: RouteFunction[GuildId]                = guild / "roles"
  val getGuildRole: GuildId => RequestRoute             = guildRoles.toRequest(GET)
  val createGuildRole: GuildId => RequestRoute          = guildRoles.toRequest(POST)
  val modifyGuildRolePositions: GuildId => RequestRoute = guildRoles.toRequest(PATCH)

  val guildRole: RouteFunction[(GuildId, RoleId)]        = guildRoles / roleId
  val modifyGuildRole: (GuildId, RoleId) => RequestRoute = guildRole.toRequest(PATCH)
  val deleteGuildRole: (GuildId, RoleId) => RequestRoute = guildRole.toRequest(DELETE)

  val daysQuery: QueryParameter[Int] =
    query[Int]("days", _.toString, a => require(a >= 1, "Can't prune for zero or negative days"))

  val includeRolesQuery: QueryParameter[Seq[RoleId]] =
    query[Seq[RoleId]]("include_roles", seq => seq.map(_.asString).mkString(","))

  val guildPrune: RouteFunction[GuildId] = guild / "prune"
  val getGuildPruneCount: (GuildId, Option[Int], Option[Seq[RoleId]]) => RequestRoute =
    (guildPrune +? daysQuery +? includeRolesQuery).toRequest(GET)
  val beginGuildPrune: GuildId => RequestRoute = guildPrune.toRequest(POST)

  val getGuildVoiceRegions: GuildId => RequestRoute = guild / "regions" toRequest GET
  val getGuildInvites: GuildId => RequestRoute      = guild / "invites" toRequest GET

  val guildIntegrations: RouteFunction[GuildId]        = guild / "integrations"
  val getGuildIntegrations: GuildId => RequestRoute    = guildIntegrations.toRequest(GET)
  val createGuildIntegrations: GuildId => RequestRoute = guildIntegrations.toRequest(POST)

  val guildIntegration: RouteFunction[(GuildId, IntegrationId)]        = guildIntegrations / integrationId
  val modifyGuildIntegration: (GuildId, IntegrationId) => RequestRoute = guildIntegration.toRequest(PATCH)
  val deleteGuildIntegration: (GuildId, IntegrationId) => RequestRoute = guildIntegration.toRequest(DELETE)
  val syncGuildIntegration: (GuildId, IntegrationId) => RequestRoute   = guildIntegration / "sync" toRequest PATCH

  val guildWidget: RouteFunction[GuildId]         = guild / "widget"
  val getGuildWidget: GuildId => RequestRoute     = guildWidget.toRequest(GET)
  val modifyGuildWidget: GuildId => RequestRoute  = guildWidget.toRequest(PATCH)
  val getGuildWidgetJson: GuildId => RequestRoute = (guildWidget ++ ".json").toRequest(GET)
  val getGuildVanityUrl: GuildId => RequestRoute  = guild / "vanity-url" toRequest GET

  val style: QueryParameter[WidgetImageStyle] = new QueryParameter("style", _.value)

  val getGuildWidgetImage: (GuildId, Option[WidgetImageStyle]) => RequestRoute =
    guild / "widget.png" +? style toRequest GET

  val guildWelcomeScreen: RouteFunction[GuildId]        = guild / "welcome-screen"
  val getGuildWelcomeScreen: GuildId => RequestRoute    = guildWelcomeScreen.toRequest(GET)
  val modifyGuildWelcomeScreen: GuildId => RequestRoute = guildWelcomeScreen.toRequest(PATCH)

  val guildVoiceStates: RouteFunction[GuildId]                = guild / "voice-states"
  val updateCurrentUserVoiceState: GuildId => RequestRoute    = guildVoiceStates / "@me" toRequest PATCH
  val updateUserVoiceState: (GuildId, UserId) => RequestRoute = guildVoiceStates / userId toRequest PATCH

  //Templates
  val template: Route                                 = guilds / "template"
  val templateCodeRoute: RouteFunction[String]        = guilds / "template" / templateCode
  val getTemplate: String => RequestRoute             = templateCodeRoute.toRequest(GET)
  val createGuildFromTemplate: String => RequestRoute = templateCodeRoute.toRequest(POST)

  val guildTemplates: RouteFunction[GuildId]                 = guild / "templates"
  val getGuildTemplates: GuildId => RequestRoute             = guildTemplates.toRequest(GET)
  val postGuildTemplate: GuildId => RequestRoute             = guildTemplates.toRequest(GET)
  val guildTemplate: RouteFunction[(GuildId, String)]        = guildTemplates / templateCode
  val putGuildTemplate: (GuildId, String) => RequestRoute    = guildTemplate.toRequest(PUT)
  val patchGuildTemplate: (GuildId, String) => RequestRoute  = guildTemplate.toRequest(PATCH)
  val deleteGuildTemplate: (GuildId, String) => RequestRoute = guildTemplate.toRequest(DELETE)

  //Invites
  val invites: Route                    = base / "invites"
  val inviteCode: RouteFunction[String] = invites / inviteCodeParam
  val getInvite: (InviteCode, Option[Boolean], Option[Boolean]) => RequestRoute = {
    val withParams = inviteCode +?
      query[Boolean]("with_counts", _.toString) +?
      query[Boolean]("with_expiration", _.toString)

    withParams.toRequest(GET)
  }
  val deleteInvite: InviteCode => RequestRoute = inviteCode.toRequest(DELETE)

  //Stage instances
  val stageInstances: Route             = base / "stage-instances"
  val createStageInstance: RequestRoute = stageInstances.toRequest(POST)

  val stageInstance: RouteFunction[StageGuildChannelId]        = stageInstances / stageChannelId
  val getStageInstance: StageGuildChannelId => RequestRoute    = stageInstance.toRequest(GET)
  val updateStageInstance: StageGuildChannelId => RequestRoute = stageInstance.toRequest(PATCH)
  val deleteStageInstance: StageGuildChannelId => RequestRoute = stageInstance.toRequest(DELETE)

  //Users
  val users: Route                 = base / "users"
  val currentUser: Route           = users / "@me"
  val getCurrentUser: RequestRoute = currentUser.toRequest(GET)

  val getUser: UserId => RequestRoute = users / userId toRequest GET
  val modifyCurrentUser: RequestRoute = currentUser.toRequest(PATCH)
  val currentUserGuilds: Route        = currentUser / "guilds"
  val getCurrentUserGuilds: (Option[GuildId], Option[GuildId], Option[Int]) => RequestRoute = {
    val queries = currentUserGuilds +?
      query[GuildId]("before", _.asString) +?
      query[GuildId]("after", _.asString) +?
      query[Int]("limit", _.toString, a => require(a >= 1 && a <= 100, "Limit must be between 1 and 100"))

    queries.toRequest(GET)
  }
  val leaveGuild: GuildId => RequestRoute = currentUserGuilds / guildId toRequest DELETE

  val userDMs: Route         = currentUser / "channels"
  val createDM: RequestRoute = userDMs.toRequest(POST)

  val getUserConnections: RequestRoute = currentUser / "connections" toRequest GET

  //Voice
  val listVoiceRegions: RequestRoute = base / "voice" / "regions" toRequest GET

  //WebHook
  val webhook: RouteFunction[SnowflakeType[Webhook]]                    = base / "webhooks" / webhookId
  val webhookWithToken: RouteFunction[(SnowflakeType[Webhook], String)] = webhook / webhookToken
  val channelWebhooks: RouteFunction[ChannelId]                         = channel / "webhooks"

  val createWebhook: ChannelId => RequestRoute                                 = channelWebhooks.toRequest(POST)
  val getChannelWebhooks: ChannelId => RequestRoute                            = channelWebhooks.toRequest(GET)
  val getGuildWebhooks: GuildId => RequestRoute                                = guild / "webhooks" toRequest GET
  val getWebhook: SnowflakeType[Webhook] => RequestRoute                       = webhook.toRequest(GET)
  val getWebhookWithToken: (SnowflakeType[Webhook], String) => RequestRoute    = webhookWithToken.toRequest(GET)
  val modifyWebhook: SnowflakeType[Webhook] => RequestRoute                    = webhook.toRequest(PATCH)
  val modifyWebhookWithToken: (SnowflakeType[Webhook], String) => RequestRoute = webhookWithToken.toRequest(PATCH)
  val deleteWebhook: SnowflakeType[Webhook] => RequestRoute                    = webhook.toRequest(DELETE)
  val deleteWebhookWithToken: (SnowflakeType[Webhook], String) => RequestRoute = webhookWithToken.toRequest(DELETE)

  val waitQuery: QueryParameter[Boolean]                  = query[Boolean]("wait", _.toString)
  val threadIdQuery: QueryParameter[ThreadGuildChannelId] = query[ThreadGuildChannelId]("thread_id", _.asString)

  val executeWebhook: (SnowflakeType[Webhook], String, Option[Boolean], Option[ThreadGuildChannelId]) => RequestRoute =
    webhookWithToken +? waitQuery +? threadIdQuery toRequest POST
  val executeSlackWebhook: (SnowflakeType[Webhook], String, Option[Boolean]) => RequestRoute =
    webhookWithToken / "slack" +? waitQuery toRequest POST
  val executeGithubWebhook: (SnowflakeType[Webhook], String, Option[Boolean]) => RequestRoute =
    webhookWithToken / "github" +? waitQuery toRequest POST

  val messagesWebhook: RouteFunction[(SnowflakeType[Webhook], String)]        = webhookWithToken / "messages"
  val originalWebhookMessage: RouteFunction[(SnowflakeType[Webhook], String)] = messagesWebhook / "@original"
  val webhookMessage
      : QueryRouteFunction[(((SnowflakeType[Webhook], String), MessageId), Option[ThreadGuildChannelId])] =
    messagesWebhook / messageId +? threadIdQuery

  val postFollowupMessage: (SnowflakeType[Webhook], String) => RequestRoute = webhookWithToken.toRequest(POST)

  val getOriginalWebhookMessage: (SnowflakeType[Webhook], String) => RequestRoute =
    originalWebhookMessage.toRequest(GET)
  val editOriginalWebhookMessage: (SnowflakeType[Webhook], String) => RequestRoute =
    originalWebhookMessage.toRequest(PATCH)
  val deleteOriginalWebhookMessage: (SnowflakeType[Webhook], String) => RequestRoute =
    originalWebhookMessage.toRequest(PATCH)
  val getWebhookMessage: (SnowflakeType[Webhook], String, MessageId, Option[ThreadGuildChannelId]) => RequestRoute =
    webhookMessage.toRequest(GET)
  val editWebhookMessage: (SnowflakeType[Webhook], String, MessageId, Option[ThreadGuildChannelId]) => RequestRoute =
    webhookMessage.toRequest(PATCH)
  val deleteWebhookMessage: (SnowflakeType[Webhook], String, MessageId, Option[ThreadGuildChannelId]) => RequestRoute =
    webhookMessage.toRequest(DELETE)

  val size: QueryParameter[Int]               = new QueryParameter("size", _.toString)
  val extension: ConcatParameter[ImageFormat] = new ConcatParameter(_.extension)
  val discriminator: MinorParameter[Int]      = new MinorParameter("discriminator", i => (i % 5).toString)

  //Images
  val emojiImage: (EmojiId, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "emojis" / emojiId ++ extension +? size toRequest GET
  val guildIconImage: (GuildId, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "icons" / guildId / hash ++ extension +? size toRequest GET
  val guildSplashImage: (GuildId, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "splashes" / guildId / hash ++ extension +? size toRequest GET
  val discoverySplashImage: (GuildId, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "splashes" / guildId / hash ++ extension +? size toRequest GET
  val guildBannerImage: (GuildId, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "banners" / guildId / hash ++ extension +? size toRequest GET
  val userBannerImage: (UserId, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "banners" / userId / hash ++ extension +? size toRequest GET
  val defaultUserAvatarImage: (Int, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "embed" / "avatars" / discriminator ++ extension +? size toRequest GET
  val userAvatarImage: (UserId, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "avatars" / userId / hash ++ extension +? size toRequest GET
  val userGuildAvatarImage: (GuildId, UserId, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "guilds" / guildId / "users" / userId / "avatars" / hash ++ extension +? size toRequest GET
  val applicationIconImage: (ApplicationId, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "app-icons" / applicationId / "icon.png" ++ extension +? size toRequest GET
  val applicationCoverImage: (ApplicationId, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "app-icons" / applicationId / "cover_image.png" ++ extension +? size toRequest GET
  val applicationAssetImage: (ApplicationId, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "app-assets" / applicationId / hash ++ extension +? size toRequest GET
  val applicationAchievementIconImage: (ApplicationId, String, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "app-assets" / applicationId / "achievements" / achievementId / "icons" / hash ++ extension +? size toRequest GET

  val stickerPackBannerImage: (String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "app-assets" / "710982414301790216" / "store" / assetId ++ extension +? size toRequest GET

  val teamIconImage: (SnowflakeType[Team], String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "team-icons" / teamId / hash ++ extension +? size toRequest GET

  val stickerImage: (StickerId, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "stickers" / stickerId ++ extension +? size toRequest GET

  val roleIconImage: (RoleId, String, ImageFormat, Option[Int]) => RequestRoute =
    cdn / "role-icons" / roleId / hash ++ extension +? size toRequest GET

  //OAuth
  val oAuth2: Route                                    = base / "oauth2"
  val oAuth2Authorize: Route                           = oAuth2 / "authorize"
  val oAuth2Token: Route                               = oAuth2 / "token"
  val oAuth2Revoke: Route                              = oAuth2Token / "revoke"
  val getCurrentApplication: RequestRoute              = (oAuth2 / "applications" / "@me").toRequest(GET)
  val getCurrentAuthorizationInformation: RequestRoute = (oAuth2 / "@me").toRequest(GET)
}
