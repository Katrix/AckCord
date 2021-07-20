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

import ackcord.data._
import ackcord.data.DiscordProtocol._
import ackcord.requests._
import ackcord.interactions.commands.CommandOrGroup
import ackcord.util.{JsonOption, JsonUndefined}
import io.circe._
import io.circe.syntax._

case class CreateCommandData(
    name: String,
    description: String,
    options: Seq[ApplicationCommandOption]
) {
  require(name.nonEmpty, "Command name too short. Minimum length is 1")
  require(description.nonEmpty, "Command description too short. Minimum length is 1")
  require(description.length <= 100, "Command description too short. Maximum length is 100")
  require(name.matches("""^[\w-]{1,32}$"""), "Invalid command name")

}
object CreateCommandData {
  implicit val encoder: Encoder[CreateCommandData] = derivation.deriveEncoder(derivation.renaming.snakeCase, None)

  def fromCommand(command: CommandOrGroup): CreateCommandData =
    CreateCommandData(
      command.name,
      command.description,
      command.makeCommandOptions
    )
}

case class PatchCommandData(
    name: JsonOption[String] = JsonUndefined,
    description: JsonOption[String] = JsonUndefined,
    options: JsonOption[Seq[ApplicationCommandOption]] = JsonUndefined
) {
  require(name.nonEmpty, "Command name too short. Minimum length is 1")
  require(description.nonEmpty, "Command description too short. Minimum length is 1")
  require(description.forall(_.length <= 100), "Command description too short. Maximum length is 100")
  require(name.forall(_.matches("""^[\w-]{1,32}$""")), "Name is invalid")
}
object PatchCommandData {
  implicit val encoder: Encoder[PatchCommandData] = (a: PatchCommandData) =>
    JsonOption.removeUndefinedToObj(
      "name"        -> a.name.toJson,
      "description" -> a.description.toJson,
      "options"     -> a.options.toJson
    )
}

case class GetGlobalCommands(applicationId: ApplicationId) extends NoParamsNiceResponseRequest[Seq[ApplicationCommand]] {
  override def route: RequestRoute                               = InteractionRoutes.getCommands(applicationId)
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}
case class GetGlobalCommand(applicationId: ApplicationId, commandId: CommandId)
    extends NoParamsNiceResponseRequest[ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.getCommand(applicationId, commandId)
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class CreateGlobalCommand(applicationId: ApplicationId, params: CreateCommandData)
    extends NoNiceResponseRequest[CreateCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.postCommand(applicationId)
  override def paramsEncoder: Encoder[CreateCommandData]    = CreateCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class BulkReplaceGlobalCommands(applicationId: ApplicationId, params: Seq[CreateCommandData])
    extends NoNiceResponseRequest[Seq[CreateCommandData], Seq[ApplicationCommand]] {
  override def route: RequestRoute                               = InteractionRoutes.putCommands(applicationId)
  override def paramsEncoder: Encoder[Seq[CreateCommandData]]    = Encoder[Seq[CreateCommandData]]
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}
case class PatchGlobalCommand(applicationId: ApplicationId, commandId: CommandId, params: PatchCommandData)
    extends NoNiceResponseRequest[PatchCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.patchCommand(applicationId, commandId)
  override def paramsEncoder: Encoder[PatchCommandData]     = PatchCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class DeleteGlobalCommand(applicationId: ApplicationId, commandId: CommandId) extends NoParamsResponseRequest {
  override def route: RequestRoute = InteractionRoutes.deleteCommand(applicationId, commandId)
}

case class GetGuildCommands(applicationId: ApplicationId, guildId: GuildId)
    extends NoParamsNiceResponseRequest[Seq[ApplicationCommand]] {
  override def route: RequestRoute                               = InteractionRoutes.getGuildCommands(applicationId, guildId)
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}
case class GetGuildCommand(applicationId: ApplicationId, guildId: GuildId, commandId: CommandId)
    extends NoParamsNiceResponseRequest[ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.getGuildCommand(applicationId, guildId, commandId)
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class CreateGuildCommand(applicationId: ApplicationId, guildId: GuildId, params: CreateCommandData)
    extends NoNiceResponseRequest[CreateCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.postGuildCommand(applicationId, guildId)
  override def paramsEncoder: Encoder[CreateCommandData]    = CreateCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class BulkReplaceGuildCommand(applicationId: ApplicationId, guildId: GuildId, params: Seq[CreateCommandData])
    extends NoNiceResponseRequest[Seq[CreateCommandData], Seq[ApplicationCommand]] {
  override def route: RequestRoute                               = InteractionRoutes.putGuildCommands(applicationId, guildId)
  override def paramsEncoder: Encoder[Seq[CreateCommandData]]    = Encoder[Seq[CreateCommandData]]
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}
case class PatchGuildCommand(
    applicationId: ApplicationId,
    guildId: GuildId,
    commandId: CommandId,
    params: PatchCommandData
) extends NoNiceResponseRequest[PatchCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = InteractionRoutes.patchGuildCommand(applicationId, guildId, commandId)
  override def paramsEncoder: Encoder[PatchCommandData]     = PatchCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class DeleteGuildCommand(applicationId: ApplicationId, guildId: GuildId, commandId: CommandId)
    extends NoParamsResponseRequest {
  override def route: RequestRoute = InteractionRoutes.deleteGuildCommand(applicationId, guildId, commandId)
}

case class CreateInteractionResponse(
    applicationId: InteractionId,
    token: String,
    params: RawInteractionResponse
) extends NoResponseRequest[RawInteractionResponse] {
  override def paramsEncoder: Encoder[RawInteractionResponse] = Encoder[RawInteractionResponse]

  override def route: RequestRoute = InteractionRoutes.callback(applicationId, token)
}
