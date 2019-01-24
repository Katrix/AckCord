/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
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

import scala.concurrent.Future
import scala.language.higherKinds

import akka.NotUsed
import akka.stream.{Graph, SourceShape}
import akka.stream.scaladsl.Flow
import net.katsstuff.ackcord.CacheSnapshot

trait CmdFlowBase[A, F[_]] {

  def getCache(a: A): CacheSnapshot[F]

  def map[B](f: CacheSnapshot[F] => A => B): Flow[A, B, NotUsed] =
    Flow[A].map(a => f(getCache(a))(a))

  def mapConcat[B](f: CacheSnapshot[F] => A => List[B]): Flow[A, B, NotUsed] =
    Flow[A].mapConcat(a => f(getCache(a))(a))

  def mapAsync[B](parallelism: Int)(f: CacheSnapshot[F] => A => Future[B]): Flow[A, B, NotUsed] =
    Flow[A].mapAsync(parallelism)(a => f(getCache(a))(a))

  def mapAsyncUnordered[B](parallelism: Int)(f: CacheSnapshot[F] => A => Future[B]): Flow[A, B, NotUsed] =
    Flow[A].mapAsyncUnordered(parallelism)(a => f(getCache(a))(a))

  def flatMapConcat[B](f: CacheSnapshot[F] => A => Graph[SourceShape[B], NotUsed]): Flow[A, B, NotUsed] =
    Flow[A].flatMapConcat(a => f(getCache(a))(a))

  def flatMapMerge[B](breadth: Int)(f: CacheSnapshot[F] => A => Graph[SourceShape[B], NotUsed]): Flow[A, B, NotUsed] =
    Flow[A].flatMapMerge(breadth, a => f(getCache(a))(a))
}

/**
  * A class to extract the cache from a parsed cmd object.
  */
class ParsedCmdFlow[F[_], A] extends CmdFlowBase[ParsedCmd[F, A], F] {
  override def getCache(a: ParsedCmd[F, A]): CacheSnapshot[F] = a.cache
}
object ParsedCmdFlow {
  def apply[F[_], A] = new ParsedCmdFlow[F, A]
}

/**
  * An object to extract the cache from a unparsed cmd object.
  */
class CmdFlow[F[_]] extends CmdFlowBase[Cmd[F], F] {
  override def getCache(a: Cmd[F]): CacheSnapshot[F] = a.cache
}
object CmdFlow {
  def apply[F[_]] = new CmdFlow[F]
}
