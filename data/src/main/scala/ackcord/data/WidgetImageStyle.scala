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

import scala.collection.immutable

import enumeratum.values.{StringEnum, StringEnumEntry}

/**
  * A style the widget image might be shown as.
  * See examples here. https://discordapp.com/developers/docs/resources/guild#get-guild-widget-image
  */
sealed abstract class WidgetImageStyle(val value: String) extends StringEnumEntry {
  @deprecated("Prefer value instead", since = "0.14.0")
  def name: String = value
}
object WidgetImageStyle extends StringEnum[WidgetImageStyle] {
  case object Shield  extends WidgetImageStyle("shield" )
  case object Banner1 extends WidgetImageStyle("banner1")
  case object Banner2 extends WidgetImageStyle("banner2")
  case object Banner3 extends WidgetImageStyle("banner3")
  case object Banner4 extends WidgetImageStyle("banner4")

  override def values: immutable.IndexedSeq[WidgetImageStyle] = findValues
}
