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
package net.katsstuff.akkacord

import java.nio.file.Path

import akka.NotUsed
import net.katsstuff.akkacord.data._
import net.katsstuff.akkacord.http.rest.RESTRequest._

package object syntax {

  implicit class ChannelSyntax(val channel: Channel) extends AnyVal {
    def delete[Context](context: Context = NotUsed) = Request(DeleteCloseChannel(channel.id), context)
  }

  implicit class TChannelSyntax(val tChannel: TChannel) extends AnyVal {
    def sendMessage[Context](
        content: String,
        tts:     Boolean = false,
        file:    Option[Path] = None,
        embed:   Option[OutgoingEmbed] = None,
        context: Context = NotUsed
    ) = Request(CreateMessage(tChannel.id, CreateMessageData(content, None, tts, file, embed)), NotUsed)

    def fetchMessagesAround[Context](around: Snowflake, limit: Option[Int] = Some(50), context: Context = NotUsed) =
      Request(GetChannelMessages(tChannel.id, GetChannelMessagesData(Some(around), None, None, limit)), context)
    def fetchMessagesBefore[Context](before: Snowflake, limit: Option[Int] = Some(50), context: Context = NotUsed) =
      Request(GetChannelMessages(tChannel.id, GetChannelMessagesData(None, Some(before), None, limit)), context)
    def fetchMessagesAfter[Context](after: Snowflake, limit: Option[Int] = Some(50), context: Context = NotUsed) =
      Request(GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, Some(after), limit)), context)
    def fetchMessages[Context](limit: Option[Int] = Some(50), context: Context = NotUsed) =
      Request(GetChannelMessages(tChannel.id, GetChannelMessagesData(None, None, None, limit)), context)

    def fetchMessage[Context](id: Snowflake, context: Context = NotUsed) = Request(GetChannelMessage(tChannel.id, id), context)

    def bulkDelete[Context](ids: Seq[Snowflake], context: Context = NotUsed) =
      Request(BulkDeleteMessages(tChannel.id, BulkDeleteMessagesData(ids)), context)

    def editChannelPermissions[Context](role: Role, allow: Permission, deny: Permission, context: Context = NotUsed) =
      Request(EditChannelPermissions(tChannel.id, role.id, EditChannelPermissionsData(allow, deny, "role")), context)

    def deleteChannelPermissions[Context](user: User, context: Context = NotUsed) = Request(DeleteChannelPermission(tChannel.id, user.id), context)

    def triggerTyping[Context](context: Context = NotUsed) = Request(TriggerTypingIndicator(tChannel.id), context)

    def fetchPinnedMessages[Context](context: Context = NotUsed) = Request(GetPinnedMessages(tChannel.id), context)
  }

  implicit class TGuildChannelSyntax(val channel: TGuildChannel) extends AnyVal {
    def modify[Context](
        name:     String = channel.name,
        position: Int = channel.position,
        topic:    Option[String] = channel.topic,
        context:  Context = NotUsed
    ) = Request(ModifyChannel(channel.id, ModifyChannelData(name, position, topic, None, None)), context)
  }

  implicit class VGuildChannelSyntax(val channel: VGuildChannel) extends AnyVal {
    def modify[Context](
        name:      String = channel.name,
        position:  Int = channel.position,
        bitrate:   Int = channel.bitrate,
        userLimit: Int = channel.userLimit,
        context:   Context = NotUsed
    ) = Request(ModifyChannel(channel.id, ModifyChannelData(name, position, None, Some(bitrate), Some(userLimit))), context)
  }

  implicit class GuildSyntax(val guild: Guild) extends AnyVal {
    def rolesForUser(userId: Snowflake): Seq[Role] = guild.members.get(userId).map(_.roles.flatMap(guild.roles.get)).toSeq.flatten

    def tChannels: Seq[TGuildChannel] =
      guild.channels.values.collect {
        case tChannel: TGuildChannel => tChannel
      }.toSeq

    def vChannels: Seq[VGuildChannel] =
      guild.channels.values.collect {
        case tChannel: VGuildChannel => tChannel
      }.toSeq

    def channelById(id:  Snowflake): Option[GuildChannel]  = guild.channels.get(id)
    def tChannelById(id: Snowflake): Option[TGuildChannel] = channelById(id).collect { case tChannel: TGuildChannel => tChannel }
    def vChannelById(id: Snowflake): Option[VGuildChannel] = channelById(id).collect { case vChannel: VGuildChannel => vChannel }

    def channelsByName(name:  String): Seq[GuildChannel]  = guild.channels.values.filter(_.name == name).toSeq
    def tChannelsByName(name: String): Seq[TGuildChannel] = tChannels.filter(_.name == name)
    def vChannelsByName(name: String): Seq[VGuildChannel] = vChannels.filter(_.name == name)

    def afkChannel: Option[VGuildChannel] = guild.afkChannelId.flatMap(vChannelById)

    def roleById(id:      Snowflake): Option[Role] = guild.roles.get(id)
    def rolesByName(name: String):    Seq[Role]    = guild.roles.values.filter(_.name == name).toSeq

    def emojiById(id:      Snowflake): Option[GuildEmoji] = guild.emojis.get(id)
    def emojisByName(name: String):    Seq[GuildEmoji]    = guild.emojis.values.filter(_.name == name).toSeq

    def memberById(id:       Snowflake): Option[GuildMember] = guild.members.get(id)
    def memberFromUser(user: User):      Option[GuildMember] = memberById(user.id)

    def presenceById(id:      Snowflake): Option[Presence] = guild.presences.get(id)
    def presenceForUser(user: User):      Option[Presence] = presenceById(user.id)
  }

  implicit class GuildEmojiSyntax(val emoji: GuildEmoji) extends AnyVal {
    def asString: String =
      if (emoji.managed) ??? else s"${emoji.name}:${emoji.id}"
  }

  implicit class MessageSyntax(val message: Message) extends AnyVal {
    def createReaction[Context](guildEmoji: GuildEmoji, context: Context = NotUsed) =
      Request(CreateReaction(message.channelId, message.id, guildEmoji.asString), context)

    def deleteOwnReaction[Context](guildEmoji: GuildEmoji, context: Context = NotUsed) =
      Request(DeleteOwnReaction(message.channelId, message.id, guildEmoji.asString), context)

    def deleteUserReaction[Context](guildEmoji: GuildEmoji, userId: Snowflake, context: Context = NotUsed) =
      Request(DeleteUserReaction(message.channelId, message.id, guildEmoji.asString, userId), context)

    def fetchReactions[Context](guildEmoji: GuildEmoji, context: Context = NotUsed) =
      Request(GetReactions(message.channelId, message.id, guildEmoji.asString), context)

    def deleteAllReactions[Context](context: Context = NotUsed) =
      Request(DeleteAllReactions(message.channelId, message.id), context)

    def edit[Context](
        content: Option[String] = Some(message.content),
        embed:   Option[OutgoingEmbed] = message.embeds.headOption.map(_.toOutgoing),
        context: Context = NotUsed
    ) = Request(EditMessage(message.channelId, message.id, EditMessageData(content, embed)))

    def delete[Context](context: Context = NotUsed) = Request(DeleteMessage(message.channelId, message.id), context)

    def addPinnedMessages[Context](context:    Context = NotUsed) = Request(AddPinnedChannelMessages(message.channelId, message.id), context)
    def removePinnedMessages[Context](context: Context = NotUsed) = Request(DeletePinnedChannelMessages(message.channelId, message.id), context)
  }
}
