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

class CachedImmutable[Mutable, Immutable](private val mutable: Mutable)(implicit createImmutable: CreateImmutable[Mutable, Immutable]) {
  private var immutableCache: Immutable = createImmutable.create(mutable)
  private var requiresUpdate = false

  def value: Immutable = {
    if(requiresUpdate) {
      immutableCache = createImmutable.create(mutable)
      requiresUpdate = false
    }

    immutableCache
  }
  def modify(f: Mutable => Unit): Unit = {
    f(mutable)
    requiresUpdate = true
  }

  override def toString = s"CachedImmutable(mutable=$mutable, value=$value)"

  def canEqual(other:        Any): Boolean = other.isInstanceOf[CachedImmutable[_, _]]
  override def equals(other: Any): Boolean = other match {
    case that: CachedImmutable[_, _] =>
      (that canEqual this) &&
        mutable == that.mutable
    case _ => false
  }
  override def hashCode(): Int = {
    val state = Seq(mutable)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
object CachedImmutable {
  def apply[Mutable, Immutable](mutable: Mutable)(implicit createImmutable: CreateImmutable[Mutable, Immutable]) =
    new CachedImmutable[Mutable, Immutable](mutable)
}
