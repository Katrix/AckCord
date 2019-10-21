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

import java.nio.file.{Files, Path}

import ackcord.CacheSnapshot
import ackcord.data.DiscordProtocol._
import ackcord.data._
import ackcord.data.raw._
import ackcord.util.{JsonOption, JsonSome, JsonUndefined}
import akka.NotUsed
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity, Uri}
import io.circe._
import io.circe.syntax._

/**
  * Get a channel by id.
  */
case class GetChannel[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsRequest[RawChannel, Option[Channel], Ctx] {
  override def route: RequestRoute = Routes.getChannel(channelId)

  override def responseDecoder: Decoder[RawChannel]                  = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[Channel] = response.toChannel

  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * @param name New name of the channel.
  * @param position New position of the channel.
  * @param topic The new channel topic for text channels.
  * @param nsfw If the channel is NSFW for text channels.
  * @param bitrate The new channel bitrate for voice channels.
  * @param userLimit The new user limit for voice channel.
  * @param rateLimitPerUser The new user ratelimit for guild text channels.
  * @param permissionOverwrites The new channel permission overwrites.
  * @param parentId The new category id of the channel.
  */
case class ModifyChannelData(
    name: JsonOption[String] = JsonUndefined,
    position: JsonOption[Int] = JsonUndefined,
    topic: JsonOption[String] = JsonUndefined,
    nsfw: JsonOption[Boolean] = JsonUndefined,
    rateLimitPerUser: JsonOption[Int] = JsonUndefined,
    bitrate: JsonOption[Int] = JsonUndefined,
    userLimit: JsonOption[Int] = JsonUndefined,
    permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
    parentId: JsonOption[ChannelId] = JsonUndefined
) {
  require(name.forall(_.length <= 100), "Name must be between 2 and 100 characters")
  require(topic.forall(_.length <= 100), "Topic must be between 0 and 1024 characters")
  require(bitrate.forall(b => b >= 8000 && b <= 128000), "Bitrate must be between 8000 and 128000 bits")
  require(userLimit.forall(b => b >= 0 && b <= 99), "User limit must be between 0 and 99 users")
  require(rateLimitPerUser.forall(i => i >= 0 && i <= 21600), "Rate limit per user must be between 0 ad 21600")
}
object ModifyChannelData {
  implicit val encoder: Encoder[ModifyChannelData] = (a: ModifyChannelData) => {
    JsonOption.removeUndefinedToObj(
      "name"                  -> a.name.map(_.asJson),
      "position"              -> a.position.map(_.asJson),
      "topic"                 -> a.topic.map(_.asJson),
      "nsfw"                  -> a.nsfw.map(_.asJson),
      "rate_limit_per_user"   -> a.rateLimitPerUser.map(_.asJson),
      "bitrate"               -> a.bitrate.map(_.asJson),
      "user_limit"            -> a.userLimit.map(_.asJson),
      "permission_overwrites" -> a.permissionOverwrites.map(_.asJson),
      "parent_id"             -> a.parentId.map(_.asJson)
    )
  }
}

/**
  * Update the settings of a channel.
  * @param channelId The channel to update.
  */
case class ModifyChannel[Ctx](
    channelId: ChannelId,
    params: ModifyChannelData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends ReasonRequest[ModifyChannel[Ctx], ModifyChannelData, RawChannel, Option[Channel], Ctx] {
  override def route: RequestRoute                       = Routes.modifyChannelPut(channelId)
  override def paramsEncoder: Encoder[ModifyChannelData] = ModifyChannelData.encoder
  override def jsonPrinter: Printer                      = Printer.noSpaces

  override def responseDecoder: Decoder[RawChannel]                  = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[Channel] = response.toChannel

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): ModifyChannel[Ctx] = copy(reason = Some(reason))
}

/**
  * Delete a guild channel, or close a DM channel.
  */
case class DeleteCloseChannel[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed, reason: Option[String] = None)
    extends NoParamsReasonRequest[DeleteCloseChannel[Ctx], RawChannel, Option[Channel], Ctx] {
  override def route: RequestRoute = Routes.deleteCloseChannel(channelId)

  override def responseDecoder: Decoder[RawChannel]                  = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[Channel] = response.toChannel

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): DeleteCloseChannel[Ctx] = copy(reason = Some(reason))
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
  require(Seq(around, before, after).count(_.isDefined) <= 1, "The around, before, after fields are mutually exclusive")
  require(limit.forall(c => c >= 1 && c <= 100), "Count must be between 1 and 100")

  def toMap: Map[String, String] =
    Map(
      "around" -> around.map(_.asString),
      "before" -> before.map(_.asString),
      "after"  -> after.map(_.asString),
      "limit"  -> limit.map(_.toString)
    ).flatMap {
      case (name, Some(value)) => Some(name -> value)
      case (_, None)           => None
    }
}

/**
  * Get the messages in a channel.
  */
case class GetChannelMessages[Ctx](channelId: ChannelId, query: GetChannelMessagesData, context: Ctx = NotUsed: NotUsed)
    extends NoParamsRequest[Seq[RawMessage], Seq[Message], Ctx] {
  override def route: RequestRoute = {
    val base = Routes.getChannelMessages(channelId)
    base.copy(uri = base.uri.withQuery(Uri.Query(query.toMap)))
  }

  override def responseDecoder: Decoder[Seq[RawMessage]]               = Decoder[Seq[RawMessage]]
  override def toNiceResponse(response: Seq[RawMessage]): Seq[Message] = response.map(_.toMessage)

  override def requiredPermissions: Permission = Permission.ViewChannel
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}
object GetChannelMessages {
  def around[Ctx](channelId: ChannelId, around: MessageId, limit: Option[Int] = None, context: Ctx = NotUsed: NotUsed) =
    new GetChannelMessages(channelId, GetChannelMessagesData(around = Some(around), limit = limit), context)

  def before[Ctx](channelId: ChannelId, before: MessageId, limit: Option[Int] = None, context: Ctx = NotUsed: NotUsed) =
    new GetChannelMessages(channelId, GetChannelMessagesData(before = Some(before), limit = limit), context)

  def after[Ctx](channelId: ChannelId, after: MessageId, limit: Option[Int] = None, context: Ctx = NotUsed: NotUsed) =
    new GetChannelMessages(channelId, GetChannelMessagesData(after = Some(after), limit = limit), context)
}

/**
  * Get a specific message in a channel.
  */
case class GetChannelMessage[Ctx](channelId: ChannelId, messageId: MessageId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsRequest[RawMessage, Message, Ctx] {
  override def route: RequestRoute = Routes.getChannelMessage(channelId, messageId)

  override def responseDecoder: Decoder[RawMessage]          = Decoder[RawMessage]
  override def toNiceResponse(response: RawMessage): Message = response.toMessage

  override def requiredPermissions: Permission = Permission.ReadMessageHistory
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
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
    nonce: Option[RawSnowflake] = None,
    tts: Boolean = false,
    files: Seq[Path] = Seq.empty,
    embed: Option[OutgoingEmbed] = None
) {
  files.foreach(path => require(Files.isRegularFile(path)))
  require(
    files.map(_.getFileName.toString).distinct.lengthCompare(files.length) == 0,
    "Please use unique filenames for all files"
  )
  require(content.length <= 2000, "The content of a message can't exceed 2000 characters")
}
object CreateMessageData {

  //We handle this here as the file argument needs special treatment
  implicit val encoder: Encoder[CreateMessageData] = (a: CreateMessageData) =>
    Json.obj("content" -> a.content.asJson, "nonce" -> a.nonce.asJson, "tts" -> a.tts.asJson, "embed" -> a.embed.asJson)
}

/**
  * Create a message in a channel.
  */
case class CreateMessage[Ctx](channelId: ChannelId, params: CreateMessageData, context: Ctx = NotUsed: NotUsed)
    extends RESTRequest[CreateMessageData, RawMessage, Message, Ctx] {
  override def route: RequestRoute                       = Routes.createMessage(channelId)
  override def paramsEncoder: Encoder[CreateMessageData] = CreateMessageData.encoder
  override def requestBody: RequestEntity = {
    this match {
      case CreateMessage(_, CreateMessageData(_, _, _, files, _), _) if files.nonEmpty =>
        val fileParts = files.map { f =>
          FormData.BodyPart.fromPath(f.getFileName.toString, ContentTypes.`application/octet-stream`, f)
        }

        val jsonPart =
          FormData.BodyPart(
            "payload_json",
            HttpEntity(ContentTypes.`application/json`, jsonParams.printWith(jsonPrinter))
          )

        FormData(fileParts :+ jsonPart: _*).toEntity()
      case _ => super.requestBody
    }
  }

  override def responseDecoder: Decoder[RawMessage]          = Decoder[RawMessage]
  override def toNiceResponse(response: RawMessage): Message = response.toMessage

  override def requiredPermissions: Permission =
    if (params.tts) Permission(Permission.SendMessages, Permission.SendTtsMessages) else Permission.SendMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}
object CreateMessage {
  def mkContent[Ctx](channelId: ChannelId, content: String, context: Ctx = NotUsed: NotUsed): CreateMessage[Ctx] =
    new CreateMessage(channelId, CreateMessageData(content), context)

  def mkEmbed[Ctx](channelId: ChannelId, embed: OutgoingEmbed, context: Ctx = NotUsed: NotUsed): CreateMessage[Ctx] =
    new CreateMessage(channelId, CreateMessageData(embed = Some(embed)), context)
}

/**
  * Create a reaction for a message.
  * @param emoji The emoji to send.
  */
case class CreateReaction[Ctx](
    channelId: ChannelId,
    messageId: MessageId,
    emoji: String,
    context: Ctx = NotUsed: NotUsed
) extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.createReaction(channelId, messageId, emoji)

  override def requiredPermissions: Permission = Permission.ReadMessageHistory
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * Delete the clients reaction to a message.
  */
case class DeleteOwnReaction[Ctx](
    channelId: ChannelId,
    messageId: MessageId,
    emoji: String,
    context: Ctx = NotUsed: NotUsed
) extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.deleteOwnReaction(channelId, messageId, emoji)
}

/**
  * Delete the reaction of another user to a message.
  */
case class DeleteUserReaction[Ctx](
    channelId: ChannelId,
    messageId: MessageId,
    emoji: String,
    userId: UserId,
    context: Ctx = NotUsed: NotUsed
) extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.deleteUserReaction(channelId, messageId, emoji, userId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * @param before Get users before this user.
  * @param after Get users after this user.
  * @param limit The max amount of users to return. Defaults to 25.
  */
case class GetReactionsData(before: Option[UserId] = None, after: Option[UserId] = None, limit: Option[Int] = None)

/**
  * Get all the users that have reacted with an emoji for a message.
  */
case class GetReactions[Ctx](
    channelId: ChannelId,
    messageId: MessageId,
    emoji: String,
    queryParams: GetReactionsData,
    context: Ctx = NotUsed: NotUsed
) extends NoParamsNiceResponseRequest[Seq[User], Ctx] {
  override def route: RequestRoute =
    Routes.getReactions(channelId, messageId, emoji, queryParams.before, queryParams.after, queryParams.limit)

  override def responseDecoder: Decoder[Seq[User]] = Decoder[Seq[User]]
}
object GetReactions {
  def before[Ctx](
      channelId: ChannelId,
      messageId: MessageId,
      emoji: String,
      before: UserId,
      limit: Option[Int] = None,
      context: Ctx = NotUsed: NotUsed
  ): GetReactions[Ctx] =
    new GetReactions(channelId, messageId, emoji, GetReactionsData(before = Some(before), limit = limit), context)

  def after[Ctx](
      channelId: ChannelId,
      messageId: MessageId,
      emoji: String,
      after: UserId,
      limit: Option[Int] = None,
      context: Ctx = NotUsed: NotUsed
  ): GetReactions[Ctx] =
    new GetReactions(channelId, messageId, emoji, GetReactionsData(after = Some(after), limit = limit), context)
}

/**
  * Clear all reactions from a message.
  */
case class DeleteAllReactions[Ctx](channelId: ChannelId, messageId: MessageId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.deleteAllReactions(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * @param content The content of the new message
  * @param embed The embed of the new message
  */
case class EditMessageData(
    content: JsonOption[String] = JsonUndefined,
    embed: JsonOption[OutgoingEmbed] = JsonUndefined
) {
  require(content.forall(_.length < 2000))
}
object EditMessageData {
  implicit val encoder: Encoder[EditMessageData] = (a: EditMessageData) =>
    JsonOption.removeUndefinedToObj("content" -> a.content.map(_.asJson), "embed" -> a.embed.map(_.asJson))
}

/**
  * Edit an existing message
  */
case class EditMessage[Ctx](
    channelId: ChannelId,
    messageId: MessageId,
    params: EditMessageData,
    context: Ctx = NotUsed: NotUsed
) extends RESTRequest[EditMessageData, RawMessage, Message, Ctx] {
  override def route: RequestRoute                     = Routes.editMessage(channelId, messageId)
  override def paramsEncoder: Encoder[EditMessageData] = EditMessageData.encoder
  override def jsonPrinter: Printer                    = Printer.noSpaces

  override def responseDecoder: Decoder[RawMessage]          = Decoder[RawMessage]
  override def toNiceResponse(response: RawMessage): Message = response.toMessage
}
object EditMessage {
  def mkContent[Ctx](
      channelId: ChannelId,
      messageId: MessageId,
      content: String,
      context: Ctx = NotUsed: NotUsed
  ): EditMessage[Ctx] = new EditMessage(channelId, messageId, EditMessageData(JsonSome(content)), context)

  def mkEmbed[Ctx](
      channelId: ChannelId,
      messageId: MessageId,
      embed: OutgoingEmbed,
      context: Ctx = NotUsed: NotUsed
  ): EditMessage[Ctx] = new EditMessage(channelId, messageId, EditMessageData(embed = JsonSome(embed)), context)
}

/**
  * Delete a message
  */
case class DeleteMessage[Ctx](
    channelId: ChannelId,
    messageId: MessageId,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteMessage[Ctx], Ctx] {
  override def route: RequestRoute = Routes.deleteMessage(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): DeleteMessage[Ctx] = copy(reason = Some(reason))
}

/**
  * @param messages All the messages to delete.
  */
case class BulkDeleteMessagesData(messages: Seq[MessageId]) {
  require(
    messages.lengthCompare(2) >= 0 && messages.lengthCompare(100) <= 0,
    "Can only delete between 2 and 100 messages at a time"
  )
}

/**
  * Delete multiple messages in a single request. Can only be used on guild channels.
  */
case class BulkDeleteMessages[Ctx](
    channelId: ChannelId,
    params: BulkDeleteMessagesData,
    context: Ctx = NotUsed: NotUsed
) extends NoResponseRequest[BulkDeleteMessagesData, Ctx] {
  override def route: RequestRoute                            = Routes.bulkDeleteMessages(channelId)
  override def paramsEncoder: Encoder[BulkDeleteMessagesData] = derivation.deriveEncoder(derivation.renaming.snakeCase)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}
object BulkDeleteMessages {
  def mk[Ctx](
      channelId: ChannelId,
      messages: Seq[MessageId],
      context: Ctx = NotUsed: NotUsed
  ): BulkDeleteMessages[Ctx] = new BulkDeleteMessages(channelId, BulkDeleteMessagesData(messages), context)
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
case class EditChannelPermissions[Ctx](
    channelId: ChannelId,
    overwriteId: UserOrRoleId,
    params: EditChannelPermissionsData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoResponseReasonRequest[EditChannelPermissions[Ctx], EditChannelPermissionsData, Ctx] {
  override def route: RequestRoute = Routes.editChannelPermissions(channelId, overwriteId)
  override def paramsEncoder: Encoder[EditChannelPermissionsData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): EditChannelPermissions[Ctx] = copy(reason = Some(reason))
}
object EditChannelPermissions {
  def mk[Ctx](
      channelId: ChannelId,
      overwriteId: UserOrRoleId,
      allow: Permission,
      deny: Permission,
      tpe: PermissionOverwriteType,
      context: Ctx = NotUsed: NotUsed
  ): EditChannelPermissions[Ctx] =
    new EditChannelPermissions(channelId, overwriteId, EditChannelPermissionsData(allow, deny, tpe), context)
}

/**
  * Delete a permission overwrite for a channel.
  */
case class DeleteChannelPermission[Ctx](
    channelId: ChannelId,
    overwriteId: UserOrRoleId,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteChannelPermission[Ctx], Ctx] {
  override def route: RequestRoute = Routes.deleteChannelPermissions(channelId, overwriteId)

  override def requiredPermissions: Permission = Permission.ManageRoles

  override def withReason(reason: String): DeleteChannelPermission[Ctx] = copy(reason = Some(reason))
}

/**
  * Get all invites for this channel. Can only be used on guild channels.
  */
case class GetChannelInvites[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Seq[InviteWithMetadata], Ctx] {
  override def route: RequestRoute = Routes.getChannelInvites(channelId)

  override def responseDecoder: Decoder[Seq[InviteWithMetadata]] = Decoder[Seq[InviteWithMetadata]]

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
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
case class CreateChannelInvite[Ctx](
    channelId: ChannelId,
    params: CreateChannelInviteData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[CreateChannelInvite[Ctx], CreateChannelInviteData, Invite, Ctx] {
  override def route: RequestRoute                             = Routes.getChannelInvites(channelId)
  override def paramsEncoder: Encoder[CreateChannelInviteData] = derivation.deriveEncoder(derivation.renaming.snakeCase)

  override def responseDecoder: Decoder[Invite] = Decoder[Invite]

  override def requiredPermissions: Permission = Permission.CreateInstantInvite
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): CreateChannelInvite[Ctx] = copy(reason = Some(reason))
}
object CreateChannelInvite {
  def mk[Ctx](
      channelId: ChannelId,
      maxAge: Int = 86400,
      maxUses: Int = 0,
      temporary: Boolean = false,
      unique: Boolean = false,
      context: Ctx = NotUsed: NotUsed
  ): CreateChannelInvite[Ctx] =
    new CreateChannelInvite(channelId, CreateChannelInviteData(maxAge, maxUses, temporary, unique), context)
}

/**
  * Triggers a typing indicator in a channel.
  */
case class TriggerTypingIndicator[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.triggerTyping(channelId)
}

/**
  * Get all the pinned messages in a channel.
  */
case class GetPinnedMessages[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsRequest[Seq[RawMessage], Seq[Message], Ctx] {
  override def route: RequestRoute = Routes.getPinnedMessage(channelId)

  override def responseDecoder: Decoder[Seq[RawMessage]]               = Decoder[Seq[RawMessage]]
  override def toNiceResponse(response: Seq[RawMessage]): Seq[Message] = response.map(_.toMessage)
}

/**
  * Add a new pinned message to a channel.
  */
case class AddPinnedChannelMessages[Ctx](channelId: ChannelId, messageId: MessageId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.addPinnedChannelMessage(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * Delete a pinned message in a channel.
  */
case class DeletePinnedChannelMessages[Ctx](channelId: ChannelId, messageId: MessageId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsResponseRequest[Ctx] {
  override def route: RequestRoute = Routes.deletePinnedChannelMessage(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/*
case class GroupDMAddRecipientData(accessToken: String, nick: String)
case class GroupDMAddRecipient[Ctx](channelId:       Snowflake, userId: Snowflake, params: GroupDMAddRecipientData, context: Ctx = NotUsed: NotUsed)
    extends RESTRequest[GroupDMAddRecipientData, Ctx] {
  override def route:         RestRoute                        = Routes.groupDmAddRecipient(userId, channelId)
  override def paramsEncoder: Encoder[GroupDMAddRecipientData] = derivation.deriveEncoder(derivation.renaming.snakeCase)
}

case class GroupDMRemoveRecipient[Ctx](channelId: Snowflake, userId: Snowflake, context: Ctx = NotUsed: NotUsed) extends NoParamsRequest {
  override def route: RestRoute = Routes.groupDmRemoveRecipient(userId, channelId)
}
 */

/**
  * Get all the emojis for this guild.
  */
case class ListGuildEmojis[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends RESTRequest[NotUsed, Seq[RawEmoji], Seq[Emoji], Ctx] {
  override def route: RequestRoute             = Routes.listGuildEmojis(guildId)
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
  override def params: NotUsed                 = NotUsed

  override def responseDecoder: Decoder[Seq[RawEmoji]]             = Decoder[Seq[RawEmoji]]
  override def toNiceResponse(response: Seq[RawEmoji]): Seq[Emoji] = response.map(_.toEmoji)
}

/**
  * @param name The name of the emoji.
  * @param image The image data for the emoji.
  * @param roles Whitelist of roles that can use this emoji.
  */
case class CreateGuildEmojiData(name: String, image: ImageData, roles: Seq[RoleId])

/**
  * Create a new emoji for a guild.
  */
case class CreateGuildEmoji[Ctx](
    guildId: GuildId,
    params: CreateGuildEmojiData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends ReasonRequest[CreateGuildEmoji[Ctx], CreateGuildEmojiData, RawEmoji, Emoji, Ctx] {
  override def route: RequestRoute                          = Routes.createGuildEmoji(guildId)
  override def paramsEncoder: Encoder[CreateGuildEmojiData] = derivation.deriveEncoder(derivation.renaming.snakeCase)

  override def responseDecoder: Decoder[RawEmoji]        = Decoder[RawEmoji]
  override def toNiceResponse(response: RawEmoji): Emoji = response.toEmoji

  override def requiredPermissions: Permission = Permission.ManageEmojis
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildEmoji[Ctx] = copy(reason = Some(reason))
}
object CreateGuildEmoji {
  def mk[Ctx](
      guildId: GuildId,
      name: String,
      image: ImageData,
      roles: Seq[RoleId],
      context: Ctx = NotUsed: NotUsed
  ): CreateGuildEmoji[Ctx] = new CreateGuildEmoji(guildId, CreateGuildEmojiData(name, image, roles), context)
}

/**
  * Get an emoji in a guild by id.
  */
case class GetGuildEmoji[Ctx](emojiId: EmojiId, guildId: GuildId, context: Ctx = NotUsed: NotUsed)
    extends NoParamsRequest[RawEmoji, Emoji, Ctx] {
  override def route: RequestRoute = Routes.getGuildEmoji(guildId, emojiId)

  override def responseDecoder: Decoder[RawEmoji]        = Decoder[RawEmoji]
  override def toNiceResponse(response: RawEmoji): Emoji = response.toEmoji
}

/**
  * @param name The new emoji name.
  * @param roles Whitelist of roles that can use this emoji.
  */
case class ModifyGuildEmojiData(name: String, roles: Seq[RoleId])

/**
  * Modify an existing emoji.
  */
case class ModifyGuildEmoji[Ctx](
    emojiId: EmojiId,
    guildId: GuildId,
    params: ModifyGuildEmojiData,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildEmoji[Ctx], ModifyGuildEmojiData, RawEmoji, Emoji, Ctx] {
  override def route: RequestRoute                          = Routes.modifyGuildEmoji(guildId, emojiId)
  override def paramsEncoder: Encoder[ModifyGuildEmojiData] = derivation.deriveEncoder(derivation.renaming.snakeCase)

  override def responseDecoder: Decoder[RawEmoji]        = Decoder[RawEmoji]
  override def toNiceResponse(response: RawEmoji): Emoji = response.toEmoji

  override def requiredPermissions: Permission = Permission.ManageEmojis
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildEmoji[Ctx] = copy(reason = Some(reason))
}
object ModifyGuildEmoji {
  def mk[Ctx](
      emojiId: EmojiId,
      guildId: GuildId,
      name: String,
      roles: Seq[RoleId],
      context: Ctx = NotUsed: NotUsed
  ): ModifyGuildEmoji[Ctx] = new ModifyGuildEmoji(emojiId, guildId, ModifyGuildEmojiData(name, roles), context)
}

/**
  * Delete an emoji from a guild.
  */
case class DeleteGuildEmoji[Ctx](
    emojiId: EmojiId,
    guildId: GuildId,
    context: Ctx = NotUsed: NotUsed,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteGuildEmoji[Ctx], Ctx] {
  override def route: RequestRoute = Routes.deleteGuildEmoji(guildId, emojiId)

  override def requiredPermissions: Permission = Permission.ManageEmojis
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): DeleteGuildEmoji[Ctx] = copy(reason = Some(reason))
}
