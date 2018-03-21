package net.katsstuff.ackcord

import net.katsstuff.ackcord.commands.{CmdCategory, CmdDescription, CmdFilter}
import net.katsstuff.ackcord.data.Message

/**
  * A handler for a specific command.
  *
  * @tparam A The parameter type.
  * @tparam B The return type, which may or may not be used for other stuff
  */
abstract class CommandHandler[A, B](
    val category: CmdCategory,
    val aliases: Seq[String],
    val filters: Seq[CmdFilter] = Nil,
    val description: Option[CmdDescription] = None
) {

  /**
    * Called whenever the command for this handler is received.
    * @param c A cache snapshot associated with the command.
    */
  def handle(msg: Message, args: A, remaining: List[String])(implicit c: CacheSnapshot): B
}

/**
  * A handler for a specific command that runs a [[RequestDSL]] when the command is received.
  *
  * @tparam A The parameter type.
  * @tparam B The return type, which may or may not be used for other stuff
  */
abstract class CommandHandlerDSL[A, B](
    val category: CmdCategory,
    val aliases: Seq[String],
    val filters: Seq[CmdFilter] = Nil,
    val description: Option[CmdDescription] = None
) {

  /**
    * Runs the [[RequestDSL]] whenever the command for this handler is received.
    * @param c A cache snapshot associated with the command.
    */
  def handle(msg: Message, args: A, remaining: List[String])(implicit c: CacheSnapshot): RequestDSL[B]
}
