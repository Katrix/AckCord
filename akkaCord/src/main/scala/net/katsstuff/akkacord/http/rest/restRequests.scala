/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.akkacord.http.rest

import java.nio.file.{Files, Path}

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.handlers.{CacheHandler, CacheSnapshotBuilder, CacheUpdateHandler, Handlers, NOOPHandler, RawHandlers}
import net.katsstuff.akkacord.http.websocket.WsEvent
import net.katsstuff.akkacord.http.{RawChannel, RawGuild, RawGuildMember, RawMessage, Routes}

trait ComplexRESTRequest[Params, Response, HandlerType] {
  def route: RestRoute

  def params:        Params
  def paramsEncoder: Encoder[Params]
  def toJsonParams: Json = paramsEncoder(params)

  def responseDecoder:                     Decoder[Response]
  def handleResponse:                      CacheHandler[HandlerType]
  def processResponse(response: Response): HandlerType
  def expectedResponseCode: StatusCode = StatusCodes.OK
}

trait SimpleRESTRequest[Params, Response] extends ComplexRESTRequest[Params, Response, Response] {
  override def processResponse(response: Response): Response = response
}

object Requests {
  import net.katsstuff.akkacord.http.DiscordProtocol._

  trait NoParamsRequest[Response] extends SimpleRESTRequest[NotUsed, Response] {
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed
  }

  trait NoResponseRequest[Params] extends SimpleRESTRequest[Params, NotUsed] {
    override def responseDecoder: Decoder[NotUsed] = (_: HCursor) => Right(NotUsed)
    override val handleResponse       = new NOOPHandler[NotUsed]
    override def expectedResponseCode = StatusCodes.NoContent
  }

  trait NoParamsResponseRequest extends NoParamsRequest[NotUsed] with NoResponseRequest[NotUsed]

  //Channels

  case class GetChannel(channelId: ChannelId) extends NoParamsRequest[RawChannel] {
    def route:                    RestRoute                = Routes.getChannel(channelId)
    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def handleResponse:  CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
  }

  case class ModifyChannelData(name: String, position: Int, topic: Option[String], bitrate: Option[Int], userLimit: Option[Int])
  case class ModifyChannel(channelId: ChannelId, params: ModifyChannelData) extends SimpleRESTRequest[ModifyChannelData, RawChannel] {
    override def route:           RestRoute                  = Routes.modifyChannelPut(channelId)
    override def paramsEncoder:   Encoder[ModifyChannelData] = deriveEncoder[ModifyChannelData]
    override def responseDecoder: Decoder[RawChannel]        = Decoder[RawChannel]
    override def handleResponse:  CacheHandler[RawChannel]   = RawHandlers.rawChannelUpdateHandler
  }

  case class DeleteCloseChannel(channelId: ChannelId) extends NoParamsRequest[RawChannel] {
    override def route:           RestRoute                = Routes.deleteCloseChannel(channelId)
    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def handleResponse:  CacheHandler[RawChannel] = RawHandlers.rawChannelDeleteHandler
  }

  case class GetChannelMessagesData(around: Option[MessageId], before: Option[MessageId], after: Option[MessageId], limit: Option[Int]) {
    require(Seq(around, before, after).count(_.isDefined) <= 1)
  }
  case class GetChannelMessages(channelId: ChannelId, params: GetChannelMessagesData)
      extends SimpleRESTRequest[GetChannelMessagesData, Seq[RawMessage]] {
    override def route:           RestRoute                       = Routes.getChannelMessages(channelId)
    override def paramsEncoder:   Encoder[GetChannelMessagesData] = deriveEncoder[GetChannelMessagesData]
    override def responseDecoder: Decoder[Seq[RawMessage]]        = Decoder[Seq[RawMessage]]
    override def handleResponse:  CacheHandler[Seq[RawMessage]]   = CacheUpdateHandler.seqHandler(RawHandlers.rawMessageUpdateHandler)
  }

  case class GetChannelMessage(channelId: ChannelId, messageId: MessageId) extends NoParamsRequest[RawMessage] {
    override def route:           RestRoute                = Routes.getChannelMessage(messageId, channelId)
    override def responseDecoder: Decoder[RawMessage]      = Decoder[RawMessage]
    override def handleResponse:  CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler
  }

  case class CreateMessageData(content: String, nonce: Option[Snowflake], tts: Boolean, file: Option[Path], embed: Option[OutgoingEmbed]) {
    file.foreach(path => require(Files.isRegularFile(path)))
  }

  //We handle this here as the file argument needs special treatment
  implicit private val createMessageDataEncoder: Encoder[CreateMessageData] = (a: CreateMessageData) =>
    Json
      .obj("content" -> a.content.asJson, "nonce" -> a.nonce.asJson, "tts" -> a.tts.asJson, "embed" -> a.embed.asJson)
  case class CreateMessage(channelId: ChannelId, params: CreateMessageData) extends SimpleRESTRequest[CreateMessageData, RawMessage] {
    override def route:           RestRoute                  = Routes.createMessage(channelId)
    override def paramsEncoder:   Encoder[CreateMessageData] = createMessageDataEncoder
    override def responseDecoder: Decoder[RawMessage]        = Decoder[RawMessage]
    override def handleResponse:  CacheHandler[RawMessage]   = RawHandlers.rawMessageUpdateHandler
  }

  case class CreateReaction(channelId: ChannelId, messageId: MessageId, emoji: String) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.createReaction(emoji, messageId, channelId)
  }

  case class DeleteOwnReaction(channelId: ChannelId, messageId: MessageId, emoji: String) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteOwnReaction(emoji, messageId, channelId)
  }

  case class DeleteUserReaction(channelId: ChannelId, messageId: MessageId, emoji: String, userId: UserId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteUserReaction(userId, emoji, messageId, channelId)
  }

  case class GetReactions(channelId: ChannelId, messageId: MessageId, emoji: String) extends NoParamsRequest[Seq[User]] {
    override def route:           RestRoute               = Routes.getReactions(emoji, messageId, channelId)
    override def responseDecoder: Decoder[Seq[User]]      = Decoder[Seq[User]]
    override def handleResponse:  CacheHandler[Seq[User]] = CacheUpdateHandler.seqHandler(Handlers.userUpdateHandler)
  }

  case class DeleteAllReactions(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteAllReactions(messageId, channelId)
  }

  case class EditMessageData(content: Option[String], embed: Option[OutgoingEmbed]) {
    require(content.forall(_.length < 2000))
  }
  case class EditMessage(channelId: ChannelId, messageId: MessageId, params: EditMessageData) extends SimpleRESTRequest[EditMessageData, RawMessage] {
    override def route:           RestRoute                = Routes.editMessage(messageId, channelId)
    override def paramsEncoder:   Encoder[EditMessageData] = deriveEncoder[EditMessageData]
    override def responseDecoder: Decoder[RawMessage]      = Decoder[RawMessage]
    override def handleResponse:  CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler
  }

  case class DeleteMessage(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteMessage(messageId, channelId)
  }

  case class BulkDeleteMessagesData(messages: Seq[MessageId])
  case class BulkDeleteMessages(channelId: ChannelId, params: BulkDeleteMessagesData) extends NoResponseRequest[BulkDeleteMessagesData] {
    override def route:         RestRoute                       = Routes.bulkDeleteMessages(channelId)
    override def paramsEncoder: Encoder[BulkDeleteMessagesData] = deriveEncoder[BulkDeleteMessagesData]
  }

  case class EditChannelPermissionsData(allow: Permission, deny: Permission, `type`: String)
  case class EditChannelPermissions(channelId: ChannelId, overwriteId: UserOrRoleId, params: EditChannelPermissionsData)
      extends NoResponseRequest[EditChannelPermissionsData] {
    override def route:         RestRoute                           = Routes.editChannelPermissions(overwriteId, channelId)
    override def paramsEncoder: Encoder[EditChannelPermissionsData] = deriveEncoder[EditChannelPermissionsData]
  }

  case class DeleteChannelPermission(channelId: ChannelId, overwriteId: UserOrRoleId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteChannelPermissions(overwriteId, channelId)
  }

  /*
  TODO
  case class GetChannelInvites(channelId: Snowflake) extends NoParamsRequest[Seq[Invite]] {
    override def route: RestRoute = Routes.getChannelInvites(channelId)
  }

  case class CreateChannelInviteData(maxAge: Int = 86400, maxUses: Int = 0, temporary: Boolean = false, unique: Boolean = false)
  case class CreateChannelInvite(channelId:  Snowflake, params:    CreateChannelInviteData) extends RESTRequest[CreateChannelInviteData, Invite] {
    override def route:         RestRoute                        = Routes.getChannelInvites(channelId)
    override def paramsEncoder: Encoder[CreateChannelInviteData] = implicitly[Encoder[CreateChannelInviteData]]
  }
   */

  case class TriggerTypingIndicator(channelId: ChannelId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.triggerTyping(channelId)
  }

  case class GetPinnedMessages(channelId: ChannelId) extends NoParamsRequest[Seq[RawMessage]] {
    override def route:           RestRoute                     = Routes.getPinnedMessage(channelId)
    override def responseDecoder: Decoder[Seq[RawMessage]]      = Decoder[Seq[RawMessage]]
    override def handleResponse:  CacheHandler[Seq[RawMessage]] = CacheUpdateHandler.seqHandler(RawHandlers.rawMessageUpdateHandler)
  }

  case class AddPinnedChannelMessages(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.addPinnedChannelMessage(messageId, channelId)
  }

  case class DeletePinnedChannelMessages(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deletePinnedChannelMessage(messageId, channelId)
  }

  /*
  case class GroupDMAddRecipientData(accessToken: String, nick: String)
  case class GroupDMAddRecipient(channelId:       Snowflake, userId: Snowflake, params: GroupDMAddRecipientData)
      extends RESTRequest[GroupDMAddRecipientData] {
    override def route:         RestRoute                        = Routes.groupDmAddRecipient(userId, channelId)
    override def paramsEncoder: Encoder[GroupDMAddRecipientData] = deriveEncoder[GroupDMAddRecipientData]
  }

  case class GroupDMRemoveRecipient(channelId: Snowflake, userId: Snowflake) extends NoParamsRequest {
    override def route: RestRoute = Routes.groupDmRemoveRecipient(userId, channelId)
  }
   */

  //Guild
  case class CreateGuildData(
      name: String,
      region: String,
      icon: String,
      verificationLevel: VerificationLevel,
      defaultMessageNotifications: NotificationLevel,
      roles: Seq[Role],
      channels: CreateGuildChannelData
  )
  case class CreateGuild(params: CreateGuildData) extends SimpleRESTRequest[CreateGuildData, RawGuild] {
    override def route: RestRoute = Routes.createGuild
    override def paramsEncoder: Encoder[CreateGuildData] = {
      import io.circe.generic.extras.auto._
      deriveEncoder[CreateGuildData]
    }
    override def responseDecoder: Decoder[RawGuild]      = Decoder[RawGuild]
    override def handleResponse:  CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler
  }

  case class GetGuild(guildId: GuildId) extends NoParamsRequest[RawGuild] {
    override def route:           RestRoute              = Routes.getGuild(guildId)
    override def responseDecoder: Decoder[RawGuild]      = Decoder[RawGuild]
    override def handleResponse:  CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler
  }

  case class ModifyGuildData(
      name: Option[String],
      region: Option[String],
      verificationLevel: Option[VerificationLevel],
      defaultMessageNotification: Option[NotificationLevel],
      afkChannelId: Option[ChannelId],
      afkTimeout: Option[Int],
      icon: Option[String],
      ownerId: Option[UserId],
      splash: Option[String]
  )
  case class ModifyGuild(guildId: GuildId, params: ModifyGuildData) extends SimpleRESTRequest[ModifyGuildData, RawGuild] {
    override def route:           RestRoute                = Routes.modifyGuild(guildId)
    override def paramsEncoder:   Encoder[ModifyGuildData] = deriveEncoder[ModifyGuildData]
    override def responseDecoder: Decoder[RawGuild]        = Decoder[RawGuild]
    override def handleResponse:  CacheHandler[RawGuild]   = RawHandlers.rawGuildUpdateHandler
  }

  case class DeleteGuild(guildId: GuildId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteGuild(guildId)
  }

  case class GetGuildChannels(guildId: GuildId) extends NoParamsRequest[Seq[RawChannel]] {
    override def route:           RestRoute                     = Routes.getGuildChannels(guildId)
    override def responseDecoder: Decoder[Seq[RawChannel]]      = Decoder[Seq[RawChannel]]
    override def handleResponse:  CacheHandler[Seq[RawChannel]] = CacheUpdateHandler.seqHandler(RawHandlers.rawChannelUpdateHandler)
  }

  case class CreateGuildChannelData(
      name: String,
      `type`: Option[ChannelType],
      bitrate: Option[Int],
      userLimit: Option[Int],
      permissionOverwrites: Option[Seq[PermissionValue]]
  )
  case class CreateGuildChannel(guildId: GuildId, params: CreateGuildChannelData) extends SimpleRESTRequest[CreateGuildChannelData, RawChannel] {
    override def route: RestRoute = Routes.createGuildChannel(guildId)
    override def paramsEncoder: Encoder[CreateGuildChannelData] = {
      import io.circe.generic.extras.auto._
      deriveEncoder[CreateGuildChannelData]
    }
    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def handleResponse:  CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
  }

  case class ModifyGuildChannelPositionsData(id: ChannelId, position: Int)
  case class ModifyGuildChannelPositions(guildId: GuildId, params: Seq[ModifyGuildChannelPositionsData])
      extends SimpleRESTRequest[Seq[ModifyGuildChannelPositionsData], Seq[RawChannel]] {
    override def route: RestRoute = Routes.modifyGuildChannelsPositions(guildId)
    override def paramsEncoder: Encoder[Seq[ModifyGuildChannelPositionsData]] = {
      implicit val enc = deriveEncoder[ModifyGuildChannelPositionsData]
      Encoder[Seq[ModifyGuildChannelPositionsData]]
    }
    override def responseDecoder: Decoder[Seq[RawChannel]]      = Decoder[Seq[RawChannel]]
    override def handleResponse:  CacheHandler[Seq[RawChannel]] = CacheUpdateHandler.seqHandler(RawHandlers.rawChannelUpdateHandler)
  }

  trait GuildMemberRequest[Params] extends ComplexRESTRequest[Params, RawGuildMember, WsEvent.RawGuildMemberWithGuild] {
    def guildId: GuildId
    override def responseDecoder:                           Decoder[RawGuildMember]                       = Decoder[RawGuildMember]
    override def handleResponse:                            CacheHandler[WsEvent.RawGuildMemberWithGuild] = RawHandlers.rawGuildMemberWithGuildUpdateHandler
    override def processResponse(response: RawGuildMember): WsEvent.RawGuildMemberWithGuild               = WsEvent.RawGuildMemberWithGuild(guildId, response)
  }

  case class GetGuildMember(guildId: GuildId, userId: UserId) extends GuildMemberRequest[NotUsed] {
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed
    override def route:         RestRoute        = Routes.getGuildMember(userId, guildId)
  }

  case class ListGuildMembersData(limit: Option[Int], after: Option[UserId])
  case class ListGuildMembers(guildId: GuildId, params: ListGuildMembersData) extends GuildMemberRequest[ListGuildMembersData] {
    override def route:         RestRoute                     = Routes.listGuildMembers(guildId)
    override def paramsEncoder: Encoder[ListGuildMembersData] = deriveEncoder[ListGuildMembersData]
  }

  case class AddGuildMemberData(accessToken: String, nick: Option[String], roles: Option[Seq[Role]], mute: Option[Boolean], deaf: Option[Boolean])
  case class AddGuildMember(guildId: GuildId, userId: UserId, params: AddGuildMemberData) extends GuildMemberRequest[AddGuildMemberData] {
    override def route:                RestRoute                   = Routes.addGuildMember(userId, guildId)
    override def paramsEncoder:        Encoder[AddGuildMemberData] = deriveEncoder[AddGuildMemberData]
    override def expectedResponseCode: StatusCode                  = StatusCodes.Created
  }

  case class ModifyGuildMemberData(
      nick: Option[String],
      roles: Option[Seq[RoleId]],
      mute: Option[Boolean],
      deaf: Option[Boolean],
      channelId: Option[ChannelId]
  )
  case class ModifyGuildMember(guildId: GuildId, userId: UserId, params: ModifyGuildMemberData) extends NoResponseRequest[ModifyGuildMemberData] {
    override def route:         RestRoute                      = Routes.modifyGuildMember(userId, guildId)
    override def paramsEncoder: Encoder[ModifyGuildMemberData] = deriveEncoder[ModifyGuildMemberData]
  }

  case class ModifyBotUsersNickData(nick: String)
  case class ModifyBotUsersNick(guildId: GuildId, params: ModifyBotUsersNickData) extends SimpleRESTRequest[ModifyBotUsersNickData, String] {
    override def route:           RestRoute                       = Routes.modifyCurrentNick(guildId)
    override def paramsEncoder:   Encoder[ModifyBotUsersNickData] = deriveEncoder[ModifyBotUsersNickData]
    override def responseDecoder: Decoder[String]                 = Decoder[String]
    override def handleResponse: CacheHandler[String] = new CacheUpdateHandler[String] {
      override def handle(builder: CacheSnapshotBuilder, obj: String)(implicit log: LoggingAdapter): Unit = {
        for {
          guild     <- builder.getGuild(guildId)
          botMember <- guild.members.get(builder.botUser.id)
        } {
          val newGuild = guild.copy(members = guild.members + (builder.botUser.id -> botMember.copy(nick = Some(obj))))
          builder.guilds.put(guildId, newGuild)
        }
      }
    }
  }

  case class AddGuildMemberRole(guildId: GuildId, userId: UserId, roleId: RoleId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.addGuildMemberRole(roleId, userId, guildId)
  }

  case class RemoveGuildMemberRole(guildId: GuildId, userId: UserId, roleId: RoleId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.removeGuildMemberRole(roleId, userId, guildId)
  }

  case class RemoveGuildMember(guildId: GuildId, userId: UserId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.removeGuildMember(userId, guildId)
  }

  case class GetGuildBans(guildId: GuildId) extends NoParamsRequest[Seq[User]] {
    override def route:           RestRoute               = Routes.getGuildBans(guildId)
    override def responseDecoder: Decoder[Seq[User]]      = Decoder[Seq[User]]
    override def handleResponse:  CacheHandler[Seq[User]] = CacheUpdateHandler.seqHandler(RawHandlers.userUpdateHandler)
  }

  case class CreateGuildBanData(`delete-message-days`: Int)
  case class CreateGuildBan(guildId: GuildId, userId: UserId, params: CreateGuildBanData) extends NoResponseRequest[CreateGuildBanData] {
    override def route:         RestRoute                   = Routes.createGuildMemberBan(userId, guildId)
    override def paramsEncoder: Encoder[CreateGuildBanData] = deriveEncoder[CreateGuildBanData]
  }

  case class RemoveGuildBan(guildId: GuildId, userId: UserId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.removeGuildMemberBan(userId, guildId)
  }

  case class GetGuildRoles(guildId: GuildId) extends ComplexRESTRequest[NotUsed, Seq[Role], Seq[WsEvent.GuildRoleModifyData]] {
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed

    override def route:                                RestRoute                                      = Routes.getGuildRole(guildId)
    override def responseDecoder:                      Decoder[Seq[Role]]                             = Decoder[Seq[Role]]
    override def handleResponse:                       CacheHandler[Seq[WsEvent.GuildRoleModifyData]] = CacheUpdateHandler.seqHandler(RawHandlers.roleUpdateHandler)
    override def processResponse(response: Seq[Role]): Seq[WsEvent.GuildRoleModifyData]               = response.map(WsEvent.GuildRoleModifyData(guildId, _))
  }

  case class CreateGuildRoleData(
      name: Option[String],
      permissions: Option[Permission],
      color: Option[Int],
      hoist: Option[Boolean],
      mentionable: Option[Boolean]
  )
  case class CreateGuildRole(guildId: GuildId, params: CreateGuildRoleData)
      extends ComplexRESTRequest[CreateGuildRoleData, Role, WsEvent.GuildRoleModifyData] {
    override def route:                           RestRoute                                 = Routes.createGuildRole(guildId)
    override def paramsEncoder:                   Encoder[CreateGuildRoleData]              = deriveEncoder[CreateGuildRoleData]
    override def responseDecoder:                 Decoder[Role]                             = Decoder[Role]
    override def handleResponse:                  CacheHandler[WsEvent.GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def processResponse(response: Role): WsEvent.GuildRoleModifyData               = WsEvent.GuildRoleModifyData(guildId, response)
  }

  case class ModifyGuildRolePositionsData(id: RoleId, position: Int)
  case class ModifyGuildRolePositions(guildId: GuildId, params: Seq[ModifyGuildRolePositionsData])
      extends ComplexRESTRequest[Seq[ModifyGuildRolePositionsData], Seq[Role], Seq[WsEvent.GuildRoleModifyData]] {
    override def route: RestRoute = Routes.modifyGuildRolePositions(guildId)
    override def paramsEncoder: Encoder[Seq[ModifyGuildRolePositionsData]] = {
      implicit val enc = deriveEncoder[ModifyGuildRolePositionsData]
      Encoder[Seq[ModifyGuildRolePositionsData]]
    }
    override def responseDecoder:                      Decoder[Seq[Role]]                             = Decoder[Seq[Role]]
    override def handleResponse:                       CacheHandler[Seq[WsEvent.GuildRoleModifyData]] = CacheUpdateHandler.seqHandler(RawHandlers.roleUpdateHandler)
    override def processResponse(response: Seq[Role]): Seq[WsEvent.GuildRoleModifyData]               = response.map(WsEvent.GuildRoleModifyData(guildId, _))
  }

  case class ModifyGuildRoleData(
      name: Option[String],
      permissions: Option[Permission],
      color: Option[Int],
      hoist: Option[Boolean],
      mentionable: Option[Boolean]
  )
  case class ModifyGuildRole(guildId: GuildId, roleId: RoleId, params: ModifyGuildRoleData)
      extends ComplexRESTRequest[ModifyGuildRoleData, Role, WsEvent.GuildRoleModifyData] {
    override def route:                           RestRoute                                 = Routes.modifyGuildRole(roleId, guildId)
    override def paramsEncoder:                   Encoder[ModifyGuildRoleData]              = deriveEncoder[ModifyGuildRoleData]
    override def responseDecoder:                 Decoder[Role]                             = Decoder[Role]
    override def handleResponse:                  CacheHandler[WsEvent.GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def processResponse(response: Role): WsEvent.GuildRoleModifyData               = WsEvent.GuildRoleModifyData(guildId, response)
  }

  case class DeleteGuildRole(guildId: GuildId, roleId: RoleId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteGuildRole(roleId, guildId)
  }

  case class GuildPruneData(days: Int)
  case class GuildPruneResponse(pruned: Int)
  trait GuildPrune extends SimpleRESTRequest[GuildPruneData, GuildPruneResponse] {
    override def paramsEncoder:   Encoder[GuildPruneData]          = deriveEncoder[GuildPruneData]
    override def responseDecoder: Decoder[GuildPruneResponse]      = deriveDecoder[GuildPruneResponse]
    override def handleResponse:  CacheHandler[GuildPruneResponse] = new NOOPHandler[GuildPruneResponse]
  }

  case class GetGuildPruneCount(guildId: GuildId, params: GuildPruneData) extends GuildPrune {
    override def route: RestRoute = Routes.getGuildPruneCount(guildId)
  }

  case class BeginGuildPrune(guildId: GuildId, params: GuildPruneData) extends GuildPrune {
    override def route: RestRoute = Routes.beginGuildPrune(guildId)
  }

  case class GetGuildVoiceRegions(guildId: GuildId) extends NoParamsRequest[Seq[VoiceRegion]] {
    override def route:           RestRoute                      = Routes.getGuildVoiceRegions(guildId)
    override def responseDecoder: Decoder[Seq[VoiceRegion]]      = Decoder[Seq[VoiceRegion]]
    override def handleResponse:  CacheHandler[Seq[VoiceRegion]] = new NOOPHandler[Seq[VoiceRegion]]
  }

  //TODO: This is supposed to have InviteMetadata too
  case class GetGuildInvites(guildId: GuildId) extends NoParamsRequest[Seq[Invite]] {
    override def route:           RestRoute                 = Routes.getGuildInvites(guildId)
    override def responseDecoder: Decoder[Seq[Invite]]      = Decoder[Seq[Invite]]
    override def handleResponse:  CacheHandler[Seq[Invite]] = new NOOPHandler[Seq[Invite]]
  }

  case class GetGuildIntegrations(guildId: GuildId) extends NoParamsRequest[Seq[Integration]] {
    override def route:           RestRoute                      = Routes.getGuildIntegrations(guildId)
    override def responseDecoder: Decoder[Seq[Integration]]      = Decoder[Seq[Integration]]
    override def handleResponse:  CacheHandler[Seq[Integration]] = new NOOPHandler[Seq[Integration]]
  }

  case class CreateGuildIntegrationData(`type`: String /*TODO: Enum here*/, id: IntegrationId)
  case class CreateGuildIntegration(guildId: GuildId, params: CreateGuildIntegrationData) extends NoResponseRequest[CreateGuildIntegrationData] {
    override def route:         RestRoute                           = Routes.createGuildIntegrations(guildId)
    override def paramsEncoder: Encoder[CreateGuildIntegrationData] = deriveEncoder[CreateGuildIntegrationData]
  }

  case class ModifyGuildIntegrationData(expireBehavior: Int /*TODO: Better than Int here*/, expireGracePeriod: Int, enableEmoticons: Boolean)
  case class ModifyGuildIntegration(guildId: GuildId, integrationId: IntegrationId, params: ModifyGuildIntegrationData)
      extends NoResponseRequest[ModifyGuildIntegrationData] {
    override def route:         RestRoute                           = Routes.modifyGuildIntegration(integrationId, guildId)
    override def paramsEncoder: Encoder[ModifyGuildIntegrationData] = deriveEncoder[ModifyGuildIntegrationData]
  }

  case class DeleteGuildIntegration(guildId: GuildId, integrationId: IntegrationId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteGuildIntegration(integrationId, guildId)
  }

  case class SyncGuildIntegration(guildId: GuildId, integrationId: IntegrationId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.syncGuildIntegration(integrationId, guildId)
  }

  case class GetGuildEmbed(guildId: GuildId) extends NoParamsRequest[GuildEmbed] {
    override def route:           RestRoute                = Routes.getGuildEmbed(guildId)
    override def responseDecoder: Decoder[GuildEmbed]      = Decoder[GuildEmbed]
    override def handleResponse:  CacheHandler[GuildEmbed] = new NOOPHandler[GuildEmbed]
  }

  case class ModifyGuildEmbed(guildId: GuildId, params: GuildEmbed) extends SimpleRESTRequest[GuildEmbed, GuildEmbed] {
    override def route:           RestRoute                = Routes.modifyGuildEmbed(guildId)
    override def paramsEncoder:   Encoder[GuildEmbed]      = Encoder[GuildEmbed]
    override def responseDecoder: Decoder[GuildEmbed]      = Decoder[GuildEmbed]
    override def handleResponse:  CacheHandler[GuildEmbed] = new NOOPHandler[GuildEmbed]
  }

  case class GetInvite(inviteCode: String) extends NoParamsRequest[Invite] {
    override def route:           RestRoute            = Routes.getInvite(inviteCode)
    override def responseDecoder: Decoder[Invite]      = Decoder[Invite]
    override def handleResponse:  CacheHandler[Invite] = new NOOPHandler[Invite]
  }

  case class DeleteInvite(inviteCode: String) extends NoParamsRequest[Invite] {
    override def route:           RestRoute            = Routes.deleteInvite(inviteCode)
    override def responseDecoder: Decoder[Invite]      = Decoder[Invite]
    override def handleResponse:  CacheHandler[Invite] = new NOOPHandler[Invite]
  }

  case class AcceptInvite(inviteCode: String) extends NoParamsRequest[Invite] {
    override def route:           RestRoute            = Routes.acceptInvite(inviteCode)
    override def responseDecoder: Decoder[Invite]      = Decoder[Invite]
    override def handleResponse:  CacheHandler[Invite] = new NOOPHandler[Invite]
  }
}
