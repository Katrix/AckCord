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
package net.katsstuff.ackcord.data

import java.time.OffsetDateTime
import java.util.Base64

import scala.util.Try

//TODO
class ImageData(val rawData: String) extends AnyVal
object ImageData {

  def forData(imageType: String, data: Array[Byte]): ImageData = {
    val base64Data = Base64.getEncoder.encodeToString(data)
    new ImageData(s"data:image/$imageType;base64,$base64Data")
  }
}

/**
  * An enum of all the different message types.
  */
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

/**
  * A author of a message. While a message is normally sent by a [[User]],
  * it can also be sent by a [[WebhookAuthor]].
  */
sealed trait Author[A] {

  /**
    * The id for this author.
    */
  def id: SnowflakeType[A]

  /**
    * If this author is not a webhook.
    */
  def isUser: Boolean
}

/**
  * An webhook author
  * @param id The webhook id
  * @param name The name of the webhook
  * @param avatar The webhook's avatar hash
  */
case class WebhookAuthor(id: SnowflakeType[Webhook], name: String, avatar: String) extends Author[Webhook] {
  override def isUser: Boolean = false
}

/**
  * A Discord user.
  * @param id The id of the user.
  * @param username The name of the user.
  * @param discriminator The discriminator for the user. Those four last
  *                      digits when clicking in a users name.
  * @param avatar The users avatar hash.
  * @param bot If this user belongs to a OAuth2 application.
  * @param mfaEnabled If this user has two factor authentication enabled.
  * @param verified If this user email is verified. Requires the email OAuth scope.
  * @param email The users email. Requires the email OAuth scope.
  */
//Remember to edit PartialUser when editing this
case class User(
    id: UserId,
    username: String,
    discriminator: String,
    avatar: Option[String], //avatar can be null
    bot: Option[Boolean],
    mfaEnabled: Option[Boolean],
    verified: Option[Boolean],
    email: Option[String]
) extends Author[User] {

  /**
    * Mention this user.
    */
  def mention: String = s"<@$id>"

  /**
    * Mention this user with their nickname.
    */
  def mentionNick: String = s"<@!$id>"

  override def isUser: Boolean = true
}

/**
  * A connection that a user has attached.
  * @param id The id of the connection.
  * @param name The name of the connection.
  * @param `type` The connection type (twitch, youtube).
  * @param revoked If the connection has been revoked.
  * @param integrations Integrations of the connection.
  */
case class Connection(
    id: String,
    name: String,
    `type`: String,
    revoked: Boolean,
    integrations: Seq[Integration] //TODO: Partial
)

/**
  * A message sent to a channel.
  * @param id The id of the message.
  * @param channelId The channel this message was sent to.
  * @param authorId The id of the author that sent this message.
  * @param isAuthorUser If the author of this message was a user.
  * @param content The content of this message.
  * @param timestamp The timestamp this message was created.
  * @param editedTimestamp The timestamp this message was last edited.
  * @param tts If this message is has text-to-speech enabled.
  * @param mentionEveryone If this message mentions everyone.
  * @param mentions All the users this message mentions.
  * @param mentionRoles All the roles this message mentions.
  * @param attachment All the attachments of this message.
  * @param embeds All the embeds of this message.
  * @param reactions All the reactions on this message.
  * @param nonce A nonce for this message.
  * @param pinned If this message is pinned.
  * @param messageType The message type
  */
case class Message(
    id: MessageId,
    channelId: ChannelId,
    authorId: RawSnowflake,
    isAuthorUser: Boolean,
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
    nonce: Option[RawSnowflake],
    pinned: Boolean,
    messageType: MessageType
) extends GetTChannel {

  def authorUserId: Option[UserId] = if(isAuthorUser) Some(UserId(authorId)) else None

  private val channelRegex = """<#(\d+)>""".r

  def channelMentions: Seq[ChannelId] = {
    channelRegex
      .findAllMatchIn(content)
      .flatMap { m =>
        Try {
          ChannelId(RawSnowflake(m.group(1)))
        }.toOption
      }
      .toSeq
  }

  /**
    * Formats mentions in this message to their normal syntax with names.
    */
  def formatMentions(implicit c: CacheSnapshot): String = {
    val withUsers = mentions
      .flatMap(_.resolve)
      .foldRight(content)((user, content) => content.replace(user.mention, s"@${user.username}"))
    val withRoles = mentionRoles
      .flatMap(_.resolve)
      .foldRight(withUsers)((role, content) => content.replace(role.mention, s"@${role.name}"))

    val optGuildId = channelId.resolve.collect {
      case channel: GuildChannel => channel.guildId
    }

    optGuildId.fold(withRoles) { guildId =>
      val withChannels = channelMentions
        .flatMap(_.guildResolve(guildId))
        .foldRight(withRoles)((channel, content) => content.replace(channel.mention, s"@${channel.name}"))

      withChannels
    }
  }
}

/**
  * A reaction to a message
  * @param count The amount of people that have reacted with this emoji.
  * @param me If the client has reacted with this emoji.
  * @param emoji The emoji of the reaction.
  */
case class Reaction(count: Int, me: Boolean, emoji: PartialEmoji)

/**
  * A partial emoji found in reactions
  * @param id The id of the emoji. If it's absent, it's not a guild emoji.
  * @param name The name of the emoji.
  */
case class PartialEmoji(id: Option[EmojiId], name: String)

/**
  * A received embed.
  * @param title The title of the embed.
  * @param `type` The embed type. Probably rich.
  * @param description The embed description or main text.
  * @param url The url of the embed.
  * @param timestamp The timestamp of the embed.
  * @param color The color of the embed
  * @param footer The footer part of the embed.
  * @param image The image part of the embed.
  * @param thumbnail The thumbnail part of the embed.
  * @param video The video part of the embed.
  * @param provider The provider part of the embed.
  * @param author The author part of the embed.
  * @param fields The fields of the embed.
  */
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
    video: Option[ReceivedEmbedVideo],
    provider: Option[ReceivedEmbedProvider],
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

/**
  * The thumbnail part of a received embed.
  * @param url The url to the thumbnail.
  * @param proxyUrl The proxy url of the thumbnail.
  * @param height The height of the thumbnail.
  * @param width The width of the thumbnail.
  */
case class ReceivedEmbedThumbnail(url: String, proxyUrl: String, height: Int, width: Int) {

  def toOutgoing: OutgoingEmbedThumbnail = OutgoingEmbedThumbnail(url)
}

/**
  * The video part of a received embed.
  * @param url The url of the video.
  * @param height The height of the video.
  * @param width The width of the video.
  */
case class ReceivedEmbedVideo(url: String, height: Int, width: Int)

/**
  * The image part of a received embed.
  * @param url The url of the image.
  * @param proxyUrl The proxy url of the image.
  * @param height The height of the image.
  * @param width The width of the image.
  */
case class ReceivedEmbedImage(url: String, proxyUrl: String, height: Int, width: Int) {
  def toOutgoing: OutgoingEmbedImage = OutgoingEmbedImage(url)
}

/**
  * The provider part of a received embed.
  * @param name The name of the provider.
  * @param url The url of a provider.
  */
case class ReceivedEmbedProvider(name: String, url: Option[String]) //url can be null

/**
  * The author part of a received embed.
  * @param name The author name
  * @param url An url for the author text.
  * @param iconUrl An icon url for the author.
  * @param proxyIconUrl A proxy url for the icon.
  */
case class ReceivedEmbedAuthor(
    name: String,
    url: Option[String],
    iconUrl: Option[String],
    proxyIconUrl: Option[String]
) {

  def toOutgoing: OutgoingEmbedAuthor = OutgoingEmbedAuthor(name, url, iconUrl)
}

/**
  * The footer part of a received embed.
  * @param text The footer text.
  * @param iconUrl An icon url for the footer.
  * @param proxyIconUrl A proxy url for the footer.
  */
case class ReceivedEmbedFooter(text: String, iconUrl: Option[String], proxyIconUrl: Option[String]) {
  def toOutgoing: OutgoingEmbedFooter = OutgoingEmbedFooter(text, iconUrl)
}

/**
  * A field for an embed
  * @param name The name or title of the field.
  * @param value The value or text of the field
  * @param inline If the field is rendered inline.
  */
case class EmbedField(name: String, value: String, inline: Option[Boolean] = None) {
  require(name.length <= 256, "A field name of an embed can't be more than 256 characters")
  require(value.length <= 1024, "A field value of an embed can't be more than 1024 characters")
}

/**
  * An attachment for a message
  * @param id The id of the attachment
  * @param filename The filename of the attachment
  * @param size The file size in bytes
  * @param url The url of the attachment
  * @param proxyUrl The proxyUrl of the attachment
  * @param height The height of the attachment if it's an image
  * @param width The width of the attachment if it's an image
  */
case class Attachment(
    id: SnowflakeType[Attachment],
    filename: String,
    size: Int,
    url: String,
    proxyUrl: String,
    height: Option[Int],
    width: Option[Int]
)

/**
  * An outgoing embed.
  * @param title The title of the embed.
  * @param description The embed description or main text.
  * @param url The url of the embed.
  * @param timestamp The timestamp of the embed.
  * @param color The color of the embed
  * @param footer The footer part of the embed.
  * @param image The image part of the embed.
  * @param thumbnail The thumbnail part of the embed.
  * @param author The author part of the embed.
  * @param fields The fields of the embed.
  */
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
) {
  require(title.forall(_.length <= 256), "The title of an embed can't be longer than 256 characters")
  require(description.forall(_.length <= 2048), "The description of an embed can't be longer than 2048 characters")
  require(fields.lengthCompare(25) <= 0, "An embed can't have more than 25 fields")
  require({
    val fromTitle = title.fold(0)(_.length)
    val fromDescription = description.fold(0)(_.length)
    val fromFooter = footer.fold(0)(_.text.length)
    val fromAuthor = author.fold(0)(_.name.length)
    val fromFields = fields.map(f => f.name.length + f.value.length).sum

    fromTitle + fromDescription + fromFooter + fromAuthor + fromFields <= 6000
  }, "An embed can't have more than 6000 characters in total")
}

/**
  * The thumbnail part of an outgoing embed.
  * @param url The url to the thumbnail.
  */
case class OutgoingEmbedThumbnail(url: String)

/**
  * The image part of an outgoing embed.
  * @param url The url to the image.
  */
case class OutgoingEmbedImage(url: String)

/**
  * The author part of an outgoing embed
  * @param name The name of the author
  * @param url The url to link when clicking on the author
  * @param iconUrl The icon to show besides the author.
  */
case class OutgoingEmbedAuthor(name: String, url: Option[String] = None, iconUrl: Option[String] = None) {
  require(name.length <= 256, "The author name of an embed can't have more than 256 characters")
}

/**
  * The footer part of an outgoing embed.
  * @param text The text of the footer
  * @param iconUrl The icon url of the footer.
  */
case class OutgoingEmbedFooter(text: String, iconUrl: Option[String] = None) {
  require(text.length <= 2048, "The footer text of an embed can't have more that 2048 characters")
}
