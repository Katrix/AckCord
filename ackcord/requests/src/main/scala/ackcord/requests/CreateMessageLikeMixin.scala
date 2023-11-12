package ackcord.requests

import ackcord.data.Message.MessageFlags
import ackcord.data.base.{DiscordObject, DiscordObjectCompanion}
import ackcord.data.{AllowedMentions, Component, OutgoingEmbed, UndefOr, UndefOrSome}

trait CreateMessageLikeMixin[Tpe <: DiscordObject] extends DiscordObjectCompanion[Tpe] {

  def of(
      content: Option[String] = None,
      embeds: Seq[OutgoingEmbed] = Nil,
      components: Seq[Component] = Nil,
      tts: Boolean = false,
      allowedMentions: Option[AllowedMentions] = None,
      flags: Option[MessageFlags] = None,
      attachments: Seq[ChannelRequests.MessageCreateEditAttachment] = Nil
  ): Tpe =
    ChannelRequests.CreateMessageBody
      .make20(
        content = UndefOr.fromOption(content),
        tts = UndefOr.someIfTrue(tts),
        embeds = UndefOrSome(embeds),
        allowedMentions = UndefOr.fromOption(allowedMentions),
        components = UndefOrSome(components),
        attachments = UndefOrSome(attachments),
        flags = UndefOr.fromOption(flags)
      )
      .retype(this)

  def ofContent(
      content: String,
      embeds: Seq[OutgoingEmbed] = Nil,
      components: Seq[Component] = Nil,
      tts: Boolean = false,
      allowedMentions: Option[AllowedMentions] = None,
      flags: Option[MessageFlags] = None,
      attachments: Seq[ChannelRequests.MessageCreateEditAttachment] = Nil
  ): Tpe = of(Some(content), embeds, components, tts, allowedMentions, flags, attachments)

  def ofEmbeds(
      embeds: Seq[OutgoingEmbed],
      content: Option[String] = None,
      components: Seq[Component] = Nil,
      tts: Boolean = false,
      allowedMentions: Option[AllowedMentions] = None,
      flags: Option[MessageFlags] = None,
      attachments: Seq[ChannelRequests.MessageCreateEditAttachment] = Nil
  ): Tpe = of(content, embeds, components, tts, allowedMentions, flags, attachments)

  def ofComponents(
      components: Seq[Component],
      content: Option[String] = None,
      embeds: Seq[OutgoingEmbed] = Nil,
      tts: Boolean = false,
      allowedMentions: Option[AllowedMentions] = None,
      flags: Option[MessageFlags] = None,
      attachments: Seq[ChannelRequests.MessageCreateEditAttachment] = Nil
  ): Tpe = of(content, embeds, components, tts, allowedMentions, flags, attachments)
}
