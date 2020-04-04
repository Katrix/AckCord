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

import ackcord.data.DiscordProtocol._
import ackcord.data._
import ackcord.data.raw._
import ackcord.util.{JsonOption, JsonSome, JsonUndefined}
import ackcord.{CacheSnapshot, SnowflakeMap}
import akka.NotUsed
import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.syntax._

/**
  * @param name The name of the guild
  * @param region The voice region for the guild
  * @param icon The icon to use for the guild. Must be 128x128 jpeg.
  * @param verificationLevel The verification level to use for the guild.
  * @param defaultMessageNotifications The notification level to use for
  *                                    the guild.
  * @param roles The roles for the new guild. Note, here the snowflake is
  *              just a placeholder.
  * @param channels The channels for the new guild.
  * @param afkChannelId The id for the AFK channel
  * @param afkTimeout The timeout in seconds until users are moved to the AFK channel.
  * @param systemChannelId The id of the system channel.
  */
case class CreateGuildData(
    name: String,
    region: Option[String],
    icon: Option[ImageData],
    verificationLevel: Option[VerificationLevel],
    defaultMessageNotifications: Option[NotificationLevel],
    explicitContentFilter: Option[FilterLevel],
    roles: Option[Seq[Role]],
    channels: Option[Seq[CreateGuildChannelData]], //Technically this should be partial channels, but I think this works too
    afkChannelId: Option[ChannelId],
    afkTimeout: Option[Int],
    systemChannelId: Option[ChannelId]
) {
  require(name.length >= 2 && name.length <= 100, "The guild name has to be between 2 and 100 characters")
}

/**
  * Create a new guild. Can only be used by bots in less than 10 guilds.
  */
case class CreateGuild(params: CreateGuildData) extends RESTRequest[CreateGuildData, RawGuild, Option[Guild]] {
  override def route: RequestRoute = Routes.createGuild
  override def paramsEncoder: Encoder[CreateGuildData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[RawGuild]                = Decoder[RawGuild]
  override def toNiceResponse(response: RawGuild): Option[Guild] = response.toGuild
}

/**
  * Get a guild by id.
  */
case class GetGuild(guildId: GuildId) extends NoParamsRequest[RawGuild, Option[Guild]] {
  override def route: RequestRoute = Routes.getGuild(guildId)

  override def responseDecoder: Decoder[RawGuild]                = Decoder[RawGuild]
  override def toNiceResponse(response: RawGuild): Option[Guild] = response.toGuild
}

/**
  * Get a guild preview by it's id. Only usable for public guilds.
  */
case class GetGuildPreview(guildId: GuildId) extends NoParamsNiceResponseRequest[GuildPreview] {
  override def route: RequestRoute = Routes.getGuildPreview(guildId)

  override def responseDecoder: Decoder[GuildPreview] = Decoder[GuildPreview]
}

/**
  * @param name The new name of the guild
  * @param region The new voice region for the guild
  * @param verificationLevel The new verification level to use for the guild.
  * @param defaultMessageNotifications The new notification level to use
  *                                    for the guild.
  * @param afkChannelId The new afk channel of the guild.
  * @param afkTimeout The new afk timeout in seconds for the guild.
  * @param icon The new icon to use for the guild. Must be 128x128 jpeg.
  * @param ownerId Transfer ownership of this guild. Must be the owner.
  * @param splash The new splash for the guild. Must be 128x128 jpeg. VIP only.
  * @param banner The new banner for the guild. Must be 128x128 jpeg. VIP only.
  * @param systemChannelId The new channel which system messages will be sent to.
  */
case class ModifyGuildData(
    name: Option[String] = None,
    region: Option[String] = None,
    verificationLevel: Option[VerificationLevel] = None,
    defaultMessageNotifications: Option[NotificationLevel] = None,
    explicitContentFilter: Option[FilterLevel] = None,
    afkChannelId: Option[ChannelId] = None,
    afkTimeout: Option[Int] = None,
    icon: Option[ImageData] = None,
    ownerId: Option[UserId] = None,
    splash: Option[ImageData] = None,
    banner: Option[ImageData] = None,
    systemChannelId: Option[ChannelId] = None
)

/**
  * Modify an existing guild.
  */
case class ModifyGuild(
    guildId: GuildId,
    params: ModifyGuildData,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuild, ModifyGuildData, RawGuild, Option[Guild]] {
  override def route: RequestRoute                     = Routes.modifyGuild(guildId)
  override def paramsEncoder: Encoder[ModifyGuildData] = derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[RawGuild]                = Decoder[RawGuild]
  override def toNiceResponse(response: RawGuild): Option[Guild] = response.toGuild

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuild = copy(reason = Some(reason))
}

/**
  * Delete a guild. Must be the owner.
  */
case class DeleteGuild(guildId: GuildId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteGuild(guildId)
}

/**
  * Get all the channels for a guild.
  */
case class GetGuildChannels(guildId: GuildId) extends NoParamsRequest[Seq[RawChannel], Seq[Option[GuildChannel]]] {
  override def route: RequestRoute = Routes.getGuildChannels(guildId)

  override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
  override def toNiceResponse(response: Seq[RawChannel]): Seq[Option[GuildChannel]] =
    response.map(_.toGuildChannel(guildId))
}

/**
  * @param name The name of the channel.
  * @param `type` The channel type.
  * @param topic The topic to give this channel.
  * @param bitrate The bitrate for the channel if it's a voice channel.
  * @param userLimit The user limit for the channel if it's a voice channel.
  * @param rateLimitPerUser The user ratelimit to give this channel.
  * @param permissionOverwrites The permission overwrites for the channel.
  * @param parentId The category id for the channel.
  * @param nsfw If the channel is NSFW.
  */
case class CreateGuildChannelData(
    name: String,
    `type`: JsonOption[ChannelType] = JsonUndefined,
    topic: JsonOption[String] = JsonUndefined,
    bitrate: JsonOption[Int] = JsonUndefined,
    userLimit: JsonOption[Int] = JsonUndefined,
    rateLimitPerUser: JsonOption[Int] = JsonUndefined,
    permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
    parentId: JsonOption[ChannelId] = JsonUndefined,
    nsfw: JsonOption[Boolean] = JsonUndefined
) {
  require(name.length >= 2 && name.length <= 100, "A channel name has to be between 2 and 100 characters")
  require(rateLimitPerUser.forall(i => i >= 0 && i <= 21600), "Rate limit per user must be between 0 ad 21600")
}
object CreateGuildChannelData {
  implicit val encoder: Encoder[CreateGuildChannelData] = (a: CreateGuildChannelData) =>
    JsonOption.removeUndefinedToObj(
      "name"                  -> JsonSome(a.name.asJson),
      "type"                  -> a.`type`.map(_.asJson),
      "topic"                 -> a.topic.map(_.asJson),
      "bitrate"               -> a.bitrate.map(_.asJson),
      "user_limit"            -> a.userLimit.map(_.asJson),
      "rate_limit_per_user"   -> a.rateLimitPerUser.map(_.asJson),
      "permission_overwrites" -> a.permissionOverwrites.map(_.asJson),
      "parent_id"             -> a.parentId.map(_.asJson),
      "nsfw"                  -> a.nsfw.map(_.asJson)
    )
}

/**
  * Create a channel in a guild.
  */
case class CreateGuildChannel(
    guildId: GuildId,
    params: CreateGuildChannelData,
    reason: Option[String] = None
) extends ReasonRequest[CreateGuildChannel, CreateGuildChannelData, RawChannel, Option[GuildChannel]] {
  override def route: RequestRoute                            = Routes.createGuildChannel(guildId)
  override def paramsEncoder: Encoder[CreateGuildChannelData] = CreateGuildChannelData.encoder

  override def jsonPrinter: Printer = Printer.noSpaces

  override def responseDecoder: Decoder[RawChannel]                       = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[GuildChannel] = response.toGuildChannel(guildId)

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildChannel = copy(reason = Some(reason))
}

/**
  * @param id The channel id
  * @param position It's new position
  */
case class ModifyGuildChannelPositionsData(id: ChannelId, position: Int)

/**
  * Modify the positions of several channels.
  */
case class ModifyGuildChannelPositions(
    guildId: GuildId,
    params: Seq[ModifyGuildChannelPositionsData],
    reason: Option[String] = None
) extends NoResponseReasonRequest[ModifyGuildChannelPositions, Seq[ModifyGuildChannelPositionsData]] {
  override def route: RequestRoute = Routes.modifyGuildChannelsPositions(guildId)
  override def paramsEncoder: Encoder[Seq[ModifyGuildChannelPositionsData]] = {
    implicit val enc: Encoder[ModifyGuildChannelPositionsData] =
      derivation.deriveEncoder(derivation.renaming.snakeCase, None)
    Encoder[Seq[ModifyGuildChannelPositionsData]]
  }

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildChannelPositions = copy(reason = Some(reason))
}

trait GuildMemberRequest[Params] extends RESTRequest[Params, RawGuildMember, GuildMember] {
  def guildId: GuildId

  override def responseDecoder: Decoder[RawGuildMember]              = Decoder[RawGuildMember]
  override def toNiceResponse(response: RawGuildMember): GuildMember = response.toGuildMember(guildId)
}

/**
  * Get a guild member by id.
  */
case class GetGuildMember(guildId: GuildId, userId: UserId) extends GuildMemberRequest[NotUsed] {
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
  override def params: NotUsed                 = NotUsed
  override def route: RequestRoute             = Routes.getGuildMember(guildId, userId)
}

/**
  * @param limit The max amount of members to get
  * @param after Get userIds after this id
  */
case class ListGuildMembersData(limit: Option[Int] = None, after: Option[UserId] = None)

/**
  * Get all the guild members in this guild.
  */
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

/**
  * @param accessToken The OAuth2 access token.
  * @param nick The nickname to give to the user.
  * @param roles The roles to give to the user.
  * @param mute If the user should be muted.
  * @param deaf If the user should be deafened.
  */
case class AddGuildMemberData(
    accessToken: String,
    nick: Option[String] = None,
    roles: Option[Seq[RoleId]] = None,
    mute: Option[Boolean] = None,
    deaf: Option[Boolean] = None
)

/**
  * Adds a user to a guild. Requires the `guilds.join` OAuth2 scope.
  */
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
  * @param nick The nickname to give to the user.
  * @param roles The roles to give to the user.
  * @param mute If the user should be muted. Will return an error if the user
  *             is not in a voice channel.
  * @param deaf If the user should be deafened. Will return an error if the user
  *             is not in a voice channel.
  * @param channelId The id of the channel to move the user to.
  */
case class ModifyGuildMemberData(
    nick: JsonOption[String] = JsonUndefined,
    roles: JsonOption[Seq[RoleId]] = JsonUndefined,
    mute: JsonOption[Boolean] = JsonUndefined,
    deaf: JsonOption[Boolean] = JsonUndefined,
    channelId: JsonOption[ChannelId] = JsonUndefined
)
object ModifyGuildMemberData {
  implicit val encoder: Encoder[ModifyGuildMemberData] = (a: ModifyGuildMemberData) =>
    JsonOption.removeUndefinedToObj(
      "nick"       -> a.nick.map(_.asJson),
      "roles"      -> a.roles.map(_.asJson),
      "mute"       -> a.mute.map(_.asJson),
      "deaf"       -> a.deaf.map(_.asJson),
      "channel_id" -> a.channelId.map(_.asJson)
    )
}

/**
  * Modify a guild member.
  */
case class ModifyGuildMember(
    guildId: GuildId,
    userId: UserId,
    params: ModifyGuildMemberData,
    reason: Option[String] = None
) extends NoResponseReasonRequest[ModifyGuildMember, ModifyGuildMemberData] {
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
}

case class ModifyBotUsersNickData(nick: String)

/**
  * Modify the clients nickname.
  */
case class ModifyBotUsersNick(
    guildId: GuildId,
    params: ModifyBotUsersNickData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[ModifyBotUsersNick, ModifyBotUsersNickData, String] {
  override def route: RequestRoute = Routes.modifyCurrentNick(guildId)
  override def paramsEncoder: Encoder[ModifyBotUsersNickData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[String] = Decoder[String]

  override def requiredPermissions: Permission = Permission.ChangeNickname
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyBotUsersNick = copy(reason = Some(reason))
}
object ModifyBotUsersNick {
  def mk(guildId: GuildId, nick: String): ModifyBotUsersNick =
    new ModifyBotUsersNick(guildId, ModifyBotUsersNickData(nick))
}

/**
  * Add a role to a guild member.
  */
case class AddGuildMemberRole(guildId: GuildId, userId: UserId, roleId: RoleId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.addGuildMemberRole(guildId, userId, roleId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Remove a role from a guild member.
  */
case class RemoveGuildMemberRole(guildId: GuildId, userId: UserId, roleId: RoleId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.removeGuildMemberRole(guildId, userId, roleId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Kicks a guild member.
  */
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

/**
  * Get all the bans for this guild.
  */
case class GetGuildBans(guildId: GuildId) extends NoParamsRequest[Seq[RawBan], Seq[Ban]] {
  override def route: RequestRoute = Routes.getGuildBans(guildId)

  override def responseDecoder: Decoder[Seq[RawBan]]           = Decoder[Seq[RawBan]]
  override def toNiceResponse(response: Seq[RawBan]): Seq[Ban] = response.map(_.toBan)

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Get the ban object for a specific member in a guild.
  */
case class GetGuildBan(guildId: GuildId, userId: UserId) extends NoParamsRequest[RawBan, Ban] {
  override def route: RequestRoute = Routes.getGuildBan(guildId, userId)

  override def responseDecoder: Decoder[RawBan]      = Decoder[RawBan]
  override def toNiceResponse(response: RawBan): Ban = response.toBan

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * @param deleteMessageDays The number of days to delete messages for
  *                          this banned user.
  * @param reason The reason for the ban.
  */
case class CreateGuildBanData(deleteMessageDays: Option[Int], reason: Option[String])

/**
  * Ban a user from a guild.
  */
case class CreateGuildBan(
    guildId: GuildId,
    userId: UserId,
    queryParams: CreateGuildBanData,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[CreateGuildBan] {
  override def route: RequestRoute =
    Routes.createGuildMemberBan(guildId, userId, queryParams.deleteMessageDays, queryParams.reason)

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildBan = copy(reason = Some(reason))
}
object CreateGuildBan {
  def mk(
      guildId: GuildId,
      userId: UserId,
      deleteMessageDays: Option[Int],
      reason: Option[String]
  ): CreateGuildBan = new CreateGuildBan(guildId, userId, CreateGuildBanData(deleteMessageDays, reason))
}

/**
  * Unban a user from a guild.
  */
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

/**
  * Get all the roles in a guild.
  */
case class GetGuildRoles(guildId: GuildId) extends RESTRequest[NotUsed, Seq[RawRole], Seq[Role]] {
  override def route: RequestRoute             = Routes.getGuildRole(guildId)
  override def params: NotUsed                 = NotUsed
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()

  override def responseDecoder: Decoder[Seq[RawRole]] = Decoder[Seq[RawRole]]
  override def toNiceResponse(response: Seq[RawRole]): Seq[Role] =
    response.map(_.toRole(guildId))
}

/**
  * @param name The name of the role.
  * @param permissions The permissions this role has.
  * @param color The color of the role.
  * @param hoist If this role is shown in the right sidebar.
  * @param mentionable If this role is mentionable.
  */
case class CreateGuildRoleData(
    name: Option[String] = None,
    permissions: Option[Permission] = None,
    color: Option[Int] = None,
    hoist: Option[Boolean] = None,
    mentionable: Option[Boolean] = None
)

/**
  * Create a new role in a guild.
  */
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
  * @param id The role id.
  * @param position The new position of the role.
  */
case class ModifyGuildRolePositionsData(id: RoleId, position: Int)

/**
  * Modify the positions of several roles.
  */
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
  * @param name The new name of the role.
  * @param permissions The new permissions this role has.
  * @param color The new color of the role.
  * @param hoist If this role is shown in the right sidebar.
  * @param mentionable If this role is mentionable.
  */
case class ModifyGuildRoleData(
    name: Option[String] = None,
    permissions: Option[Permission] = None,
    color: Option[Int] = None,
    hoist: Option[Boolean] = None,
    mentionable: Option[Boolean] = None
)

/**
  * Modify a role.
  */
case class ModifyGuildRole(
    guildId: GuildId,
    roleId: RoleId,
    params: ModifyGuildRoleData,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildRole, ModifyGuildRoleData, RawRole, Role] {
  override def route: RequestRoute = Routes.modifyGuildRole(guildId, roleId)
  override def paramsEncoder: Encoder[ModifyGuildRoleData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[RawRole]       = Decoder[RawRole]
  override def toNiceResponse(response: RawRole): Role = response.toRole(guildId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildRole = copy(reason = Some(reason))
}

/**
  * Delete a role in a guild.
  */
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
  * @param days The amount of days to prune for.
  */
case class GuildPruneCountData(days: Int) {
  require(days > 0 && days <= 30, "Days must be inbetween 1 and 30")
}

/**
  * @param pruned The number of members that would be removed.
  */
case class GuildPruneCountResponse(pruned: Int)

/**
  * Check how many members would be removed if a prune was started now.
  */
case class GetGuildPruneCount(guildId: GuildId, queryParams: GuildPruneCountData)
    extends NoParamsNiceResponseRequest[GuildPruneCountResponse] {
  override def route: RequestRoute = Routes.getGuildPruneCount(guildId, Some(queryParams.days))

  override def requiredPermissions: Permission = Permission.KickMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def responseDecoder: Decoder[GuildPruneCountResponse] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)
}
object GetGuildPruneCount {
  def mk(guildId: GuildId, days: Int): GetGuildPruneCount =
    new GetGuildPruneCount(guildId, GuildPruneCountData(days))
}

/**
  * @param days The amount of days to prune for.
  * @param computePruneCount If the pruned return field should be present.
  */
case class BeginGuildPruneData(days: Int, computePruneCount: Option[Boolean])

/**
  * @param pruned The number of members that were removed.
  */
case class BeginGuildPruneResponse(pruned: Option[Int])

/**
  * Begin a guild prune.
  */
case class BeginGuildPrune(
    guildId: GuildId,
    queryParams: BeginGuildPruneData,
    reason: Option[String] = None
) extends NoParamsNiceResponseRequest[BeginGuildPruneResponse]
    with NoParamsNiceResponseReasonRequest[BeginGuildPrune, BeginGuildPruneResponse] {
  override def route: RequestRoute =
    Routes.beginGuildPrune(guildId, Some(queryParams.days), queryParams.computePruneCount)

  override def requiredPermissions: Permission = Permission.KickMembers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): BeginGuildPrune = copy(reason = Some(reason))

  override def responseDecoder: Decoder[BeginGuildPruneResponse] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)
}
object BeginGuildPrune {
  def mk(
      guildId: GuildId,
      days: Int,
      computePruneCount: Boolean = true
  ): BeginGuildPrune =
    new BeginGuildPrune(guildId, BeginGuildPruneData(days, Some(computePruneCount)))
}

/**
  * Get the voice regions for this guild.
  */
case class GetGuildVoiceRegions(guildId: GuildId) extends NoParamsNiceResponseRequest[Seq[VoiceRegion]] {
  override def route: RequestRoute = Routes.getGuildVoiceRegions(guildId)

  override def responseDecoder: Decoder[Seq[VoiceRegion]] = Decoder[Seq[VoiceRegion]]
}

/**
  * Get the invites for this guild.
  */
case class GetGuildInvites(guildId: GuildId) extends NoParamsNiceResponseRequest[Seq[InviteWithMetadata]] {
  override def route: RequestRoute = Routes.getGuildInvites(guildId)

  override def responseDecoder: Decoder[Seq[InviteWithMetadata]] = Decoder[Seq[InviteWithMetadata]]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Get the integrations for this guild.
  */
case class GetGuildIntegrations(guildId: GuildId) extends NoParamsNiceResponseRequest[Seq[Integration]] {
  override def route: RequestRoute = Routes.getGuildIntegrations(guildId)

  override def responseDecoder: Decoder[Seq[Integration]] = Decoder[Seq[Integration]]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * @param `type` The integration type
  * @param id The integration id
  */
case class CreateGuildIntegrationData(`type`: String /*TODO: Enum here*/, id: IntegrationId)

/**
  * Attach an integration to a guild.
  */
case class CreateGuildIntegration(
    guildId: GuildId,
    params: CreateGuildIntegrationData
) extends NoResponseRequest[CreateGuildIntegrationData] {
  override def route: RequestRoute = Routes.createGuildIntegrations(guildId)
  override def paramsEncoder: Encoder[CreateGuildIntegrationData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * @param expireBehavior The behavior of expiring subscribers.
  * @param expireGracePeriod The grace period before expiring subscribers.
  * @param enableEmoticons If emojis should be synced for this integration.
  *                        (Twitch only)
  */
case class ModifyGuildIntegrationData(
    expireBehavior: IntegrationExpireBehavior,
    expireGracePeriod: Int,
    enableEmoticons: Boolean
)

/**
  * Modify an existing integration for a guild.
  */
case class ModifyGuildIntegration(
    guildId: GuildId,
    integrationId: IntegrationId,
    params: ModifyGuildIntegrationData
) extends NoResponseRequest[ModifyGuildIntegrationData] {
  override def route: RequestRoute = Routes.modifyGuildIntegration(guildId, integrationId)
  override def paramsEncoder: Encoder[ModifyGuildIntegrationData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Delete an integration.
  */
case class DeleteGuildIntegration(guildId: GuildId, integrationId: IntegrationId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteGuildIntegration(guildId, integrationId)

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Sync an integration.
  */
case class SyncGuildIntegration(guildId: GuildId, integrationId: IntegrationId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.syncGuildIntegration(guildId, integrationId)

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Get the guild embed for a guild.
  */
case class GetGuildEmbed(guildId: GuildId) extends NoParamsNiceResponseRequest[GuildEmbed] {
  override def route: RequestRoute = Routes.getGuildEmbed(guildId)

  override def responseDecoder: Decoder[GuildEmbed] = Decoder[GuildEmbed]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Modify a guild embed for a guild.
  */
case class ModifyGuildEmbed(guildId: GuildId, params: GuildEmbed)
    extends NoNiceResponseRequest[GuildEmbed, GuildEmbed] {
  override def route: RequestRoute                = Routes.modifyGuildEmbed(guildId)
  override def paramsEncoder: Encoder[GuildEmbed] = Encoder[GuildEmbed]

  override def responseDecoder: Decoder[GuildEmbed] = Decoder[GuildEmbed]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)
}

case class VanityUrlResponse(code: String)

/**
  * Get a partial invite object for guilds with that feature enabled.
  */
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
  * @param withCounts If the returned invite object should return approximate
  *                   counts for members and people online.
  */
case class GetInvite(inviteCode: String, withCounts: Boolean = false) extends NoParamsNiceResponseRequest[Invite] {
  override def route: RequestRoute = {
    val raw = Routes.getInvite(inviteCode)
    raw.copy(uri = raw.uri.withQuery(Uri.Query("with_counts" -> withCounts.toString)))
  }

  override def responseDecoder: Decoder[Invite] = Decoder[Invite]
}

/**
  * Delete an invite.
  */
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

/**
  * Fetch the client user.
  */
case object GetCurrentUser extends NoParamsNiceResponseRequest[User] {
  override def route: RequestRoute = Routes.getCurrentUser

  override def responseDecoder: Decoder[User] = Decoder[User]
}

case class ModifyCurrentUserData(
    username: Option[String],
    avatar: Option[ImageData]
)

/**
  * Modify the current user.
  */
case class ModifyCurrentUser(params: ModifyCurrentUserData) extends NoNiceResponseRequest[ModifyCurrentUserData, User] {
  override def route: RequestRoute = Routes.modifyCurrentUser
  override def paramsEncoder: Encoder[ModifyCurrentUserData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)
  override def responseDecoder: Decoder[User] = Decoder[User]
}

/**
  * Get a user by id.
  */
case class GetUser(userId: UserId) extends NoParamsNiceResponseRequest[User] {
  override def route: RequestRoute = Routes.getUser(userId)

  override def responseDecoder: Decoder[User] = Decoder[User]
}

case class GetUserGuildsGuild(id: GuildId, name: String, icon: Option[String], owner: Boolean, permissions: Permission)

/**
  * @param before Get guilds before this id.
  * @param after Get guilds after this id.
  * @param limit The max amount of guilds to return.
  */
case class GetCurrentUserGuildsData(
    before: Option[GuildId] = None,
    after: Option[GuildId] = None,
    limit: Option[Int] = None
)

/**
  * Get the guilds the client user is in.
  */
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

/**
  * Leave a guild.
  */
case class LeaveGuild(guildId: GuildId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.leaveGuild(guildId)
}

/**
  * @param recipientId User to send a DM to.
  */
case class CreateDMData(recipientId: UserId)

/**
  * Create a new DM channel.
  */
case class CreateDm(params: CreateDMData) extends RESTRequest[CreateDMData, RawChannel, Option[DMChannel]] {
  override def route: RequestRoute                  = Routes.createDM
  override def paramsEncoder: Encoder[CreateDMData] = derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[RawChannel] = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[DMChannel] =
    response.toChannel.collect { case dmChannel: DMChannel => dmChannel }
}
object CreateDm {
  def mk(to: UserId): CreateDm = new CreateDm(CreateDMData(to))
}

/**
  * @param accessTokens The access tokens of users that have granted the bot
  *                     the `gdm.join` scope.
  * @param nicks A map specifying the nicknames for the users in this group DM.
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

  override def responseDecoder: Decoder[RawChannel] = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[GroupDMChannel] =
    response.toChannel.collect { case dmChannel: GroupDMChannel => dmChannel }
}

/**
  * Get a list of connection objects. Requires the `connection` OAuth2 scope.
  */
case object GetUserConnections extends NoParamsNiceResponseRequest[Seq[Connection]] {
  override def route: RequestRoute = Routes.getUserConnections

  override def responseDecoder: Decoder[Seq[Connection]] = Decoder[Seq[Connection]]
}
