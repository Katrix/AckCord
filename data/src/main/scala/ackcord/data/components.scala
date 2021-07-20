package ackcord.data

import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.{IntEnum, IntEnumEntry}
import java.util.UUID

import scala.collection.immutable

case class ActionRow(
    components: Seq[Button]
) {
  require(components.size <= 5, "Too many components in ActionRow")

  def mapButtons(f: Button => Button): ActionRow = copy(components = components.map(f))

  def updateButton(identifier: String, f: TextButton => Button): ActionRow = copy(components = components.map {
    case button: TextButton if button.identifier == identifier => f(button)
    case button                                                => button
  })
}
object ActionRow {
  def of(buttons: Button*): ActionRow = new ActionRow(buttons)
}

sealed trait Button {

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
  require(label.forall(_.length <= 80), "Label must be 80 chars or less")
  require(identifier.length <= 100, "Identifier must be 100 chars or less")
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
  require(label.forall(_.length <= 80), "Label must be 80 chars or less")
  require(label.isDefined || emoji.isDefined, "Label or emoji must be defined")
  require(urlLink.length <= 512, "Url length must be 512 chars or less")

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
