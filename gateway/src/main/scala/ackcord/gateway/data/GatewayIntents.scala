package ackcord.gateway.data

import ackcord.data.base.{DiscordIntEnum, DiscordIntEnumCompanion}

sealed case class GatewayIntents private(value: Int) extends DiscordIntEnum
object GatewayIntents extends DiscordIntEnumCompanion[GatewayIntents] with GatewayIntentsMixin {

  val Guilds: GatewayIntents = GatewayIntents(1 << 0)
  val GuildMembers: GatewayIntents = GatewayIntents(1 << 1)
  val GuildModeration: GatewayIntents = GatewayIntents(1 << 2)
  val GuildEmojisAndStickers: GatewayIntents = GatewayIntents(1 << 3)
  val GuildIntegrations: GatewayIntents = GatewayIntents(1 << 4)
  val GuildWebhooks: GatewayIntents = GatewayIntents(1 << 5)
  val GuildInvites: GatewayIntents = GatewayIntents(1 << 6)
  val GuildVoiceStates: GatewayIntents = GatewayIntents(1 << 7)
  val GuildPresence: GatewayIntents = GatewayIntents(1 << 8)
  val GuildMessages: GatewayIntents = GatewayIntents(1 << 9)
  val GuildMessageReactions: GatewayIntents = GatewayIntents(1 << 10)
  val GuildMessageTyping: GatewayIntents = GatewayIntents(1 << 11)
  val DirectMessages: GatewayIntents = GatewayIntents(1 << 12)
  val DirectMessageReactions: GatewayIntents = GatewayIntents(1 << 13)
  val DirectMessageTyping: GatewayIntents = GatewayIntents(1 << 14)
  val MessageContent: GatewayIntents = GatewayIntents(1 << 15)
  val GuildScheduledEvents: GatewayIntents = GatewayIntents(1 << 16)
  val AutoModerationConfiguration: GatewayIntents = GatewayIntents(1 << 20)
  val AutoModerationExecution: GatewayIntents = GatewayIntents(1 << 21)
  
  def unknown(value: Int): GatewayIntents = new GatewayIntents(value)
  
  def values: Seq[GatewayIntents] = Seq(Guilds, GuildMembers, GuildModeration, GuildEmojisAndStickers, GuildIntegrations, GuildWebhooks, GuildInvites, GuildVoiceStates, GuildPresence, GuildMessages, GuildMessageReactions, GuildMessageTyping, DirectMessages, DirectMessageReactions, DirectMessageTyping, MessageContent, GuildScheduledEvents, AutoModerationConfiguration, AutoModerationExecution)

  
}