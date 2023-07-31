package ackcord.interactions

import ackcord.data._
import ackcord.interactions
import ackcord.interactions.data.{InteractionRequests, InteractionResponse}
import ackcord.requests.EncodeBody
import ackcord.requests.base.Requests

trait InteractionHandlerOps[F[_]] {

  def requests: Requests[F, Any]

  protected def doNothing: F[Unit]

  // TODO: Make mixins for these to make them easier to create

  type MessageData = InteractionResponse.MessageData
  val MessageData: InteractionResponse.MessageData.type = InteractionResponse.MessageData

  type CreateFollowupMessageBody = InteractionRequests.CreateFollowupMessageBody
  val CreateFollowupMessageBody: InteractionRequests.CreateFollowupMessageBody.type =
    InteractionRequests.CreateFollowupMessageBody

  type EditOriginalInteractionResponseBody = InteractionRequests.EditOriginalInteractionResponseBody
  val EditOriginalInteractionResponseBody: InteractionRequests.EditOriginalInteractionResponseBody.type =
    InteractionRequests.EditOriginalInteractionResponseBody

  type EditFollowupMessageBody = InteractionRequests.EditFollowupMessageBody
  val EditFollowupMessageBody: InteractionRequests.EditFollowupMessageBody.type =
    InteractionRequests.EditFollowupMessageBody

  def async[A](interaction: Interaction)(handle: AsyncToken => F[_]): HighInteractionResponse[F] =
    interactions.HighInteractionResponse.Acknowledge(handle(AsyncToken.fromInteraction(interaction)))

  def sendMessage(data: MessageData): HighInteractionResponse.AsyncMessageable[F] =
    HighInteractionResponse.ChannelMessage(data, doNothing)

  def sendAsyncMessage(
      body: InteractionRequests.CreateFollowupMessageBody,
      parts: Seq[EncodeBody.Multipart[_, Any]] = Nil
  )(implicit async: AsyncToken): F[Message] =
    requests.runRequest(
      InteractionRequests.createFollowupMessage(async.applicationId, async.interactionToken, body, parts)
    )

  def getOriginalMessage(implicit async: AsyncMessageToken): F[Message] = requests.runRequest(
    InteractionRequests.getOriginalInteractionResponse(async.applicationId, async.interactionToken)
  )

  def editOriginalMessage(
      body: InteractionRequests.EditOriginalInteractionResponseBody,
      parts: Seq[EncodeBody.Multipart[_, Any]] = Nil
  )(implicit async: AsyncMessageToken): F[Message] = requests.runRequest(
    InteractionRequests.editOriginalInteractionResponse(async.applicationId, async.interactionToken, body, parts)
  )

  def deleteOriginalMessage(implicit async: AsyncMessageToken): F[Unit] = requests.runRequest(
    InteractionRequests.deleteOriginalInteractionResponse(async.applicationId, async.interactionToken)
  )

  def getPreviousMessage(messageId: MessageId)(implicit async: AsyncMessageToken): F[Message] = requests.runRequest(
    InteractionRequests.getFollowupMessage(async.applicationId, async.interactionToken, messageId)
  )

  def editPreviousMessage(
      messageId: MessageId,
      body: InteractionRequests.EditFollowupMessageBody,
      parts: Seq[EncodeBody.Multipart[_, Any]] = Nil
  )(implicit async: AsyncMessageToken): F[Message] = requests.runRequest(
    InteractionRequests.editFollowupMessage(async.applicationId, async.interactionToken, messageId, body, parts)
  )

  def deletePreviousMessage(messageId: MessageId)(implicit async: AsyncMessageToken): F[Unit] = requests.runRequest(
    InteractionRequests.deleteFollowupMessage(async.applicationId, async.interactionToken, messageId)
  )
}
