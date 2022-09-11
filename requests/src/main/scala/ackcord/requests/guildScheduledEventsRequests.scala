package ackcord.requests

import java.time.OffsetDateTime

import ackcord.data.DiscordProtocol._
import ackcord.data._
import ackcord.util.{JsonOption, JsonUndefined, Verifier}
import io.circe._

/**
  * List the scheduled events for a guild.
  * @param withUserCount
  *   Include number of users subscribed to each event.
  */
case class ListScheduledEventsForGuild(guildId: GuildId, withUserCount: Option[Boolean] = None)
    extends NoParamsNiceResponseRequest[Seq[GuildScheduledEvent]] {
  override def route: RequestRoute = Routes.listScheduledEventsForGuild(guildId, withUserCount)

  override def responseDecoder: Decoder[Seq[GuildScheduledEvent]] = Decoder[Seq[GuildScheduledEvent]]
}

case class CreateGuildScheduledEventData(
    channelId: Option[VoiceGuildChannelId],
    entityMetadata: Option[GuildScheduledEventEntityMetadata],
    name: String,
    privacyLevel: GuildScheduledEventPrivacyLevel,
    scheduledStartTime: OffsetDateTime,
    scheduledEndTime: Option[OffsetDateTime],
    description: Option[String],
    entityType: Option[GuildScheduledEventEntityType]
)

/** Create a new guild scheduled event. */
case class CreateGuildScheduledEvent(
    guildId: GuildId,
    params: CreateGuildScheduledEventData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[CreateGuildScheduledEvent, CreateGuildScheduledEventData, GuildScheduledEvent] {
  override def route: RequestRoute = Routes.createGuildScheduledEvent(guildId)

  override def paramsEncoder: Encoder[CreateGuildScheduledEventData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  override def responseDecoder: Decoder[GuildScheduledEvent] = Decoder[GuildScheduledEvent]

  override def withReason(reason: String): CreateGuildScheduledEvent = copy(reason = Some(reason))
}

/**
  * Get a specific guild scheduled event.
  * @param withUserCount
  *   Include number of users subscribed to each event.
  */
case class GetGuildScheduledEvent(
    guildId: GuildId,
    guildScheduledEventId: SnowflakeType[GuildScheduledEvent],
    withUserCount: Option[Boolean] = None
) extends NoParamsNiceResponseRequest[GuildScheduledEvent] {
  override def route: RequestRoute = Routes.getGuildScheduledEvent(guildId, guildScheduledEventId, withUserCount)
  override def responseDecoder: Decoder[GuildScheduledEvent] = Decoder[GuildScheduledEvent]
}

case class ModifyGuildScheduledEventData(
    channelId: JsonOption[VoiceGuildChannelId] = JsonUndefined,
    entityMetadata: JsonOption[GuildScheduledEventEntityMetadata] = JsonUndefined,
    name: JsonOption[String] = JsonUndefined,
    privacyLevel: JsonOption[StageInstancePrivacyLevel] = JsonUndefined,
    scheduledStartTime: JsonOption[OffsetDateTime] = JsonUndefined,
    scheduledEndTime: JsonOption[OffsetDateTime] = JsonUndefined,
    description: JsonOption[String] = JsonUndefined,
    entityType: JsonOption[GuildScheduledEventEntityType] = JsonUndefined,
    status: JsonOption[GuildScheduledEventStatus] = JsonUndefined
)
object ModifyGuildScheduledEventData {
  implicit val encoder: Encoder[ModifyGuildScheduledEventData] = (a: ModifyGuildScheduledEventData) =>
    JsonOption.removeUndefinedToObj(
      "channel_id"           -> a.channelId.toJson,
      "entity_metadata"      -> a.entityMetadata.toJson,
      "name"                 -> a.name.toJson,
      "privacy_level"        -> a.privacyLevel.toJson,
      "scheduled_start_time" -> a.scheduledStartTime.toJson,
      "scheduled_end_time"   -> a.scheduledEndTime.toJson,
      "description"          -> a.description.toJson,
      "entity_type"          -> a.entityType.toJson,
      "status"               -> a.status.toJson
    )
}

/** Modify a specific guild scheduled event. */
case class ModifyGuildScheduledEvent(
    guildId: GuildId,
    guildScheduledEventId: SnowflakeType[GuildScheduledEvent],
    params: ModifyGuildScheduledEventData,
    reason: Option[String] = None
) extends NoNiceResponseReasonRequest[ModifyGuildScheduledEvent, ModifyGuildScheduledEventData, GuildScheduledEvent] {
  override def route: RequestRoute = Routes.modifyGuildScheduledEvent(guildId, guildScheduledEventId)

  override def paramsEncoder: Encoder[ModifyGuildScheduledEventData] = ModifyGuildScheduledEventData.encoder
  override def responseDecoder: Decoder[GuildScheduledEvent]         = Decoder[GuildScheduledEvent]

  override def withReason(reason: String): ModifyGuildScheduledEvent = copy(reason = Some(reason))
}

/** Delete a specific guild scheduled event. */
case class DeleteGuildScheduledEvent(guildId: GuildId, guildScheduledEventId: SnowflakeType[GuildScheduledEvent])
    extends NoParamsResponseRequest {
  override def route: RequestRoute = Routes.deleteGuildScheduledEvent(guildId, guildScheduledEventId)
}

/**
  * Get the users that have subscribed to a guild scheduled event.
  * @param limit
  *   How many users to receive.
  * @param withMember
  *   If the members should be included if they exist.
  * @param before
  *   Only return users before the given user id.
  * @param after
  *   Only return users after the given user id.
  */
case class GetGuildScheduledEventUsers(
    guildId: GuildId,
    guildScheduledEventId: SnowflakeType[GuildScheduledEvent],
    limit: Option[Int] = None,
    withMember: Option[Boolean] = None,
    before: Option[UserId] = None,
    after: Option[UserId] = None
) extends NoParamsNiceResponseRequest[Seq[GuildScheduledEventUser]] {
  Verifier.requireRangeO(limit, "Limit", max = 100)

  override def route: RequestRoute =
    Routes.getGuildScheduledEventUsers(guildId, guildScheduledEventId, limit, withMember, before, after)
  override def responseDecoder: Decoder[Seq[GuildScheduledEventUser]] = Decoder[Seq[GuildScheduledEventUser]]
}
