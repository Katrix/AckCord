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
package net.katsstuff.ackcord.http.rest

import scala.language.higherKinds

import akka.NotUsed
import cats.Monad
import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.data.raw._
import net.katsstuff.ackcord.http.Routes
import net.katsstuff.ackcord.http.requests.RequestRoute
import net.katsstuff.ackcord.{CacheSnapshot, SnowflakeMap}
import net.katsstuff.ackcord.data.DiscordProtocol._
import net.katsstuff.ackcord.util.{JsonOption, JsonSome, JsonUndefined}

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
  */
case class CreateGuildData(
    name: String,
    region: String,
    icon: Option[ImageData],
    verificationLevel: VerificationLevel,
    defaultMessageNotifications: NotificationLevel,
    explicitContentFilter: FilterLevel,
    roles: Seq[Role],
    channels: Seq[CreateGuildChannelData] //Technically this should be partial channels, but I think this works too
) {
  require(name.length >= 2 && name.length <= 100, "The guild name has to be between 2 and 100 characters")
}

/**
  * Create a new guild. Bots can only have 10 guilds by default.
  */
case class CreateGuild[Ctx](params: CreateGuildData, context: Ctx = NotUsed: NotUsed)
    extends RESTRequest[CreateGuildData, RawGuild, Option[Guild], Ctx] {
  override def route: RequestRoute = Routes.createGuild
  override def paramsEncoder: Encoder[CreateGuildData] =
    deriveEncoder[CreateGuildData]

  override def responseDecoder:                    Decoder[RawGuild] = Decoder[RawGuild]
  override def toNiceResponse(response: RawGuild): Option[Guild]     = response.toGuild
}

/**
  * Get a guild by id.
  */
case class GetGuild[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsRequest[RawGuild, Option[Guild], Ctx] {
  override def route: RequestRoute = Routes.getGuild(guildId)

  override def responseDecoder:                    Decoder[RawGuild] = Decoder[RawGuild]
  override def toNiceResponse(response: RawGuild): Option[Guild]     = response.toGuild
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
    systemChannelId: Option[ChannelId] = None
)

/**
  * Modify an existing guild.
  */
case class ModifyGuild[Ctx](
    guildId: GuildId,
    params: ModifyGuildData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuild[Ctx], ModifyGuildData, RawGuild, Option[Guild], Ctx] {
  override def route:         RequestRoute             = Routes.modifyGuild(guildId)
  override def paramsEncoder: Encoder[ModifyGuildData] = deriveEncoder[ModifyGuildData]

  override def responseDecoder:                    Decoder[RawGuild] = Decoder[RawGuild]
  override def toNiceResponse(response: RawGuild): Option[Guild]     = response.toGuild

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuild[Ctx] = copy(reason = Some(reason))
}

/**
  * Delete a guild. Must be the owner.
  */
case class DeleteGuild[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed) extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.deleteGuild(guildId)
}

/**
  * Get all the channels for a guild.
  */
case class GetGuildChannels[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsRequest[Seq[RawChannel], Seq[Option[GuildChannel]], Ctx] {
  override def route: RequestRoute = Routes.getGuildChannels(guildId)

  override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
  override def toNiceResponse(response: Seq[RawChannel]): Seq[Option[GuildChannel]] =
    response.map(_.toGuildChannel(guildId))
}

/**
  * @param name The name of the channel.
  * @param `type` The channel type.
  * @param bitrate The bitrate for the channel if it's a voice channel.
  * @param userLimit The user limit for the channel if it's a voice channel.
  * @param permissionOverwrites The permission overwrites for the channel.
  * @param parentId The category id for the channel.
  * @param nsfw If the channel is NSFW.
  */
case class CreateGuildChannelData(
    name: String,
    `type`: JsonOption[ChannelType] = JsonUndefined,
    bitrate: JsonOption[Int] = JsonUndefined,
    userLimit: JsonOption[Int] = JsonUndefined,
    permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
    parentId: JsonOption[ChannelId] = JsonUndefined,
    nsfw: JsonOption[Boolean] = JsonUndefined
) {
  require(name.length >= 2 && name.length <= 100, "A channel name has to be between 2 and 100 characters")
}
object CreateGuildChannelData {
  implicit val encoder: Encoder[CreateGuildChannelData] = (a: CreateGuildChannelData) =>
    JsonOption.removeUndefinedToObj(
      "name"                  -> JsonSome(a.name.asJson),
      "type"                  -> a.`type`.map(_.asJson),
      "bitrate"               -> a.bitrate.map(_.asJson),
      "user_limit"            -> a.userLimit.map(_.asJson),
      "permission_overwrites" -> a.permissionOverwrites.map(_.asJson),
      "parent_id"             -> a.parentId.map(_.asJson),
      "nsfw"                  -> a.nsfw.map(_.asJson)
  )
}

/**
  * Create a channel in a guild.
  */
case class CreateGuildChannel[Ctx](
    guildId: GuildId,
    params: CreateGuildChannelData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends ReasonRequest[CreateGuildChannel[Ctx], CreateGuildChannelData, RawChannel, Option[GuildChannel], Ctx] {
  override def route:         RequestRoute                    = Routes.createGuildChannel(guildId)
  override def paramsEncoder: Encoder[CreateGuildChannelData] = CreateGuildChannelData.encoder

  override def jsonPrinter: Printer = Printer.noSpaces

  override def responseDecoder:                      Decoder[RawChannel]  = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[GuildChannel] = response.toGuildChannel(guildId)

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildChannel[Ctx] = copy(reason = Some(reason))
}

/**
  * @param id The channel id
  * @param position It's new position
  */
case class ModifyGuildChannelPositionsData(id: ChannelId, position: Int)

/**
  * Modify the positions of several channels.
  */
case class ModifyGuildChannelPositions[Ctx](
    guildId: GuildId,
    params: Seq[ModifyGuildChannelPositionsData],
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildChannelPositions[Ctx], Seq[ModifyGuildChannelPositionsData], Seq[RawChannel], Seq[
      Option[Channel]
    ], Ctx] {
  override def route: RequestRoute = Routes.modifyGuildChannelsPositions(guildId)
  override def paramsEncoder: Encoder[Seq[ModifyGuildChannelPositionsData]] = {
    implicit val enc: Encoder[ModifyGuildChannelPositionsData] = deriveEncoder[ModifyGuildChannelPositionsData]
    Encoder[Seq[ModifyGuildChannelPositionsData]]
  }

  override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
  override def toNiceResponse(response: Seq[RawChannel]): Seq[Option[GuildChannel]] =
    response.map(_.toGuildChannel(guildId))

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildChannelPositions[Ctx] = copy(reason = Some(reason))
}

trait GuildMemberRequest[Params, Ctx] extends RESTRequest[Params, RawGuildMember, GuildMember, Ctx] {
  def guildId: GuildId

  override def responseDecoder:                          Decoder[RawGuildMember] = Decoder[RawGuildMember]
  override def toNiceResponse(response: RawGuildMember): GuildMember             = response.toGuildMember(guildId)
}

/**
  * Get a guild member by id.
  */
case class GetGuildMember[Ctx](guildId: GuildId, userId: UserId, context: Ctx = NotUsed: NotUsed)
    extends GuildMemberRequest[NotUsed, Ctx] {
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
  override def params:        NotUsed          = NotUsed
  override def route:         RequestRoute     = Routes.getGuildMember(userId, guildId)
}

/**
  * @param limit The max amount of members to get
  * @param after Get userIds after this id
  */
case class ListGuildMembersData(limit: Option[Int] = None, after: Option[UserId] = None) {
  require(limit.forall(l => l >= 1 && l <= 1000), "Can only get between 1 and 1000 guild members at a time")
}

/**
  * Get all the guild members in this guild.
  */
case class ListGuildMembers[Ctx](guildId: GuildId, params: ListGuildMembersData, context: Ctx = NotUsed: NotUsed)
    extends RESTRequest[ListGuildMembersData, Seq[RawGuildMember], Seq[GuildMember], Ctx] {
  override def route:         RequestRoute                  = Routes.listGuildMembers(guildId)
  override def paramsEncoder: Encoder[ListGuildMembersData] = deriveEncoder[ListGuildMembersData]

  override def responseDecoder: Decoder[Seq[RawGuildMember]] = Decoder[Seq[RawGuildMember]]
  override def toNiceResponse(response: Seq[RawGuildMember]): Seq[GuildMember] =
    response.map(_.toGuildMember(guildId))
}
object ListGuildMembers {
  def mk[Ctx](
      guildId: GuildId,
      limit: Option[Int] = None,
      after: Option[UserId] = None,
      context: Ctx = NotUsed: NotUsed
  ): ListGuildMembers[Ctx] = new ListGuildMembers(guildId, ListGuildMembersData(limit, after), context)
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
case class AddGuildMember[Ctx](
    guildId: GuildId,
    userId: UserId,
    params: AddGuildMemberData,
    context: Ctx = NotUsed: NotUsed
) extends GuildMemberRequest[AddGuildMemberData, Ctx] {
  override def route:         RequestRoute                = Routes.addGuildMember(userId, guildId)
  override def paramsEncoder: Encoder[AddGuildMemberData] = deriveEncoder[AddGuildMemberData]

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
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * @param nick The nickname to give to the user.
  * @param roles The roles to give to the user.
  * @param mute If the user should be muted.
  * @param deaf If the user should be deafened.
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
case class ModifyGuildMember[Ctx](
    guildId: GuildId,
    userId: UserId,
    params: ModifyGuildMemberData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoResponseReasonRequest[ModifyGuildMember[Ctx], ModifyGuildMemberData, Ctx] {
  override def jsonPrinter: Printer = Printer.noSpaces

  override def route:         RequestRoute                   = Routes.modifyGuildMember(userId, guildId)
  override def paramsEncoder: Encoder[ModifyGuildMemberData] = ModifyGuildMemberData.encoder

  override def requiredPermissions: Permission = {
    def ifDefined(opt: JsonOption[_], perm: Permission): Permission = if (opt.nonEmpty) perm else Permission.None
    Permission(
      Permission.CreateInstantInvite,
      ifDefined(params.nick, Permission.ManageNicknames),
      ifDefined(params.roles, Permission.ManageRoles),
      ifDefined(params.mute, Permission.MuteMembers),
      ifDefined(params.deaf, Permission.DeafenMembers)
    )
  }
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
  override def withReason(reason: String): ModifyGuildMember[Ctx] = copy(reason = Some(reason))
}

case class ModifyBotUsersNickData(nick: String)

/**
  * Modify the clients nickname.
  */
case class ModifyBotUsersNick[Ctx](
    guildId: GuildId,
    params: ModifyBotUsersNickData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[ModifyBotUsersNick[Ctx], ModifyBotUsersNickData, String, Ctx] {
  override def route:         RequestRoute                    = Routes.modifyCurrentNick(guildId)
  override def paramsEncoder: Encoder[ModifyBotUsersNickData] = deriveEncoder[ModifyBotUsersNickData]

  override def responseDecoder: Decoder[String] = Decoder[String]

  override def requiredPermissions: Permission = Permission.ChangeNickname
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyBotUsersNick[Ctx] = copy(reason = Some(reason))
}
object ModifyBotUsersNick {
  def mk[Ctx](guildId: GuildId, nick: String, context: Ctx = NotUsed: NotUsed): ModifyBotUsersNick[Ctx] =
    new ModifyBotUsersNick(guildId, ModifyBotUsersNickData(nick), context)
}

/**
  * Add a role to a guild member.
  */
case class AddGuildMemberRole[Ctx](guildId: GuildId, userId: UserId, roleId: RoleId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.addGuildMemberRole(roleId, userId, guildId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Remove a role from a guild member.
  */
case class RemoveGuildMemberRole[Ctx](guildId: GuildId, userId: UserId, roleId: RoleId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.removeGuildMemberRole(roleId, userId, guildId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Kicks a guild member.
  */
case class RemoveGuildMember[Ctx](
    guildId: GuildId,
    userId: UserId,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[RemoveGuildMember[Ctx], Ctx] {
  override def route: RequestRoute = Routes.removeGuildMember(userId, guildId)

  override def requiredPermissions: Permission = Permission.KickMembers
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): RemoveGuildMember[Ctx] = copy(reason = Some(reason))
}

/**
  * Get all the bans for this guild.
  */
case class GetGuildBans[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends RESTRequest[NotUsed, Seq[RawBan], Seq[Ban], Ctx] {
  override def route:         RequestRoute     = Routes.getGuildBans(guildId)
  override def params:        NotUsed          = NotUsed
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()

  override def responseDecoder:                       Decoder[Seq[RawBan]] = Decoder[Seq[RawBan]]
  override def toNiceResponse(response: Seq[RawBan]): Seq[Ban]             = response.map(_.toBan)

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

}

/**
  * @param `delete-message-days` The number of days to delete messages for
  *                              this banned user.
  * @param reason The reason for the ban.
  */
case class CreateGuildBanData(`delete-message-days`: Int, reason: String)

/**
  * Ban a user from a guild.
  */
case class CreateGuildBan[Ctx](
    guildId: GuildId,
    userId: UserId,
    params: CreateGuildBanData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoResponseReasonRequest[CreateGuildBan[Ctx], CreateGuildBanData, Ctx] {
  override def route:         RequestRoute                = Routes.createGuildMemberBan(userId, guildId)
  override def paramsEncoder: Encoder[CreateGuildBanData] = deriveEncoder[CreateGuildBanData]

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildBan[Ctx] = copy(reason = Some(reason))
}
object CreateGuildBan {
  def mk[Ctx](
      guildId: GuildId,
      userId: UserId,
      deleteMessageDays: Int,
      reason: String,
      context: Ctx = NotUsed: NotUsed
  ): CreateGuildBan[Ctx] = new CreateGuildBan(guildId, userId, CreateGuildBanData(deleteMessageDays, reason), context)
}

/**
  * Unban a user from a guild.
  */
case class RemoveGuildBan[Ctx](
    guildId: GuildId,
    userId: UserId,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[RemoveGuildBan[Ctx], Ctx] {
  override def route: RequestRoute = Routes.removeGuildMemberBan(userId, guildId)

  override def requiredPermissions: Permission = Permission.BanMembers
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): RemoveGuildBan[Ctx] = copy(reason = Some(reason))
}

/**
  * Get all the roles in a guild.
  */
case class GetGuildRoles[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends RESTRequest[NotUsed, Seq[RawRole], Seq[Role], Ctx] {
  override def route:         RequestRoute     = Routes.getGuildRole(guildId)
  override def params:        NotUsed          = NotUsed
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()

  override def responseDecoder: Decoder[Seq[RawRole]] = Decoder[Seq[RawRole]]
  override def toNiceResponse(response: Seq[RawRole]): Seq[Role] =
    response.map(_.toRole(guildId))

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
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
case class CreateGuildRole[Ctx](
    guildId: GuildId,
    params: CreateGuildRoleData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends ReasonRequest[CreateGuildRole[Ctx], CreateGuildRoleData, RawRole, Role, Ctx] {
  override def route:         RequestRoute                 = Routes.createGuildRole(guildId)
  override def paramsEncoder: Encoder[CreateGuildRoleData] = deriveEncoder[CreateGuildRoleData]

  override def responseDecoder: Decoder[RawRole] = Decoder[RawRole]
  override def toNiceResponse(response: RawRole): Role =
    response.toRole(guildId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildRole[Ctx] = copy(reason = Some(reason))
}

/**
  * @param id The role id.
  * @param position The new position of the role.
  */
case class ModifyGuildRolePositionsData(id: RoleId, position: Int)

/**
  * Modify the positions of several roles.
  */
case class ModifyGuildRolePositions[Ctx](
    guildId: GuildId,
    params: Seq[ModifyGuildRolePositionsData],
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildRolePositions[Ctx], Seq[ModifyGuildRolePositionsData], Seq[RawRole], Seq[Role], Ctx] {
  override def route: RequestRoute = Routes.modifyGuildRolePositions(guildId)
  override def paramsEncoder: Encoder[Seq[ModifyGuildRolePositionsData]] = {
    implicit val enc: Encoder[ModifyGuildRolePositionsData] = deriveEncoder[ModifyGuildRolePositionsData]
    Encoder[Seq[ModifyGuildRolePositionsData]]
  }

  override def responseDecoder:                        Decoder[Seq[RawRole]] = Decoder[Seq[RawRole]]
  override def toNiceResponse(response: Seq[RawRole]): Seq[Role]             = response.map(_.toRole(guildId))

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildRolePositions[Ctx] = copy(reason = Some(reason))
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
case class ModifyGuildRole[Ctx](
    guildId: GuildId,
    roleId: RoleId,
    params: ModifyGuildRoleData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildRole[Ctx], ModifyGuildRoleData, RawRole, Role, Ctx] {
  override def route:         RequestRoute                 = Routes.modifyGuildRole(roleId, guildId)
  override def paramsEncoder: Encoder[ModifyGuildRoleData] = deriveEncoder[ModifyGuildRoleData]

  override def responseDecoder:                   Decoder[RawRole] = Decoder[RawRole]
  override def toNiceResponse(response: RawRole): Role             = response.toRole(guildId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildRole[Ctx] = copy(reason = Some(reason))
}

/**
  * Delete a role in a guild.
  */
case class DeleteGuildRole[Ctx](
    guildId: GuildId,
    roleId: RoleId,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteGuildRole[Ctx], Ctx] {
  override def route: RequestRoute = Routes.deleteGuildRole(roleId, guildId)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): DeleteGuildRole[Ctx] = copy(reason = Some(reason))
}

/**
  * @param days The amount of days to prune for.
  */
case class GuildPruneData(days: Int)

/**
  * @param pruned The number of members that would be removed.
  */
case class GuildPruneResponse(pruned: Int)

trait GuildPrune[Ctx] extends NoNiceResponseRequest[GuildPruneData, GuildPruneResponse, Ctx] {
  override def paramsEncoder: Encoder[GuildPruneData] = deriveEncoder[GuildPruneData]

  override def responseDecoder: Decoder[GuildPruneResponse] = deriveDecoder[GuildPruneResponse]
}

/**
  * Check how many members would be removed if a prune was started now.
  */
case class GetGuildPruneCount[Ctx](guildId: GuildId, params: GuildPruneData, context: Ctx = NotUsed: NotUsed)
    extends GuildPrune[Ctx] {
  override def route: RequestRoute = Routes.getGuildPruneCount(guildId)

  override def requiredPermissions: Permission = Permission.KickMembers
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}
object GetGuildPruneCount {
  def mk[Ctx](guildId: GuildId, days: Int, context: Ctx = NotUsed: NotUsed): GetGuildPruneCount[Ctx] =
    new GetGuildPruneCount(guildId, GuildPruneData(days), context)
}

/**
  * Begin a guild prune.
  */
case class BeginGuildPrune[Ctx](
    guildId: GuildId,
    params: GuildPruneData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends GuildPrune[Ctx]
    with NoNiceResponseReasonRequest[BeginGuildPrune[Ctx], GuildPruneData, GuildPruneResponse, Ctx] {
  override def route: RequestRoute = Routes.beginGuildPrune(guildId)

  override def requiredPermissions: Permission = Permission.KickMembers
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): BeginGuildPrune[Ctx] = copy(reason = Some(reason))
}
object BeginGuildPrune {
  def mk[Ctx](guildId: GuildId, days: Int, context: Ctx = NotUsed: NotUsed): BeginGuildPrune[Ctx] =
    new BeginGuildPrune(guildId, GuildPruneData(days), context)
}

/**
  * Get the voice regions for this guild.
  */
case class GetGuildVoiceRegions[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Seq[VoiceRegion], Ctx] {
  override def route: RequestRoute = Routes.getGuildVoiceRegions(guildId)

  override def responseDecoder: Decoder[Seq[VoiceRegion]] = Decoder[Seq[VoiceRegion]]
}

/**
  * Get the invites for this guild.
  */
case class GetGuildInvites[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Seq[InviteWithMetadata], Ctx] {
  override def route: RequestRoute = Routes.getGuildInvites(guildId)

  override def responseDecoder: Decoder[Seq[InviteWithMetadata]] = Decoder[Seq[InviteWithMetadata]]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Get the integrations for this guild.
  */
case class GetGuildIntegrations[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Seq[Integration], Ctx] {
  override def route: RequestRoute = Routes.getGuildIntegrations(guildId)

  override def responseDecoder: Decoder[Seq[Integration]] = Decoder[Seq[Integration]]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
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
case class CreateGuildIntegration[Ctx](
    guildId: GuildId,
    params: CreateGuildIntegrationData,
    context: Ctx = NotUsed: NotUsed
) extends NoResponseRequest[CreateGuildIntegrationData, Ctx] {
  override def route:         RequestRoute                        = Routes.createGuildIntegrations(guildId)
  override def paramsEncoder: Encoder[CreateGuildIntegrationData] = deriveEncoder[CreateGuildIntegrationData]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * @param expireBehavior The behavior of expiring subscribers.
  * @param expireGracePeriod The grace period before expiring subscribers.
  * @param enableEmoticons If emojis should be synced for this integration.
  *                        (Twitch only)
  */
case class ModifyGuildIntegrationData(
    expireBehavior: Int /*TODO: Better than Int here*/,
    expireGracePeriod: Int,
    enableEmoticons: Boolean
)

/**
  * Modify an existing integration for a guild.
  */
case class ModifyGuildIntegration[Ctx](
    guildId: GuildId,
    integrationId: IntegrationId,
    params: ModifyGuildIntegrationData,
    context: Ctx = NotUsed: NotUsed
) extends NoResponseRequest[ModifyGuildIntegrationData, Ctx] {
  override def route:         RequestRoute                        = Routes.modifyGuildIntegration(integrationId, guildId)
  override def paramsEncoder: Encoder[ModifyGuildIntegrationData] = deriveEncoder[ModifyGuildIntegrationData]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Delete an integration.
  */
case class DeleteGuildIntegration[Ctx](guildId: GuildId, integrationId: IntegrationId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.deleteGuildIntegration(integrationId, guildId)

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Sync an integration.
  */
case class SyncGuildIntegration[Ctx](guildId: GuildId, integrationId: IntegrationId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.syncGuildIntegration(integrationId, guildId)

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Get the guild embed for a guild.
  */
case class GetGuildEmbed[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[GuildEmbed, Ctx] {
  override def route: RequestRoute = Routes.getGuildEmbed(guildId)

  override def responseDecoder: Decoder[GuildEmbed] = Decoder[GuildEmbed]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Modify a guild embed for a guild.
  */
case class ModifyGuildEmbed[Ctx](guildId: GuildId, params: GuildEmbed, context: Ctx = NotUsed: NotUsed)
    extends NoNiceResponseRequest[GuildEmbed, GuildEmbed, Ctx] {
  override def route:         RequestRoute        = Routes.modifyGuildEmbed(guildId)
  override def paramsEncoder: Encoder[GuildEmbed] = Encoder[GuildEmbed]

  override def responseDecoder: Decoder[GuildEmbed] = Decoder[GuildEmbed]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

case class VanityUrlResponse(code: String)

/**
  * Get a partial invite object for guilds with that feature enabled.
  */
case class GetGuildVanityUrl[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[VanityUrlResponse, Ctx] {
  override def route: RequestRoute = Routes.getGuildVanityUrl(guildId)

  override def responseDecoder: Decoder[VanityUrlResponse] = deriveDecoder[VanityUrlResponse]

  override def requiredPermissions: Permission = Permission.ManageGuild
  override def hasPermissions[F[_]: Monad](implicit c: CacheSnapshot[F]): F[Boolean] =
    hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * Get an invite for a given invite code
  */
case class GetInvite[Ctx](inviteCode: String, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Invite, Ctx] {
  override def route: RequestRoute = Routes.getInvite(inviteCode)

  override def responseDecoder: Decoder[Invite] = Decoder[Invite]
}

/**
  * Delete an invite.
  */
case class DeleteInvite[Ctx](inviteCode: String, context: Ctx = NotUsed: NotUsed, reason: Option[String] = None)
    extends NoParamsNiceResponseReasonRequest[DeleteInvite[Ctx], Invite, Ctx] {
  override def route: RequestRoute = Routes.deleteInvite(inviteCode)

  override def responseDecoder: Decoder[Invite] = Decoder[Invite]

  override def requiredPermissions: Permission = Permission.ManageChannels

  override def withReason(reason: String): DeleteInvite[Ctx] = copy(reason = Some(reason))
}

/**
  * Fetch the client user.
  */
case class GetCurrentUser[Ctx](context: Ctx = NotUsed: NotUsed) extends NoParamsNiceResponseRequest[User, Ctx] {
  override def route: RequestRoute = Routes.getCurrentUser

  override def responseDecoder: Decoder[User] = Decoder[User]
}

/**
  * Get a user by id.
  */
case class GetUser[Ctx](userId: UserId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[User, Ctx] {
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
) {
  require(limit.forall(l => l >= 1 && l <= 100), "The limit must be between 1 and 100")
}

/**
  * Get the guilds the client user is in.
  */
case class GetCurrentUserGuilds[Ctx](params: GetCurrentUserGuildsData, context: Ctx = NotUsed: NotUsed)
    extends NoNiceResponseRequest[GetCurrentUserGuildsData, Seq[GetUserGuildsGuild], Ctx] {
  override def route:         RequestRoute                      = Routes.getCurrentUserGuilds
  override def paramsEncoder: Encoder[GetCurrentUserGuildsData] = deriveEncoder[GetCurrentUserGuildsData]

  override def responseDecoder: Decoder[Seq[GetUserGuildsGuild]] = {
    implicit val dec: Decoder[GetUserGuildsGuild] = deriveDecoder[GetUserGuildsGuild]
    Decoder[Seq[GetUserGuildsGuild]]
  }
}
object GetCurrentUserGuilds {
  def before[Ctx](
      before: GuildId,
      limit: Option[Int] = None,
      context: Ctx = NotUsed: NotUsed
  ): GetCurrentUserGuilds[Ctx] =
    new GetCurrentUserGuilds(GetCurrentUserGuildsData(before = Some(before), limit = limit), context)

  def after[Ctx](
      after: GuildId,
      limit: Option[Int] = None,
      context: Ctx = NotUsed: NotUsed
  ): GetCurrentUserGuilds[Ctx] =
    new GetCurrentUserGuilds(GetCurrentUserGuildsData(after = Some(after), limit = limit), context)
}

/**
  * Leave a guild.
  */
case class LeaveGuild[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed) extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.leaveGuild(guildId)
}

case class GetUserDMs[Ctx](context: Ctx = NotUsed: NotUsed)
    extends NoParamsRequest[Seq[RawChannel], Seq[Option[DMChannel]], Ctx] {
  override def route: RequestRoute = Routes.getUserDMs

  override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
  override def toNiceResponse(response: Seq[RawChannel]): Seq[Option[DMChannel]] =
    response.map(_.toChannel.collect { case dmChannel: DMChannel => dmChannel })
}

/**
  * @param recipientId User to send a DM to.
  */
case class CreateDMData(recipientId: UserId)

/**
  * Create a new DM channel.
  */
case class CreateDm[Ctx](params: CreateDMData, context: Ctx = NotUsed: NotUsed)
    extends RESTRequest[CreateDMData, RawChannel, Option[DMChannel], Ctx] {
  override def route:         RequestRoute          = Routes.createDM
  override def paramsEncoder: Encoder[CreateDMData] = deriveEncoder[CreateDMData]

  override def responseDecoder: Decoder[RawChannel] = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[DMChannel] =
    response.toChannel.collect { case dmChannel: DMChannel => dmChannel }
}
object CreateDm {
  def mk[Ctx](to: UserId, context: Ctx = NotUsed: NotUsed): CreateDm[Ctx] = new CreateDm(CreateDMData(to), context)
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
case class CreateGroupDm[Ctx](params: CreateGroupDMData, context: Ctx = NotUsed: NotUsed)
    extends RESTRequest[CreateGroupDMData, RawChannel, Option[GroupDMChannel], Ctx] {
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
case class GetUserConnections[Ctx](context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Seq[Connection], Ctx] {
  override def route: RequestRoute = Routes.getUserConnections

  override def responseDecoder: Decoder[Seq[Connection]] = Decoder[Seq[Connection]]
}
