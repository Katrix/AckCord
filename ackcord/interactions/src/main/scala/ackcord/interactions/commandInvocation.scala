package ackcord.interactions

import ackcord.gateway.Context

trait InteractionInvocation[A] {
  def interaction: Interaction
  def interactionData: Interaction.InteractionData
  def args: A
  def context: Context
}

case class CommandInvocation[A](
    interaction: Interaction,
    interactionData: Interaction.ApplicationCommandData,
    args: A,
    context: Context
) extends InteractionInvocation[A]

case class ComponentInvocation[A](
    interaction: Interaction,
    interactionData: Interaction.MessageComponentData,
    args: A,
    context: Context
) extends InteractionInvocation[A]
