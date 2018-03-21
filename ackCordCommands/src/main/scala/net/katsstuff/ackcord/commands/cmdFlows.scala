package net.katsstuff.ackcord.commands

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.{Graph, SourceShape}
import akka.stream.scaladsl.Flow
import net.katsstuff.ackcord.CacheSnapshot

/**
  * A class to extract the cache from a parsed cmd object.
  */
class ParsedCmdFlow[A] {
  def map[B](f: CacheSnapshot => ParsedCmd[A] => B): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].map(parsed => f(parsed.cache)(parsed))

  def mapConcat[B](f: CacheSnapshot => ParsedCmd[A] => List[B]): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].mapConcat(parsed => f(parsed.cache)(parsed))

  def mapAsync[B](parallelism: Int)(f: CacheSnapshot => ParsedCmd[A] => Future[B]): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].mapAsync(parallelism)(parsed => f(parsed.cache)(parsed))

  def mapAsyncUnordered[B](
      parallelism: Int
  )(f: CacheSnapshot => ParsedCmd[A] => Future[B]): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].mapAsyncUnordered(parallelism)(parsed => f(parsed.cache)(parsed))

  def flatMapConcat[B](
      f: CacheSnapshot => ParsedCmd[A] => Graph[SourceShape[B], NotUsed]
  ): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].flatMapConcat(parsed => f(parsed.cache)(parsed))

  def flatMapMerge[B](
      breadth: Int
  )(f: CacheSnapshot => ParsedCmd[A] => Graph[SourceShape[B], NotUsed]): Flow[ParsedCmd[A], B, NotUsed] =
    Flow[ParsedCmd[A]].flatMapMerge(breadth, parsed => f(parsed.cache)(parsed))
}
object ParsedCmdFlow {
  def apply[A] = new ParsedCmdFlow[A]
}

/**
  * An object to extract the cache from a unparsed cmd object.
  */
object CmdFlow {
  def map[B](f: CacheSnapshot => Cmd => B): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].map(cmd => f(cmd.cache)(cmd))

  def mapConcat[B](f: CacheSnapshot => Cmd => List[B]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].mapConcat(cmd => f(cmd.cache)(cmd))

  def mapAsync[B](parallelism: Int)(f: CacheSnapshot => Cmd => Future[B]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].mapAsync(parallelism)(cmd => f(cmd.cache)(cmd))

  def mapAsyncUnordered[B](parallelism: Int)(f: CacheSnapshot => Cmd => Future[B]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].mapAsyncUnordered(parallelism)(cmd => f(cmd.cache)(cmd))

  def flatMapConcat[B](f: CacheSnapshot => Cmd => Graph[SourceShape[B], NotUsed]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].flatMapConcat(cmd => f(cmd.cache)(cmd))

  def flatMapMerge[B](breadth: Int)(f: CacheSnapshot => Cmd => Graph[SourceShape[B], NotUsed]): Flow[Cmd, B, NotUsed] =
    Flow[Cmd].flatMapMerge(breadth, cmd => f(cmd.cache)(cmd))
}
