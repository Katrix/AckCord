package net.katsstuff.ackcord

import akka.actor.ActorRef
import akka.util.ByteString
import net.katsstuff.ackcord.data.{RawSnowflake, UserId}
import net.katsstuff.ackcord.websocket.voice.VoiceUDPHandler.RTPHeader

/**
  * The base trait for all audio events. Note that the audio API does not
  * have any connections to any [[CacheSnapshot]]s.
  * As such you have to find the objects for the IDs yourself.
  */
sealed trait AudioAPIMessage {

  /**
    * The server id for the voice channel. For guilds this is the guild id.
    */
  def serverId: RawSnowflake

  /**
    * The client user id
    */
  def userId: UserId
}
object AudioAPIMessage {

  /**
    * Sent to the receiver when a user is speaking
    * @param speakingUserId The userId of the speaker
    * @param ssrc The ssrc of the speaker
    * @param isSpeaking If the user is speaking, or stopped speaking
    */
  case class UserSpeaking(
      speakingUserId: UserId,
      ssrc: Int,
      isSpeaking: Boolean,
      delay: Option[Int],
      serverId: RawSnowflake,
      userId: UserId
  ) extends AudioAPIMessage

  /**
    * Sent to the listener when everything is ready to send voice data.
    * @param udpHandler The udp handler. Used for sending data.
    */
  case class Ready(udpHandler: ActorRef, serverId: RawSnowflake, userId: UserId) extends AudioAPIMessage

  /**
    * Sent to the data receiver when a user speaks.
    * @param data The raw data
    * @param header The RTP header. This contains the ssrc of the speaker.
    *               To get the userId of the speaker, use [[UserSpeaking]].
    */
  case class ReceivedData(data: ByteString, header: RTPHeader, serverId: RawSnowflake, userId: UserId)
      extends AudioAPIMessage
}
