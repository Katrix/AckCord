package ackcord.interactions

import ackcord.data.ApplicationId

/**
 * A type allowing providing evidence that an interaction response is being
 * done async.
 */
sealed trait AsyncToken {
  def applicationId: ApplicationId
  def interactionToken: String
}
sealed trait AsyncMessageToken extends AsyncToken

object AsyncToken {
  private[interactions] case class Impl(applicationId: ApplicationId, interactionToken: String)
    extends AsyncMessageToken

  private[interactions] def fromInteraction(interaction: Interaction): AsyncToken =
    Impl(interaction.applicationId, interaction.token)

  private[interactions] def fromInteractionWithMessage(interaction: Interaction): AsyncMessageToken =
    Impl(interaction.applicationId, interaction.token)
}
