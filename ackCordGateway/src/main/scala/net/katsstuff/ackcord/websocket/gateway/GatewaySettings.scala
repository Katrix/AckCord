package net.katsstuff.ackcord.websocket.gateway

import java.time.Instant

import net.katsstuff.ackcord.data.PresenceStatus
import net.katsstuff.ackcord.data.raw.RawActivity

/**
  * All the settings used by AckCord when connecting and similar
  *
  * @param token The token for the bot
  * @param largeThreshold The large threshold
  * @param shardNum The shard index of this
  * @param shardTotal The amount of shards
  * @param idleSince If the bot has been idle, set the time since
  * @param activity Send an activity when connecting
  * @param status The status to use when connecting
  * @param afk If the bot should be afk when connecting
  */
case class GatewaySettings(
    token: String,
    largeThreshold: Int = 100,
    shardNum: Int = 0,
    shardTotal: Int = 1,
    idleSince: Option[Instant] = None,
    activity: Option[RawActivity] = None,
    status: PresenceStatus = PresenceStatus.Online,
    afk: Boolean = false
) {
  activity.foreach(_.requireCanSend())
}
