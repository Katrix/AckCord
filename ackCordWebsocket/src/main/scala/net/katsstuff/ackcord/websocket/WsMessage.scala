package net.katsstuff.ackcord.websocket

import cats.Eval
import io.circe.{Decoder, Encoder}

/**
  * Represents a message sent by websocket handlers
  *
  * @tparam D The data in this message
  * @tparam OpCode The opcode used by this websocket
  */
private[websocket] trait WsMessage[D, OpCode] {

  /**
    * The op code for the message.
    */
  def op: OpCode

  /**
    * The data for the message.
    */
  def d: Eval[Decoder.Result[D]]

  /**
    * A sequence number for the message if there is one.
    */
  def s: Option[Int] = None

  /**
    * An encoder for the message.
    */
  def dataEncoder: Encoder[D]
}
