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

import ackcord.data.raw.RawSticker
import ackcord.util.IntCirceEnumWithUnknown
import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class FormatType(val value: Int) extends IntEnumEntry
object FormatType extends IntEnum[FormatType] with IntCirceEnumWithUnknown[FormatType] {
  override def values: immutable.IndexedSeq[FormatType] = findValues

  case object PNG            extends FormatType(1)
  case object APNG           extends FormatType(2)
  case object LOTTIE         extends FormatType(3)
  case class Unknown(i: Int) extends FormatType(i)

  override def createUnknown(value: Int): FormatType = Unknown(value)
}

sealed abstract class StickerType(val value: Int) extends IntEnumEntry
object StickerType extends IntEnum[StickerType] with IntCirceEnumWithUnknown[StickerType] {
  override def values: immutable.IndexedSeq[StickerType] = findValues

  case object Standard       extends FormatType(1)
  case object Guild          extends FormatType(2)
  case class Unknown(i: Int) extends StickerType(i)

  override def createUnknown(value: Int): StickerType = Unknown(value)
}

/**
  * The structure of a sticker sent in a message.
  * @param id
  *   Id of the sticker.
  * @param packId
  *   Id of the pack the sticker is from.
  * @param name
  *   Name of the sticker.
  * @param description
  *   Description of the sticker.
  * @param tags
  *   A comma-separated list of tags for the sticker.
  * @param tpe`
  *   Type of the sticker.
  * @param formatType
  *   Type of sticker format.
  * @param available
  *   If this guild sticker can currently be used.
  * @param guildId
  *   Id of the guild that owns this sticker.
  * @param userId
  *   The id of the user that uploaded the sticker.
  * @param sortValue
  *   A standard sticker's sort value in it's pack.
  */
case class Sticker(
    id: StickerId,
    packId: Option[SnowflakeType[StickerPack]],
    name: String,
    description: Option[String],
    tags: Option[String],
    tpe: StickerType,
    formatType: FormatType,
    available: Option[Boolean],
    guildId: Option[GuildId],
    userId: Option[UserId],
    sortValue: Option[Int]
)

/**
  * The structure of a sticker item (the smallest amount of data required to
  * render a sticker)
  * @param id
  *   Id of the sticker.
  * @param name
  *   Name of the sticker.
  * @param formatType
  *   Type of sticker format.
  */
case class StickerItem(
    id: StickerId,
    name: String,
    formatType: FormatType
)

/**
  * A pack of standard stickers
  * @param id
  *   The id of the sticker pack.
  * @param stickers
  *   The stickers in the pack.
  * @param name
  *   The name of the pack.
  * @param skuId
  *   The id of the pack's SKU.
  * @param coverStickerId
  *   The id of a sticker in the pack which is shown as the pack's icon.
  * @param description
  *   A description of the pack.
  * @param bannerAssetId
  *   Id of the pack's banner image.
  */
case class StickerPack(
    id: SnowflakeType[StickerPack],
    stickers: Seq[RawSticker],
    name: String,
    skuId: RawSnowflake,
    coverStickerId: Option[RawSnowflake],
    description: String,
    bannerAssetId: RawSnowflake
)
