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
package ackcord.slashcommands

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

import ackcord.OptFuture
import ackcord.data._
import ackcord.data.raw.RawMessage
import ackcord.requests._
import ackcord.slashcommands.raw.{
  ApplicationCommandOptionChoice,
  ApplicationCommandOptionType,
  InteractionApplicationCommandCallbackData
}
import ackcord.util.{JsonOption, JsonUndefined}
import akka.NotUsed
import cats.Id
import io.circe.{DecodingFailure, Json}

trait SlashCommandControllerBase[BaseInteraction[A] <: CommandInteraction[A]] {
  def requests: Requests

  implicit def executionContext: ExecutionContext = requests.system.executionContext

  def Command: CommandBuilder[BaseInteraction, NotUsed]

  private val userRegex: Regex    = """<@!?(\d+)>""".r
  private val channelRegex: Regex = """<#(\d+)>""".r
  private val roleRegex: Regex    = """<@&(\d+)>""".r

  private def parseMention[A](regex: Regex)(s: String): Either[DecodingFailure, SnowflakeType[A]] = s match {
    case regex(id) => Right(SnowflakeType(id))
    case _         => Left(DecodingFailure("Not a valid mention", Nil))
  }

  def string(name: String, description: String): ChoiceParam[String, String, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.String,
      name,
      description,
      (name, str) => ApplicationCommandOptionChoice(name, Left(str)),
      _.hcursor.as[String]
    )

  def int(name: String, description: String): ChoiceParam[Int, Int, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.Integer,
      name,
      description,
      (name, i) => ApplicationCommandOptionChoice(name, Right(i)),
      _.hcursor.as[Int]
    )

  def bool(name: String, description: String): ValueParam[Boolean, Boolean, Id] =
    ValueParam.default(ApplicationCommandOptionType.Boolean, name, description, _.hcursor.as[Boolean])

  def user(name: String, description: String): ValueParam[UserId, UserId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.User,
      name,
      description,
      _.hcursor.as[String].flatMap(parseMention(userRegex))
    )

  def channel(name: String, description: String): ValueParam[ChannelId, ChannelId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.Channel,
      name,
      description,
      _.hcursor.as[String].flatMap(parseMention(channelRegex))
    )

  def role(name: String, description: String): ValueParam[RoleId, RoleId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.Role,
      name,
      description,
      _.hcursor.as[String].flatMap(parseMention(roleRegex))
    )

  def async(handle: AsyncToken => OptFuture[_])(implicit interaction: CommandInteraction[_]): CommandResponse =
    CommandResponse.Acknowledge(() => handle(AsyncToken.fromInteraction(interaction)))

  def acknowledge: CommandResponse =
    CommandResponse.Acknowledge(() => OptFuture.unit)

  def sendMessage(
      content: String,
      tts: Option[Boolean] = None,
      embeds: Seq[OutgoingEmbed] = Nil,
      allowedMentions: Option[AllowedMention] = None
  ): CommandResponse.AsyncMessageable = CommandResponse.ChannelMessage(
    InteractionApplicationCommandCallbackData(tts, Some(content), embeds, allowedMentions),
    () => OptFuture.unit
  )

  def sendEmbed(
      embeds: Seq[OutgoingEmbed],
      content: Option[String] = None,
      tts: Option[Boolean] = None,
      allowedMentions: Option[AllowedMention] = None
  ): CommandResponse.AsyncMessageable = CommandResponse.ChannelMessage(
    InteractionApplicationCommandCallbackData(tts, content, embeds, allowedMentions),
    () => OptFuture.unit
  )

  private def commandRequest[Response](request: Request[Response]): OptFuture[Response] =
    OptFuture.fromFuture(requests.singleFutureSuccess(request))

  def sendAsyncMessage(
      content: String,
      tts: Option[Boolean] = None,
      files: Seq[CreateMessageFile] = Seq.empty,
      embeds: Seq[OutgoingEmbed] = Nil,
      allowedMentions: Option[AllowedMention] = None
  )(implicit async: AsyncToken): OptFuture[RawMessage] =
    commandRequest(
      CreateFollowupMessage(
        async.webhookId,
        async.webhookToken,
        params = ExecuteWebhookData(
          content,
          None,
          None,
          tts,
          files,
          embeds,
          allowedMentions
        )
      )
    ).map(_.get)

  def sendAsyncEmbed(
      embeds: Seq[OutgoingEmbed],
      content: String = "",
      tts: Option[Boolean] = None,
      files: Seq[CreateMessageFile] = Seq.empty,
      allowedMentions: Option[AllowedMention] = None
  )(implicit async: AsyncToken): OptFuture[RawMessage] =
    commandRequest(
      CreateFollowupMessage(
        async.webhookId,
        async.webhookToken,
        params = ExecuteWebhookData(
          content,
          None,
          None,
          tts,
          files,
          embeds,
          allowedMentions
        )
      )
    ).map(_.get)

  def editOriginalMessage(
      content: JsonOption[String] = JsonUndefined,
      embeds: JsonOption[Seq[OutgoingEmbed]] = JsonUndefined,
      files: JsonOption[Seq[CreateMessageFile]] = JsonUndefined,
      allowedMentions: JsonOption[AllowedMention] = JsonUndefined
  )(implicit async: AsyncMessageToken): OptFuture[Json] =
    commandRequest(
      EditOriginalWebhookMessage(
        async.webhookId,
        async.webhookToken,
        EditWebhookMessageData(
          content,
          embeds,
          files,
          allowedMentions
        )
      )
    )

  def deleteOriginalMessage(implicit async: AsyncMessageToken): OptFuture[NotUsed] = commandRequest(
    DeleteOriginalWebhookMessage(async.webhookId, async.webhookToken)
  )

  def editPreviousMessage(
      messageId: MessageId,
      content: JsonOption[String] = JsonUndefined,
      embeds: JsonOption[Seq[OutgoingEmbed]] = JsonUndefined,
      files: JsonOption[Seq[CreateMessageFile]] = JsonUndefined,
      allowedMentions: JsonOption[AllowedMention] = JsonUndefined
  )(implicit async: AsyncToken): OptFuture[Json] =
    commandRequest(
      EditWebhookMessage(
        async.webhookId,
        async.webhookToken,
        messageId,
        EditWebhookMessageData(
          content,
          embeds,
          files,
          allowedMentions
        )
      )
    )
}
