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
package ackcord.requests

import ackcord.data.raw.RawGuild
import ackcord.data.{ImageData, Template}
import ackcord.data.DiscordProtocol._
import io.circe.{Decoder, Encoder, derivation}

case class GetTemplate(code: String) extends NoParamsRequest[Template, Template] {
  override def route: RequestRoute = Routes.getTemplate(code)

  override def responseDecoder: Decoder[Template] = Decoder[Template]

  override def toNiceResponse(response: Template): Template = response
}

case class CreateGuildFromTemplateData(
    name: String,
    icon: Option[ImageData]
)

case class CreateGuildFromTemplate(code: String, params: CreateGuildFromTemplateData)
    extends NoNiceResponseRequest[CreateGuildFromTemplateData, RawGuild] {
  override def route: RequestRoute = Routes.createGuildFromTemplate(code)

  override def responseDecoder: Decoder[RawGuild] = Decoder[RawGuild]

  override def paramsEncoder: Encoder[CreateGuildFromTemplateData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)
}
