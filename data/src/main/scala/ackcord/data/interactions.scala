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

import ackcord.data.raw.{RawGuildMember, RawMessage}
import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.{IntEnum, IntEnumEntry}
import io.circe.Json

sealed abstract class InteractionType(val value: Int) extends IntEnumEntry
object InteractionType extends IntEnum[InteractionType] with IntCirceEnumWithUnknown[InteractionType] {
  override def values: collection.immutable.IndexedSeq[InteractionType] = findValues

  case object Ping               extends InteractionType(1)
  case object ApplicationCommand extends InteractionType(2)
  case object ComponentClicked   extends InteractionType(3)
  case class Unknown(i: Int)     extends InteractionType(i)

  override def createUnknown(value: Int): InteractionType = Unknown(value)
}

sealed abstract class InteractionResponseType(val value: Int) extends IntEnumEntry
object InteractionResponseType
    extends IntEnum[InteractionResponseType]
    with IntCirceEnumWithUnknown[InteractionResponseType] {
  override def values: collection.immutable.IndexedSeq[InteractionResponseType] = findValues

  case object Pong                             extends InteractionResponseType(1)
  case object ChannelMessageWithSource         extends InteractionResponseType(4)
  case object DeferredChannelMessageWithSource extends InteractionResponseType(5)
  case object DeferredMessageUpdate            extends InteractionResponseType(6)
  case class Unknown(i: Int)                   extends InteractionResponseType(i)

  override def createUnknown(value: Int): InteractionResponseType = Unknown(value)
}

case class RawInteraction(
    id: InteractionId,
    applicationId: RawSnowflake,
    tpe: InteractionType,
    data: Option[ApplicationInteractionData],
    guildId: Option[GuildId],
    channelId: TextChannelId,
    member: Option[RawGuildMember],
    memberPermission: Option[Permission],
    user: Option[User],
    token: String,
    message: Option[RawMessage],
    version: Option[Int]
)

case class ApplicationCommand(
    id: CommandId,
    applicationId: ApplicationId,
    name: String,
    description: String,
    options: Option[Seq[ApplicationCommandOption]]
)

case class ApplicationCommandOption(
    `type`: ApplicationCommandOptionType,
    name: String,
    description: String,
    required: Option[Boolean],
    choices: Option[Seq[ApplicationCommandOptionChoice]],
    options: Option[Seq[ApplicationCommandOption]]
)

sealed abstract class ApplicationCommandOptionType(val value: Int) extends IntEnumEntry
object ApplicationCommandOptionType
    extends IntEnum[ApplicationCommandOptionType]
    with IntCirceEnumWithUnknown[ApplicationCommandOptionType] {
  override def values: collection.immutable.IndexedSeq[ApplicationCommandOptionType] = findValues

  case object SubCommand      extends ApplicationCommandOptionType(1)
  case object SubCommandGroup extends ApplicationCommandOptionType(2)
  case object String          extends ApplicationCommandOptionType(3)
  case object Integer         extends ApplicationCommandOptionType(4)
  case object Boolean         extends ApplicationCommandOptionType(5)
  case object User            extends ApplicationCommandOptionType(6)
  case object Channel         extends ApplicationCommandOptionType(7)
  case object Role            extends ApplicationCommandOptionType(8)

  case class Unknown(i: Int) extends ApplicationCommandOptionType(i)

  override def createUnknown(value: Int): ApplicationCommandOptionType = Unknown(value)
}

case class ApplicationCommandOptionChoice(
    name: String,
    value: Either[String, Int]
)

sealed trait ApplicationInteractionData
case class ApplicationCommandInteractionData(
    id: CommandId,
    name: String,
    options: Option[Seq[ApplicationCommandInteractionDataOption]]
)                                                                  extends ApplicationInteractionData
case class ApplicationComponentInteractionData(customId: String)   extends ApplicationInteractionData
case class ApplicationUnknownInteractionData(tpe: Int, data: Json) extends ApplicationInteractionData

sealed trait ApplicationCommandInteractionDataOption {
  def name: String
}
object ApplicationCommandInteractionDataOption {
  case class ApplicationCommandInteractionDataOptionWithValue(name: String, value: Json)
      extends ApplicationCommandInteractionDataOption
  case class ApplicationCommandInteractionDataOptionWithOptions(
      name: String,
      options: Seq[ApplicationCommandInteractionDataOption]
  ) extends ApplicationCommandInteractionDataOption
}

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
    components: Option[Seq[ActionRow]] = None
)
