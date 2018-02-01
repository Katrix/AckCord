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
package net.katsstuff.ackcord.http.requests

import java.nio.file.{Files, Path}

import scala.concurrent.Future

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, RequestEntity, ResponseEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Flow
import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.handlers._
import net.katsstuff.ackcord.http.websocket.gateway.GatewayEvent
import net.katsstuff.ackcord.http.websocket.gateway.GatewayEvent.GuildEmojisUpdateData
import net.katsstuff.ackcord.http.{RawBan, RawChannel, RawEmoji, RawGuild, RawGuildMember, RawMessage, RawRole, Routes}
import net.katsstuff.ackcord.util.{AckCordSettings, MapWithMaterializer}
import net.katsstuff.ackcord.{CacheState, SnowflakeMap}

object RESTRequests {
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import net.katsstuff.ackcord.http.DiscordProtocol._

  /**
    * Base trait for all REST requests in AckCord. If you feel an endpoint is
    * missing, and AckCord hasn't added it yet, you can extend this and create
    * your own request. I'd recommend you to extend
    * [[ComplexRESTRequest]] or [[SimpleRESTRequest]] tough for simplicity.
    * @tparam RawResponse The response type of the request
    * @tparam HandlerType The response type as the cache handler can handle it.
    * @tparam Response The response type as sent back to the handler.
    */
  trait BaseRESTRequest[RawResponse, HandlerType, Response, Ctx] extends Request[RawResponse, Ctx] {

    override def parseResponse(
        parallelism: Int
    )(implicit system: ActorSystem): Flow[ResponseEntity, RawResponse, NotUsed] = {
      val baseFlow = MapWithMaterializer
        .flow { implicit mat => responseEntity: ResponseEntity =>
          Unmarshal(responseEntity)
            .to[Json]
        }
        .mapAsyncUnordered(parallelism)(identity)

      val withLogging =
        if (AckCordSettings().LogReceivedREST)
          baseFlow.log(
            s"Received REST response",
            json => s"From ${route.uri} with method ${route.method.value} and content ${json.noSpaces}"
          )
        else baseFlow

      withLogging.mapAsyncUnordered(parallelism)(json => Future.fromTry(json.as(responseDecoder).toTry))
    }

    /**
      * A decoder to decode the response.
      */
    def responseDecoder: Decoder[RawResponse]

    /**
      * A cache handler to update the cache with the response of this request.
      */
    def cacheHandler: CacheHandler[HandlerType]

    /**
      * Convert the response to a format the cache handler can understand.
      */
    def convertToCacheHandlerType(response: RawResponse): HandlerType

    /**
      * Check if this request has a more nicer response object that can be gotten through the cache.
      */
    def hasCustomResponseData: Boolean

    /**
      * Find the data to send back to the handler.
      */
    def findData(response: RawResponse)(cache: CacheState): Option[Response]

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
  trait ComplexRESTRequest[Params, RawResponse, HandlerType, Response, Ctx]
      extends BaseRESTRequest[RawResponse, HandlerType, Response, Ctx] {

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

    def jsonPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)

    def requestBody: RequestEntity =
      if (params == NotUsed) HttpEntity.Empty
      else HttpEntity(ContentTypes.`application/json`, jsonParams.pretty(jsonPrinter))
  }

  /**
    * A complex REST request with an audit log reason.
    */
  trait ComplexReasonRequest[Self <: ComplexReasonRequest[Self, Params, RawResponse, HandlerType, Response, Ctx], Params, RawResponse, HandlerType, Response, Ctx]
      extends ComplexRESTRequest[Params, RawResponse, HandlerType, Response, Ctx] {

    /**
      * A reason to add to the audit log entry.
      */
    def withReason(reason: String): Self

    def reason: Option[String]

    override def extraHeaders: Seq[HttpHeader] =
      reason.fold[Seq[HttpHeader]](Nil)(str => Seq(`X-Audit-Log-Reason`(str)))
  }

  /**
    * An even simpler request trait where the response type and the handler type
    * are the same.
    */
  trait SimpleRESTRequest[Params, RawResponse, Response, Ctx]
      extends ComplexRESTRequest[Params, RawResponse, RawResponse, Response, Ctx] {
    override def convertToCacheHandlerType(response: RawResponse): RawResponse = response
  }

  /**
    * An even simpler request trait where the response type and the handler type
    * are the same.
    */
  trait SimpleReasonRequest[Self <: SimpleReasonRequest[Self, Params, RawResponse, Response, Ctx], Params, RawResponse, Response, Ctx]
      extends ComplexReasonRequest[Self, Params, RawResponse, RawResponse, Response, Ctx] {
    override def convertToCacheHandlerType(response: RawResponse): RawResponse = response
  }

  /**
    * A simple request that takes to params.
    */
  trait NoParamsRequest[RawResponse, Response, Ctx] extends SimpleRESTRequest[NotUsed, RawResponse, Response, Ctx] {
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed
  }

  /**
    * A simple request that takes to params with an audit log reason.
    */
  trait NoParamsReasonRequest[Self <: NoParamsReasonRequest[Self, RawResponse, Response, Ctx], RawResponse, Response, Ctx]
      extends SimpleReasonRequest[Self, NotUsed, RawResponse, Response, Ctx] {
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed
  }

  trait NoNiceResponseRequest[Params, RawResponse, Ctx] extends SimpleRESTRequest[Params, RawResponse, NotUsed, Ctx] {
    override def hasCustomResponseData: Boolean = false

    override def findData(response: RawResponse)(cache: CacheState): Option[NotUsed] = None
  }

  trait NoNiceResponseReasonRequest[Self <: NoNiceResponseReasonRequest[Self, Params, RawResponse, Ctx], Params, RawResponse, Ctx]
      extends SimpleReasonRequest[Self, Params, RawResponse, NotUsed, Ctx] {
    override def hasCustomResponseData: Boolean = false

    override def findData(response: RawResponse)(cache: CacheState): Option[NotUsed] = None
  }

  /**
    * A trait for when there is no nicer response that can be gotten through the cache.
    */
  trait NoParamsNiceResponseRequest[RawResponse, Ctx]
      extends NoParamsRequest[RawResponse, NotUsed, Ctx]
      with NoNiceResponseRequest[NotUsed, RawResponse, Ctx]

  /**
    * A trait for when there is no nicer response that can be gotten through the cache, with a reason.
    */
  trait NoParamsNiceResponseReasonRequest[Self <: NoParamsNiceResponseReasonRequest[Self, RawResponse, Ctx], RawResponse, Ctx]
      extends NoParamsReasonRequest[Self, RawResponse, NotUsed, Ctx]
      with NoNiceResponseReasonRequest[Self, NotUsed, RawResponse, Ctx]

  /**
    * A simple request that doesn't have a response.
    */
  trait NoResponseRequest[Params, Ctx] extends SimpleRESTRequest[Params, NotUsed, NotUsed, Ctx] {
    override def responseDecoder: Decoder[NotUsed]      = (_: HCursor) => Right(NotUsed)
    override def cacheHandler:    CacheHandler[NotUsed] = NOOPHandler

    override def hasCustomResponseData:                          Boolean         = false
    override def findData(response: NotUsed)(cache: CacheState): Option[NotUsed] = None
  }

  /**
    * A simple request that doesn't have a response.
    */
  trait NoResponseReasonRequest[Self <: NoResponseReasonRequest[Self, Params, Ctx], Params, Ctx]
      extends SimpleReasonRequest[Self, Params, NotUsed, NotUsed, Ctx] {
    override def responseDecoder: Decoder[NotUsed]      = (_: HCursor) => Right(NotUsed)
    override def cacheHandler:    CacheHandler[NotUsed] = NOOPHandler

    override def hasCustomResponseData:                          Boolean         = false
    override def findData(response: NotUsed)(cache: CacheState): Option[NotUsed] = None
  }

  /**
    * A simple request that has neither params nor a response.
    */
  trait NoParamsResponseRequest[Ctx] extends NoParamsRequest[NotUsed, NotUsed, Ctx] with NoResponseRequest[NotUsed, Ctx]

  /**
    * A simple request that has neither params nor a response with a reason.
    */
  trait NoParamsResponseReasonRequest[Self <: NoParamsResponseReasonRequest[Self, Ctx], Ctx]
      extends NoParamsReasonRequest[Self, NotUsed, NotUsed, Ctx]
      with NoResponseReasonRequest[Self, NotUsed, Ctx]

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
  case class GetGuildAuditLog[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[AuditLog, Ctx] {
    override def route: RequestRoute = Routes.getGuildAuditLogs(guildId)

    override def responseDecoder: Decoder[AuditLog]      = Decoder[AuditLog]
    override def cacheHandler:    CacheHandler[AuditLog] = NOOPHandler

    override def requiredPermissions:                        Permission = Permission.ViewAuditLog
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  //Channels

  /**
    * Get a channel by id.
    */
  case class GetChannel[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsRequest[RawChannel, Channel, Ctx] {
    def route:                    RequestRoute             = Routes.getChannel(channelId)
    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def cacheHandler:    CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler

    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawChannel)(cache: CacheState): Option[Channel] =
      cache.current.getChannel(channelId)
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
      name: RestOption[String] = RestUndefined,
      position: RestOption[Int] = RestUndefined,
      topic: RestOption[String] = RestUndefined,
      nsfw: RestOption[Boolean] = RestUndefined,
      bitrate: RestOption[Int] = RestUndefined,
      userLimit: RestOption[Int] = RestUndefined,
      permissionOverwrites: RestOption[Seq[PermissionOverwrite]] = RestUndefined,
      parentId: RestOption[ChannelId] = RestUndefined
  ) {
    require(name.forall(_.length <= 100), "Name must be between 2 and 100 characters")
    require(topic.forall(_.length <= 100), "Topic must be between 0 and 1024 characters")
    require(bitrate.forall(b => b >= 8000 && b <= 128000), "Bitrate must be between 8000 and 128000 bits")
    require(userLimit.forall(b => b >= 0 && b <= 99), "User limit must be between 0 and 99 users")
  }
  object ModifyChannelData {
    implicit val encoder: Encoder[ModifyChannelData] = (a: ModifyChannelData) => {
      RestOption.removeUndefinedToObj(
        "name"                  -> a.name.map(_.asJson),
        "position"              -> a.position.map(_.asJson),
        "topic"                 -> a.topic.map(_.asJson),
        "nsfw"                  -> a.nsfw.map(_.asJson),
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
  ) extends SimpleReasonRequest[ModifyChannel[Ctx], ModifyChannelData, RawChannel, Channel, Ctx] {
    override def route:         RequestRoute               = Routes.modifyChannelPut(channelId)
    override def paramsEncoder: Encoder[ModifyChannelData] = ModifyChannelData.encoder

    override def jsonPrinter: Printer = Printer.noSpaces

    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def cacheHandler:    CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler

    override def requiredPermissions: Permission = Permission.ManageChannels
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawChannel)(cache: CacheState): Option[Channel] =
      cache.current.getChannel(channelId)

    override def withReason(reason: String): ModifyChannel[Ctx] = copy(reason = Some(reason))
  }

  /**
    * Delete a guild channel, or close a DM channel.
    */
  case class DeleteCloseChannel[Ctx](
      channelId: ChannelId,
      context: Ctx = NotUsed: NotUsed,
      reason: Option[String] = None
  ) extends NoParamsReasonRequest[DeleteCloseChannel[Ctx], RawChannel, Channel, Ctx] {
    override def route: RequestRoute = Routes.deleteCloseChannel(channelId)

    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def cacheHandler:    CacheHandler[RawChannel] = RawHandlers.rawChannelDeleteHandler

    override def requiredPermissions: Permission = Permission.ManageChannels
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawChannel)(cache: CacheState): Option[Channel] =
      cache.previous.getChannel(channelId)

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
    require(
      Seq(around, before, after).count(_.isDefined) <= 1,
      "The around, before, after fields are mutually exclusive"
    )
    require(limit.forall(c => c >= 1 && c <= 100), "Count must be between 1 and 100")

    def toMap: Map[String, String] = Map(
      "around" -> around.map(_.asString),
      "before" -> before.map(_.asString),
      "after" -> after.map(_.asString),
      "limit" -> limit.map(_.toString),
    ).flatMap {
      case (name, Some(value)) => Some(name -> value)
      case (_, None) => None
    }
  }

  /**
    * Get the messages in a channel.
    */
  case class GetChannelMessages[Ctx](
      channelId: ChannelId,
      query: GetChannelMessagesData,
      context: Ctx = NotUsed: NotUsed
  ) extends NoParamsRequest[Seq[RawMessage], Seq[Message], Ctx] {
    override def route: RequestRoute = {
      val base = Routes.getChannelMessages(channelId)
      base.copy(uri = base.uri.withQuery(Uri.Query(query.toMap)))
    }

    override def responseDecoder: Decoder[Seq[RawMessage]] = Decoder[Seq[RawMessage]]
    override def cacheHandler: CacheHandler[Seq[RawMessage]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawMessageUpdateHandler)

    override def requiredPermissions: Permission = Permission.ReadMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawMessage])(cache: CacheState): Option[Seq[Message]] =
      Some(cache.current.getChannelMessages(channelId).values.toSeq)
  }
  object GetChannelMessages {
    def around[Ctx](
        channelId: ChannelId,
        around: MessageId,
        limit: Option[Int] = None,
        context: Ctx = NotUsed: NotUsed
    ) = new GetChannelMessages(channelId, GetChannelMessagesData(around = Some(around), limit = limit), context)

    def before[Ctx](
        channelId: ChannelId,
        before: MessageId,
        limit: Option[Int] = None,
        context: Ctx = NotUsed: NotUsed
    ) = new GetChannelMessages(channelId, GetChannelMessagesData(before = Some(before), limit = limit), context)

    def after[Ctx](channelId: ChannelId, after: MessageId, limit: Option[Int] = None, context: Ctx = NotUsed: NotUsed) =
      new GetChannelMessages(channelId, GetChannelMessagesData(after = Some(after), limit = limit), context)
  }

  /**
    * Get a specific message in a channel.
    */
  case class GetChannelMessage[Ctx](channelId: ChannelId, messageId: MessageId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsRequest[RawMessage, Message, Ctx] {
    override def route: RequestRoute = Routes.getChannelMessage(messageId, channelId)

    override def responseDecoder: Decoder[RawMessage]      = Decoder[RawMessage]
    override def cacheHandler:    CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler

    override def requiredPermissions: Permission = Permission.ReadMessageHistory
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawMessage)(cache: CacheState): Option[Message] =
      cache.current.getMessage(channelId, messageId)
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

  //We handle this here as the file argument needs special treatment
  implicit private val createMessageDataEncoder: Encoder[CreateMessageData] = (a: CreateMessageData) =>
    Json.obj("content" -> a.content.asJson, "nonce" -> a.nonce.asJson, "tts" -> a.tts.asJson, "embed" -> a.embed.asJson)

  /**
    * Create a message in a channel.
    */
  case class CreateMessage[Ctx](channelId: ChannelId, params: CreateMessageData, context: Ctx = NotUsed: NotUsed)
      extends SimpleRESTRequest[CreateMessageData, RawMessage, Message, Ctx] {
    override def route:         RequestRoute               = Routes.createMessage(channelId)
    override def paramsEncoder: Encoder[CreateMessageData] = createMessageDataEncoder
    override def requestBody: RequestEntity = {
      this match {
        case CreateMessage(_, CreateMessageData(_, _, _, files, _), _) if files.nonEmpty =>
          val fileParts = files.map { f =>
            FormData.BodyPart.fromPath(f.getFileName.toString, ContentTypes.`application/octet-stream`, f)
          }

          val jsonPart = FormData.BodyPart(
            "payload_json",
            HttpEntity(ContentTypes.`application/json`, jsonParams.pretty(jsonPrinter))
          )

          FormData(fileParts :+ jsonPart: _*).toEntity()
        case _ => super.requestBody
      }
    }

    override def responseDecoder: Decoder[RawMessage]      = Decoder[RawMessage]
    override def cacheHandler:    CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler

    override def requiredPermissions: Permission =
      if (params.tts) Permission(Permission.SendMessages, Permission.SendTtsMessages) else Permission.SendMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawMessage)(cache: CacheState): Option[Message] =
      cache.current.getMessage(channelId, response.id)
  }
  object CreateMessage {
    def mkContent[Ctx](channelId: ChannelId, content: String, context: Ctx = NotUsed: NotUsed): CreateMessage[Ctx] =
      new CreateMessage(channelId, CreateMessageData(content), context)

    def mkEmbed[Ctx](channelId: ChannelId, embed: OutgoingEmbed, context: Ctx = NotUsed: NotUsed): CreateMessage[Ctx] =
      new CreateMessage(channelId, CreateMessageData(embed = Some(embed)), context)
  }

  /**
    * Create a reaction for a message.
    */
  case class CreateReaction[Ctx](
      channelId: ChannelId,
      messageId: MessageId,
      emoji: String,
      context: Ctx = NotUsed: NotUsed
  ) extends NoParamsResponseRequest[Ctx] {
    override def route: RequestRoute = Routes.createReaction(emoji, messageId, channelId)

    override def requiredPermissions: Permission = Permission.ReadMessageHistory
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
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
    override def route: RequestRoute = Routes.deleteOwnReaction(emoji, messageId, channelId)
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
    override def route: RequestRoute = Routes.deleteUserReaction(userId, emoji, messageId, channelId)

    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * @param before Get users before this user.
    * @param after Get users after this user.
    * @param limit The max amount of users to return. Defaults to 100.
    */
  case class GetReactionsData(before: Option[UserId] = None, after: Option[UserId] = None, limit: Option[Int] = None) {
    require(limit.forall(l => l >= 1 && l <= 100), "Limit must be between 1 and 100")
  }

  /**
    * Get all the users that have reacted with an emoji for a message.
    */
  case class GetReactions[Ctx](
      channelId: ChannelId,
      messageId: MessageId,
      emoji: String,
      params: GetReactionsData,
      context: Ctx = NotUsed: NotUsed
  ) extends NoNiceResponseRequest[GetReactionsData, Seq[User], Ctx] {
    override def route:         RequestRoute              = Routes.getReactions(emoji, messageId, channelId)
    override def paramsEncoder: Encoder[GetReactionsData] = deriveEncoder

    override def responseDecoder: Decoder[Seq[User]]      = Decoder[Seq[User]]
    override def cacheHandler:    CacheHandler[Seq[User]] = CacheUpdateHandler.seqHandler(Handlers.userUpdateHandler)
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
    override def route: RequestRoute = Routes.deleteAllReactions(messageId, channelId)

    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * @param content The content of the new message
    * @param embed The embed of the new message
    */
  case class EditMessageData(
      content: RestOption[String] = RestUndefined,
      embed: RestOption[OutgoingEmbed] = RestUndefined
  ) {
    require(content.forall(_.length < 2000))
  }
  object EditMessageData {
    implicit val encoder: Encoder[EditMessageData] = (a: EditMessageData) =>
      RestOption.removeUndefinedToObj("content" -> a.content.map(_.asJson), "content" -> a.embed.map(_.asJson))
  }

  /**
    * Edit an existing message
    */
  case class EditMessage[Ctx](
      channelId: ChannelId,
      messageId: MessageId,
      params: EditMessageData,
      context: Ctx = NotUsed: NotUsed
  ) extends SimpleRESTRequest[EditMessageData, RawMessage, Message, Ctx] {
    override def route:         RequestRoute             = Routes.editMessage(messageId, channelId)
    override def paramsEncoder: Encoder[EditMessageData] = EditMessageData.encoder

    override def jsonPrinter: Printer = Printer.noSpaces

    override def responseDecoder: Decoder[RawMessage]      = Decoder[RawMessage]
    override def cacheHandler:    CacheHandler[RawMessage] = RawHandlers.rawMessageUpdateHandler

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawMessage)(cache: CacheState): Option[Message] =
      cache.current.getMessage(channelId, messageId)
  }
  object EditMessage {
    def mkContent[Ctx](
        channelId: ChannelId,
        messageId: MessageId,
        content: String,
        context: Ctx = NotUsed: NotUsed
    ): EditMessage[Ctx] = new EditMessage(channelId, messageId, EditMessageData(RestSome(content)), context)

    def mkEmbed[Ctx](
        channelId: ChannelId,
        messageId: MessageId,
        embed: OutgoingEmbed,
        context: Ctx = NotUsed: NotUsed
    ): EditMessage[Ctx] = new EditMessage(channelId, messageId, EditMessageData(embed = RestSome(embed)), context)
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
    override def route: RequestRoute = Routes.deleteMessage(messageId, channelId)

    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
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
    override def route:         RequestRoute                    = Routes.bulkDeleteMessages(channelId)
    override def paramsEncoder: Encoder[BulkDeleteMessagesData] = deriveEncoder[BulkDeleteMessagesData]

    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
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
    override def route:         RequestRoute                        = Routes.editChannelPermissions(overwriteId, channelId)
    override def paramsEncoder: Encoder[EditChannelPermissionsData] = deriveEncoder[EditChannelPermissionsData]

    override def requiredPermissions: Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
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
    override def route: RequestRoute = Routes.deleteChannelPermissions(overwriteId, channelId)

    override def requiredPermissions: Permission = Permission.ManageRoles

    override def withReason(reason: String): DeleteChannelPermission[Ctx] = copy(reason = Some(reason))
  }

  /**
    * Get all invites for this channel. Can only be used on guild channels.
    */
  case class GetChannelInvites[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Seq[InviteWithMetadata], Ctx] {
    override def route: RequestRoute = Routes.getChannelInvites(channelId)

    override def responseDecoder: Decoder[Seq[InviteWithMetadata]]      = Decoder[Seq[InviteWithMetadata]]
    override def cacheHandler:    CacheHandler[Seq[InviteWithMetadata]] = NOOPHandler

    override def requiredPermissions: Permission = Permission.ManageChannels
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
  case class CreateChannelInvite[Ctx](
      channelId: ChannelId,
      params: CreateChannelInviteData,
      context: Ctx = NotUsed: NotUsed,
      reason: Option[String] = None
  ) extends NoNiceResponseReasonRequest[CreateChannelInvite[Ctx], CreateChannelInviteData, Invite, Ctx] {
    override def route:         RequestRoute                     = Routes.getChannelInvites(channelId)
    override def paramsEncoder: Encoder[CreateChannelInviteData] = deriveEncoder[CreateChannelInviteData]

    override def responseDecoder: Decoder[Invite]      = Decoder[Invite]
    override def cacheHandler:    CacheHandler[Invite] = NOOPHandler

    override def requiredPermissions: Permission = Permission.CreateInstantInvite
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
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

    override def responseDecoder: Decoder[Seq[RawMessage]] = Decoder[Seq[RawMessage]]
    override def cacheHandler: CacheHandler[Seq[RawMessage]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawMessageUpdateHandler)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawMessage])(cache: CacheState): Option[Seq[Message]] =
      Some(response.map(_.id).flatMap(cache.current.getMessage(channelId, _)))
  }

  /**
    * Add a new pinned message to a channel.
    */
  case class AddPinnedChannelMessages[Ctx](channelId: ChannelId, messageId: MessageId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsResponseRequest[Ctx] {
    override def route: RequestRoute = Routes.addPinnedChannelMessage(messageId, channelId)

    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Delete a pinned message in a channel.
    */
  case class DeletePinnedChannelMessages[Ctx](
      channelId: ChannelId,
      messageId: MessageId,
      context: Ctx = NotUsed: NotUsed
  ) extends NoParamsResponseRequest[Ctx] {
    override def route: RequestRoute = Routes.deletePinnedChannelMessage(messageId, channelId)

    override def requiredPermissions: Permission = Permission.ManageMessages
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /*
  case class GroupDMAddRecipientData(accessToken: String, nick: String)
  case class GroupDMAddRecipient[Ctx](channelId:       Snowflake, userId: Snowflake, params: GroupDMAddRecipientData, context: Ctx = NotUsed: NotUsed)
      extends RESTRequest[GroupDMAddRecipientData, Ctx] {
    override def route:         RestRoute                        = Routes.groupDmAddRecipient(userId, channelId)
    override def paramsEncoder: Encoder[GroupDMAddRecipientData] = deriveEncoder[GroupDMAddRecipientData]
  }

  case class GroupDMRemoveRecipient[Ctx](channelId: Snowflake, userId: Snowflake, context: Ctx = NotUsed: NotUsed) extends NoParamsRequest {
    override def route: RestRoute = Routes.groupDmRemoveRecipient(userId, channelId)
  }
   */

  /**
    * Get all the emojis for this guild.
    */
  case class ListGuildEmojis[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
      extends ComplexRESTRequest[NotUsed, Seq[RawEmoji], GuildEmojisUpdateData, Seq[Emoji], Ctx] {
    override def route:         RequestRoute     = Routes.listGuildEmojis(guildId)
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()
    override def params:        NotUsed          = NotUsed

    override def responseDecoder: Decoder[Seq[RawEmoji]]              = Decoder[Seq[RawEmoji]]
    override def cacheHandler:    CacheHandler[GuildEmojisUpdateData] = RawHandlers.guildEmojisUpdateDataHandler
    override def convertToCacheHandlerType(response: Seq[RawEmoji]): GuildEmojisUpdateData =
      GuildEmojisUpdateData(guildId, response)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawEmoji])(cache: CacheState): Option[Seq[Emoji]] =
      Some(response.map(_.toEmoji))
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
  ) extends SimpleReasonRequest[CreateGuildEmoji[Ctx], CreateGuildEmojiData, RawEmoji, Emoji, Ctx] {
    override def route:         RequestRoute                  = Routes.createGuildEmoji(guildId)
    override def paramsEncoder: Encoder[CreateGuildEmojiData] = deriveEncoder[CreateGuildEmojiData]

    override def responseDecoder: Decoder[RawEmoji]      = Decoder[RawEmoji]
    override def cacheHandler:    CacheHandler[RawEmoji] = RawHandlers.rawEmojiUpdateHandler(guildId)

    override def requiredPermissions:                        Permission = Permission.ManageEmojis
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData:                           Boolean       = true
    override def findData(response: RawEmoji)(cache: CacheState): Option[Emoji] = Some(response.toEmoji)

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
    override def route: RequestRoute = Routes.getGuildEmoji(emojiId, guildId)

    override def responseDecoder: Decoder[RawEmoji]      = Decoder[RawEmoji]
    override def cacheHandler:    CacheHandler[RawEmoji] = RawHandlers.rawEmojiUpdateHandler(guildId)

    override def hasCustomResponseData:                           Boolean       = true
    override def findData(response: RawEmoji)(cache: CacheState): Option[Emoji] = Some(response.toEmoji)
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
  ) extends SimpleReasonRequest[ModifyGuildEmoji[Ctx], ModifyGuildEmojiData, RawEmoji, Emoji, Ctx] {
    override def route:         RequestRoute                  = Routes.modifyGuildEmoji(emojiId, guildId)
    override def paramsEncoder: Encoder[ModifyGuildEmojiData] = deriveEncoder[ModifyGuildEmojiData]

    override def responseDecoder: Decoder[RawEmoji]      = Decoder[RawEmoji]
    override def cacheHandler:    CacheHandler[RawEmoji] = RawHandlers.rawEmojiUpdateHandler(guildId)

    override def requiredPermissions:                        Permission = Permission.ManageEmojis
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData:                           Boolean       = true
    override def findData(response: RawEmoji)(cache: CacheState): Option[Emoji] = Some(response.toEmoji)

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
    override def route: RequestRoute = Routes.deleteGuildEmoji(emojiId, guildId)

    override def requiredPermissions:                        Permission = Permission.ManageEmojis
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def withReason(reason: String): DeleteGuildEmoji[Ctx] = copy(reason = Some(reason))
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
      extends SimpleRESTRequest[CreateGuildData, RawGuild, Guild, Ctx] {
    override def route: RequestRoute = Routes.createGuild
    override def paramsEncoder: Encoder[CreateGuildData] = {
      import io.circe.generic.extras.auto._
      deriveEncoder[CreateGuildData]
    }

    override def responseDecoder: Decoder[RawGuild]      = Decoder[RawGuild]
    override def cacheHandler:    CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawGuild)(cache: CacheState): Option[Guild] =
      cache.current.getGuild(response.id)
  }

  /**
    * Get a guild by id.
    */
  case class GetGuild[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsRequest[RawGuild, Guild, Ctx] {
    override def route: RequestRoute = Routes.getGuild(guildId)

    override def responseDecoder: Decoder[RawGuild]      = Decoder[RawGuild]
    override def cacheHandler:    CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawGuild)(cache: CacheState): Option[Guild] =
      cache.current.getGuild(guildId)
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
  ) extends SimpleReasonRequest[ModifyGuild[Ctx], ModifyGuildData, RawGuild, Guild, Ctx] {
    override def route:         RequestRoute             = Routes.modifyGuild(guildId)
    override def paramsEncoder: Encoder[ModifyGuildData] = deriveEncoder[ModifyGuildData]

    override def responseDecoder: Decoder[RawGuild]      = Decoder[RawGuild]
    override def cacheHandler:    CacheHandler[RawGuild] = RawHandlers.rawGuildUpdateHandler

    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawGuild)(cache: CacheState): Option[Guild] =
      cache.current.getGuild(guildId)

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
      extends NoParamsRequest[Seq[RawChannel], Seq[GuildChannel], Ctx] {
    override def route: RequestRoute = Routes.getGuildChannels(guildId)

    override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
    override def cacheHandler: CacheHandler[Seq[RawChannel]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawChannelUpdateHandler)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawChannel])(cache: CacheState): Option[Seq[GuildChannel]] =
      cache.current.getGuild(guildId).map(_.channels.values.toSeq)
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
      `type`: RestOption[ChannelType] = RestUndefined,
      bitrate: RestOption[Int] = RestUndefined,
      userLimit: RestOption[Int] = RestUndefined,
      permissionOverwrites: RestOption[Seq[PermissionOverwrite]] = RestUndefined,
      parentId: RestOption[ChannelId] = RestUndefined,
      nsfw: RestOption[Boolean] = RestUndefined
  ) {
    require(name.length >= 2 && name.length <= 100, "A channel name has to be between 2 and 100 characters")
  }
  object CreateGuildChannelData {
    implicit val encoder: Encoder[CreateGuildChannelData] = (a: CreateGuildChannelData) =>
      RestOption.removeUndefinedToObj(
        "name"                  -> RestSome(a.name.asJson),
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
  ) extends SimpleReasonRequest[CreateGuildChannel[Ctx], CreateGuildChannelData, RawChannel, Channel, Ctx] {
    override def route:         RequestRoute                    = Routes.createGuildChannel(guildId)
    override def paramsEncoder: Encoder[CreateGuildChannelData] = CreateGuildChannelData.encoder

    override def jsonPrinter: Printer = Printer.noSpaces

    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def cacheHandler:    CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler

    override def requiredPermissions:                        Permission = Permission.ManageChannels
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawChannel)(cache: CacheState): Option[Channel] =
      cache.current.getGuildChannel(guildId, response.id)

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
  ) extends SimpleReasonRequest[ModifyGuildChannelPositions[Ctx], Seq[ModifyGuildChannelPositionsData], Seq[
        RawChannel
      ], Seq[Channel], Ctx] {
    override def route: RequestRoute = Routes.modifyGuildChannelsPositions(guildId)
    override def paramsEncoder: Encoder[Seq[ModifyGuildChannelPositionsData]] = {
      implicit val enc: Encoder[ModifyGuildChannelPositionsData] = deriveEncoder[ModifyGuildChannelPositionsData]
      Encoder[Seq[ModifyGuildChannelPositionsData]]
    }

    override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
    override def cacheHandler: CacheHandler[Seq[RawChannel]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawChannelUpdateHandler)

    override def requiredPermissions:                        Permission = Permission.ManageChannels
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawChannel])(cache: CacheState): Option[Seq[Channel]] =
      cache.current.getGuild(guildId).map(g => params.map(_.id).flatMap(g.channels.get))

    override def withReason(reason: String): ModifyGuildChannelPositions[Ctx] = copy(reason = Some(reason))
  }

  trait GuildMemberRequest[Params, Ctx]
      extends ComplexRESTRequest[Params, RawGuildMember, GatewayEvent.RawGuildMemberWithGuild, GuildMember, Ctx] {
    def guildId: GuildId

    override def responseDecoder: Decoder[RawGuildMember] = Decoder[RawGuildMember]
    override def cacheHandler: CacheHandler[GatewayEvent.RawGuildMemberWithGuild] =
      RawHandlers.rawGuildMemberWithGuildUpdateHandler
    override def convertToCacheHandlerType(response: RawGuildMember): GatewayEvent.RawGuildMemberWithGuild =
      GatewayEvent.RawGuildMemberWithGuild(guildId, response)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawGuildMember)(cache: CacheState): Option[GuildMember] =
      cache.current.getGuild(guildId).flatMap(_.members.get(response.user.id))
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
      extends ComplexRESTRequest[ListGuildMembersData, Seq[RawGuildMember], GatewayEvent.GuildMemberChunkData, Seq[
        GuildMember
      ], Ctx] {
    override def route:         RequestRoute                  = Routes.listGuildMembers(guildId)
    override def paramsEncoder: Encoder[ListGuildMembersData] = deriveEncoder[ListGuildMembersData]

    override def responseDecoder: Decoder[Seq[RawGuildMember]] = Decoder[Seq[RawGuildMember]]
    override def cacheHandler: CacheHandler[GatewayEvent.GuildMemberChunkData] =
      RawHandlers.rawGuildMemberChunkHandler
    override def convertToCacheHandlerType(response: Seq[RawGuildMember]): GatewayEvent.GuildMemberChunkData =
      GatewayEvent.GuildMemberChunkData(guildId, response)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawGuildMember])(cache: CacheState): Option[Seq[GuildMember]] =
      cache.current.getGuild(guildId).map(_.members.values.toSeq)
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
      nick: RestOption[String] = RestUndefined,
      roles: RestOption[Seq[RoleId]] = RestUndefined,
      mute: RestOption[Boolean] = RestUndefined,
      deaf: RestOption[Boolean] = RestUndefined,
      channelId: RestOption[ChannelId] = RestUndefined
  )
  object ModifyGuildMemberData {
    implicit val encoder: Encoder[ModifyGuildMemberData] = (a: ModifyGuildMemberData) =>
      RestOption.removeUndefinedToObj(
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
      def ifDefined(opt: RestOption[_], perm: Permission): Permission = if (opt.nonEmpty) perm else Permission.None
      Permission(
        Permission.CreateInstantInvite,
        ifDefined(params.nick, Permission.ManageNicknames),
        ifDefined(params.roles, Permission.ManageRoles),
        ifDefined(params.mute, Permission.MuteMembers),
        ifDefined(params.deaf, Permission.DeafenMembers)
      )
    }
    override def havePermissions(implicit c: CacheSnapshot): Boolean                = hasPermissionsGuild(guildId, requiredPermissions)
    override def withReason(reason: String):                 ModifyGuildMember[Ctx] = copy(reason = Some(reason))
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
    override def cacheHandler: CacheHandler[String] = new CacheUpdateHandler[String] {
      override def handle(builder: CacheSnapshotBuilder, obj: String)(implicit log: LoggingAdapter): Unit = {
        for {
          guild     <- builder.getGuild(guildId)
          botMember <- guild.members.get(builder.botUser.id)
        } {
          val newMembers = guild.members.updated(builder.botUser.id, botMember.copy(nick = Some(obj)))
          val newGuild   = guild.copy(members = newMembers)
          builder.guilds.put(guildId, newGuild)
        }
      }
    }

    override def requiredPermissions:                        Permission = Permission.ChangeNickname
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

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

    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Remove a role from a guild member.
    */
  case class RemoveGuildMemberRole[Ctx](
      guildId: GuildId,
      userId: UserId,
      roleId: RoleId,
      context: Ctx = NotUsed: NotUsed
  ) extends NoParamsResponseRequest[Ctx] {
    override def route: RequestRoute = Routes.removeGuildMemberRole(roleId, userId, guildId)

    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
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

    override def requiredPermissions:                        Permission             = Permission.KickMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean                = hasPermissionsGuild(guildId, requiredPermissions)
    override def withReason(reason: String):                 RemoveGuildMember[Ctx] = copy(reason = Some(reason))
  }

  /**
    * Get all the bans for this guild.
    */
  case class GetGuildBans[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
      extends ComplexRESTRequest[NotUsed, Seq[RawBan], Seq[(GuildId, RawBan)], Seq[Ban], Ctx] {
    override def route:         RequestRoute     = Routes.getGuildBans(guildId)
    override def params:        NotUsed          = NotUsed
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()

    override def responseDecoder: Decoder[Seq[RawBan]] = Decoder[Seq[RawBan]]
    override def cacheHandler: CacheHandler[Seq[(GuildId, RawBan)]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawBanUpdateHandler)
    override def convertToCacheHandlerType(response: Seq[RawBan]): Seq[(GuildId, RawBan)] = response.map(guildId -> _)

    override def requiredPermissions:                        Permission = Permission.BanMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawBan])(cache: CacheState): Option[Seq[Ban]] =
      cache.current.bans.get(guildId).map(_.values.toSeq)
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

    override def requiredPermissions:                        Permission = Permission.BanMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

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

    override def requiredPermissions:                        Permission = Permission.BanMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def withReason(reason: String): RemoveGuildBan[Ctx] = copy(reason = Some(reason))
  }

  /**
    * Get all the roles in a guild.
    */
  case class GetGuildRoles[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
      extends ComplexRESTRequest[NotUsed, Seq[RawRole], Seq[GatewayEvent.GuildRoleModifyData], Seq[Role], Ctx] {
    override def route:         RequestRoute     = Routes.getGuildRole(guildId)
    override def params:        NotUsed          = NotUsed
    override def paramsEncoder: Encoder[NotUsed] = (_: NotUsed) => Json.obj()

    override def responseDecoder: Decoder[Seq[RawRole]] = Decoder[Seq[RawRole]]
    override def cacheHandler: CacheHandler[Seq[GatewayEvent.GuildRoleModifyData]] =
      CacheUpdateHandler.seqHandler(RawHandlers.roleUpdateHandler)
    override def convertToCacheHandlerType(response: Seq[RawRole]): Seq[GatewayEvent.GuildRoleModifyData] =
      response.map(GatewayEvent.GuildRoleModifyData(guildId, _))

    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawRole])(cache: CacheState): Option[Seq[Role]] =
      cache.current.getGuild(guildId).map(_.roles.values.toSeq)
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
  ) extends ComplexReasonRequest[CreateGuildRole[Ctx], CreateGuildRoleData, RawRole, GatewayEvent.GuildRoleModifyData, Role, Ctx] {
    override def route:         RequestRoute                 = Routes.createGuildRole(guildId)
    override def paramsEncoder: Encoder[CreateGuildRoleData] = deriveEncoder[CreateGuildRoleData]

    override def responseDecoder: Decoder[RawRole]                               = Decoder[RawRole]
    override def cacheHandler:    CacheHandler[GatewayEvent.GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def convertToCacheHandlerType(response: RawRole): GatewayEvent.GuildRoleModifyData =
      GatewayEvent.GuildRoleModifyData(guildId, response)

    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawRole)(cache: CacheState): Option[Role] =
      cache.current.getRole(guildId, response.id)

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
  ) extends ComplexReasonRequest[ModifyGuildRolePositions[Ctx], Seq[ModifyGuildRolePositionsData], Seq[RawRole], Seq[
        GatewayEvent.GuildRoleModifyData
      ], Seq[Role], Ctx] {
    override def route: RequestRoute = Routes.modifyGuildRolePositions(guildId)
    override def paramsEncoder: Encoder[Seq[ModifyGuildRolePositionsData]] = {
      implicit val enc: Encoder[ModifyGuildRolePositionsData] = deriveEncoder[ModifyGuildRolePositionsData]
      Encoder[Seq[ModifyGuildRolePositionsData]]
    }

    override def responseDecoder: Decoder[Seq[RawRole]] = Decoder[Seq[RawRole]]
    override def cacheHandler: CacheHandler[Seq[GatewayEvent.GuildRoleModifyData]] =
      CacheUpdateHandler.seqHandler(RawHandlers.roleUpdateHandler)
    override def convertToCacheHandlerType(response: Seq[RawRole]): Seq[GatewayEvent.GuildRoleModifyData] =
      response.map(GatewayEvent.GuildRoleModifyData(guildId, _))

    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawRole])(cache: CacheState): Option[Seq[Role]] =
      cache.current.getGuild(guildId).map(g => params.map(_.id).flatMap(g.roles.get))

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
  ) extends ComplexReasonRequest[ModifyGuildRole[Ctx], ModifyGuildRoleData, RawRole, GatewayEvent.GuildRoleModifyData, Role, Ctx] {
    override def route:         RequestRoute                 = Routes.modifyGuildRole(roleId, guildId)
    override def paramsEncoder: Encoder[ModifyGuildRoleData] = deriveEncoder[ModifyGuildRoleData]

    override def responseDecoder: Decoder[RawRole]                               = Decoder[RawRole]
    override def cacheHandler:    CacheHandler[GatewayEvent.GuildRoleModifyData] = RawHandlers.roleUpdateHandler
    override def convertToCacheHandlerType(response: RawRole): GatewayEvent.GuildRoleModifyData =
      GatewayEvent.GuildRoleModifyData(guildId, response)

    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawRole)(cache: CacheState): Option[Role] =
      cache.current.getRole(guildId, roleId)

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

    override def requiredPermissions:                        Permission = Permission.ManageRoles
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

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

    override def responseDecoder: Decoder[GuildPruneResponse]      = deriveDecoder[GuildPruneResponse]
    override def cacheHandler:    CacheHandler[GuildPruneResponse] = NOOPHandler
  }

  /**
    * Check how many members would be removed if a prune was started now.
    */
  case class GetGuildPruneCount[Ctx](guildId: GuildId, params: GuildPruneData, context: Ctx = NotUsed: NotUsed)
      extends GuildPrune[Ctx] {
    override def route: RequestRoute = Routes.getGuildPruneCount(guildId)

    override def requiredPermissions:                        Permission = Permission.KickMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
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

    override def requiredPermissions:                        Permission = Permission.KickMembers
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)

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

    override def responseDecoder: Decoder[Seq[VoiceRegion]]      = Decoder[Seq[VoiceRegion]]
    override def cacheHandler:    CacheHandler[Seq[VoiceRegion]] = NOOPHandler
  }

  /**
    * Get the invites for this guild.
    */
  case class GetGuildInvites[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Seq[InviteWithMetadata], Ctx] {
    override def route: RequestRoute = Routes.getGuildInvites(guildId)

    override def responseDecoder: Decoder[Seq[InviteWithMetadata]]      = Decoder[Seq[InviteWithMetadata]]
    override def cacheHandler:    CacheHandler[Seq[InviteWithMetadata]] = NOOPHandler

    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get the integrations for this guild.
    */
  case class GetGuildIntegrations[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Seq[Integration], Ctx] {
    override def route: RequestRoute = Routes.getGuildIntegrations(guildId)

    override def responseDecoder: Decoder[Seq[Integration]]      = Decoder[Seq[Integration]]
    override def cacheHandler:    CacheHandler[Seq[Integration]] = NOOPHandler

    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
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

    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
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

    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Delete an integration.
    */
  case class DeleteGuildIntegration[Ctx](
      guildId: GuildId,
      integrationId: IntegrationId,
      context: Ctx = NotUsed: NotUsed
  ) extends NoParamsResponseRequest[Ctx] {
    override def route: RequestRoute = Routes.deleteGuildIntegration(integrationId, guildId)

    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Sync an integration.
    */
  case class SyncGuildIntegration[Ctx](guildId: GuildId, integrationId: IntegrationId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsResponseRequest[Ctx] {
    override def route: RequestRoute = Routes.syncGuildIntegration(integrationId, guildId)

    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get the guild embed for a guild.
    */
  case class GetGuildEmbed[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[GuildEmbed, Ctx] {
    override def route: RequestRoute = Routes.getGuildEmbed(guildId)

    override def responseDecoder: Decoder[GuildEmbed]      = Decoder[GuildEmbed]
    override def cacheHandler:    CacheHandler[GuildEmbed] = NOOPHandler

    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Modify a guild embed for a guild.
    */
  case class ModifyGuildEmbed[Ctx](guildId: GuildId, params: GuildEmbed, context: Ctx = NotUsed: NotUsed)
      extends NoNiceResponseRequest[GuildEmbed, GuildEmbed, Ctx] {
    override def route:         RequestRoute        = Routes.modifyGuildEmbed(guildId)
    override def paramsEncoder: Encoder[GuildEmbed] = Encoder[GuildEmbed]

    override def responseDecoder: Decoder[GuildEmbed]      = Decoder[GuildEmbed]
    override def cacheHandler:    CacheHandler[GuildEmbed] = NOOPHandler

    override def requiredPermissions:                        Permission = Permission.ManageGuild
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get an invite for a given invite code
    */
  case class GetInvite[Ctx](inviteCode: String, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Invite, Ctx] {
    override def route: RequestRoute = Routes.getInvite(inviteCode)

    override def responseDecoder: Decoder[Invite]      = Decoder[Invite]
    override def cacheHandler:    CacheHandler[Invite] = NOOPHandler
  }

  /**
    * Delete an invite.
    */
  case class DeleteInvite[Ctx](inviteCode: String, context: Ctx = NotUsed: NotUsed, reason: Option[String] = None)
      extends NoParamsNiceResponseReasonRequest[DeleteInvite[Ctx], Invite, Ctx] {
    override def route: RequestRoute = Routes.deleteInvite(inviteCode)

    override def responseDecoder: Decoder[Invite]      = Decoder[Invite]
    override def cacheHandler:    CacheHandler[Invite] = NOOPHandler

    override def requiredPermissions: Permission = Permission.ManageChannels

    override def withReason(reason: String): DeleteInvite[Ctx] = copy(reason = Some(reason))
  }

  /**
    * Fetch the client user.
    */
  case class GetCurrentUser[Ctx](context: Ctx = NotUsed: NotUsed) extends NoParamsNiceResponseRequest[User, Ctx] {
    override def route: RequestRoute = Routes.getCurrentUser

    override def responseDecoder: Decoder[User]      = Decoder[User]
    override def cacheHandler:    CacheHandler[User] = Handlers.botUserUpdateHandler
  }

  /**
    * Get a user by id.
    */
  case class GetUser[Ctx](userId: UserId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[User, Ctx] {
    override def route: RequestRoute = Routes.getUser(userId)

    override def responseDecoder: Decoder[User]      = Decoder[User]
    override def cacheHandler:    CacheHandler[User] = Handlers.userUpdateHandler
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
  case class GetCurrentUserGuilds[Ctx](params: GetCurrentUserGuildsData, context: Ctx = NotUsed: NotUsed)
      extends NoNiceResponseRequest[GetCurrentUserGuildsData, Seq[GetUserGuildsGuild], Ctx] {
    override def route:         RequestRoute                      = Routes.getCurrentUserGuilds
    override def paramsEncoder: Encoder[GetCurrentUserGuildsData] = deriveEncoder[GetCurrentUserGuildsData]

    override def responseDecoder: Decoder[Seq[GetUserGuildsGuild]] = {
      implicit val dec: Decoder[GetUserGuildsGuild] = deriveDecoder[GetUserGuildsGuild]
      Decoder[Seq[GetUserGuildsGuild]]
    }
    override def cacheHandler: CacheHandler[Seq[GetUserGuildsGuild]] = NOOPHandler
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
      extends NoParamsRequest[Seq[RawChannel], Seq[DMChannel], Ctx] {
    override def route: RequestRoute = Routes.getUserDMs

    override def responseDecoder: Decoder[Seq[RawChannel]] = Decoder[Seq[RawChannel]]
    override def cacheHandler: CacheHandler[Seq[RawChannel]] =
      CacheUpdateHandler.seqHandler(RawHandlers.rawChannelUpdateHandler)

    override def hasCustomResponseData: Boolean = true
    override def findData(response: Seq[RawChannel])(cache: CacheState): Option[Seq[DMChannel]] =
      Some(cache.current.dmChannels.values.toSeq)
  }

  /**
    * @param recipientId User to send a DM to.
    */
  case class CreateDMData(recipientId: UserId)

  /**
    * Create a new DM channel.
    */
  case class CreateDm[Ctx](params: CreateDMData, context: Ctx = NotUsed: NotUsed)
      extends SimpleRESTRequest[CreateDMData, RawChannel, DMChannel, Ctx] {
    override def route:         RequestRoute          = Routes.createDM
    override def paramsEncoder: Encoder[CreateDMData] = deriveEncoder[CreateDMData]

    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def cacheHandler:    CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawChannel)(cache: CacheState): Option[DMChannel] =
      cache.current.getDmChannel(response.id)
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
      extends SimpleRESTRequest[CreateGroupDMData, RawChannel, GroupDMChannel, Ctx] {
    override def route: RequestRoute = Routes.createDM
    override def paramsEncoder: Encoder[CreateGroupDMData] = (data: CreateGroupDMData) => {
      Json
        .obj("access_tokens" -> data.accessTokens.asJson, "nicks" -> data.nicks.map(t => t._1.asString -> t._2).asJson)
    }

    override def responseDecoder: Decoder[RawChannel]      = Decoder[RawChannel]
    override def cacheHandler:    CacheHandler[RawChannel] = RawHandlers.rawChannelUpdateHandler

    override def hasCustomResponseData: Boolean = true
    override def findData(response: RawChannel)(cache: CacheState): Option[GroupDMChannel] =
      cache.current.getGroupDmChannel(response.id)
  }

  /**
    * Get a list of connection objects. Requires the `connection` OAuth2 scope.
    */
  case class GetUserConnections[Ctx](context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Seq[Connection], Ctx] {
    override def route: RequestRoute = Routes.getUserConnections

    override def responseDecoder: Decoder[Seq[Connection]]      = Decoder[Seq[Connection]]
    override def cacheHandler:    CacheHandler[Seq[Connection]] = NOOPHandler
  }

  //Voice
  /**
    * List all the voice regions that can be used when creating a guild.
    */
  case class ListVoiceRegions[Ctx](context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Seq[VoiceRegion], Ctx] {
    override def route: RequestRoute = Routes.listVoiceRegions

    override def responseDecoder: Decoder[Seq[VoiceRegion]]      = Decoder[Seq[VoiceRegion]]
    override def cacheHandler:    CacheHandler[Seq[VoiceRegion]] = NOOPHandler
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
  case class CreateWebhook[Ctx](
      channelId: ChannelId,
      params: CreateWebhookData,
      context: Ctx = NotUsed: NotUsed,
      reason: Option[String] = None
  ) extends NoNiceResponseReasonRequest[CreateWebhook[Ctx], CreateWebhookData, Webhook, Ctx] {
    override def route:         RequestRoute               = Routes.createWebhook(channelId)
    override def paramsEncoder: Encoder[CreateWebhookData] = deriveEncoder[CreateWebhookData]

    override def responseDecoder: Decoder[Webhook]      = Decoder[Webhook]
    override def cacheHandler:    CacheHandler[Webhook] = NOOPHandler

    override def requiredPermissions: Permission = Permission.ManageWebhooks
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)

    override def withReason(reason: String): CreateWebhook[Ctx] = copy(reason = Some(reason))
  }

  /**
    * Get the webhooks in a channel.
    */
  case class GetChannelWebhooks[Ctx](channelId: ChannelId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Seq[Webhook], Ctx] {
    override def route: RequestRoute = Routes.getChannelWebhooks(channelId)

    override def responseDecoder: Decoder[Seq[Webhook]]      = Decoder[Seq[Webhook]]
    override def cacheHandler:    CacheHandler[Seq[Webhook]] = NOOPHandler

    override def requiredPermissions: Permission = Permission.ManageWebhooks
    override def havePermissions(implicit c: CacheSnapshot): Boolean =
      hasPermissionsChannel(channelId, requiredPermissions)
  }

  /**
    * Get the webhooks in a guild.
    */
  case class GetGuildWebhooks[Ctx](guildId: GuildId, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Seq[Webhook], Ctx] {
    override def route: RequestRoute = Routes.getGuildWebhooks(guildId)

    override def responseDecoder: Decoder[Seq[Webhook]]      = Decoder[Seq[Webhook]]
    override def cacheHandler:    CacheHandler[Seq[Webhook]] = NOOPHandler

    override def requiredPermissions:                        Permission = Permission.ManageWebhooks
    override def havePermissions(implicit c: CacheSnapshot): Boolean    = hasPermissionsGuild(guildId, requiredPermissions)
  }

  /**
    * Get a webhook by id.
    */
  case class GetWebhook[Ctx](id: SnowflakeType[Webhook], context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Webhook, Ctx] {
    override def route: RequestRoute = Routes.getWebhook(id)

    override def responseDecoder: Decoder[Webhook]      = Decoder[Webhook]
    override def cacheHandler:    CacheHandler[Webhook] = NOOPHandler

    override def requiredPermissions: Permission = Permission.ManageWebhooks
  }

  /**
    * Get a webhook by id with a token. Doesn't require authentication.
    */
  case class GetWebhookWithToken[Ctx](id: SnowflakeType[Webhook], token: String, context: Ctx = NotUsed: NotUsed)
      extends NoParamsNiceResponseRequest[Webhook, Ctx] {
    override def route: RequestRoute = Routes.getWebhookWithToken(token, id)

    override def responseDecoder: Decoder[Webhook]      = Decoder[Webhook]
    override def cacheHandler:    CacheHandler[Webhook] = NOOPHandler

    override def requiredPermissions: Permission = Permission.ManageWebhooks
  }

  /**
    * @param name Name of the webhook.
    * @param avatar The avatar data of the webhook.
    * @param channelId The channel this webhook should be moved to.
    */
  case class ModifyWebhookData(
      name: Option[String] = None,
      avatar: Option[ImageData] = None,
      channelId: Option[ChannelId] = None
  )

  /**
    * Modify a webhook.
    */
  case class ModifyWebhook[Ctx](
      id: SnowflakeType[Webhook],
      params: ModifyWebhookData,
      context: Ctx = NotUsed: NotUsed,
      reason: Option[String] = None
  ) extends NoNiceResponseReasonRequest[ModifyWebhook[Ctx], ModifyWebhookData, Webhook, Ctx] {
    override def route:         RequestRoute               = Routes.getWebhook(id)
    override def paramsEncoder: Encoder[ModifyWebhookData] = deriveEncoder[ModifyWebhookData]

    override def responseDecoder: Decoder[Webhook]      = Decoder[Webhook]
    override def cacheHandler:    CacheHandler[Webhook] = NOOPHandler

    override def requiredPermissions:        Permission         = Permission.ManageWebhooks
    override def withReason(reason: String): ModifyWebhook[Ctx] = copy(reason = Some(reason))
  }

  /**
    * Modify a webhook with a token. Doesn't require authentication
    */
  case class ModifyWebhookWithToken[Ctx](
      id: SnowflakeType[Webhook],
      token: String,
      params: ModifyWebhookData,
      context: Ctx = NotUsed: NotUsed,
      reason: Option[String] = None
  ) extends NoNiceResponseReasonRequest[ModifyWebhookWithToken[Ctx], ModifyWebhookData, Webhook, Ctx] {
    require(params.channelId.isEmpty, "ModifyWebhookWithToken does not accept a channelId in the request")
    override def route: RequestRoute = Routes.getWebhookWithToken(token, id)

    override def paramsEncoder:   Encoder[ModifyWebhookData] = deriveEncoder[ModifyWebhookData]
    override def responseDecoder: Decoder[Webhook]           = Decoder[Webhook]

    override def cacheHandler:        CacheHandler[Webhook] = NOOPHandler
    override def requiredPermissions: Permission            = Permission.ManageWebhooks

    override def withReason(reason: String): ModifyWebhookWithToken[Ctx] = copy(reason = Some(reason))
  }

  /**
    * Delete a webhook.
    */
  case class DeleteWebhook[Ctx](
      id: SnowflakeType[Webhook],
      context: Ctx = NotUsed: NotUsed,
      reason: Option[String] = None
  ) extends NoParamsResponseReasonRequest[DeleteWebhook[Ctx], Ctx] {
    override def route: RequestRoute = Routes.deleteWebhook(id)

    override def requiredPermissions: Permission = Permission.ManageWebhooks

    override def withReason(reason: String): DeleteWebhook[Ctx] = copy(reason = Some(reason))
  }

  /**
    * Delete a webhook with a token. Doesn't require authentication
    */
  case class DeleteWebhookWithToken[Ctx](
      id: SnowflakeType[Webhook],
      token: String,
      context: Ctx = NotUsed: NotUsed,
      reason: Option[String] = None
  ) extends NoParamsResponseReasonRequest[DeleteWebhookWithToken[Ctx], Ctx] {
    override def route: RequestRoute = Routes.deleteWebhookWithToken(token, id)

    override def requiredPermissions: Permission = Permission.ManageWebhooks

    override def withReason(reason: String): DeleteWebhookWithToken[Ctx] = copy(reason = Some(reason))
  }

  /*
  TODO
  case class ExecuteWebhook[Ctx](id: Snowflake, token: String, params: Nothing, context: Ctx = NotUsed: NotUsed) extends SimpleRESTRequest[Nothing, Nothing, Ctx] {
    override def route: RestRoute = Routes.deleteWebhookWithToken(token, id)
  }
 */
}
