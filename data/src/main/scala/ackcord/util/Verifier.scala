package ackcord.util

object Verifier {

  /**
    * Verifies the length of a string in codepoints.
    * @param value
    *   The string to check.
    * @param name
    *   The name of the parameter it came from.
    * @param min
    *   The minimum length.
    * @param max
    *   The maximum length.
    */
  def requireLength(value: String, name: String, min: Int = -1, max: Int = -1): Unit = {
    if (min > 0) require(stringLength(value) >= min, s"$name must be $min chars or more")
    if (max > 0) require(stringLength(value) <= max, s"$name must be $max chars or less")
  }

  /**
    * Verifies the length of a string in codepoints.
    * @param value
    *   The string to check.
    * @param name
    *   The name of the parameter it came from.
    * @param min
    *   The minimum length.
    * @param max
    *   The maximum length.
    */
  def requireLengthO(value: Option[String], name: String, min: Int = -1, max: Int = -1): Unit = {
    if (min > 0) require(value.forall(stringLength(_) >= min), s"$name must be $min chars or more")
    if (max > 0) require(value.forall(stringLength(_) <= max), s"$name must be $max chars or less")
  }

  /**
    * Verifies the length of a string in codepoints.
    * @param value
    *   The string to check.
    * @param name
    *   The name of the parameter it came from.
    * @param min
    *   The minimum length.
    * @param max
    *   The maximum length.
    */
  def requireLengthJO(value: JsonOption[String], name: String, min: Int = -1, max: Int = -1): Unit =
    requireLengthO(value.toOption, name, min, max)

  /**
    * Verifies the length of a sequence.
    * @param seq
    *   The seq to check.
    * @param name
    *   The name of the parameter it came from.
    * @param min
    *   The minimum length.
    * @param max
    *   The maximum length.
    */
  def requireLengthS(seq: Seq[_], name: String, min: Int = -1, max: Int = -1): Unit = {
    if (min > 0) require(seq.length >= min, s"$name must be of length $min or more")
    if (max > 0) require(seq.length <= max, s"$name must be of length $max or less")
  }

  /**
    * Verifies the length of a sequence.
    * @param seq
    *   The seq to check.
    * @param name
    *   The name of the parameter it came from.
    * @param min
    *   The minimum length.
    * @param max
    *   The maximum length.
    */
  def requireLengthOS(seq: Option[Seq[_]], name: String, min: Int = -1, max: Int = -1)(
      implicit dummyImplicit: DummyImplicit
  ): Unit = {
    if (min > 0) require(seq.forall(_.length >= min), s"$name must be of length $min or more")
    if (max > 0) require(seq.forall(_.length <= max), s"$name must be of length $max or less")
  }

  /**
    * Verifies the length of a sequence.
    * @param seq
    *   The seq to check.
    * @param name
    *   The name of the parameter it came from.
    * @param min
    *   The minimum length.
    * @param max
    *   The maximum length.
    */
  def requireLengthJOS(seq: JsonOption[Seq[_]], name: String, min: Int = -1, max: Int = -1)(
      implicit dummyImplicit: DummyImplicit
  ): Unit =
    requireLengthOS(seq.toOption, name, min, max)

  /** Gets the length of a string in code points */
  def stringLength(s: String): Int = s.codePointCount(0, 0.max(s.length - 1))

  /**
    * Verifies that a number is in a range..
    * @param i
    *   The value to check.
    * @param name
    *   The name of the parameter it came from.
    * @param min
    *   The minimum value.
    * @param max
    *   The maximum value.
    */
  def requireRange(i: Int, name: String, min: Int = -1, max: Int = -1): Unit = {
    if (min > 0) require(i >= min, s"$name must be $min or more")
    if (max > 0) require(i <= max, s"$name must be $max or less")
  }

  /**
    * Verifies that a number is in a range..
    * @param i
    *   The value to check.
    * @param name
    *   The name of the parameter it came from.
    * @param min
    *   The minimum value.
    * @param max
    *   The maximum value.
    */
  def requireRangeO(i: Option[Int], name: String, min: Int = -1, max: Int = -1): Unit = {
    if (min > 0) require(i.forall(_ >= min), s"$name must be $min or more")
    if (max > 0) require(i.forall(_ <= max), s"$name must be $max or less")
  }

  /**
    * Verifies that a number is in a range..
    * @param i
    *   The value to check.
    * @param name
    *   The name of the parameter it came from.
    * @param min
    *   The minimum value.
    * @param max
    *   The maximum value.
    */
  def requireRangeJO(i: JsonOption[Int], name: String, min: Int = -1, max: Int = -1): Unit =
    requireRangeO(i.toOption, name, min, max)
}
