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
package ackcord.data

import java.time.OffsetDateTime
import java.util.Base64

import scala.collection.immutable
import scala.util.Try

import ackcord.CacheSnapshot
import ackcord.util.{IntCirceEnumWithUnknown, StringCirceEnumWithUnknown}
import enumeratum.values.{IntEnum, IntEnumEntry, StringEnum, StringEnumEntry}

sealed trait ImageFormat {
  def extensions: Seq[String]
  def extension: String = extensions.head
  def base64Name: String
}
object ImageFormat {
  case object JPEG extends ImageFormat {
    override def extensions: Seq[String] = Seq("jpg", "jpeg")
    override def base64Name: String = "image/jpeg"
  }
  case object PNG extends ImageFormat {
    override def extensions: Seq[String] = Seq("png")
    override def base64Name: String = "image/png"
  }
  case object WebP extends ImageFormat {
    override def extensions: Seq[String] = Seq("webp")
    override def base64Name: String = throw new IllegalArgumentException(
      "WepP is not supported as Base64 image data"
    )
  }
  case object GIF extends ImageFormat {
    override def extensions: Seq[String] = Seq("gif")
    override def base64Name: String = "image/gif"
  }
}

class ImageData(val rawData: String) extends AnyVal
object ImageData {

  def forData(imageType: ImageFormat, data: Array[Byte]): ImageData = {
    val base64Data = Base64.getEncoder.encodeToString(data)
    new ImageData(s"data:${imageType.base64Name};base64,$base64Data")
  }
}

sealed abstract class FormatType(val value: Int) extends IntEnumEntry
object FormatType
    extends IntEnum[FormatType]
    with IntCirceEnumWithUnknown[FormatType] {
  override def values: immutable.IndexedSeq[FormatType] = findValues

  case object PNG extends FormatType(1)
  case object APNG extends FormatType(2)
  case object LOTTIE extends FormatType(3)
  case class Unknown(i: Int) extends FormatType(i)

  override def createUnknown(value: Int): FormatType = Unknown(value)
}

/** An enum of all the different message types. */
sealed abstract class MessageType(val value: Int) extends IntEnumEntry
object MessageType
    extends IntEnum[MessageType]
    with IntCirceEnumWithUnknown[MessageType] {
  override def values: immutable.IndexedSeq[MessageType] = findValues

  case object Default extends MessageType(0)
  case object RecipientAdd extends MessageType(1)
  case object RecipientRemove extends MessageType(2)
  case object Call extends MessageType(3)
  case object ChannelNameChange extends MessageType(4)
  case object ChannelIconChange extends MessageType(5)
  case object ChannelPinnedMessage extends MessageType(6)
  case object GuildMemberJoin extends MessageType(7)
  case object UserPremiumGuildSubscription extends MessageType(8)
  case object UserPremiumGuildTier1 extends MessageType(9)
  case object UserPremiumGuildTier2 extends MessageType(10)
  case object UserPremiumGuildTier3 extends MessageType(11)
  case object ChannelFollowAdd extends MessageType(12)
  case object GuildDiscoveryDisqualified extends MessageType(14)
  case object GuildDiscoveryRequalified extends MessageType(15)
  case object GuildDiscoveryGracePeriodInitialWarning extends MessageType(16)
  case object GuildDiscoveryGracePeriodFinalWarning extends MessageType(17)
  case object ThreadCreated extends MessageType(18)
  case object Reply extends MessageType(19)
  case object ApplicationCommand extends MessageType(20)
  case object ThreadStarterMessage extends MessageType(21)
  case object GuildInviteReminder extends MessageType(22)
  case object ContextMenuCommand extends MessageType(23)
  case class Unknown(i: Int) extends MessageType(i)

  override def createUnknown(value: Int): MessageType = Unknown(value)
}

sealed abstract class PremiumType(val value: Int) extends IntEnumEntry
object PremiumType
    extends IntEnum[PremiumType]
    with IntCirceEnumWithUnknown[PremiumType] {
  override def values: immutable.IndexedSeq[PremiumType] = findValues

  case object None extends PremiumType(0)
  case object NitroClassic extends PremiumType(1)
  case object Nitro extends PremiumType(2)
  case class Unknown(i: Int) extends PremiumType(i)

  override def createUnknown(value: Int): PremiumType = Unknown(value)
}

/**
  * A author of a message. While a message is normally sent by a [[User]], it
  * can also be sent by a [[WebhookAuthor]].
  */
sealed trait Author[A] {

  /** The id for this author. */
  def id: SnowflakeType[A]

  /** If this author is not a webhook. */
  def isUser: Boolean

  /** The name of the author. */
  def username: String

  /** The discriminator of the author. */
  def discriminator: String
}

/**
  * An webhook author
  * @param id
  *   The webhook id
  * @param username
  *   The name of the webhook
  * @param avatar
  *   The webhook's avatar hash
  */
case class WebhookAuthor(
    id: SnowflakeType[Webhook],
    username: String,
    discriminator: String,
    avatar: Option[String]
) extends Author[Webhook] {
  override def isUser: Boolean = false
}

/**
  * A Discord user.
  * @param id
  *   The id of the user.
  * @param username
  *   The name of the user.
  * @param discriminator
  *   The discriminator for the user. Those four last digits when clicking in a
  *   users name.
  * @param avatar
  *   The users avatar hash.
  * @param bot
  *   If this user belongs to a OAuth2 application.
  * @param system
  *   If the user is part of Discord's urgent messaging system.
  * @param mfaEnabled
  *   If this user has two factor authentication enabled.
  * @param locale
  *   The user's chosen language.
  * @param verified
  *   If this user email is verified. Requires the email OAuth scope.
  * @param email
  *   The users email. Requires the email OAuth scope.
  * @param flags
  *   The flags on a user's account.
  * @param premiumType
  *   The type of nitro the account has.
  * @param publicFlags
  *   The public flags on a user's account.
  */
//Remember to edit PartialUser when editing this
case class User(
    id: UserId,
    username: String,
    discriminator: String,
    avatar: Option[String],
    bot: Option[Boolean],
    system: Option[Boolean],
    mfaEnabled: Option[Boolean],
    locale: Option[String],
    verified: Option[Boolean],
    email: Option[String],
    flags: Option[UserFlags],
    premiumType: Option[PremiumType],
    publicFlags: Option[UserFlags]
) extends Author[User]
    with UserOrRole {

  /** Mention this user. */
  def mention: String = id.mention

  /** Mention this user with their nickname. */
  def mentionNick: String = id.mentionNick

  override def isUser: Boolean = true
}

/**
  * A connection that a user has attached.
  * @param id
  *   The id of the connection.
  * @param name
  *   The name of the connection.
  * @param `type`
  *   The connection type (twitch, youtube).
  * @param revoked
  *   If the connection has been revoked.
  * @param integrations
  *   Integrations of the connection.
  * @param verified
  *   If the connection is verified.
  * @param friendSync
  *   If friend sync is enabled for the connection.
  * @param showActivity
  *   If things related this this connection will be included in presence
  *   updates.
  * @param visibility
  *   The visibility of the connection
  */
case class Connection(
    id: String,
    name: String,
    `type`: String,
    revoked: Option[Boolean],
    integrations: Option[Seq[Integration]], //TODO: Partial
    verified: Boolean,
    friendSync: Boolean,
    showActivity: Boolean,
    visibility: ConnectionVisibility
)
sealed abstract class ConnectionVisibility(val value: Int) extends IntEnumEntry
object ConnectionVisibility
    extends IntEnum[ConnectionVisibility]
    with IntCirceEnumWithUnknown[ConnectionVisibility] {

  override def values: immutable.IndexedSeq[ConnectionVisibility] = findValues

  //We use a different name here so that people don't accidentially switch up this and Option.None
  case object NoneVisibility extends ConnectionVisibility(0)
  case object Everyone extends ConnectionVisibility(1)

  case class Unknown(i: Int) extends ConnectionVisibility(i)

  override def createUnknown(value: Int): ConnectionVisibility = Unknown(value)
}

sealed abstract class MessageActivityType(val value: Int) extends IntEnumEntry
object MessageActivityType
    extends IntEnum[MessageActivityType]
    with IntCirceEnumWithUnknown[MessageActivityType] {
  override def values: immutable.IndexedSeq[MessageActivityType] = findValues

  case object Join extends MessageActivityType(1)
  case object Spectate extends MessageActivityType(2)
  case object Listen extends MessageActivityType(3)
  case object JoinRequest extends MessageActivityType(5)
  case class Unknown(i: Int) extends MessageActivityType(i)

  override def createUnknown(value: Int): MessageActivityType = Unknown(value)
}

/**
  * @param activityType
  *   Activity type.
  * @param partyId
  *   Party id from rich presence.
  */
case class MessageActivity(
    activityType: MessageActivityType,
    partyId: Option[String]
)

sealed trait Message {

  /** The id of the message. */
  def id: MessageId

  /** The channel this message was sent to. */
  def channelId: TextChannelId

  /** True if the author is a user. */
  def isAuthorUser: Boolean

  /** The id of the author that sent this message. */
  def authorId: RawSnowflake

  /** The username of the author. */
  def authorUsername: String

  /** The content of this message. */
  def content: String

  /** The timestamp this message was created. */
  def timestamp: OffsetDateTime

  /** The timestamp this message was last edited. */
  def editedTimestamp: Option[OffsetDateTime]

  /** If this message is has text-to-speech enabled. */
  def tts: Boolean

  /** If this message mentions everyone. */
  def mentionEveryone: Boolean

  /** All the users this message mentions. */
  def mentions: Seq[UserId]

  /**
    * Potentially channels mentioned in the message. Only used for cross posted
    * public channels so far.
    */
  def mentionChannels: Seq[ChannelMention]

  /** All the attachments of this message. */
  def attachment: Seq[Attachment]

  /** All the embeds of this message. */
  def embeds: Seq[ReceivedEmbed]

  /** All the reactions on this message. */
  def reactions: Seq[Reaction]

  /** A nonce for this message. */
  def nonce: Option[String]

  /** If this message is pinned. */
  def pinned: Boolean

  /** The message type. */
  def messageType: MessageType

  /** Sent with rich presence chat embeds. */
  def activity: Option[MessageActivity]

  /** Sent with rich presence chat embeds. */
  def application: Option[PartialApplication]

  /**
    * If an message is a response to an interaction, then this is the id of the
    * interaction's application.
    */
  def applicationId: Option[ApplicationId]

  /** Data sent with a crossposts and replies. */
  def messageReference: Option[MessageReference]

  /** Sent if the message is a response to an Interaction */
  def interaction: Option[MessageInteraction]

  /** Extra features of the message. */
  def flags: Option[MessageFlags]

  /** Stickers sent with the message. (deprecated) */
  @deprecated("Removed in favour of stickerItems", since = "0.18.0")
  def stickers: Option[Seq[Sticker]]

  /** Stickers sent with the message. */
  def stickerItems: Option[Seq[StickerItem]]

  /** Message associated with the message reference. */
  def referencedMessage: Option[Message]

  /** The thread that was started from this message. */
  def threadId: Option[ThreadGuildChannelId]

  /** Get the guild this message was sent to. */
  def guild(implicit c: CacheSnapshot): Option[GatewayGuild]

  /** Get the guild member of the one that sent this message. */
  def guildMember(implicit c: CacheSnapshot): Option[GuildMember]

  /** The extra interaction components added to this message. */
  def components: Seq[ActionRow]

  /** If the author is a user, their user id. */
  def authorUserId: Option[UserId] =
    if (isAuthorUser) Some(UserId(authorId)) else None

  /**
    * Gets the author of this message, ignoring the case where the author might
    * be a webhook.
    */
  def authorUser(implicit c: CacheSnapshot): Option[User] =
    authorUserId.fold(None: Option[User])(c.getUser)

  /** Expands all mentions in the message. */
  def formatMentions(implicit c: CacheSnapshot): String

  private[ackcord] def withReactions(reactions: Seq[Reaction]): Message

  def updateButton(identifier: String, f: TextButton => Button): Message
}

/**
  * A message missing the guild info. This can be because it was sent to a DM
  * channel, or because it was retrieved later through a REST call. Note that a
  * message can still be sent to a guild, but be missing the guild info. For
  * example if it's gotten from a REST request.
  */
case class SparseMessage(
    id: MessageId,
    channelId: TextChannelId,
    authorId: RawSnowflake,
    isAuthorUser: Boolean,
    authorUsername: String,
    content: String,
    timestamp: OffsetDateTime,
    editedTimestamp: Option[OffsetDateTime],
    tts: Boolean,
    mentionEveryone: Boolean,
    mentions: Seq[UserId],
    mentionChannels: Seq[ChannelMention],
    attachment: Seq[Attachment],
    embeds: Seq[ReceivedEmbed],
    reactions: Seq[Reaction],
    nonce: Option[String],
    pinned: Boolean,
    messageType: MessageType,
    activity: Option[MessageActivity],
    application: Option[PartialApplication],
    applicationId: Option[ApplicationId],
    messageReference: Option[MessageReference],
    flags: Option[MessageFlags],
    stickers: Option[Seq[Sticker]],
    stickerItems: Option[Seq[StickerItem]],
    referencedMessage: Option[Message],
    interaction: Option[MessageInteraction],
    components: Seq[ActionRow],
    threadId: Option[ThreadGuildChannelId]
) extends Message {

  override def guild(implicit c: CacheSnapshot): Option[GatewayGuild] =
    channelId.asChannelId[GuildChannel].resolve.flatMap(_.guild)

  override def guildMember(implicit c: CacheSnapshot): Option[GuildMember] =
    for {
      userId <- authorUserId
      g <- guild
      member <- g.members.get(userId)
    } yield member

  override def formatMentions(implicit c: CacheSnapshot): String = {
    val userList = mentions.toList.flatMap(_.resolve)

    userList.foldRight(content)((user, content) =>
      content.replace(user.mention, s"@${user.username}")
    )
  }

  override private[ackcord] def withReactions(
      reactions: Seq[Reaction]
  ): SparseMessage = copy(reactions = reactions)

  override def updateButton(
      identifier: String,
      f: TextButton => Button
  ): SparseMessage =
    copy(components = components.map(_.updateButton(identifier, f)))
}

/**
  * A message sent with additional guild info. Note that a message can still be
  * sent to a guild, but be missing the guild info. For example if it's gotten
  * from a REST request.
  * @param guildId
  *   The guild this message was sent to.
  * @param isAuthorUser
  *   If the author of this message was a user.
  * @param member
  *   The guild member user that sent this message.
  * @param mentionRoles
  *   All the roles this message mentions.
  */
case class GuildGatewayMessage(
    id: MessageId,
    channelId: TextGuildChannelId,
    guildId: GuildId,
    authorId: RawSnowflake,
    isAuthorUser: Boolean,
    authorUsername: String,
    member: Option[GuildMember],
    content: String,
    timestamp: OffsetDateTime,
    editedTimestamp: Option[OffsetDateTime],
    tts: Boolean,
    mentionEveryone: Boolean,
    mentions: Seq[UserId],
    mentionRoles: Seq[RoleId],
    mentionChannels: Seq[ChannelMention],
    attachment: Seq[Attachment],
    embeds: Seq[ReceivedEmbed],
    reactions: Seq[Reaction],
    nonce: Option[String],
    pinned: Boolean,
    messageType: MessageType,
    activity: Option[MessageActivity],
    application: Option[PartialApplication],
    applicationId: Option[ApplicationId],
    messageReference: Option[MessageReference],
    flags: Option[MessageFlags],
    stickers: Option[Seq[Sticker]],
    stickerItems: Option[Seq[StickerItem]],
    referencedMessage: Option[Message],
    interaction: Option[MessageInteraction],
    components: Seq[ActionRow],
    threadId: Option[ThreadGuildChannelId]
) extends Message {

  override def guild(implicit c: CacheSnapshot): Option[GatewayGuild] =
    c.getGuild(guildId)

  override def guildMember(implicit c: CacheSnapshot): Option[GuildMember] =
    member

  private val channelRegex = """<#(\d+)>""".r

  def channelMentions: Seq[TextGuildChannelId] = {
    channelRegex
      .findAllMatchIn(content)
      .flatMap { m =>
        Try {
          TextGuildChannelId(RawSnowflake(m.group(1)))
        }.toOption
      }
      .toSeq
  }

  override def formatMentions(implicit c: CacheSnapshot): String = {
    val userList = mentions.toList.flatMap(_.resolve)
    val roleList = mentionRoles.toList.flatMap(_.resolve)
    val optGuildId = channelId.resolve.collect { case channel: GuildChannel =>
      channel.guildId
    }
    val channelList =
      optGuildId.fold[List[Option[GuildChannel]]](Nil)(guildId =>
        channelMentions.toList.map(_.resolve(guildId))
      )

    val withUsers = userList
      .foldRight(content)((user, content) =>
        content.replace(user.mention, s"@${user.username}")
      )
    val withRoles = roleList
      .foldRight(withUsers)((role, content) =>
        content.replace(role.mention, s"@${role.name}")
      )
    val withChannels = channelList.flatten
      .foldRight(withRoles)((channel, content) =>
        content.replace(channel.mention, s"@${channel.name}")
      )

    withChannels
  }

  override private[ackcord] def withReactions(
      reactions: Seq[Reaction]
  ): GuildGatewayMessage =
    copy(reactions = reactions)

  override def updateButton(
      identifier: String,
      f: TextButton => Button
  ): GuildGatewayMessage =
    copy(components = components.map(_.updateButton(identifier, f)))
}

/**
  * Basic info of a channel in a cross posted message.
  * @param id
  *   The id of the channel
  * @param guildId
  *   The guild the channel is in
  * @param `type`
  *   The channel type
  * @param name
  *   The name of the channel
  */
case class ChannelMention(
    id: TextChannelId,
    guildId: GuildId,
    `type`: ChannelType,
    name: String
)

/** A reference to another message. */
case class MessageReference(
    messageId: Option[MessageId],
    channelId: TextChannelId,
    guildId: Option[GuildId]
)

/**
  * A reaction to a message
  * @param count
  *   The amount of people that have reacted with this emoji.
  * @param me
  *   If the client has reacted with this emoji.
  * @param emoji
  *   The emoji of the reaction.
  */
case class Reaction(count: Int, me: Boolean, emoji: PartialEmoji)

/**
  * A partial emoji found in reactions
  * @param id
  *   The id of the emoji. If it's absent, it's not a guild emoji.
  * @param name
  *   The name of the emoji.
  */
case class PartialEmoji(
    id: Option[EmojiId] = None,
    name: Option[String] = None,
    animated: Option[Boolean] = None
) {

  /** Returns a string representation of this emoji for use in requests. */
  def asString: String = (id, name) match {
    case (Some(id), Some(name)) => s"$name:$id"
    case (_, Some(name))        => name
    case _                      => sys.error("Unexpected PartialEmoji form")
  }
}

/**
  * A received embed.
  * @param title
  *   The title of the embed.
  * @param `type`
  *   The embed type. Probably rich.
  * @param description
  *   The embed description or main text.
  * @param url
  *   The url of the embed.
  * @param timestamp
  *   The timestamp of the embed.
  * @param color
  *   The color of the embed
  * @param footer
  *   The footer part of the embed.
  * @param image
  *   The image part of the embed.
  * @param thumbnail
  *   The thumbnail part of the embed.
  * @param video
  *   The video part of the embed.
  * @param provider
  *   The provider part of the embed.
  * @param author
  *   The author part of the embed.
  * @param fields
  *   The fields of the embed.
  */
case class ReceivedEmbed(
    title: Option[String],
    `type`: Option[EmbedType],
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
    image = image.flatMap(_.toOutgoing),
    video = video.flatMap(_.toOutgoing),
    thumbnail = thumbnail.flatMap(_.toOutgoing),
    author = author.flatMap(_.toOutgoing),
    fields = fields.getOrElse(Seq.empty)
  )
}

sealed abstract class EmbedType(val value: String) extends StringEnumEntry
object EmbedType
    extends StringEnum[EmbedType]
    with StringCirceEnumWithUnknown[EmbedType] {
  override def values: immutable.IndexedSeq[EmbedType] = findValues

  case object Rich extends EmbedType("rich")
  case object Image extends EmbedType("image")
  case object Video extends EmbedType("video")
  case object GifV extends EmbedType("gifv")
  case object Article extends EmbedType("article")
  case object Link extends EmbedType("link")
  case class Unknown(str: String) extends EmbedType(str)

  override def createUnknown(value: String): EmbedType = Unknown(value)
}

/**
  * The thumbnail part of a received embed.
  * @param url
  *   The url to the thumbnail.
  * @param proxyUrl
  *   The proxy url of the thumbnail.
  * @param height
  *   The height of the thumbnail.
  * @param width
  *   The width of the thumbnail.
  */
case class ReceivedEmbedThumbnail(
    url: Option[String],
    proxyUrl: Option[String],
    height: Option[Int],
    width: Option[Int]
) {

  def toOutgoing: Option[OutgoingEmbedThumbnail] =
    url.map(OutgoingEmbedThumbnail)
}

/**
  * The video part of a received embed.
  * @param url
  *   The url of the video.
  * @param proxyUrl
  *   The proxy url of the video.
  * @param height
  *   The height of the video.
  * @param width
  *   The width of the video.
  */
case class ReceivedEmbedVideo(
    url: Option[String],
    proxyUrl: Option[String],
    height: Option[Int],
    width: Option[Int]
) {
  def toOutgoing: Option[OutgoingEmbedVideo] = url.map(OutgoingEmbedVideo)
}

/**
  * The image part of a received embed.
  * @param url
  *   The url of the image.
  * @param proxyUrl
  *   The proxy url of the image.
  * @param height
  *   The height of the image.
  * @param width
  *   The width of the image.
  */
case class ReceivedEmbedImage(
    url: Option[String],
    proxyUrl: Option[String],
    height: Option[Int],
    width: Option[Int]
) {
  def toOutgoing: Option[OutgoingEmbedImage] = url.map(OutgoingEmbedImage)
}

/**
  * The provider part of a received embed.
  * @param name
  *   The name of the provider.
  * @param url
  *   The url of a provider.
  */
case class ReceivedEmbedProvider(name: Option[String], url: Option[String])

/**
  * The author part of a received embed.
  * @param name
  *   The author name
  * @param url
  *   An url for the author text.
  * @param iconUrl
  *   An icon url for the author.
  * @param proxyIconUrl
  *   A proxy url for the icon.
  */
case class ReceivedEmbedAuthor(
    name: Option[String],
    url: Option[String],
    iconUrl: Option[String],
    proxyIconUrl: Option[String]
) {

  def toOutgoing: Option[OutgoingEmbedAuthor] =
    name.map(n => OutgoingEmbedAuthor(n, url, iconUrl))
}

/**
  * The footer part of a received embed.
  * @param text
  *   The footer text.
  * @param iconUrl
  *   An icon url for the footer.
  * @param proxyIconUrl
  *   A proxy url for the footer.
  */
case class ReceivedEmbedFooter(
    text: String,
    iconUrl: Option[String],
    proxyIconUrl: Option[String]
) {
  def toOutgoing: OutgoingEmbedFooter = OutgoingEmbedFooter(text, iconUrl)
}

/**
  * A field for an embed
  * @param name
  *   The name or title of the field.
  * @param value
  *   The value or text of the field
  * @param inline
  *   If the field is rendered inline.
  */
case class EmbedField(
    name: String,
    value: String,
    `inline`: Option[Boolean] = None
) {
  require(
    name.length <= 256,
    "A field name of an embed can't be more than 256 characters"
  )
  require(
    value.length <= 1024,
    "A field value of an embed can't be more than 1024 characters"
  )
}

/**
  * An attachment for a message
  * @param id
  *   The id of the attachment
  * @param filename
  *   The filename of the attachment
  * @param contentType
  *   The attachment's media type
  * @param size
  *   The file size in bytes
  * @param url
  *   The url of the attachment
  * @param proxyUrl
  *   The proxyUrl of the attachment
  * @param height
  *   The height of the attachment if it's an image
  * @param width
  *   The width of the attachment if it's an image
  */
case class Attachment(
    id: SnowflakeType[Attachment],
    filename: String,
    contentType: Option[String],
    size: Int,
    url: String,
    proxyUrl: String,
    height: Option[Int],
    width: Option[Int]
)

/**
  * An outgoing embed.
  * @param title
  *   The title of the embed.
  * @param description
  *   The embed description or main text.
  * @param url
  *   The url of the embed.
  * @param timestamp
  *   The timestamp of the embed.
  * @param color
  *   The color of the embed
  * @param footer
  *   The footer part of the embed.
  * @param image
  *   The image part of the embed.
  * @param thumbnail
  *   The thumbnail part of the embed.
  * @param author
  *   The author part of the embed.
  * @param fields
  *   The fields of the embed.
  */
case class OutgoingEmbed(
    title: Option[String] = None,
    description: Option[String] = None,
    url: Option[String] = None,
    timestamp: Option[OffsetDateTime] = None,
    color: Option[Int] = None,
    footer: Option[OutgoingEmbedFooter] = None,
    image: Option[OutgoingEmbedImage] = None,
    video: Option[OutgoingEmbedVideo] = None,
    thumbnail: Option[OutgoingEmbedThumbnail] = None,
    author: Option[OutgoingEmbedAuthor] = None,
    fields: Seq[EmbedField] = Seq.empty
) {
  require(
    title.forall(_.length <= 256),
    "The title of an embed can't be longer than 256 characters"
  )
  require(
    description.forall(_.length <= 4096),
    "The description of an embed can't be longer than 2048 characters"
  )
  require(
    fields.lengthCompare(25) <= 0,
    "An embed can't have more than 25 fields"
  )
  require(
    totalCharAmount <= 6000,
    "An embed can't have more than 6000 characters in total"
  )

  /** The total amount of characters in this embed so far. */
  def totalCharAmount: Int = {
    val fromTitle = title.fold(0)(_.length)
    val fromDescription = description.fold(0)(_.length)
    val fromFooter = footer.fold(0)(_.text.length)
    val fromAuthor = author.fold(0)(_.name.length)
    val fromFields = fields.map(f => f.name.length + f.value.length).sum

    fromTitle + fromDescription + fromFooter + fromAuthor + fromFields
  }
}

/**
  * The thumbnail part of an outgoing embed.
  * @param url
  *   The url to the thumbnail.
  */
case class OutgoingEmbedThumbnail(url: String)

/**
  * The image part of an outgoing embed.
  * @param url
  *   The url to the image.
  */
case class OutgoingEmbedImage(url: String)

/**
  * The video part of an outgoing embed.
  * @param url
  *   The url to the video.
  */
case class OutgoingEmbedVideo(url: String)

/**
  * The author part of an outgoing embed
  * @param name
  *   The name of the author
  * @param url
  *   The url to link when clicking on the author
  * @param iconUrl
  *   The icon to show besides the author.
  */
case class OutgoingEmbedAuthor(
    name: String,
    url: Option[String] = None,
    iconUrl: Option[String] = None
) {
  require(
    name.length <= 256,
    "The author name of an embed can't have more than 256 characters"
  )
}

/**
  * The footer part of an outgoing embed.
  * @param text
  *   The text of the footer
  * @param iconUrl
  *   The icon url of the footer.
  */
case class OutgoingEmbedFooter(text: String, iconUrl: Option[String] = None) {
  require(
    text.length <= 2048,
    "The footer text of an embed can't have more that 2048 characters"
  )
}

/**
  * The structure of a sticker sent in a message.
  * @param id
  *   id of the sticker
  * @param packId
  *   id of the pack the sticker is from
  * @param name
  *   name of the sticker
  * @param description
  *   description of the sticker
  * @param tags
  *   a comma-separated list of tags for the sticker
  * @param asset
  *   sticker asset hash
  * @param formatType
  *   type of sticker format
  */
case class Sticker(
    id: StickerId,
    packId: Option[RawSnowflake],
    name: String,
    description: String,
    tags: Option[String],
    asset: String,
    formatType: FormatType
)

/**
  * The structure of a sticker item (the smallest amount of data required to
  * render a sticker)
  * @param id
  *   id of the sticker
  * @param name
  *   name of the sticker
  * @param formatType
  *   type of sticker format
  */
case class StickerItem(
    id: StickerId,
    name: String,
    formatType: FormatType
)

/**
  * This is sent on the message object when the message is a response to an
  * Interaction.
  * @param id
  *   id of the interaction
  * @param type
  *   the type of interaction
  * @param name
  *   the name of the ApplicationCommand
  * @param user
  *   the user who invoked the interaction
  */
case class MessageInteraction(
    id: String,
    `type`: InteractionType,
    name: String,
    user: User
)

/**
  * @param parse
  *   Which mention types should be allowed.
  * @param roles
  *   The roles to allow mention.
  * @param users
  *   The users to allow mention.
  * @param repliedUser
  *   For replires, if the author of the message you are replying to should be
  *   mentioned.
  */
case class AllowedMention(
    parse: Seq[AllowedMentionTypes] = Seq.empty,
    roles: Seq[RoleId] = Seq.empty,
    users: Seq[UserId] = Seq.empty,
    repliedUser: Boolean = false
) {
  require(roles.size <= 100, "Too many allowed role mentions")
  require(users.size <= 100, "Too many allowed user mentions")
}

object AllowedMention {
  val none: AllowedMention = AllowedMention()
  val all: AllowedMention = AllowedMention(
    parse = Seq(
      AllowedMentionTypes.Roles,
      AllowedMentionTypes.Users,
      AllowedMentionTypes.Everyone
    )
  )
}

sealed abstract class AllowedMentionTypes(val value: String)
    extends StringEnumEntry
object AllowedMentionTypes
    extends StringEnum[AllowedMentionTypes]
    with StringCirceEnumWithUnknown[AllowedMentionTypes] {
  override def values: immutable.IndexedSeq[AllowedMentionTypes] = findValues

  case object Roles extends AllowedMentionTypes("roles")
  case object Users extends AllowedMentionTypes("users")
  case object Everyone extends AllowedMentionTypes("everyone")
  case class Unknown(str: String) extends AllowedMentionTypes(str)

  override def createUnknown(value: String): AllowedMentionTypes = Unknown(
    value
  )
}
