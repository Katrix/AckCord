//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/AuditLog.yaml

import ackcord.data.base._
import io.circe.Json

class AuditLog(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

  /** List of application commands referenced in the audit log */
  @inline def applicationCommands: Seq[AuditLog.AuditLogApplicationCommandStub] =
    selectDynamic[Seq[AuditLog.AuditLogApplicationCommandStub]]("application_commands")

  /** List of audit log entries, sorted from most to least recent */
  @inline def auditLogEntries: Seq[AuditLog.AuditLogEntry] =
    selectDynamic[Seq[AuditLog.AuditLogEntry]]("audit_log_entries")

  /** List of auto moderation rules referenced in the audit log */
  @inline def autoModerationRules: Seq[AutoModerationRule] =
    selectDynamic[Seq[AutoModerationRule]]("auto_moderation_rules")

  /** List of guild scheduled events referenced in the audit log */
  @inline def guildScheduledEvents: Seq[GuildScheduledEvent] =
    selectDynamic[Seq[GuildScheduledEvent]]("guild_scheduled_events")

  /** List of partial integration objects */
  @inline def integrations: Seq[AuditLog.AuditLogIntegration] =
    selectDynamic[Seq[AuditLog.AuditLogIntegration]]("integrations")

  /** List of threads referenced in the audit log */
  @inline def threads: Seq[ThreadChannel] = selectDynamic[Seq[ThreadChannel]]("threads")

  /** List of users referenced in the audit log */
  @inline def users: Seq[User] = selectDynamic[Seq[User]]("users")

  /** List of webhooks referenced in the audit log */
  @inline def webhooks: Seq[Webhook] = selectDynamic[Seq[Webhook]]("webhooks")

  override def values: Seq[() => Any] = Seq(
    () => applicationCommands,
    () => auditLogEntries,
    () => autoModerationRules,
    () => guildScheduledEvents,
    () => integrations,
    () => threads,
    () => users,
    () => webhooks
  )
}
object AuditLog extends DiscordObjectCompanion[AuditLog] {
  def makeRaw(json: Json, cache: Map[String, Any]): AuditLog = new AuditLog(json, cache)

  /**
    * @param applicationCommands
    *   List of application commands referenced in the audit log
    * @param auditLogEntries
    *   List of audit log entries, sorted from most to least recent
    * @param autoModerationRules
    *   List of auto moderation rules referenced in the audit log
    * @param guildScheduledEvents
    *   List of guild scheduled events referenced in the audit log
    * @param integrations
    *   List of partial integration objects
    * @param threads
    *   List of threads referenced in the audit log
    * @param users
    *   List of users referenced in the audit log
    * @param webhooks
    *   List of webhooks referenced in the audit log
    */
  def make20(
      applicationCommands: Seq[AuditLog.AuditLogApplicationCommandStub],
      auditLogEntries: Seq[AuditLog.AuditLogEntry],
      autoModerationRules: Seq[AutoModerationRule],
      guildScheduledEvents: Seq[GuildScheduledEvent],
      integrations: Seq[AuditLog.AuditLogIntegration],
      threads: Seq[ThreadChannel],
      users: Seq[User],
      webhooks: Seq[Webhook]
  ): AuditLog = makeRawFromFields(
    "application_commands"   := applicationCommands,
    "audit_log_entries"      := auditLogEntries,
    "auto_moderation_rules"  := autoModerationRules,
    "guild_scheduled_events" := guildScheduledEvents,
    "integrations"           := integrations,
    "threads"                := threads,
    "users"                  := users,
    "webhooks"               := webhooks
  )

  class AuditLogApplicationCommandStub(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache) {

    override def values: Seq[() => Any] = Seq()
  }
  object AuditLogApplicationCommandStub extends DiscordObjectCompanion[AuditLogApplicationCommandStub] {
    def makeRaw(json: Json, cache: Map[String, Any]): AuditLogApplicationCommandStub =
      new AuditLogApplicationCommandStub(json, cache)

    def make20(): AuditLogApplicationCommandStub = makeRawFromFields()

  }

  class AuditLogIntegration(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {
    @inline def id: Snowflake[Integration] = selectDynamic[Snowflake[Integration]]("id")

    @inline def name: String = selectDynamic[String]("name")

    @inline def tpe: String = selectDynamic[String]("type")

    @inline def account: AuditLogIntegration.AuditLogIntegrationAccount =
      selectDynamic[AuditLogIntegration.AuditLogIntegrationAccount]("account")

    @inline def applicationId: ApplicationId = selectDynamic[ApplicationId]("application_id")

    override def values: Seq[() => Any] = Seq(() => id, () => name, () => tpe, () => account, () => applicationId)
  }
  object AuditLogIntegration extends DiscordObjectCompanion[AuditLogIntegration] {
    def makeRaw(json: Json, cache: Map[String, Any]): AuditLogIntegration = new AuditLogIntegration(json, cache)

    def make20(
        id: Snowflake[Integration],
        name: String,
        tpe: String,
        account: AuditLogIntegration.AuditLogIntegrationAccount,
        applicationId: ApplicationId
    ): AuditLogIntegration = makeRawFromFields(
      "id"             := id,
      "name"           := name,
      "type"           := tpe,
      "account"        := account,
      "application_id" := applicationId
    )

    class AuditLogIntegrationAccount(json: Json, cache: Map[String, Any] = Map.empty)
        extends DiscordObject(json, cache) {

      override def values: Seq[() => Any] = Seq()
    }
    object AuditLogIntegrationAccount extends DiscordObjectCompanion[AuditLogIntegrationAccount] {
      def makeRaw(json: Json, cache: Map[String, Any]): AuditLogIntegrationAccount =
        new AuditLogIntegrationAccount(json, cache)

      def make20(): AuditLogIntegrationAccount = makeRawFromFields()

    }
  }

  class AuditLogEntry(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** ID of the affected entity (webhook, user, role, etc.) */
    @inline def targetId: Option[String] = selectDynamic[Option[String]]("target_id")

    /** Changes made to the target_id */
    @inline def changes: UndefOr[Seq[AuditLog.AuditLogChange]] =
      selectDynamic[UndefOr[Seq[AuditLog.AuditLogChange]]]("changes")

    /** User or app that made the changes */
    @inline def userId: Option[UserId] = selectDynamic[Option[UserId]]("user_id")

    /** ID of the entry */
    @inline def id: Snowflake[AuditLogEntry] = selectDynamic[Snowflake[AuditLogEntry]]("id")

    /** Type of action that occurred */
    @inline def actionType: AuditLog.AuditLogEvent = selectDynamic[AuditLog.AuditLogEvent]("action_type")

    @inline def options: UndefOr[AuditLog.AuditEntryInfo] = selectDynamic[UndefOr[AuditLog.AuditEntryInfo]]("options")

    @inline def reason: UndefOr[String] = selectDynamic[UndefOr[String]]("reason")

    override def values: Seq[() => Any] =
      Seq(() => targetId, () => changes, () => userId, () => id, () => actionType, () => options, () => reason)
  }
  object AuditLogEntry extends DiscordObjectCompanion[AuditLogEntry] {
    def makeRaw(json: Json, cache: Map[String, Any]): AuditLogEntry = new AuditLogEntry(json, cache)

    /**
      * @param targetId
      *   ID of the affected entity (webhook, user, role, etc.)
      * @param changes
      *   Changes made to the target_id
      * @param userId
      *   User or app that made the changes
      * @param id
      *   ID of the entry
      * @param actionType
      *   Type of action that occurred
      */
    def make20(
        targetId: Option[String],
        changes: UndefOr[Seq[AuditLog.AuditLogChange]] = UndefOrUndefined,
        userId: Option[UserId],
        id: Snowflake[AuditLogEntry],
        actionType: AuditLog.AuditLogEvent,
        options: UndefOr[AuditLog.AuditEntryInfo] = UndefOrUndefined,
        reason: UndefOr[String] = UndefOrUndefined
    ): AuditLogEntry = makeRawFromFields(
      "target_id"   := targetId,
      "changes"    :=? changes,
      "user_id"     := userId,
      "id"          := id,
      "action_type" := actionType,
      "options"    :=? options,
      "reason"     :=? reason
    )

  }

  class AuditEntryInfo(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** ID of the app whose permissions were targeted */
    @inline def applicationId: UndefOr[ApplicationId] = selectDynamic[UndefOr[ApplicationId]]("application_id")

    /** Name of the Auto Moderation rule that was triggered */
    @inline def autoModerationRuleName: UndefOr[String] = selectDynamic[UndefOr[String]]("auto_moderation_rule_name")

    /** Trigger type of the Auto Moderation rule that was triggered */
    @inline def autoModerationRuleTriggerType: UndefOr[String] =
      selectDynamic[UndefOr[String]]("auto_moderation_rule_trigger_type")

    /** Channel in which the entities were targeted */
    @inline def channelId: UndefOr[ChannelId] = selectDynamic[UndefOr[ChannelId]]("channel_id")

    /** Number of entities that were targeted */
    @inline def count: UndefOr[String] = selectDynamic[UndefOr[String]]("count")

    /** Number of days after which inactive members were kicked */
    @inline def deleteMemberDays: UndefOr[String] = selectDynamic[UndefOr[String]]("delete_member_days")

    /** ID of the overwritten entity */
    @inline def id: UndefOr[RawSnowflake] = selectDynamic[UndefOr[RawSnowflake]]("id")

    /** Number of members removed by the prune */
    @inline def membersRemoved: UndefOr[String] = selectDynamic[UndefOr[String]]("members_removed")

    /** ID of the message that was targeted */
    @inline def messageId: UndefOr[MessageId] = selectDynamic[UndefOr[MessageId]]("message_id")

    /** Name of the role if type is "0" (not present if type is "1") */
    @inline def roleName: UndefOr[String] = selectDynamic[UndefOr[String]]("role_name")

    /** Type of overwritten entity - role ("0") or member ("1") */
    @inline def tpe: UndefOr[String] = selectDynamic[UndefOr[String]]("type")

    override def values: Seq[() => Any] = Seq(
      () => applicationId,
      () => autoModerationRuleName,
      () => autoModerationRuleTriggerType,
      () => channelId,
      () => count,
      () => deleteMemberDays,
      () => id,
      () => membersRemoved,
      () => messageId,
      () => roleName,
      () => tpe
    )
  }
  object AuditEntryInfo extends DiscordObjectCompanion[AuditEntryInfo] {
    def makeRaw(json: Json, cache: Map[String, Any]): AuditEntryInfo = new AuditEntryInfo(json, cache)

    /**
      * @param applicationId
      *   ID of the app whose permissions were targeted
      * @param autoModerationRuleName
      *   Name of the Auto Moderation rule that was triggered
      * @param autoModerationRuleTriggerType
      *   Trigger type of the Auto Moderation rule that was triggered
      * @param channelId
      *   Channel in which the entities were targeted
      * @param count
      *   Number of entities that were targeted
      * @param deleteMemberDays
      *   Number of days after which inactive members were kicked
      * @param id
      *   ID of the overwritten entity
      * @param membersRemoved
      *   Number of members removed by the prune
      * @param messageId
      *   ID of the message that was targeted
      * @param roleName
      *   Name of the role if type is "0" (not present if type is "1")
      * @param tpe
      *   Type of overwritten entity - role ("0") or member ("1")
      */
    def make20(
        applicationId: UndefOr[ApplicationId] = UndefOrUndefined,
        autoModerationRuleName: UndefOr[String] = UndefOrUndefined,
        autoModerationRuleTriggerType: UndefOr[String] = UndefOrUndefined,
        channelId: UndefOr[ChannelId] = UndefOrUndefined,
        count: UndefOr[String] = UndefOrUndefined,
        deleteMemberDays: UndefOr[String] = UndefOrUndefined,
        id: UndefOr[RawSnowflake] = UndefOrUndefined,
        membersRemoved: UndefOr[String] = UndefOrUndefined,
        messageId: UndefOr[MessageId] = UndefOrUndefined,
        roleName: UndefOr[String] = UndefOrUndefined,
        tpe: UndefOr[String] = UndefOrUndefined
    ): AuditEntryInfo = makeRawFromFields(
      "application_id"                    :=? applicationId,
      "auto_moderation_rule_name"         :=? autoModerationRuleName,
      "auto_moderation_rule_trigger_type" :=? autoModerationRuleTriggerType,
      "channel_id"                        :=? channelId,
      "count"                             :=? count,
      "delete_member_days"                :=? deleteMemberDays,
      "id"                                :=? id,
      "members_removed"                   :=? membersRemoved,
      "message_id"                        :=? messageId,
      "role_name"                         :=? roleName,
      "type"                              :=? tpe
    )

  }

  class AuditLogChange(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** New value of the key */
    @inline def newValue: UndefOr[Json] = selectDynamic[UndefOr[Json]]("new_value")

    /** Old value of the key */
    @inline def oldValue: UndefOr[Json] = selectDynamic[UndefOr[Json]]("old_value")

    /** Name of the changed entity, with a few exceptions */
    @inline def key: String = selectDynamic[String]("key")

    override def values: Seq[() => Any] = Seq(() => newValue, () => oldValue, () => key)
  }
  object AuditLogChange extends DiscordObjectCompanion[AuditLogChange] {
    def makeRaw(json: Json, cache: Map[String, Any]): AuditLogChange = new AuditLogChange(json, cache)

    /**
      * @param newValue
      *   New value of the key
      * @param oldValue
      *   Old value of the key
      * @param key
      *   Name of the changed entity, with a few exceptions
      */
    def make20(
        newValue: UndefOr[Json] = UndefOrUndefined,
        oldValue: UndefOr[Json] = UndefOrUndefined,
        key: String
    ): AuditLogChange = makeRawFromFields("new_value" :=? newValue, "old_value" :=? oldValue, "key" := key)

  }

  sealed case class AuditLogEvent private (value: Int) extends DiscordEnum[Int]
  object AuditLogEvent                                 extends DiscordEnumCompanion[Int, AuditLogEvent] {

    /** Server settings were updated */
    val GUILD_UPDATE: AuditLogEvent = AuditLogEvent(1)

    /** Channel was created */
    val CHANNEL_CREATE: AuditLogEvent = AuditLogEvent(10)

    /** Channel settings were updated */
    val CHANNEL_UPDATE: AuditLogEvent = AuditLogEvent(11)

    /** Channel was deleted */
    val CHANNEL_DELETE: AuditLogEvent = AuditLogEvent(12)

    /** Permission overwrite was added to a channel */
    val CHANNEL_OVERWRITE_CREATE: AuditLogEvent = AuditLogEvent(13)

    /** Permission overwrite was updated for a channel */
    val CHANNEL_OVERWRITE_UPDATE: AuditLogEvent = AuditLogEvent(14)

    /** Permission overwrite was deleted from a channel */
    val CHANNEL_OVERWRITE_DELETE: AuditLogEvent = AuditLogEvent(15)

    /** Member was removed from server */
    val MEMBER_KICK: AuditLogEvent = AuditLogEvent(20)

    /** Members were pruned from server */
    val MEMBER_PRUNE: AuditLogEvent = AuditLogEvent(21)

    /** Member was banned from server */
    val MEMBER_BAN_ADD: AuditLogEvent = AuditLogEvent(22)

    /** Server ban was lifted for a member */
    val MEMBER_BAN_REMOVE: AuditLogEvent = AuditLogEvent(23)

    /** Member was updated in server */
    val MEMBER_UPDATE: AuditLogEvent = AuditLogEvent(24)

    /** Member was added or removed from a role */
    val MEMBER_ROLE_UPDATE: AuditLogEvent = AuditLogEvent(25)

    /** Member was moved to a different voice channel */
    val MEMBER_MOVE: AuditLogEvent = AuditLogEvent(26)

    /** Member was disconnected from a voice channel */
    val MEMBER_DISCONNECT: AuditLogEvent = AuditLogEvent(27)

    /** Bot user was added to server */
    val BOT_ADD: AuditLogEvent = AuditLogEvent(28)

    /** Role was created */
    val ROLE_CREATE: AuditLogEvent = AuditLogEvent(30)

    /** Role was edited */
    val ROLE_UPDATE: AuditLogEvent = AuditLogEvent(31)

    /** Role was deleted */
    val ROLE_DELETE: AuditLogEvent = AuditLogEvent(32)

    /** Server invite was created */
    val INVITE_CREATE: AuditLogEvent = AuditLogEvent(40)

    /** Server invite was updated */
    val INVITE_UPDATE: AuditLogEvent = AuditLogEvent(41)

    /** Server invite was deleted */
    val INVITE_DELETE: AuditLogEvent = AuditLogEvent(42)

    /** Webhook was created */
    val WEBHOOK_CREATE: AuditLogEvent = AuditLogEvent(50)

    /** Webhook properties or channel were updated */
    val WEBHOOK_UPDATE: AuditLogEvent = AuditLogEvent(51)

    /** Webhook was deleted */
    val WEBHOOK_DELETE: AuditLogEvent = AuditLogEvent(52)

    /** Emoji was created */
    val EMOJI_CREATE: AuditLogEvent = AuditLogEvent(60)

    /** Emoji name was updated */
    val EMOJI_UPDATE: AuditLogEvent = AuditLogEvent(61)

    /** Emoji was deleted */
    val EMOJI_DELETE: AuditLogEvent = AuditLogEvent(62)

    /** Single message was deleted */
    val MESSAGE_DELETE: AuditLogEvent = AuditLogEvent(72)

    /** Multiple messages were deleted */
    val MESSAGE_BULK_DELETE: AuditLogEvent = AuditLogEvent(73)

    /** Message was pinned to a channel */
    val MESSAGE_PIN: AuditLogEvent = AuditLogEvent(74)

    /** Message was unpinned from a channel */
    val MESSAGE_UNPIN: AuditLogEvent = AuditLogEvent(75)

    /** App was added to server */
    val INTEGRATION_CREATE: AuditLogEvent = AuditLogEvent(80)

    /** App was updated (as an example, its scopes were updated) */
    val INTEGRATION_UPDATE: AuditLogEvent = AuditLogEvent(81)

    /** App was removed from server */
    val INTEGRATION_DELETE: AuditLogEvent = AuditLogEvent(82)

    /** Stage instance was created (stage channel becomes live) */
    val STAGE_INSTANCE_CREATE: AuditLogEvent = AuditLogEvent(83)

    /** Stage instance details were updated */
    val STAGE_INSTANCE_UPDATE: AuditLogEvent = AuditLogEvent(84)

    /** Stage instance was deleted (stage channel no longer live) */
    val STAGE_INSTANCE_DELETE: AuditLogEvent = AuditLogEvent(85)

    /** Sticker was created */
    val STICKER_CREATE: AuditLogEvent = AuditLogEvent(90)

    /** Sticker details were updated */
    val STICKER_UPDATE: AuditLogEvent = AuditLogEvent(91)

    /** Sticker was deleted */
    val STICKER_DELETE: AuditLogEvent = AuditLogEvent(92)

    /** Event was created */
    val GUILD_SCHEDULED_EVENT_CREATE: AuditLogEvent = AuditLogEvent(100)

    /** Event was updated */
    val GUILD_SCHEDULED_EVENT_UPDATE: AuditLogEvent = AuditLogEvent(101)

    /** Event was cancelled */
    val GUILD_SCHEDULED_EVENT_DELETE: AuditLogEvent = AuditLogEvent(102)

    /** Thread was created in a channel */
    val THREAD_CREATE: AuditLogEvent = AuditLogEvent(110)

    /** Thread was updated */
    val THREAD_UPDATE: AuditLogEvent = AuditLogEvent(111)

    /** Thread was deleted */
    val THREAD_DELETE: AuditLogEvent = AuditLogEvent(112)

    /** Permissions were updated for a command */
    val APPLICATION_COMMAND_PERMISSION_UPDATE: AuditLogEvent = AuditLogEvent(121)

    /** Auto Moderation rule was created */
    val AUTO_MODERATION_RULE_CREATE: AuditLogEvent = AuditLogEvent(140)

    /** Auto Moderation rule was updated */
    val AUTO_MODERATION_RULE_UPDATE: AuditLogEvent = AuditLogEvent(141)

    /** Auto Moderation rule was deleted */
    val AUTO_MODERATION_RULE_DELETE: AuditLogEvent = AuditLogEvent(142)

    /** Message was blocked by Auto Moderation */
    val AUTO_MODERATION_BLOCK_MESSAGE: AuditLogEvent = AuditLogEvent(143)

    /** Message was flagged by Auto Moderation */
    val AUTO_MODERATION_FLAG_TO_CHANNEL: AuditLogEvent = AuditLogEvent(144)

    /** Member was timed out by Auto Moderation */
    val AUTO_MODERATION_USER_COMMUNICATION_DISABLED: AuditLogEvent = AuditLogEvent(145)

    /** Creator monetization request was created */
    val CREATOR_MONETIZATION_REQUEST_CREATED: AuditLogEvent = AuditLogEvent(150)

    /** Creator monetization terms were accepted */
    val CREATOR_MONETIZATION_TERMS_ACCEPTED: AuditLogEvent = AuditLogEvent(151)

    def unknown(value: Int): AuditLogEvent = new AuditLogEvent(value)

    def values: Seq[AuditLogEvent] = Seq(
      GUILD_UPDATE,
      CHANNEL_CREATE,
      CHANNEL_UPDATE,
      CHANNEL_DELETE,
      CHANNEL_OVERWRITE_CREATE,
      CHANNEL_OVERWRITE_UPDATE,
      CHANNEL_OVERWRITE_DELETE,
      MEMBER_KICK,
      MEMBER_PRUNE,
      MEMBER_BAN_ADD,
      MEMBER_BAN_REMOVE,
      MEMBER_UPDATE,
      MEMBER_ROLE_UPDATE,
      MEMBER_MOVE,
      MEMBER_DISCONNECT,
      BOT_ADD,
      ROLE_CREATE,
      ROLE_UPDATE,
      ROLE_DELETE,
      INVITE_CREATE,
      INVITE_UPDATE,
      INVITE_DELETE,
      WEBHOOK_CREATE,
      WEBHOOK_UPDATE,
      WEBHOOK_DELETE,
      EMOJI_CREATE,
      EMOJI_UPDATE,
      EMOJI_DELETE,
      MESSAGE_DELETE,
      MESSAGE_BULK_DELETE,
      MESSAGE_PIN,
      MESSAGE_UNPIN,
      INTEGRATION_CREATE,
      INTEGRATION_UPDATE,
      INTEGRATION_DELETE,
      STAGE_INSTANCE_CREATE,
      STAGE_INSTANCE_UPDATE,
      STAGE_INSTANCE_DELETE,
      STICKER_CREATE,
      STICKER_UPDATE,
      STICKER_DELETE,
      GUILD_SCHEDULED_EVENT_CREATE,
      GUILD_SCHEDULED_EVENT_UPDATE,
      GUILD_SCHEDULED_EVENT_DELETE,
      THREAD_CREATE,
      THREAD_UPDATE,
      THREAD_DELETE,
      APPLICATION_COMMAND_PERMISSION_UPDATE,
      AUTO_MODERATION_RULE_CREATE,
      AUTO_MODERATION_RULE_UPDATE,
      AUTO_MODERATION_RULE_DELETE,
      AUTO_MODERATION_BLOCK_MESSAGE,
      AUTO_MODERATION_FLAG_TO_CHANNEL,
      AUTO_MODERATION_USER_COMMUNICATION_DISABLED,
      CREATOR_MONETIZATION_REQUEST_CREATED,
      CREATOR_MONETIZATION_TERMS_ACCEPTED
    )

  }
}
