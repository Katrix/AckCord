/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 Katrix
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
package ackcord

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap

package object interactions {
  type ~[A, B] = (A, B)
  object ~ {
    def unapply[A, B](t: (A, B)): Some[(A, B)] = Some(t)
  }

  //Taken from the standard library to compile for 2.12
  private[interactions] def updateWith[K, V](map: TrieMap[K, V])(key: K)(
      remappingFunction: Option[V] => Option[V]
  ): Option[V] = updateWithAux(map)(key)(remappingFunction)

  @tailrec
  private def updateWithAux[K, V](
      map: TrieMap[K, V]
  )(key: K)(remappingFunction: Option[V] => Option[V]): Option[V] = {
    val previousValue = map.get(key)
    val nextValue = remappingFunction(previousValue)
    (previousValue, nextValue) match {
      case (None, None)                                             => None
      case (None, Some(next)) if map.putIfAbsent(key, next).isEmpty => nextValue
      case (Some(prev), None) if map.remove(key, prev)              => None
      case (Some(prev), Some(next)) if map.replace(key, prev, next) => nextValue
      case _ => updateWithAux(map)(key)(remappingFunction)
    }
  }
}
