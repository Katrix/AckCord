package ackcord.data

import java.time.Instant

sealed abstract class TimestampStyle(val flag: String) {
  def mentionTime(instant: Instant): String =
    s"<t:${instant.toEpochMilli / 1000}:$flag>"
}
object TimestampStyle {
  case object ShortTime extends TimestampStyle("t")
  case object LongTime extends TimestampStyle("T")
  case object ShortDate extends TimestampStyle("d")
  case object LongDate extends TimestampStyle("D")
  case object ShortDateTime extends TimestampStyle("f")
  case object LongDateTime extends TimestampStyle("F")
  case object RelativeTime extends TimestampStyle("R")

  final val Default: ShortDateTime.type = ShortDateTime
}
