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

import ackcord.CacheSnapshot
import ackcord.data.DiscordProtocol._
import ackcord.data.raw.RawGuild
import ackcord.data.{GuildId, GuildTemplate, ImageData, Permission}
import ackcord.util.{JsonOption, JsonSome, JsonUndefined, Verifier}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, derivation}

/** Get a template by it's code. */
case class GetGuildTemplate(code: String) extends NoParamsRequest[GuildTemplate, GuildTemplate] {
  override def route: RequestRoute = Routes.getTemplate(code)

  override def responseDecoder: Decoder[GuildTemplate] = Decoder[GuildTemplate]

  override def toNiceResponse(response: GuildTemplate): GuildTemplate = response
}

case class CreateGuildFromTemplateData(
    name: String,
    icon: Option[ImageData]
)

/** Create a guild from a guild template. */
case class CreateGuildFromTemplate(code: String, params: CreateGuildFromTemplateData)
    extends NoNiceResponseRequest[CreateGuildFromTemplateData, RawGuild] {
  override def route: RequestRoute = Routes.createGuildFromTemplate(code)

  override def responseDecoder: Decoder[RawGuild] = Decoder[RawGuild]

  override def paramsEncoder: Encoder[CreateGuildFromTemplateData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)
}

/** Gets the guild templates for a guild. */
case class GetGuildTemplates(guildId: GuildId) extends NoParamsNiceResponseRequest[Seq[GuildTemplate]] {
  override def responseDecoder: Decoder[Seq[GuildTemplate]] = Decoder[Seq[GuildTemplate]]

  override def route: RequestRoute = Routes.getGuildTemplates(guildId)

  override def requiredPermissions: Permission = Permission.ManageGuild

  override def hasPermissions(implicit c: CacheSnapshot): Boolean = hasPermissionsGuild(guildId, requiredPermissions)
}

/**
  * @param name
  *   Name of the template
  * @param description
  *   Description of the template
  */
case class CreateGuildTemplateData(name: String, description: JsonOption[String] = JsonUndefined) {
  Verifier.requireLength(name, "Name", min = 1, max = 100)
  Verifier.requireLengthJO(description, "Description", max = 120)
}

/** Create a guild template for the guild. */
case class CreateGuildTemplate(guildId: GuildId, params: CreateGuildTemplateData)
    extends NoNiceResponseRequest[CreateGuildTemplateData, GuildTemplate] {
  override def paramsEncoder: Encoder[CreateGuildTemplateData] =
    (a: CreateGuildTemplateData) =>
      JsonOption.removeUndefinedToObj(
        "name" -> JsonSome(a.name.asJson),
        "name" -> a.description.toJson
      )

  override def responseDecoder: Decoder[GuildTemplate] = Decoder[GuildTemplate]

  override def route: RequestRoute = Routes.postGuildTemplate(guildId)

  override def requiredPermissions: Permission = Permission.ManageGuild

  override def hasPermissions(implicit c: CacheSnapshot): Boolean = hasPermissionsGuild(guildId, requiredPermissions)
}

/** Syncs the template and the guild. */
case class SyncGuildTemplate(guildId: GuildId, code: String) extends NoParamsNiceResponseRequest[GuildTemplate] {
  override def responseDecoder: Decoder[GuildTemplate] = Decoder[GuildTemplate]

  override def route: RequestRoute = Routes.putGuildTemplate(guildId, code)

  override def requiredPermissions: Permission = Permission.ManageGuild

  override def hasPermissions(implicit c: CacheSnapshot): Boolean = hasPermissionsGuild(guildId, requiredPermissions)
}

case class ModifyGuildTemplateData(
    name: JsonOption[String] = JsonUndefined,
    description: JsonOption[String] = JsonUndefined
) {
  Verifier.requireLengthJO(name, "Name", min = 1, max = 100)
  Verifier.requireLengthJO(description, "Description", max = 120)
}

/** Modify the info around a guild template. */
case class ModifyGuildTemplate(guildId: GuildId, code: String, params: ModifyGuildTemplateData)
    extends NoNiceResponseRequest[ModifyGuildTemplateData, GuildTemplate] {

  override def paramsEncoder: Encoder[ModifyGuildTemplateData] =
    (a: ModifyGuildTemplateData) =>
      JsonOption.removeUndefinedToObj(
        "name"        -> a.name.toJson,
        "description" -> a.description.toJson
      )

  override def responseDecoder: Decoder[GuildTemplate] = Decoder[GuildTemplate]

  override def route: RequestRoute = Routes.patchGuildTemplate(guildId, code)

  override def requiredPermissions: Permission = Permission.ManageGuild

  override def hasPermissions(implicit c: CacheSnapshot): Boolean = hasPermissionsGuild(guildId, requiredPermissions)
}

/** Deletes the given guild template. */
case class DeleteGuildTemplate(guildId: GuildId, code: String) extends NoParamsNiceResponseRequest[GuildTemplate] {
  override def responseDecoder: Decoder[GuildTemplate] = Decoder[GuildTemplate]

  override def route: RequestRoute = Routes.deleteGuildTemplate(guildId, code)

  override def requiredPermissions: Permission = Permission.ManageGuild

  override def hasPermissions(implicit c: CacheSnapshot): Boolean = hasPermissionsGuild(guildId, requiredPermissions)
}
