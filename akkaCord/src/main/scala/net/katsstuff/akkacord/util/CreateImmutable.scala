/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
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
package net.katsstuff.akkacord.util

import scala.collection.generic.CanBuildFrom

trait CreateImmutable[Mutable, Immutable] {

  def create(mutable: Mutable): Immutable
}
object CreateImmutable {
  def apply[Mutable, Immutable](implicit createImmutable: CreateImmutable[Mutable, Immutable]): CreateImmutable[Mutable, Immutable] = createImmutable

  implicit def cbfTraversable[From[X] <: Traversable[X], Elem, To[X] <: Traversable[X]](
      implicit canBuildFrom: CanBuildFrom[Nothing, Elem, To[Elem]]
  ) =
    new CreateImmutable[From[Elem], To[Elem]] {
      override def create(mutable: From[Elem]): To[Elem] = {
        val builder = canBuildFrom.apply()

        builder.sizeHint(mutable)
        builder ++= mutable

        builder.result()
      }
    }

  implicit def cbfMap[From[A, B] <: scala.collection.Map[A, B], Key, Value, To[A, B] <: scala.collection.Map[A, B]](
      implicit canBuildFrom: CanBuildFrom[Nothing, (Key, Value), To[Key, Value]]
  ) =
    new CreateImmutable[From[Key, Value], To[Key, Value]] {
      override def create(mutable: From[Key, Value]): To[Key, Value] = {
        val builder = canBuildFrom.apply()

        builder.sizeHint(mutable)
        builder ++= mutable

        builder.result()
      }
    }
}