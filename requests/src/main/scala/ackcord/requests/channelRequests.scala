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
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe._
import io.circe.syntax._

/**
  * Get a channel by id.
  */
case class GetChannel(channelId: ChannelId) extends NoParamsRequest[RawChannel, Option[Channel]] {
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
case class ModifyChannel(
    channelId: ChannelId,
    params: ModifyChannelData,
    reason: Option[String] = None
) extends ReasonRequest[ModifyChannel, ModifyChannelData, RawChannel, Option[Channel]] {
  override def route: RequestRoute                       = Routes.modifyChannel(channelId)
  override def paramsEncoder: Encoder[ModifyChannelData] = ModifyChannelData.encoder
  override def jsonPrinter: Printer                      = Printer.noSpaces

  override def responseDecoder: Decoder[RawChannel]                  = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[Channel] = response.toChannel

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): ModifyChannel = copy(reason = Some(reason))
}

/**
  * Delete a guild channel, or close a DM channel.
  */
case class DeleteCloseChannel(channelId: ChannelId, reason: Option[String] = None)
    extends NoParamsReasonRequest[DeleteCloseChannel, RawChannel, Option[Channel]] {
  override def route: RequestRoute = Routes.deleteCloseChannel(channelId)

  override def responseDecoder: Decoder[RawChannel]                  = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[Channel] = response.toChannel

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): DeleteCloseChannel = copy(reason = Some(reason))
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
case class GetChannelMessages(channelId: ChannelId, query: GetChannelMessagesData)
    extends NoParamsRequest[Seq[RawMessage], Seq[Message]] {
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
  def around(channelId: ChannelId, around: MessageId, limit: Option[Int] = None) =
    new GetChannelMessages(channelId, GetChannelMessagesData(around = Some(around), limit = limit))

  def before(channelId: ChannelId, before: MessageId, limit: Option[Int] = None) =
    new GetChannelMessages(channelId, GetChannelMessagesData(before = Some(before), limit = limit))

  def after(channelId: ChannelId, after: MessageId, limit: Option[Int] = None) =
    new GetChannelMessages(channelId, GetChannelMessagesData(after = Some(after), limit = limit))
}

/**
  * Get a specific message in a channel.
  */
case class GetChannelMessage(channelId: ChannelId, messageId: MessageId) extends NoParamsRequest[RawMessage, Message] {
  override def route: RequestRoute = Routes.getChannelMessage(channelId, messageId)

  override def responseDecoder: Decoder[RawMessage]          = Decoder[RawMessage]
  override def toNiceResponse(response: RawMessage): Message = response.toMessage

  override def requiredPermissions: Permission = Permission.ReadMessageHistory
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

sealed trait CreateMessageFile {
  def fileName: String
  def isValid: Boolean
  protected[requests] def toBodyPart: FormData.BodyPart
}
object CreateMessageFile {
  case class FromPath(path: Path) extends CreateMessageFile {
    override def fileName: String = path.getFileName.toString
    override def isValid: Boolean = Files.isRegularFile(path)
    override protected[requests] def toBodyPart: FormData.BodyPart =
      FormData.BodyPart.fromPath(fileName, ContentTypes.`application/octet-stream`, path)
  }

  case class SourceFile(
      contentType: ContentType,
      contentLength: Int,
      bytes: Source[ByteString, NotUsed],
      fileName: String
  ) extends CreateMessageFile {
    override def isValid: Boolean = true

    override protected[requests] def toBodyPart: FormData.BodyPart =
      FormData.BodyPart(fileName, HttpEntity(contentType, contentLength, bytes), Map("filename" -> fileName))
  }

  case class ByteFile(
      contentType: ContentType,
      bytes: ByteString,
      fileName: String
  ) extends CreateMessageFile {
    override def isValid: Boolean = true

    override protected[requests] def toBodyPart: FormData.BodyPart =
      FormData.BodyPart(
        fileName,
        HttpEntity(ContentTypes.`application/octet-stream`, bytes),
        Map("filename" -> fileName)
      )
  }

  case class StringFile(
      contentType: ContentType.NonBinary,
      contents: String,
      fileName: String
  ) extends CreateMessageFile {
    override def isValid: Boolean = true

    override protected[requests] def toBodyPart: FormData.BodyPart =
      FormData.BodyPart(fileName, HttpEntity(ContentTypes.`text/plain(UTF-8)`, contents), Map("filename" -> fileName))
  }

}

case class AllowedMention(
    parse: Seq[AllowedMentionTypes] = Seq.empty,
    roles: Seq[RoleId] = Seq.empty,
    users: Seq[UserId] = Seq.empty
)
object AllowedMention {
  val none: AllowedMention = AllowedMention()
  val all: AllowedMention = AllowedMention(
    parse = Seq(AllowedMentionTypes.Roles, AllowedMentionTypes.Users, AllowedMentionTypes.Everyone)
  )

  //noinspection NameBooleanParameters
  implicit val codec: Codec[AllowedMention] = derivation.deriveCodec(derivation.renaming.snakeCase, false, None)
}

sealed abstract class AllowedMentionTypes(val value: String) extends StringEnumEntry
object AllowedMentionTypes extends StringEnum[AllowedMentionTypes] with StringCirceEnum[AllowedMentionTypes] {
  override def values: IndexedSeq[AllowedMentionTypes] = findValues

  case object Roles    extends AllowedMentionTypes("roles")
  case object Users    extends AllowedMentionTypes("users")
  case object Everyone extends AllowedMentionTypes("everyone")
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
    files: Seq[CreateMessageFile] = Seq.empty,
    embed: Option[OutgoingEmbed] = None,
    allowedMentions: AllowedMention = AllowedMention.all
) {
  files.foreach(file => require(file.isValid))
  require(
    files.map(_.fileName).distinct.lengthCompare(files.length) == 0,
    "Please use unique filenames for all files"
  )
  require(content.length <= 2000, "The content of a message can't exceed 2000 characters")
}
object CreateMessageData {

  //We handle this here as the file argument needs special treatment
  implicit val encoder: Encoder[CreateMessageData] = (a: CreateMessageData) =>
    Json.obj(
      "content" := a.content,
      "nonce" := a.nonce,
      "tts" := a.tts,
      "embed" := a.embed,
      "allowed_mentions" := a.allowedMentions
    )
}

/**
  * Create a message in a channel.
  */
case class CreateMessage(channelId: ChannelId, params: CreateMessageData)
    extends RESTRequest[CreateMessageData, RawMessage, Message] {
  override def route: RequestRoute                       = Routes.createMessage(channelId)
  override def paramsEncoder: Encoder[CreateMessageData] = CreateMessageData.encoder
  override def requestBody: RequestEntity = {
    this match {
      case CreateMessage(_, CreateMessageData(_, _, _, files, _, _)) if files.nonEmpty =>
        val jsonPart = FormData.BodyPart(
          "payload_json",
          HttpEntity(ContentTypes.`application/json`, jsonParams.printWith(jsonPrinter))
        )

        FormData(files.map(_.toBodyPart) :+ jsonPart: _*).toEntity()
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
  def mkContent(channelId: ChannelId, content: String): CreateMessage =
    new CreateMessage(channelId, CreateMessageData(content))

  def mkEmbed(channelId: ChannelId, embed: OutgoingEmbed): CreateMessage =
    new CreateMessage(channelId, CreateMessageData(embed = Some(embed)))
}

/**
  * Create a reaction for a message.
  * @param emoji The emoji to send.
  */
case class CreateReaction(
    channelId: ChannelId,
    messageId: MessageId,
    emoji: String
) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.createReaction(channelId, messageId, emoji)

  override def requiredPermissions: Permission = Permission.ReadMessageHistory
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * Delete the clients reaction to a message.
  */
case class DeleteOwnReaction(
    channelId: ChannelId,
    messageId: MessageId,
    emoji: String
) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteOwnReaction(channelId, messageId, emoji)
}

/**
  * Delete the reaction of another user to a message.
  */
case class DeleteUserReaction(
    channelId: ChannelId,
    messageId: MessageId,
    emoji: String,
    userId: UserId
) extends NoParamsResponseRequest {
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
case class GetReactions(
    channelId: ChannelId,
    messageId: MessageId,
    emoji: String,
    queryParams: GetReactionsData
) extends NoParamsNiceResponseRequest[Seq[User]] {
  override def route: RequestRoute =
    Routes.getReactions(channelId, messageId, emoji, queryParams.before, queryParams.after, queryParams.limit)

  override def responseDecoder: Decoder[Seq[User]] = Decoder[Seq[User]]
}
object GetReactions {
  def before(
      channelId: ChannelId,
      messageId: MessageId,
      emoji: String,
      before: UserId,
      limit: Option[Int] = None
  ): GetReactions =
    new GetReactions(channelId, messageId, emoji, GetReactionsData(before = Some(before), limit = limit))

  def after(
      channelId: ChannelId,
      messageId: MessageId,
      emoji: String,
      after: UserId,
      limit: Option[Int] = None
  ): GetReactions =
    new GetReactions(channelId, messageId, emoji, GetReactionsData(after = Some(after), limit = limit))
}

/**
  * Clear all reactions from a message.
  */
case class DeleteAllReactions(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteAllReactions(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * Clear all reactions for a single emoji from a message.
  */
case class DeleteAllReactionsForEmoji(channelId: ChannelId, messageId: MessageId, emoji: String)
    extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteAllReactionsForEmoji(channelId, messageId, emoji)

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
    embed: JsonOption[OutgoingEmbed] = JsonUndefined,
    flags: JsonOption[MessageFlags] = JsonUndefined
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
case class EditMessage(
    channelId: ChannelId,
    messageId: MessageId,
    params: EditMessageData
) extends RESTRequest[EditMessageData, RawMessage, Message] {
  override def route: RequestRoute                     = Routes.editMessage(channelId, messageId)
  override def paramsEncoder: Encoder[EditMessageData] = EditMessageData.encoder
  override def jsonPrinter: Printer                    = Printer.noSpaces

  override def responseDecoder: Decoder[RawMessage]          = Decoder[RawMessage]
  override def toNiceResponse(response: RawMessage): Message = response.toMessage
}
object EditMessage {
  def mkContent(
      channelId: ChannelId,
      messageId: MessageId,
      content: String
  ): EditMessage = new EditMessage(channelId, messageId, EditMessageData(JsonSome(content)))

  def mkEmbed(
      channelId: ChannelId,
      messageId: MessageId,
      embed: OutgoingEmbed
  ): EditMessage = new EditMessage(channelId, messageId, EditMessageData(embed = JsonSome(embed)))

  def suppressEmbeds(
      channelId: ChannelId,
      messageId: MessageId,
      existingFlags: MessageFlags
  ) =
    new EditMessage(
      channelId,
      messageId,
      EditMessageData(flags = JsonSome(existingFlags -- MessageFlags.SuppressEmbeds))
    )
}

/**
  * Delete a message
  */
case class DeleteMessage(
    channelId: ChannelId,
    messageId: MessageId,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteMessage] {
  override def route: RequestRoute = Routes.deleteMessage(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): DeleteMessage = copy(reason = Some(reason))
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
case class BulkDeleteMessages(
    channelId: ChannelId,
    params: BulkDeleteMessagesData
) extends NoResponseRequest[BulkDeleteMessagesData] {
  override def route: RequestRoute = Routes.bulkDeleteMessages(channelId)
  override def paramsEncoder: Encoder[BulkDeleteMessagesData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}
object BulkDeleteMessages {
  def mk(
      channelId: ChannelId,
      messages: Seq[MessageId]
  ): BulkDeleteMessages = new BulkDeleteMessages(channelId, BulkDeleteMessagesData(messages))
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
case class EditChannelPermissions(
    channelId: ChannelId,
    overwriteId: UserOrRoleId,
    params: EditChannelPermissionsData,
    reason: Option[String] = None
) extends NoResponseReasonRequest[EditChannelPermissions, EditChannelPermissionsData] {
  override def route: RequestRoute = Routes.editChannelPermissions(channelId, overwriteId)
  override def paramsEncoder: Encoder[EditChannelPermissionsData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def requiredPermissions: Permission = Permission.ManageRoles
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): EditChannelPermissions = copy(reason = Some(reason))
}
object EditChannelPermissions {
  def mk(
      channelId: ChannelId,
      overwriteId: UserOrRoleId,
      allow: Permission,
      deny: Permission,
      tpe: PermissionOverwriteType
  ): EditChannelPermissions =
    new EditChannelPermissions(channelId, overwriteId, EditChannelPermissionsData(allow, deny, tpe))
}

/**
  * Delete a permission overwrite for a channel.
  */
case class DeleteChannelPermission(
    channelId: ChannelId,
    overwriteId: UserOrRoleId,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteChannelPermission] {
  override def route: RequestRoute = Routes.deleteChannelPermissions(channelId, overwriteId)

  override def requiredPermissions: Permission = Permission.ManageRoles

  override def withReason(reason: String): DeleteChannelPermission = copy(reason = Some(reason))
}

/**
  * Get all invites for this channel. Can only be used on guild channels.
  */
case class GetChannelInvites(channelId: ChannelId) extends NoParamsNiceResponseRequest[Seq[InviteWithMetadata]] {
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
  * @param targetUser The target user for this invite.
  */
case class CreateChannelInviteData(
    maxAge: Int = 86400,
    maxUses: Int = 0,
    temporary: Boolean = false,
    unique: Boolean = false,
    targetUser: Option[UserId],
    targetUserType: Option[Int] //TODO: What is the type here
)

/**
  * Create a new invite for a channel. Can only be used on guild channels.
  */
case class CreateChannelInvite(
    channelId: ChannelId,
    params: CreateChannelInviteData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[CreateChannelInvite, CreateChannelInviteData, Invite] {
  override def route: RequestRoute = Routes.getChannelInvites(channelId)
  override def paramsEncoder: Encoder[CreateChannelInviteData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[Invite] = Decoder[Invite]

  override def requiredPermissions: Permission = Permission.CreateInstantInvite
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): CreateChannelInvite = copy(reason = Some(reason))
}
object CreateChannelInvite {
  def mk(
      channelId: ChannelId,
      maxAge: Int = 86400,
      maxUses: Int = 0,
      temporary: Boolean = false,
      unique: Boolean = false,
      targetUser: Option[UserId] = None,
      targetUserType: Option[Int] = None
  ): CreateChannelInvite =
    new CreateChannelInvite(
      channelId,
      CreateChannelInviteData(maxAge, maxUses, temporary, unique, targetUser, targetUserType)
    )
}

/**
  * Triggers a typing indicator in a channel.
  */
case class TriggerTypingIndicator(channelId: ChannelId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.triggerTyping(channelId)
}

/**
  * Get all the pinned messages in a channel.
  */
case class GetPinnedMessages(channelId: ChannelId) extends NoParamsRequest[Seq[RawMessage], Seq[Message]] {
  override def route: RequestRoute = Routes.getPinnedMessage(channelId)

  override def responseDecoder: Decoder[Seq[RawMessage]]               = Decoder[Seq[RawMessage]]
  override def toNiceResponse(response: Seq[RawMessage]): Seq[Message] = response.map(_.toMessage)
}

/**
  * Add a new pinned message to a channel.
  */
case class AddPinnedChannelMessages(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.addPinnedChannelMessage(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * Delete a pinned message in a channel.
  */
case class DeletePinnedChannelMessages(channelId: ChannelId, messageId: MessageId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deletePinnedChannelMessage(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/*
case class GroupDMAddRecipientData(accessToken: String, nick: String)
case class GroupDMAddRecipient(channelId:       Snowflake, userId: Snowflake, params: GroupDMAddRecipientData)
    extends RESTRequest[GroupDMAddRecipientData] {
  override def route:         RestRoute                        = Routes.groupDmAddRecipient(userId, channelId)
  override def paramsEncoder: Encoder[GroupDMAddRecipientData] = derivation.deriveEncoder(derivation.renaming.snakeCase, None)
}

case class GroupDMRemoveRecipient(channelId: Snowflake, userId: Snowflake) extends NoParamsRequest {
  override def route: RestRoute = Routes.groupDmRemoveRecipient(userId, channelId)
}
 */

/**
  * Get all the emojis for this guild.
  */
case class ListGuildEmojis(guildId: GuildId) extends RESTRequest[NotUsed, Seq[RawEmoji], Seq[Emoji]] {
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
case class CreateGuildEmoji(
    guildId: GuildId,
    params: CreateGuildEmojiData,
    reason: Option[String] = None
) extends ReasonRequest[CreateGuildEmoji, CreateGuildEmojiData, RawEmoji, Emoji] {
  override def route: RequestRoute = Routes.createGuildEmoji(guildId)
  override def paramsEncoder: Encoder[CreateGuildEmojiData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[RawEmoji]        = Decoder[RawEmoji]
  override def toNiceResponse(response: RawEmoji): Emoji = response.toEmoji

  override def requiredPermissions: Permission = Permission.ManageEmojis
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): CreateGuildEmoji = copy(reason = Some(reason))
}
object CreateGuildEmoji {
  def mk(
      guildId: GuildId,
      name: String,
      image: ImageData,
      roles: Seq[RoleId]
  ): CreateGuildEmoji = new CreateGuildEmoji(guildId, CreateGuildEmojiData(name, image, roles))
}

/**
  * Get an emoji in a guild by id.
  */
case class GetGuildEmoji(emojiId: EmojiId, guildId: GuildId) extends NoParamsRequest[RawEmoji, Emoji] {
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
case class ModifyGuildEmoji(
    emojiId: EmojiId,
    guildId: GuildId,
    params: ModifyGuildEmojiData,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildEmoji, ModifyGuildEmojiData, RawEmoji, Emoji] {
  override def route: RequestRoute = Routes.modifyGuildEmoji(guildId, emojiId)
  override def paramsEncoder: Encoder[ModifyGuildEmojiData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[RawEmoji]        = Decoder[RawEmoji]
  override def toNiceResponse(response: RawEmoji): Emoji = response.toEmoji

  override def requiredPermissions: Permission = Permission.ManageEmojis
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): ModifyGuildEmoji = copy(reason = Some(reason))
}
object ModifyGuildEmoji {
  def mk(
      emojiId: EmojiId,
      guildId: GuildId,
      name: String,
      roles: Seq[RoleId]
  ): ModifyGuildEmoji = new ModifyGuildEmoji(emojiId, guildId, ModifyGuildEmojiData(name, roles))
}

/**
  * Delete an emoji from a guild.
  */
case class DeleteGuildEmoji(
    emojiId: EmojiId,
    guildId: GuildId,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteGuildEmoji] {
  override def route: RequestRoute = Routes.deleteGuildEmoji(guildId, emojiId)

  override def requiredPermissions: Permission = Permission.ManageEmojis
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): DeleteGuildEmoji = copy(reason = Some(reason))
}
