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
package net.katsstuff.akkacord.http.websocket

import java.time.{LocalDateTime, OffsetDateTime}

import akka.NotUsed
import net.katsstuff.akkacord.http._
import spray.json._
import net.katsstuff.akkacord.data.{Attachment, Embed, Reaction, Snowflake, User, VoiceState, WebhookAuthor}
import net.katsstuff.akkacord.http.websocket.WsEvent.RawOptionalMessage

object WsProtocol extends DiscordProtocol {

  implicit object EventFormat extends JsonFormat[WsEvent] {
    override def read(json: JsValue): WsEvent   = WsEvent.forName(json.convertTo[String]).getOrElse(throw DeserializationException("Invalid event"))
    override def write(obj: WsEvent):   JsValue = JsString(obj.name)
  }

  implicit object OpCodeFormat extends JsonFormat[OpCode] {
    override def read(json: JsValue): OpCode  = OpCode.forCode(json.convertTo[Int]).getOrElse(throw DeserializationException("Invalid opCode"))
    override def write(obj: OpCode):  JsValue = JsNumber(obj.code)
  }

  implicit val ReadyDataFormat:                   RootJsonFormat[WsEvent.ReadyData]                   = jsonFormat6(WsEvent.ReadyData)
  implicit val ResumedDataFormat:                 RootJsonFormat[WsEvent.ResumedData]                 = jsonFormat1(WsEvent.ResumedData)
  implicit val GuildDeleteDataFormat:             RootJsonFormat[WsEvent.GuildDeleteData]             = jsonFormat2(WsEvent.GuildDeleteData)
  implicit val GuildUserFormat:                   RootJsonFormat[WsEvent.GuildUser]                   = jsonFormat9(WsEvent.GuildUser)
  implicit val GuildEmojiUpdateDataFormat:        RootJsonFormat[WsEvent.GuildEmojisUpdateData]       = jsonFormat2(WsEvent.GuildEmojisUpdateData)
  implicit val GuildIntegrationsUpdateDataFormat: RootJsonFormat[WsEvent.GuildIntegrationsUpdateData] = jsonFormat1(WsEvent.GuildIntegrationsUpdateData)
  implicit val RawGuildMemberWithGuildFormat:     RootJsonFormat[WsEvent.RawGuildMemberWithGuild]     = jsonFormat7(WsEvent.RawGuildMemberWithGuild)
  implicit val GuildMemberRemoveDataFormat:       RootJsonFormat[WsEvent.GuildMemberRemoveData]       = jsonFormat2(WsEvent.GuildMemberRemoveData)
  implicit val GuildMemberUpdateDataFormat:       RootJsonFormat[WsEvent.GuildMemberUpdateData]       = jsonFormat4(WsEvent.GuildMemberUpdateData)
  implicit val GuildMemberChunkDataFormat:        RootJsonFormat[WsEvent.GuildMemberChunkData]        = jsonFormat2(WsEvent.GuildMemberChunkData)
  implicit val GuildRoleModifyDataFormat:         RootJsonFormat[WsEvent.GuildRoleModifyData]         = jsonFormat2(WsEvent.GuildRoleModifyData)
  implicit val GuildRoleDeleteDataFormat:         RootJsonFormat[WsEvent.GuildRoleDeleteData]         = jsonFormat2(WsEvent.GuildRoleDeleteData)
  implicit val MessageDeleteDataFormat:           RootJsonFormat[WsEvent.MessageDeleteData]           = jsonFormat2(WsEvent.MessageDeleteData)
  implicit val MessageDeleteBulkDataFormat:       RootJsonFormat[WsEvent.MessageDeleteBulkData]       = jsonFormat2(WsEvent.MessageDeleteBulkData)
  implicit val TypingStartDataFormat:             RootJsonFormat[WsEvent.TypingStartData]             = jsonFormat3(WsEvent.TypingStartData)

  implicit object RawOptionalMessageFormat extends RootJsonFormat[WsEvent.RawOptionalMessage] {
    override def read(json: JsValue): RawOptionalMessage = {
      val fields = json.asJsObject.fields
      val isWebhook = fields.contains("webhook_id")

      RawOptionalMessage(
        id = fields("id").convertTo[Snowflake],
        channelId = fields("channel_id").convertTo[Snowflake],
        author = if (isWebhook) fields.get("author").flatMap(_.convertTo[Option[WebhookAuthor]]) else fields.get("author").flatMap(_.convertTo[Option[User]]),
        content =         fields.get("content").flatMap(_.convertTo[Option[String]]),
        timestamp =       fields.get("timestamp").flatMap(_.convertTo[Option[OffsetDateTime]]),
        editedTimestamp = fields.get("edited_timestamp").flatMap(_.convertTo[Option[OffsetDateTime]]),
        tts =             fields.get("tts").flatMap(_.convertTo[Option[Boolean]]),
        mentionEveryone = fields.get("mention_everyone").flatMap(_.convertTo[Option[Boolean]]),
        mentions =        fields.get("mentions").flatMap(_.convertTo[Option[Seq[User]]]),
        mentionRoles =    fields.get("mention_roles").flatMap(_.convertTo[Option[Seq[Snowflake]]]),
        attachment =      fields.get("attachments").flatMap(_.convertTo[Option[Seq[Attachment]]]),
        embeds =          fields.get("embeds").flatMap(_.convertTo[Option[Seq[Embed]]]),
        reactions =       fields.get("reactions").flatMap(_.convertTo[Option[Seq[Reaction]]]),
        nonce =           fields.get("nonce").flatMap(_.convertTo[Option[Snowflake]]),
        pinned =          fields.get("pinned").flatMap(_.convertTo[Option[Boolean]]),
        webhookId =       fields.get("webhook_id").flatMap(_.convertTo[Option[String]])
      )
    }
    override def write(obj: RawOptionalMessage): JsValue = {
      val base = Seq(
        "id"               -> obj.id.toJson,
        "channel_id"       -> obj.channelId.toJson,
        "content"          -> obj.content.toJson,
        "timestamp"        -> obj.timestamp.toJson,
        "edited_timestamp" -> obj.editedTimestamp.toJson,
        "tts"              -> obj.tts.toJson,
        "mention_everyone" -> obj.mentionEveryone.toJson,
        "mentions"         -> obj.mentions.toJson,
        "mention_roles"    -> obj.mentionRoles.toJson,
        "attachments"      -> obj.attachment.toJson,
        "embeds"           -> obj.embeds.toJson,
        "reactions"        -> obj.reactions.toJson,
        "nonce"            -> obj.nonce.toJson,
        "pinned"           -> obj.pinned.toJson,
        "webhook_id"       -> obj.webhookId.toJson
      )

      obj.author match {
        case Some(user:    User)          => JsObject(base :+ "author" -> user.toJson:    _*)
        case Some(webhook: WebhookAuthor) => JsObject(base :+ "author" -> webhook.toJson: _*)
        case None => JsObject(base: _*)
      }
    }
  }

  implicit val IdentifyObjectFormat:          RootJsonFormat[IdentifyObject]          = jsonFormat5(IdentifyObject.apply)
  implicit val GameFormat:                    RootJsonFormat[Game]                    = jsonFormat1(Game)
  implicit val StatusDataFormat:              RootJsonFormat[StatusData]              = jsonFormat2(StatusData)
  implicit val VoiceStatusDataFormat:         RootJsonFormat[VoiceStatusData]         = jsonFormat4(VoiceStatusData)
  implicit val VoiceServerUpdateDataFormat:   RootJsonFormat[VoiceServerUpdateData]   = jsonFormat3(VoiceServerUpdateData)
  implicit val ResumeDataFormat:              RootJsonFormat[ResumeData]              = jsonFormat3(ResumeData)
  implicit val RequestGuildMembersDataFormat: RootJsonFormat[RequestGuildMembersData] = jsonFormat3(RequestGuildMembersData)
  implicit val HelloDataFormat:               RootJsonFormat[HelloData]               = jsonFormat2(HelloData)

  implicit object wsMessageReader extends RootJsonReader[WsMessage[_]] {
    override def read(json: JsValue): WsMessage[_] = {
      val obj = json.asJsObject
      val d   = obj.fields("d")
      obj.fields("op").convertTo[OpCode] match {
        case OpCode.Dispatch =>
          val seq      = obj.fields("s").convertTo[Int]
          val event    = obj.fields("t").convertTo[WsEvent]
          val auxEvent = event.asInstanceOf[WsEvent.Aux[AnyRef]]
          val data = event match {
            case WsEvent.Ready                   => d.convertTo[WsEvent.ReadyData]
            case WsEvent.Resumed                 => d.convertTo[WsEvent.ResumedData]
            case WsEvent.ChannelCreate           => d.convertTo[RawChannel]
            case WsEvent.ChannelUpdate           => d.convertTo[RawGuildChannel]
            case WsEvent.ChannelDelete           => d.convertTo[RawChannel]
            case WsEvent.GuildCreate             => d.convertTo[RawGuild]
            case WsEvent.GuildUpdate             => d.convertTo[RawGuild]
            case WsEvent.GuildDelete             => d.convertTo[WsEvent.GuildDeleteData]
            case WsEvent.GuildBanAdd             => d.convertTo[WsEvent.GuildUser]
            case WsEvent.GuildBanRemove          => d.convertTo[WsEvent.GuildUser]
            case WsEvent.GuildEmojisUpdate       => d.convertTo[WsEvent.GuildEmojisUpdateData]
            case WsEvent.GuildIntegrationsUpdate => d.convertTo[WsEvent.GuildIntegrationsUpdateData]
            case WsEvent.GuildMemberAdd          => d.convertTo[WsEvent.RawGuildMemberWithGuild]
            case WsEvent.GuildMemberRemove       => d.convertTo[WsEvent.GuildMemberRemoveData]
            case WsEvent.GuildMemberUpdate       => d.convertTo[WsEvent.GuildMemberUpdateData]
            case WsEvent.GuildMemberChunk        => d.convertTo[WsEvent.GuildMemberChunkData]
            case WsEvent.GuildRoleCreate         => d.convertTo[WsEvent.GuildRoleModifyData]
            case WsEvent.GuildRoleUpdate         => d.convertTo[WsEvent.GuildRoleModifyData]
            case WsEvent.GuildRoleDelete         => d.convertTo[WsEvent.GuildRoleDeleteData]
            case WsEvent.MessageCreate           => d.convertTo[RawMessage]
            case WsEvent.MessageUpdate           => d.convertTo[WsEvent.RawOptionalMessage]
            case WsEvent.MessageDelete           => d.convertTo[WsEvent.MessageDeleteData]
            case WsEvent.MessageDeleteBulk       => d.convertTo[WsEvent.MessageDeleteBulkData]
            //case Event.PresenceUpdate =>
            case WsEvent.TypingStart => d.convertTo[WsEvent.TypingStartData]
            //case Event.UserSettingsUpdate =>
            case WsEvent.UserUpdate        => d.convertTo[User]
            case WsEvent.VoiceStateUpdate  => d.convertTo[VoiceState]
            case WsEvent.VoiceServerUpdate => d.convertTo[VoiceServerUpdateData]
            case _ => throw DeserializationException("Illegal event")
          }

          Dispatch(seq, auxEvent, data)
        case OpCode.Heartbeat           => Heartbeat(d.convertTo[Option[Int]])
        case OpCode.Identify            => Identify(d.convertTo[IdentifyObject])
        case OpCode.StatusUpdate        => StatusUpdate(d.convertTo[StatusData])
        case OpCode.VoiceStateUpdate    => VoiceStateUpdate(d.convertTo[VoiceStatusData])
        case OpCode.VoiceServerPing     => VoiceServerUpdate(d.convertTo[VoiceServerUpdateData])
        case OpCode.Resume              => Resume(d.convertTo[ResumeData])
        case OpCode.Reconnect           => Reconnect(NotUsed)
        case OpCode.RequestGuildMembers => RequestGuildMembers(d.convertTo[RequestGuildMembersData])
        case OpCode.InvalidSession      => InvalidSession(NotUsed)
        case OpCode.Hello               => Hello(d.convertTo[HelloData])
        case OpCode.HeartbeatACK        => HeartbeatACK(NotUsed)
        case _ => throw DeserializationException("Illegal OpCode")
      }

    }

  }

  implicit def wsMessageWriter[Data: JsonFormat]: RootJsonWriter[WsMessage[Data]] = new RootJsonWriter[WsMessage[Data]] {
    override def write(obj: WsMessage[Data]) = JsObject("d" -> obj.d.toJson, "op" -> obj.op.toJson, "s" -> obj.s.toJson, "t" -> obj.t.toJson)
  }
}
