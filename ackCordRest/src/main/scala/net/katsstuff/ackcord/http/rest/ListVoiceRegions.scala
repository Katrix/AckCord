package net.katsstuff.ackcord.http.rest

import akka.NotUsed
import io.circe._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.http.Routes
import net.katsstuff.ackcord.http.requests.RequestRoute

import net.katsstuff.ackcord.data.DiscordProtocol._

//Place for future audit log requests if they should ever appear

/**
  * List all the voice regions that can be used when creating a guild.
  */
case class ListVoiceRegions[Ctx](context: Ctx = NotUsed: NotUsed)
    extends NoParamsNiceResponseRequest[Seq[VoiceRegion], Ctx] {
  override def route: RequestRoute = Routes.listVoiceRegions

  override def responseDecoder: Decoder[Seq[VoiceRegion]] = Decoder[Seq[VoiceRegion]]
}
