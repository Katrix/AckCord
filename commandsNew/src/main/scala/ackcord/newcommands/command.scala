package ackcord.newcommands

import akka.stream.scaladsl.Flow

/**
  * A constructed command execution.
  *
  * @tparam A The argument type of the command
  * @tparam Mat The materialized result of creating this command
  */
trait ComplexCommand[A, Mat] {

  def parser: MessageParser[A]

  def flow: Flow[CommandMessage[A], CommandError, Mat]
}

/**
  * A constructed command execution with a name.
  *
  * @tparam A The argument type of the command
  * @tparam Mat The materialized result of creating this command
  */
trait NamedComplexCommand[A, Mat] extends ComplexCommand[A, Mat] {

  /**
    * The prefix symbol to use for this command.
    */
  def symbol: String

  /**
    * The valid aliases of this command.
    */
  def aliases: Seq[String]

  /**
    * If this command requires a mention when invoking it.
    */
  def requiresMention: Boolean
}

/**
  * Represents non essential information about a command intended to be
  * displayed to an end user.
  *
  * @param name The display name of a command.
  * @param description The description of what a command does.
  * @param usage How to use the command. Does not include the name or prefix.
  * @param extra Extra stuff about the command that you yourself decide on.
  */
case class CommandDescription(
    name: String,
    description: String,
    usage: String = "",
    extra: Map[String, String] = Map.empty
)
