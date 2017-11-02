/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
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
package net.katsstuff.ackcord.http.rest

import java.nio.file.{Files, Path}

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity, StatusCode, StatusCodes}
import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.handlers._
import net.katsstuff.ackcord.http.websocket.gateway.GatewayEvent
import net.katsstuff.ackcord.http.websocket.gateway.GatewayEvent.GuildEmojisUpdateData
import net.katsstuff.ackcord.http.{RawBan, RawChannel, RawGuild, RawGuildMember, RawMessage, RawRole, Routes}

/**
  * Base trait for all REST requests in AckCord. If you feel an endpoint is
  * missing, and AckCord hasn't added it yet, you can extend this and create
  * your own request. I'd recommend you to extend
  * [[ComplexRESTRequest]] or [[SimpleRESTRequest]] tough for simplicity.
  * @tparam Response The response type of the request
  * @tparam HandlerType The response type as the cache handler can handle it.
  */
trait BaseRESTRequest[Response, HandlerType] {

  /**
    * The router for this request.
    */
  def route: RestRoute

  /**
    * The body of the request to send.
    */
  def requestBody: RequestEntity

  /**
    * A decoder to decode the response.
    */
  def responseDecoder: Decoder[Response]

  /**
    * A cache handler to update the cache with the response of this request.
    */
  def handleResponse: CacheHandler[HandlerType]

  /**
    * Convert the response to a format the cache handler can understand.
    */
  def processResponse(response: Response): HandlerType

  /**
    * The expected response code from this request.
    */
  def expectedResponseCode: StatusCode = StatusCodes.OK

  /**
    * The permissions needed to use this request.
    */
  def requiredPermissions: Permission = Permission.None

  /**
    * Check if a client has the needed permissions to execute this request.
    */
  def havePermissions(implicit c: CacheSnapshot): Boolean = true

  /**
    * Throw an exception if the client doesn't have the needed permissions to
    * execute this request.
    */
  def requirePerms(implicit c: CacheSnapshot): this.type = {
    require(havePermissions, "Did not have sufficient permissions to complete that action")
    this
  }
}

/**
  * A simpler, request trait where the params are defined explicitly and converted to json.
  * @tparam Params The json parameters of the request.
  */
trait ComplexRESTRequest[Params, Response, HandlerType] extends BaseRESTRequest[Response, HandlerType] {

  /**
    * The params of this request
    */
  def params: Params

  /**
    * An encoder for the params of this request
    */
  def paramsEncoder: Encoder[Params]

  /**
    * The params of this request converted to json.
    */
  def jsonParams: Json = paramsEncoder(params)

  def requestBody: RequestEntity =
    if (params == NotUsed) HttpEntity.Empty
    else HttpEntity(ContentTypes.`application/json`, jsonParams.noSpaces)
}

/**
  * An even simpler request trait where the response type and the handler type
  * are the same.
  */
trait SimpleRESTRequest[Params, Response] extends ComplexRESTRequest[Params, Response, Response] {
  override def processResponse(response: Response): Response = response
}

object Requests {
  import net.katsstuff.ackcord.http.DiscordProtocol._

  /**
    * A simple request that takes to params.
    */
  trait NoParamsRequest[Response] extends SimpleRESTRequest[NotUsed, Response] {
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed
  }

  /**
    * A simple request that doesn't have a response.
    */
  trait NoResponseRequest[Params] extends SimpleRESTRequest[Params, NotUsed] {
    override def responseDecoder:      Decoder[NotUsed]      = (_: HCursor) => Right(NotUsed)
    override def handleResponse:       CacheHandler[NotUsed] = NOOPHandler
    override def expectedResponseCode: StatusCode            = StatusCodes.NoContent
  }

  /**
    * A simple request that has neither params nor a response.
    */
  trait NoParamsResponseRequest extends NoParamsRequest[NotUsed] with NoResponseRequest[NotUsed]

  /**
    * Check if a client has the needed permissions in a guild
    * @param guildId The guild to check for
    * @param permissions The needed permissions
    * @param c The cache
    */
  def hasPermissionsGuild(guildId: GuildId, permissions: Permission)(implicit c: CacheSnapshot): Boolean = {
    c.getGuild(guildId).forall { g =>
      g.members.get(c.botUser.id).forall { mem =>
        mem.permissions.hasPermissions(permissions)
      }
    }
  }

  /**
    * Check if a client has the needed permissions in a channel
    * @param channelId The channel to check for
    * @param permissions The needed permissions
    * @param c The cache
    */
  def hasPermissionsChannel(channelId: ChannelId, permissions: Permission)(implicit c: CacheSnapshot): Boolean = {
    c.getGuildChannel(channelId).forall { gChannel =>
      gChannel.guild.forall { g =>
        g.members.get(c.botUser.id).forall { mem =>
          mem.permissions.hasPermissions(permissions)
        }
      }
    }
  }

  //Audit logs

  /**
    * Get an audit log for a given guild.
    */
  case class GetGuildAuditLog(guildId: GuildId) extends NoParamsRequest[AuditLog] {
    override def route:                                      RestRoute              = Routes.getGuildAuditLogs(guildId)
    override def responseDecoder:                            Decoder[AuditLog]      = Decoder[AuditLog]
    override def handleResponse:                             CacheHandler[AuditLog] = NOOPHandler
    override def requiredPermissions:                        Permission             = Permission.ViewAuditLog
    override def havePermissions(implicit c: CacheSnapshot): Boolean                = hasPermissionsGuild(guildId, requiredPermissions)
  }

  //Channels

  /**
    * Get a channel by id.
    */
  case class GetChannel(channelId: ChannelId) extends NoParamsRequest[RawChannel] {
    def route:                    RestRoute                = Routes.getChannel(channelId)
    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def handleResponse:  CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * @param name New name of the channel.
    * @param position New position of the channel.
    * @param topic The new channel topic for text channels.
    * @param nsfw If the channel is NSFW for text channels.
    * @param bitrate The new channel bitrate for voice channels.
    * @param userLimit The new user limit for voice channel.
    * @param permissionOverwrites The new channel permission overwrites.
    * @param parentId The new category id of the channel.
    */
  case class ModifyChannelData(
      name: Option[String] = None,
      position: Option[Int] = None,
      topic: Option[String] = None,
      nsfw: Option[Boolean] = None,
      bitrate: Option[Int] = None,
      userLimit: Option[Int] = None,
      permissionOverwrites: Option[Seq[PermissionOverwrite]] = None,
      parentId: Option[ChannelId] = None
  ) {
    require(name.forall(_.length <= 100), "Name must be between 2 and 100 characters")
    require(topic.forall(_.length <= 100), "Topic must be between 0 and 1024 characters")
    require(bitrate.forall(b => b >= 8000 && b <= 128000), "Bitrate must be between 8000 and 128000 bits")
    require(userLimit.forall(b => b >= 0 && b <= 99), "User limit must be between 0 and 99 users")
  }

  /**
    * Update the settings of a channel.
    * @param channelId The channel to update.
    */
  case class ModifyChannel(channelId: ChannelId, params: ModifyChannelData)
      extends SimpleRESTRequest[ModifyChannelData, RawChannel] {
    override def route:               RestRoute                  = Routes.modifyChannelPut(channelId)
    override def paramsEncoder:       Encoder[ModifyChannelData] = deriveEncoder[ModifyChannelData]
    override def responseDecoder:     Decoder[RawChannel]        = Decoder[RawChannel]
    override def handleResponse:      CacheHandler[RawChannel]   = RawHandlers.rawChannelUpdateHandler
    override def requiredPermissions: Permission                 = Permission.ManageChannels
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Delete a guild channel, or close a DM channel.
    */
  case class DeleteCloseChannel(channelId: ChannelId) extends NoParamsRequest[RawChannel] {
    override def route:               RestRoute                = Routes.deleteCloseChannel(channelId)
    override def responseDecoder:     Decoder[RawChannel]      = Decoder[RawChannel]
    override def handleResponse:      CacheHandler[RawChannel] = RawHandlers.rawChannelDeleteHandler
    override def requiredPermissions: Permission               = Permission.ManageChannels
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * @param around Get messages around this message id.
    * @param before Get messages before this message id.
    * @param after Get messages after this message id.
    * @param limit The max amount of messages to return. The default is 50.
    */
  case class GetChannelMessagesData(
      around: Option[MessageId] = None,
      before: Option[MessageId] = None,
      after: Option[MessageId] = None,
      limit: Option[Int] = None
  ) {
    require(
      Seq(around, before, after).count(_.isDefined) <= 1,
      "The around, before, after fields are mutually exclusive"
    )
    require(limit.forall(c => c >= 1 && c <= 100), "Count must be between 1 and 100")
  }

  /**
    * Get the messages in a channel.
    */
  case class GetChannelMessages(channelId: ChannelId, params: GetChannelMessagesData)
      extends SimpleRESTRequest[GetChannelMessagesData, Seq[RawMessage]] {
    override def route:           RestRoute                       = Routes.getChannelMessages(channelId)
    override def paramsEncoder:   Encoder[GetChannelMessagesData] = deriveEncoder[GetChannelMessagesData]
    override def responseDecoder: Decoder[Seq[RawMessage]]        = Decoder[Seq[RawMessage]]
    override def handleResponse: CacheHandler[Seq[RawMessage]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawMessageUpdateHandler)
    override def requiredPermissions: Permission = Permission.ReadMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Get a specific message in a channel.
    */
  case class GetChannelMessage(channelId: ChannelId, messageId: MessageId) extends NoParamsRequest[RawMessage] {
    override def route:               RestRoute                = Routes.getChannelMessage(messageId, channelId)
    override def responseDecoder:     Decoder[RawMessage]      = Decoder[RawMessage]
    override def handleResponse:      CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler
    override def requiredPermissions: Permission               = Permission.ReadMessageHistory
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * @param content The content of the message.
    * @param nonce A nonce used for optimistic message sending.
    * @param tts If this is a text-to-speech message.
    * @param files The files to send with this message. You can reference these
    *              files in the embed using `attachment://filename`.
    * @param embed An embed to send with this message.
    */
  case class CreateMessageData(
      content: String = "",
      nonce: Option[Snowflake] = None,
      tts: Boolean = false,
      files: Seq[Path] = Seq.empty,
      embed: Option[OutgoingEmbed] = None
  ) {
    files.foreach(path => require(Files.isRegularFile(path)))
    require(
      files.map(_.getFileName.toString).distinct.length == files.length,
      "Please use unique filenames for all files"
    )
    require(content.length <= 2000, "The content of a message can't exceed 2000 characters")
  }

  //We handle this here as the file argument needs special treatment
  implicit private val createMessageDataEncoder: Encoder[CreateMessageData] = (a: CreateMessageData) =>
    Json.obj("content" -> a.content.asJson, "nonce" -> a.nonce.asJson, "tts" -> a.tts.asJson, "embed" -> a.embed.asJson)

  /**
    * Create a message in a channel.
    */
  case class CreateMessage(channelId: ChannelId, params: CreateMessageData)
      extends SimpleRESTRequest[CreateMessageData, RawMessage] {
    override def route:           RestRoute                  = Routes.createMessage(channelId)
    override def paramsEncoder:   Encoder[CreateMessageData] = createMessageDataEncoder
    override def responseDecoder: Decoder[RawMessage]        = Decoder[RawMessage]
    override def handleResponse:  CacheHandler[RawMessage]   = RawHandlers.rawMessageUpdateHandler

    override def requestBody: RequestEntity = {
      this match {
        case CreateMessage(_, CreateMessageData(_, _, _, files, _)) if files.nonEmpty =>
          val fileParts = files.map { f =>
            FormData.BodyPart.fromPath(f.getFileName.toString, ContentTypes.`application/octet-stream`, f)
          }

          val jsonPart =
            FormData.BodyPart("payload_json", HttpEntity(ContentTypes.`application/json`, jsonParams.noSpaces))

          FormData(fileParts :+ jsonPart: _*).toEntity()
        case _ => super.requestBody
      }
    }
    override def requiredPermissions: Permission =
      if (params.tts) Permission(Permission.SendMessages, Permission.SendTtsMessages) else Permission.SendMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Create a reaction for a message.
    */
  case class CreateReaction(channelId: ChannelId, messageId: MessageId, emoji: String) extends NoParamsResponseRequest {
    override def route:               RestRoute  = Routes.createReaction(emoji, messageId, channelId)
    override def requiredPermissions: Permission = Permission.ReadMessageHistory
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Delete the clients reaction to a message.
    */
  case class DeleteOwnReaction(channelId: ChannelId, messageId: MessageId, emoji: String)
      extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteOwnReaction(emoji, messageId, channelId)
  }

  /**
    * Delete the reaction of another user to a message.
    */
  case class DeleteUserReaction(channelId: ChannelId, messageId: MessageId, emoji: String, userId: UserId)
      extends NoParamsResponseRequest {
    override def route:               RestRoute  = Routes.deleteUserReaction(userId, emoji, messageId, channelId)
    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Get all the users that have reacted with an emoji for a message.
    */
  case class GetReactions(channelId: ChannelId, messageId: MessageId, emoji: String)
      extends NoParamsRequest[Seq[User]] {
    override def route:           RestRoute               = Routes.getReactions(emoji, messageId, channelId)
    override def responseDecoder: Decoder[Seq[User]]      = Decoder[Seq[User]]
    override def handleResponse:  CacheHandler[Seq[User]] = CacheUpdateHandler.seqHandler(Handlers.userUpdateHandler)
  }

  /**
    * Clear all reactions from a message.
    */
  case class DeleteAllReactions(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
    override def route:               RestRoute  = Routes.deleteAllReactions(messageId, channelId)
    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * @param content The content of the new message
    * @param embed The embed of the new message
    */
  case class EditMessageData(content: Option[String] = None, embed: Option[OutgoingEmbed] = None) {
    require(content.forall(_.length < 2000))
  }

  /**
    * Edit an existing message
    */
  case class EditMessage(channelId: ChannelId, messageId: MessageId, params: EditMessageData)
      extends SimpleRESTRequest[EditMessageData, RawMessage] {
    override def route:           RestRoute                = Routes.editMessage(messageId, channelId)
    override def paramsEncoder:   Encoder[EditMessageData] = deriveEncoder[EditMessageData]
    override def responseDecoder: Decoder[RawMessage]      = Decoder[RawMessage]
    override def handleResponse:  CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler
  }

  /**
    * Delete a message
    */
  case class DeleteMessage(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
    override def route:               RestRoute  = Routes.deleteMessage(messageId, channelId)
    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * @param messages All the messages to delete.
    */
  case class BulkDeleteMessagesData(messages: Seq[MessageId]) {
    require(messages.length >= 2 && messages.length <= 100, "Can only delete between 2 and 100 messages at a time")
  }

  /**
    * Delete multiple messages in a single request. Can only be used on guild channels.
    */
  case class BulkDeleteMessages(channelId: ChannelId, params: BulkDeleteMessagesData)
      extends NoResponseRequest[BulkDeleteMessagesData] {
    override def route:               RestRoute                       = Routes.bulkDeleteMessages(channelId)
    override def paramsEncoder:       Encoder[BulkDeleteMessagesData] = deriveEncoder[BulkDeleteMessagesData]
    override def requiredPermissions: Permission                      = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * @param allow The permissions to allow.
    * @param deny The permissions to deny.
    * @param `type` If this is a user or role overwrite.
    */
  case class EditChannelPermissionsData(allow: Permission, deny: Permission, `type`: PermissionOverwriteType)

  /**
    * Edit a permission overwrite for a channel.
    */
  case class EditChannelPermissions(channelId: ChannelId, overwriteId: UserOrRoleId, params: EditChannelPermissionsData)
      extends NoResponseRequest[EditChannelPermissionsData] {
    override def route:               RestRoute                           = Routes.editChannelPermissions(overwriteId, channelId)
    override def paramsEncoder:       Encoder[EditChannelPermissionsData] = deriveEncoder[EditChannelPermissionsData]
    override def requiredPermissions: Permission                          = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Delete a permission overwrite for a channel.
    */
  case class DeleteChannelPermission(channelId: ChannelId, overwriteId: UserOrRoleId) extends NoParamsResponseRequest {
    override def route:               RestRoute  = Routes.deleteChannelPermissions(overwriteId, channelId)
    override def requiredPermissions: Permission = Permission.ManageRoles
  }

  /**
    * Get all invites for this channel. Can only be used on guild channels.
    */
  case class GetChannelInvites(channelId: ChannelId) extends NoParamsRequest[Seq[InviteWithMetadata]] {
    override def route:               RestRoute                             = Routes.getChannelInvites(channelId)
    override def responseDecoder:     Decoder[Seq[InviteWithMetadata]]      = Decoder[Seq[InviteWithMetadata]]
    override def handleResponse:      CacheHandler[Seq[InviteWithMetadata]] = NOOPHandler
    override def requiredPermissions: Permission                            = Permission.ManageChannels
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * @param maxAge Duration in seconds before this invite expires.
    * @param maxUses Amount of times this invite can be used before expiring,
    *                or 0 for unlimited.
    * @param temporary If this invite only grants temporary membership.
    * @param unique If true, guarantees to create a new invite.
    */
  case class CreateChannelInviteData(
      maxAge: Int = 86400,
      maxUses: Int = 0,
      temporary: Boolean = false,
      unique: Boolean = false
  )

  /**
    * Create a new invite for a channel. Can only be used on guild channels.
    */
  case class CreateChannelInvite(channelId: ChannelId, params: CreateChannelInviteData)
      extends SimpleRESTRequest[CreateChannelInviteData, Invite] {
    override def route:               RestRoute                        = Routes.getChannelInvites(channelId)
    override def paramsEncoder:       Encoder[CreateChannelInviteData] = deriveEncoder[CreateChannelInviteData]
    override def responseDecoder:     Decoder[Invite]                  = Decoder[Invite]
    override def handleResponse:      CacheHandler[Invite]             = NOOPHandler
    override def requiredPermissions: Permission                       = Permission.CreateInstantInvite
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Triggers a typing indicator in a channel.
    */
  case class TriggerTypingIndicator(channelId: ChannelId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.triggerTyping(channelId)
  }

  /**
    * Get all the pinned messages in a channel.
    */
  case class GetPinnedMessages(channelId: ChannelId) extends NoParamsRequest[Seq[RawMessage]] {
    override def route:           RestRoute                = Routes.getPinnedMessage(channelId)
    override def responseDecoder: Decoder[Seq[RawMessage]] = Decoder[Seq[RawMessage]]
    override def handleResponse: CacheHandler[Seq[RawMessage]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawMessageUpdateHandler)
  }

  /**
    * Add a new pinned message to a channel.
    */
  case class AddPinnedChannelMessages(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
    override def route:               RestRoute  = Routes.addPinnedChannelMessage(messageId, channelId)
    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Delete a pinned message in a channel.
    */
  case class DeletePinnedChannelMessages(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
    override def route:               RestRoute  = Routes.deletePinnedChannelMessage(messageId, channelId)
    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
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

  /**
    * Get all the emijis for this guild.
    */
  case class ListGuildEmojis(guildId: GuildId) extends ComplexRESTRequest[NotUsed, Seq[Emoji], GuildEmojisUpdateData] {
    override def route:           RestRoute                           = Routes.listGuildEmojis(guildId)
    override def paramsEncoder:   Encoder[NotUsed]                    = (_: NotUsed) => Json.obj()
    override def params:          NotUsed                             = NotUsed
    override def responseDecoder: Decoder[Seq[Emoji]]                 = Decoder[Seq[Emoji]]
    override def handleResponse:  CacheHandler[GuildEmojisUpdateData] = RawHandlers.guildEmojisUpdateDataHandler
    override def processResponse(response: Seq[Emoji]): GuildEmojisUpdateData =
      GuildEmojisUpdateData(guildId, response)
    override def havePermissions(implicit c: CacheSnapshot): Boolean = hasPermissionsGuild(guildId, requiredPermissions)
  }

  //Can take an array of role snowflakes, but it's only read for some bots, Ignored for now
  /**
    * @param name The name of the emoji.
    * @param image The image data for the emoji.
    */
  case class CreateGuildEmojiData(name: String, image: ImageData)

  /**
    * Create a new emoji for a guild.
    */
  case class CreateGuildEmoji(guildId: GuildId, params: CreateGuildEmojiData)
      extends SimpleRESTRequest[CreateGuildEmojiData, Emoji] {
    override def route:                                      RestRoute                     = Routes.createGuildEmoji(guildId)
    override def paramsEncoder:                              Encoder[CreateGuildEmojiData] = deriveEncoder[CreateGuildEmojiData]
    override def responseDecoder:                            Decoder[Emoji]                = Decoder[Emoji]
    override def handleResponse:                             CacheHandler[Emoji]           = Handlers.guildEmojiUpdateHandler(guildId)
    override def requiredPermissions:                        Permission                    = Permission.ManageEmojis
    override def havePermissions(implicit c: CacheSnapshot): Boolean                       = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get an emoji in a guild by id.
    */
  case class GetGuildEmoji(emojiId: EmojiId, guildId: GuildId) extends NoParamsRequest[Emoji] {
    override def route:           RestRoute           = Routes.getGuildEmoji(emojiId, guildId)
    override def responseDecoder: Decoder[Emoji]      = Decoder[Emoji]
    override def handleResponse:  CacheHandler[Emoji] = Handlers.guildEmojiUpdateHandler(guildId)
  }

  //Can take an array of role snowflakes, but it's only read for some bots, Ignored for now
  /**
    * @param name The new emoji name.
    */
  case class ModifyGuildEmojiData(name: String)

  /**
    * Modify an existing emoji.
    */
  case class ModifyGuildEmoji(emojiId: EmojiId, guildId: GuildId, params: ModifyGuildEmojiData)
      extends SimpleRESTRequest[ModifyGuildEmojiData, Emoji] {
    override def route:                                      RestRoute                     = Routes.modifyGuildEmoji(emojiId, guildId)
    override def paramsEncoder:                              Encoder[ModifyGuildEmojiData] = deriveEncoder[ModifyGuildEmojiData]
    override def responseDecoder:                            Decoder[Emoji]                = Decoder[Emoji]
    override def handleResponse:                             CacheHandler[Emoji]           = Handlers.guildEmojiUpdateHandler(guildId)
    override def requiredPermissions:                        Permission                    = Permission.ManageEmojis
    override def havePermissions(implicit c: CacheSnapshot): Boolean                       = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Delete an emoji from a guild.
    */
  case class DeleteGuildEmoji(emojiId: EmojiId, guildId: GuildId) extends NoParamsResponseRequest {
    override def route:                                      RestRoute  = Routes.deleteGuildEmoji(emojiId, guildId)
    override def requiredPermissions:                        Permission = Permission.ManageEmojis
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  //Guild
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
      roles: Seq[Role],
      channels: Seq[CreateGuildChannelData] //Techically this should be partial channels, but I think this works too
  ) {
    require(name.length >= 2 && name.length <= 100, "The guild name has to be between 2 and 100 characters")
  }

  /**
    * Create a new guild. Bots can only have 10 guilds by default.
    */
  case class CreateGuild(params: CreateGuildData) extends SimpleRESTRequest[CreateGuildData, RawGuild] {
    override def route: RestRoute = Routes.createGuild
    override def paramsEncoder: Encoder[CreateGuildData] = {
      import io.circe.generic.extras.auto._
      deriveEncoder[CreateGuildData]
    }
    override def responseDecoder: Decoder[RawGuild]      = Decoder[RawGuild]
    override def handleResponse:  CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler
  }

  /**
    * Get a guild by id.
    */
  case class GetGuild(guildId: GuildId) extends NoParamsRequest[RawGuild] {
    override def route:           RestRoute              = Routes.getGuild(guildId)
    override def responseDecoder: Decoder[RawGuild]      = Decoder[RawGuild]
    override def handleResponse:  CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler
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
    */
  case class ModifyGuildData(
      name: Option[String] = None,
      region: Option[String] = None,
      verificationLevel: Option[VerificationLevel] = None,
      defaultMessageNotifications: Option[NotificationLevel] = None,
      afkChannelId: Option[ChannelId] = None,
      afkTimeout: Option[Int] = None,
      icon: Option[ImageData] = None,
      ownerId: Option[UserId] = None,
      splash: Option[ImageData] = None
  )

  /**
    * Modify an existing guild.
    */
  case class ModifyGuild(guildId: GuildId, params: ModifyGuildData)
      extends SimpleRESTRequest[ModifyGuildData, RawGuild] {
    override def route:                                      RestRoute                = Routes.modifyGuild(guildId)
    override def paramsEncoder:                              Encoder[ModifyGuildData] = deriveEncoder[ModifyGuildData]
    override def responseDecoder:                            Decoder[RawGuild]        = Decoder[RawGuild]
    override def handleResponse:                             CacheHandler[RawGuild]   = RawHandlers.rawGuildUpdateHandler
    override def requiredPermissions:                        Permission               = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean                  = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Delete a guild. Must be the owner.
    */
  case class DeleteGuild(guildId: GuildId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteGuild(guildId)
  }

  /**
    * Get all the channels for a guild.
    */
  case class GetGuildChannels(guildId: GuildId) extends NoParamsRequest[Seq[RawChannel]] {
    override def route:           RestRoute                = Routes.getGuildChannels(guildId)
    override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
    override def handleResponse: CacheHandler[Seq[RawChannel]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawChannelUpdateHandler)
  }

  /**
    * @param name The name of the channel.
    * @param `type` The channel type.
    * @param bitrate The bitrate for the channel if it's a voice channel.
    * @param userLimit The user limit for the channel if it's a voice channel.
    * @param permissionOverwrites The permission overwrites for the channel.
    * @param parentId The categori id for the channel.
    * @param nsfw If the channel is NSFW.
    */
  case class CreateGuildChannelData(
      name: String,
      `type`: Option[ChannelType] = None,
      bitrate: Option[Int] = None,
      userLimit: Option[Int] = None,
      permissionOverwrites: Option[Seq[PermissionOverwrite]] = None,
      parentId: Option[ChannelId] = None,
      nsfw: Option[Boolean] = None
  ) {
    require(name.length >= 2 && name.length <= 100, "A channel name has to be between 2 and 100 characters")
  }

  /**
    * Create a channel in a guild.
    */
  case class CreateGuildChannel(guildId: GuildId, params: CreateGuildChannelData)
      extends SimpleRESTRequest[CreateGuildChannelData, RawChannel] {
    override def route: RestRoute = Routes.createGuildChannel(guildId)
    override def paramsEncoder: Encoder[CreateGuildChannelData] = {
      import io.circe.generic.extras.auto._
      deriveEncoder[CreateGuildChannelData]
    }
    override def responseDecoder:                            Decoder[RawChannel]      = Decoder[RawChannel]
    override def handleResponse:                             CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
    override def requiredPermissions:                        Permission               = Permission.ManageChannels
    override def havePermissions(implicit c: CacheSnapshot): Boolean                  = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * @param id The channel id
    * @param position It's new position
    */
  case class ModifyGuildChannelPositionsData(id: ChannelId, position: Int)

  /**
    * Modify the positions of several channels.
    */
  case class ModifyGuildChannelPositions(guildId: GuildId, params: Seq[ModifyGuildChannelPositionsData])
      extends SimpleRESTRequest[Seq[ModifyGuildChannelPositionsData], Seq[RawChannel]] {
    override def route: RestRoute = Routes.modifyGuildChannelsPositions(guildId)
    override def paramsEncoder: Encoder[Seq[ModifyGuildChannelPositionsData]] = {
      implicit val enc: Encoder[ModifyGuildChannelPositionsData] = deriveEncoder[ModifyGuildChannelPositionsData]
      Encoder[Seq[ModifyGuildChannelPositionsData]]
    }
    override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
    override def handleResponse: CacheHandler[Seq[RawChannel]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawChannelUpdateHandler)
    override def requiredPermissions:                        Permission = Permission.ManageChannels
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  trait GuildMemberRequest[Params]
      extends ComplexRESTRequest[Params, RawGuildMember, GatewayEvent.RawGuildMemberWithGuild] {
    def guildId: GuildId
    override def responseDecoder: Decoder[RawGuildMember] = Decoder[RawGuildMember]
    override def handleResponse: CacheHandler[GatewayEvent.RawGuildMemberWithGuild] =
      RawHandlers.rawGuildMemberWithGuildUpdateHandler
    override def processResponse(response: RawGuildMember): GatewayEvent.RawGuildMemberWithGuild =
      GatewayEvent.RawGuildMemberWithGuild(guildId, response)
  }

  /**
    * Get a guild member by id.
    */
  case class GetGuildMember(guildId: GuildId, userId: UserId) extends GuildMemberRequest[NotUsed] {
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed
    override def route:         RestRoute        = Routes.getGuildMember(userId, guildId)
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
  case class ListGuildMembers(guildId: GuildId, params: ListGuildMembersData)
      extends ComplexRESTRequest[ListGuildMembersData, Seq[RawGuildMember], GatewayEvent.GuildMemberChunkData] {
    override def route:           RestRoute                     = Routes.listGuildMembers(guildId)
    override def paramsEncoder:   Encoder[ListGuildMembersData] = deriveEncoder[ListGuildMembersData]
    override def responseDecoder: Decoder[Seq[RawGuildMember]]  = Decoder[Seq[RawGuildMember]]
    override def handleResponse: CacheHandler[GatewayEvent.GuildMemberChunkData] =
      RawHandlers.rawGuildMemberChunkHandler
    override def processResponse(response: Seq[RawGuildMember]): GatewayEvent.GuildMemberChunkData =
      GatewayEvent.GuildMemberChunkData(guildId, response)
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
  case class AddGuildMember(guildId: GuildId, userId: UserId, params: AddGuildMemberData)
      extends GuildMemberRequest[AddGuildMemberData] {
    override def route:                RestRoute                   = Routes.addGuildMember(userId, guildId)
    override def paramsEncoder:        Encoder[AddGuildMemberData] = deriveEncoder[AddGuildMemberData]
    override def expectedResponseCode: StatusCode                  = StatusCodes.Created
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
    override def havePermissions(implicit c: CacheSnapshot): Boolean = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * @param nick The nickname to give to the user.
    * @param roles The roles to give to the user.
    * @param mute If the user should be muted.
    * @param deaf If the user should be deafened.
    * @param channelId The id of the channel to move the user to.
    */
  case class ModifyGuildMemberData(
      nick: Option[String] = None,
      roles: Option[Seq[RoleId]] = None,
      mute: Option[Boolean] = None,
      deaf: Option[Boolean] = None,
      channelId: Option[ChannelId] = None
  )

  /**
    * Modify a guild member.
    */
  case class ModifyGuildMember(guildId: GuildId, userId: UserId, params: ModifyGuildMemberData)
      extends NoResponseRequest[ModifyGuildMemberData] {
    override def route:         RestRoute                      = Routes.modifyGuildMember(userId, guildId)
    override def paramsEncoder: Encoder[ModifyGuildMemberData] = deriveEncoder[ModifyGuildMemberData]
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
    override def havePermissions(implicit c: CacheSnapshot): Boolean = hasPermissionsGuild(guildId, requiredPermissions)
  }

  case class ModifyBotUsersNickData(nick: String)

  /**
    * Modify the clients nickname.
    */
  case class ModifyBotUsersNick(guildId: GuildId, params: ModifyBotUsersNickData)
      extends SimpleRESTRequest[ModifyBotUsersNickData, String] {
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
    override def requiredPermissions:                        Permission = Permission.ChangeNickname
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Add a role to a guild member.
    */
  case class AddGuildMemberRole(guildId: GuildId, userId: UserId, roleId: RoleId) extends NoParamsResponseRequest {
    override def route:                                      RestRoute  = Routes.addGuildMemberRole(roleId, userId, guildId)
    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Remove a role from a guild member.
    */
  case class RemoveGuildMemberRole(guildId: GuildId, userId: UserId, roleId: RoleId) extends NoParamsResponseRequest {
    override def route:                                      RestRoute  = Routes.removeGuildMemberRole(roleId, userId, guildId)
    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Kicks a guild member.
    */
  case class RemoveGuildMember(guildId: GuildId, userId: UserId) extends NoParamsResponseRequest {
    override def route:                                      RestRoute  = Routes.removeGuildMember(userId, guildId)
    override def requiredPermissions:                        Permission = Permission.KickMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get all the bans for this guild.
    */
  case class GetGuildBans(guildId: GuildId) extends ComplexRESTRequest[NotUsed, Seq[RawBan], Seq[(GuildId, RawBan)]] {
    override def route:           RestRoute            = Routes.getGuildBans(guildId)
    override def responseDecoder: Decoder[Seq[RawBan]] = Decoder[Seq[RawBan]]
    override def handleResponse: CacheHandler[Seq[(GuildId, RawBan)]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawBanUpdateHandler)
    override def params:                                     NotUsed                = NotUsed
    override def paramsEncoder:                              Encoder[NotUsed]       = (_: NotUsed) => Json.obj()
    override def processResponse(response: Seq[RawBan]):     Seq[(GuildId, RawBan)] = response.map(guildId -> _)
    override def requiredPermissions:                        Permission             = Permission.BanMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean                = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * @param `delete-message-days` The number of days to delete messages for
    *                              this banned user.
    */
  case class CreateGuildBanData(`delete-message-days`: Int)

  /**
    * Ban a user from a guild.
    */
  case class CreateGuildBan(guildId: GuildId, userId: UserId, params: CreateGuildBanData)
      extends NoResponseRequest[CreateGuildBanData] {
    override def route:                                      RestRoute                   = Routes.createGuildMemberBan(userId, guildId)
    override def paramsEncoder:                              Encoder[CreateGuildBanData] = deriveEncoder[CreateGuildBanData]
    override def requiredPermissions:                        Permission                  = Permission.BanMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean                     = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Unban a user from a guild.
    */
  case class RemoveGuildBan(guildId: GuildId, userId: UserId) extends NoParamsResponseRequest {
    override def route:                                      RestRoute  = Routes.removeGuildMemberBan(userId, guildId)
    override def requiredPermissions:                        Permission = Permission.BanMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get all the roles in a guild.
    */
  case class GetGuildRoles(guildId: GuildId)
      extends ComplexRESTRequest[NotUsed, Seq[RawRole], Seq[GatewayEvent.GuildRoleModifyData]] {
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed

    override def route:           RestRoute             = Routes.getGuildRole(guildId)
    override def responseDecoder: Decoder[Seq[RawRole]] = Decoder[Seq[RawRole]]
    override def handleResponse: CacheHandler[Seq[GatewayEvent.GuildRoleModifyData]] =
      CacheUpdateHandler.seqHandler(RawHandlers.roleUpdateHandler)
    override def processResponse(response: Seq[RawRole]): Seq[GatewayEvent.GuildRoleModifyData] =
      response.map(GatewayEvent.GuildRoleModifyData(guildId, _))
    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
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
  case class CreateGuildRole(guildId: GuildId, params: CreateGuildRoleData)
      extends ComplexRESTRequest[CreateGuildRoleData, RawRole, GatewayEvent.GuildRoleModifyData] {
    override def route:           RestRoute                                      = Routes.createGuildRole(guildId)
    override def paramsEncoder:   Encoder[CreateGuildRoleData]                   = deriveEncoder[CreateGuildRoleData]
    override def responseDecoder: Decoder[RawRole]                               = Decoder[RawRole]
    override def handleResponse:  CacheHandler[GatewayEvent.GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def processResponse(response: RawRole): GatewayEvent.GuildRoleModifyData =
      GatewayEvent.GuildRoleModifyData(guildId, response)
    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * @param id The role id.
    * @param position The new position of the role.
    */
  case class ModifyGuildRolePositionsData(id: RoleId, position: Int)

  /**
    * Modify the positions of several roles.
    */
  case class ModifyGuildRolePositions(guildId: GuildId, params: Seq[ModifyGuildRolePositionsData])
      extends ComplexRESTRequest[Seq[ModifyGuildRolePositionsData], Seq[RawRole], Seq[GatewayEvent.GuildRoleModifyData]] {
    override def route: RestRoute = Routes.modifyGuildRolePositions(guildId)
    override def paramsEncoder: Encoder[Seq[ModifyGuildRolePositionsData]] = {
      implicit val enc: Encoder[ModifyGuildRolePositionsData] = deriveEncoder[ModifyGuildRolePositionsData]
      Encoder[Seq[ModifyGuildRolePositionsData]]
    }
    override def responseDecoder: Decoder[Seq[RawRole]] = Decoder[Seq[RawRole]]
    override def handleResponse: CacheHandler[Seq[GatewayEvent.GuildRoleModifyData]] =
      CacheUpdateHandler.seqHandler(RawHandlers.roleUpdateHandler)
    override def processResponse(response: Seq[RawRole]): Seq[GatewayEvent.GuildRoleModifyData] =
      response.map(GatewayEvent.GuildRoleModifyData(guildId, _))
    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
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
  case class ModifyGuildRole(guildId: GuildId, roleId: RoleId, params: ModifyGuildRoleData)
      extends ComplexRESTRequest[ModifyGuildRoleData, RawRole, GatewayEvent.GuildRoleModifyData] {
    override def route:           RestRoute                                      = Routes.modifyGuildRole(roleId, guildId)
    override def paramsEncoder:   Encoder[ModifyGuildRoleData]                   = deriveEncoder[ModifyGuildRoleData]
    override def responseDecoder: Decoder[RawRole]                               = Decoder[RawRole]
    override def handleResponse:  CacheHandler[GatewayEvent.GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def processResponse(response: RawRole): GatewayEvent.GuildRoleModifyData =
      GatewayEvent.GuildRoleModifyData(guildId, response)
    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Delete a role in a guild.
    */
  case class DeleteGuildRole(guildId: GuildId, roleId: RoleId) extends NoParamsResponseRequest {
    override def route:                                      RestRoute  = Routes.deleteGuildRole(roleId, guildId)
    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * @param days The amount of days to prune for.
    */
  case class GuildPruneData(days: Int)

  /**
    * @param pruned The number of membres that would be removed.
    */
  case class GuildPruneResponse(pruned: Int)

  trait GuildPrune extends SimpleRESTRequest[GuildPruneData, GuildPruneResponse] {
    override def paramsEncoder:   Encoder[GuildPruneData]          = deriveEncoder[GuildPruneData]
    override def responseDecoder: Decoder[GuildPruneResponse]      = deriveDecoder[GuildPruneResponse]
    override def handleResponse:  CacheHandler[GuildPruneResponse] = NOOPHandler
  }

  /**
    * Check how many members would be removed if a prune was started now.
    */
  case class GetGuildPruneCount(guildId: GuildId, params: GuildPruneData) extends GuildPrune {
    override def route:                                      RestRoute  = Routes.getGuildPruneCount(guildId)
    override def requiredPermissions:                        Permission = Permission.KickMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Begin a guild prune.
    */
  case class BeginGuildPrune(guildId: GuildId, params: GuildPruneData) extends GuildPrune {
    override def route:                                      RestRoute  = Routes.beginGuildPrune(guildId)
    override def requiredPermissions:                        Permission = Permission.KickMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get the voice regions for this guild.
    */
  case class GetGuildVoiceRegions(guildId: GuildId) extends NoParamsRequest[Seq[VoiceRegion]] {
    override def route:           RestRoute                      = Routes.getGuildVoiceRegions(guildId)
    override def responseDecoder: Decoder[Seq[VoiceRegion]]      = Decoder[Seq[VoiceRegion]]
    override def handleResponse:  CacheHandler[Seq[VoiceRegion]] = NOOPHandler
  }

  /**
    * Get the invites for this guild.
    */
  case class GetGuildInvites(guildId: GuildId) extends NoParamsRequest[Seq[InviteWithMetadata]] {
    override def route:                                      RestRoute                             = Routes.getGuildInvites(guildId)
    override def responseDecoder:                            Decoder[Seq[InviteWithMetadata]]      = Decoder[Seq[InviteWithMetadata]]
    override def handleResponse:                             CacheHandler[Seq[InviteWithMetadata]] = NOOPHandler
    override def requiredPermissions:                        Permission                            = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean                               = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get the integrations for this guild.
    */
  case class GetGuildIntegrations(guildId: GuildId) extends NoParamsRequest[Seq[Integration]] {
    override def route:                                      RestRoute                      = Routes.getGuildIntegrations(guildId)
    override def responseDecoder:                            Decoder[Seq[Integration]]      = Decoder[Seq[Integration]]
    override def handleResponse:                             CacheHandler[Seq[Integration]] = NOOPHandler
    override def requiredPermissions:                        Permission                     = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean                        = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * @param `type` The integration type
    * @param id The integration id
    */
  case class CreateGuildIntegrationData(`type`: String /*TODO: Enum here*/, id: IntegrationId)

  /**
    * Attach an itegration to a guild.
    */
  case class CreateGuildIntegration(guildId: GuildId, params: CreateGuildIntegrationData)
      extends NoResponseRequest[CreateGuildIntegrationData] {
    override def route:                                      RestRoute                           = Routes.createGuildIntegrations(guildId)
    override def paramsEncoder:                              Encoder[CreateGuildIntegrationData] = deriveEncoder[CreateGuildIntegrationData]
    override def requiredPermissions:                        Permission                          = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean                             = hasPermissionsGuild(guildId, requiredPermissions)
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
  case class ModifyGuildIntegration(guildId: GuildId, integrationId: IntegrationId, params: ModifyGuildIntegrationData)
      extends NoResponseRequest[ModifyGuildIntegrationData] {
    override def route:                                      RestRoute                           = Routes.modifyGuildIntegration(integrationId, guildId)
    override def paramsEncoder:                              Encoder[ModifyGuildIntegrationData] = deriveEncoder[ModifyGuildIntegrationData]
    override def requiredPermissions:                        Permission                          = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean                             = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Delete an integration.
    */
  case class DeleteGuildIntegration(guildId: GuildId, integrationId: IntegrationId) extends NoParamsResponseRequest {
    override def route:                                      RestRoute  = Routes.deleteGuildIntegration(integrationId, guildId)
    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Sync an integration.
    */
  case class SyncGuildIntegration(guildId: GuildId, integrationId: IntegrationId) extends NoParamsResponseRequest {
    override def route:                                      RestRoute  = Routes.syncGuildIntegration(integrationId, guildId)
    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get the guild embed for a guild.
    */
  case class GetGuildEmbed(guildId: GuildId) extends NoParamsRequest[GuildEmbed] {
    override def route:                                      RestRoute                = Routes.getGuildEmbed(guildId)
    override def responseDecoder:                            Decoder[GuildEmbed]      = Decoder[GuildEmbed]
    override def handleResponse:                             CacheHandler[GuildEmbed] = NOOPHandler
    override def requiredPermissions:                        Permission               = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean                  = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Modify a guild embed for a guild.
    */
  case class ModifyGuildEmbed(guildId: GuildId, params: GuildEmbed) extends SimpleRESTRequest[GuildEmbed, GuildEmbed] {
    override def route:                                      RestRoute                = Routes.modifyGuildEmbed(guildId)
    override def paramsEncoder:                              Encoder[GuildEmbed]      = Encoder[GuildEmbed]
    override def responseDecoder:                            Decoder[GuildEmbed]      = Decoder[GuildEmbed]
    override def handleResponse:                             CacheHandler[GuildEmbed] = NOOPHandler
    override def requiredPermissions:                        Permission               = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean                  = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get an invite for a given invite code
    */
  case class GetInvite(inviteCode: String) extends NoParamsRequest[Invite] {
    override def route:           RestRoute            = Routes.getInvite(inviteCode)
    override def responseDecoder: Decoder[Invite]      = Decoder[Invite]
    override def handleResponse:  CacheHandler[Invite] = NOOPHandler
  }

  /**
    * Delete an invite.
    */
  case class DeleteInvite(inviteCode: String) extends NoParamsRequest[Invite] {
    override def route:               RestRoute            = Routes.deleteInvite(inviteCode)
    override def responseDecoder:     Decoder[Invite]      = Decoder[Invite]
    override def handleResponse:      CacheHandler[Invite] = NOOPHandler
    override def requiredPermissions: Permission           = Permission.ManageChannels
  }

  /**
    * Accept invite.
    */
  case class AcceptInvite(inviteCode: String) extends NoParamsRequest[Invite] {
    override def route:           RestRoute            = Routes.acceptInvite(inviteCode)
    override def responseDecoder: Decoder[Invite]      = Decoder[Invite]
    override def handleResponse:  CacheHandler[Invite] = NOOPHandler
  }

  /**
    * Fet the client user.
    */
  case object GetCurrentUser extends NoParamsRequest[User] {
    override def route:           RestRoute          = Routes.getCurrentUser
    override def responseDecoder: Decoder[User]      = Decoder[User]
    override def handleResponse:  CacheHandler[User] = Handlers.botUserUpdateHandler
  }

  /**
    * Get a user by id.
    */
  case class GetUser(userId: UserId) extends NoParamsRequest[User] {
    override def route:           RestRoute          = Routes.getUser(userId)
    override def responseDecoder: Decoder[User]      = Decoder[User]
    override def handleResponse:  CacheHandler[User] = Handlers.userUpdateHandler
  }

  case class GetUserGuildsGuild(
      id: GuildId,
      name: String,
      icon: Option[String],
      owner: Boolean,
      permissions: Permission
  )

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
  case class GetCurrentUserGuilds(params: GetCurrentUserGuildsData)
      extends SimpleRESTRequest[GetCurrentUserGuildsData, Seq[GetUserGuildsGuild]] {
    override def route:         RestRoute                         = Routes.getCurrentUserGuilds
    override def paramsEncoder: Encoder[GetCurrentUserGuildsData] = deriveEncoder[GetCurrentUserGuildsData]
    override def responseDecoder: Decoder[Seq[GetUserGuildsGuild]] = {
      implicit val dec: Decoder[GetUserGuildsGuild] = deriveDecoder[GetUserGuildsGuild]
      Decoder[Seq[GetUserGuildsGuild]]
    }
    override def handleResponse: CacheHandler[Seq[GetUserGuildsGuild]] = NOOPHandler
  }

  /**
    * Leave a guild.
    */
  case class LeaveGuild(guildId: GuildId) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.leaveGuild(guildId)
  }

  case object GetUserDMs extends NoParamsRequest[Seq[RawChannel]] {
    override def route:           RestRoute                = Routes.getUserDMs
    override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
    override def handleResponse: CacheHandler[Seq[RawChannel]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawChannelUpdateHandler)
  }

  /**
    * @param recipentId User to send a DM to.
    */
  case class CreateDMData(recipentId: UserId)

  /**
    * Create a new DM channel.
    */
  case class CreateDm(params: CreateDMData) extends SimpleRESTRequest[CreateDMData, RawChannel] {
    override def route:           RestRoute                = Routes.createDM
    override def paramsEncoder:   Encoder[CreateDMData]    = deriveEncoder[CreateDMData]
    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def handleResponse:  CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
  }

  /**
    * @param accessTokens The access tokens of users that have granted the bot
    *                     the `gdm.join` scope.
    * @param nicks A map specifying the nicnames for the users in this group DM.
    */
  case class CreateGroupDMData(accessTokens: Seq[String], nicks: Map[UserId, String])

  /**
    * Create a group DM. By default the client is limited to 10 active group DMs.
    */
  case class CreateGroupDm(params: CreateGroupDMData) extends SimpleRESTRequest[CreateGroupDMData, RawChannel] {
    override def route: RestRoute = Routes.createDM
    override def paramsEncoder: Encoder[CreateGroupDMData] = (data: CreateGroupDMData) => {
      Json.obj("access_tokens" -> data.accessTokens.asJson, "nicks" -> data.nicks.map(t => t._1.content -> t._2).asJson)
    }
    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def handleResponse:  CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
  }

  /**
    * Get a list of connection objects. Requires the `connection` OAuth2 scope.
    */
  case object GetUserConnections extends NoParamsRequest[Seq[Connection]] {
    override def route:           RestRoute                     = Routes.getUserConnections
    override def responseDecoder: Decoder[Seq[Connection]]      = Decoder[Seq[Connection]]
    override def handleResponse:  CacheHandler[Seq[Connection]] = NOOPHandler
  }

  //Voice
  /**
    * List all the voice regions that can be used when creating a guild.
    */
  case object ListVoiceRegions extends NoParamsRequest[Seq[VoiceRegion]] {
    override def route:           RestRoute                      = Routes.listVoiceRegions
    override def responseDecoder: Decoder[Seq[VoiceRegion]]      = Decoder[Seq[VoiceRegion]]
    override def handleResponse:  CacheHandler[Seq[VoiceRegion]] = NOOPHandler
  }

  //Webhook
  /**
    * @param name Name of the webhook
    * @param avatar The avatar data of the webhook
    */
  case class CreateWebhookData(name: String, avatar: ImageData) {
    require(name.length >= 2 && name.length <= 32, "Webhook name must be between 2 and 32 characters")
  }

  /**
    * Create a new webhook in a channel.
    */
  case class CreateWebhook(channelId: ChannelId, params: CreateWebhookData)
      extends SimpleRESTRequest[CreateWebhookData, Webhook] {
    override def route:               RestRoute                  = Routes.createWebhook(channelId)
    override def paramsEncoder:       Encoder[CreateWebhookData] = deriveEncoder[CreateWebhookData]
    override def responseDecoder:     Decoder[Webhook]           = Decoder[Webhook]
    override def handleResponse:      CacheHandler[Webhook]      = NOOPHandler
    override def requiredPermissions: Permission                 = Permission.ManageWebhooks
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Get the webhooks in a channel.
    */
  case class GetChannelWebhooks(channelId: ChannelId) extends NoParamsRequest[Seq[Webhook]] {
    override def route:               RestRoute                  = Routes.getChannelWebhooks(channelId)
    override def responseDecoder:     Decoder[Seq[Webhook]]      = Decoder[Seq[Webhook]]
    override def handleResponse:      CacheHandler[Seq[Webhook]] = NOOPHandler
    override def requiredPermissions: Permission                 = Permission.ManageWebhooks
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Get the webhooks in a guild.
    */
  case class GetGuildWebhooks(guildId: GuildId) extends NoParamsRequest[Seq[Webhook]] {
    override def route:                                      RestRoute                  = Routes.getGuildWebhooks(guildId)
    override def responseDecoder:                            Decoder[Seq[Webhook]]      = Decoder[Seq[Webhook]]
    override def handleResponse:                             CacheHandler[Seq[Webhook]] = NOOPHandler
    override def requiredPermissions:                        Permission                 = Permission.ManageWebhooks
    override def havePermissions(implicit c: CacheSnapshot): Boolean                    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get a webhook by id.
    */
  case class GetWebhook(id: Snowflake) extends NoParamsRequest[Webhook] {
    override def route:               RestRoute             = Routes.getWebhook(id)
    override def responseDecoder:     Decoder[Webhook]      = Decoder[Webhook]
    override def handleResponse:      CacheHandler[Webhook] = NOOPHandler
    override def requiredPermissions: Permission            = Permission.ManageWebhooks //TODO: Correct?
  }

  /**
    * Get a webhook by id with a token. Doesn't require authentication.
    */
  case class GetWebhookWithToken(id: Snowflake, token: String) extends NoParamsRequest[Webhook] {
    override def route:               RestRoute             = Routes.getWebhookWithToken(token, id)
    override def responseDecoder:     Decoder[Webhook]      = Decoder[Webhook]
    override def handleResponse:      CacheHandler[Webhook] = NOOPHandler
    override def requiredPermissions: Permission            = Permission.ManageWebhooks //TODO: Correct?
  }

  /**
    * @param name Name of the webhook.
    * @param avatar The avatar data of the webhook.
    * @param channelId The channel this webhook should be moved to.
    */
  case class ModifyWebhookData(
      name: Option[String] = None,
      avatar: Option[ImageData] = None,
      channelId: Option[Snowflake] = None
  )

  /**
    * Modify a webhook.
    */
  case class ModifyWebhook(id: Snowflake, params: ModifyWebhookData)
      extends SimpleRESTRequest[ModifyWebhookData, Webhook] {
    override def route:               RestRoute                  = Routes.getWebhook(id)
    override def responseDecoder:     Decoder[Webhook]           = Decoder[Webhook]
    override def paramsEncoder:       Encoder[ModifyWebhookData] = deriveEncoder[ModifyWebhookData]
    override def handleResponse:      CacheHandler[Webhook]      = NOOPHandler
    override def requiredPermissions: Permission                 = Permission.ManageWebhooks
  }

  /**
    * Modify a webhook with a token. Doesn't require authentication
    */
  case class ModifyWebhookWithToken(id: Snowflake, token: String, params: ModifyWebhookData)
      extends SimpleRESTRequest[ModifyWebhookData, Webhook] {
    override def route:               RestRoute                  = Routes.getWebhookWithToken(token, id)
    override def responseDecoder:     Decoder[Webhook]           = Decoder[Webhook]
    override def paramsEncoder:       Encoder[ModifyWebhookData] = deriveEncoder[ModifyWebhookData]
    override def handleResponse:      CacheHandler[Webhook]      = NOOPHandler
    override def requiredPermissions: Permission                 = Permission.ManageWebhooks
  }

  /**
    * Delete a webhook.
    */
  case class DeleteWebhook(id: Snowflake) extends NoParamsResponseRequest {
    override def route:               RestRoute  = Routes.deleteWebhook(id)
    override def requiredPermissions: Permission = Permission.ManageWebhooks
  }

  /**
    * Delete a webhook with a token. Doesn't require authentication
    */
  case class DeleteWebhookWithToken(id: Snowflake, token: String) extends NoParamsResponseRequest {
    override def route:               RestRoute  = Routes.deleteWebhookWithToken(token, id)
    override def requiredPermissions: Permission = Permission.ManageWebhooks
  }

  /*
  TODO
  case class ExecuteWebhook(id: Snowflake, token: String, params: Nothing) extends SimpleRESTRequest[Nothing, Nothing] {
    override def route: RestRoute = Routes.deleteWebhookWithToken(token, id)
  }
 */
}
