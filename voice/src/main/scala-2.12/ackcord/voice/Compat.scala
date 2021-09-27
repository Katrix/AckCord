package ackcord.voice

import akka.util.ByteString

private[voice] object Compat {

  def padBytestring(bs: ByteString, len: Int, elem: Byte): ByteString =
    bs.padTo(len, elem)
}
