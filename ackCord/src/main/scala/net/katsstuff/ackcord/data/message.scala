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
package net.katsstuff.ackcord.data

import java.time.OffsetDateTime
import java.util.Base64

//TODO
class ImageData(val rawData: String) extends AnyVal
object ImageData {

  def forData(imageType: String, data: Array[Byte]): ImageData = {
    val base64Data = Base64.getEncoder.encodeToString(data)
    new ImageData(s"data:image/$imageType;base64,$base64Data")
  }
}

trait MessageType
object MessageType {
  object Default              extends MessageType
  object RecipientAdd         extends MessageType
  object RecipientRemove      extends MessageType
  object Call                 extends MessageType
  object ChannelNameChange    extends MessageType
  object ChannelIconChange    extends MessageType
  object ChannelPinnedMessage extends MessageType
  object GuildMemberJoin      extends MessageType

  def forId(id: Int): Option[MessageType] = id match {
    case 0 => Some(Default)
    case 1 => Some(RecipientAdd)
    case 2 => Some(RecipientRemove)
    case 3 => Some(Call)
    case 4 => Some(ChannelNameChange)
    case 5 => Some(ChannelIconChange)
    case 6 => Some(ChannelPinnedMessage)
    case 7 => Some(GuildMemberJoin)
    case _ => None
  }

  def idFor(tpe: MessageType): Int = tpe match {
    case Default              => 0
    case RecipientAdd         => 1
    case RecipientRemove      => 2
    case Call                 => 3
    case ChannelNameChange    => 4
    case ChannelIconChange    => 5
    case ChannelPinnedMessage => 6
    case GuildMemberJoin      => 7
  }
}

sealed trait Author
case class WebhookAuthor(id: Snowflake, name: String, avatar: String) extends Author
//Remember to edit PartialUser when editing this
case class User(
    id: UserId,
    username: String,
    discriminator: String,
    avatar: Option[String], //avatar can be null
    bot: Option[Boolean], //Bot can be missing
    mfaEnabled: Option[Boolean], //mfaEnabled can be missing
    verified: Option[Boolean], //verified can be missing
    email: Option[String] //Email can be null
) extends Author

case class Connection(
    id: String,
    name: String,
    `type`: String,
    revoked: Boolean,
    integrations: Seq[Integration] //TODO: Partial
)

case class Message(
    id: MessageId,
    channelId: ChannelId,
    author: Author, //TODO: Factor webhook messages and normal messages into separate classes, to remove a reference to a user
    content: String,
    timestamp: OffsetDateTime,
    editedTimestamp: Option[OffsetDateTime],
    tts: Boolean,
    mentionEveryone: Boolean,
    mentions: Seq[UserId],
    mentionRoles: Seq[RoleId],
    attachment: Seq[Attachment],
    embeds: Seq[ReceivedEmbed],
    reactions: Seq[Reaction],
    nonce: Option[Snowflake],
    pinned: Boolean,
    webhookId: Option[String],
    messageType: MessageType
) extends GetChannel

case class Reaction(count: Int, me: Boolean, emoji: MessageEmoji)
case class MessageEmoji(id: Option[EmojiId], name: String) //TODO: Change to partial GuildEmoji

//TODO: Why all options here?
case class ReceivedEmbed(
    title: Option[String],
    `type`: Option[String],
    description: Option[String],
    url: Option[String],
    timestamp: Option[OffsetDateTime],
    color: Option[Int],
    footer: Option[ReceivedEmbedFooter],
    image: Option[ReceivedEmbedImage],
    thumbnail: Option[ReceivedEmbedThumbnail],
    video: Option[EmbedVideo],
    provider: Option[EmbedProvider],
    author: Option[ReceivedEmbedAuthor],
    fields: Option[Seq[EmbedField]]
) {

  def toOutgoing: OutgoingEmbed = OutgoingEmbed(
    title = title,
    description = description,
    url = url,
    timestamp = timestamp,
    color = color,
    footer = footer.map(_.toOutgoing),
    image = image.map(_.toOutgoing),
    thumbnail = thumbnail.map(_.toOutgoing),
    author = author.map(_.toOutgoing),
    fields = fields.getOrElse(Seq.empty)
  )
}

case class ReceivedEmbedThumbnail(url: String, proxyUrl: Option[String], height: Option[Int], width: Option[Int]) {

  def toOutgoing: OutgoingEmbedThumbnail = OutgoingEmbedThumbnail(url)
}
case class EmbedVideo(url: Option[String], height: Option[Int], width: Option[Int])
case class ReceivedEmbedImage(url: String, proxyUrl: Option[String], height: Option[Int], width: Option[Int]) {
  def toOutgoing: OutgoingEmbedImage = OutgoingEmbedImage(url)
}
case class EmbedProvider(name: Option[String], url: Option[String])
case class ReceivedEmbedAuthor(
    name: String,
    url: Option[String],
    iconUrl: Option[String],
    proxyIconUrl: Option[String]
) {

  def toOutgoing: OutgoingEmbedAuthor = OutgoingEmbedAuthor(name, url, iconUrl)
}
case class ReceivedEmbedFooter(text: String, iconUrl: Option[String], proxyIconUrl: Option[String]) {
  def toOutgoing: OutgoingEmbedFooter = OutgoingEmbedFooter(text, iconUrl)
}

case class EmbedField(name: String, value: String, inline: Option[Boolean] = None)

case class Attachment(
    id: Snowflake,
    filename: String,
    size: Int,
    url: String,
    proxyUrl: String,
    height: Option[Int],
    width: Option[Int]
)

//TODO: Why all options here?
case class OutgoingEmbed(
    title: Option[String] = None,
    description: Option[String] = None,
    url: Option[String] = None,
    timestamp: Option[OffsetDateTime] = None,
    color: Option[Int] = None,
    footer: Option[OutgoingEmbedFooter] = None,
    image: Option[OutgoingEmbedImage] = None,
    thumbnail: Option[OutgoingEmbedThumbnail] = None,
    author: Option[OutgoingEmbedAuthor] = None,
    fields: Seq[EmbedField] = Seq.empty
)

case class OutgoingEmbedThumbnail(url: String)
case class OutgoingEmbedImage(url: String)
case class OutgoingEmbedAuthor(name: String, url: Option[String] = None, iconUrl: Option[String] = None)
case class OutgoingEmbedFooter(text: String, iconUrl: Option[String] = None)
