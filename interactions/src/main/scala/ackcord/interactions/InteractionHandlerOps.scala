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
package ackcord.interactions

import scala.concurrent.ExecutionContext

import ackcord.OptFuture
import ackcord.data._
import ackcord.data.raw.RawMessage
import ackcord.requests._
import ackcord.util.{JsonOption, JsonUndefined}
import akka.NotUsed
import io.circe.Json

trait InteractionHandlerOps {
  def requests: Requests

  implicit def executionContext: ExecutionContext = requests.system.executionContext

  def async(handle: AsyncToken => OptFuture[_])(implicit interaction: Interaction): InteractionResponse =
    InteractionResponse.Acknowledge(() => handle(AsyncToken.fromInteraction(interaction)))

  def acknowledge: InteractionResponse =
    InteractionResponse.Acknowledge(() => OptFuture.unit)

  def sendMessage(
      content: String,
      tts: Option[Boolean] = None,
      embeds: Seq[OutgoingEmbed] = Nil,
      allowedMentions: Option[AllowedMention] = None,
      flags: MessageFlags = MessageFlags.None,
      components: Seq[ActionRow] = Nil,
      attachments: Option[Seq[PartialAttachment]] = None
  ): InteractionResponse.AsyncMessageable = InteractionResponse.ChannelMessage(
    RawInteractionApplicationCommandCallbackData(
      tts,
      Some(content),
      embeds,
      allowedMentions,
      flags,
      if (components.isEmpty) None else Some(components),
      attachments
    ),
    () => OptFuture.unit
  )

  def sendEmbed(
      embeds: Seq[OutgoingEmbed],
      content: Option[String] = None,
      tts: Option[Boolean] = None,
      allowedMentions: Option[AllowedMention] = None,
      flags: MessageFlags = MessageFlags.None,
      components: Seq[ActionRow] = Nil,
      attachments: Option[Seq[PartialAttachment]] = None
  ): InteractionResponse.AsyncMessageable = InteractionResponse.ChannelMessage(
    RawInteractionApplicationCommandCallbackData(
      tts,
      content,
      embeds,
      allowedMentions,
      flags,
      if (components.isEmpty) None else Some(components),
      attachments
    ),
    () => OptFuture.unit
  )

  private def interactionRequest[Response](request: Request[Response]): OptFuture[Response] =
    OptFuture.fromFuture(requests.singleFutureSuccess(request))

  def sendAsyncMessage(
      content: String,
      tts: Option[Boolean] = None,
      files: Seq[CreateMessageFile] = Seq.empty,
      embeds: Seq[OutgoingEmbed] = Nil,
      allowedMentions: Option[AllowedMention] = None,
      components: Seq[ActionRow] = Nil
  )(implicit async: AsyncToken): OptFuture[RawMessage] =
    interactionRequest(
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
          allowedMentions,
          if (components.isEmpty) None else Some(components)
        )
      )
    ).map(_.get)

  def sendAsyncEmbed(
      embeds: Seq[OutgoingEmbed],
      content: String = "",
      tts: Option[Boolean] = None,
      files: Seq[CreateMessageFile] = Seq.empty,
      allowedMentions: Option[AllowedMention] = None,
      components: Seq[ActionRow] = Nil
  )(implicit async: AsyncToken): OptFuture[RawMessage] =
    interactionRequest(
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
          allowedMentions,
          if (components.isEmpty) None else Some(components)
        )
      )
    ).map(_.get)

  def getOriginalMessage()(implicit async: AsyncMessageToken): OptFuture[Message] = interactionRequest(
    GetOriginalWebhookMessage(async.webhookId, async.webhookToken)
  ).map(_.toMessage)

  def editOriginalMessage(
      content: JsonOption[String] = JsonUndefined,
      embeds: JsonOption[Seq[OutgoingEmbed]] = JsonUndefined,
      files: JsonOption[Seq[CreateMessageFile]] = JsonUndefined,
      allowedMentions: JsonOption[AllowedMention] = JsonUndefined,
      components: JsonOption[Seq[ActionRow]] = JsonUndefined
  )(implicit async: AsyncMessageToken): OptFuture[Json] =
    interactionRequest(
      EditOriginalWebhookMessage(
        async.webhookId,
        async.webhookToken,
        EditWebhookMessageData(
          content,
          embeds,
          files,
          allowedMentions,
          components
        )
      )
    )

  def deleteOriginalMessage(implicit async: AsyncMessageToken): OptFuture[NotUsed] = interactionRequest(
    DeleteOriginalWebhookMessage(async.webhookId, async.webhookToken)
  )

  def editPreviousMessage(
      messageId: MessageId,
      content: JsonOption[String] = JsonUndefined,
      embeds: JsonOption[Seq[OutgoingEmbed]] = JsonUndefined,
      files: JsonOption[Seq[CreateMessageFile]] = JsonUndefined,
      allowedMentions: JsonOption[AllowedMention] = JsonUndefined,
      components: JsonOption[Seq[ActionRow]] = JsonUndefined
  )(implicit async: AsyncToken): OptFuture[Json] =
    interactionRequest(
      EditWebhookMessage(
        async.webhookId,
        async.webhookToken,
        messageId,
        EditWebhookMessageData(
          content,
          embeds,
          files,
          allowedMentions,
          components
        )
      )
    )

  def getPreviousMessage(messageId: MessageId)(implicit async: AsyncMessageToken): OptFuture[Message] =
    interactionRequest(
      GetWebhookMessage(async.webhookId, async.webhookToken, messageId)
    ).map(_.toMessage)
}
