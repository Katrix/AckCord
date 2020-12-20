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

import ackcord.data._
import ackcord.requests._
import ackcord.slashcommands.raw.CommandsProtocol._
import ackcord.slashcommands.{CommandId, CommandOrGroup}
import ackcord.util.{JsonOption, JsonUndefined}
import io.circe._
import io.circe.syntax._

case class CreateCommandData(
    name: String,
    description: String,
    options: Seq[ApplicationCommandOption]
)
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
)
object PatchCommandData {
  implicit val encoder: Encoder[PatchCommandData] = (a: PatchCommandData) =>
    JsonOption.removeUndefinedToObj(
      "name"        -> a.name.map(_.asJson),
      "description" -> a.description.map(_.asJson),
      "options"     -> a.options.map(_.asJson)
    )
}

case class GetGlobalCommands(applicationId: RawSnowflake) extends NoParamsNiceResponseRequest[Seq[ApplicationCommand]] {
  override def route: RequestRoute                               = CommandRoutes.getCommands(applicationId)
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}
case class CreateGlobalCommand(applicationId: RawSnowflake, params: CreateCommandData)
    extends NoNiceResponseRequest[CreateCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = CommandRoutes.postCommand(applicationId)
  override def paramsEncoder: Encoder[CreateCommandData]    = CreateCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class PatchGlobalCommand(applicationId: RawSnowflake, commandId: CommandId, params: PatchCommandData)
    extends NoNiceResponseRequest[PatchCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = CommandRoutes.patchCommand(applicationId, commandId)
  override def paramsEncoder: Encoder[PatchCommandData]     = PatchCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class DeleteGlobalCommand(applicationId: RawSnowflake, commandId: CommandId) extends NoParamsResponseRequest {
  override def route: RequestRoute = CommandRoutes.deleteCommand(applicationId, commandId)
}

case class GetGuildCommands(applicationId: RawSnowflake, guildId: GuildId)
    extends NoParamsNiceResponseRequest[Seq[ApplicationCommand]] {
  override def route: RequestRoute                               = CommandRoutes.getGuildCommands(applicationId, guildId)
  override def responseDecoder: Decoder[Seq[ApplicationCommand]] = Decoder[Seq[ApplicationCommand]]
}
case class CreateGuildCommand(applicationId: RawSnowflake, guildId: GuildId, params: CreateCommandData)
    extends NoNiceResponseRequest[CreateCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = CommandRoutes.postGuildCommand(applicationId, guildId)
  override def paramsEncoder: Encoder[CreateCommandData]    = CreateCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class PatchGuildCommand(
    applicationId: RawSnowflake,
    guildId: GuildId,
    commandId: CommandId,
    params: PatchCommandData
) extends NoNiceResponseRequest[PatchCommandData, ApplicationCommand] {
  override def route: RequestRoute                          = CommandRoutes.patchGuildCommand(applicationId, guildId, commandId)
  override def paramsEncoder: Encoder[PatchCommandData]     = PatchCommandData.encoder
  override def responseDecoder: Decoder[ApplicationCommand] = Decoder[ApplicationCommand]
}
case class DeleteGuildCommand(applicationId: RawSnowflake, guildId: GuildId, commandId: CommandId)
    extends NoParamsResponseRequest {
  override def route: RequestRoute = CommandRoutes.deleteGuildCommand(applicationId, guildId, commandId)
}

case class InteractionCallback(
    applicationId: RawSnowflake,
    token: String,
    params: InteractionResponse
) extends NoResponseRequest[InteractionResponse] {
  override def paramsEncoder: Encoder[InteractionResponse] = Encoder[InteractionResponse]

  override def route: RequestRoute = CommandRoutes.callback(applicationId, token)
}
