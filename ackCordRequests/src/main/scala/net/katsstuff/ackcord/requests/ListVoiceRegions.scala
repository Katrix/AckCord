package net.katsstuff.ackcord.requests

import akka.NotUsed
import io.circe._
import net.katsstuff.ackcord.data.VoiceRegion
import net.katsstuff.ackcord.data.DiscordProtocol._

/**
  * List all the voice regions that can be used when creating a guild.
  */
case class ListVoiceRegions[Ctx](context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Seq[VoiceRegion], Ctx] {
  override def route: RequestRoute = Routes.listVoiceRegions

  override def responseDecoder: Decoder[Seq[VoiceRegion]] = Decoder[Seq[VoiceRegion]]
}
