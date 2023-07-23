package ackcord.gateway.data

import ackcord.data.base.{DiscordStringEnum, DiscordStringEnumCompanion}

sealed case class GatewayDispatchType private (value: String) extends DiscordStringEnum
object GatewayDispatchType extends DiscordStringEnumCompanion[GatewayDispatchType] {

  val Ready: GatewayDispatchType   = GatewayDispatchType("READY")
  val Resumed: GatewayDispatchType = GatewayDispatchType("RESUMED")
  val ApplicationCommandPermissionUpdate: GatewayDispatchType = GatewayDispatchType(
    "APPLICATION_COMMAND_PERMISSIONS_UPDATE"
  )
  val AutoModerationRuleCreate: GatewayDispatchType      = GatewayDispatchType("AUTO_MODERATION_RULE_CREATE")
  val AutoModerationRuleUpdate: GatewayDispatchType      = GatewayDispatchType("AUTO_MODERATION_RULE_UPDATE")
  val AutoModerationRuleDelete: GatewayDispatchType      = GatewayDispatchType("AUTO_MODERATION_RULE_DELETE")
  val AutoModerationActionExecution: GatewayDispatchType = GatewayDispatchType("AUTO_MODERATION_ACTION_EXECUTION")
  val ChannelCreate: GatewayDispatchType                 = GatewayDispatchType("CHANNEL_CREATE")
  val ChannelUpdate: GatewayDispatchType                 = GatewayDispatchType("CHANNEL_UPDATE")
  val ChannelDelete: GatewayDispatchType                 = GatewayDispatchType("CHANNEL_DELETE")
  val ThreadCreate: GatewayDispatchType                  = GatewayDispatchType("THREAD_CREATE")
  val ThreadUpdate: GatewayDispatchType                  = GatewayDispatchType("THREAD_UPDATE")
  val ThreadDelete: GatewayDispatchType                  = GatewayDispatchType("THREAD_DELETE")
  val ThreadListSync: GatewayDispatchType                = GatewayDispatchType("THREAD_LIST_SYNC")
  val ThreadMemberUpdate: GatewayDispatchType            = GatewayDispatchType("THREAD_MEMBER_UPDATE")
  val ThreadMembersUpdate: GatewayDispatchType           = GatewayDispatchType("THREAD_MEMBERS_UPDATE")
  val ChannelPinsUpdate: GatewayDispatchType             = GatewayDispatchType("CHANNEL_PINS_UPDATE")
  val GuildCreate: GatewayDispatchType                   = GatewayDispatchType("GUILD_CREATE")
  val GuildUpdate: GatewayDispatchType                   = GatewayDispatchType("GUILD_UPDATE")
  val GuildDelete: GatewayDispatchType                   = GatewayDispatchType("GUILD_DELETE")
  val GuildAuditLogEntryCreate: GatewayDispatchType      = GatewayDispatchType("GUILD_AUDIT_LOG_ENTRY_CREATE")
  val GuildBanAdd: GatewayDispatchType                   = GatewayDispatchType("GUILD_BAN_ADD")
  val GuildBanRemove: GatewayDispatchType                = GatewayDispatchType("GUILD_BAN_REMOVE")
  val GuildEmojiUpdate: GatewayDispatchType              = GatewayDispatchType("GUILD_EMOJI_UPDATE")
  val GuildStickersUpdate: GatewayDispatchType           = GatewayDispatchType("GUILD_STICKERS_UPDATE")
  val GuildIntegrationsUpdate: GatewayDispatchType       = GatewayDispatchType("GUILD_INTEGRATIONS_UPDATE")
  val GuildMemberAdd: GatewayDispatchType                = GatewayDispatchType("GUILD_MEMBER_ADD")
  val GuildMemberRemove: GatewayDispatchType             = GatewayDispatchType("GUILD_MEMBER_REMOVE")
  val GuildMemberUpdate: GatewayDispatchType             = GatewayDispatchType("GUILD_MEMBER_UPDATE")
  val GuildMembersChunk: GatewayDispatchType             = GatewayDispatchType("GUILD_MEMBERS_CHUNK")
  val GuildRoleCreate: GatewayDispatchType               = GatewayDispatchType("GUILD_ROLE_CREATE")
  val GuildRoleUpdate: GatewayDispatchType               = GatewayDispatchType("GUILD_ROLE_UPDATE")
  val GuildRoleDelete: GatewayDispatchType               = GatewayDispatchType("GUILD_ROLE_DELETE")
  val GuildScheduledEventCreate: GatewayDispatchType     = GatewayDispatchType("GUILD_SCHEDULED_EVENT_CREATE")
  val GuildScheduledEventUpdate: GatewayDispatchType     = GatewayDispatchType("GUILD_SCHEDULED_EVENT_UPDATE")
  val GuildScheduledEventDelete: GatewayDispatchType     = GatewayDispatchType("GUILD_SCHEDULED_EVENT_DELETE")
  val GuildScheduledEventUserAdd: GatewayDispatchType    = GatewayDispatchType("GUILD_SCHEDULED_EVENT_USER_ADD")
  val GuildScheduledEventUserRemove: GatewayDispatchType = GatewayDispatchType("GUILD_SCHEDULED_EVENT_USER_REMOVE")
  val IntegrationCreate: GatewayDispatchType             = GatewayDispatchType("INTEGRATION_CREATE")
  val IntegrationUpdate: GatewayDispatchType             = GatewayDispatchType("INTEGRATION_UPDATE")
  val IntegrationDelete: GatewayDispatchType             = GatewayDispatchType("INTEGRATION_DELETE")
  val InviteCreate: GatewayDispatchType                  = GatewayDispatchType("INVITE_CREATE")
  val InviteDelete: GatewayDispatchType                  = GatewayDispatchType("INVITE_DELETE")
  val MessageCreate: GatewayDispatchType                 = GatewayDispatchType("MESSAGE_CREATE")
  val MessageUpdate: GatewayDispatchType                 = GatewayDispatchType("MESSAGE_UPDATE")
  val MessageDelete: GatewayDispatchType                 = GatewayDispatchType("MESSAGE_DELETE")
  val MessageDeleteBulk: GatewayDispatchType             = GatewayDispatchType("MESSAGE_DELETE_BULK")
  val MessageReactionAdd: GatewayDispatchType            = GatewayDispatchType("MESSAGE_REACTION_ADD")
  val MessageReactionRemove: GatewayDispatchType         = GatewayDispatchType("MESSAGE_REACTION_REMOVE")
  val MessageReactionRemoveAll: GatewayDispatchType      = GatewayDispatchType("MESSAGE_REACTION_REMOVE_ALL")
  val MessageReactionRemoveEmoji: GatewayDispatchType    = GatewayDispatchType("MESSAGE_REACTION_REMOVE_EMOJI")
  val PresenceUpdate: GatewayDispatchType                = GatewayDispatchType("PRESENCE_UPDATE")
  val TypingStart: GatewayDispatchType                   = GatewayDispatchType("TYPING_START")
  val UserUpdate: GatewayDispatchType                    = GatewayDispatchType("USER_UPDATE")
  val VoiceStateUpdate: GatewayDispatchType              = GatewayDispatchType("VOICE_STATE_UPDATE")
  val VoiceServerUpdate: GatewayDispatchType             = GatewayDispatchType("VOICE_SERVER_UPDATE")
  val WebhookUpdate: GatewayDispatchType                 = GatewayDispatchType("WEBHOOK_UPDATE")
  val InteractionCreate: GatewayDispatchType             = GatewayDispatchType("INTERACTION_CREATE")
  val StageInstanceCreate: GatewayDispatchType           = GatewayDispatchType("STAGE_INSTANCE_CREATE")
  val StageInstanceUpdate: GatewayDispatchType           = GatewayDispatchType("STAGE_INSTANCE_UPDATE")
  val StageInstanceDelete: GatewayDispatchType           = GatewayDispatchType("STAGE_INSTANCE_DELETE")

  def unknown(value: String): GatewayDispatchType = new GatewayDispatchType(value)

  def values: Seq[GatewayDispatchType] = Seq(
    Ready,
    Resumed,
    ApplicationCommandPermissionUpdate,
    AutoModerationRuleCreate,
    AutoModerationRuleUpdate,
    AutoModerationRuleDelete,
    AutoModerationActionExecution,
    ChannelCreate,
    ChannelUpdate,
    ChannelDelete,
    ThreadCreate,
    ThreadUpdate,
    ThreadDelete,
    ThreadListSync,
    ThreadMemberUpdate,
    ThreadMembersUpdate,
    ChannelPinsUpdate,
    GuildCreate,
    GuildUpdate,
    GuildDelete,
    GuildAuditLogEntryCreate,
    GuildBanAdd,
    GuildBanRemove,
    GuildEmojiUpdate,
    GuildStickersUpdate,
    GuildIntegrationsUpdate,
    GuildMemberAdd,
    GuildMemberRemove,
    GuildMemberUpdate,
    GuildMembersChunk,
    GuildRoleCreate,
    GuildRoleUpdate,
    GuildRoleDelete,
    GuildScheduledEventCreate,
    GuildScheduledEventUpdate,
    GuildScheduledEventDelete,
    GuildScheduledEventUserAdd,
    GuildScheduledEventUserRemove,
    IntegrationCreate,
    IntegrationUpdate,
    IntegrationDelete,
    InviteCreate,
    InviteDelete,
    MessageCreate,
    MessageUpdate,
    MessageDelete,
    MessageDeleteBulk,
    MessageReactionAdd,
    MessageReactionRemove,
    MessageReactionRemoveAll,
    MessageReactionRemoveEmoji,
    PresenceUpdate,
    TypingStart,
    UserUpdate,
    VoiceStateUpdate,
    VoiceServerUpdate,
    WebhookUpdate,
    InteractionCreate,
    StageInstanceCreate,
    StageInstanceUpdate,
    StageInstanceDelete
  )

}
