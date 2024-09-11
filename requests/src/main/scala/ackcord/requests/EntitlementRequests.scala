//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.requests

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/requests/EntitlementRequests.yaml

import ackcord.data._
import ackcord.data.base._
import io.circe.Json
import sttp.model.Method

object EntitlementRequests {

  class ListEntitlementsQuery(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** User ID to look up entitlements for */
    @inline def userId: UndefOr[UserId] = selectDynamic[UndefOr[UserId]]("user_id")

    @inline def withUserId(newValue: UndefOr[UserId]): ListEntitlementsQuery =
      objWithUndef(ListEntitlementsQuery, "user_id", newValue)

    /**
      * Comma-delimited set of snowflakes. Optional list of SKU IDs to check
      * entitlements for
      */
    @inline def skuIds: UndefOr[String] = selectDynamic[UndefOr[String]]("sku_ids")

    @inline def withSkuIds(newValue: UndefOr[String]): ListEntitlementsQuery =
      objWithUndef(ListEntitlementsQuery, "sku_ids", newValue)

    /** Retrieve entitlements before this time */
    @inline def before: UndefOr[RawSnowflake] = selectDynamic[UndefOr[RawSnowflake]]("before")

    @inline def withBefore(newValue: UndefOr[RawSnowflake]): ListEntitlementsQuery =
      objWithUndef(ListEntitlementsQuery, "before", newValue)

    /** Retrieve entitlements after this time */
    @inline def after: UndefOr[RawSnowflake] = selectDynamic[UndefOr[RawSnowflake]]("after")

    @inline def withAfter(newValue: UndefOr[RawSnowflake]): ListEntitlementsQuery =
      objWithUndef(ListEntitlementsQuery, "after", newValue)

    /** Number of entitlements to return, 1-100, default 100 */
    @inline def limit: UndefOr[Int] = selectDynamic[UndefOr[Int]]("limit")

    @inline def withLimit(newValue: UndefOr[Int]): ListEntitlementsQuery =
      objWithUndef(ListEntitlementsQuery, "limit", newValue)

    /** Guild ID to look up entitlements for */
    @inline def guildId: UndefOr[GuildId] = selectDynamic[UndefOr[GuildId]]("guild_id")

    @inline def withGuildId(newValue: UndefOr[GuildId]): ListEntitlementsQuery =
      objWithUndef(ListEntitlementsQuery, "guild_id", newValue)

    /** Whether entitlements should be omitted */
    @inline def excludeEnded: UndefOr[Boolean] = selectDynamic[UndefOr[Boolean]]("exclude_ended")

    @inline def withExcludeEnded(newValue: UndefOr[Boolean]): ListEntitlementsQuery =
      objWithUndef(ListEntitlementsQuery, "exclude_ended", newValue)

    override def values: Seq[() => Any] =
      Seq(() => userId, () => skuIds, () => before, () => after, () => limit, () => guildId, () => excludeEnded)
  }
  object ListEntitlementsQuery extends DiscordObjectCompanion[ListEntitlementsQuery] {
    def makeRaw(json: Json, cache: Map[String, Any]): ListEntitlementsQuery =
      new ListEntitlementsQuery(json, cache)

    /**
      * @param userId
      *   User ID to look up entitlements for
      * @param skuIds
      *   Comma-delimited set of snowflakes. Optional list of SKU IDs to check
      *   entitlements for
      * @param before
      *   Retrieve entitlements before this time
      * @param after
      *   Retrieve entitlements after this time
      * @param limit
      *   Number of entitlements to return, 1-100, default 100
      * @param guildId
      *   Guild ID to look up entitlements for
      * @param excludeEnded
      *   Whether entitlements should be omitted
      */
    def make20(
        userId: UndefOr[UserId] = UndefOrUndefined(Some("user_id")),
        skuIds: UndefOr[String] = UndefOrUndefined(Some("sku_ids")),
        before: UndefOr[RawSnowflake] = UndefOrUndefined(Some("before")),
        after: UndefOr[RawSnowflake] = UndefOrUndefined(Some("after")),
        limit: UndefOr[Int] = UndefOrUndefined(Some("limit")),
        guildId: UndefOr[GuildId] = UndefOrUndefined(Some("guild_id")),
        excludeEnded: UndefOr[Boolean] = UndefOrUndefined(Some("exclude_ended"))
    ): ListEntitlementsQuery = makeRawFromFields(
      "user_id"       :=? userId,
      "sku_ids"       :=? skuIds,
      "before"        :=? before,
      "after"         :=? after,
      "limit"         :=? limit,
      "guild_id"      :=? guildId,
      "exclude_ended" :=? excludeEnded
    )
  }

  /** Returns all entitlements for a given app, active and expired. */
  def listEntitlements(
      applicationId: ApplicationId,
      query: ListEntitlementsQuery = ListEntitlementsQuery.make20()
  ): Request[Unit, Seq[Entitlement]] =
    Request.restRequest(
      route = (Route.Empty / "applications" / Parameters[ApplicationId](
        "applicationId",
        applicationId
      ) / "entitlements" +? Parameters.query("user_id", query.userId) +? Parameters.query(
        "sku_ids",
        query.skuIds
      ) +? Parameters.query("before", query.before) +? Parameters.query("after", query.after) +? Parameters.query(
        "limit",
        query.limit
      ) +? Parameters.query("guild_id", query.guildId) +? Parameters.query("exclude_ended", query.excludeEnded))
        .toRequest(Method.GET)
    )

  class CreateTestEntitlementBody(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** ID of the SKU to grant the entitlement to */
    @inline def skuId: Snowflake[SKU] = selectDynamic[Snowflake[SKU]]("sku_id")

    @inline def withSkuId(newValue: Snowflake[SKU]): CreateTestEntitlementBody =
      objWith(CreateTestEntitlementBody, "sku_id", newValue)

    /** ID of the guild or user to grant the entitlement to */
    @inline def ownerId: RawSnowflake = selectDynamic[RawSnowflake]("owner_id")

    @inline def withOwnerId(newValue: RawSnowflake): CreateTestEntitlementBody =
      objWith(CreateTestEntitlementBody, "owner_id", newValue)

    /** 1 for a guild subscription, 2 for a user subscription */
    @inline def ownerType: Int = selectDynamic[Int]("owner_type")

    @inline def withOwnerType(newValue: Int): CreateTestEntitlementBody =
      objWith(CreateTestEntitlementBody, "owner_type", newValue)

    override def values: Seq[() => Any] = Seq(() => skuId, () => ownerId, () => ownerType)
  }
  object CreateTestEntitlementBody extends DiscordObjectCompanion[CreateTestEntitlementBody] {
    def makeRaw(json: Json, cache: Map[String, Any]): CreateTestEntitlementBody =
      new CreateTestEntitlementBody(json, cache)

    /**
      * @param skuId
      *   ID of the SKU to grant the entitlement to
      * @param ownerId
      *   ID of the guild or user to grant the entitlement to
      * @param ownerType
      *   1 for a guild subscription, 2 for a user subscription
      */
    def make20(skuId: Snowflake[SKU], ownerId: RawSnowflake, ownerType: Int): CreateTestEntitlementBody =
      makeRawFromFields("sku_id" := skuId, "owner_id" := ownerId, "owner_type" := ownerType)
  }

  /**
    * Creates a test entitlement to a given SKU for a given guild or user.
    * Discord will act as though that user or guild has entitlement to your
    * premium offering. This endpoint returns a partial entitlement object. It
    * will not contain subscription_id, starts_at, or ends_at, as it's valid in
    * perpetuity. After creating a test entitlement, you'll need to reload your
    * Discord client. After doing so, you'll see that your server or user now
    * has premium access.
    */
  def createTestEntitlement(
      applicationId: ApplicationId,
      body: CreateTestEntitlementBody
  ): Request[CreateTestEntitlementBody, Entitlement] =
    Request.restRequest(
      route =
        (Route.Empty / "applications" / Parameters[ApplicationId]("applicationId", applicationId) / "entitlements")
          .toRequest(Method.POST),
      params = body
    )

  /**
    * Deletes a currently-active test entitlement. Discord will act as though
    * that user or guild no longer has entitlement to your premium offering.
    */
  def deleteTestEntitlement(
      applicationId: ApplicationId,
      entitlementId: Snowflake[Entitlement]
  ): Request[Unit, Unit] =
    Request.restRequest(
      route = (Route.Empty / "applications" / Parameters[ApplicationId](
        "applicationId",
        applicationId
      ) / "entitlements" / Parameters[Snowflake[Entitlement]]("entitlementId", entitlementId)).toRequest(Method.DELETE)
    )
}