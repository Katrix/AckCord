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

import java.util.UUID

import scala.concurrent.ExecutionContext

import ackcord.OptFuture
import ackcord.data._
import ackcord.data.raw.RawMessage
import ackcord.requests._
import ackcord.util.{JsonOption, JsonUndefined}
import akka.NotUsed
import io.circe.Json

/** Exposes an DSL of sorts for creating responses to an interaction. */
trait InteractionHandlerOps {
  def requests: Requests

  implicit def executionContext: ExecutionContext = requests.system.executionContext

  /**
    * Specify that the response to this interaction will be done async.
    * @param handle
    *   The action to do async.
    */
  def async(handle: AsyncToken => OptFuture[_])(implicit interaction: Interaction): InteractionResponse =
    InteractionResponse.Acknowledge(() => handle(AsyncToken.fromInteraction(interaction)))

  //TODO: Determine of this should be exposed or not
  //def acknowledge: InteractionResponse =
  //  InteractionResponse.Acknowledge(() => OptFuture.unit)

  /**
    * Send a message as response to the interaction with text content as the
    * primary thing.
    * @param content
    *   The content of the message.
    * @param tts
    *   If the message will be tts.
    * @param embeds
    *   The embeds of the message.
    * @param allowedMentions
    *   The allowed mentions of the message.
    * @param flags
    *   The flags of the message.
    * @param components
    *   The components of the message. // * @param attachments The attachments
    *   of the message. Not currently implemented.
    */
  def sendMessage(
      content: String,
      tts: Option[Boolean] = None,
      embeds: Seq[OutgoingEmbed] = Nil,
      allowedMentions: Option[AllowedMention] = None,
      flags: MessageFlags = MessageFlags.None,
      components: Seq[ActionRow] = Nil
      //attachments: Option[Seq[PartialAttachment]] = None
  ): InteractionResponse.AsyncMessageable = InteractionResponse.ChannelMessage(
    InteractionCallbackDataMessage(
      tts,
      Some(content),
      embeds,
      allowedMentions,
      flags,
      if (components.isEmpty) None else Some(components),
      attachments = None
    ),
    () => OptFuture.unit
  )

  def sendModal(): InteractionResponse = {
    InteractionResponse.ModalMessage(
      InteractionCallbackDataModal(
        UUID.randomUUID().toString,
        "Test Modal",
        Seq(
          ActionRow.ofInputs(
            TextInput(
              label = Some("Input 1")
            )
          ),
          ActionRow.ofInputs(
            TextInput(
              label = Some("Input 2"),
              style = TextInputStyle.Paragraph
            )
          )
        )
      )
    )
  }

  /**
    * Send a message as response to the interaction with embeds as the primary
    * thing.
    * @param content
    *   The content of the message.
    * @param tts
    *   If the message will be tts.
    * @param embeds
    *   The embeds of the message.
    * @param allowedMentions
    *   The allowed mentions of the message.
    * @param flags
    *   The flags of the message.
    * @param components
    *   The components of the message. // * @param attachments The attachments
    *   of the message. Not currently implemented.
    */
  def sendEmbed(
      embeds: Seq[OutgoingEmbed],
      content: Option[String] = None,
      tts: Option[Boolean] = None,
      allowedMentions: Option[AllowedMention] = None,
      flags: MessageFlags = MessageFlags.None,
      components: Seq[ActionRow] = Nil,
      attachments: Option[Seq[PartialAttachment]] = None
  ): InteractionResponse.AsyncMessageable = InteractionResponse.ChannelMessage(
    InteractionCallbackDataMessage(
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

  /**
    * Send an async message as part of the interaction with text content as the
    * primary thing.
    * @param content
    *   The content of the message.
    * @param tts
    *   If the message will be tts.
    * @param files
    *   The files to send with the message.
    * @param embeds
    *   The embeds of the message.
    * @param allowedMentions
    *   The allowed mentions of the message.
    * @param components
    *   The components of the message.
    */
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

  /**
    * Send an async message as part of the interaction with embeds as the
    * primary thing.
    * @param content
    *   The content of the message.
    * @param tts
    *   If the message will be tts.
    * @param files
    *   The files to send with the message.
    * @param embeds
    *   The embeds of the message.
    * @param allowedMentions
    *   The allowed mentions of the message.
    * @param components
    *   The components of the message.
    */
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

  /** Get the original message sent as a response to the interaction. */
  def getOriginalMessage()(implicit async: AsyncMessageToken): OptFuture[Message] = interactionRequest(
    GetOriginalWebhookMessage(async.webhookId, async.webhookToken)
  ).map(_.toMessage)

  /**
    * Edits the original message sent as a response to the interaction.
    * @param content
    *   The content of the message.
    * @param embeds
    *   The embeds of the message.
    * @param files
    *   The files of the message.
    * @param allowedMentions
    *   The allowed mentions of the message.
    * @param components
    *   The components of the message.
    */
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

  /** Delete the original message sent as a response to the interaction. */
  def deleteOriginalMessage(implicit async: AsyncMessageToken): OptFuture[NotUsed] = interactionRequest(
    DeleteOriginalWebhookMessage(async.webhookId, async.webhookToken)
  )

  /**
    * Edits a previous message sent as a part of the interaction.
    * @param messageId
    *   The message to edit.
    * @param content
    *   The content of the message.
    * @param embeds
    *   The embeds of the message.
    * @param files
    *   The files of the message.
    * @param allowedMentions
    *   The allowed mentions of the message.
    * @param components
    *   The components of the message.
    */
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

  /**
    * Get a previous message sent as a part of the interaction.
    * @param messageId
    *   The message to get.
    */
  def getPreviousMessage(messageId: MessageId)(implicit async: AsyncMessageToken): OptFuture[Message] =
    interactionRequest(
      GetWebhookMessage(async.webhookId, async.webhookToken, messageId)
    ).map(_.toMessage)
}
