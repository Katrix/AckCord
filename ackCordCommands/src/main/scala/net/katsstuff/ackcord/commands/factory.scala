/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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

import scala.concurrent.Future

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{Graph, Materializer, SourceShape}
import akka.stream.scaladsl.{Flow, Sink}
import net.katsstuff.ackcord.RequestDSL
import net.katsstuff.ackcord.data.CacheSnapshot
import net.katsstuff.ackcord.http.requests.RequestStreams
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
case class CmdDescription(name: String, description: String, usage: String = "")

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
  def sink: (String, ActorSystem, Materializer) => Sink[A, Mat]

  /**
    * A description of this command.
    */
  def description: Option[CmdDescription]

  def lowercaseAliases: Seq[String] = aliases.map(_.toLowerCase(Locale.ROOT))
}

/**
  * The factory for an unparsed command.
  * @param category The category of this command.
  * @param aliases The aliases for this command.
  * @param sink A sink which defines the behavior of this command.
  * @param filters The filters to use for this command.
  * @param description A description of this command.
  */
case class BaseCmdFactory[+Mat](
    category: CmdCategory,
    aliases: Seq[String],
    sink: (String, ActorSystem, Materializer) => Sink[Cmd, Mat],
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
    val sink: (String, ActorSystem, Materializer) => Sink[Cmd, NotUsed] =
      (token, system, mat) =>
        flow
          .flatMapConcat(dsl => RequestDSL(RequestStreams.simpleRequestFlow(token)(system, mat))(dsl))
          .to(Sink.ignore)

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
    sink: (String, ActorSystem, Materializer) => Sink[ParsedCmd[A], Mat],
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
    val sink: (String, ActorSystem, Materializer) => Sink[ParsedCmd[A], NotUsed] =
      (token, system, mat) =>
        flow
          .flatMapConcat(dsl => RequestDSL(RequestStreams.simpleRequestFlow(token)(system, mat))(dsl))
          .to(Sink.ignore)

    new ParsedCmdFactory(category, aliases, sink, filters, description)
  }
}

/**
  * A class to extract the cache from a parsed cmd object.
  */
class ParsedCmdFlow[A] {
  def map[B](f: CacheSnapshot => ParsedCmd[A] => B): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].map {
      case parsed @ ParsedCmd(_, _, _, c) => f(c)(parsed)
    }

  def mapConcat[B](f: CacheSnapshot => ParsedCmd[A] => List[B]): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].mapConcat {
      case parsed @ ParsedCmd(_, _, _, c) => f(c)(parsed)
    }

  def mapAsync[B](parallelism: Int)(f: CacheSnapshot => ParsedCmd[A] => Future[B]): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].mapAsync(parallelism) {
      case parsed @ ParsedCmd(_, _, _, c) => f(c)(parsed)
    }

  def mapAsyncUnordered[B](
      parallelism: Int
  )(f: CacheSnapshot => ParsedCmd[A] => Future[B]): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].mapAsyncUnordered(parallelism) {
      case parsed @ ParsedCmd(_, _, _, c) => f(c)(parsed)
    }

  def flatMapConcat[B](
      f: CacheSnapshot => ParsedCmd[A] => Graph[SourceShape[B], NotUsed]
  ): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].flatMapConcat {
      case parsed @ ParsedCmd(_, _, _, c) => f(c)(parsed)
    }

  def flatMapMerge[B](
      breadth: Int
  )(f: CacheSnapshot => ParsedCmd[A] => Graph[SourceShape[B], NotUsed]): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].flatMapMerge(breadth, {
      case parsed @ ParsedCmd(_, _, _, c) => f(c)(parsed)
    })
}
object ParsedCmdFlow {
  def apply[A] = new ParsedCmdFlow[A]
}

/**
  * An object to extract the cache from a unparsed cmd object.
  */
object CmdFlow {
  def map[B](f: CacheSnapshot => Cmd => B): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].map {
      case parsed @ Cmd(_, _, c) => f(c)(parsed)
    }

  def mapConcat[B](f: CacheSnapshot => Cmd => List[B]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].mapConcat {
      case parsed @ Cmd(_, _, c) => f(c)(parsed)
    }

  def mapAsync[B](parallelism: Int)(f: CacheSnapshot => Cmd => Future[B]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].mapAsync(parallelism) {
      case parsed @ Cmd(_, _, c) => f(c)(parsed)
    }

  def mapAsyncUnordered[B](parallelism: Int)(f: CacheSnapshot => Cmd => Future[B]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].mapAsyncUnordered(parallelism) {
      case parsed @ Cmd(_, _, c) => f(c)(parsed)
    }

  def flatMapConcat[B](f: CacheSnapshot => Cmd => Graph[SourceShape[B], NotUsed]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].flatMapConcat {
      case parsed @ Cmd(_, _, c) => f(c)(parsed)
    }

  def flatMapMerge[B](breadth: Int)(f: CacheSnapshot => Cmd => Graph[SourceShape[B], NotUsed]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].flatMapMerge(breadth, {
      case parsed @ Cmd(_, _, c) => f(c)(parsed)
    })
}
