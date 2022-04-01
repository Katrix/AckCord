package ackcord.interactions.components

import ackcord.CacheSnapshot
import ackcord.data.{ApplicationComponentInteractionData, ComponentType, Message, RawInteraction}
import ackcord.interactions._
import ackcord.requests.Requests

/**
  * Base class for all menu handlers. For more comfort, use [[AutoMenuHandler]]
  * to automatically register the handler when you create an instance of it it.
  * @param interactionTransformer
  *   A transformer to do base processing of the interaction before handling it.
  */
abstract class MenuHandler[InteractionTpe <: MenuInteraction](
    requests: Requests,
    interactionTransformer: InteractionTransformer[MenuInteraction, InteractionTpe] =
      InteractionTransformer.identity[MenuInteraction]
) extends ComponentHandler[MenuInteraction, InteractionTpe](
      requests,
      interactionTransformer,
      ComponentType.StringSelect
    ) {

  override protected def makeBaseInteraction(
      invocationInfo: InteractionInvocationInfo,
      message: Message,
      interaction: RawInteraction,
      customId: String,
      cacheSnapshot: Option[CacheSnapshot]
  ): MenuInteraction = cacheSnapshot match {
    case Some(c) =>
      BaseCacheMenuInteraction(
        invocationInfo,
        message,
        customId,
        interaction.data
          .collect { case ApplicationComponentInteractionData(_, _, values) => values }
          .flatten
          .getOrElse(Nil),
        c
      )
    case None =>
      StatelessMenuInteraction(
        invocationInfo,
        message,
        customId,
        interaction.data
          .collect { case ApplicationComponentInteractionData(_, _, values) => values }
          .flatten
          .getOrElse(Nil)
      )
  }
}
object MenuHandler {

  /**
    * An [[InteractionTransformer]] adding cache information to the interaction.
    */
  val cacheTransformer: InteractionTransformer[MenuInteraction, CacheMenuInteraction] =
    InteractionTransformer
      .identity[MenuInteraction]
      .andThen(
        InteractionTransformer.cache(c =>
          i => BaseCacheMenuInteraction(i.interactionInvocationInfo, i.message, i.customId, i.values, c)
        )
      )

  /**
    * An [[InteractionTransformer]] adding resolved data structures to the
    * interaction.
    */
  val resolvedTransformer: InteractionTransformer[MenuInteraction, ResolvedMenuInteraction] =
    cacheTransformer.andThen(
      InteractionTransformer.resolved((t, g) =>
        i => BaseResolvedMenuInteraction(i.interactionInvocationInfo, i.message, i.customId, i.values, t, g, i.cache)
      )
    )

  /**
    * An [[InteractionTransformer]] adding the guild of the component to the
    * interaction.
    */
  val guildTransformer: InteractionTransformer[MenuInteraction, GuildMenuInteraction] =
    resolvedTransformer.andThen(
      InteractionTransformer.onlyInGuild((g, m, p, t) =>
        i => BaseGuildMenuInteraction(i.interactionInvocationInfo, i.message, i.customId, i.values, t, g, m, p, i.cache)
      )
    )

  /**
    * An [[InteractionTransformer]] adding the voice channel of the user using
    * the component to the interaction.
    */
  val voiceChannelTransformer: InteractionTransformer[MenuInteraction, VoiceChannelMenuInteraction] =
    guildTransformer.andThen(
      InteractionTransformer.inVoiceChannel(v =>
        i =>
          BaseVoiceChannelMenuInteraction(
            i.interactionInvocationInfo,
            i.message,
            i.customId,
            i.values,
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
