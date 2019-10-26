package ackcord.voice

import java.nio.{ByteBuffer, ByteOrder}

import akka.util.ByteString

/**
  * Represents the RTP header used for sending and receiving voice data
  *
  * @param tpe The type to use. Should be `0x80`
  * @param version The version to use. Should be `0x78`
  * @param sequence The sequence
  * @param timestamp Timestamp
  * @param ssrc SSRC of sender
  */
case class RTPHeader(tpe: Byte, version: Byte, sequence: Short, timestamp: Int, ssrc: Int) {
  lazy val byteString: ByteString = {
    val builder                   = ByteString.newBuilder
    implicit val order: ByteOrder = ByteOrder.BIG_ENDIAN
    builder.putByte(tpe)
    builder.putByte(version)
    builder.putShort(sequence)
    builder.putInt(timestamp)
    builder.putInt(ssrc)

    builder.result()
  }

  def nonceToBuffer(buffer: ByteBuffer): Unit = {
    buffer.put(tpe)
    buffer.put(version)
    buffer.putShort(sequence)
    buffer.putInt(timestamp)
    buffer.putInt(ssrc)
  }
}
object RTPHeader {

  /**
    * Deserialize an [[RTPHeader]]
    */
  def fromBytes(bytes: ByteString): (RTPHeader, ByteString) = {
    val (header, extra) = bytes.splitAt(12)

    val buffer    = header.asByteBuffer.order(ByteOrder.BIG_ENDIAN)
    val tpe       = buffer.get()
    val version   = buffer.get()
    val sequence  = buffer.getShort()
    val timestamp = buffer.getInt()
    val ssrc      = buffer.getInt()

    //https://tools.ietf.org/html/rfc5285#section-4.2
    //I have no idea what this does
    if (tpe == 0x90 && extra(0) == 0xBE && extra(1) == 0xDE) {
      val hlen = extra(2) << 8 | extra(3)
      var i    = 4

      while (i < hlen + 4) {
        val b   = extra(i)
        val len = (b & 0x0F) + 1
        i += (len + 1)
      }
      while (extra(i) == 0) i += 1

      val newAudio = extra.drop(i)
      (RTPHeader(tpe, version, sequence, timestamp, ssrc), newAudio)
    } else (RTPHeader(tpe, version, sequence, timestamp, ssrc), extra)
  }

  def apply(sequence: Short, timestamp: Int, ssrc: Int): RTPHeader =
    RTPHeader(0x80.toByte, 0x78, sequence, timestamp, ssrc)
}
