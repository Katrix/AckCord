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
package ackcord.interactions.raw

import ackcord.data.DiscordProtocol._
import ackcord.data._
import ackcord.interactions.commands.CreatedApplicationCommand
import ackcord.requests._
import ackcord.util.{JsonOption, JsonUndefined}
import akka.http.scaladsl.model.RequestEntity
import io.circe._

case class CreateCommandData(
    name: String,
    description: Option[String],
    options: Seq[ApplicationCommandOption],
    defaultPermission: Boolean = true,
    `type`: Option[ApplicationCommandType]
) {
  require(name.nonEmpty, "Command name too short. Minimum length is 1")
  require(description.forall(_.length <= 100), "Command description too short. Maximum length is 100")
  require(name.matches("""^[\w-]{1,32}$"""), "Invalid command name")

}
object CreateCommandData {
  implicit val encoder: Encoder[CreateCommandData] = derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  def fromCommand(command: CreatedApplicationCommand): CreateCommandData =
    CreateCommandData(
      command.name,
      command.description,
      command.makeCommandOptions,
      command.defaultPermission,
      Some(command.commandType)
    )
}

case class PatchCommandData(
    name: JsonOption[String] = JsonUndefined,
    description: JsonOption[String] = JsonUndefined,
    options: JsonOption[Seq[ApplicationCommandOption]] = JsonUndefined,
    defaultPermission: JsonOption[Boolean] = JsonUndefined
) {
  require(name.nonEmpty, "Command name too short. Minimum length is 1")
  require(description.nonEmpty, "Command description too short. Minimum length is 1")
  require(description.forall(_.length <= 100), "Command description too short. Maximum length is 100")
  require(name.forall(_.matches("""^[\w-]{1,32}$""")), "Name is invalid")
}
object PatchCommandData {
  implicit val encoder: Encoder[PatchCommandData] = (a: PatchCommandData) =>
    JsonOption.removeUndefinedToObj(
      "name"               -> a.name.toJson,
      "description"        -> a.description.toJson,
      "options"            -> a.options.toJson,
      "default_permission" -> a.defaultPermission.toJson
    )
}

case class GetGlobalApplicationCommands(applicationId: ApplicationId)
    extends NoParamsNiceResponseRequest[Seq[ApplicationCommand]] {
  override def route: RequestRoute                               = InteractionRoutes.getCommands(applicationId)
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}
case class CreateGlobalApplicationCommand(applicationId: ApplicationId, params: CreateCommandData)
    extends NoNiceResponseRequest[CreateCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.postCommand(applicationId)
  override def paramsEncoder: Encoder[CreateCommandData]    = CreateCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class GetGlobalApplicationCommand(applicationId: ApplicationId, commandId: CommandId)
    extends NoParamsNiceResponseRequest[ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.getCommand(applicationId, commandId)
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class EditGlobalApplicationCommand(applicationId: ApplicationId, commandId: CommandId, params: PatchCommandData)
    extends NoNiceResponseRequest[PatchCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.patchCommand(applicationId, commandId)
  override def paramsEncoder: Encoder[PatchCommandData]     = PatchCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class DeleteGlobalApplicationCommand(applicationId: ApplicationId, commandId: CommandId)
    extends NoParamsResponseRequest {
  override def route: RequestRoute = InteractionRoutes.deleteCommand(applicationId, commandId)
}
case class BulkOverwriteGlobalApplicationCommands(applicationId: ApplicationId, params: Seq[CreateCommandData])
    extends NoNiceResponseRequest[Seq[CreateCommandData], Seq[ApplicationCommand]] {
  override def route: RequestRoute                               = InteractionRoutes.putCommands(applicationId)
  override def paramsEncoder: Encoder[Seq[CreateCommandData]]    = Encoder[Seq[CreateCommandData]]
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}

case class GetGuildApplicationCommands(applicationId: ApplicationId, guildId: GuildId)
    extends NoParamsNiceResponseRequest[Seq[ApplicationCommand]] {
  override def route: RequestRoute = InteractionRoutes.getGuildCommands(applicationId, guildId)
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}
case class CreateGuildApplicationCommand(applicationId: ApplicationId, guildId: GuildId, params: CreateCommandData)
    extends NoNiceResponseRequest[CreateCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.postGuildCommand(applicationId, guildId)
  override def paramsEncoder: Encoder[CreateCommandData]    = CreateCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class GetGuildApplicationCommand(applicationId: ApplicationId, guildId: GuildId, commandId: CommandId)
    extends NoParamsNiceResponseRequest[ApplicationCommand] {
  override def route: RequestRoute = InteractionRoutes.getGuildCommand(applicationId, guildId, commandId)
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class EditGuildApplicationCommand(
    applicationId: ApplicationId,
    guildId: GuildId,
    commandId: CommandId,
    params: PatchCommandData
) extends NoNiceResponseRequest[PatchCommandData, ApplicationCommand] {
  override def route: RequestRoute = InteractionRoutes.patchGuildCommand(applicationId, guildId, commandId)
  override def paramsEncoder: Encoder[PatchCommandData]     = PatchCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class DeleteGuildApplicationCommand(applicationId: ApplicationId, guildId: GuildId, commandId: CommandId)
    extends NoParamsResponseRequest {
  override def route: RequestRoute = InteractionRoutes.deleteGuildCommand(applicationId, guildId, commandId)
}
case class BulkOverwriteGuildApplicationCommands(
    applicationId: ApplicationId,
    guildId: GuildId,
    params: Seq[CreateCommandData]
) extends NoNiceResponseRequest[Seq[CreateCommandData], Seq[ApplicationCommand]] {
  override def route: RequestRoute = InteractionRoutes.putGuildCommands(applicationId, guildId)
  override def paramsEncoder: Encoder[Seq[CreateCommandData]]    = Encoder[Seq[CreateCommandData]]
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}

case class CreateInteractionResponse(
    applicationId: InteractionId,
    token: String,
    params: RawInteractionResponse
) extends NoResponseRequest[RawInteractionResponse] {
  override def paramsEncoder: Encoder[RawInteractionResponse] = Encoder[RawInteractionResponse]

  override def requestBody: RequestEntity =
    /* TODO: Support files in here
    params.data.filter(_.files.nonEmpty).fold(super.requestBody) { data =>
      val jsonPart = FormData.BodyPart(
        "payload_json",
        HttpEntity(ContentTypes.`application/json`, jsonParams.printWith(jsonPrinter))
      )

      FormData(data.files.map(_.toBodyPart) :+ jsonPart: _*).toEntity()
    }
     */

    super.requestBody

  override def route: RequestRoute = InteractionRoutes.callback(applicationId, token)
}

case class GetGuildApplicationCommandPermissions(applicationId: ApplicationId, guildId: GuildId)
    extends NoParamsNiceResponseRequest[Seq[GuildApplicationCommandPermissions]] {
  override def route: RequestRoute = InteractionRoutes.getGuildCommandPermissions(applicationId, guildId)

  override def responseDecoder: Decoder[Seq[GuildApplicationCommandPermissions]] =
    Decoder[Seq[GuildApplicationCommandPermissions]]
}

case class GetApplicationCommandPermissions(applicationId: ApplicationId, guildId: GuildId, commandId: CommandId)
    extends NoParamsNiceResponseRequest[GuildApplicationCommandPermissions] {
  override def route: RequestRoute = InteractionRoutes.getCommandPermissions(applicationId, guildId, commandId)

  override def responseDecoder: Decoder[GuildApplicationCommandPermissions] =
    Decoder[GuildApplicationCommandPermissions]
}

case class EditCommandPermissionsData(permissions: Seq[ApplicationCommandPermissions]) {
  require(permissions.length <= 10, "At most 10 overrides can be used for a command")
}

case class EditApplicationCommandPermissions(
    applicationId: ApplicationId,
    guildId: GuildId,
    commandId: CommandId,
    params: EditCommandPermissionsData
) extends NoNiceResponseRequest[EditCommandPermissionsData, GuildApplicationCommandPermissions] {
  override def route: RequestRoute = InteractionRoutes.putCommandPermissions(applicationId, guildId, commandId)

  override def paramsEncoder: Encoder[EditCommandPermissionsData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase, None)
  override def responseDecoder: Decoder[GuildApplicationCommandPermissions] =
    Decoder[GuildApplicationCommandPermissions]
}

case class BatchEditCommandPermissionsData(id: CommandId, permissions: Seq[ApplicationCommandPermissions]) {
  require(permissions.length <= 10, "At most 10 overrides can be used for a command")
}

case class BatchEditApplicationCommandPermissions(
    applicationId: ApplicationId,
    guildId: GuildId,
    params: Seq[BatchEditCommandPermissionsData]
) extends NoNiceResponseRequest[Seq[BatchEditCommandPermissionsData], Seq[GuildApplicationCommandPermissions]] {
  override def route: RequestRoute = InteractionRoutes.putCommandsPermissions(applicationId, guildId)

  override def paramsEncoder: Encoder[Seq[BatchEditCommandPermissionsData]] = {
    implicit val single: Encoder[BatchEditCommandPermissionsData] =
      derivation.deriveEncoder(derivation.renaming.snakeCase, None)
    implicitly
  }
  override def responseDecoder: Decoder[Seq[GuildApplicationCommandPermissions]] =
    Decoder[Seq[GuildApplicationCommandPermissions]]
}
