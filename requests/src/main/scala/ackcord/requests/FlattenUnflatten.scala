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

package ackcord.requests

import shapeless._

trait FlattenUnflatten[In, Out] {
  def toOut(in: In): Out
  def toIn(out: Out): In
}
object FlattenUnflatten extends LowPriorityFlatten {
  def apply[A, B](implicit flatten: FlattenUnflatten[A, B]): FlattenUnflatten[A, B] = flatten

  implicit def two[A, AO <: HList, B, BO <: HList, ALen <: Nat, Concatted <: HList](
      implicit aFlatten: FlattenUnflatten[A, AO],
      bFlatten: FlattenUnflatten[B, BO],
      lenA: ops.hlist.Length.Aux[AO, ALen],
      concat: ops.hlist.Prepend.Aux[AO, BO, Concatted],
      split: ops.hlist.Split.Aux[Concatted, ALen, AO, BO]
  ): FlattenUnflatten[(A, B), Concatted] = new FlattenUnflatten[(A, B), Concatted] {
    override def toOut(in: (A, B)): Concatted =
      concat(aFlatten.toOut(in._1), bFlatten.toOut(in._2))

    override def toIn(out: Concatted): (A, B) = {
      val (ao, bo) = split(out)
      (aFlatten.toIn(ao), bFlatten.toIn(bo))
    }
  }

  implicit val end: FlattenUnflatten[HNil, HNil] =
    new FlattenUnflatten[HNil, HNil] {
      override def toOut(in: HNil): HNil = HNil

      override def toIn(out: HNil): HNil = HNil
    }
}
trait LowPriorityFlatten {
  implicit def dontFlatten[A]: FlattenUnflatten[A, A :: HNil] = new FlattenUnflatten[A, A :: HNil] {
    override def toOut(in: A): A :: HNil = in :: HNil

    override def toIn(out: A :: HNil): A = out.head
  }
}
