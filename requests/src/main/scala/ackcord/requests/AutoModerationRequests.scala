//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.requests

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/requests/AutoModerationRequests.yaml

import ackcord.data._
import ackcord.data.base._
import io.circe.Json
import sttp.model.Method

object AutoModerationRequests {

  def listAutoModerationRulesForGuild(guildId: GuildId): Request[Unit, Seq[AutoModerationRule]] =
    Request.restRequest(
      route =
        (Route.Empty / "guilds" / Parameters[GuildId]("guildId", guildId, major = true) / "auto-moderation" / "rules")
          .toRequest(Method.GET)
    )

  def getAutoModerationRule(
      guildId: GuildId,
      autoModerationRuleId: Snowflake[AutoModerationRule]
  ): Request[Unit, AutoModerationRule] =
    Request.restRequest(
      route = (Route.Empty / "guilds" / Parameters[GuildId](
        "guildId",
        guildId,
        major = true
      ) / "auto-moderation" / "rules" / Parameters[Snowflake[AutoModerationRule]](
        "autoModerationRuleId",
        autoModerationRuleId
      )).toRequest(Method.GET)
    )

  class CreateAutoModerationRuleBody(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache) {

    /** The rule name */
    @inline def name: String = selectDynamic[String]("name")

    @inline def withName(newValue: String): CreateAutoModerationRuleBody =
      objWith(CreateAutoModerationRuleBody, "name", newValue)

    /** The event type */
    @inline def eventType: AutoModerationRule.AutoModerationRuleEventType =
      selectDynamic[AutoModerationRule.AutoModerationRuleEventType]("event_type")

    @inline def withEventType(newValue: AutoModerationRule.AutoModerationRuleEventType): CreateAutoModerationRuleBody =
      objWith(CreateAutoModerationRuleBody, "event_type", newValue)

    /** The trigger type */
    @inline def triggerType: AutoModerationRule.AutoModerationRuleTriggerType =
      selectDynamic[AutoModerationRule.AutoModerationRuleTriggerType]("trigger_type")

    @inline def withTriggerType(
        newValue: AutoModerationRule.AutoModerationRuleTriggerType
    ): CreateAutoModerationRuleBody = objWith(CreateAutoModerationRuleBody, "trigger_type", newValue)

    /** The trigger metadata */
    @inline def triggerMetadata: AutoModerationRule.AutoModerationRuleTriggerMetadata =
      selectDynamic[AutoModerationRule.AutoModerationRuleTriggerMetadata]("trigger_metadata")

    @inline def withTriggerMetadata(
        newValue: AutoModerationRule.AutoModerationRuleTriggerMetadata
    ): CreateAutoModerationRuleBody = objWith(CreateAutoModerationRuleBody, "trigger_metadata", newValue)

    /** The actions which will execute when the rule is triggered */
    @inline def actions: Seq[AutoModerationRule.AutoModerationRuleAction] =
      selectDynamic[Seq[AutoModerationRule.AutoModerationRuleAction]]("actions")

    @inline def withActions(
        newValue: Seq[AutoModerationRule.AutoModerationRuleAction]
    ): CreateAutoModerationRuleBody = objWith(CreateAutoModerationRuleBody, "actions", newValue)

    /** Whether the rule is enabled (False by default) */
    @inline def enabled: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("enabled")

    @inline def withEnabled(newValue: UndefOr[Boolean]): CreateAutoModerationRuleBody =
      objWithUndef(CreateAutoModerationRuleBody, "enabled", newValue)

    /** The role ids that should not be affected by the rule (Maximum of 20) */
    @inline def exemptRoles: Seq[RoleId] = selectDynamic[Seq[RoleId]]("exempt_roles")

    @inline def withExemptRoles(newValue: Seq[RoleId]): CreateAutoModerationRuleBody =
      objWith(CreateAutoModerationRuleBody, "exempt_roles", newValue)

    /**
      * The channel ids that should not be affected by the rule (Maximum of 50)
      */
    @inline def exemptChannels: Seq[GuildChannelId] = selectDynamic[Seq[GuildChannelId]]("exempt_channels")

    @inline def withExemptChannels(newValue: Seq[GuildChannelId]): CreateAutoModerationRuleBody =
      objWith(CreateAutoModerationRuleBody, "exempt_channels", newValue)

    override def values: Seq[() => Any] = Seq(
      () => name,
      () => eventType,
      () => triggerType,
      () => triggerMetadata,
      () => actions,
      () => enabled,
      () => exemptRoles,
      () => exemptChannels
    )
  }
  object CreateAutoModerationRuleBody extends DiscordObjectCompanion[CreateAutoModerationRuleBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): CreateAutoModerationRuleBody =
      new CreateAutoModerationRuleBody(json, cache)

    /**
      * @param name
      *   The rule name
      * @param eventType
      *   The event type
      * @param triggerType
      *   The trigger type
      * @param triggerMetadata
      *   The trigger metadata
      * @param actions
      *   The actions which will execute when the rule is triggered
      * @param enabled
      *   Whether the rule is enabled (False by default)
      * @param exemptRoles
      *   The role ids that should not be affected by the rule (Maximum of 20)
      * @param exemptChannels
      *   The channel ids that should not be affected by the rule (Maximum of
      *   50)
      */
    def make20(
        name: String,
        eventType: AutoModerationRule.AutoModerationRuleEventType,
        triggerType: AutoModerationRule.AutoModerationRuleTriggerType,
        triggerMetadata: AutoModerationRule.AutoModerationRuleTriggerMetadata,
        actions: Seq[AutoModerationRule.AutoModerationRuleAction],
        enabled: UndefOr[Boolean] = UndefOrUndefined(Some("enabled")),
        exemptRoles: Seq[RoleId],
        exemptChannels: Seq[GuildChannelId]
    ): CreateAutoModerationRuleBody = makeRawFromFields(
      "name"             := name,
      "event_type"       := eventType,
      "trigger_type"     := triggerType,
      "trigger_metadata" := triggerMetadata,
      "actions"          := actions,
      "enabled"         :=? enabled,
      "exempt_roles"     := exemptRoles,
      "exempt_channels"  := exemptChannels
    )
  }

  def createAutoModerationRule(
      guildId: GuildId,
      body: CreateAutoModerationRuleBody,
      reason: Option[String]
  ): Request[CreateAutoModerationRuleBody, AutoModerationRule] =
    Request.restRequest(
      route =
        (Route.Empty / "guilds" / Parameters[GuildId]("guildId", guildId, major = true) / "auto-moderation" / "rules")
          .toRequest(Method.POST),
      params = body,
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )

  class ModifyAutoModerationRuleBody(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache) {

    /** The rule name */
    @inline def name: UndefOr[String] = selectDynamic[UndefOr[String]]("name")

    @inline def withName(newValue: UndefOr[String]): ModifyAutoModerationRuleBody =
      objWithUndef(ModifyAutoModerationRuleBody, "name", newValue)

    /** The event type */
    @inline def eventType: UndefOr[AutoModerationRule.AutoModerationRuleEventType] =
      selectDynamic[UndefOr[AutoModerationRule.AutoModerationRuleEventType]]("event_type")

    @inline def withEventType(
        newValue: UndefOr[AutoModerationRule.AutoModerationRuleEventType]
    ): ModifyAutoModerationRuleBody = objWithUndef(ModifyAutoModerationRuleBody, "event_type", newValue)

    /** The trigger metadata */
    @inline def triggerMetadata: UndefOr[AutoModerationRule.AutoModerationRuleTriggerMetadata] =
      selectDynamic[UndefOr[AutoModerationRule.AutoModerationRuleTriggerMetadata]]("trigger_metadata")

    @inline def withTriggerMetadata(
        newValue: UndefOr[AutoModerationRule.AutoModerationRuleTriggerMetadata]
    ): ModifyAutoModerationRuleBody = objWithUndef(ModifyAutoModerationRuleBody, "trigger_metadata", newValue)

    /** The actions which will execute when the rule is triggered */
    @inline def actions: UndefOr[Seq[AutoModerationRule.AutoModerationRuleAction]] =
      selectDynamic[UndefOr[Seq[AutoModerationRule.AutoModerationRuleAction]]]("actions")

    @inline def withActions(
        newValue: UndefOr[Seq[AutoModerationRule.AutoModerationRuleAction]]
    ): ModifyAutoModerationRuleBody = objWithUndef(ModifyAutoModerationRuleBody, "actions", newValue)

    /** Whether the rule is enabled */
    @inline def enabled: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("enabled")

    @inline def withEnabled(newValue: UndefOr[Boolean]): ModifyAutoModerationRuleBody =
      objWithUndef(ModifyAutoModerationRuleBody, "enabled", newValue)

    /** The role ids that should not be affected by the rule (Maximum of 20) */
    @inline def exemptRoles: UndefOr[Seq[RoleId]] = selectDynamic[UndefOr[Seq[RoleId]]]("exempt_roles")

    @inline def withExemptRoles(newValue: UndefOr[Seq[RoleId]]): ModifyAutoModerationRuleBody =
      objWithUndef(ModifyAutoModerationRuleBody, "exempt_roles", newValue)

    /**
      * The channel ids that should not be affected by the rule (Maximum of 50)
      */
    @inline def exemptChannels: UndefOr[Seq[GuildChannelId]] =
      selectDynamic[UndefOr[Seq[GuildChannelId]]]("exempt_channels")

    @inline def withExemptChannels(newValue: UndefOr[Seq[GuildChannelId]]): ModifyAutoModerationRuleBody =
      objWithUndef(ModifyAutoModerationRuleBody, "exempt_channels", newValue)

    override def values: Seq[() => Any] = Seq(
      () => name,
      () => eventType,
      () => triggerMetadata,
      () => actions,
      () => enabled,
      () => exemptRoles,
      () => exemptChannels
    )
  }
  object ModifyAutoModerationRuleBody extends DiscordObjectCompanion[ModifyAutoModerationRuleBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): ModifyAutoModerationRuleBody =
      new ModifyAutoModerationRuleBody(json, cache)

    /**
      * @param name
      *   The rule name
      * @param eventType
      *   The event type
      * @param triggerMetadata
      *   The trigger metadata
      * @param actions
      *   The actions which will execute when the rule is triggered
      * @param enabled
      *   Whether the rule is enabled
      * @param exemptRoles
      *   The role ids that should not be affected by the rule (Maximum of 20)
      * @param exemptChannels
      *   The channel ids that should not be affected by the rule (Maximum of
      *   50)
      */
    def make20(
        name: UndefOr[String] = UndefOrUndefined(Some("name")),
        eventType: UndefOr[AutoModerationRule.AutoModerationRuleEventType] = UndefOrUndefined(Some("event_type")),
        triggerMetadata: UndefOr[AutoModerationRule.AutoModerationRuleTriggerMetadata] = UndefOrUndefined(
          Some("trigger_metadata")
        ),
        actions: UndefOr[Seq[AutoModerationRule.AutoModerationRuleAction]] = UndefOrUndefined(Some("actions")),
        enabled: UndefOr[Boolean] = UndefOrUndefined(Some("enabled")),
        exemptRoles: UndefOr[Seq[RoleId]] = UndefOrUndefined(Some("exempt_roles")),
        exemptChannels: UndefOr[Seq[GuildChannelId]] = UndefOrUndefined(Some("exempt_channels"))
    ): ModifyAutoModerationRuleBody = makeRawFromFields(
      "name"             :=? name,
      "event_type"       :=? eventType,
      "trigger_metadata" :=? triggerMetadata,
      "actions"          :=? actions,
      "enabled"          :=? enabled,
      "exempt_roles"     :=? exemptRoles,
      "exempt_channels"  :=? exemptChannels
    )
  }

  def modifyAutoModerationRule(
      guildId: GuildId,
      autoModerationRuleId: Snowflake[AutoModerationRule],
      body: ModifyAutoModerationRuleBody,
      reason: Option[String]
  ): Request[ModifyAutoModerationRuleBody, AutoModerationRule] =
    Request.restRequest(
      route = (Route.Empty / "guilds" / Parameters[GuildId](
        "guildId",
        guildId,
        major = true
      ) / "auto-moderation" / "rules" / Parameters[Snowflake[AutoModerationRule]](
        "autoModerationRuleId",
        autoModerationRuleId
      )).toRequest(Method.PATCH),
      params = body,
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )

  def deleteAutoModerationRule(
      guildId: GuildId,
      autoModerationRuleId: Snowflake[AutoModerationRule],
      reason: Option[String]
  ): Request[Unit, Unit] =
    Request.restRequest(
      route = (Route.Empty / "guilds" / Parameters[GuildId](
        "guildId",
        guildId,
        major = true
      ) / "auto-moderation" / "rules" / Parameters[Snowflake[AutoModerationRule]](
        "autoModerationRuleId",
        autoModerationRuleId
      )).toRequest(Method.DELETE),
      extraHeaders = reason.fold(Map.empty[String, String])(r => Map("X-Audit-Log-Reason" -> r))
    )
}
