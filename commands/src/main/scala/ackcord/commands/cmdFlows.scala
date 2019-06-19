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
package ackcord.commands

import scala.concurrent.Future

import ackcord.CacheSnapshot
import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{Graph, SourceShape}

trait CmdFlowBase[A] {

  def getCache(a: A): CacheSnapshot

  def map[B](f: CacheSnapshot => A => B): Flow[A, B, NotUsed] =
    Flow[A].map(a => f(getCache(a))(a))

  def mapConcat[B](f: CacheSnapshot => A => List[B]): Flow[A, B, NotUsed] =
    Flow[A].mapConcat(a => f(getCache(a))(a))

  def mapAsync[B](parallelism: Int)(f: CacheSnapshot => A => Future[B]): Flow[A, B, NotUsed] =
    Flow[A].mapAsync(parallelism)(a => f(getCache(a))(a))

  def mapAsyncUnordered[B](parallelism: Int)(f: CacheSnapshot => A => Future[B]): Flow[A, B, NotUsed] =
    Flow[A].mapAsyncUnordered(parallelism)(a => f(getCache(a))(a))

  def flatMapConcat[B](f: CacheSnapshot => A => Graph[SourceShape[B], NotUsed]): Flow[A, B, NotUsed] =
    Flow[A].flatMapConcat(a => f(getCache(a))(a))

  def flatMapMerge[B](breadth: Int)(f: CacheSnapshot => A => Graph[SourceShape[B], NotUsed]): Flow[A, B, NotUsed] =
    Flow[A].flatMapMerge(breadth, a => f(getCache(a))(a))
}

/**
  * A class to extract the cache from a parsed cmd object.
  */
class ParsedCmdFlow[A] extends CmdFlowBase[ParsedCmd[A]] {
  override def getCache(a: ParsedCmd[A]): CacheSnapshot = a.cache
}
object ParsedCmdFlow {
  def apply[A] = new ParsedCmdFlow[A]
}

/**
  * An object to extract the cache from a unparsed cmd object.
  */
object CmdFlow extends CmdFlowBase[Cmd] {
  override def getCache(a: Cmd): CacheSnapshot = a.cache
}
