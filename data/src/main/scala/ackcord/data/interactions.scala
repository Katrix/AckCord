/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 Katrix
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

import scala.collection.immutable
import scala.util.matching.Regex

import ackcord.data.raw.{RawGuildMember, RawMessage, RawThreadMetadata}
import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.{IntEnum, IntEnumEntry}
import cats.syntax.either._
import io.circe._
import io.circe.syntax._

sealed abstract class InteractionType(val value: Int) extends IntEnumEntry
object InteractionType extends IntCirceEnumWithUnknown[InteractionType] {
  override def values: collection.immutable.IndexedSeq[InteractionType] = findValues

  case object Ping               extends InteractionType(1)
  case object ApplicationCommand extends InteractionType(2)
  case object MessageComponent   extends InteractionType(3)
  case class Unknown(i: Int)     extends InteractionType(i)

  override def createUnknown(value: Int): InteractionType = Unknown(value)
}

sealed abstract class InteractionResponseType(val value: Int) extends IntEnumEntry
object InteractionResponseType extends IntCirceEnumWithUnknown[InteractionResponseType] {
  override def values: immutable.IndexedSeq[InteractionResponseType] = findValues

  case object Pong                             extends InteractionResponseType(1)
  case object ChannelMessageWithSource         extends InteractionResponseType(4)
  case object DeferredChannelMessageWithSource extends InteractionResponseType(5)
  case object DeferredUpdateMessage            extends InteractionResponseType(6)
  case object UpdateMessage                    extends InteractionResponseType(7)
  case class Unknown(i: Int)                   extends InteractionResponseType(i)

  override def createUnknown(value: Int): InteractionResponseType = Unknown(value)
}

case class RawInteraction(
    id: InteractionId,
    applicationId: RawSnowflake,
    tpe: InteractionType,
    data: Option[ApplicationInteractionData],
    guildId: Option[GuildId],
    channelId: Option[TextChannelId],
    member: Option[RawGuildMember],
    memberPermission: Option[Permission],
    user: Option[User],
    token: String,
    version: Int,
    message: Option[RawMessage]
)

case class ApplicationCommand(
    id: CommandId,
    `type`: ApplicationCommandType,
    applicationId: ApplicationId,
    guildId: Option[String],
    name: String,
    description: String,
    options: Option[Seq[ApplicationCommandOption]],
    defaultPermission: Option[Boolean],
    version: RawSnowflake
)

sealed abstract class ApplicationCommandType(val value: Int) extends IntEnumEntry
object ApplicationCommandType extends IntCirceEnumWithUnknown[ApplicationCommandType] {
  override def values: immutable.IndexedSeq[ApplicationCommandType] = findValues

  case object ChatInput extends ApplicationCommandType(1)
  case object User      extends ApplicationCommandType(2)
  case object Message   extends ApplicationCommandType(3)

  case class Unknown(i: Int) extends ApplicationCommandType(i)

  override def createUnknown(value: Int): ApplicationCommandType = Unknown(value)
}

case class ApplicationCommandOption(
    `type`: ApplicationCommandOptionType,
    name: String,
    description: String,
    required: Option[Boolean],
    choices: Option[Seq[ApplicationCommandOptionChoice]],
    options: Option[Seq[ApplicationCommandOption]],
    channelTypes: Option[Seq[ChannelType]],
    minValue: Option[Double],
    maxValue: Option[Double]
)

//A dirty hack to get dependant types for params
sealed trait ApplicationCommandOptionType extends IntEnumEntry {
  type Res

  def value: Int
  def decodeJson: Json => Decoder.Result[Res]
  def encodeJson: Res => Json
  def valueJsonName: String
}
object ApplicationCommandOptionType
    extends IntEnum[ApplicationCommandOptionType]
    with IntCirceEnumWithUnknown[ApplicationCommandOptionType] {
  type Aux[A] = ApplicationCommandOptionType { type Res = A }

  override def values: immutable.IndexedSeq[ApplicationCommandOptionType] =
    ApplicationCommandOptionTypeE.values.asInstanceOf[immutable.IndexedSeq[ApplicationCommandOptionType]]

  final val SubCommand: ApplicationCommandOptionType.Aux[Seq[ApplicationCommandInteractionDataOption[_]]] =
    ApplicationCommandOptionTypeE.SubCommand
  final val SubCommandGroup: ApplicationCommandOptionType.Aux[Seq[ApplicationCommandInteractionDataOption[_]]] =
    ApplicationCommandOptionTypeE.SubCommandGroup

  final val String: ApplicationCommandOptionType.Aux[String]              = ApplicationCommandOptionTypeE.String
  final val Integer: ApplicationCommandOptionType.Aux[Int]                = ApplicationCommandOptionTypeE.Integer
  final val Boolean: ApplicationCommandOptionType.Aux[Boolean]            = ApplicationCommandOptionTypeE.Boolean
  final val User: ApplicationCommandOptionType.Aux[UserId]                = ApplicationCommandOptionTypeE.User
  final val Channel: ApplicationCommandOptionType.Aux[TextGuildChannelId] = ApplicationCommandOptionTypeE.Channel
  final val Role: ApplicationCommandOptionType.Aux[RoleId]                = ApplicationCommandOptionTypeE.Role
  final val Mentionable: ApplicationCommandOptionType.Aux[UserOrRoleId]   = ApplicationCommandOptionTypeE.Mentionable
  final val Number: ApplicationCommandOptionType.Aux[Double]              = ApplicationCommandOptionTypeE.Number

  def Unknown(i: Int): ApplicationCommandOptionType = ApplicationCommandOptionTypeE.Unknown(i)

  override def createUnknown(value: Int): ApplicationCommandOptionType = ApplicationCommandOptionTypeE.Unknown(value)
}

sealed abstract private class ApplicationCommandOptionTypeE[A](
    val value: Int,
    val decodeJson: Json => Decoder.Result[A],
    val encodeJson: A => Json,
    val valueJsonName: String = "value"
) extends IntEnumEntry
    with ApplicationCommandOptionType {
  type Res = A
}
private object ApplicationCommandOptionTypeE extends IntEnum[ApplicationCommandOptionTypeE[_]] {
  override def values: immutable.IndexedSeq[ApplicationCommandOptionTypeE[_]] = findValues

  private val userRegex: Regex    = """<@!?(\d+)>""".r
  private val channelRegex: Regex = """<#(\d+)>""".r
  private val roleRegex: Regex    = """<@&(\d+)>""".r

  import DiscordProtocol._

  private def decodeMention[A](regex: Regex)(json: Json): Decoder.Result[SnowflakeType[A]] =
    json.as[java.lang.String].flatMap {
      case regex(id) => Right(SnowflakeType(id))
      case _         => Left(DecodingFailure("Not a valid mention", Nil))
    }

  case object SubCommand
      extends ApplicationCommandOptionTypeE[Seq[ApplicationCommandInteractionDataOption[_]]](
        1,
        _.as[Seq[ApplicationCommandInteractionDataOption[_]]],
        _.asJson,
        "options"
      )
  case object SubCommandGroup
      extends ApplicationCommandOptionTypeE[Seq[ApplicationCommandInteractionDataOption[_]]](
        2,
        _.as[Seq[ApplicationCommandInteractionDataOption[_]]],
        _.asJson,
        "options"
      )

  case object String  extends ApplicationCommandOptionTypeE[java.lang.String](3, _.as[java.lang.String], _.asJson)
  case object Integer extends ApplicationCommandOptionTypeE[Int](4, _.as[Int], _.asJson)
  case object Boolean extends ApplicationCommandOptionTypeE[scala.Boolean](5, _.as[scala.Boolean], _.asJson)
  case object User    extends ApplicationCommandOptionTypeE[UserId](6, decodeMention(userRegex), _.mention.asJson)
  case object Channel
      extends ApplicationCommandOptionTypeE[TextGuildChannelId](7, decodeMention(channelRegex), _.mention.asJson)
  case object Role extends ApplicationCommandOptionTypeE[RoleId](8, decodeMention(roleRegex), _.mention.asJson)
  case object Mentionable
      extends ApplicationCommandOptionTypeE[UserOrRoleId](
        9,
        json => decodeMention[UserOrRole](userRegex)(json).orElse(decodeMention[UserOrRole](roleRegex)(json)),
        id => s"<@$id>".asJson
      ) //Let's just hope it's a user here
  case object Number extends ApplicationCommandOptionTypeE[Double](10, _.as[Double], _.asJson)

  case class Unknown(i: Int) extends ApplicationCommandOptionTypeE[Json](i, Right(_), identity)

  implicit def encoder[A]: Encoder[ApplicationCommandOptionTypeE[A]] = (a: ApplicationCommandOptionTypeE[A]) =>
    a.value.asJson
  implicit val decoder: Decoder[ApplicationCommandOptionTypeE[_]] = (c: HCursor) =>
    c.as[Int].map(v => withValueOpt(v).getOrElse(Unknown(v)))
}

case class ApplicationCommandOptionChoice(
    name: String,
    value: Either[String, Double]
)

sealed trait ApplicationInteractionData
case class ApplicationCommandInteractionData(
    id: CommandId,
    name: String,
    `type`: ApplicationCommandType,
    resolved: Option[ApplicationCommandInteractionDataResolved],
    options: Option[Seq[ApplicationCommandInteractionDataOption[_]]],
    targetId: Option[RawSnowflake]
) extends ApplicationInteractionData
case class ApplicationComponentInteractionData(
    componentType: ComponentType,
    customId: String,
    values: Option[Seq[String]]
)                                                        extends ApplicationInteractionData
case class ApplicationUnknownInteractionData(data: Json) extends ApplicationInteractionData

case class ApplicationCommandInteractionDataResolved(
    users: Map[UserId, User],
    members: Map[UserId, InteractionRawGuildMember],
    roles: Map[RoleId, Role],
    channels: Map[TextGuildChannelId, InteractionChannel],
    messages: Map[MessageId, InteractionPartialMessage]
)
object ApplicationCommandInteractionDataResolved {
  val empty: ApplicationCommandInteractionDataResolved =
    ApplicationCommandInteractionDataResolved(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty)
}

case class InteractionGuildMember(
    user: User,
    avatar: Option[String],
    nick: Option[String],
    roles: Seq[RoleId],
    joinedAt: Option[OffsetDateTime],
    premiumSince: Option[OffsetDateTime],
    pending: Option[Boolean]
)

case class InteractionRawGuildMember(
    nick: Option[String],
    avatar: Option[String],
    roles: Seq[RoleId],
    joinedAt: Option[OffsetDateTime],
    premiumSince: Option[OffsetDateTime],
    pending: Option[Boolean]
)

case class InteractionChannel(
    id: TextGuildChannelId,
    name: String,
    `type`: ChannelType,
    permissions: Permission,
    threadMetadata: Option[RawThreadMetadata],
    parentId: Option[TextGuildChannelId]
)

case class InteractionPartialMessage(
    id: MessageId,
    channelId: TextChannelId,
    author: User,
    content: String,
    timestamp: OffsetDateTime,
    editedTimestamp: Option[OffsetDateTime],
    tts: Boolean,
    mentionEveryone: Boolean,
    mentions: Seq[User],
    mentionRoles: Seq[RoleId],
    attachments: Seq[Attachment],
    embeds: Seq[ReceivedEmbed],
    pinned: Boolean,
    `type`: MessageType,
    flags: MessageFlags,
    components: Option[Seq[ActionRow]],
)

case class ApplicationCommandInteractionDataOption[A](
    name: String,
    tpe: ApplicationCommandOptionType.Aux[A],
    value: Option[A]
)

case class RawInteractionResponse(
    `type`: InteractionResponseType,
    data: Option[RawInteractionApplicationCommandCallbackData]
)

case class RawInteractionApplicationCommandCallbackData(
    tts: Option[Boolean] = None,
    content: Option[String] = None,
    embeds: Seq[OutgoingEmbed] = Nil,
    allowedMentions: Option[AllowedMention] = None,
    flags: MessageFlags = MessageFlags.None,
    components: Option[Seq[ActionRow]] = None,
    attachments: Option[Seq[PartialAttachment]] = None
)

case class GuildApplicationCommandPermissions(
    id: CommandId,
    applicationId: ApplicationId,
    guildId: GuildId,
    permissions: Seq[ApplicationCommandPermissions]
)

case class ApplicationCommandPermissions(
    id: UserOrRoleId,
    `type`: ApplicationCommandPermissionType,
    permission: Boolean
)

sealed abstract class ApplicationCommandPermissionType(val value: Int) extends IntEnumEntry
object ApplicationCommandPermissionType
    extends IntEnum[ApplicationCommandPermissionType]
    with IntCirceEnumWithUnknown[ApplicationCommandPermissionType] {
  override def values: immutable.IndexedSeq[ApplicationCommandPermissionType] = findValues

  case object Role extends ApplicationCommandPermissionType(1)
  case object User extends ApplicationCommandPermissionType(2)

  case class Unknown(i: Int) extends ApplicationCommandPermissionType(i)

  override def createUnknown(value: Int): ApplicationCommandPermissionType = Unknown(value)
}
