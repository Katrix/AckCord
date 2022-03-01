package ackcord.interactions.components

import ackcord.CacheSnapshot
import ackcord.data.{ComponentType, Message, RawInteraction}
import ackcord.interactions._
import ackcord.requests.Requests

/**
  * Base class for all button handlers. For more comfort, use
  * [[AutoButtonHandler]] to automatically register the handler when you create
  * an instance of it it.
  * @param interactionTransformer
  *   A transformer to do base processing of the interaction before handling it.
  */
abstract class ButtonHandler[InteractionTpe <: ComponentInteraction](
    requests: Requests,
    interactionTransformer: InteractionTransformer[ComponentInteraction, InteractionTpe] =
      InteractionTransformer.identity[ComponentInteraction]
) extends ComponentHandler[ComponentInteraction, InteractionTpe](
      requests,
      interactionTransformer,
      ComponentType.Button
    ) {

  override protected def makeBaseInteraction(
      invocationInfo: InteractionInvocationInfo,
      message: Message,
      interaction: RawInteraction,
      customId: String,
      cacheSnapshot: Option[CacheSnapshot]
  ): ComponentInteraction = cacheSnapshot match {
    case Some(value) => BaseCacheComponentInteraction(invocationInfo, message, customId, value)
    case None        => StatelessComponentInteraction(invocationInfo, message, customId)
  }
}
object ButtonHandler {

  /**
    * An [[InteractionTransformer]] adding cache information to the interaction.
    */
  val cacheTransformer: InteractionTransformer[ComponentInteraction, CacheComponentInteraction] =
    InteractionTransformer
      .identity[ComponentInteraction]
      .andThen(
        InteractionTransformer.cache(c =>
          i => BaseCacheComponentInteraction(i.interactionInvocationInfo, i.message, i.customId, c)
        )
      )

  /**
    * An [[InteractionTransformer]] adding resolved data structures to the
    * interaction.
    */
  val resolvedTransformer: InteractionTransformer[ComponentInteraction, ResolvedComponentInteraction] =
    cacheTransformer.andThen(
      InteractionTransformer.resolved((t, g) =>
        i => BaseResolvedComponentInteraction(i.interactionInvocationInfo, i.message, i.customId, t, g, i.cache)
      )
    )

  /**
    * An [[InteractionTransformer]] adding the guild of the component to the
    * interaction.
    */
  val guildTransformer: InteractionTransformer[ComponentInteraction, GuildComponentInteraction] =
    resolvedTransformer.andThen(
      InteractionTransformer.onlyInGuild((g, m, p, t) =>
        i => BaseGuildComponentInteraction(i.interactionInvocationInfo, i.message, i.customId, t, g, m, p, i.cache)
      )
    )

  /**
    * An [[InteractionTransformer]] adding the voice channel of the user using
    * the component to the interaction.
    */
  val voiceChannelTransformer: InteractionTransformer[ComponentInteraction, VoiceChannelComponentInteraction] =
    guildTransformer.andThen(
      InteractionTransformer.inVoiceChannel(v =>
        i =>
          BaseVoiceChannelComponentInteraction(
            i.interactionInvocationInfo,
            i.message,
            i.customId,
            i.textChannel,
            i.guild,
            i.member,
            i.memberPermissions,
            v,
            i.cache
          )
      )
    )
}
