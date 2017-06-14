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
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import io.circe._
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import net.katsstuff.akkacord.data.{OutgoingEmbed, Permission, Snowflake, User}
import net.katsstuff.akkacord.handlers.{CacheHandler, CacheUpdateHandler, Handlers, NOOPHandler, RawHandlers}
import net.katsstuff.akkacord.http.{RawChannel, RawGuildChannel, RawMessage, Routes}

sealed trait RESTRequest[Params, Response] {
  def route: RestRoute

  def params:        Params
  def paramsEncoder: Encoder[Params]
  def toJsonParams: Json = paramsEncoder(params)

  def responseDecoder: Decoder[Response]
  def handleResponse:  CacheHandler[Response]
  def expectedResponseCode: StatusCode = StatusCodes.OK
}
object RESTRequest {
  import net.katsstuff.akkacord.http.DiscordProtocol._
  trait NoParamsRequest[Response] extends RESTRequest[NotUsed, Response] {
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed
  }

  trait NoResponseRequest[Params] extends RESTRequest[Params, NotUsed] {
    override def responseDecoder: Decoder[NotUsed] = (_: HCursor) => Right(NotUsed)
    override val handleResponse       = new NOOPHandler[NotUsed]
    override def expectedResponseCode = StatusCodes.NoContent
  }

  trait NoParamsResponseRequest extends NoParamsRequest[NotUsed] with NoResponseRequest[NotUsed]

  //Channels

  case class GetChannel(channelId: Snowflake) extends NoParamsRequest[RawChannel] {
    def route:                    RestRoute                = Routes.getChannel(channelId)
    override def responseDecoder: Decoder[RawChannel]      = implicitly[Decoder[RawChannel]]
    override def handleResponse:  CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler
  }

  case class ModifyChannelData(name: String, position: Int, topic: Option[String], bitrate: Option[Int], userLimit: Option[Int])
  case class ModifyChannel(channelId: Snowflake, params: ModifyChannelData) extends RESTRequest[ModifyChannelData, RawGuildChannel] {
    override def route:           RestRoute                     = Routes.modifyChannelPut(channelId)
    override def paramsEncoder:   Encoder[ModifyChannelData]    = deriveEncoder[ModifyChannelData]
    override def responseDecoder: Decoder[RawGuildChannel]      = implicitly[Decoder[RawGuildChannel]]
    override def handleResponse:  CacheHandler[RawGuildChannel] = RawHandlers.rawGuildChannelUpdateHandler
  }

  case class DeleteCloseChannel(channelId: Snowflake) extends NoParamsRequest[RawChannel] {
    override def route:           RestRoute                = Routes.deleteCloseChannel(channelId)
    override def responseDecoder: Decoder[RawChannel]      = implicitly[Decoder[RawChannel]]
    override def handleResponse:  CacheHandler[RawChannel] = RawHandlers.rawChannelDeleteHandler
  }

  case class GetChannelMessagesData(around: Option[Snowflake], before: Option[Snowflake], after: Option[Snowflake], limit: Option[Int]) {
    require(Seq(around, before, after).count(_.isDefined) <= 1)
  }
  case class GetChannelMessages(channelId: Snowflake, params: GetChannelMessagesData) extends RESTRequest[GetChannelMessagesData, Seq[RawMessage]] {
    override def route:           RestRoute                       = Routes.getChannelMessages(channelId)
    override def paramsEncoder:   Encoder[GetChannelMessagesData] = deriveEncoder[GetChannelMessagesData]
    override def responseDecoder: Decoder[Seq[RawMessage]]        = implicitly[Decoder[Seq[RawMessage]]]
    override def handleResponse:  CacheHandler[Seq[RawMessage]]   = CacheUpdateHandler.seqHandler(RawHandlers.rawMessageUpdateHandler)
  }

  case class GetChannelMessage(channelId: Snowflake, messageId: Snowflake) extends NoParamsRequest[RawMessage] {
    override def route:           RestRoute                = Routes.getChannelMessage(messageId, channelId)
    override def responseDecoder: Decoder[RawMessage]      = implicitly[Decoder[RawMessage]]
    override def handleResponse:  CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler
  }

  case class CreateMessageData(content: String, nonce: Option[Snowflake], tts: Boolean, file: Option[Path], embed: Option[OutgoingEmbed]) {
    file.foreach(path => require(Files.isRegularFile(path)))
  }

  //We handle this here as the file argument needs special treatment
  implicit private val createMessageDataEncoder: Encoder[CreateMessageData] = (a: CreateMessageData) =>
    Json
      .obj("content" -> a.content.asJson, "nonce" -> a.nonce.asJson, "tts" -> a.tts.asJson, "embed" -> a.embed.asJson)
  case class CreateMessage(channelId: Snowflake, params: CreateMessageData) extends RESTRequest[CreateMessageData, RawMessage] {
    override def route:           RestRoute                  = Routes.createMessage(channelId)
    override def paramsEncoder:   Encoder[CreateMessageData] = createMessageDataEncoder
    override def responseDecoder: Decoder[RawMessage]        = implicitly[Decoder[RawMessage]]
    override def handleResponse:  CacheHandler[RawMessage]   = RawHandlers.rawMessageUpdateHandler
  }

  case class CreateReaction(channelId: Snowflake, messageId: Snowflake, emoji: String) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.createReaction(emoji, messageId, channelId)
  }

  case class DeleteOwnReaction(channelId: Snowflake, messageId: Snowflake, emoji: String) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteOwnReaction(emoji, messageId, channelId)
  }

  case class DeleteUserReaction(channelId: Snowflake, messageId: Snowflake, emoji: String, userId: Snowflake) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteUserReaction(userId, emoji, messageId, channelId)
  }

  case class GetReactions(channelId: Snowflake, messageId: Snowflake, emoji: String) extends NoParamsRequest[Seq[User]] {
    override def route:           RestRoute               = Routes.getReactions(emoji, messageId, channelId)
    override def responseDecoder: Decoder[Seq[User]]      = implicitly[Decoder[Seq[User]]]
    override def handleResponse:  CacheHandler[Seq[User]] = CacheUpdateHandler.seqHandler(Handlers.userUpdateHandler)
  }

  case class DeleteAllReactions(channelId: Snowflake, messageId: Snowflake) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteAllReactions(messageId, channelId)
  }

  case class EditMessageData(content: Option[String], embed: Option[OutgoingEmbed]) {
    require(content.forall(_.length < 2000))
  }
  case class EditMessage(channelId: Snowflake, messageId: Snowflake, params: EditMessageData) extends RESTRequest[EditMessageData, RawMessage] {
    override def route:           RestRoute                = Routes.editMessage(messageId, channelId)
    override def paramsEncoder:   Encoder[EditMessageData] = deriveEncoder[EditMessageData]
    override def responseDecoder: Decoder[RawMessage]      = implicitly[Decoder[RawMessage]]
    override def handleResponse:  CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler
  }

  case class DeleteMessage(channelId: Snowflake, messageId: Snowflake) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.deleteMessage(messageId, channelId)
  }

  case class BulkDeleteMessagesData(messages: Seq[Snowflake])
  case class BulkDeleteMessages(channelId: Snowflake, params: BulkDeleteMessagesData) extends NoResponseRequest[BulkDeleteMessagesData] {
    override def route:         RestRoute                       = Routes.bulkDeleteMessages(channelId)
    override def paramsEncoder: Encoder[BulkDeleteMessagesData] = deriveEncoder[BulkDeleteMessagesData]
  }

  case class EditChannelPermissionsData(allow: Permission, deny: Permission, `type`: String)
  case class EditChannelPermissions(channelId: Snowflake, overwriteId: Snowflake, params: EditChannelPermissionsData)
      extends NoResponseRequest[EditChannelPermissionsData] {
    override def route:         RestRoute                           = Routes.editChannelPermissions(overwriteId, channelId)
    override def paramsEncoder: Encoder[EditChannelPermissionsData] = deriveEncoder[EditChannelPermissionsData]
  }

  case class DeleteChannelPermission(channelId: Snowflake, overwriteId: Snowflake) extends NoParamsResponseRequest {
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

  case class TriggerTypingIndicator(channelId: Snowflake) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.triggerTyping(channelId)
  }

  case class GetPinnedMessages(channelId: Snowflake) extends NoParamsRequest[Seq[RawMessage]] {
    override def route:           RestRoute                     = Routes.getPinnedMessage(channelId)
    override def responseDecoder: Decoder[Seq[RawMessage]]      = implicitly[Decoder[Seq[RawMessage]]]
    override def handleResponse:  CacheHandler[Seq[RawMessage]] = CacheUpdateHandler.seqHandler(RawHandlers.rawMessageUpdateHandler)
  }

  case class AddPinnedChannelMessages(channelId: Snowflake, messageId: Snowflake) extends NoParamsResponseRequest {
    override def route: RestRoute = Routes.addPinnedChannelMessage(messageId, channelId)
  }

  case class DeletePinnedChannelMessages(channelId: Snowflake, messageId: Snowflake) extends NoParamsResponseRequest {
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
}
