package ackcord.gateway

import ackcord.data.{UndefOr, UndefOrSome, UndefOrUndefined}
import ackcord.gateway.data.GatewayEvent

class IdentifyData private (
    val token: String,
    val properties: Map[String, String],
    val compress: Compress,
    val largeThreshold: UndefOr[Int],
    val shard: UndefOr[Seq[Int]],
    val presence: UndefOr[GatewayEvent.UpdatePresence.Data],
    val intents: GatewayIntents,
) {

  private def copy(
      token: String = token,
      properties: Map[String, String] = properties,
      compress: Compress = compress,
      largeThreshold: UndefOr[Int] = largeThreshold,
      shard: UndefOr[Seq[Int]] = shard,
      presence: UndefOr[GatewayEvent.UpdatePresence.Data] = presence,
      intents: GatewayIntents = intents,
  ): IdentifyData = new IdentifyData(token, properties, compress, largeThreshold, shard, presence, intents)

  def withProperties(properties: Map[String, String]): IdentifyData = copy(properties = properties)
  def withCompress(compress: Compress): IdentifyData                = copy(compress = compress)
  def withLargeThreshold(largeThreshold: Int): IdentifyData         = copy(largeThreshold = UndefOrSome(largeThreshold))
  def withShard(shard: (Int, Int)): IdentifyData                    = copy(shard = UndefOrSome(Seq(shard._1, shard._2)))
  def withPresence(presence: GatewayEvent.UpdatePresence.Data): IdentifyData = copy(presence = UndefOrSome(presence))
}
object IdentifyData {

  def default(token: String, intents: GatewayIntents): IdentifyData =
    new IdentifyData(
      token = token,
      properties = Map("os" -> System.getProperty("os.name"), "browser" -> "AckCord 2", "device" -> "AckCord 2"),
      compress = Compress.ZLibStreamCompress,
      largeThreshold = UndefOrUndefined,
      shard = UndefOrUndefined,
      presence = UndefOrUndefined,
      intents = intents
    )
}
