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

package ackcord.util

import enumeratum.values.{IntEnum, IntEnumEntry, StringEnum, StringEnumEntry, ValueEnum, ValueEnumEntry}
import io.circe.Decoder.Result
import io.circe._

trait ValueEnumWithUnknown[ValueType, EntryType <: ValueEnumEntry[ValueType]] { self: ValueEnum[ValueType, EntryType] =>

  def createUnknown(value: ValueType): EntryType
}

trait IntCirceEnumWithUnknown[EntryType <: IntEnumEntry]
    extends IntEnum[EntryType]
    with ValueEnumWithUnknown[Int, EntryType] { self =>

  implicit val codec: Codec[EntryType] = new Codec[EntryType] {
    private val valueEncoder = implicitly[Encoder[Int]]
    private val valueDecoder = implicitly[Decoder[Int]]

    def apply(a: EntryType): Json = valueEncoder.apply(a.value)
    def apply(c: HCursor): Result[EntryType] =
      valueDecoder.apply(c).map(v => withValueOpt(v).getOrElse(createUnknown(v)))
  }
}

trait StringCirceEnumWithUnknown[EntryType <: StringEnumEntry]
    extends StringEnum[EntryType]
    with ValueEnumWithUnknown[String, EntryType] { self =>

  implicit val codec: Codec[EntryType] = new Codec[EntryType] {
    private val valueEncoder = implicitly[Encoder[String]]
    private val valueDecoder = implicitly[Decoder[String]]

    def apply(a: EntryType): Json = valueEncoder.apply(a.value)
    def apply(c: HCursor): Result[EntryType] =
      valueDecoder.apply(c).map(v => withValueOpt(v).getOrElse(createUnknown(v)))
  }
}
