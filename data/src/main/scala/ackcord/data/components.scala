package ackcord.data

import java.util.UUID

import scala.collection.immutable

import ackcord.util.{IntCirceEnumWithUnknown, Verifier}
import enumeratum.values.{IntEnum, IntEnumEntry}

sealed trait Component {
  def tpe: ComponentType
}

case class ActionRow private (
    components: Seq[ActionRowContent]
) extends Component {
  require(components.size <= 5, "Too many components in ActionRow")

  override def tpe: ComponentType = ComponentType.ActionRow

  def mapButtons(f: Button => Button): ActionRow = copy(components = components.map {
    case button: Button     => f(button)
    case menu: StringSelect => menu
  })

  def updateButton(identifier: String, f: TextButton => Button): ActionRow = copy(components = components.map {
    case button: TextButton if button.identifier == identifier => f(button)
    case button                                                => button
  })
}
object ActionRow {
  def ofUnsafe(components: Seq[ActionRowContent])     = new ActionRow(components)
  def of(buttons: Button*): ActionRow                 = new ActionRow(buttons)
  def of(selectMenu: InteractiveComponent): ActionRow = new ActionRow(Seq(selectMenu))
}

sealed trait ActionRowContent extends Component

sealed trait InteractiveComponent extends ActionRowContent {
  def customId: String
}

sealed trait Button extends ActionRowContent {
  def tpe: ComponentType = ComponentType.Button

  def label: Option[String]
  def withLabel(label: String): Button

  def customId: Option[String]

  def style: ButtonStyle

  def emoji: Option[PartialEmoji]
  def withEmoji(emoji: PartialEmoji): Button

  def url: Option[String]

  def disabled: Option[Boolean]
  def withDisabled(disabled: Boolean): Button
}
object Button {

  def text(
      label: String,
      identifier: String = UUID.randomUUID().toString,
      style: TextButtonStyle = ButtonStyle.Secondary,
      emoji: Option[PartialEmoji] = None,
      disabled: Boolean = false
  ): TextButton = TextButton(Some(label), identifier, style, emoji, Some(disabled))

  def textEmoji(
      emoji: PartialEmoji,
      identifier: String = UUID.randomUUID().toString,
      style: TextButtonStyle = ButtonStyle.Secondary,
      disabled: Boolean = false
  ): TextButton = TextButton(None, identifier, style, Some(emoji), Some(disabled))

  def link(
      label: String,
      urlLink: String,
      emoji: Option[PartialEmoji] = None,
      disabled: Boolean = false
  ): LinkButton = LinkButton(Some(label), emoji, urlLink, Some(disabled))

  def linkEmoji(
      emoji: PartialEmoji,
      urlLink: String,
      disabled: Boolean = false
  ): LinkButton = LinkButton(None, Some(emoji), urlLink, Some(disabled))
}

case class RawButton(
    label: Option[String] = None,
    customId: Option[String] = None,
    style: ButtonStyle = ButtonStyle.Secondary,
    emoji: Option[PartialEmoji] = None,
    url: Option[String] = None,
    disabled: Option[Boolean] = None
) extends Button {
  override def withLabel(label: String): Button = copy(label = Some(label))

  override def withEmoji(emoji: PartialEmoji): Button = copy(emoji = Some(emoji))

  override def withDisabled(disabled: Boolean): Button = copy(disabled = Some(disabled))
}

case class TextButton(
    label: Option[String] = None,
    identifier: String = UUID.randomUUID().toString,
    style: TextButtonStyle = ButtonStyle.Secondary,
    emoji: Option[PartialEmoji] = None,
    disabled: Option[Boolean] = None
) extends Button {
  Verifier.requireLengthO(label, "Label", max = 80)
  Verifier.requireLength(identifier, "Identifier", max = 100)
  require(label.isDefined || emoji.isDefined, "Label or emoji must be defined")

  override def url: Option[String] = None

  override def customId: Option[String] = Some(identifier)

  override def withLabel(label: String): TextButton = copy(label = Some(label))

  def withIdentifier(identifier: String): TextButton = copy(identifier = identifier)

  def withStyle(style: TextButtonStyle): TextButton = copy(style = style)

  override def withEmoji(emoji: PartialEmoji): TextButton = copy(emoji = Some(emoji))

  override def withDisabled(disabled: Boolean): TextButton = copy(disabled = Some(disabled))
}

case class LinkButton(
    label: Option[String] = None,
    emoji: Option[PartialEmoji] = None,
    urlLink: String,
    disabled: Option[Boolean] = None
) extends Button {
  Verifier.requireLengthO(label, "Label", max = 80)
  require(label.isDefined || emoji.isDefined, "Label or emoji must be defined")
  Verifier.requireLength(urlLink, "Url", max = 512)

  override def customId: Option[String] = None

  override def style: ButtonStyle = ButtonStyle.Link

  override def url: Option[String] = Some(urlLink)

  override def withLabel(label: String): LinkButton = copy(label = Some(label))

  override def withEmoji(emoji: PartialEmoji): LinkButton = copy(emoji = Some(emoji))

  def withUrl(link: String): LinkButton = copy(urlLink = link)

  override def withDisabled(disabled: Boolean): LinkButton = copy(disabled = Some(disabled))
}

sealed abstract class ButtonStyle(val value: Int) extends IntEnumEntry
sealed trait TextButtonStyle                      extends ButtonStyle
object ButtonStyle extends IntEnum[ButtonStyle] with IntCirceEnumWithUnknown[ButtonStyle] {
  override def values: immutable.IndexedSeq[ButtonStyle] = findValues

  case object Primary   extends ButtonStyle(1) with TextButtonStyle // Blurple
  case object Secondary extends ButtonStyle(2) with TextButtonStyle // Gray
  case object Success   extends ButtonStyle(3) with TextButtonStyle // Green
  case object Danger    extends ButtonStyle(4) with TextButtonStyle // Red
  case object Link      extends ButtonStyle(5)

  case class Unknown(id: Int) extends ButtonStyle(id)

  override def createUnknown(value: Int): ButtonStyle = Unknown(value)
}

case class StringSelect(
    options: Seq[SelectOption],
    placeholder: Option[String] = None,
    customId: String = UUID.randomUUID().toString,
    minValues: Int = 1,
    maxValues: Int = 1,
    disabled: Boolean = false
) extends InteractiveComponent {
  Verifier.requireLength(customId, "Custom id", max = 100)
  Verifier.requireLengthS(options, "Options", max = 25)
  Verifier.requireLengthO(placeholder, "Placeholder", max = 100)
  Verifier.requireRange(minValues, "Min values", min = 0, max = 25)
  Verifier.requireRange(maxValues, "Max values", min = 0, max = 25)

  override def tpe: ComponentType = ComponentType.StringSelect
}

case class UserSelect(
    placeholder: Option[String] = None,
    customId: String = UUID.randomUUID().toString,
    minValues: Int = 1,
    maxValues: Int = 1,
    disabled: Boolean = false
) extends InteractiveComponent {
  Verifier.requireLength(customId, "Custom id", max = 100)
  Verifier.requireLengthO(placeholder, "Placeholder", max = 100)
  Verifier.requireRange(minValues, "Min values", min = 0, max = 25)
  Verifier.requireRange(maxValues, "Max values", min = 0, max = 25)

  override def tpe: ComponentType = ComponentType.UserSelect
}

case class RoleSelect(
    placeholder: Option[String] = None,
    customId: String = UUID.randomUUID().toString,
    minValues: Int = 1,
    maxValues: Int = 1,
    disabled: Boolean = false
) extends InteractiveComponent {
  Verifier.requireLength(customId, "Custom id", max = 100)
  Verifier.requireLengthO(placeholder, "Placeholder", max = 100)
  Verifier.requireRange(minValues, "Min values", min = 0, max = 25)
  Verifier.requireRange(maxValues, "Max values", min = 0, max = 25)

  override def tpe: ComponentType = ComponentType.RoleSelect
}

case class MentionableSelect(
    placeholder: Option[String] = None,
    customId: String = UUID.randomUUID().toString,
    minValues: Int = 1,
    maxValues: Int = 1,
    disabled: Boolean = false
) extends InteractiveComponent {
  Verifier.requireLength(customId, "Custom id", max = 100)
  Verifier.requireLengthO(placeholder, "Placeholder", max = 100)
  Verifier.requireRange(minValues, "Min values", min = 0, max = 25)
  Verifier.requireRange(maxValues, "Max values", min = 0, max = 25)

  override def tpe: ComponentType = ComponentType.MentionableSelect
}

case class ChannelSelect(
    placeholder: Option[String] = None,
    customId: String = UUID.randomUUID().toString,
    minValues: Int = 1,
    maxValues: Int = 1,
    channelTypes: Option[Seq[ChannelType]] = None,
    disabled: Boolean = false
) extends InteractiveComponent {
  Verifier.requireLength(customId, "Custom id", max = 100)
  Verifier.requireLengthO(placeholder, "Placeholder", max = 100)
  Verifier.requireRange(minValues, "Min values", min = 0, max = 25)
  Verifier.requireRange(maxValues, "Max values", min = 0, max = 25)

  override def tpe: ComponentType = ComponentType.ChannelSelect
}

case class SelectOption(
    label: String,
    value: String,
    description: Option[String] = None,
    emoji: Option[PartialEmoji] = None,
    default: Boolean = false
) {
  Verifier.requireLength(label, "Select option label", max = 100)
  Verifier.requireLength(value, "Select option value", max = 100)
  Verifier.requireLengthO(description, "Select option description", max = 100)
}
object SelectOption {
  def of(
      content: String,
      description: Option[String] = None,
      emoji: Option[PartialEmoji] = None,
      default: Boolean = false
  ): SelectOption = new SelectOption(content, content, description, emoji, default)
}

sealed abstract class ComponentType(val value: Int) extends IntEnumEntry
object ComponentType extends IntEnum[ComponentType] with IntCirceEnumWithUnknown[ComponentType] {
  override def values: immutable.IndexedSeq[ComponentType] = findValues

  case object ActionRow         extends ComponentType(1)
  case object Button            extends ComponentType(2)
  case object StringSelect      extends ComponentType(3)
  case object InputText         extends ComponentType(4)
  case object UserSelect        extends ComponentType(5)
  case object RoleSelect        extends ComponentType(6)
  case object MentionableSelect extends ComponentType(7)
  case object ChannelSelect     extends ComponentType(8)

  case class Unknown(id: Int) extends ComponentType(id)

  override def createUnknown(value: Int): ComponentType = Unknown(value)
}
