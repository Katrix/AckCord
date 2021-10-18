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
import java.time.OffsetDateTime

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
import io.circe._
import io.circe.syntax._

/** Get a channel by id. */
case class GetChannel(channelId: ChannelId) extends NoParamsRequest[RawChannel, Option[Channel]] {
  override def route: RequestRoute = Routes.getChannel(channelId)

  override def responseDecoder: Decoder[RawChannel]                  = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[Channel] = response.toChannel(None)

  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * @param name
  *   New name of the channel.
  * @param position
  *   New position of the channel.
  * @param topic
  *   The new channel topic for text channels.
  * @param nsfw
  *   If the channel is NSFW for text channels.
  * @param bitrate
  *   The new channel bitrate for voice channels.
  * @param userLimit
  *   The new user limit for voice channel.
  * @param rateLimitPerUser
  *   The new user ratelimit for guild text channels.
  * @param permissionOverwrites
  *   The new channel permission overwrites.
  * @param parentId
  *   The new category id of the channel.
  * @param rtcRegion
  *   The voice region to use. Automatic if None.
  */
case class ModifyChannelData(
    name: JsonOption[String] = JsonUndefined,
    icon: JsonOption[ImageData] = JsonUndefined,
    tpe: JsonOption[ChannelType] = JsonUndefined,
    position: JsonOption[Int] = JsonUndefined,
    topic: JsonOption[String] = JsonUndefined,
    nsfw: JsonOption[Boolean] = JsonUndefined,
    rateLimitPerUser: JsonOption[Int] = JsonUndefined,
    bitrate: JsonOption[Int] = JsonUndefined,
    userLimit: JsonOption[Int] = JsonUndefined,
    permissionOverwrites: JsonOption[Seq[PermissionOverwrite]] = JsonUndefined,
    parentId: JsonOption[SnowflakeType[GuildCategory]] = JsonUndefined,
    rtcRegion: JsonOption[String] = JsonUndefined,
    videoQualityMode: JsonOption[VideoQualityMode] = JsonUndefined,
    archived: JsonOption[Boolean] = JsonUndefined,
    locked: JsonOption[Boolean] = JsonUndefined,
    autoArchiveDuration: JsonOption[Int] = JsonUndefined
) {
  require(name.forall(_.length <= 100), "Name must be between 2 and 100 characters")
  require(topic.forall(_.length <= 100), "Topic must be between 0 and 1024 characters")
  require(bitrate.forall(b => b >= 8000 && b <= 128000), "Bitrate must be between 8000 and 128000 bits")
  require(userLimit.forall(b => b >= 0 && b <= 99), "User limit must be between 0 and 99 users")
  require(rateLimitPerUser.forall(i => i >= 0 && i <= 21600), "Rate limit per user must be between 0 ad 21600")
  require(tpe.forall(Seq(ChannelType.GuildNews, ChannelType.GuildText).contains))
  require(
    autoArchiveDuration.forall(Seq(60, 1440, 4320, 10080).contains),
    "Auto archive duration can only be 60, 1440, 4320 or 10080"
  )
}
object ModifyChannelData {
  implicit val encoder: Encoder[ModifyChannelData] = (a: ModifyChannelData) => {
    JsonOption.removeUndefinedToObj(
      "name"                  -> a.name.toJson,
      "icon"                  -> a.icon.toJson,
      "type"                  -> a.tpe.toJson,
      "position"              -> a.position.toJson,
      "topic"                 -> a.topic.toJson,
      "nsfw"                  -> a.nsfw.toJson,
      "rate_limit_per_user"   -> a.rateLimitPerUser.toJson,
      "bitrate"               -> a.bitrate.toJson,
      "user_limit"            -> a.userLimit.toJson,
      "permission_overwrites" -> a.permissionOverwrites.toJson,
      "parent_id"             -> a.parentId.toJson,
      "rtc_region"            -> a.rtcRegion.toJson,
      "video_quality_mode"    -> a.videoQualityMode.toJson,
      "archived"              -> a.archived.toJson,
      "locked"                -> a.locked.toJson,
      "auto_archive_duration" -> a.autoArchiveDuration.toJson
    )
  }
}

/**
  * Update the settings of a channel.
  * @param channelId
  *   The channel to update.
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
  override def toNiceResponse(response: RawChannel): Option[Channel] = response.toChannel(None)

  override def withReason(reason: String): ModifyChannel = copy(reason = Some(reason))
}

/** Delete a guild channel, or close a DM channel. */
case class DeleteCloseChannel(channelId: ChannelId, reason: Option[String] = None)
    extends NoParamsReasonRequest[DeleteCloseChannel, RawChannel, Option[Channel]] {
  override def route: RequestRoute = Routes.deleteCloseChannel(channelId)

  override def responseDecoder: Decoder[RawChannel]                  = Decoder[RawChannel]
  override def toNiceResponse(response: RawChannel): Option[Channel] = response.toChannel(None)

  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, Permission.ManageChannels) ||
      hasPermissionsChannel(channelId, Permission.ManageThreads)

  override def withReason(reason: String): DeleteCloseChannel = copy(reason = Some(reason))
}

/**
  * @param around
  *   Get messages around this message id.
  * @param before
  *   Get messages before this message id.
  * @param after
  *   Get messages after this message id.
  * @param limit
  *   The max amount of messages to return. The default is 50.
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

/** Get the messages in a channel. */
case class GetChannelMessages(channelId: TextChannelId, query: GetChannelMessagesData)
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
  def around(channelId: TextChannelId, around: MessageId, limit: Option[Int] = None) =
    new GetChannelMessages(channelId, GetChannelMessagesData(around = Some(around), limit = limit))

  def before(channelId: TextChannelId, before: MessageId, limit: Option[Int] = None) =
    new GetChannelMessages(channelId, GetChannelMessagesData(before = Some(before), limit = limit))

  def after(channelId: TextChannelId, after: MessageId, limit: Option[Int] = None) =
    new GetChannelMessages(channelId, GetChannelMessagesData(after = Some(after), limit = limit))
}

/** Get a specific message in a channel. */
case class GetChannelMessage(channelId: TextChannelId, messageId: MessageId)
    extends NoParamsRequest[RawMessage, Message] {
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
  protected[requests] def toBodyPart: FormData.BodyPart =
    FormData.BodyPart(fileName, toBodyPartEntity, Map("filename" -> fileName))
  protected[requests] def toBodyPartEntity: BodyPartEntity
}
object CreateMessageFile {
  case class FromPath(path: Path) extends CreateMessageFile {
    override def fileName: String = path.getFileName.toString
    override def isValid: Boolean = Files.isRegularFile(path)
    override protected[requests] def toBodyPartEntity: BodyPartEntity =
      HttpEntity.fromPath(ContentTypes.`application/octet-stream`, path)
  }

  case class SourceFile(
      contentType: ContentType,
      contentLength: Int,
      bytes: Source[ByteString, NotUsed],
      fileName: String
  ) extends CreateMessageFile {
    override def isValid: Boolean = true

    override protected[requests] def toBodyPartEntity: BodyPartEntity =
      HttpEntity(contentType, contentLength, bytes)
  }

  case class ByteFile(
      contentType: ContentType,
      bytes: ByteString,
      fileName: String
  ) extends CreateMessageFile {
    override def isValid: Boolean = true

    override protected[requests] def toBodyPartEntity: BodyPartEntity =
      HttpEntity(ContentTypes.`application/octet-stream`, bytes)
  }

  case class StringFile(
      contentType: ContentType.NonBinary,
      contents: String,
      fileName: String
  ) extends CreateMessageFile {
    override def isValid: Boolean = true

    override protected[requests] def toBodyPartEntity: BodyPartEntity =
      HttpEntity(ContentTypes.`text/plain(UTF-8)`, contents)
  }

}

/**
  * @param content
  *   The content of the message.
  * @param nonce
  *   A nonce used for optimistic message sending (up to 25 characters).
  * @param tts
  *   If this is a text-to-speech message.
  * @param files
  *   The files to send with this message. You can reference these files in the
  *   embed using `attachment://filename`.
  * @param embeds
  *   Embeds to send with this message.
  * @param replyTo
  *   The message to reply to
  * @param replyFailIfNotExist
  *   Fail the reply if the message to reply to does not exist.
  */
case class CreateMessageData(
    content: String = "",
    nonce: Option[Either[String, RawSnowflake]] = None,
    tts: Boolean = false,
    files: Seq[CreateMessageFile] = Seq.empty,
    embeds: Seq[OutgoingEmbed] = Seq.empty,
    allowedMentions: AllowedMention = AllowedMention.all,
    replyTo: Option[MessageId] = None,
    replyFailIfNotExist: Boolean = true,
    components: Seq[ActionRow] = Nil,
    stickerIds: Seq[StickerId] = Nil
) {
  files.foreach(file => require(file.isValid))
  require(
    files.map(_.fileName).distinct.lengthCompare(files.length) == 0,
    "Please use unique filenames for all files"
  )
  require(content.length <= 4000, "The content of a message can't exceed 4000 characters")
  require(embeds.size <= 10, "Can't send more than 10 embeds with a webhook message")
  require(components.length <= 5, "Can't have more than 5 action rows on a message")
  require(nonce.forall(_.swap.forall(_.length <= 25)), "Nonce too long")
  require(stickerIds.length <= 3, "Too many stickers")
}
object CreateMessageData {

  //We handle this here as the file argument needs special treatment
  implicit val encoder: Encoder[CreateMessageData] = (a: CreateMessageData) => {
    val base = Json.obj(
      "content"          := a.content,
      "nonce"            := a.nonce.map(_.fold(_.asJson, _.asJson)),
      "tts"              := a.tts,
      "embeds"           := a.embeds,
      "allowed_mentions" := a.allowedMentions,
      "components"       := a.components,
      "sticker_ids"      := a.stickerIds
    )

    a.replyTo.fold(base) { reply =>
      base.withObject(o =>
        Json.fromJsonObject(
          o.add("message_reference", Json.obj("message_id" := reply, "fail_if_not_exist" := a.replyFailIfNotExist))
        )
      )
    }
  }
}

/** Create a message in a channel. */
case class CreateMessage(channelId: TextChannelId, params: CreateMessageData)
    extends RESTRequest[CreateMessageData, RawMessage, Message] {
  override def route: RequestRoute                       = Routes.createMessage(channelId)
  override def paramsEncoder: Encoder[CreateMessageData] = CreateMessageData.encoder
  override def requestBody: RequestEntity = {
    if (params.files.nonEmpty) {
      val jsonPart = FormData.BodyPart(
        "payload_json",
        HttpEntity(ContentTypes.`application/json`, jsonParams.printWith(jsonPrinter))
      )

      FormData(params.files.map(_.toBodyPart) :+ jsonPart: _*).toEntity()
    } else {
      super.requestBody
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
  def mkContent(channelId: TextChannelId, content: String): CreateMessage =
    new CreateMessage(channelId, CreateMessageData(content))

  def mkEmbed(channelId: TextChannelId, embed: OutgoingEmbed): CreateMessage =
    new CreateMessage(channelId, CreateMessageData(embeds = Seq(embed)))
}

/**
  * Create a reaction for a message.
  * @param emoji
  *   The emoji to send.
  */
case class CreateReaction(
    channelId: TextChannelId,
    messageId: MessageId,
    emoji: String
) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.createReaction(channelId, messageId, emoji)

  override def requiredPermissions: Permission = Permission.ReadMessageHistory
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/** Delete the clients reaction to a message. */
case class DeleteOwnReaction(
    channelId: TextChannelId,
    messageId: MessageId,
    emoji: String
) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteOwnReaction(channelId, messageId, emoji)
}

/** Delete the reaction of another user to a message. */
case class DeleteUserReaction(
    channelId: TextChannelId,
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
  * @param after
  *   Get users after this user.
  * @param limit
  *   The max amount of users to return. Defaults to 25.
  */
case class GetReactionsData(after: Option[UserId] = None, limit: Option[Int] = None)

/** Get all the users that have reacted with an emoji for a message. */
case class GetReactions(
    channelId: TextChannelId,
    messageId: MessageId,
    emoji: String,
    queryParams: GetReactionsData
) extends NoParamsNiceResponseRequest[Seq[User]] {
  override def route: RequestRoute =
    Routes.getReactions(channelId, messageId, emoji, queryParams.after, queryParams.limit)

  override def responseDecoder: Decoder[Seq[User]] = Decoder[Seq[User]]
}
object GetReactions {

  def after(
      channelId: TextChannelId,
      messageId: MessageId,
      emoji: String,
      after: UserId,
      limit: Option[Int] = None
  ): GetReactions =
    new GetReactions(channelId, messageId, emoji, GetReactionsData(after = Some(after), limit = limit))
}

/** Clear all reactions from a message. */
case class DeleteAllReactions(channelId: TextChannelId, messageId: MessageId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteAllReactions(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/** Clear all reactions for a single emoji from a message. */
case class DeleteAllReactionsForEmoji(channelId: TextChannelId, messageId: MessageId, emoji: String)
    extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteAllReactionsForEmoji(channelId, messageId, emoji)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * @param content
  *   The content of the new message
  * @param embed
  *   The embed of the new message
  * @param attachments
  *   The attachments to keep in the new message.
  */
case class EditMessageData(
    content: JsonOption[String] = JsonUndefined,
    files: Seq[CreateMessageFile] = Seq.empty,
    allowedMentions: JsonOption[AllowedMention] = JsonUndefined,
    embeds: JsonOption[Seq[OutgoingEmbed]] = JsonUndefined,
    flags: JsonOption[MessageFlags] = JsonUndefined,
    components: JsonOption[Seq[ActionRow]] = JsonUndefined,
    attachments: JsonOption[Seq[Attachment]] = JsonUndefined
) {
  require(content.forall(_.length < 2000))
  require(embeds.forall(_.size <= 10), "Can't send more than 10 embeds with a webhook message")
  require(components.forall(_.length <= 5), "Can't have more than 5 action rows on a message")
}
object EditMessageData {
  implicit val encoder: Encoder[EditMessageData] = (a: EditMessageData) =>
    JsonOption.removeUndefinedToObj(
      "content"          -> a.content.toJson,
      "allowed_mentions" -> a.allowedMentions.toJson,
      "embeds"           -> a.embeds.toJson,
      "flags"            -> a.flags.toJson,
      "components"       -> a.components.toJson,
      "attachments"      -> a.attachments.toJson
    )
}

/** Edit an existing message */
case class EditMessage(
    channelId: TextChannelId,
    messageId: MessageId,
    params: EditMessageData
) extends RESTRequest[EditMessageData, RawMessage, Message] {
  override def route: RequestRoute                     = Routes.editMessage(channelId, messageId)
  override def paramsEncoder: Encoder[EditMessageData] = EditMessageData.encoder
  override def jsonPrinter: Printer                    = Printer.noSpaces
  override def requestBody: RequestEntity = {
    if (params.files.nonEmpty) {
      val jsonPart = FormData.BodyPart(
        "payload_json",
        HttpEntity(ContentTypes.`application/json`, jsonParams.printWith(jsonPrinter))
      )

      FormData(params.files.map(_.toBodyPart) :+ jsonPart: _*).toEntity()
    } else {
      super.requestBody
    }
  }

  override def responseDecoder: Decoder[RawMessage]          = Decoder[RawMessage]
  override def toNiceResponse(response: RawMessage): Message = response.toMessage
}
object EditMessage {
  def mkContent(
      channelId: TextChannelId,
      messageId: MessageId,
      content: String
  ): EditMessage = new EditMessage(channelId, messageId, EditMessageData(JsonSome(content)))

  def mkEmbed(
      channelId: TextChannelId,
      messageId: MessageId,
      embed: OutgoingEmbed
  ): EditMessage = new EditMessage(channelId, messageId, EditMessageData(embeds = JsonSome(Seq(embed))))

  def suppressEmbeds(
      channelId: TextChannelId,
      messageId: MessageId,
      existingFlags: MessageFlags
  ) =
    new EditMessage(
      channelId,
      messageId,
      EditMessageData(flags = JsonSome(existingFlags -- MessageFlags.SuppressEmbeds))
    )
}

/** Delete a message */
case class DeleteMessage(
    channelId: TextChannelId,
    messageId: MessageId,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteMessage] {
  override def route: RequestRoute = Routes.deleteMessage(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): DeleteMessage = copy(reason = Some(reason))
}

/** @param messages All the messages to delete. */
case class BulkDeleteMessagesData(messages: Seq[MessageId]) {
  require(
    messages.lengthCompare(2) >= 0 && messages.lengthCompare(100) <= 0,
    "Can only delete between 2 and 100 messages at a time"
  )
}

/**
  * Delete multiple messages in a single request. Can only be used on guild
  * channels.
  */
case class BulkDeleteMessages(
    channelId: TextChannelId,
    params: BulkDeleteMessagesData,
    reason: Option[String] = None
) extends NoResponseReasonRequest[BulkDeleteMessages, BulkDeleteMessagesData] {
  override def route: RequestRoute = Routes.bulkDeleteMessages(channelId)
  override def paramsEncoder: Encoder[BulkDeleteMessagesData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): BulkDeleteMessages = copy(reason = Some(reason))
}
object BulkDeleteMessages {
  def mk(
      channelId: TextChannelId,
      messages: Seq[MessageId]
  ): BulkDeleteMessages = new BulkDeleteMessages(channelId, BulkDeleteMessagesData(messages))
}

/**
  * @param allow
  *   The permissions to allow.
  * @param deny
  *   The permissions to deny.
  * @param `type`
  *   If this is a user or role overwrite.
  */
case class EditChannelPermissionsData(allow: Permission, deny: Permission, `type`: PermissionOverwriteType)

/** Edit a permission overwrite for a channel. */
case class EditChannelPermissions(
    channelId: GuildChannelId,
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
      channelId: GuildChannelId,
      overwriteId: UserOrRoleId,
      allow: Permission,
      deny: Permission,
      tpe: PermissionOverwriteType
  ): EditChannelPermissions =
    new EditChannelPermissions(channelId, overwriteId, EditChannelPermissionsData(allow, deny, tpe))
}

/** Delete a permission overwrite for a channel. */
case class DeleteChannelPermission(
    channelId: GuildChannelId,
    overwriteId: UserOrRoleId,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteChannelPermission] {
  override def route: RequestRoute = Routes.deleteChannelPermissions(channelId, overwriteId)

  override def requiredPermissions: Permission = Permission.ManageRoles

  override def withReason(reason: String): DeleteChannelPermission = copy(reason = Some(reason))
}

/** Get all invites for this channel. Can only be used on guild channels. */
case class GetChannelInvites(channelId: GuildChannelId) extends NoParamsNiceResponseRequest[Seq[InviteWithMetadata]] {
  override def route: RequestRoute = Routes.getChannelInvites(channelId)

  override def responseDecoder: Decoder[Seq[InviteWithMetadata]] = Decoder[Seq[InviteWithMetadata]]

  override def requiredPermissions: Permission = Permission.ManageChannels
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * @param maxAge
  *   Duration in seconds before this invite expires.
  * @param maxUses
  *   Amount of times this invite can be used before expiring, or 0 for
  *   unlimited.
  * @param temporary
  *   If this invite only grants temporary membership.
  * @param unique
  *   If true, guarantees to create a new invite.
  * @param targetUserId
  *   The target user for this invite, who's stream will be displayed.
  * @param targetType
  *   The type of target for this voice channel invite
  */
case class CreateChannelInviteData(
    maxAge: JsonOption[Int] = JsonUndefined,
    maxUses: JsonOption[Int] = JsonUndefined,
    temporary: JsonOption[Boolean] = JsonUndefined,
    unique: JsonOption[Boolean] = JsonUndefined,
    targetUserId: JsonOption[UserId] = JsonUndefined,
    targetType: JsonOption[InviteTargetType] = JsonUndefined, //TODO: What is the type here
    targetApplicationId: JsonOption[ApplicationId] = JsonUndefined
)
object CreateChannelInviteData {
  implicit val encoder: Encoder[CreateChannelInviteData] = (a: CreateChannelInviteData) =>
    JsonOption.removeUndefinedToObj(
      "max_age"               -> a.maxAge.toJson,
      "max_uses"              -> a.maxUses.toJson,
      "temporary"             -> a.temporary.toJson,
      "unique"                -> a.unique.toJson,
      "target_user_id"        -> a.targetUserId.toJson,
      "target_type"           -> a.targetType.toJson,
      "target_application_id" -> a.targetApplicationId.toJson
    )
}

/** Create a new invite for a channel. Can only be used on guild channels. */
case class CreateChannelInvite(
    channelId: GuildChannelId,
    params: CreateChannelInviteData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[CreateChannelInvite, CreateChannelInviteData, Invite] {
  override def route: RequestRoute                             = Routes.getChannelInvites(channelId)
  override def paramsEncoder: Encoder[CreateChannelInviteData] = CreateChannelInviteData.encoder

  override def responseDecoder: Decoder[Invite] = Decoder[Invite]

  override def requiredPermissions: Permission = Permission.CreateInstantInvite
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): CreateChannelInvite = copy(reason = Some(reason))
}
object CreateChannelInvite {
  def mk(
      channelId: GuildChannelId,
      maxAge: Int = 86400,
      maxUses: Int = 0,
      temporary: Boolean = false,
      unique: Boolean = false
  ): CreateChannelInvite =
    new CreateChannelInvite(
      channelId,
      CreateChannelInviteData(JsonSome(maxAge), JsonSome(maxUses), JsonSome(temporary), JsonSome(unique))
    )
}

/** Triggers a typing indicator in a channel. */
case class TriggerTypingIndicator(channelId: TextChannelId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.triggerTyping(channelId)
}

/** Get all the pinned messages in a channel. */
case class GetPinnedMessages(channelId: TextChannelId) extends NoParamsRequest[Seq[RawMessage], Seq[Message]] {
  override def route: RequestRoute = Routes.getPinnedMessage(channelId)

  override def responseDecoder: Decoder[Seq[RawMessage]]               = Decoder[Seq[RawMessage]]
  override def toNiceResponse(response: Seq[RawMessage]): Seq[Message] = response.map(_.toMessage)
}

/** Pin a message in a channel. */
case class PinMessage(channelId: TextChannelId, messageId: MessageId, reason: Option[String] = None)
    extends NoParamsResponseReasonRequest[PinMessage] {
  override def route: RequestRoute = Routes.addPinnedChannelMessage(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): PinMessage = copy(reason = Some(reason))
}

/** Unpin a message in a channel.. */
case class UnpinMessage(channelId: TextChannelId, messageId: MessageId, reason: Option[String] = None)
    extends NoParamsResponseReasonRequest[UnpinMessage] {
  override def route: RequestRoute = Routes.deletePinnedChannelMessage(channelId, messageId)

  override def requiredPermissions: Permission = Permission.ManageMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)

  override def withReason(reason: String): UnpinMessage = copy(reason = Some(reason))
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

case class StartThreadWithMessageData(name: String, autoArchiveDuration: Int) {
  require(
    Seq(60, 1440, 4320, 10080).contains(autoArchiveDuration),
    "Auto archive duration can only be 60, 1440, 4320 or 10080"
  )
}

/** Create a new public thread from an existing message. */
case class StartThreadWithMessage(
    channelId: TextGuildChannelId,
    messageId: MessageId,
    params: StartThreadWithMessageData,
    reason: Option[String] = None
) extends ReasonRequest[StartThreadWithMessage, StartThreadWithMessageData, RawChannel, Option[ThreadGuildChannel]] {
  override def route: RequestRoute = Routes.startThreadWithMessage(channelId, messageId)

  override def paramsEncoder: Encoder[StartThreadWithMessageData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)
  override def responseDecoder: Decoder[RawChannel] = Decoder[RawChannel]

  override def toNiceResponse(response: RawChannel): Option[ThreadGuildChannel] =
    response.toChannel(None).collect { case thread: ThreadGuildChannel => thread }

  override def withReason(reason: String): StartThreadWithMessage = copy(reason = Some(reason))
}

case class StartThreadWithoutMessageData(
    name: String,
    autoArchiveDuration: Int,
    `type`: ChannelType.ThreadChannelType
) {
  require(
    Seq(60, 1440, 4320, 10080).contains(autoArchiveDuration),
    "Auto archive duration can only be 60, 1440, 4320 or 10080"
  )
}

/** Create a new public thread from an existing message. */
case class StartThreadWithoutMessage(
    channelId: TextGuildChannelId,
    params: StartThreadWithoutMessageData,
    reason: Option[String] = None
) extends ReasonRequest[StartThreadWithoutMessage, StartThreadWithoutMessageData, RawChannel, Option[
      ThreadGuildChannel
    ]] {
  override def route: RequestRoute = Routes.startThreadWithoutMessage(channelId)

  override def paramsEncoder: Encoder[StartThreadWithoutMessageData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)
  override def responseDecoder: Decoder[RawChannel] = Decoder[RawChannel]

  override def toNiceResponse(response: RawChannel): Option[ThreadGuildChannel] =
    response.toChannel(None).collect { case thread: ThreadGuildChannel => thread }

  override def withReason(reason: String): StartThreadWithoutMessage = copy(reason = Some(reason))
}

/** Adds the current user to a thread. */
case class JoinThread(channelId: ThreadGuildChannelId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.joinThread(channelId)
}

/** Adds the specified user to a thread. */
case class AddThreadMember(channelId: ThreadGuildChannelId, userId: UserId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.addThreadMember(channelId, userId)
}

/** Makes the current user leave a thread. */
case class LeaveThread(channelId: ThreadGuildChannelId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.leaveThread(channelId)
}

/** Removes the specified user from a thread. */
case class RemoveThreadMember(channelId: ThreadGuildChannelId, userId: UserId) extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.removeThreadMember(channelId, userId)
}

/**
  * Gets all the members of a thread. Requires the privileged `GUILD_MEMBERS`
  * intent.
  */
case class ListThreadMembers(channelId: ThreadGuildChannelId)
    extends NoParamsRequest[Seq[RawThreadMember], Seq[ThreadMember]] {
  override def route: RequestRoute = Routes.listThreadMembers(channelId)

  override def responseDecoder: Decoder[Seq[RawThreadMember]] = Decoder[Seq[RawThreadMember]]

  override def toNiceResponse(response: Seq[RawThreadMember]): Seq[ThreadMember] =
    //Safe
    response.map(raw => ThreadMember(raw.id.get, raw.userId.get, raw.joinTimestamp, raw.flags))
}

case class GetThreadsResponse(
    threads: Seq[RawChannel],
    members: Seq[RawThreadMember],
    hasMore: Boolean
)
object GetThreadsResponse {
  implicit val decoder: Decoder[GetThreadsResponse] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)
}

/**
  * Lists all the active threads in a channel. Threads are ordered in descending
  * order by their id.
  */
@deprecated("Deprecated by Discord", "0.18.0")
case class ListActiveChannelThreads(channelId: TextGuildChannelId)
    extends NoParamsNiceResponseRequest[GetThreadsResponse] {
  override def route: RequestRoute = Routes.listActiveChannelThreads(channelId)

  override def responseDecoder: Decoder[GetThreadsResponse] = GetThreadsResponse.decoder
}

/**
  * Lists all the public archived threads in a channel. Threads are ordered in
  * descending order by [[RawThreadMetadata.archiveTimestamp]].
  */
case class ListPublicArchivedThreads(channelId: TextGuildChannelId, before: Option[OffsetDateTime], limit: Option[Int])
    extends NoParamsNiceResponseRequest[GetThreadsResponse] {
  override def route: RequestRoute = Routes.listPublicArchivedThreads(channelId, before, limit)

  override def responseDecoder: Decoder[GetThreadsResponse] = GetThreadsResponse.decoder

  override def requiredPermissions: Permission = Permission.ReadMessageHistory
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * Lists all the private archived threads in a channel. Threads are ordered in
  * descending order by [[RawThreadMetadata.archiveTimestamp]].
  */
case class ListPrivateArchivedThreads(channelId: TextGuildChannelId, before: Option[OffsetDateTime], limit: Option[Int])
    extends NoParamsNiceResponseRequest[GetThreadsResponse] {
  override def route: RequestRoute = Routes.listPrivateArchivedThreads(channelId, before, limit)

  override def responseDecoder: Decoder[GetThreadsResponse] = GetThreadsResponse.decoder

  override def requiredPermissions: Permission = Permission.ReadMessageHistory ++ Permission.ManageThreads
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * Lists all the joined private archived threads in a channel. Threads are
  * ordered in descending order by [[RawThreadMetadata.archiveTimestamp]].
  */
case class ListJoinedPrivateArchivedThreads(
    channelId: TextGuildChannelId,
    before: Option[OffsetDateTime],
    limit: Option[Int]
) extends NoParamsNiceResponseRequest[GetThreadsResponse] {
  override def route: RequestRoute = Routes.listJoinedPrivateArchivedThreads(channelId, before, limit)

  override def responseDecoder: Decoder[GetThreadsResponse] = GetThreadsResponse.decoder

  override def requiredPermissions: Permission = Permission.ReadMessageHistory
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/** Get all the emojis for this guild. */
case class ListGuildEmojis(guildId: GuildId) extends RESTRequest[NotUsed, Seq[RawEmoji], Seq[Emoji]] {
  override def route: RequestRoute             = Routes.listGuildEmojis(guildId)
  override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
  override def params: NotUsed                 = NotUsed

  override def responseDecoder: Decoder[Seq[RawEmoji]]             = Decoder[Seq[RawEmoji]]
  override def toNiceResponse(response: Seq[RawEmoji]): Seq[Emoji] = response.map(_.toEmoji)
}

/**
  * @param name
  *   The name of the emoji.
  * @param image
  *   The image data for the emoji.
  * @param roles
  *   Whitelist of roles that can use this emoji.
  */
case class CreateGuildEmojiData(name: String, image: ImageData, roles: Seq[RoleId])

/** Create a new emoji for a guild. */
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

  override def requiredPermissions: Permission = Permission.ManageEmojisAndStickers
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

/** Get an emoji in a guild by id. */
case class GetGuildEmoji(emojiId: EmojiId, guildId: GuildId) extends NoParamsRequest[RawEmoji, Emoji] {
  override def route: RequestRoute = Routes.getGuildEmoji(guildId, emojiId)

  override def responseDecoder: Decoder[RawEmoji]        = Decoder[RawEmoji]
  override def toNiceResponse(response: RawEmoji): Emoji = response.toEmoji
}

/**
  * @param name
  *   The new emoji name.
  * @param roles
  *   Whitelist of roles that can use this emoji.
  */
case class ModifyGuildEmojiData(name: String, roles: JsonOption[Seq[RoleId]] = JsonUndefined)
object ModifyGuildEmojiData {
  implicit val encoder: Encoder[ModifyGuildEmojiData] = (a: ModifyGuildEmojiData) =>
    JsonOption.removeUndefinedToObj(
      "name"  -> JsonSome(a.name.asJson),
      "roles" -> a.roles.toJson
    )
}

/** Modify an existing emoji. */
case class ModifyGuildEmoji(
    emojiId: EmojiId,
    guildId: GuildId,
    params: ModifyGuildEmojiData,
    reason: Option[String] = None
) extends ReasonRequest[ModifyGuildEmoji, ModifyGuildEmojiData, RawEmoji, Emoji] {
  override def route: RequestRoute                          = Routes.modifyGuildEmoji(guildId, emojiId)
  override def paramsEncoder: Encoder[ModifyGuildEmojiData] = ModifyGuildEmojiData.encoder

  override def responseDecoder: Decoder[RawEmoji]        = Decoder[RawEmoji]
  override def toNiceResponse(response: RawEmoji): Emoji = response.toEmoji

  override def requiredPermissions: Permission = Permission.ManageEmojisAndStickers
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
  ): ModifyGuildEmoji = new ModifyGuildEmoji(emojiId, guildId, ModifyGuildEmojiData(name, JsonSome(roles)))
}

/** Delete an emoji from a guild. */
case class DeleteGuildEmoji(
    emojiId: EmojiId,
    guildId: GuildId,
    reason: Option[String] = None
) extends NoParamsResponseReasonRequest[DeleteGuildEmoji] {
  override def route: RequestRoute = Routes.deleteGuildEmoji(guildId, emojiId)

  override def requiredPermissions: Permission = Permission.ManageEmojisAndStickers
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsGuild(guildId, requiredPermissions)

  override def withReason(reason: String): DeleteGuildEmoji = copy(reason = Some(reason))
}

case class CrosspostMessage(channelId: ChannelId, messageId: MessageId) extends NoParamsRequest[RawMessage, Message] {
  override def route: RequestRoute = Routes.crosspostMessage(channelId, messageId)

  override def responseDecoder: Decoder[RawMessage]          = Decoder[RawMessage]
  override def toNiceResponse(response: RawMessage): Message = response.toMessage

  override def requiredPermissions: Permission = Permission.SendMessages
  override def hasPermissions(implicit c: CacheSnapshot): Boolean =
    hasPermissionsChannel(channelId, requiredPermissions)
}

/**
  * @param channelId
  *   Source channel id
  * @param webhookId
  *   Id of the created webhook
  */
case class FollowedChannel(
    channelId: ChannelId,
    webhookId: SnowflakeType[Webhook]
)

case class FollowNewsChannelData(webhookChannelId: ChannelId)

/** @param channelId Where to send messages to */
case class FollowNewsChannel(channelId: ChannelId, params: FollowNewsChannelData)
    extends RESTRequest[FollowNewsChannelData, FollowedChannel, FollowedChannel] {
  override def route: RequestRoute = Routes.followNewsChannel(channelId)

  override def paramsEncoder: Encoder[FollowNewsChannelData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  override def responseDecoder: Decoder[FollowedChannel] =
    derivation.deriveDecoder(derivation.renaming.snakeCase, false, None)
  override def toNiceResponse(response: FollowedChannel): FollowedChannel =
    response
}
