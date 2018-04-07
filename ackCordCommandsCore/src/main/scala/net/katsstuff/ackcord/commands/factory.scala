package net.katsstuff.ackcord.commands

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import net.katsstuff.ackcord.RequestDSL
import net.katsstuff.ackcord.http.requests.RequestHelper
import net.katsstuff.ackcord.util.MessageParser

/**
  * The factory for an unparsed command.
  *
  * @param category The category of this command.
  * @param aliases The aliases for this command.
  * @param sink A sink which defines the behavior of this command.
  * @param filters The filters to use for this command.
  * @param description A description of this command.
  */
case class BaseCmdFactory[+Mat](
    category: CmdCategory,
    aliases: Seq[String],
    sink: RequestHelper => Sink[Cmd, Mat],
    filters: Seq[CmdFilter] = Seq.empty,
    description: Option[CmdDescription] = None,
) extends CmdFactory[Cmd, Mat]
object BaseCmdFactory {
  def requestDSL(
      category: CmdCategory,
      aliases: Seq[String],
      flow: Flow[Cmd, RequestDSL[_], NotUsed],
      filters: Seq[CmdFilter] = Seq.empty,
      description: Option[CmdDescription] = None,
  ): BaseCmdFactory[NotUsed] = {
    val sink: RequestHelper => Sink[Cmd, NotUsed] = requests =>
      flow.flatMapConcat(dsl => RequestDSL(requests.flow)(dsl)).to(Sink.ignore)

    BaseCmdFactory(category, aliases, sink, filters, description)
  }
}

/**
  * The factory for a parsed command.
  * @param category The category of this command.
  * @param aliases The aliases for this command.
  * @param sink A sink which defines the behavior of this command.
  * @param filters The filters to use for this command.
  * @param description A description of this command.
  */
case class ParsedCmdFactory[A, +Mat](
    category: CmdCategory,
    aliases: Seq[String],
    sink: RequestHelper => Sink[ParsedCmd[A], Mat],
    filters: Seq[CmdFilter] = Seq.empty,
    description: Option[CmdDescription] = None,
)(implicit val parser: MessageParser[A])
  extends CmdFactory[ParsedCmd[A], Mat]
object ParsedCmdFactory {
  def requestDSL[A](
      category: CmdCategory,
      aliases: Seq[String],
      flow: Flow[ParsedCmd[A], RequestDSL[_], NotUsed],
      filters: Seq[CmdFilter] = Seq.empty,
      description: Option[CmdDescription] = None,
  )(implicit parser: MessageParser[A]): ParsedCmdFactory[A, NotUsed] = {
    val sink: RequestHelper => Sink[ParsedCmd[A], NotUsed] = requests =>
      flow.flatMapConcat(dsl => RequestDSL(requests.flow)(dsl)).to(Sink.ignore)

    new ParsedCmdFactory(category, aliases, sink, filters, description)
  }
}
