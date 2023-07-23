//noinspection ScalaWeakerAccess, ScalaUnusedSymbol
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/AutoModerationRule.yaml

import ackcord.data.base._
import io.circe.Json

class AutoModerationRule(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

  /** The id of this rule */
  @inline def id: Snowflake[AutoModerationRule] = selectDynamic[Snowflake[AutoModerationRule]]("id")

  /** The id of the guild which this rule belongs to */
  @inline def guildId: GuildId = selectDynamic[GuildId]("guild_id")

  /** The rule name */
  @inline def name: String = selectDynamic[String]("name")

  /** The user which first created this rule */
  @inline def creatorId: UserId = selectDynamic[UserId]("creator_id")

  /** The rule event type */
  @inline def eventType: AutoModerationRule.AutoModerationRuleEventType =
    selectDynamic[AutoModerationRule.AutoModerationRuleEventType]("event_type")

  /** The rule trigger type */
  @inline def triggerType: AutoModerationRule.AutoModerationRuleTriggerType =
    selectDynamic[AutoModerationRule.AutoModerationRuleTriggerType]("trigger_type")

  /** The rule trigger metadata */
  @inline def triggerMetadata: AutoModerationRule.AutoModerationRuleTriggerMetadata =
    selectDynamic[AutoModerationRule.AutoModerationRuleTriggerMetadata]("trigger_metadata")

  /** The actions which will execute when the rule is triggered */
  @inline def actions: Seq[AutoModerationRule.AutoModerationRuleAction] =
    selectDynamic[Seq[AutoModerationRule.AutoModerationRuleAction]]("actions")

  /** Whether the rule is enabled */
  @inline def enabled: Boolean = selectDynamic[Boolean]("enabled")

  /** The role ids that should not be affected by the rule (Maximum of 20) */
  @inline def exemptRoles: Seq[RoleId] = selectDynamic[Seq[RoleId]]("exempt_roles")

  /** The channel ids that should not be affected by the rule (Maximum of 50) */
  @inline def exemptChannels: Seq[GuildChannelId] = selectDynamic[Seq[GuildChannelId]]("exempt_channels")

  override def values: Seq[() => Any] = Seq(
    () => id,
    () => guildId,
    () => name,
    () => creatorId,
    () => eventType,
    () => triggerType,
    () => triggerMetadata,
    () => actions,
    () => enabled,
    () => exemptRoles,
    () => exemptChannels
  )
}
object AutoModerationRule extends DiscordObjectCompanion[AutoModerationRule] {
  def makeRaw(json: Json, cache: Map[String, Any]): AutoModerationRule = new AutoModerationRule(json, cache)

  /**
    * @param id
    *   The id of this rule
    * @param guildId
    *   The id of the guild which this rule belongs to
    * @param name
    *   The rule name
    * @param creatorId
    *   The user which first created this rule
    * @param eventType
    *   The rule event type
    * @param triggerType
    *   The rule trigger type
    * @param triggerMetadata
    *   The rule trigger metadata
    * @param actions
    *   The actions which will execute when the rule is triggered
    * @param enabled
    *   Whether the rule is enabled
    * @param exemptRoles
    *   The role ids that should not be affected by the rule (Maximum of 20)
    * @param exemptChannels
    *   The channel ids that should not be affected by the rule (Maximum of 50)
    */
  def make20(
      id: Snowflake[AutoModerationRule],
      guildId: GuildId,
      name: String,
      creatorId: UserId,
      eventType: AutoModerationRule.AutoModerationRuleEventType,
      triggerType: AutoModerationRule.AutoModerationRuleTriggerType,
      triggerMetadata: AutoModerationRule.AutoModerationRuleTriggerMetadata,
      actions: Seq[AutoModerationRule.AutoModerationRuleAction],
      enabled: Boolean,
      exemptRoles: Seq[RoleId],
      exemptChannels: Seq[GuildChannelId]
  ): AutoModerationRule = makeRawFromFields(
    "id"               := id,
    "guild_id"         := guildId,
    "name"             := name,
    "creator_id"       := creatorId,
    "event_type"       := eventType,
    "trigger_type"     := triggerType,
    "trigger_metadata" := triggerMetadata,
    "actions"          := actions,
    "enabled"          := enabled,
    "exempt_roles"     := exemptRoles,
    "exempt_channels"  := exemptChannels
  )

  sealed case class AutoModerationRuleTriggerType private (value: Int) extends DiscordEnum[Int]
  object AutoModerationRuleTriggerType extends DiscordEnumCompanion[Int, AutoModerationRuleTriggerType] {

    /** Check if content contains words from a user defined list of keywords */
    val KEYWORD: AutoModerationRuleTriggerType = AutoModerationRuleTriggerType(1)

    /** Check if content represents generic spam */
    val SPAM: AutoModerationRuleTriggerType = AutoModerationRuleTriggerType(3)

    /** Check if content contains words from internal pre-defined wordsets */
    val KEYWORD_PRESET: AutoModerationRuleTriggerType = AutoModerationRuleTriggerType(4)

    /** Check if content contains more unique mentions than allowed */
    val MENTION_SPAM: AutoModerationRuleTriggerType = AutoModerationRuleTriggerType(5)

    def unknown(value: Int): AutoModerationRuleTriggerType = new AutoModerationRuleTriggerType(value)

    def values: Seq[AutoModerationRuleTriggerType] = Seq(KEYWORD, SPAM, KEYWORD_PRESET, MENTION_SPAM)

  }

  class AutoModerationRuleTriggerMetadata(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache) {

    /** Substrings which will be searched for in content (Maximum of 1000) */
    @inline def keywordFilter: Seq[String] = selectDynamic[Seq[String]]("keyword_filter")

    /**
      * Regular expression patterns which will be matched against content
      * (Maximum of 10)
      */
    @inline def regexPatterns: Seq[String] = selectDynamic[Seq[String]]("regex_patterns")

    /**
      * The internally pre-defined wordsets which will be searched for in
      * content
      */
    @inline def presets: Seq[AutoModerationRule.AutoModerationRuleKeywordPresetType] =
      selectDynamic[Seq[AutoModerationRule.AutoModerationRuleKeywordPresetType]]("presets")

    /** Substrings which should not trigger the rule (Maximum of 100 or 1000) */
    @inline def allowList: Seq[String] = selectDynamic[Seq[String]]("allow_list")

    /**
      * Total number of unique role and user mentions allowed per message
      * (Maximum of 50)
      */
    @inline def mentionTotalLimit: Int = selectDynamic[Int]("mention_total_limit")

    /** Whether to automatically detect mention raids */
    @inline def mentionRaidProtectionEnabled: Boolean = selectDynamic[Boolean]("mention_raid_protection_enabled")

    override def values: Seq[() => Any] = Seq(
      () => keywordFilter,
      () => regexPatterns,
      () => presets,
      () => allowList,
      () => mentionTotalLimit,
      () => mentionRaidProtectionEnabled
    )
  }
  object AutoModerationRuleTriggerMetadata extends DiscordObjectCompanion[AutoModerationRuleTriggerMetadata] {
    def makeRaw(json: Json, cache: Map[String, Any]): AutoModerationRuleTriggerMetadata =
      new AutoModerationRuleTriggerMetadata(json, cache)

    /**
      * @param keywordFilter
      *   Substrings which will be searched for in content (Maximum of 1000)
      * @param regexPatterns
      *   Regular expression patterns which will be matched against content
      *   (Maximum of 10)
      * @param presets
      *   The internally pre-defined wordsets which will be searched for in
      *   content
      * @param allowList
      *   Substrings which should not trigger the rule (Maximum of 100 or 1000)
      * @param mentionTotalLimit
      *   Total number of unique role and user mentions allowed per message
      *   (Maximum of 50)
      * @param mentionRaidProtectionEnabled
      *   Whether to automatically detect mention raids
      */
    def make20(
        keywordFilter: Seq[String],
        regexPatterns: Seq[String],
        presets: Seq[AutoModerationRule.AutoModerationRuleKeywordPresetType],
        allowList: Seq[String],
        mentionTotalLimit: Int,
        mentionRaidProtectionEnabled: Boolean
    ): AutoModerationRuleTriggerMetadata = makeRawFromFields(
      "keyword_filter"                  := keywordFilter,
      "regex_patterns"                  := regexPatterns,
      "presets"                         := presets,
      "allow_list"                      := allowList,
      "mention_total_limit"             := mentionTotalLimit,
      "mention_raid_protection_enabled" := mentionRaidProtectionEnabled
    )

  }

  sealed case class AutoModerationRuleKeywordPresetType private (value: Int) extends DiscordEnum[Int]
  object AutoModerationRuleKeywordPresetType extends DiscordEnumCompanion[Int, AutoModerationRuleKeywordPresetType] {

    /** Words that may be considered forms of swearing or cursing */
    val PROFANITY: AutoModerationRuleKeywordPresetType = AutoModerationRuleKeywordPresetType(1)

    /** Words that refer to sexually explicit behavior or activity */
    val SEXUAL_CONTENT: AutoModerationRuleKeywordPresetType = AutoModerationRuleKeywordPresetType(2)

    /** Personal insults or words that may be considered hate speech */
    val SLURS: AutoModerationRuleKeywordPresetType = AutoModerationRuleKeywordPresetType(3)

    def unknown(value: Int): AutoModerationRuleKeywordPresetType = new AutoModerationRuleKeywordPresetType(value)

    def values: Seq[AutoModerationRuleKeywordPresetType] = Seq(PROFANITY, SEXUAL_CONTENT, SLURS)

  }

  sealed case class AutoModerationRuleEventType private (value: Int) extends DiscordEnum[Int]
  object AutoModerationRuleEventType extends DiscordEnumCompanion[Int, AutoModerationRuleEventType] {

    /** When a member sends or edits a message in the guild */
    val MESSAGE_SEND: AutoModerationRuleEventType = AutoModerationRuleEventType(1)

    def unknown(value: Int): AutoModerationRuleEventType = new AutoModerationRuleEventType(value)

    def values: Seq[AutoModerationRuleEventType] = Seq(MESSAGE_SEND)

  }

  class AutoModerationRuleAction(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** The type of action */
    @inline def tpe: AutoModerationRule.AutoModerationRuleActionType =
      selectDynamic[AutoModerationRule.AutoModerationRuleActionType]("type")

    /**
      * Additional metadata needed during execution for this specific action
      * type
      */
    @inline def metadata: UndefOr[AutoModerationRule.AutoModerationRuleActionMetadata] =
      selectDynamic[UndefOr[AutoModerationRule.AutoModerationRuleActionMetadata]]("metadata")

    override def values: Seq[() => Any] = Seq(() => tpe, () => metadata)
  }
  object AutoModerationRuleAction extends DiscordObjectCompanion[AutoModerationRuleAction] {
    def makeRaw(json: Json, cache: Map[String, Any]): AutoModerationRuleAction =
      new AutoModerationRuleAction(json, cache)

    /**
      * @param tpe
      *   The type of action
      * @param metadata
      *   Additional metadata needed during execution for this specific action
      *   type
      */
    def make20(
        tpe: AutoModerationRule.AutoModerationRuleActionType,
        metadata: UndefOr[AutoModerationRule.AutoModerationRuleActionMetadata]
    ): AutoModerationRuleAction = makeRawFromFields("type" := tpe, "metadata" :=? metadata)

  }

  sealed case class AutoModerationRuleActionType private (value: Int) extends DiscordEnum[Int]
  object AutoModerationRuleActionType extends DiscordEnumCompanion[Int, AutoModerationRuleActionType] {

    /**
      * Blocks a member's message and prevents it from being posted. A custom
      * explanation can be specified and shown to members whenever their message
      * is blocked.
      */
    val BLOCK_MESSAGE: AutoModerationRuleActionType = AutoModerationRuleActionType(1)

    /** Logs user content to a specified channel */
    val SEND_ALERT_MESSAGE: AutoModerationRuleActionType = AutoModerationRuleActionType(2)

    /** Timeout user for a specified duration */
    val TIMEOUT: AutoModerationRuleActionType = AutoModerationRuleActionType(3)

    def unknown(value: Int): AutoModerationRuleActionType = new AutoModerationRuleActionType(value)

    def values: Seq[AutoModerationRuleActionType] = Seq(BLOCK_MESSAGE, SEND_ALERT_MESSAGE, TIMEOUT)

  }

  class AutoModerationRuleActionMetadata(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache) {

    /** Channel to which user content should be logged */
    @inline def channelId: ChannelId = selectDynamic[ChannelId]("channel_id")

    /** Timeout duration in seconds */
    @inline def durationSeconds: Int = selectDynamic[Int]("duration_seconds")

    /**
      * Additional explanation that will be shown to members whenever their
      * message is blocked
      */
    @inline def customMessage: String = selectDynamic[String]("custom_message")

    override def values: Seq[() => Any] = Seq(() => channelId, () => durationSeconds, () => customMessage)
  }
  object AutoModerationRuleActionMetadata extends DiscordObjectCompanion[AutoModerationRuleActionMetadata] {
    def makeRaw(json: Json, cache: Map[String, Any]): AutoModerationRuleActionMetadata =
      new AutoModerationRuleActionMetadata(json, cache)

    /**
      * @param channelId
      *   Channel to which user content should be logged
      * @param durationSeconds
      *   Timeout duration in seconds
      * @param customMessage
      *   Additional explanation that will be shown to members whenever their
      *   message is blocked
      */
    def make20(channelId: ChannelId, durationSeconds: Int, customMessage: String): AutoModerationRuleActionMetadata =
      makeRawFromFields(
        "channel_id"       := channelId,
        "duration_seconds" := durationSeconds,
        "custom_message"   := customMessage
      )

  }
}
