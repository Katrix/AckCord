package ackcord.newcommands

import scala.language.higherKinds

import akka.stream.scaladsl.Flow

/**
  * A constructed command execution.
  *
  * @tparam F The effect type of the cache
  * @tparam A The argument type of the command
  * @tparam Mat The materialized result of creating this command
  */
trait Command[F[_], A, Mat] {

  def parser: MessageParser[A]

  def flow: Flow[CommandMessage[F, A], CommandError[F], Mat]
}

/**
  * A constructed command execution with a name.
  *
  * @tparam F The effect type of the cache
  * @tparam A The argument type of the command
  * @tparam Mat The materialized result of creating this command
  */
trait NamedCommand[F[_], A, Mat] extends Command[F, A, Mat] {

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
