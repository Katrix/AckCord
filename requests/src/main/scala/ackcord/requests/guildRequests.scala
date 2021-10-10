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

import java.time.OffsetDateTime

import ackcord.data.DiscordProtocol._
import ackcord.data._
import ackcord.data.raw._
import ackcord.util.{JsonOption, JsonSome, JsonUndefined}
import ackcord.{CacheSnapshot, SnowflakeMap}
import akka.NotUsed
import io.circe._
import io.circe.syntax._

/**
  * @param name
  *   The name of the guild
  * @param icon
  *   The icon to use for the guild. Must be 1024x1024 png/jpeg.
  * @param verificationLevel
  *   The verification level to use for the guild.
  * @param defaultMessageNotifications
  *   The notification level to use for the guild.
  * @param roles
  *   The roles for the new guild. Note, here the snowflake is just a
  *   placeholder.
  * @param channels
  *   The channels for the new guild.
  * @param afkChannelId
  *   The id for the AFK channel
  * @param afkTimeout
  *   The timeout in seconds until users are moved to the AFK channel.
  * @param systemChannelId
  *   The id of the system channel.
  * @param systemChannelFlags
  *   The flags for the system channel.
  */
case class CreateGuildData(
    name: String,
    icon: Option[ImageData],
    verificationLevel: Option[VerificationLevel],
    defaultMessageNotifications: Option[NotificationLevel],
    explicitContentFilter: Option[FilterLevel],
    roles: Option[Seq[Role]],
    channels: Option[
      Seq[CreateGuildChannelData]
    ], //Technically this should be partial channels, but I think this works too
    afkChannelId: Option[NormalVoiceGuildChannelId],
    afkTimeout: Option[Int],
    systemChannelId: Option[TextGuildChannelId],
    systemChannelFlags: Option[SystemChannelFlags]
) {
  require(name.length >= 2 && name.length <= 100, "The guild name has to be between 2 and 100 characters")
}

/** Create a new guild. Can only be used by bots in less than 10 guilds. */
case class CreateGuild(params: CreateGuildData) extends RESTRequest[CreateGuildData, RawGuild, RequestsGuild] {
  override def route: RequestRoute = Routes.createGuild
  override def paramsEncoder: Encoder[CreateGuildData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[RawGuild]                = Decoder[RawGuild]
  override def toNiceResponse(response: RawGuild): RequestsGuild = response.toRequestGuild
}

/** Get a guild by id. */
case class GetGuild(guildId: GuildId, withCounts: Boolean = false) extends NoParamsRequest[RawGuild, RequestsGuild] {
  override def route: RequestRoute = Routes.getGuild(guildId, Some(withCounts))

  override def responseDecoder: Decoder[RawGuild]                = Decoder[RawGuild]
  override def toNiceResponse(response: RawGuild): RequestsGuild = response.toRequestGuild
}

/** Get a guild preview by it's id. Only usable for public guilds. */
case class GetGuildPreview(guildId: GuildId) extends NoParamsNiceResponseRequest[GuildPreview] {
  override def route: RequestRoute = Routes.getGuildPreview(guildId)

  override def responseDecoder: Decoder[GuildPreview] = Decoder[GuildPreview]
}

/**
  * @param name
  *   The new name of the guild
  * @param region
  *   The new voice region for the guild
  * @param verificationLevel
  *   The new verification level to use for the guild.
  * @param defaultMessageNotifications
  *   The new notification level to use for the guild.
  * @param afkChannelId
  *   The new afk channel of the guild.
  * @param afkTimeout
  *   The new afk timeout in seconds for the guild.
  * @param icon
  *   The new icon to use for the guild. Must be 1024x1024 png/jpeg/gif. Can be
  *   animated if the guild has the `ANIMATED_ICON` feature.
  * @param ownerId
  *   Transfer ownership of this guild. Must be the owner.
  * @param splash
  *   The new splash for the guild. Must be 16:9 png/jpeg. Only available if the
  *   guild has the `INVITE_SPLASH` feature.
  * @param discoverySplash
  *   Thew new discovery slash for the guild's discovery splash. Only available
  *   if the guild has the `DISCOVERABLE` feature.
  * @param banner
  *   The new banner for the guild. Must be 16:9 png/jpeg. Only available if the
  *   guild has the `BANNER` feature.
  * @param systemChannelId
  *   The new channel which system messages will be sent to.
  * @param systemChannelFlags
  *   The new flags for the system channel.
  * @param preferredLocale
  *   The new preferred locale for the guild.
  * @param features
  *   The new enabled features for the guild.
  * @param description
  *   The new description for the guild if it is discoverable.
  */
case class ModifyGuildData(
    name: JsonOption[String] = JsonUndefined,
    verificationLevel: JsonOption[VerificationLevel] = JsonUndefined,
    defaultMessageNotifications: JsonOption[NotificationLevel] = JsonUndefined,
    explicitContentFilter: JsonOption[FilterLevel] = JsonUndefined,
    afkChannelId: JsonOption[NormalVoiceGuildChannelId] = JsonUndefined,
    afkTimeout: JsonOption[Int] = JsonUndefined,
    icon: JsonOption[ImageData] = JsonUndefined,
    ownerId: JsonOption[UserId] = JsonUndefined,
    splash: JsonOption[ImageData] = JsonUndefined,
    discoverySplash: JsonOption[ImageData] = JsonUndefined,
    banner: JsonOption[ImageData] = JsonUndefined,
    systemChannelId: JsonOption[TextGuildChannelId] = JsonUndefined,
    systemChannelFlags: JsonOption[SystemChannelFlags] = JsonUndefined,
    preferredLocale: JsonOption[String] = JsonUndefined,
    features: JsonOption[Seq[String]] = JsonUndefined,
    description: JsonOption[String] = JsonUndefined
)
object ModifyGuildData {
  implicit val encoder: Encoder[ModifyGuildData] = (a: ModifyGuildData) =>
    JsonOption.removeUndefinedToObj(
      "name"                          -> a.name.toJson,
      "verification_level"            -> a.verificationLevel.toJson,
      "default_message_notifications" -> a.defaultMessageNotifications.toJson,
      "explicit_content_filter"       -> a.explicitContentFilter.toJson,
      "afk_channel_id"                -> a.afkChannelId.toJson,
      "afk_timeout"                   -> a.afkTimeout.toJson,
      "icon"                          -> a.icon.toJson,
      "owner_id"                      -> a.ownerId.toJson,
      "splash"                        -> a.splash.toJson,
      "discovery_splash"              -> a.discoverySplash.toJson,
      "banner"                        -> a.banner.toJson,
      "system_channel_id"             -> a.systemChannelId.toJson,
      "system_channel_flags"          -> a.systemChannelFlags.toJson,
      "preferred_locale"              -> a.preferredLocale.toJson,
      "features"                      -> a.features.toJson,
      "description"                   -> a.description.toJson
    )
}

/** Modify an existing guild. */
case class ModifyGuild(
    guildId: GuildId,
    params: ModifyGuildData,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuild, ModifyGuildData, RawGuild, RequestsGuild] {
  override def route: RequestRoute                     = Routes.modifyGuild(guildId)
  override def paramsEncoder: Encoder[ModifyGuildData] = ModifyGuildData.encoder

  override def responseDecoder: Decoder[RawGuild]                = Decoder[RawGuild]
  override def toNiceResponse(response: RawGuild): RequestsGuild = response.toRequestGuild

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuild = copy(reason = Some(reason))
}

/** Delete a guild. Must be the owner. */
case class DeleteGuild(guildId: GuildId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteGuild(guildId)
}

/** Get all the channels for a guild. */
case class GetGuildChannels(guildId: GuildId) extends NoParamsRequest[Seq[RawChannel], Seq[Option[GuildChannel]]] {
  override def route: RequestRoute = Routes.getGuildChannels(guildId)

  override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
  override def toNiceResponse(response: Seq[RawChannel]): Seq[Option[GuildChannel]] =
    response.map(_.toGuildChannel(guildId, None)) //Safe
}

/**
  * @param name
  *   The name of the channel.
  * @param `type`
  *   The channel type.
  * @param topic
  *   The topic to give this channel.
  * @param bitrate
  *   The bitrate for the channel if it's a voice channel.
  * @param userLimit
  *   The user limit for the channel if it's a voice channel.
  * @param rateLimitPerUser
  *   The user ratelimit to give this channel.
  * @param permissionOverwrites
  *   The permission overwrites for the channel.
  * @param parentId
  *   The category id for the channel.
  * @param nsfw
  *   If the channel is NSFW.
  */
case class CreateGuildChannelData(
    name: String,
    `type`: JsonOption[ChannelType] = JsonUndefined,
    topic: JsonOption[String] = JsonUndefined,
    bitrate: JsonOption[Int] = JsonUndefined,
    userLimit: JsonOption[Int] = JsonUndefined,
    rateLimitPerUser: JsonOption[Int] = JsonUndefined,
    permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
    parentId: JsonOption[SnowflakeType[GuildCategory]] = JsonUndefined,
    nsfw: JsonOption[Boolean] = JsonUndefined
) {
  require(name.nonEmpty && name.length <= 100, "A channel name has to be between 2 and 100 characters")
  require(rateLimitPerUser.forall(i => i >= 0 && i <= 21600), "Rate limit per user must be between 0 ad 21600")
}
object CreateGuildChannelData {
  implicit val encoder: Encoder[CreateGuildChannelData] = (a: CreateGuildChannelData) =>
    JsonOption.removeUndefinedToObj(
      "name"                  -> JsonSome(a.name.asJson),
      "type"                  -> a.`type`.toJson,
      "topic"                 -> a.topic.toJson,
      "bitrate"               -> a.bitrate.toJson,
      "user_limit"            -> a.userLimit.toJson,
      "rate_limit_per_user"   -> a.rateLimitPerUser.toJson,
      "permission_overwrites" -> a.permissionOverwrites.toJson,
      "parent_id"             -> a.parentId.toJson,
      "nsfw"                  -> a.nsfw.toJson
    )
}

/** Create a channel in a guild. */
case class CreateGuildChannel(
    guildId: GuildId,
    params: CreateGuildChannelData,
    reason: Option[String] = None
) extends ReasonRequest[CreateGuildChannel, CreateGuildChannelData, RawChannel, Option[GuildChannel]] {
  override def route: RequestRoute                            = Routes.createGuildChannel(guildId)
  override def paramsEncoder: Encoder[CreateGuildChannelData] = CreateGuildChannelData.encoder

  override def jsonPrinter: Printer = Printer.noSpaces

  override def responseDecoder: Decoder[RawChannel] = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[GuildChannel] =
    response.toGuildChannel(guildId, None) //Safe

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildChannel = copy(reason = Some(reason))
}

/**
  * @param id
  *   The channel id.
  * @param position
  *   It's new position.
  * @param lockPermissions
  *   If the permissions should be synced with the category if the channel is
  *   moved to a new category.
  * @param parentId
  *   Parent category id to move the channel to a new category.
  */
case class ModifyGuildChannelPositionsData(
    id: GuildChannelId,
    position: JsonOption[Int] = JsonUndefined,
    lockPermissions: JsonOption[Boolean] = JsonUndefined,
    parentId: JsonOption[ChannelId] = JsonUndefined
)
object ModifyGuildChannelPositionsData {
  def apply(id: GuildChannelId, position: Int): ModifyGuildChannelPositionsData =
    new ModifyGuildChannelPositionsData(id, JsonSome(position))

  implicit val encoder: Encoder[ModifyGuildChannelPositionsData] = (a: ModifyGuildChannelPositionsData) =>
    JsonOption.removeUndefinedToObj(
      "id"       -> JsonSome(a.id.asJson),
      "position" -> a.position.toJson
    )
}

/** Modify the positions of several channels. */
case class ModifyGuildChannelPositions(
    guildId: GuildId,
    params: Seq[ModifyGuildChannelPositionsData],
    reason: Option[String] = None
) extends NoResponseReasonRequest[ModifyGuildChannelPositions, Seq[ModifyGuildChannelPositionsData]] {
  override def route: RequestRoute = Routes.modifyGuildChannelsPositions(guildId)
  override def paramsEncoder: Encoder[Seq[ModifyGuildChannelPositionsData]] =
    Encoder[Seq[ModifyGuildChannelPositionsData]]

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildChannelPositions = copy(reason = Some(reason))
}

/**
  * @param threads
  *   The active threads.
  * @param members
  *   A thread member object for each thread the current user has joined.
  */
case class ListActiveThreadsResponse(threads: Seq[RawChannel], members: Seq[RawThreadMember])
case class ListActiveGuildThreads(guildId: GuildId) extends NoParamsNiceResponseRequest[ListActiveThreadsResponse] {
  override def route: RequestRoute = Routes.listActiveGuildThreads(guildId)
  override def responseDecoder: Decoder[ListActiveThreadsResponse] =
    derivation.deriveDecoder(derivation.renaming.snakeCase)
}

trait GuildMemberRequest[Params] extends RESTRequest[Params, RawGuildMember, GuildMember] {
  def guildId: GuildId

  override def responseDecoder: Decoder[RawGuildMember]              = Decoder[RawGuildMember]
  override def toNiceResponse(response: RawGuildMember): GuildMember = response.toGuildMember(guildId)
}

/** Get a guild member by id. */
case class GetGuildMember(guildId: GuildId, userId: UserId) extends GuildMemberRequest[NotUsed] {
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
  override def params: NotUsed                 = NotUsed
  override def route: RequestRoute             = Routes.getGuildMember(guildId, userId)
}

/**
  * @param limit
  *   The max amount of members to get
  * @param after
  *   Get userIds after this id
  */
case class ListGuildMembersData(limit: Option[Int] = None, after: Option[UserId] = None)

/** Get all the guild members in this guild. */
case class ListGuildMembers(guildId: GuildId, queryParams: ListGuildMembersData)
    extends NoParamsRequest[Seq[RawGuildMember], Seq[GuildMember]] {
  override def route: RequestRoute = Routes.listGuildMembers(guildId, queryParams.limit, queryParams.after)

  override def responseDecoder: Decoder[Seq[RawGuildMember]] = Decoder[Seq[RawGuildMember]]
  override def toNiceResponse(response: Seq[RawGuildMember]): Seq[GuildMember] =
    response.map(_.toGuildMember(guildId))
}
object ListGuildMembers {
  def mk(
      guildId: GuildId,
      limit: Option[Int] = None,
      after: Option[UserId] = None
  ): ListGuildMembers = new ListGuildMembers(guildId, ListGuildMembersData(limit, after))
}

case class SearchGuildMembersData(query: String, limit: JsonOption[Int] = JsonUndefined)
object SearchGuildMembersData {
  implicit val encoder: Encoder[SearchGuildMembersData] = (a: SearchGuildMembersData) =>
    JsonOption.removeUndefinedToObj(
      "query" -> JsonSome(a.query.asJson),
      "limit" -> a.limit.toJson
    )
}

case class SearchGuildMembers(guildId: GuildId, params: SearchGuildMembersData)
    extends RESTRequest[SearchGuildMembersData, Seq[RawGuildMember], Seq[GuildMember]] {
  override def route: RequestRoute = Routes.searchGuildMembers(guildId)

  override def paramsEncoder: Encoder[SearchGuildMembersData] = SearchGuildMembersData.encoder

  override def responseDecoder: Decoder[Seq[RawGuildMember]]                   = Decoder[Seq[RawGuildMember]]
  override def toNiceResponse(response: Seq[RawGuildMember]): Seq[GuildMember] = response.map(_.toGuildMember(guildId))
}

/**
  * @param accessToken
  *   The OAuth2 access token.
  * @param nick
  *   The nickname to give to the user.
  * @param roles
  *   The roles to give to the user. If the guild has membership screening
  *   enabled, this setting this will bypass that.
  * @param mute
  *   If the user should be muted.
  * @param deaf
  *   If the user should be deafened.
  */
case class AddGuildMemberData(
    accessToken: String,
    nick: Option[String] = None,
    roles: Option[Seq[RoleId]] = None,
    mute: Option[Boolean] = None,
    deaf: Option[Boolean] = None
)

/** Adds a user to a guild. Requires the `guilds.join` OAuth2 scope. */
case class AddGuildMember(
    guildId: GuildId,
    userId: UserId,
    params: AddGuildMemberData
) extends GuildMemberRequest[AddGuildMemberData] {
  override def route: RequestRoute = Routes.addGuildMember(guildId, userId)
  override def paramsEncoder: Encoder[AddGuildMemberData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def requiredPermissions: Permission = {
    def ifDefined(opt: Option[_], perm: Permission): Permission = if (opt.isDefined) perm else Permission.None
    Permission(
      Permission.CreateInstantInvite,
      ifDefined(params.nick, Permission.ManageNicknames),
      ifDefined(params.roles, Permission.ManageRoles),
      ifDefined(params.mute, Permission.MuteMembers),
      ifDefined(params.deaf, Permission.DeafenMembers)
    )
  }
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * @param nick
  *   The nickname to give to the user.
  * @param roles
  *   The roles to give to the user.
  * @param mute
  *   If the user should be muted. Will return an error if the user is not in a
  *   voice channel.
  * @param deaf
  *   If the user should be deafened. Will return an error if the user is not in
  *   a voice channel.
  * @param channelId
  *   The id of the channel to move the user to.
  */
case class ModifyGuildMemberData(
    nick: JsonOption[String] = JsonUndefined,
    roles: JsonOption[Seq[RoleId]] = JsonUndefined,
    mute: JsonOption[Boolean] = JsonUndefined,
    deaf: JsonOption[Boolean] = JsonUndefined,
    channelId: JsonOption[VoiceGuildChannelId] = JsonUndefined
)
object ModifyGuildMemberData {
  implicit val encoder: Encoder[ModifyGuildMemberData] = (a: ModifyGuildMemberData) =>
    JsonOption.removeUndefinedToObj(
      "nick"       -> a.nick.toJson,
      "roles"      -> a.roles.toJson,
      "mute"       -> a.mute.toJson,
      "deaf"       -> a.deaf.toJson,
      "channel_id" -> a.channelId.toJson
    )
}

/** Modify a guild member. */
case class ModifyGuildMember(
    guildId: GuildId,
    userId: UserId,
    params: ModifyGuildMemberData,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildMember, ModifyGuildMemberData, RawGuildMember, GuildMember] {
  override def jsonPrinter: Printer = Printer.noSpaces

  override def route: RequestRoute                           = Routes.modifyGuildMember(guildId, userId)
  override def paramsEncoder: Encoder[ModifyGuildMemberData] = ModifyGuildMemberData.encoder

  override def requiredPermissions: Permission = {
    def ifDefined(opt: JsonOption[_], perm: Permission): Permission = if (opt.nonEmpty) perm else Permission.None
    Permission(
      Permission.CreateInstantInvite,
      ifDefined(params.nick, Permission.ManageNicknames),
      ifDefined(params.roles, Permission.ManageRoles),
      ifDefined(params.mute, Permission.MuteMembers),
      ifDefined(params.deaf, Permission.DeafenMembers),
      ifDefined(params.channelId, Permission.MoveMembers)
    )
  }
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
  override def withReason(reason: String): ModifyGuildMember = copy(reason = Some(reason))

  override def responseDecoder: Decoder[RawGuildMember] = Decoder[RawGuildMember]

  override def toNiceResponse(response: RawGuildMember): GuildMember = response.toGuildMember(guildId)
}

@deprecated case class ModifyBotUsersNickData(nick: JsonOption[String] = JsonUndefined)
@deprecated object ModifyBotUsersNickData {
  implicit val encoder: Encoder[ModifyBotUsersNickData] = (a: ModifyBotUsersNickData) =>
    JsonOption.removeUndefinedToObj(
      "nick" -> a.nick.toJson
    )
}

/** Modify the clients nickname. */
@deprecated case class ModifyBotUsersNick(
    guildId: GuildId,
    params: ModifyBotUsersNickData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[ModifyBotUsersNick, ModifyBotUsersNickData, String] {
  override def route: RequestRoute = Routes.modifyCurrentNick(guildId)
  override def paramsEncoder: Encoder[ModifyBotUsersNickData] =
    ModifyBotUsersNickData.encoder

  override def responseDecoder: Decoder[String] = Decoder[String]

  override def requiredPermissions: Permission = Permission.ChangeNickname
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyBotUsersNick = copy(reason = Some(reason))
}
@deprecated object ModifyBotUsersNick {
  def mk(guildId: GuildId, nick: String): ModifyBotUsersNick =
    new ModifyBotUsersNick(guildId, ModifyBotUsersNickData(JsonSome(nick)))
}

case class ModifyCurrentMemberData(nick: JsonOption[String] = JsonUndefined)
object ModifyCurrentMemberData {
  implicit val encoder: Encoder[ModifyCurrentMemberData] = (a: ModifyCurrentMemberData) =>
    JsonOption.removeUndefinedToObj(
      "nick" -> a.nick.toJson
    )
}

/** Modify Current Member */
case class ModifyCurrentMember(guildId: GuildId, params: ModifyCurrentMemberData)
    extends GuildMemberRequest[ModifyCurrentMemberData] {
  override def route: RequestRoute = Routes.modifyCurrentUser

  override def paramsEncoder: Encoder[ModifyCurrentMemberData] =
    ModifyCurrentMemberData.encoder
}

/** Add a role to a guild member. */
case class AddGuildMemberRole(guildId: GuildId, userId: UserId, roleId: RoleId, reason: Option[String] = None)
    extends NoParamsResponseReasonRequest[AddGuildMemberRole] {
  override def route: RequestRoute = Routes.addGuildMemberRole(guildId, userId, roleId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): AddGuildMemberRole = copy(reason = Some(reason))
}

/** Remove a role from a guild member. */
case class RemoveGuildMemberRole(guildId: GuildId, userId: UserId, roleId: RoleId, reason: Option[String] = None)
    extends NoParamsResponseReasonRequest[RemoveGuildMemberRole] {
  override def route: RequestRoute = Routes.removeGuildMemberRole(guildId, userId, roleId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): RemoveGuildMemberRole = copy(reason = Some(reason))
}

/** Kicks a guild member. */
case class RemoveGuildMember(
    guildId: GuildId,
    userId: UserId,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[RemoveGuildMember] {
  override def route: RequestRoute = Routes.removeGuildMember(guildId, userId)

  override def requiredPermissions: Permission = Permission.KickMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): RemoveGuildMember = copy(reason = Some(reason))
}

/** Get all the bans for this guild. */
case class GetGuildBans(guildId: GuildId) extends NoParamsRequest[Seq[RawBan], Seq[Ban]] {
  override def route: RequestRoute = Routes.getGuildBans(guildId)

  override def responseDecoder: Decoder[Seq[RawBan]]           = Decoder[Seq[RawBan]]
  override def toNiceResponse(response: Seq[RawBan]): Seq[Ban] = response.map(_.toBan)

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/** Get the ban object for a specific member in a guild. */
case class GetGuildBan(guildId: GuildId, userId: UserId) extends NoParamsRequest[RawBan, Ban] {
  override def route: RequestRoute = Routes.getGuildBan(guildId, userId)

  override def responseDecoder: Decoder[RawBan]      = Decoder[RawBan]
  override def toNiceResponse(response: RawBan): Ban = response.toBan

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * @param deleteMessageDays
  *   The number of days to delete messages for this banned user.
  */
case class CreateGuildBanData(deleteMessageDays: Option[Int])

/** Ban a user from a guild. */
case class CreateGuildBan(
    guildId: GuildId,
    userId: UserId,
    params: CreateGuildBanData,
    reason: Option[String] = None
) extends NoResponseReasonRequest[CreateGuildBan, CreateGuildBanData] {
  override def route: RequestRoute = Routes.createGuildMemberBan(guildId, userId)

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildBan = copy(reason = Some(reason))

  override def paramsEncoder: Encoder[CreateGuildBanData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)
}
object CreateGuildBan {
  def mk(
      guildId: GuildId,
      userId: UserId,
      deleteMessageDays: Option[Int],
      reason: Option[String]
  ): CreateGuildBan = new CreateGuildBan(guildId, userId, CreateGuildBanData(deleteMessageDays), reason)
}

/** Unban a user from a guild. */
case class RemoveGuildBan(
    guildId: GuildId,
    userId: UserId,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[RemoveGuildBan] {
  override def route: RequestRoute = Routes.removeGuildMemberBan(guildId, userId)

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): RemoveGuildBan = copy(reason = Some(reason))
}

/** Get all the roles in a guild. */
case class GetGuildRoles(guildId: GuildId) extends RESTRequest[NotUsed, Seq[RawRole], Seq[Role]] {
  override def route: RequestRoute             = Routes.getGuildRole(guildId)
  override def params: NotUsed                 = NotUsed
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()

  override def responseDecoder: Decoder[Seq[RawRole]] = Decoder[Seq[RawRole]]
  override def toNiceResponse(response: Seq[RawRole]): Seq[Role] =
    response.map(_.toRole(guildId))
}

/**
  * @param name
  *   The name of the role.
  * @param permissions
  *   The permissions this role has.
  * @param color
  *   The color of the role.
  * @param hoist
  *   If this role is shown in the right sidebar.
  * @param mentionable
  *   If this role is mentionable.
  */
case class CreateGuildRoleData(
    name: Option[String] = None,
    permissions: Option[Permission] = None,
    color: Option[Int] = None,
    hoist: Option[Boolean] = None,
    mentionable: Option[Boolean] = None
)

/** Create a new role in a guild. */
case class CreateGuildRole(
    guildId: GuildId,
    params: CreateGuildRoleData,
    reason: Option[String] = None
) extends ReasonRequest[CreateGuildRole, CreateGuildRoleData, RawRole, Role] {
  override def route: RequestRoute = Routes.createGuildRole(guildId)
  override def paramsEncoder: Encoder[CreateGuildRoleData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[RawRole] = Decoder[RawRole]
  override def toNiceResponse(response: RawRole): Role =
    response.toRole(guildId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildRole = copy(reason = Some(reason))
}

/**
  * @param id
  *   The role id.
  * @param position
  *   The new position of the role.
  */
case class ModifyGuildRolePositionsData(id: RoleId, position: Int)

/** Modify the positions of several roles. */
case class ModifyGuildRolePositions(
    guildId: GuildId,
    params: Seq[ModifyGuildRolePositionsData],
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildRolePositions, Seq[ModifyGuildRolePositionsData], Seq[RawRole], Seq[Role]] {
  override def route: RequestRoute = Routes.modifyGuildRolePositions(guildId)
  override def paramsEncoder: Encoder[Seq[ModifyGuildRolePositionsData]] = {
    implicit val enc: Encoder[ModifyGuildRolePositionsData] =
      derivation.deriveEncoder(derivation.renaming.snakeCase, None)
    Encoder[Seq[ModifyGuildRolePositionsData]]
  }

  override def responseDecoder: Decoder[Seq[RawRole]]            = Decoder[Seq[RawRole]]
  override def toNiceResponse(response: Seq[RawRole]): Seq[Role] = response.map(_.toRole(guildId))

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildRolePositions = copy(reason = Some(reason))
}

/**
  * @param name
  *   The new name of the role.
  * @param permissions
  *   The new permissions this role has.
  * @param color
  *   The new color of the role.
  * @param hoist
  *   If this role is shown in the right sidebar.
  * @param mentionable
  *   If this role is mentionable.
  */
case class ModifyGuildRoleData(
    name: JsonOption[String] = JsonUndefined,
    permissions: JsonOption[Permission] = JsonUndefined,
    color: JsonOption[Int] = JsonUndefined,
    hoist: JsonOption[Boolean] = JsonUndefined,
    mentionable: JsonOption[Boolean] = JsonUndefined
)
object ModifyGuildRoleData {
  implicit val encoder: Encoder[ModifyGuildRoleData] = (a: ModifyGuildRoleData) =>
    JsonOption.removeUndefinedToObj(
      "name"        -> a.name.toJson,
      "permissions" -> a.permissions.toJson,
      "color"       -> a.color.toJson,
      "hoist"       -> a.hoist.toJson,
      "mentionable" -> a.mentionable.toJson
    )
}

/** Modify a role. */
case class ModifyGuildRole(
    guildId: GuildId,
    roleId: RoleId,
    params: ModifyGuildRoleData,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildRole, ModifyGuildRoleData, RawRole, Role] {
  override def route: RequestRoute = Routes.modifyGuildRole(guildId, roleId)
  override def paramsEncoder: Encoder[ModifyGuildRoleData] =
    ModifyGuildRoleData.encoder

  override def responseDecoder: Decoder[RawRole]       = Decoder[RawRole]
  override def toNiceResponse(response: RawRole): Role = response.toRole(guildId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildRole = copy(reason = Some(reason))
}

/** Delete a role in a guild. */
case class DeleteGuildRole(
    guildId: GuildId,
    roleId: RoleId,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteGuildRole] {
  override def route: RequestRoute = Routes.deleteGuildRole(guildId, roleId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): DeleteGuildRole = copy(reason = Some(reason))
}

/**
  * @param days
  *   The amount of days to prune for.
  * @param includeRoles
  *   Roles that should be ignored when checking for inactive users.
  */
case class GuildPruneCountData(days: Int, includeRoles: Seq[RoleId]) {
  require(days > 0 && days <= 30, "Days must be inbetween 1 and 30")
}

/** @param pruned The number of members that would be removed. */
case class GuildPruneCountResponse(pruned: Int)

/** Check how many members would be removed if a prune was started now. */
case class GetGuildPruneCount(guildId: GuildId, queryParams: GuildPruneCountData)
    extends NoParamsNiceResponseRequest[GuildPruneCountResponse] {
  override def route: RequestRoute =
    Routes.getGuildPruneCount(guildId, Some(queryParams.days), Some(queryParams.includeRoles))

  override def requiredPermissions: Permission = Permission.KickMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def responseDecoder: Decoder[GuildPruneCountResponse] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)
}
object GetGuildPruneCount {
  def mk(guildId: GuildId, days: Int, includeRoles: Seq[RoleId] = Nil): GetGuildPruneCount =
    new GetGuildPruneCount(guildId, GuildPruneCountData(days, includeRoles))
}

/**
  * @param days
  *   The amount of days to prune for.
  * @param computePruneCount
  *   If the pruned return field should be present.
  * @param includeRoles
  *   Roles that should be ignored when checking for inactive users.
  */
case class BeginGuildPruneData(
    days: Int,
    computePruneCount: Option[Boolean],
    includeRoles: Seq[RoleId],
    reason: Option[String] = None
) {
  require(days > 0 && days <= 30, "Days must be inbetween 1 and 30")
}

/** @param pruned The number of members that were removed. */
case class BeginGuildPruneResponse(pruned: Option[Int])

/** Begin a guild prune. */
case class BeginGuildPrune(
    guildId: GuildId,
    params: BeginGuildPruneData,
    reason: Option[String] = None
) extends NoNiceResponseRequest[BeginGuildPruneData, BeginGuildPruneResponse]
    with NoNiceResponseReasonRequest[BeginGuildPrune, BeginGuildPruneData, BeginGuildPruneResponse] {
  override def route: RequestRoute = Routes.beginGuildPrune(guildId)

  override def requiredPermissions: Permission = Permission.KickMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): BeginGuildPrune = copy(reason = Some(reason))

  override def responseDecoder: Decoder[BeginGuildPruneResponse] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)

  override def paramsEncoder: Encoder[BeginGuildPruneData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)
}
object BeginGuildPrune {
  def mk(
      guildId: GuildId,
      days: Int,
      computePruneCount: Boolean = true,
      includeRoles: Seq[RoleId] = Nil
  ): BeginGuildPrune =
    new BeginGuildPrune(guildId, BeginGuildPruneData(days, Some(computePruneCount), includeRoles))
}

/** Get the voice regions for this guild. */
case class GetGuildVoiceRegions(guildId: GuildId) extends NoParamsNiceResponseRequest[Seq[VoiceRegion]] {
  override def route: RequestRoute = Routes.getGuildVoiceRegions(guildId)

  override def responseDecoder: Decoder[Seq[VoiceRegion]] = Decoder[Seq[VoiceRegion]]
}

/** Get the invites for this guild. */
case class GetGuildInvites(guildId: GuildId) extends NoParamsNiceResponseRequest[Seq[InviteWithMetadata]] {
  override def route: RequestRoute = Routes.getGuildInvites(guildId)

  override def responseDecoder: Decoder[Seq[InviteWithMetadata]] = Decoder[Seq[InviteWithMetadata]]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/** Get the integrations for this guild. */
case class GetGuildIntegrations(guildId: GuildId) extends NoParamsNiceResponseRequest[Seq[Integration]] {
  override def route: RequestRoute = Routes.getGuildIntegrations(guildId)

  override def responseDecoder: Decoder[Seq[Integration]] = Decoder[Seq[Integration]]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/** Delete an integration. */
case class DeleteGuildIntegration(guildId: GuildId, integrationId: IntegrationId, reason: Option[String] = None)
    extends NoParamsResponseReasonRequest[DeleteGuildIntegration] {
  override def route: RequestRoute = Routes.deleteGuildIntegration(guildId, integrationId)

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): DeleteGuildIntegration = copy(reason = Some(reason))
}

/** Get the guild widget settings for a guild. */
case class GetGuildWidgetSettings(guildId: GuildId) extends NoParamsNiceResponseRequest[GuildWidgetSettings] {
  override def route: RequestRoute = Routes.getGuildWidget(guildId)

  override def responseDecoder: Decoder[GuildWidgetSettings] = Decoder[GuildWidgetSettings]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/** Modify a guild widget for a guild. */
case class ModifyGuildWidget(guildId: GuildId, params: GuildWidgetSettings, reason: Option[String] = None)
    extends NoNiceResponseReasonRequest[ModifyGuildWidget, GuildWidgetSettings, GuildWidgetSettings] {
  override def route: RequestRoute                         = Routes.modifyGuildWidget(guildId)
  override def paramsEncoder: Encoder[GuildWidgetSettings] = Encoder[GuildWidgetSettings]

  override def responseDecoder: Decoder[GuildWidgetSettings] = Decoder[GuildWidgetSettings]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildWidget = copy(reason = Some(reason))
}

/** Get the guild widget for a guild. */
case class GetGuildWidget(guildId: GuildId) extends NoParamsNiceResponseRequest[GuildWidget] {
  override def route: RequestRoute = Routes.getGuildWidgetJson(guildId)

  override def responseDecoder: Decoder[GuildWidget] = Decoder[GuildWidget]
}

case class VanityUrlResponse(code: String)

/** Get a partial invite object for guilds with that feature enabled. */
case class GetGuildVanityUrl(guildId: GuildId) extends NoParamsNiceResponseRequest[VanityUrlResponse] {
  override def route: RequestRoute = Routes.getGuildVanityUrl(guildId)

  override def responseDecoder: Decoder[VanityUrlResponse] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Get an invite for a given invite code.
  * @param withCounts
  *   If the returned invite object should return approximate counts for members
  *   and people online.
  * @param withExpiration
  *   If the invite should contain the expiration date.
  */
case class GetInvite(inviteCode: String, withCounts: Boolean = false, withExpiration: Boolean = false)
    extends NoParamsNiceResponseRequest[Invite] {
  override def route: RequestRoute              = Routes.getInvite(inviteCode, Some(withCounts), Some(withExpiration))
  override def responseDecoder: Decoder[Invite] = Decoder[Invite]
}

/** Delete an invite. */
case class DeleteInvite(inviteCode: String, reason: Option[String] = None)
    extends NoParamsNiceResponseReasonRequest[DeleteInvite, Invite] {
  override def route: RequestRoute = Routes.deleteInvite(inviteCode)

  override def responseDecoder: Decoder[Invite] = Decoder[Invite]

  //Can't say this without more context, so we're optimistic
  override def requiredPermissions: Permission = Permission.None

  //We don't track invites, so we'll have to stay optimistic with this one too
  override def hasPermissions(implicit c: CacheSnapshot): Boolean = true

  override def withReason(reason: String): DeleteInvite = copy(reason = Some(reason))
}

/** Fetch the client user. */
case object GetCurrentUser extends NoParamsNiceResponseRequest[User] {
  override def route: RequestRoute = Routes.getCurrentUser

  override def responseDecoder: Decoder[User] = Decoder[User]
}

case class ModifyCurrentUserData(
    username: JsonOption[String],
    avatar: JsonOption[ImageData]
)
object ModifyCurrentUserData {
  implicit val encoder: Encoder[ModifyCurrentUserData] = (a: ModifyCurrentUserData) =>
    JsonOption.removeUndefinedToObj(
      "username" -> a.username.toJson,
      "avatar"   -> a.avatar.toJson
    )
}

/** Modify the current user. */
case class ModifyCurrentUser(params: ModifyCurrentUserData) extends NoNiceResponseRequest[ModifyCurrentUserData, User] {
  override def route: RequestRoute = Routes.modifyCurrentUser
  override def paramsEncoder: Encoder[ModifyCurrentUserData] =
    ModifyCurrentUserData.encoder
  override def responseDecoder: Decoder[User] = Decoder[User]
}

/** Get a user by id. */
case class GetUser(userId: UserId) extends NoParamsNiceResponseRequest[User] {
  override def route: RequestRoute = Routes.getUser(userId)

  override def responseDecoder: Decoder[User] = Decoder[User]
}

case class GetUserGuildsGuild(
    id: GuildId,
    name: String,
    icon: Option[String],
    owner: Boolean,
    permissions: Permission,
    features: Seq[GuildFeature]
)

/**
  * @param before
  *   Get guilds before this id.
  * @param after
  *   Get guilds after this id.
  * @param limit
  *   The max amount of guilds to return.
  */
case class GetCurrentUserGuildsData(
    before: Option[GuildId] = None,
    after: Option[GuildId] = None,
    limit: Option[Int] = None
)

/** Get the guilds the client user is in. */
case class GetCurrentUserGuilds(queryParams: GetCurrentUserGuildsData)
    extends NoParamsNiceResponseRequest[Seq[GetUserGuildsGuild]] {
  override def route: RequestRoute =
    Routes.getCurrentUserGuilds(queryParams.before, queryParams.after, queryParams.limit)

  override def responseDecoder: Decoder[Seq[GetUserGuildsGuild]] = {
    implicit val dec: Decoder[GetUserGuildsGuild] = derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)
    Decoder[Seq[GetUserGuildsGuild]]
  }
}
object GetCurrentUserGuilds {
  def before(
      before: GuildId,
      limit: Option[Int] = None
  ): GetCurrentUserGuilds =
    new GetCurrentUserGuilds(GetCurrentUserGuildsData(before = Some(before), limit = limit))

  def after(
      after: GuildId,
      limit: Option[Int] = None
  ): GetCurrentUserGuilds =
    new GetCurrentUserGuilds(GetCurrentUserGuildsData(after = Some(after), limit = limit))
}

/** Leave a guild. */
case class LeaveGuild(guildId: GuildId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.leaveGuild(guildId)
}

/** @param recipientId User to send a DM to. */
case class CreateDMData(recipientId: UserId)

/** Create a new DM channel. */
case class CreateDm(params: CreateDMData) extends RESTRequest[CreateDMData, RawChannel, Option[DMChannel]] {
  override def route: RequestRoute                  = Routes.createDM
  override def paramsEncoder: Encoder[CreateDMData] = derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[RawChannel]                    = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[DMChannel] =
    //Safe
    response.toChannel(None).collect { case dmChannel: DMChannel => dmChannel }
}
object CreateDm {
  def mk(to: UserId): CreateDm = new CreateDm(CreateDMData(to))
}

/**
  * @param accessTokens
  *   The access tokens of users that have granted the bot the `gdm.join` scope.
  * @param nicks
  *   A map specifying the nicknames for the users in this group DM.
  */
case class CreateGroupDMData(accessTokens: Seq[String], nicks: SnowflakeMap[User, String])

/**
  * Create a group DM. By default the client is limited to 10 active group DMs.
  */
@deprecated("Deprecated by Discord", since = "0.13")
case class CreateGroupDm(params: CreateGroupDMData)
    extends RESTRequest[CreateGroupDMData, RawChannel, Option[GroupDMChannel]] {
  override def route: RequestRoute = Routes.createDM
  override def paramsEncoder: Encoder[CreateGroupDMData] = (data: CreateGroupDMData) => {
    Json
      .obj("access_tokens" -> data.accessTokens.asJson, "nicks" -> data.nicks.map(t => t._1.asString -> t._2).asJson)
  }

  override def responseDecoder: Decoder[RawChannel]                         = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[GroupDMChannel] =
    //Safe
    response.toChannel(None).collect { case dmChannel: GroupDMChannel => dmChannel }
}

/** Get a list of connection objects. Requires the `connection` OAuth2 scope. */
case object GetUserConnections extends NoParamsNiceResponseRequest[Seq[Connection]] {
  override def route: RequestRoute = Routes.getUserConnections

  override def responseDecoder: Decoder[Seq[Connection]] = Decoder[Seq[Connection]]
}

case class GetGuildWelcomeScreen(guildId: GuildId) extends NoParamsNiceResponseRequest[WelcomeScreen] {
  override def route: RequestRoute = Routes.getGuildWelcomeScreen(guildId)

  override def responseDecoder: Decoder[WelcomeScreen] = Decoder[WelcomeScreen]
}

case class ModifyGuildWelcomeScreenData(
    enabled: JsonOption[Boolean] = JsonUndefined,
    welcomeChannels: JsonOption[Seq[WelcomeScreenChannel]] = JsonUndefined,
    description: JsonOption[String] = JsonUndefined
)
object ModifyGuildWelcomeScreenData {
  implicit val encoder: Encoder[ModifyGuildWelcomeScreenData] = (a: ModifyGuildWelcomeScreenData) =>
    JsonOption.removeUndefinedToObj(
      "enabled"          -> a.enabled.toJson,
      "welcome_channels" -> a.welcomeChannels.toJson,
      "description"      -> a.description.toJson
    )
}

case class ModifyGuildWelcomeScreen(
    guildId: GuildId,
    params: ModifyGuildWelcomeScreenData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[ModifyGuildWelcomeScreen, ModifyGuildWelcomeScreenData, WelcomeScreen] {
  override def route: RequestRoute = Routes.modifyGuildWelcomeScreen(guildId)

  override def paramsEncoder: Encoder[ModifyGuildWelcomeScreenData] = ModifyGuildWelcomeScreenData.encoder
  override def responseDecoder: Decoder[WelcomeScreen]              = Decoder[WelcomeScreen]

  override def withReason(reason: String): ModifyGuildWelcomeScreen = copy(reason = Some(reason))
}

case class UpdateCurrentUserVoiceStateData(
    channelId: StageGuildChannelId,
    suppress: JsonOption[Boolean],
    requestToSpeakTimestamp: JsonOption[OffsetDateTime]
)
object UpdateCurrentUserVoiceStateData {
  implicit val encoder: Encoder[UpdateCurrentUserVoiceStateData] = (a: UpdateCurrentUserVoiceStateData) =>
    JsonOption.removeUndefinedToObj(
      "channel_id"                 -> JsonSome(a.channelId.asJson),
      "suppress"                   -> a.suppress.toJson,
      "request_to_speak_timestamp" -> a.requestToSpeakTimestamp.toJson
    )
}
case class UpdateCurrentUserVoiceState(guildId: GuildId, params: UpdateCurrentUserVoiceStateData)
    extends NoResponseRequest[UpdateCurrentUserVoiceStateData] {
  override def route: RequestRoute = Routes.updateCurrentUserVoiceState(guildId)

  override def paramsEncoder: Encoder[UpdateCurrentUserVoiceStateData] =
    UpdateCurrentUserVoiceStateData.encoder

  override def hasPermissions(implicit c: CacheSnapshot): Boolean = {
    val suppressCheck =
      params.suppress.forall(_ == true) || hasPermissionsChannel(params.channelId, Permission.MuteMembers)
    val speakCheck =
      params.requestToSpeakTimestamp.isEmpty || hasPermissionsChannel(params.channelId, Permission.RequestToSpeak)

    suppressCheck && speakCheck
  }
}

case class UpdateUserVoiceStateData(
    channelId: StageGuildChannelId,
    suppress: JsonOption[Boolean]
)
object UpdateUserVoiceStateData {
  implicit val encoder: Encoder[UpdateUserVoiceStateData] = (a: UpdateUserVoiceStateData) =>
    JsonOption.removeUndefinedToObj(
      "channel_id" -> JsonSome(a.channelId.asJson),
      "suppress"   -> a.suppress.toJson
    )
}
case class UpdateUserVoiceState(guildId: GuildId, userId: UserId, params: UpdateUserVoiceStateData)
    extends NoResponseRequest[UpdateUserVoiceStateData] {
  override def route: RequestRoute = Routes.updateUserVoiceState(guildId, userId)

  override def paramsEncoder: Encoder[UpdateUserVoiceStateData] =
    UpdateUserVoiceStateData.encoder

  override def requiredPermissions: Permission = super.requiredPermissions
}
