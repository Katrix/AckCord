package ackcord.requests

import java.time.OffsetDateTime

import ackcord.data.DiscordProtocol._
import ackcord.data._
import ackcord.util.{JsonOption, JsonUndefined}
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
case class CreateGuildScheduledEvent(guildId: GuildId, params: CreateGuildScheduledEventData)
    extends NoNiceResponseRequest[CreateGuildScheduledEventData, GuildScheduledEvent] {
  override def route: RequestRoute = Routes.createGuildScheduledEvent(guildId)

  override def paramsEncoder: Encoder[CreateGuildScheduledEventData] =
    derivation.deriveEncoder(derivation.renaming.snakeCase)
  override def responseDecoder: Decoder[GuildScheduledEvent] = Decoder[GuildScheduledEvent]
}

/** Get a specific guild scheduled event. */
case class GetGuildScheduledEvent(guildId: GuildId, guildScheduledEventId: SnowflakeType[GuildScheduledEvent])
    extends NoParamsNiceResponseRequest[GuildScheduledEvent] {
  override def route: RequestRoute = Routes.getGuildScheduledEvent(guildId, guildScheduledEventId)
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
    params: ModifyGuildScheduledEventData
) extends NoNiceResponseRequest[ModifyGuildScheduledEventData, GuildScheduledEvent] {
  override def route: RequestRoute = Routes.modifyGuildScheduledEvent(guildId, guildScheduledEventId)

  override def paramsEncoder: Encoder[ModifyGuildScheduledEventData] = ModifyGuildScheduledEventData.encoder
  override def responseDecoder: Decoder[GuildScheduledEvent]         = Decoder[GuildScheduledEvent]
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
  override def route: RequestRoute =
    Routes.getGuildScheduledEventUsers(guildId, guildScheduledEventId, limit, withMember, before, after)
  override def responseDecoder: Decoder[Seq[GuildScheduledEventUser]] = Decoder[Seq[GuildScheduledEventUser]]
}
