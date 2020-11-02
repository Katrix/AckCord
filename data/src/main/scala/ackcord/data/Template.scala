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

import ackcord.data.raw.RawGuild

/**
  * @param code The code of the template
  * @param name Name of the template
  * @param description Description of the template
  * @param usageCount How many times the template has been used
  * @param creatorId Id of the user who created the template
  * @param creator Creator of the template
  * @param createdAt When the template was created
  * @param updatedAt When the template was updated
  * @param sourceGuildId What was the source of the template
  * @param serializedSourceGuild A snapshot of the guild this template was created from
  * @param isDirty If the template has unsaved changes
  */
case class Template(
    code: String,
    name: String,
    description: Option[String],
    usageCount: Int,
    creatorId: UserId,
    creator: User,
    createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
    sourceGuildId: GuildId,
    serializedSourceGuild: RawGuild, //TODO: Partial
    isDirty: Option[Boolean]
)
