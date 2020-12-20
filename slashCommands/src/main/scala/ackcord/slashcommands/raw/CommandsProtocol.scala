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
package ackcord.slashcommands.raw

import ackcord.data.DiscordProtocol
import io.circe._
import io.circe.syntax._
import cats.syntax.all._

trait CommandsProtocol extends DiscordProtocol {
  implicit val applicationCommandCodec: Codec[ApplicationCommand] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)
  implicit val applicationCommandOptionCodec: Codec[ApplicationCommandOption] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)
  implicit val interactionCodec: Codec[RawInteraction] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)
  implicit val applicationCommandInteractionDataCodec: Codec[ApplicationCommandInteractionData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)
  implicit val interactionResponseCodec: Codec[InteractionResponse] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)
  implicit val interactionApplicationCommandCallbackDataCodec: Codec[InteractionApplicationCommandCallbackData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val applicationCommandOptionChoiceCodec: Codec[ApplicationCommandOptionChoice] = Codec.from(
    (c: HCursor) =>
      for {
        name  <- c.get[String]("name")
        value <- c.get[String]("value").map(Left(_)).orElse(c.get[Int]("value").map(Right(_)))
      } yield ApplicationCommandOptionChoice(name, value),
    (a: ApplicationCommandOptionChoice) => Json.obj("name" := a.name, "value" := a.value.fold(_.asJson, _.asJson))
  )

  implicit val applicationCommandInteractionDataOptionCodec: Codec[ApplicationCommandInteractionDataOption] = {
    import ApplicationCommandInteractionDataOption._
    Codec.from(
      (c: HCursor) =>
        for {
          name    <- c.get[String]("name")
          value   <- c.get[Option[Json]]("value")
          options <- c.get[Option[Seq[ApplicationCommandInteractionDataOption]]]("options")
          res <- (value, options) match {
            case (Some(value), None)   => Right(ApplicationCommandInteractionDataOptionWithValue(name, value))
            case (None, Some(options)) => Right(ApplicationCommandInteractionDataOptionWithOptions(name, options))
            case (Some(_), Some(_)) | (None, None) =>
              Left(DecodingFailure("Expected either value or options", c.history))
          }
        } yield res,
      {
        case ApplicationCommandInteractionDataOptionWithValue(name, value) => Json.obj("name" := name, "value" := value)
        case ApplicationCommandInteractionDataOptionWithOptions(name, options) =>
          Json.obj("name" := name, "options" := options)
      }
    )
  }
}
object CommandsProtocol extends CommandsProtocol
