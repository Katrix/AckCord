/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.katsstuff.ackcord.commands

import java.util.Locale

import scala.language.higherKinds

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import net.katsstuff.ackcord.RequestDSL
import net.katsstuff.ackcord.http.requests.RequestHelper
import net.katsstuff.ackcord.util.MessageParser

/**
  * Represents some group of commands.
  * The description has no effect on equality between to categories.
  * @param prefix The prefix for this category. This must be lowercase.
  * @param description The description for this category.
  */
case class CmdCategory(prefix: String, description: String) {
  require(prefix.toLowerCase(Locale.ROOT) == prefix, "The prefix of a command category must be lowercase")
  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case null             => false
      case cat: CmdCategory => prefix == cat.prefix
      case _                => false
    }
  }

  override def hashCode(): Int = prefix.hashCode
}

/**
  * Represents non essential information about a command intended to be
  * displayed to an end user.
  * @param name The display name of a command.
  * @param description The description of what a command does.
  * @param usage How to use the command. Does not include the name or prefix.
  */
case class CmdDescription(name: String, description: String, usage: String = "", extra: Map[String, String] = Map.empty)

/**
  * A factory for a command, that also includes other information about
  * the command.
  */
trait CmdFactory[A, +Mat] {

  /**
    * The category of this command.
    */
  def category: CmdCategory

  /**
    * The aliases for this command.
    */
  def aliases: Seq[String]

  /**
    * A sink which defines the behavior of this command.
    */
  def sink: RequestHelper => Sink[A, Mat]

  /**
    * A description of this command.
    */
  def description: Option[CmdDescription]

  def lowercaseAliases: Seq[String] = aliases.map(_.toLowerCase(Locale.ROOT))
}

/**
  * The factory for an unparsed command.
  *
  * @param category The category of this command.
  * @param aliases The aliases for this command.
  * @param sink A sink which defines the behavior of this command.
  * @param filters The filters to use for this command.
  * @param description A description of this command.
  */
case class BaseCmdFactory[F[_], +Mat](
    category: CmdCategory,
    aliases: Seq[String],
    sink: RequestHelper => Sink[Cmd[F], Mat],
    filters: Seq[CmdFilter] = Seq.empty,
    description: Option[CmdDescription] = None,
) extends CmdFactory[Cmd[F], Mat]
object BaseCmdFactory {
  def requestDSL[F[_]](
      category: CmdCategory,
      aliases: Seq[String],
      flow: Flow[Cmd[F], RequestDSL[_], NotUsed],
      filters: Seq[CmdFilter] = Seq.empty,
      description: Option[CmdDescription] = None,
  ): BaseCmdFactory[F, NotUsed] = {
    val sink: RequestHelper => Sink[Cmd[F], NotUsed] = requests =>
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
case class ParsedCmdFactory[F[_], A, +Mat](
    category: CmdCategory,
    aliases: Seq[String],
    sink: RequestHelper => Sink[ParsedCmd[F, A], Mat],
    filters: Seq[CmdFilter] = Seq.empty,
    description: Option[CmdDescription] = None,
)(implicit val parser: MessageParser[A])
    extends CmdFactory[ParsedCmd[F, A], Mat]
object ParsedCmdFactory {
  def requestDSL[F[_], A](
      category: CmdCategory,
      aliases: Seq[String],
      flow: Flow[ParsedCmd[F, A], RequestDSL[_], NotUsed],
      filters: Seq[CmdFilter] = Seq.empty,
      description: Option[CmdDescription] = None,
  )(implicit parser: MessageParser[A]): ParsedCmdFactory[F, A, NotUsed] = {
    val sink: RequestHelper => Sink[ParsedCmd[F, A], NotUsed] = requests =>
      flow.flatMapConcat(dsl => RequestDSL(requests.flow)(dsl)).to(Sink.ignore)

    new ParsedCmdFactory(category, aliases, sink, filters, description)
  }
}
