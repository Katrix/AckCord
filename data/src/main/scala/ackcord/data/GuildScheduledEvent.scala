//noinspection ScalaWeakerAccess, ScalaUnusedSymbol, DuplicatedCode
package ackcord.data

// THIS FILE IS MACHINE GENERATED!
//
// Do not edit this file directly.
// Instead, edit the file generated/ackcord/data/GuildScheduledEvent.yaml

import java.time.OffsetDateTime

import ackcord.data.base._
import io.circe.Json

/** A scheduled event for a guild */
class GuildScheduledEvent(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

  /** The id of the scheduled event */
  @inline def id: Snowflake[GuildScheduledEvent] = selectDynamic[Snowflake[GuildScheduledEvent]]("id")

  @inline def withId(newValue: Snowflake[GuildScheduledEvent]): GuildScheduledEvent =
    objWith(GuildScheduledEvent, "id", newValue)

  /** the guild id which the scheduled event belongs to */
  @inline def guildId: GuildId = selectDynamic[GuildId]("guild_id")

  @inline def withGuildId(newValue: GuildId): GuildScheduledEvent = objWith(GuildScheduledEvent, "guild_id", newValue)

  /**
    * The channel id in which the scheduled event will be hosted, or null if
    * scheduled entity type is EXTERNAL
    */
  @inline def channelId: Option[VoiceGuildChannelId] = selectDynamic[Option[VoiceGuildChannelId]]("channel_id")

  @inline def withChannelId(newValue: Option[VoiceGuildChannelId]): GuildScheduledEvent =
    objWith(GuildScheduledEvent, "channel_id", newValue)

  /** The id of the user that created the scheduled event */
  @inline def creatorId: JsonOption[UserId] = selectDynamic[JsonOption[UserId]]("creator_id")

  @inline def withCreatorId(newValue: JsonOption[UserId]): GuildScheduledEvent =
    objWithUndef(GuildScheduledEvent, "creator_id", newValue)

  /** The name of the scheduled event (1-100 characters) */
  @inline def name: String = selectDynamic[String]("name")

  @inline def withName(newValue: String): GuildScheduledEvent = objWith(GuildScheduledEvent, "name", newValue)

  /** The description of the scheduled event (1-1000 characters) */
  @inline def description: JsonOption[String] = selectDynamic[JsonOption[String]]("description")

  @inline def withDescription(newValue: JsonOption[String]): GuildScheduledEvent =
    objWithUndef(GuildScheduledEvent, "description", newValue)

  /** The time the scheduled event will start */
  @inline def scheduledStartTime: OffsetDateTime = selectDynamic[OffsetDateTime]("scheduled_start_time")

  @inline def withScheduledStartTime(newValue: OffsetDateTime): GuildScheduledEvent =
    objWith(GuildScheduledEvent, "scheduled_start_time", newValue)

  /**
    * The time the scheduled event will end, required if entity_type is EXTERNAL
    */
  @inline def scheduledEndTime: Option[OffsetDateTime] = selectDynamic[Option[OffsetDateTime]]("scheduled_end_time")

  @inline def withScheduledEndTime(newValue: Option[OffsetDateTime]): GuildScheduledEvent =
    objWith(GuildScheduledEvent, "scheduled_end_time", newValue)

  /** The privacy level of the scheduled event */
  @inline def privacyLevel: GuildScheduledEvent.GuildScheduledEventPrivacyLevel =
    selectDynamic[GuildScheduledEvent.GuildScheduledEventPrivacyLevel]("privacy_level")

  @inline def withPrivacyLevel(
      newValue: GuildScheduledEvent.GuildScheduledEventPrivacyLevel
  ): GuildScheduledEvent = objWith(GuildScheduledEvent, "privacy_level", newValue)

  /** The status of the scheduled event */
  @inline def status: GuildScheduledEvent.GuildScheduledEventStatus =
    selectDynamic[GuildScheduledEvent.GuildScheduledEventStatus]("status")

  @inline def withStatus(newValue: GuildScheduledEvent.GuildScheduledEventStatus): GuildScheduledEvent =
    objWith(GuildScheduledEvent, "status", newValue)

  /** The type of the scheduled event */
  @inline def entityType: GuildScheduledEvent.GuildScheduledEventEntityType =
    selectDynamic[GuildScheduledEvent.GuildScheduledEventEntityType]("entity_type")

  @inline def withEntityType(
      newValue: GuildScheduledEvent.GuildScheduledEventEntityType
  ): GuildScheduledEvent = objWith(GuildScheduledEvent, "entity_type", newValue)

  /** The id of an entity associated with a guild scheduled event */
  @inline def entityId: Option[RawSnowflake] = selectDynamic[Option[RawSnowflake]]("entity_id")

  @inline def withEntityId(newValue: Option[RawSnowflake]): GuildScheduledEvent =
    objWith(GuildScheduledEvent, "entity_id", newValue)

  /** Additional metadata for the guild scheduled event */
  @inline def entityMetadata: Option[GuildScheduledEvent.GuildScheduledEventEntityMetadata] =
    selectDynamic[Option[GuildScheduledEvent.GuildScheduledEventEntityMetadata]]("entity_metadata")

  @inline def withEntityMetadata(
      newValue: Option[GuildScheduledEvent.GuildScheduledEventEntityMetadata]
  ): GuildScheduledEvent = objWith(GuildScheduledEvent, "entity_metadata", newValue)

  /** The user that created the scheduled event */
  @inline def creator: UndefOr[User] = selectDynamic[UndefOr[User]]("creator")

  @inline def withCreator(newValue: UndefOr[User]): GuildScheduledEvent =
    objWithUndef(GuildScheduledEvent, "creator", newValue)

  /** The number of users subscribed to the scheduled event */
  @inline def userCount: UndefOr[Int] = selectDynamic[UndefOr[Int]]("user_count")

  @inline def withUserCount(newValue: UndefOr[Int]): GuildScheduledEvent =
    objWithUndef(GuildScheduledEvent, "user_count", newValue)

  /** The cover image hash of the scheduled event */
  @inline def image: JsonOption[String] = selectDynamic[JsonOption[String]]("image")

  @inline def withImage(newValue: JsonOption[String]): GuildScheduledEvent =
    objWithUndef(GuildScheduledEvent, "image", newValue)

  override def values: Seq[() => Any] = Seq(
    () => id,
    () => guildId,
    () => channelId,
    () => creatorId,
    () => name,
    () => description,
    () => scheduledStartTime,
    () => scheduledEndTime,
    () => privacyLevel,
    () => status,
    () => entityType,
    () => entityId,
    () => entityMetadata,
    () => creator,
    () => userCount,
    () => image
  )
}
object GuildScheduledEvent extends DiscordObjectCompanion[GuildScheduledEvent] {
  def makeRaw(json: Json, cache: Map[String, Any]): GuildScheduledEvent =
    new GuildScheduledEvent(json, cache)

  /**
    * @param id
    *   The id of the scheduled event
    * @param guildId
    *   the guild id which the scheduled event belongs to
    * @param channelId
    *   The channel id in which the scheduled event will be hosted, or null if
    *   scheduled entity type is EXTERNAL
    * @param creatorId
    *   The id of the user that created the scheduled event
    * @param name
    *   The name of the scheduled event (1-100 characters)
    * @param description
    *   The description of the scheduled event (1-1000 characters)
    * @param scheduledStartTime
    *   The time the scheduled event will start
    * @param scheduledEndTime
    *   The time the scheduled event will end, required if entity_type is
    *   EXTERNAL
    * @param privacyLevel
    *   The privacy level of the scheduled event
    * @param status
    *   The status of the scheduled event
    * @param entityType
    *   The type of the scheduled event
    * @param entityId
    *   The id of an entity associated with a guild scheduled event
    * @param entityMetadata
    *   Additional metadata for the guild scheduled event
    * @param creator
    *   The user that created the scheduled event
    * @param userCount
    *   The number of users subscribed to the scheduled event
    * @param image
    *   The cover image hash of the scheduled event
    */
  def make20(
      id: Snowflake[GuildScheduledEvent],
      guildId: GuildId,
      channelId: Option[VoiceGuildChannelId],
      creatorId: JsonOption[UserId] = JsonUndefined(Some("creator_id")),
      name: String,
      description: JsonOption[String] = JsonUndefined(Some("description")),
      scheduledStartTime: OffsetDateTime,
      scheduledEndTime: Option[OffsetDateTime],
      privacyLevel: GuildScheduledEvent.GuildScheduledEventPrivacyLevel,
      status: GuildScheduledEvent.GuildScheduledEventStatus,
      entityType: GuildScheduledEvent.GuildScheduledEventEntityType,
      entityId: Option[RawSnowflake],
      entityMetadata: Option[GuildScheduledEvent.GuildScheduledEventEntityMetadata],
      creator: UndefOr[User] = UndefOrUndefined(Some("creator")),
      userCount: UndefOr[Int] = UndefOrUndefined(Some("user_count")),
      image: JsonOption[String] = JsonUndefined(Some("image"))
  ): GuildScheduledEvent = makeRawFromFields(
    "id"                   := id,
    "guild_id"             := guildId,
    "channel_id"           := channelId,
    "creator_id"          :=? creatorId,
    "name"                 := name,
    "description"         :=? description,
    "scheduled_start_time" := scheduledStartTime,
    "scheduled_end_time"   := scheduledEndTime,
    "privacy_level"        := privacyLevel,
    "status"               := status,
    "entity_type"          := entityType,
    "entity_id"            := entityId,
    "entity_metadata"      := entityMetadata,
    "creator"             :=? creator,
    "user_count"          :=? userCount,
    "image"               :=? image
  )

  sealed case class GuildScheduledEventPrivacyLevel private (value: Int) extends DiscordEnum[Int]
  object GuildScheduledEventPrivacyLevel extends DiscordEnumCompanion[Int, GuildScheduledEventPrivacyLevel] {

    /** The scheduled event is only accessible to guild members */
    val GUILD_ONLY: GuildScheduledEventPrivacyLevel = GuildScheduledEventPrivacyLevel(2)

    def unknown(value: Int): GuildScheduledEventPrivacyLevel = new GuildScheduledEventPrivacyLevel(value)

    val values: Seq[GuildScheduledEventPrivacyLevel] = Seq(GUILD_ONLY)
  }

  sealed case class GuildScheduledEventEntityType private (value: Int) extends DiscordEnum[Int]
  object GuildScheduledEventEntityType extends DiscordEnumCompanion[Int, GuildScheduledEventEntityType] {
    val STAGE_INSTANCE: GuildScheduledEventEntityType = GuildScheduledEventEntityType(1)

    val VOICE: GuildScheduledEventEntityType = GuildScheduledEventEntityType(2)

    val EXTERNAL: GuildScheduledEventEntityType = GuildScheduledEventEntityType(3)

    def unknown(value: Int): GuildScheduledEventEntityType = new GuildScheduledEventEntityType(value)

    val values: Seq[GuildScheduledEventEntityType] = Seq(STAGE_INSTANCE, VOICE, EXTERNAL)
  }

  sealed case class GuildScheduledEventStatus private (value: Int) extends DiscordEnum[Int]
  object GuildScheduledEventStatus extends DiscordEnumCompanion[Int, GuildScheduledEventStatus] {
    val SCHEDULED: GuildScheduledEventStatus = GuildScheduledEventStatus(1)

    val ACTIVE: GuildScheduledEventStatus = GuildScheduledEventStatus(2)

    val COMPLETED: GuildScheduledEventStatus = GuildScheduledEventStatus(3)

    val CANCELED: GuildScheduledEventStatus = GuildScheduledEventStatus(4)

    def unknown(value: Int): GuildScheduledEventStatus = new GuildScheduledEventStatus(value)

    val values: Seq[GuildScheduledEventStatus] = Seq(SCHEDULED, ACTIVE, COMPLETED, CANCELED)
  }

  /** Additional metadata for the guild scheduled event */
  class GuildScheduledEventEntityMetadata(json: Json, cache: Map[String, Any] = Map.empty)
      extends DiscordObject(json, cache) {

    /** The location of the scheduled event (1-100 characters) */
    @inline def location: String = selectDynamic[String]("location")

    @inline def withLocation(newValue: String): GuildScheduledEventEntityMetadata =
      objWith(GuildScheduledEventEntityMetadata, "location", newValue)

    override def values: Seq[() => Any] = Seq(() => location)
  }
  object GuildScheduledEventEntityMetadata extends DiscordObjectCompanion[GuildScheduledEventEntityMetadata] {
    def makeRaw(json: Json, cache: Map[String, Any]): GuildScheduledEventEntityMetadata =
      new GuildScheduledEventEntityMetadata(json, cache)

    /**
      * @param location
      *   The location of the scheduled event (1-100 characters)
      */
    def make20(location: String): GuildScheduledEventEntityMetadata = makeRawFromFields("location" := location)
  }

  class GuildScheduledEventUser(json: Json, cache: Map[String, Any] = Map.empty) extends DiscordObject(json, cache) {

    /** The scheduled event id which the user subscribed to */
    @inline def guildScheduledEventId: GuildScheduledEventId =
      selectDynamic[GuildScheduledEventId]("guild_scheduled_event_id")

    @inline def withGuildScheduledEventId(newValue: GuildScheduledEventId): GuildScheduledEventUser =
      objWith(GuildScheduledEventUser, "guild_scheduled_event_id", newValue)

    /** User which subscribed to an event */
    @inline def user: User = selectDynamic[User]("user")

    @inline def withUser(newValue: User): GuildScheduledEventUser = objWith(GuildScheduledEventUser, "user", newValue)

    /**
      * Guild member data for this user for the guild which this event belongs
      * to, if any
      */
    @inline def member: UndefOr[GuildMember] = selectDynamic[UndefOr[GuildMember]]("member")

    @inline def withMember(newValue: UndefOr[GuildMember]): GuildScheduledEventUser =
      objWithUndef(GuildScheduledEventUser, "member", newValue)

    override def values: Seq[() => Any] = Seq(() => guildScheduledEventId, () => user, () => member)
  }
  object GuildScheduledEventUser extends DiscordObjectCompanion[GuildScheduledEventUser] {
    def makeRaw(json: Json, cache: Map[String, Any]): GuildScheduledEventUser =
      new GuildScheduledEventUser(json, cache)

    /**
      * @param guildScheduledEventId
      *   The scheduled event id which the user subscribed to
      * @param user
      *   User which subscribed to an event
      * @param member
      *   Guild member data for this user for the guild which this event belongs
      *   to, if any
      */
    def make20(
        guildScheduledEventId: GuildScheduledEventId,
        user: User,
        member: UndefOr[GuildMember] = UndefOrUndefined(Some("member"))
    ): GuildScheduledEventUser =
      makeRawFromFields("guild_scheduled_event_id" := guildScheduledEventId, "user" := user, "member" :=? member)
  }
}
